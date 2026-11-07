"""Narrow, read-only facade over the upstream Sport World Campus Python clients.

Upstream reference (MIT): Inverx/com.zjwh.android_wh_physicalfitness,
protocol 7.3.92, reviewed at commit 412a2e28bed209201057bf8b943fcd0a0e2211c1.

The host must supply *already authorized* ProfileClient, PolicyClient and
HistoryClient instances from its trusted local runtime. This module never
logs in, obtains/extracts tokens, synthesizes device identifiers, uploads
tracks, submits running results, or initializes the upstream SportClient792
(which also loads unrelated write-capable modules).

Responses are candidates until a real user-account and official-page
comparison establishes field meaning and units.
"""
from __future__ import annotations

import hashlib
import hmac
from dataclasses import dataclass
from typing import Any, Callable, Mapping
from urllib.parse import urlsplit


class ReadOnlyAccessError(Exception):
    """Safe, non-sensitive error; no credentials or response bodies."""


class SchemaNotVerified(Exception):
    """No trustworthy upstream business result or pagination evidence."""


_ALLOWED = frozenset({
    ("GET", "run.gxapp.iydsj.com", "/api/v70390/user/info"),
    ("POST", "run.gxapp.iydsj.com", "/api/v41/running/getPersonalSemesterInfo"),
    ("POST", "run.gxapp.iydsj.com", "/api/v55/runnings/recordssummary/semester"),
    ("POST", "run.gxapp.iydsj.com", "/api/v70230/runnings/records"),
    ("POST", "run.gxapp.iydsj.com", "/api/v70320/runnings/get_one_record"),
})
_SUMMARY_FIELDS = frozenset({
    "sname", "semesterDis", "semesterValidDis",
    "semesterCount", "semesterValidCount",
})
_SEMESTER_INFO_FIELDS = frozenset({
    "semesterName", "targetDistance", "goalDistance",
    "totalDistance", "validCount",
})
_RECORD_FIELDS = frozenset({
    "rrid", "id", "sportType", "totalDis", "validDis",
    "totalTime", "validTime", "startTime", "stopTime",
    "status", "complete", "isValid", "unCompleteReason",
    "calorie", "costTime",
})


def authorize_readonly_call(method: str, url: str) -> None:
    """Permit only previously observed or source-verified own-record reads."""
    parts = urlsplit(url)
    if (
        method.upper() != method
        or parts.scheme != "https"
        or parts.username is not None
        or parts.password is not None
        or parts.port not in (None, 443)
        or parts.query
        or parts.fragment
        or (method, parts.hostname, parts.path) not in _ALLOWED
    ):
        raise ReadOnlyAccessError("read_endpoint_not_allowed")


def restrict_upstream_client(client: Any) -> Any:
    """Guard the upstream execute_request *before* exposing its read methods.

    Retains the open-source library's original auth/response processing,
    while preventing accidental use of unrelated endpoints by this facade.
    The caller owns the already-authorized session. No new credentials are
    generated or persisted here.
    """
    execute: Callable[..., Any] = getattr(client, "execute_request")

    def readonly_execute(
        method: str,
        url: str,
        body_plain: Any = None,
        custom_headers: Any = None,
        sign_params: Any = None,
        timeout: int = 20,
    ) -> Any:
        authorize_readonly_call(method, url)
        if custom_headers is not None or sign_params is not None:
            raise ReadOnlyAccessError("header_override_not_allowed")
        if not isinstance(timeout, int) or not 1 <= timeout <= 30:
            raise ReadOnlyAccessError("invalid_timeout")
        return execute(
            method, url, body_plain=body_plain,
            custom_headers=None, sign_params=None, timeout=timeout,
        )

    client.execute_request = readonly_execute
    return client


def _business_result(value: Any) -> Any:
    if not isinstance(value, dict):
        raise SchemaNotVerified("unexpected_upstream_response")
    if set(("r", "s", "v")).issubset(value):
        raise SchemaNotVerified("opaque_response_is_not_business_data")
    if value.get("error") != 10000 or "data" not in value:
        raise SchemaNotVerified("unverified_business_status")
    return value["data"]


def _candidate_fields(value: Mapping[str, Any], allowed: frozenset[str]) -> dict[str, Any]:
    return {
        key: data for key, data in value.items()
        if key in allowed and (data is None or type(data) in (int, float, str, bool))
    }


def _history_rows(data: Any) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    metadata: dict[str, Any] = {}
    if isinstance(data, dict):
        for key in ("hasNext", "total", "pages", "pageNum", "pageSize"):
            if key in data:
                metadata[key] = data[key]
        data = data.get("recordList", data.get("records", data.get("list")))
    if not isinstance(data, list) or len(data) > 100:
        raise SchemaNotVerified("invalid_history_rows")
    rows: list[dict[str, Any]] = []
    for item in data:
        if not isinstance(item, dict):
            raise SchemaNotVerified("invalid_history_row")
        cleaned = _candidate_fields(item, _RECORD_FIELDS)
        if not ("rrid" in cleaned or "id" in cleaned):
            raise SchemaNotVerified("history_record_without_id")
        rows.append(cleaned)
    return rows, metadata


