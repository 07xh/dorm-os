package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/glass/GlassLensFrameIdentity.kt
// Original authors and contributors; package adapted.

/** Background refreshes may reuse an optical frame; new glyphs/geometry may not. */
internal data class GlassLensFrameIdentity(
    val geometry: GlassLensCaptureGeometry,
    val overlayRevision: Int
)

internal fun canRetainLensFrame(
    rendered: GlassLensFrameIdentity?,
    current: GlassLensFrameIdentity?,
    retainCompatibleBackground: Boolean
): Boolean = retainCompatibleBackground && rendered != null && rendered == current
