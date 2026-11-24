"""Offline schema adapter for the owner's archived ydsj_toolkit.zip (2026-11-17).

This module has ZERO networking, credentials, login, device binding, ranking,
exercise creation or upload capabilities. The archived toolkit's live_sync.py
selected the first leaderboard row, mixed semesterDis with semesterValidDis,
and counted only the first history page: NONE of those shortcuts is adopted.

An upstream adapter must independently prove the legitimate logged-in subject
before supplying the subject fingerprint; the supplied fingerprint alone does
not authenticate imported JSON. No output is marked verified for production.
"""
from __future__ import annotations

import re
from typing import Any, Mapping

from sport_world_readonly_bridge import (
    SchemaNotVerified,
    _business_result,
    _candidate_fields,
    _history_rows,
    _pagination,
)

_SUBJECT = re.compile(r"^[a-f0-9]{64}$")
_SUMMARY_KEYS = frozenset({
    "sname", "semesterDis", "semesterValidDis",
    "semesterCount", "semesterValidCount",
})
# No guessed target/grade policy semantics. The archived toolkit has not shown
# a real owner's plaintext "goal" field and corresponding verified unit.
_INFO_KEYS = frozenset({
    "semesterName", "targetDistance", "goalDistance", "totalDistance",
    "validCount",
})


def _subject_fingerprint(subject: str) -> str:
    if not isinstance(subject, str) or not _SUBJECT.fullmatch(subject):
        raise SchemaNotVerified("unverified_subject_fingerprint")
    return subject


def normalize_toolkit_score(
    score: Mapping[str, Any], subject_fingerprint: str
) -> dict[str, Any]:
    """Normalize only explicit completed-semester metrics, never rankings.

    Expects archived json_client.py 'score' shape, not live_sync.py's flattened
    summary. In particular, semesterDis and semesterValidDis stay distinct.
    """
    subject = _subject_fingerprint(subject_fingerprint)
    if not isinstance(score, Mapping):
        raise SchemaNotVerified("invalid_toolkit_score")
    completed = _business_result(score.get("semester_completed"))
    if not isinstance(completed, dict):
        raise SchemaNotVerified("invalid_toolkit_semester_data")
    fields = {
        "summary." + k: v
        for k, v in _candidate_fields(completed, _SUMMARY_KEYS).items()
    }
    info = score.get("semester_info")
    if info is not None:
        info_data = _business_result(info)
        if not isinstance(info_data, dict):
            raise SchemaNotVerified("invalid_toolkit_info_data")
        fields.update({
            "info." + k: v
            for k, v in _candidate_fields(info_data, _INFO_KEYS).items()
        })
    if not fields:
        raise SchemaNotVerified("no_toolkit_semester_fields")

    return {
        "bridgeSchemaVersion": 1,
        "bridgeKind": "semester",
        "subjectFingerprint": subject,
        "fields": fields,
        # This bool must be promoted only by a separate legitimate
        # source-identity, field unit and official App comparison gate.
        "verifiedFieldSemantics": False,
    }


def normalize_toolkit_history_page(
    response: Mapping[str, Any],
    subject_fingerprint: str,
    *,
    page: int = 1,
    page_size: int = 20,
) -> dict[str, Any]:
    """One historical page only: unknown pagination is explicitly incomplete.

    Archived history_cli.py gets a single page and may receive bare list
    'data'. A short/empty page cannot establish that records are complete.
    """
    subject = _subject_fingerprint(subject_fingerprint)
    if type(page) is not int or not 1 <= page <= 100:
        raise SchemaNotVerified("invalid_toolkit_page")
    if type(page_size) is not int or not 1 <= page_size <= 50:
        raise SchemaNotVerified("invalid_toolkit_page_size")
    data = _business_result(response)
    rows, meta = _history_rows(data)
    last, cursor = _pagination(meta, page, page_size)
    return {
        "bridgeSchemaVersion": 1,
        "bridgeKind": "history",
        "subjectFingerprint": subject,
        "recordsPayload": rows,
        "nextCursor": cursor,
        "isLastPage": last,
        "paginationVerified": last is not None,
        "verifiedFieldSemantics": False,
    }
