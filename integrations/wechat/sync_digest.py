"""Local-only, privacy-minimal WeChat synchronization evidence and daily report.

No WeChat reading, cloud API, credentials, raw chat messages, titles, URLs or
group names. Reports describe acknowledged candidate uploads only, NOT actual
schoolwork completion or the current review status on Cloudflare D1.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone, date
from pathlib import Path
import json
import os
import re

CHINA = timezone(timedelta(hours=8))
AUDIT_PATH = Path.home() / ".wechat-cli" / "xiaoxu-sync-audit.json"
REPORT_DIR = Path.home() / ".wechat-cli" / "daily-reports"
RUN_LOG_PATH = Path.home() / ".wechat-cli" / "xiaoxu-run-log.jsonl"
SCHEMA = "xiaoxu/wechat-local-audit-v1"
CANDIDATE = re.compile(r"^[A-Za-z0-9_-]{40,128}$")
DAY = re.compile(r"^\d{4}-\d{2}-\d{2}$")
KINDS = {"homework", "exam", "activity", "signup", "other"}
MAX_DAYS = 32
MAX_KEYS_PER_DAY = 10000


class AuditError(ValueError):
    pass


def _day(value: str) -> date:
    if not isinstance(value, str) or not DAY.fullmatch(value):
        raise AuditError("invalid_day")
    try:
        parsed = date.fromisoformat(value)
    except ValueError as e:
        raise AuditError("invalid_day") from e
    if parsed.isoformat() != value:
        raise AuditError("invalid_day")
    return parsed


def _stamp(dt: datetime) -> str:
    if not isinstance(dt, datetime) or dt.tzinfo is None or dt.utcoffset() is None:
        raise AuditError("invalid_time")
    return dt.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def read_audit(path: Path = AUDIT_PATH) -> dict:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {"schema": SCHEMA, "days": {}}
    except (OSError, UnicodeError, ValueError) as e:
        raise AuditError("audit_unavailable_or_corrupt") from e
    if not isinstance(raw, dict) or set(raw) != {"schema", "days"} or raw["schema"] != SCHEMA \
            or not isinstance(raw["days"], dict) or len(raw["days"]) > MAX_DAYS:
        raise AuditError("audit_invalid")
    for day, entry in raw["days"].items():
        _day(day)
        if not isinstance(entry, dict) or set(entry) != {"runs", "lastSuccessAt", "modeRuns", "candidates"}:
            raise AuditError("audit_invalid")
        if type(entry["runs"]) is not int or not 0 <= entry["runs"] <= 100000:
            raise AuditError("audit_invalid")
        if not isinstance(entry["modeRuns"], dict) or set(entry["modeRuns"]) != {"rules", "ai"} \
                or any(type(x) is not int or x < 0 for x in entry["modeRuns"].values()):
            raise AuditError("audit_invalid")
        if not isinstance(entry["lastSuccessAt"], str) or len(entry["lastSuccessAt"]) > 40 \
                or not isinstance(entry["candidates"], dict) \
                or len(entry["candidates"]) > MAX_KEYS_PER_DAY:
            raise AuditError("audit_invalid")
        for key, task in entry["candidates"].items():
            if not isinstance(key, str) or not CANDIDATE.fullmatch(key) \
                    or not isinstance(task, dict) or set(task) != {"kind", "due"} \
                    or task["kind"] not in KINDS:
                raise AuditError("audit_invalid")
            if task["due"] is not None:
                _day(task["due"])
    return raw


def _save(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    try:
        with temp.open("w", encoding="utf-8") as stream:
            json.dump(data, stream, ensure_ascii=False, separators=(",", ":"))
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def record_success(records: list[dict], mode: str, *, at: datetime | None = None,
                   path: Path = AUDIT_PATH) -> None:
    """Call only AFTER server acknowledged all batches and local cursor was saved.

    Replayed candidates and 24-hour overlap do not inflate new-candidate count.
    If audit I/O fails, caller must warn, but must not undo a completed cloud sync.
    """
    if mode not in ("rules", "ai") or not isinstance(records, list) or len(records) > MAX_KEYS_PER_DAY:
        raise AuditError("audit_record_invalid")
    now = at or datetime.now(timezone.utc)
    day = now.astimezone(CHINA).date().isoformat()
    audit = read_audit(path)
    days = audit["days"]
    previous = set()
    for record_day, entry in days.items():
        if record_day != day:
            previous.update(entry["candidates"])
    entry = days.setdefault(day, {
        "runs": 0, "lastSuccessAt": _stamp(now),
        "modeRuns": {"rules": 0, "ai": 0}, "candidates": {}
    })
    for record in records:
        if not isinstance(record, dict) or not isinstance(record.get("candidateKey"), str) \
                or not CANDIDATE.fullmatch(record["candidateKey"]) \
                or record.get("kind") not in KINDS:
            raise AuditError("audit_record_invalid")
        key = record["candidateKey"]
        if key in previous or key in entry["candidates"]:
            continue
        if len(entry["candidates"]) >= MAX_KEYS_PER_DAY:
            raise AuditError("audit_candidate_limit")
        due = record.get("deadline", {}).get("date")
        if due is not None:
            _day(due)
        entry["candidates"][key] = {"kind": record["kind"], "due": due}
    entry["runs"] += 1
    entry["modeRuns"][mode] += 1
    entry["lastSuccessAt"] = _stamp(now)
    while len(days) > MAX_DAYS:
        del days[min(days)]
    _save(path, audit)


def read_failures(for_day: str, path: Path = RUN_LOG_PATH) -> list[dict] | None:
    """Local-only run log entries for one Beijing day. None means unreadable,
    which must be reported as unknown rather than as zero failures.
    """
    try:
        raw = path.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError:
        return []
    except (OSError, UnicodeError):
        return None
    out = []
    for line in raw:
        try:
            item = json.loads(line)
        except ValueError:
            continue
        if isinstance(item, dict) and isinstance(item.get("at"), str) \
                and item["at"].startswith(for_day) and isinstance(item.get("error"), str):
            out.append(item)
    return out


def _sections(entry: dict | None, today: date, failures: list[dict] | None) -> list[str]:
    """Six required daily-report sections. Cloud task status is not available
    locally, so those sections state the absence of evidence instead of
    inventing reviewed, completed, cancelled or rescheduled tasks.
    """
    lines = ["## 1. 今日新增候选"]
    if entry:
        data = entry["candidates"]
        kinds = {kind: sum(x["kind"] == kind for x in data.values()) for kind in sorted(KINDS)}
        lines += [
            f"- 本机首次记录 **{len(data)}** 条候选（仅云端确认的上传证据，不代表已审核任务）。",
            "- 类型统计：" + "；".join(f"{kind} {value}" for kind, value in kinds.items()) + "。",
        ]
    else:
        lines.append("- 本机没有可用的同步证据；不能据此判断微信群是否有新任务。")
    lines += [
        "",
        "## 2. 待审核任务",
        "- 本机不读取校序云端审核状态，没有证据列出待审核清单；请在「任务 → 微信待审核」核对，本区不编造条目。",
        "",
        "## 3. 已确认完成的任务",
        "- 本机没有完成状态证据，**不得判断任何任务已完成**；可确认完成 0 条。",
        "",
        "## 4. 已取消、改期任务",
        "- 本机没有取消或改期状态证据；可确认 0 条，请勿用本地摘要推断。",
        "",
        "## 5. 最近三天截止任务",
    ]
    if entry:
        per_date = {}
        for item in entry["candidates"].values():
            if item["due"] is not None and today <= _day(item["due"]) <= today + timedelta(days=3):
                per_date[item["due"]] = per_date.get(item["due"], 0) + 1
        lines.extend(f"- {day}：{number} 条待核对候选" for day, number in sorted(per_date.items()))
        if not per_date:
            lines.append("- 本机摘要中没有这段时间的截止候选；不代表没有未读通知。")
    else:
        lines.append("- 没有可用摘要；不能判断未来三天是否有截止任务。")
    lines += ["", "## 6. 同步失败及漏读风险"]
    if failures is None:
        lines.append("- 本地运行日志不可读或未附带：无法统计失败次数，不能排除漏读。")
    else:
        by_collector = sum(1 for f in failures if f.get("source") != "scheduler")
        by_scheduler = sum(1 for f in failures if f.get("source") == "scheduler")
        last_at = max((f["at"] for f in failures), default=None)
        lines.append(
            f"- 当日本地失败记录 **{len(failures)}** 条（采集 {by_collector}，调度 {by_scheduler}）"
            + (f"，最后一次 {last_at[11:19]}" if last_at else "") + "。"
        )
    lines += [
        "- 失败不推进同步游标，已成功同步的消息不会被重复处理；但不能据此认定失败期间的微信消息都已采集。",
        "- 连续失败时段存在漏读风险：这些消息要等下一次成功 sync 才进入候选，本机无法证明无漏读。",
        "- 打开校序任务中心核对云端审核、完成、取消和改期状态；本日报不能替代任务中心。",
        "",
    ]
    return lines


def render_report(audit: dict, for_day: str, *, now: datetime | None = None,
                  failures: list[dict] | None = None) -> str:
    today = _day(for_day)
    entry = audit["days"].get(for_day)
    lines = [
        f"# 校序微信候选同步日报 · {for_day}（北京时间）",
        "",
        "> 本报告来自本机已记录的云端确认结果，不代表已审核任务、学校官方提交状态或全量微信消息。",
        "> 仅记录首次观察到的候选；历史重叠消息不重复计数。实际待办请在校序「微信待审核」与任务中心核对。",
        "",
    ]
    if not entry:
        return "\n".join(lines + [
            "## 当日同步证据",
            "本机没有可用的成功同步记录；不能据此判断微信群是否有新任务，也不能认定同步成功。",
            "",
        ] + _sections(None, today, failures))
    data = entry["candidates"]
    kinds = {kind: sum(x["kind"] == kind for x in data.values()) for kind in sorted(KINDS)}
    pending = [x for x in data.values() if x["due"] is not None and
               today <= _day(x["due"]) <= today + timedelta(days=3)]
    per_date = {}
    for item in pending:
        per_date[item["due"]] = per_date.get(item["due"], 0) + 1
    last = datetime.fromisoformat(entry["lastSuccessAt"].replace("Z", "+00:00")).astimezone(CHINA)
    lines.extend([
        "## 当日同步证据",
        f"- 云端确认的同步运行：**{entry['runs']}** 次（规则 {entry['modeRuns']['rules']}，AI {entry['modeRuns']['ai']}）。",
        f"- 本机首次记录的候选：**{len(data)}** 条（不代表本日新增的已审核任务）。",
        f"- 最近确认时间：**{last.strftime('%Y-%m-%d %H:%M:%S')}**。",
        "- 类型统计：" + "；".join(f"{kind} {value}" for kind, value in kinds.items()) + "。",
        "",
        "## 未来三日内的候选截止日期",
    ])
    if per_date:
        lines.extend(f"- {day}：{number} 条待核对候选" for day, number in sorted(per_date.items()))
    else:
        lines.append("- 本机摘要中没有带这段时间截止日期的候选；不代表没有未读通知。")
    lines.extend([
        "",
        "## 下一步",
        "- 打开校序「任务 → 微信待审核」，确认消息是否属于本人以及截止时间是否正确。",
        "- 本报告无法读取云端审核、已完成、取消和改期状态；不要用这里的数量代替任务中心数量。",
        "",
    ])
    return "\n".join(lines + _sections(entry, today, failures))


def write_report(*, at: datetime | None = None, path: Path = AUDIT_PATH,
                 directory: Path = REPORT_DIR,
                 run_log: Path = RUN_LOG_PATH) -> Path:
    now = at or datetime.now(timezone.utc)
    day = now.astimezone(CHINA).date().isoformat()
    content = render_report(read_audit(path), day, now=now,
                            failures=read_failures(day, run_log))
    target = directory / (day + ".md")
    directory.mkdir(parents=True, exist_ok=True)
    # Fail closed if one report already exists; Scheduler may re-trigger within
    # 23:10-23:59, but must not silently rewrite an audited report.
    try:
        with target.open("x", encoding="utf-8") as stream:
            stream.write(content)
    except FileExistsError:
        pass
    return target


if __name__ == "__main__":
    import argparse
    cli = argparse.ArgumentParser(description="校序微信同步证据日报（本地、无联网）")
    cli.add_argument("command", choices=("render",))
    args = cli.parse_args()
    output = write_report()
    print("已生成或保留本地校序微信候选同步日报：" + str(output))
