"""校序 Windows 微信采集桥接（个人授权场景）。

复用已安装 wechat-cli-plus 0.2.4 的 AppContext/resolve_chat_context/_query_messages。
本脚本不实现微信解密、不调用微信发送接口、不扫描未选择的群。
首次运行需在校序网站显式绑定电脑并选择微信群；消息筛选为保守规则，
不代表 AI 准确理解，更不代表官方学校作业状态。

运行环境：Python 3.12 虚拟环境，PYTHONPATH 仅包含微信工具 tool。
"""
from __future__ import annotations

import argparse
import base64
import ctypes
from ctypes import wintypes
from datetime import date, datetime, timedelta, timezone
import getpass
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import socket
import sqlite3
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

CHINA = timezone(timedelta(hours=8))
VERSION = "xiaoxu/wechat-candidates-v1"
DEFAULT_SITE = "https://dorm-os-dk9.pages.dev"
CONFIG = Path.home() / ".wechat-cli" / "xiaoxu-bridge.json"
STATE = Path.home() / ".wechat-cli" / "xiaoxu-sync-state.json"
MAX_CHAT = 36
MAX_ROWS_PER_CHAT = 1500
PAGE = 250
OVERLAP_SECONDS = 24 * 3600
BOOTSTRAP_SECONDS = 72 * 3600
TASK_WORDS = re.compile(r"作业|实验报告|课程报告|提交|上交|截止|ddl|考试|测验|互评|报名|签到|考核|作图|结课|打卡", re.I)
CLEAN_URL = re.compile(r"https?://\S+", re.I)
CLEAN_PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
CLEAN_LONG_ID = re.compile(r"(?<!\d)\d{8,}(?!\d)")
CLEAN_EMAIL = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")
DAY_ISO = re.compile(r"(?<!\d)(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})日?(?!\d)")
DAY_SHORT = re.compile(r"(?<!\d)(\d{1,2})月(\d{1,2})日")
ID = re.compile(r"^wxd_[A-Za-z0-9_-]{24}$")
TOKEN = re.compile(r"^wxu_[A-Za-z0-9_-]{43}$")


class BridgeError(Exception):
    pass


# Windows DPAPI: encrypt the pairing token and local HMAC key for the
# current Windows user. No plaintext token or full chat database in config.
class Blob(ctypes.Structure):
    _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]


