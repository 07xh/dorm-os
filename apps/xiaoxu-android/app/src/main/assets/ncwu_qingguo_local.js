/*
 * 校序 · 华水青果本机课表提取
 * Adapted from MIT-licensed ShiGuangSchedule/shiguang_warehouse
 * resources/qingguo_jiaowu/qingguo_01.js (copyright 2025 星河欲转).
 * See THIRD_PARTY_NOTICES.md. Runs only after the user taps "提取课表"
 * on the official NCWU site. Does not read passwords, cookies or student IDs.
 */
(function () {
  "use strict";
  const badText = /^(未知|待定|未安排|待通知|无|不详)$/;
  const tidy = value => String(value == null ? "" : value).replace(/\s+/g, " ").trim();
  function weeks(spec, parity) {
    const result = new Set();
    const pieces = String(spec).replace(/[，\s]/g, ",").replace(/[—–～~至]/g, "-").split(",").filter(Boolean);
    if (!pieces.length || pieces.length > 35) return null;
    for (const piece of pieces) {
      const m = /^(\d{1,2})(?:-(\d{1,2}))?$/.exec(piece);
      if (!m) return null;
      const from = Number(m[1]), to = Number(m[2] || m[1]);
      if (from < 1 || to > 35 || from > to) return null;
      for (let week = from; week <= to; week++) {
        if (!parity || (parity === "单" ? week % 2 === 1 : week % 2 === 0)) result.add(week);
      }
    }
    return result.size ? [...result].sort((a, b) => a - b) : null;
  }
  function sectionTime(text) {
    const m = /^([\d,，\s\-—–~～至]+?)(?:\(([单双])\)|([单双]))?周\s*\[\s*(\d{1,2})\s*[-~～至]\s*(\d{1,2})\s*\]$/.exec(tidy(text));
    if (!m) return null;
    const start = Number(m[4]), end = Number(m[5]);
    const parsedWeeks = weeks(m[1], m[2] || m[3]);
    return parsedWeeks && start >= 1 && end >= start && end <= 24
      ? { startSection: start, endSection: end, weeks: parsedWeeks } : null;
  }
  function sane(text, max) { return text.length > 0 && text.length <= max && !badText.test(text); }
  function readLines(element) {
    // innerText preserves explicit line breaks that textContent would discard.
    return String(element.innerText || "").split(/[\r\n]+/).map(tidy).filter(Boolean);
  }
  // Official layouts differ in inline CSS. Parse the visible four-field
  // course lines even if the original padding-bottom marker is absent.
  // A non-four-line block is retained as ONE invalid candidate so the whole
  // import fails closed rather than silently dropping a visible course.
  function readCourseGroups(cell) {
    const marked = [...cell.querySelectorAll("div")].filter(div =>
      /padding-bottom\s*:\s*5px/i.test(div.getAttribute("style") || ""));
    if (marked.length) return marked.map(readLines);
    const lines = readLines(cell);
    if (!lines.some(line => /周\s*[\[【]\s*\d{1,2}/.test(line))) return [];
    if (lines.length % 4 !== 0) return [lines];
    return Array.from({ length: lines.length / 4 },
      (_, index) => lines.slice(index * 4, index * 4 + 4));
  }
  function parseBlock(lines, day) {
    if (lines.length !== 4) return null;
    const [name, teacher, time, position] = lines;
    const sections = sectionTime(time);
    if (!sane(name, 120) || !sane(teacher, 90) || !sane(position, 130) || !sections) return null;
    return { name, teacher, position, day, ...sections };
  }
  function span(value) {
    if (!value) return 1;
    if (!/^[1-9]\d?$/.test(value)) return -1;
    const n = Number(value);
    return n <= 80 ? n : -1;
  }
  function findTables(win, depth) {
    if (depth > 4) return [];
    const found = [];
    try {
      const doc = win.document;
      for (const table of doc.querySelectorAll("table")) {
        found.push(table);
      }
      for (let i = 0; i < win.frames.length; i++) found.push(...findTables(win.frames[i], depth + 1));
    } catch (_) { /* cross-origin frame; no cookie or network fallback */ }
    return found;
  }
  function parseGrid(table) {
    const rows = [...table.querySelectorAll("tr")].filter(row => row.querySelector(":scope > td.td"));
    if (!rows.length || rows.length > 80) return null;
    let carried = Array(7).fill(0), blocksSeen = 0, bad = 0;
    const courses = [];
    for (const row of rows) {
      const cells = [...row.children].filter(cell => cell.matches("td.td"));
      const occupied = carried.map(value => value > 0);
      const next = carried.map(value => Math.max(0, value - 1));
      let dayIndex = 0, rowInvalid = false, rowBlocks = 0;
      const rowCourses = [];
      for (const cell of cells) {
        while (dayIndex < 7 && occupied[dayIndex]) dayIndex++;
        const rs = span(cell.getAttribute("rowspan"));
        const cs = span(cell.getAttribute("colspan"));
        if (rs < 1 || cs < 1 || dayIndex + cs > 7 ||
            occupied.slice(dayIndex, dayIndex + cs).some(Boolean)) { rowInvalid = true; break; }
        for (let k = dayIndex; k < dayIndex + cs; k++) {
          occupied[k] = true;
          if (rs > 1) next[k] = rs - 1;
        }
        const groups = readCourseGroups(cell);
        if (cs > 1 && groups.length) { rowInvalid = true; break; }
        for (const lines of groups) {
          rowBlocks++;
          const parsed = parseBlock(lines, dayIndex + 1);
          if (!parsed) bad++; else rowCourses.push(parsed);
        }
        dayIndex += cs;
      }
      if (rowInvalid || occupied.some(value => !value)) {
        bad += rowBlocks || 1;
        carried = Array(7).fill(0);
      } else {
        blocksSeen += rowBlocks;
        courses.push(...rowCourses);
        carried = next;
      }
    }
    if (carried.some(n => n > 0)) bad++;
    return { items: courses, seen: blocksSeen, blocked: bad };
  }

  /*
   * The upstream MIT Qingguo adapter also supports tables identified by
   * 星期一…星期日 headers instead of td.td classes. This fallback maps each day
   * to its actual header column; it does NOT guess weekday from cell offsets.
   * Merged data cells or incomplete course blocks are fail-closed.
   */
  function parseHeaderGrid(table) {
    const rows = [...table.querySelectorAll("tr")];
    if (rows.length < 2 || rows.length > 100) return null;
    const dayMap = { "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "日": 7, "天": 7 };
    let headerIndex = -1, columnForDay = null, expectedCells = 0;
    for (let rowIndex = 0; rowIndex < Math.min(rows.length, 4); rowIndex++) {
      const headers = [...(rows[rowIndex].cells || [])];
      const columns = new Map();
      headers.forEach((cell, col) => {
        const matched = /^(?:星期|周)([一二三四五六日天])$/.exec(tidy(cell.innerText || ""));
        if (matched) columns.set(dayMap[matched[1]], col);
      });
      if (columns.size === 7 && new Set(columns.values()).size === 7 &&
          headers.every(cell => span(cell.getAttribute("colspan")) === 1)) {
        headerIndex = rowIndex;
        columnForDay = columns;
        expectedCells = headers.length;
        break;
      }
    }
    if (headerIndex < 0) return null;
    const courses = [];
    let seen = 0, blocked = 0;
    for (const row of rows.slice(headerIndex + 1)) {
      const cells = [...(row.cells || [])];
      // Treat visible bracketed course markers as evidence of a data row.
      const hasCourse = cells.some(cell => /[\[【]\s*\d{1,2}\s*[-~～至]/.test(cell.innerText || ""));
      if (!hasCourse) continue;
      if (cells.length !== expectedCells ||
          cells.some(cell => span(cell.getAttribute("rowspan")) !== 1 ||
            span(cell.getAttribute("colspan")) !== 1)) {
        blocked++;
        continue;
      }
      for (let day = 1; day <= 7; day++) {
        const cell = cells[columnForDay.get(day)];
        if (!cell) { blocked++; continue; }
        const lineGroups = readCourseGroups(cell);
        for (const lines of lineGroups) {
          seen++;
          const result = parseBlock(lines, day);
          if (result) courses.push(result); else blocked++;
        }
      }
    }
    return { items: courses, seen, blocked };
  }

  function merge(items) {
    // Combine identical meetings by explicit week/section occupancy, not by
    // silently choosing a weekday or discarding teacher/location differences.
    const groups = new Map();
    for (const item of items) {
      const key = [item.name, item.teacher, item.position, item.day].join("\u001f");
      if (!groups.has(key)) groups.set(key, new Map());
      const dayWeeks = groups.get(key);
      for (const week of item.weeks) {
        if (!dayWeeks.has(week)) dayWeeks.set(week, new Set());
        for (let section = item.startSection; section <= item.endSection; section++)
          dayWeeks.get(week).add(section);
      }
    }
    const output = [];
    for (const [key, dayWeeks] of groups) {
      const [name, teacher, position, dayString] = key.split("\u001f");
      const patternWeeks = new Map();
      for (const [week, sectionsSet] of dayWeeks) {
        const sections = [...sectionsSet].sort((a, b) => a - b);
        let first = sections[0];
        for (let i = 0; i < sections.length; i++) {
          if (i === sections.length - 1 || sections[i + 1] !== sections[i] + 1) {
            const pattern = first + "-" + sections[i];
            if (!patternWeeks.has(pattern)) patternWeeks.set(pattern, []);
            patternWeeks.get(pattern).push(week);
            first = sections[i + 1];
          }
        }
      }
      for (const [pattern, weekList] of patternWeeks) {
        const [startSection, endSection] = pattern.split("-").map(Number);
        output.push({ name, teacher, position, day: Number(dayString), startSection,
          endSection, weeks: weekList.sort((a, b) => a - b) });
      }
    }
    return output;
  }
  window.__xiaoxuExtract = function () {
    const tables = findTables(window, 0);
    // Prefer explicitly marked seven-day grids. Different sites/versions can
    // use normal <td> without a .td class, so use the weekday-header fallback
    // only when no recognized class grid exists.
    const classGrids = tables.filter(t => t.querySelector("td.td"));
    let candidates = classGrids.map(parseGrid).filter(x => x && x.seen > 0);
    if (!candidates.length) {
      candidates = tables.filter(t => !t.querySelector("td.td"))
        .map(parseHeaderGrid).filter(x => x && x.seen > 0);
    }
    if (candidates.length !== 1)
      return { error: candidates.length ? "识别到多个课表，无法确定应导入哪一个" :
        "当前页未找到可完整识别的七日课表；请在官网进入个人课表页面（必要时切换电脑版）" };
    const result = candidates[0];
    if (result.blocked > 0 || result.items.length !== result.seen)
      return { error: "识别到 " + result.seen + " 个课块，其中 " + result.blocked + " 个结构不完整；已保护原有课表，请在官方课表核对后重试" };
    const items = merge(result.items);
    if (!items.length || items.length > 300)
      return { error: "未获得完整课程，旧课表保持不变" };
    return { items };
  };
})();
