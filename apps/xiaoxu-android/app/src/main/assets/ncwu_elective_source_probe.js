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
})();
