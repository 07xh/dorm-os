"""Local AI handoff for the existing Windows WeChat collector.

This is NOT another WeChat reader or an AI classifier. A compatible agent can
reuse syskuku/wechat-todo's Skill classification/reporting rules against the
already-authorized groups in the local, redacted snapshot. Only explicit,
schema-checked task decisions are uploaded through the existing device API.
"""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
import hmac
import json
from pathlib import Path

import collector

INPUT = Path.home() / ".wechat-cli" / "xiaoxu-ai-input.json"
DECISIONS = Path.home() / ".wechat-cli" / "xiaoxu-ai-decisions.json"
SNAPSHOT_SCHEMA = "xiaoxu/wechat-ai-local-v1"
DECISION_SCHEMA = "xiaoxu/wechat-ai-decisions-v1"
MAX_AGE = timedelta(hours=48)
MAX_TASKS = 5000
KINDS = {"homework", "exam", "activity", "signup", "other"}


def _error() -> None:
    # Never interpolate contents of an untrusted local file into logs.
    raise collector.BridgeError("AI 文件格式、来源或同步进度不匹配；未上传也未推进游标")


def _canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _signature(secret: bytes, unsigned: dict) -> str:
    return collector.fingerprint(secret, "ai-snapshot-v1", _canonical(unsigned))


def build_snapshot(config: dict, secret: bytes, cursors: dict, *, reader=None, now_ts=None) -> tuple[dict, list[str]]:
    """Produce selected-group inputs without advancing a single cursor."""
    messages = []
    _, next_state, notes = collector.extract_groups(
        config, secret, cursors, reader=reader, now_ts=now_ts, ai_messages=messages
    )
    # The collector's default rule classifier is intentionally not used as an
    # inclusion filter for the local AI message snapshot.
    seen = set()
    unique = []
    for row in messages:
        if row["messageKey"] not in seen:
            seen.add(row["messageKey"])
            unique.append(row)
    unsigned = {
        "schema": SNAPSHOT_SCHEMA,
        "createdAt": datetime.fromtimestamp(
            now_ts if now_ts is not None else datetime.now(timezone.utc).timestamp(), timezone.utc
        ).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "deviceId": config["deviceId"],
        "selectedGroups": list(config["chats"]),
        "baseCursors": dict(cursors),
        "nextCursors": next_state,
        "messages": unique,
    }
    return {**unsigned, "signature": _signature(secret, unsigned)}, notes


def prepare_ai(config: dict, secret: bytes, cursors: dict) -> None:
    snapshot, notes = build_snapshot(config, secret, cursors)
    collector.save_json(INPUT, snapshot)
    # Do not print message bodies, group names or HMAC material.
    print(f"已为 AI 整理准备 {len(snapshot['messages'])} 条本机脱敏消息；无云端上传，游标未推进。")
    print("请由兼容 Agent 参照原版 wechat-todo Skill 生成 xiaoxu-ai-decisions.json。")
    print("请确认 AI 服务商的数据处理政策；不要将本机文件提交 Git 或粘贴到聊天中。")


def _read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        _error()
    if not isinstance(value, dict):
        _error()
    return value


def _deadline(value: object) -> dict:
    if not isinstance(value, dict) or value.get("precision") not in ("unknown", "date", "exact"):
        _error()
    precision = value["precision"]
    if precision == "unknown":
        if set(value) != {"precision"}:
            _error()
        return {"precision": "unknown"}
    if set(value) != ({"precision", "date"} if precision == "date" else {"precision", "date", "at"}):
        _error()
    day = value["date"]
    if not isinstance(day, str) or len(day) != 10:
        _error()
    try:
        parsed_day = date.fromisoformat(day)
    except ValueError:
        _error()
    if parsed_day.isoformat() != day:
        _error()
    if precision == "date":
        return {"precision": "date", "date": day}
    stamp = value["at"]
    if not isinstance(stamp, str) or len(stamp) > 40:
        _error()
    try:
        parsed = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
    except ValueError:
        _error()
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        _error()
    if parsed.astimezone(collector.CHINA).date() != parsed_day:
        _error()
    return {
        "precision": "exact", "date": day,
        "at": parsed.isoformat(timespec="seconds"),
    }


