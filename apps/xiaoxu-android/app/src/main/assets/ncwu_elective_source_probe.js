/*
 * NCWU elective source fingerprint for the already-open official WebView.
 * Structure only: no rows, cell contents, URLs, accounts, credentials or
 * network requests leave the page. Manual tap only; does not save anything.
 */
(function () {
  "use strict";
  const rules = [
    ["courseName", /^(?:课程|课程名称|课程名|选修课程)$/],
    ["courseCode", /^(?:课程号|课程代码|课程编号)$/],
    ["classCode", /^(?:教学班|教学班号|教学班代码|教学班编号|上课班级)$/],
    ["teacher", /^(?:教师|任课教师|授课教师|主讲教师)$/],
    ["campus", /^(?:校区|上课校区)$/],
    ["capacity", /^(?:容量|选课容量|班级容量|计划人数|限选人数|人数上限)$/],
    ["remaining", /^(?:余量|剩余名额|剩余人数|可选人数|剩余容量)$/],
    ["meeting", /^(?:上课时间|上课时间地点|上课时间及地点|时间地点|上课地点|时间|星期节次)$/],
    ["status", /^(?:选课状态|选课情况|状态)$/],
    ["courseType", /^(?:课程类别|课程类型|课程性质|修读性质)$/],
    ["credit", /^(?:学分|学分数)$/]
  ];
  const tidy = value => String(value == null ? "" : value)
    .replace(/[\s:：*＊]+/g, "").trim().slice(0, 40);
  const classify = value => {
    const label = tidy(value);
    const rule = rules.find(([, pattern]) => pattern.test(label));
    return rule ? rule[0] : null;
  };

  function inspect(root) {
    let scannedDocuments = 0;
    let blockedFrames = 0;
    let scannedTables = 0;
    const tables = [];
    const seenDocuments = new Set();
    function traverse(doc, depth) {
      if (!doc || seenDocuments.has(doc) || depth > 2 || scannedDocuments >= 12) return;
      seenDocuments.add(doc);
      scannedDocuments++;
      let available;
      try { available = Array.from(doc.querySelectorAll("table")).slice(0, 60); }
      catch (_) { return; }
      for (const table of available) {
        if (scannedTables >= 80) break;
        scannedTables++;
        let rows;
        try { rows = Array.from(table.querySelectorAll("tr")); }
        catch (_) { continue; }
        let columnTypes = [];
        for (const row of rows.slice(0, 3)) {
          let cells;
          try { cells = Array.from(row.querySelectorAll("th,td")); }
          catch (_) { continue; }
          const recognized = [...new Set(cells.slice(0, 32)
            .map(cell => classify(cell.textContent)).filter(Boolean))];
          if (recognized.length > columnTypes.length) columnTypes = recognized;
        }
        // Only a structural hint. Even an exact match is not trusted course data.
        if (columnTypes.includes("courseName") || columnTypes.includes("classCode")) {
          tables.push({
            columns: columnTypes,
            rows: Math.min(rows.length, 500),
            likelyCatalog: columnTypes.includes("courseName") &&
              columnTypes.some(x => x === "classCode" || x === "teacher" ||
                x === "capacity" || x === "remaining")
          });
        }
      }
      if (depth === 2) return;
      let frames;
      try { frames = Array.from(doc.querySelectorAll("iframe,frame")).slice(0, 12); }
      catch (_) { return; }
      for (const frame of frames) {
        try {
          if (frame.contentDocument) traverse(frame.contentDocument, depth + 1);
          else blockedFrames++;
        } catch (_) { blockedFrames++; }
      }
    }
    traverse(root, 0);
    return {
      schema: "xiaoxu/ncwu-elective-source-fingerprint-v1",
      scannedDocuments, scannedTables, blockedFrames,
      candidateTables: tables.slice(0, 20),
      // Identification of headings does NOT prove authenticated catalog access.
      containsCourseRecords: false,
      officialAvailabilityVerified: false
    };
  }
  window.__xiaoxuInspectElectiveSource = () => inspect(document);

  /**
   * Explicit, local-only preview of existing table rows. No network action,
   * no school write, no account data, and no automatic persistence.
   * A row is accepted only when all three official-like headers are unique.
   */
  function preview(root) {
    const classes = [];
    const documents = new Set();
    let scannedTables = 0;
    let blockedFrames = 0;
    let truncated = false;
    const safeText = (value, max) => {
      const valueText = String(value == null ? "" : value).trim();
      return valueText.length <= max && !/[\u0000-\u001f\u007f<>]/.test(valueText)
        ? valueText : "";
    };
    const count = value => {
      const data = safeText(value, 8);
      return /^\d{1,5}$/.test(data) ? Number(data) : null;
    };
    function traverse(doc, depth) {
      if (!doc || documents.has(doc) || depth > 2 || documents.size >= 12) return;
      documents.add(doc);
      let tables = [];
      try { tables = Array.from(doc.querySelectorAll("table")).slice(0, 60); }
      catch (_) { return; }
      for (const table of tables) {
        if (scannedTables >= 80) { truncated = true; return; }
        scannedTables++;
        let rows = [];
        try { rows = Array.from(table.querySelectorAll("tr")).slice(0, 501); }
        catch (_) { continue; }
        let columns = null;
        let heading = -1;
        for (let i = 0; i < Math.min(3, rows.length); i++) {
          let cells = [];
          try { cells = Array.from(rows[i].querySelectorAll("th,td")); }
          catch (_) { continue; }
          if (cells.length > 32) continue;
          const labels = cells.map(cell => classify(cell.textContent));
          if (["courseName", "courseCode", "classCode"].every(name =>
            labels.filter(label => label === name).length === 1)) {
            columns = labels;
            heading = i;
            break;
          }
        }
        if (!columns) continue;
        for (const row of rows.slice(heading + 1)) {
          if (classes.length >= 100) { truncated = true; return; }
          let cells = [];
          try { cells = Array.from(row.querySelectorAll("th,td")); }
          catch (_) { continue; }
          // Do not guess cell positions in rowspan/colspan or nested layouts.
          if (cells.length !== columns.length) continue;
          const field = name => {
            const index = columns.indexOf(name);
            return index < 0 ? "" : cells[index].textContent;
          };
          const courseCode = safeText(field("courseCode"), 96);
          const classCode = safeText(field("classCode"), 96);
          const courseName = safeText(field("courseName"), 120);
          if (!/^[A-Za-z0-9._:-]{1,96}$/.test(courseCode) ||
              !/^[A-Za-z0-9._:-]{1,96}$/.test(classCode) ||
              !courseName) continue;
          const capacity = count(field("capacity"));
          let remaining = count(field("remaining"));
          if (capacity !== null && remaining !== null && remaining > capacity)
            remaining = null;
          const record = {
            courseCode, classCode, courseName,
            teacher: safeText(field("teacher"), 90),
            campus: safeText(field("campus"), 90),
            capacity, remaining
          };
          if (!classes.some(x => x.courseCode === courseCode &&
                                 x.classCode === classCode)) classes.push(record);
        }
        if (rows.length >= 501) truncated = true;
      }
      if (depth >= 2) return;
      let frames = [];
      try { frames = Array.from(doc.querySelectorAll("iframe,frame")).slice(0, 12); }
      catch (_) { return; }
      for (const frame of frames) {
        try {
          if (frame.contentDocument) traverse(frame.contentDocument, depth + 1);
          else blockedFrames++;
        } catch (_) { blockedFrames++; }
      }
    }
    traverse(root, 0);
    return {
      schema: "xiaoxu/ncwu-elective-local-preview-v1",
      classes, scannedTables, blockedFrames, truncated,
      sourceState: "unverified", readOnly: true,
      submissionAllowed: false, officialAvailabilityVerified: false
    };
  }
  window.__xiaoxuPreviewElectiveCatalog = () => preview(document);
})();
