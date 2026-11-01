package xyz.dormos.nfc

internal const val TAPFLOW_TAB_COUNT = 3

/**
 * Visual order of the bottom navigation. Screen IDs are intentionally kept
 * stable for existing state and routing; animation direction uses this order.
 */
internal fun tabNavigationOrder(tab: Int): Int = when (tab) {
    0 -> 0 // Home
    2 -> 1 // Scenes
    1 -> 2 // Tools
    else -> tab
}

/** Reverse lookup for pager position; stable internal IDs remain unchanged. */
internal fun tabIdForPage(page: Int): Int = when (page) {
    0 -> 0
    1 -> 2
    2 -> 1
    else -> 0
}