def protect_windows(raw: bytes, decrypt: bool = False) -> bytes:
    if sys.platform != "win32":
        raise BridgeError("设备凭据保护仅支持 Windows；请勿改为明文存储")
    data = (ctypes.c_ubyte * len(raw)).from_buffer_copy(raw)
    src = Blob(len(raw), ctypes.cast(data, ctypes.POINTER(ctypes.c_ubyte)))
    dest = Blob()
    lib = ctypes.windll.crypt32
    proc = lib.CryptUnprotectData if decrypt else lib.CryptProtectData
    if not proc(ctypes.byref(src), None, None, None, None, 0x01, ctypes.byref(dest)):
        raise BridgeError("Windows 设备凭据保护/读取失败")
    try:
        return ctypes.string_at(dest.pbData, dest.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(ctypes.cast(dest.pbData, ctypes.c_void_p))


def sealed(raw: bytes) -> str:
    return base64.b64encode(protect_windows(raw)).decode("ascii")


def opened(value: str) -> bytes:
    try:
        return protect_windows(base64.b64decode(value, validate=True), decrypt=True)
    except (ValueError, TypeError) as exc:
        raise BridgeError("设备凭据已损坏，请在校序撤销旧设备后重新绑定") from exc


def save_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    try:
        with tmp.open("w", encoding="utf-8") as f:
            json.dump(value, f, ensure_ascii=False, separators=(",", ":"))
        os.replace(tmp, path)
    finally:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass


def validate_site(raw: str) -> str:
    u = urllib.parse.urlsplit(raw.rstrip("/"))
    if u.scheme != "https" or not u.hostname or u.username or u.password or u.port not in (None, 443) or u.path or u.query or u.fragment:
        raise BridgeError("校序网址必须是 HTTPS 站点根地址")
    if u.hostname.lower() != "dorm-os-dk9.pages.dev":
        raise BridgeError("仅允许连接校序正式域名，拒绝向未知站点发送设备凭据")
    return "https://dorm-os-dk9.pages.dev"


def load_config(path: Path = CONFIG) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if value.get("schema") != "xiaoxu-wechat-bridge-v1":
            raise BridgeError("本地设置格式不受支持")
        if not ID.fullmatch(value["deviceId"]):
            raise BridgeError("设备 ID 不合法")
        validate_site(value["site"])
        chats = value["chats"]
        if not isinstance(chats, list) or not 1 <= len(chats) <= MAX_CHAT or len(set(chats)) != len(chats):
            raise BridgeError("必须明确选择 1 至 36 个不同的课程群")
        if any(not isinstance(x, str) or not x.strip() or len(x) > 80 for x in chats):
            raise BridgeError("所选群名无效")
        return value
    except (FileNotFoundError, KeyError, ValueError, TypeError) as exc:
        raise BridgeError("尚未配置电脑。请在校序绑定设备后运行 setup") from exc


def fingerprint(secret: bytes, *parts: object) -> str:
    value = "\x1e".join(str(x) for x in parts).encode("utf-8")
    return base64.urlsafe_b64encode(hmac.new(secret, value, hashlib.sha256).digest()).rstrip(b"=").decode("ascii")


def contract_text(value: str, max_units: int) -> str:
    """Match the Cloudflare JS contract's UTF-16 unit limit, not Python len().

    JavaScript counts emoji and other non-BMP glyphs as two string units.
    Do not retain C0 control characters rejected by the server.
    """
    value = re.sub(r"[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", " ", value)
    value = re.sub(r"\\s+", " ", value).strip()
    remaining = max_units
    output = []
    for char in value:
        units = 2 if ord(char) > 0xFFFF else 1
        if units > remaining:
            break
        output.append(char)
        remaining -= units
    return "".join(output).strip()


def due_date(text: str, sent_ts: int) -> dict:
    sent = datetime.fromtimestamp(sent_ts, CHINA).date()
    for regex in (DAY_ISO, DAY_SHORT):
        m = regex.search(text)
        if not m:
            continue
        try:
            if regex is DAY_ISO:
                year, month, day = (int(p) for p in m.groups())
            else:
                month, day = (int(p) for p in m.groups())
                year = sent.year
            parsed = date(year, month, day)
            if regex is DAY_SHORT and (sent - parsed).days > 30:
                parsed = date(year + 1, month, day)
            return {"precision": "date", "date": parsed.isoformat()}
        except ValueError:
            continue
    return {"precision": "unknown"}


def classify(text: str, sent_ts: int) -> dict | None:
    # Only task-like text messages are considered. Never upload full chat,
    # attachments, sender names, phone numbers, cookies or external URLs.
    if not TASK_WORDS.search(text):
        return None
    cleaned = CLEAN_URL.sub("[链接]", text)
    cleaned = CLEAN_EMAIL.sub("[邮箱]", cleaned)
    cleaned = CLEAN_PHONE.sub("[电话]", cleaned)
    cleaned = CLEAN_LONG_ID.sub("[编号]", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if len(cleaned) < 4:
        return None
    if re.search(r"考试|测验|考核", text):
        kind = "exam"
    elif re.search(r"报名", text):
        kind = "signup"
    elif re.search(r"签到|打卡", text):
        kind = "activity"
    else:
        kind = "homework"
    return {"kind": kind, "title": contract_text(cleaned, 120), "deadline": due_date(text, sent_ts)}


def extract_groups(config: dict, secret: bytes, cursors: dict, *,
                   reader=None, now_ts=None) -> tuple[list[dict], dict, list[str]]:
    now_ts = int(time.time()) if now_ts is None else int(now_ts)
    if reader is None:
        # Reuse wechat-cli-plus ONLY. It has already been installed and tested
        # by the user; do not implement a second database reader.
        from wechat_cli.core.context import AppContext
        from wechat_cli.core.messages import (
            _iter_table_contexts, _query_messages, decompress_content,
            _parse_message_content, resolve_chat_context,
        )
        app = AppContext()
        def reader(chat, cutoff):
            ctx = resolve_chat_context(chat, app.msg_db_keys, app.cache, app.decrypted_dir)
            if not ctx or not ctx["is_group"] or ctx["display_name"] != chat or not ctx["db_path"]:
                raise BridgeError("所选群不存在、名称不唯一或没有可读历史；请在电脑上核对群名称")
            total = 0
            for table in _iter_table_contexts(ctx):
                with sqlite3.connect(table["db_path"]) as con:
                    offset = 0
                    while True:
                        rows = _query_messages(con, table["table_name"], start_ts=cutoff,
                                               limit=PAGE, offset=offset)
                        total += len(rows)
                        if total > MAX_ROWS_PER_CHAT:
                            raise BridgeError("某个群消息量超过本轮安全上限；未推进游标，避免漏消息")
                        for local_id, local_type, ts, sender_id, content, ct in rows:
                            if int(local_type) & 0xFFFFFFFF != 1:
                                continue
                            raw = decompress_content(content, ct)
                            if not isinstance(raw, str):
                                continue
                            _, body = _parse_message_content(raw, local_type, True)
                            # Stable source identity: installed reader table + message ID.
                            # Include the stable source shard path privately; local IDs
                            # may repeat across separate message_*.db partitions.
                            shard = str(table["db_path"]) + "/" + str(table["table_name"])
                            yield (shard, int(local_id), int(ts), body)
                        if len(rows) < PAGE:
                            break
                        offset += len(rows)
    candidates, next_cursors, summary = [], dict(cursors), []
    for chat in config["chats"]:
        old = int(cursors.get(chat, 0))
        since = max(0, old - OVERLAP_SECONDS) if old else now_ts - BOOTSTRAP_SECONDS
        total = 0
        max_ts = old
        for table_name, local_id, ts, text in reader(chat, since):
            total += 1
            if total > MAX_ROWS_PER_CHAT:
                raise BridgeError("群消息量超过本轮上限；请缩小首次同步范围")
            max_ts = max(max_ts, ts)
            task = classify(text, ts)
            if task is None:
                continue
            group_key = fingerprint(secret, "chat", chat)
            msg_key = fingerprint(secret, "msg", chat, table_name, local_id, ts)
            candidate_key = fingerprint(secret, "candidate", msg_key, 0)
            candidates.append({
                "messageKey": msg_key, "candidateKey": candidate_key,
                "chatKey": group_key, "chatLabel": contract_text(chat, 80),
                "kind": task["kind"], "title": task["title"],
                "sentAt": datetime.fromtimestamp(ts, timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
                "deadline": task["deadline"],
            })
        next_cursors[chat] = max_ts or now_ts
        summary.append(f"已选群：扫描 {total} 条文本记录")
    # Dedup when the upstream reader references the same logical message twice.
    unique = {item["candidateKey"]: item for item in candidates}
    return sorted(unique.values(), key=lambda v: (v["sentAt"], v["candidateKey"])), next_cursors, summary


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise BridgeError("校序服务器返回跳转；为防止泄漏设备凭据，已停止同步")


def submit(site: str, token: str, payload: dict) -> dict:
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if len(body) > 60000:
        raise BridgeError("候选批次过大；请减少单批任务数量")
    opener = urllib.request.build_opener(NoRedirect())
    req = urllib.request.Request(
        validate_site(site) + "/api/v1/wechat/ingest", data=body, method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json",
                 "Accept": "application/json",
                 "User-Agent": "Xiaoxu-Desktop-Connector/1.0"},
    )
    try:
        with opener.open(req, timeout=20) as response:
            if response.status != 202:
                raise BridgeError("云端尚未接受本批数据")
            result = json.load(response)
    except urllib.error.HTTPError as exc:
        # The Cloudflare edge and the private Academic OS API can both reply
        # HTTP 403. Only read a bounded, allowlisted machine error code;
        # never print raw response HTML, chat contents or device credentials.
        known = {
            "device_mismatch": "设备 ID 与授权凭证不属于同一台设备；请在校序撤销旧设备并重新绑定。",
            "forbidden_origin_or_format": "校序接口拒绝上传格式；请更新校序电脑同步脚本，无需重配群聊。",
            "device_unauthorized": "设备凭证无效或已撤销；请重新绑定电脑。",
            "database_unavailable": "校序云端数据服务暂不可用；无需重新配置群聊。",
        }
        code = None
        try:
            content_type = (exc.headers.get("Content-Type", "") if exc.headers else "").lower()
            if "application/json" in content_type:
                response_data = json.loads(exc.read(1024))
                if isinstance(response_data, dict) and isinstance(response_data.get("error"), str):
                    code = response_data["error"]
        except (ValueError, UnicodeError, OSError):
            pass
        if code in known:
            raise BridgeError(f"云端拒绝批次（HTTP {exc.code}，{code}）：{known[code]}") from None
        if exc.code == 422:
            # Never show raw server content; only expose allowlisted schema codes.
            schema_errors = {
                "batch_invalid", "schema_unsupported", "device_id_invalid",
                "batch_id_invalid", "captured_at_invalid", "batch_size_invalid",
                "record_invalid", "message_key_invalid", "candidate_key_invalid",
                "chat_key_invalid", "duplicate_candidate", "kind_invalid",
                "title_invalid", "chat_label_invalid", "excerpt_invalid",
                "sent_at_invalid", "sent_at_future", "deadline_invalid",
            }
            detail = code if code in schema_errors else "unknown_schema_error"
            raise BridgeError(
                f"云端字段校验失败（HTTP 422，{detail}）；"
                "保留原同步进度，请更新采集器后再试。"
            ) from None
        if exc.code == 403:
            raise BridgeError("云端拒绝批次（HTTP 403，非校序已知错误）；可能是网络或 Cloudflare 安全检查。无需重配群聊。") from None
        raise BridgeError(f"云端拒绝批次（HTTP {exc.code}）；请检查设备授权或云端服务。") from None
    except urllib.error.URLError as exc:
        # urllib hides DNS, TLS, proxy and timeout failures under the same
        # URLError. Report only a fixed category: raw errors can expose
        # proxy credentials, private hostnames or network details.
        reason = exc.reason
        if isinstance(reason, ssl.SSLCertVerificationError):
            message = "HTTPS 证书验证失败：请检查系统时间、证书及网络代理配置"
        elif isinstance(reason, socket.gaierror):
            message = "DNS 域名解析失败：电脑无法解析校序域名"
        elif isinstance(reason, TimeoutError):
            message = "HTTPS 连接超时：请检查电脑网络或代理"
        elif isinstance(reason, ConnectionRefusedError):
            message = "HTTPS 连接被拒绝：请检查网络或代理"
        elif isinstance(reason, OSError) and "tunnel connection failed" in str(reason).lower():
            message = "代理隧道连接失败：请检查电脑代理配置"
        else:
            message = "网络连接失败：无法到达校序服务，请检查网络或代理"
        raise BridgeError(message) from None
    if (result.get("reviewStatus") != "needs_review" or
            type(result.get("received")) is not int or
            result["received"] != len(payload.get("records", []))):
        raise BridgeError("云端确认数量或状态异常；未推进同步游标")
    return result


def setup() -> None:
    site = validate_site(input(f"校序网址 [{DEFAULT_SITE}]：").strip() or DEFAULT_SITE)
    device_id = input("校序网页中创建的设备 ID：").strip()
    if not ID.fullmatch(device_id):
        raise BridgeError("设备 ID 格式错误")
    token = getpass.getpass("一次性设备凭据（输入不显示）：").strip()
    if not TOKEN.fullmatch(token):
        raise BridgeError("设备凭据格式错误")
    print("逐个输入你允许同步的微信群名。请确保群名与微信显示完全一致；空行结束。")
    chats = []
    while len(chats) < MAX_CHAT:
        name = input(f"群 {len(chats)+1}：").strip()
        if not name:
            break
        if len(name) > 80 or name in chats:
            raise BridgeError("群名称重复或过长")
        chats.append(name)
    if not chats:
        raise BridgeError("至少选择一个群，不会默认读取全部微信")
    if CONFIG.exists():
        raise BridgeError("已存在桥接配置。请先在校序中撤销旧设备，再手动备份或移走本机配置")
    save_json(CONFIG, {
        "schema": "xiaoxu-wechat-bridge-v1", "site": site, "deviceId": device_id,
        "chats": chats, "tokenDpapi": sealed(token.encode("utf-8")),
        "hmacDpapi": sealed(secrets.token_bytes(32)),
    })
    print(f"本地配置完成：已选择 {len(chats)} 个群。设备凭据已由 Windows 保护；尚未上传任何消息。")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="校序 · 微信消息候选同步（复用 Wechat-Todo，不上传原文）")
    parser.add_argument("command", choices=("setup", "preview", "sync", "status", "verify"))
    args = parser.parse_args(argv)
    if args.command == "setup":
        setup()
        return 0
    config = load_config()
    if args.command == "status":
        state = read_state()
        print(f"已选 {len(config['chats'])} 个群；上次成功处理时间戳的群数：{len(state)}")
        return 0
    if args.command == "verify":
        # A credential-only heartbeat: no WhatsApp/WeChat message reader,
        # no selected chat inspection, no state cursor mutation.
        token = opened(config["tokenDpapi"]).decode("utf-8")
        if not TOKEN.fullmatch(token):
            raise BridgeError("本地设备凭据无效")
        secret = opened(config["hmacDpapi"])
        payload = {
            "schema": VERSION, "deviceId": config["deviceId"],
            "batchId": fingerprint(secret, "verify", secrets.token_urlsafe(18)),
            "capturedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "records": [],
        }
        submit(config["site"], token, payload)
        print("设备已通过校序云端身份验证；未读取或上传任何微信聊天，也未改变同步进度。")
        return 0
    secret = opened(config["hmacDpapi"])
    cursors = read_state()
    records, next_state, notes = extract_groups(config, secret, cursors)
    for note in notes:
        print(note)
    print(f"检测到 {len(records)} 条待审核候选。规则筛选可能有误，均需在校序人工确认。")
    if args.command == "preview":
        for item in records[:10]:
            print("· " + item["title"] + " [" + item["deadline"]["precision"] + "]")
        print("仅在本机预览，没有上传或推进游标。")
        return 0
    token = opened(config["tokenDpapi"]).decode("utf-8")
    if not TOKEN.fullmatch(token):
        raise BridgeError("设备凭据无效")
    # An empty scan must STILL reach the real server. Without this heartbeat,
    # "同步完成" could be printed while the device never authenticated once.
    # Server receives zero message bodies, updates the paired device's last_seen_at
    # and acknowledges received:0. A failure never advances local checkpoints.
    chunks = [records[i:i + 25] for i in range(0, len(records), 25)] or [[]]
    for chunk in chunks:
        if chunk:
            # Stable retry ID for candidate-containing batches.
            fingerprint_input = json.dumps(chunk, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
            batch_id = fingerprint(secret, "batch", fingerprint_input)
            captured_at = max(x["sentAt"] for x in chunk)
        else:
            # Heartbeats contain no messages; fresh identity avoids a prior
            # successful heartbeat with another timestamp being a conflict.
            batch_id = fingerprint(secret, "heartbeat", secrets.token_urlsafe(18))
            captured_at = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        payload = {"schema": VERSION, "deviceId": config["deviceId"],
                   "batchId": batch_id, "capturedAt": captured_at, "records": chunk}
        submit(config["site"], token, payload)
    # Advance only when the server has acknowledged every batch (or heartbeat).
    save_json(STATE, next_state)
    print("服务器已确认本次同步；请在校序「任务 → 微信待审核」中审核。"
          if records else "服务器已确认设备在线，本次没有待审核任务。")
    return 0


def read_state() -> dict:
    try:
        value = json.loads(STATE.read_text(encoding="utf-8"))
        if not isinstance(value, dict) or any(
            not isinstance(k, str) or not isinstance(v, int) or v < 0
            for k, v in value.items()
        ):
            raise BridgeError("本地同步游标损坏；未继续采集")
        return value
    except FileNotFoundError:
        return {}
    except (ValueError, TypeError) as exc:
        raise BridgeError("本地同步游标损坏；未继续采集") from exc


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BridgeError as exc:
        # Never print message bodies, tokens, internal account directories.
        print("校序微信同步：" + str(exc), file=sys.stderr)
        raise SystemExit(1)
