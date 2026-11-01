package xyz.dormos.nfc

/**
 * Bottom-navigation visual order. Stable internal tab IDs are retained so
 * existing routes and page-specific state are not affected.
 */
internal fun tabNavigationOrder(tab: Int): Int = when (tab) {
    0 -> 0 // Home
    2 -> 1 // Scenes
    1 -> 2 // Tools
    else -> tab
}

/**
 * Direction for click-triggered page transitions:
 * +1 means the target enters from the right, -1 from the left, 0 unchanged.
 */
internal fun tabTransitionDirection(fromTab: Int, toTab: Int): Int =
    tabNavigationOrder(toTab).compareTo(tabNavigationOrder(fromTab))
