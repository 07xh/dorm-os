/* 校序：个人只读任务快照导入。独立于任何第三方登录、抓包或自动提交。 */
(function (root) {
  'use strict';
  const SOURCES = Object.freeze(['chaoxing', 'ncwu', 'second_class']);
  const STATUSES = Object.freeze(['todo', 'submitted', 'graded', 'returned', 'unknown']);
  const FORMAT = 'xiaoxu-task-batch-v1';
  const text = (v, max) => typeof v === 'string' ? v.trim().slice(0, max) : '';
  const validDate = v => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
    const d = new Date(v + 'T00:00:00Z');
    return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === v;
  };
  function normalizeBatch(input) {
    if (!input || typeof input !== 'object' || Array.isArray(input) || input.format !== FORMAT)
      throw new Error('文件格式不支持，请使用 xiaoxu-task-batch-v1');
    if (!SOURCES.includes(input.source)) throw new Error('任务来源不支持');
    if (!Array.isArray(input.tasks) || input.tasks.length > 300) throw new Error('任务列表必须是数组且不超过 300 条');
    const seen = new Set();
    const tasks = input.tasks.map((entry, index) => {
      if (!entry || typeof entry !== 'object' || Array.isArray(entry)) throw new Error('第 ' + (index + 1) + ' 条任务格式错误');
      const externalId = text(entry.externalId, 80);
      const title = text(entry.title, 100);
      const course = text(entry.course, 80);
      const due = entry.due == null || entry.due === '' ? '' : entry.due;
      const dueAt = entry.dueAt == null || entry.dueAt === '' ? '' : entry.dueAt;
      const instant=/^(\\d{4}-\\d{2}-\\d{2})T(?:[01]\\d|2[0-3]):[0-5]\\d:00\\+08:00$/.exec(dueAt);
      if (!externalId || externalId.length > 80 || !title || typeof due !== 'string' ||
          (due && !validDate(due)) || typeof dueAt !== 'string' || (dueAt && (!instant || !validDate(due) || instant[1]!==due)) || !STATUSES.includes(entry.status))
        throw new Error('第 ' + (index + 1) + ' 条任务缺少 ID/标题，或日期、状态不合法');
      if (typeof entry.externalId !== 'string' || entry.externalId.trim() !== externalId ||
          typeof entry.title !== 'string' || entry.title.trim() !== title ||
          (entry.course != null && typeof entry.course !== 'string'))
        throw new Error('第 ' + (index + 1) + ' 条任务存在非法或超长字段');
      if (seen.has(externalId)) throw new Error('任务文件中存在重复 ID：' + externalId);
      seen.add(externalId);
      return {externalId, title, course, due, dueAt, status: entry.status};
    });
    return {format: FORMAT, source: input.source, tasks};
  }
  function planImport(existingTasks, batch, makeId, importedAt) {
    if (!Array.isArray(existingTasks) || typeof makeId !== 'function') throw new Error('本地任务状态无效');
    const clean = normalizeBatch(batch);
    const output = existingTasks.map(item => ({...item}));
    const changes = [];
    const ids = new Set(output.map(item => item.id));
    for (const item of clean.tasks) {
      const current = output.find(t => t.source === clean.source && t.sourceId === item.externalId);
      if (current) {
        const changeset = {
          title: item.title, course: item.course, due: item.due, dueAt: item.dueAt,
          sourceStatus: item.status
        };
        const differs = Object.keys(changeset).some(k => (current[k] || '') !== changeset[k]);
        if (!differs) continue;
        Object.assign(current, changeset, {importedAt});
        changes.push({type: 'updated', title: item.title});
      } else {
        if (output.length >= 300) throw new Error('本地任务已达到 300 条上限，未进行任何导入');
        const id = makeId();
        if (typeof id !== 'string' || !id || ids.has(id)) throw new Error('生成任务标识失败');
        ids.add(id);
        output.push({
          id, title: item.title, category: '课程', due: item.due, dueAt: item.dueAt, done: false,
          createdAt: importedAt, source: clean.source, sourceId: item.externalId,
          sourceStatus: item.status, course: item.course, importedAt
        });
        changes.push({type: 'added', title: item.title});
      }
    }
    return {
      tasks: output,
      source: clean.source,
      inputCount: clean.tasks.length,
      added: changes.filter(c => c.type === 'added').length,
      updated: changes.filter(c => c.type === 'updated').length,
      unchanged: clean.tasks.length - changes.length,
      changes
    };
  }
  root.XiaoxuTaskImport = Object.freeze({FORMAT, SOURCES, STATUSES, normalizeBatch, planImport});
})(globalThis);
