"""No accounts, no network, no login; Python import and method surface probe.

The upstream read-only modules are copied from the pinned MIT commit by CI.
Nothing calls the upstream constructors (which initialize network/crypto state).
"""


def verify_only():
    from core.client import BaseNetSecClient
    from profile.client import ProfileClient
    from policy.client import PolicyClient
    from history.client import HistoryClient

    required = (
        (ProfileClient, ("get_user_info",)),
        (PolicyClient, ("get_semester_info", "get_semester_summary")),
        (HistoryClient, ("get_run_records",)),
    )
    if not callable(getattr(BaseNetSecClient, "execute_request", None)):
        raise AssertionError("OSS client execute_request not importable")
    for clazz, methods in required:
        for name in methods:
            if not callable(getattr(clazz, name, None)):
                raise AssertionError("OSS read method missing")
    return "PINNED_OSS_READ_IMPORT_OK"