def validate_decisions(config: dict, secret: bytes, cursors: dict, snapshot: dict,
                       output: dict, *, now=None) -> tuple[list[dict], dict]:
    if set(snapshot) != {
        "schema", "createdAt", "deviceId", "selectedGroups", "baseCursors",
        "nextCursors", "messages", "signature"
    } or snapshot["schema"] != SNAPSHOT_SCHEMA:
        _error()
    unsigned = {key: val for key, val in snapshot.items() if key != "signature"}
    if not isinstance(snapshot["signature"], str) or not hmac.compare_digest(
        snapshot["signature"], _signature(secret, unsigned)
    ):
        _error()
    if (snapshot["deviceId"] != config["deviceId"]
            or snapshot["selectedGroups"] != config["chats"]
            or snapshot["baseCursors"] != cursors
            or not isinstance(snapshot["nextCursors"], dict)
            or any(not isinstance(v, int) or isinstance(v, bool) or v < 0
                   for v in snapshot["nextCursors"].values())):
        _error()
    try:
        created = datetime.fromisoformat(snapshot["createdAt"].replace("Z", "+00:00"))
        current = now or datetime.now(timezone.utc)
        age = current - created
        if created.utcoffset() is None or not timedelta(0) <= age <= MAX_AGE:
            _error()
    except (ValueError, TypeError, OverflowError):
        _error()
    rows = snapshot["messages"]
    if not isinstance(rows, list) or len(rows) > collector.AI_MAX_MESSAGES:
        _error()
    source = {}
    for row in rows:
        if (not isinstance(row, dict) or set(row) != {
            "messageKey", "chatKey", "chatLabel", "sentAt", "text", "links"
        } or not isinstance(row["messageKey"], str)
                or not isinstance(row["links"], list) or row["messageKey"] in source):
            _error()
        source[row["messageKey"]] = row
    if (not isinstance(output, dict)
            or set(output) != {"schema", "snapshotSignature", "tasks"}
            or output["schema"] != DECISION_SCHEMA
            or output["snapshotSignature"] != snapshot["signature"]
            or not isinstance(output["tasks"], list)
            or len(output["tasks"]) > MAX_TASKS):
        _error()
    seen = set()
    records = []
    for decision in output["tasks"]:
        if not isinstance(decision, dict) or set(decision) != {
            "messageKey", "slot", "kind", "title", "deadline"
        }:
            _error()
        msg = decision["messageKey"]
        slot = decision["slot"]
        if (not isinstance(msg, str) or msg not in source
                or type(slot) is not int or not 0 <= slot <= 2
                or (msg, slot) in seen or decision["kind"] not in KINDS
                or not isinstance(decision["title"], str)):
            _error()
        seen.add((msg, slot))
        title = collector.contract_text(collector.redact_message(decision["title"]), 120)
        if len(title) < 4:
            _error()
        row = source[msg]
        records.append({
            "messageKey": msg,
            # Slot zero shares the existing rule-based identity, avoiding
            # duplicated first tasks if the user already ran manual sync.
            "candidateKey": collector.fingerprint(secret, "candidate", msg, slot),
            "chatKey": row["chatKey"], "chatLabel": row["chatLabel"],
            "kind": decision["kind"], "title": title,
            "sentAt": row["sentAt"], "deadline": _deadline(decision["deadline"]),
            "links": row["links"],
        })
    records.sort(key=lambda row: (row["sentAt"], row["candidateKey"]))
    return records, snapshot["nextCursors"]


def submit_ai(config: dict, secret: bytes, cursors: dict) -> None:
    snapshot = _read_json(INPUT)
    output = _read_json(DECISIONS)
    records, next_state = validate_decisions(config, secret, cursors, snapshot, output)
    # An empty AI decision set still sends a verified zero-record heartbeat.
    # Uploading uses the same DPAPI token, idempotent batches and D1 review
    # queue as the established deterministic collector.
    collector.upload_candidates(config, secret, records, next_state)
    for path in (INPUT, DECISIONS):
        try:
            path.unlink(missing_ok=True)
        except OSError:
            print("提示：同步已成功，但部分本地 AI 临时文件无法删除，请手动清理。")
    print(f"AI 任务候选已传输 {len(records)} 条；仍须本人在校序审核后才进入待办。")
