package io.github.x07xh.xiaoxu.data

import java.security.MessageDigest

/**
 * Session-scoped visibility for manually entered running summaries.
 *
 * This is NOT an identification of a school account. A confirmed 校序 owner
 * session is only a privacy boundary for the local manual-only feature.
 * When an owner token exists but has not been authenticated, hide all
 * saved summaries instead of downgrading them to the unowned local profile.
 *
 * Binding is already the SHA-256 fingerprint of an entropy-rich owner token;
 * hash again with domain separation before using it as an on-device key.
 * Neither the token nor the raw binding is persisted by this module.
 */
fun campusRunVisibleScope(
    authenticated: Boolean,
    ownerSessionBinding: String?
): String? {
    if (ownerSessionBinding == null) return if (authenticated) null else "local"
    if (!authenticated || !Regex("^[A-Za-z0-9_-]{43}$").matches(ownerSessionBinding)) {
        return null
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(
        ("xiaoxu-campus-run-owner-v2|" + ownerSessionBinding).toByteArray(Charsets.UTF_8)
    ).joinToString("") { "%02x".format(it) }
    return "owner_$digest"
}