def _pagination(
    metadata: Mapping[str, Any], page: int, page_size: int
) -> tuple[bool | None, str | None]:
    """Unknown termination is never silently treated as a complete history."""
    flag = metadata.get("hasNext")
    if type(flag) is bool:
        return (not flag), (str(page + 1) if flag else None)
    pages = metadata.get("pages")
    if type(pages) is int and 1 <= pages <= 100_000:
        last = page >= pages
        return last, (None if last else str(page + 1))
    total = metadata.get("total")
    if type(total) is int and 0 <= total <= 10_000_000:
        last = page * page_size >= total
        return last, (None if last else str(page + 1))
    return None, None


@dataclass(frozen=True)
class ReadOnlySession:
    """Provided from an already legitimate, local upstream login session."""

    uid: str
    fingerprint_key: bytes

    def __post_init__(self) -> None:
        if not (self.uid.isdecimal() and 1 <= len(self.uid) <= 24
                and int(self.uid) > 0):
            raise ReadOnlyAccessError("invalid_expected_subject")
        if len(self.fingerprint_key) < 32:
            raise ReadOnlyAccessError("missing_local_fingerprint_key")

    def fingerprint(self) -> str:
        return hmac.new(
            self.fingerprint_key,
            ("sport-world-campus:" + self.uid).encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()


class SportWorldReadOnlyBridge:
    """Thin orchestration layer for the upstream *read* methods only.

    Inject upstream classes' ProfileClient/PolicyClient/HistoryClient objects,
    not their broader SportClient792 or login/running/telemetry modules.
    """

    def __init__(
        self, profile_client: Any, policy_client: Any,
        history_client: Any, session: ReadOnlySession
    ) -> None:
        if len({id(profile_client), id(policy_client), id(history_client)}) != 3:
            raise ReadOnlyAccessError("clients_must_be_separate")
        self.profile = restrict_upstream_client(profile_client)
        self.policy = restrict_upstream_client(policy_client)
        self.history = restrict_upstream_client(history_client)
        self.session = session

    def current_subject_fingerprint(self) -> str:
        profile = self.profile.get_user_info()
        actual = getattr(profile, "uid", None)
        if type(actual) not in (str, int) or str(actual) != self.session.uid:
            raise ReadOnlyAccessError("authorized_subject_mismatch")
        return self.session.fingerprint()

    def read_semester(self) -> dict[str, Any]:
        subject = self.current_subject_fingerprint()
        summary = _business_result(self.policy.get_semester_summary())
        if not isinstance(summary, dict):
            raise SchemaNotVerified("invalid_semester_summary")
        fields = {
            "summary." + key: value
            for key, value in _candidate_fields(summary, _SUMMARY_FIELDS).items()
        }
        # Reuse the upstream's existing getPersonalSemesterInfo reader, but
        # consume ONLY its raw fields: its display model defaults absent keys
        # to zero, which is not evidence of a zero official running distance.
        info = self.policy.get_semester_info()
        raw = getattr(info, "raw", None)
        if isinstance(raw, dict):
            fields.update({
                "info." + key: value
                for key, value in _candidate_fields(
                    raw, _SEMESTER_INFO_FIELDS
                ).items()
            })
        if not fields:
            raise SchemaNotVerified("no_verified_candidate_fields")
        if self.current_subject_fingerprint() != subject:
            raise ReadOnlyAccessError("authorized_subject_changed")
        return {
            "subjectFingerprint": subject,
            "fields": fields,
            "verifiedFieldSemantics": False,
        }

    def read_history_page(self, cursor: str | None = None, page_size: int = 20) -> dict[str, Any]:
        if cursor is None:
            page = 1
        elif cursor.isdecimal() and 1 <= len(cursor) <= 3 and 1 <= int(cursor) <= 100:
            page = int(cursor)
        else:
            raise SchemaNotVerified("invalid_history_cursor")
        if type(page_size) is not int or not 1 <= page_size <= 50:
            raise SchemaNotVerified("invalid_page_size")
        subject = self.current_subject_fingerprint()
        data = _business_result(
            self.history.get_run_records(
                page_num=page, page_size=page_size, sid=0, sport_type=1
            )
        )
        records, metadata = _history_rows(data)
        is_last, next_cursor = _pagination(metadata, page, page_size)
        if self.current_subject_fingerprint() != subject:
            raise ReadOnlyAccessError("authorized_subject_changed")
        return {
            "subjectFingerprint": subject,
            "recordsPayload": records,
            "nextCursor": next_cursor,
            "isLastPage": is_last,
            "paginationVerified": is_last is not None,
            "verifiedFieldSemantics": False,
        }
