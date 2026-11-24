"""Reduce an already-owned, already-downloaded toolkit score JSON to a local import.

Purely offline. Never logs in, contacts an API, reads Android app storage,
replays a credential, or changes a running result. Raw input is not retained.
"""
from __future__ import annotations

import argparse
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path

SCHEMA = "xiaoxu/campus-run-toolkit-safe-v1"
MAX_BYTES = 256_000
MAX_METERS = 10_000_000
MAX_COUNT = 1_000_000


def _integer_field(data: dict, name: str, maximum: int) -> int | None:
    value = data.get(name)
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise ValueError("invalid_" + name)
    raw = str(value)
    if not raw or len(raw) > 30 or raw[0] == "-" or "e" in raw.lower():
        raise ValueError("invalid_" + name)
    try:
        number = Decimal(raw)
        if not number.is_finite() or number != number.to_integral_value():
            raise ValueError("non_integral_" + name)
        result = int(number)
    except (InvalidOperation, OverflowError) as exc:
        raise ValueError("invalid_" + name) from exc
    if not 0 <= result <= maximum:
        raise ValueError("out_of_range_" + name)
    return result


def extract_score_only(raw_text: str) -> dict:
    """Return only six whitelisted semester fields and an explicit meter unit."""
    if not 2 <= len(raw_text.encode("utf-8")) <= MAX_BYTES:
        raise ValueError("invalid_input_size")
    root = json.loads(raw_text)
    if not isinstance(root, dict):
        raise ValueError("invalid_root")
    score = root.get("score", root)
    if not isinstance(score, dict):
        raise ValueError("invalid_score")
    completed = score.get("semester_completed")
    if not isinstance(completed, dict) or type(completed.get("error")) is not int or completed["error"] != 10000:
        raise ValueError("semester_not_successful")
    data = completed.get("data")
    if not isinstance(data, dict) or len(data) > 150:
        raise ValueError("missing_semester_summary")

    semester = {}
    label = data.get("sname")
    if label is not None:
        if not isinstance(label, str) or len(label.strip()) > 80 or any(ch in label for ch in "\n\r"):
            raise ValueError("invalid_semester_label")
        if label.strip():
            semester["sname"] = label.strip()
    for name, maximum in (
        ("semesterDis", MAX_METERS),
        ("semesterValidDis", MAX_METERS),
        ("semesterCount", MAX_COUNT),
        ("semesterValidCount", MAX_COUNT),
    ):
        value = _integer_field(data, name, maximum)
        if value is not None:
            semester[name] = value

    if not any(name in semester for name in
               ("semesterDis", "semesterValidDis", "semesterCount", "semesterValidCount")):
        raise ValueError("no_semester_metrics")
    if ("semesterDis" in semester and "semesterValidDis" in semester and
            semester["semesterValidDis"] > semester["semesterDis"]):
        raise ValueError("credited_exceeds_total")
    if ("semesterCount" in semester and "semesterValidCount" in semester and
            semester["semesterValidCount"] > semester["semesterCount"]):
        raise ValueError("effective_count_exceeds_total")

    return {
        "schema": SCHEMA,
        "unit": "m",
        "semester_completed": {
            "error": 10000,
            "data": semester,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Offline sanitizer of an existing personal toolkit score/all JSON."
    )
    parser.add_argument("--input", required=True, type=Path, help="Existing local score/all JSON")
    parser.add_argument("--output", required=True, type=Path, help="New sanitized import JSON")
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error("output must be a different new file")
    with args.input.open("rb") as source:
        raw = source.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        parser.error("source file exceeds 256KB")
    result = extract_score_only(raw.decode("utf-8-sig"))
    # Exclusive create means an old file, especially a raw credentials export,
    # cannot be accidentally overwritten or mixed with sanitized output.
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as out:
        json.dump(result, out, ensure_ascii=False, indent=2)
        out.write("\n")
    print("Sanitized score-only JSON created. No tokens/history/rank copied.")


if __name__ == "__main__":
    main()
