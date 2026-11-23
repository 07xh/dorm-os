package io.github.x07xh.xiaoxu.data

/**
 * School-owned H5 navigation only. This is NOT an API adapter or a claim that
 * native balance/usage reads are verified. Do not append a school token, room,
 * student identifier or other personal data to these URLs.
 */
enum class CampusUtilityKind(val title: String, val route: String) {
    WATER("水费", "/pages_payment/waterFees/waterFees"),
    ELECTRIC("电费", "/pages_payment/electrictyFees/electrictyFees")
}

object CampusUtilitiesPortal {
    const val HOME = "https://mobile-ykt.ncwu.edu.cn/?orgId=2"

    fun officialUrl(kind: CampusUtilityKind): String = "$HOME#${kind.route}"
}
