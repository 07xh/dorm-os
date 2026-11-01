package xyz.dormos.nfc

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
