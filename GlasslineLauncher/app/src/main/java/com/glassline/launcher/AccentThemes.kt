package com.glassline.launcher

object AccentThemes {
    fun styleFor(index: Int): Int = when (index.coerceIn(0, 5)) {
        1 -> R.style.Theme_Glassline_AccentViolet
        2 -> R.style.Theme_Glassline_AccentTeal
        3 -> R.style.Theme_Glassline_AccentRose
        4 -> R.style.Theme_Glassline_AccentAmber
        5 -> R.style.Theme_Glassline_AccentEmerald
        else -> R.style.Theme_Glassline_AccentBlue
    }
    val names = listOf("Blue", "Violet", "Teal", "Rose", "Amber", "Emerald")
}
