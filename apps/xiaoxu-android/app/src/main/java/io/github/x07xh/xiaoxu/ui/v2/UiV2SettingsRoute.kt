package io.github.x07xh.xiaoxu.ui.v2

/** Both toolbar and Android back use the same parent map; no duplicate route state. */
internal fun uiV2SettingsParent(route: String): String = when (route) {
    "licenses" -> "about"
    "notifications-course", "notifications-task" -> "notifications"
    else -> "home"
}
