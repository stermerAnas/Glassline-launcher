
package com.glassline.launcher

import android.content.SharedPreferences
import java.text.Normalizer
import java.util.Locale

/**
 * Small, deterministic helpers shared by the launcher and its tests.
 */
object LauncherLogic {

    /**
     * Trims whitespace, ignores case, and normalizes accented characters.
     *
     * For example, "  Café " and "cafe\u0301" normalize identically.
     */
    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        )

        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Matches a query against either an app's visible label or package name.
     */
    fun matchesQuery(
        label: String,
        packageName: String,
        query: String
    ): Boolean {
        val normalizedQuery = normalize(query)

        if (normalizedQuery.isBlank()) {
            return true
        }

        return normalize(label).contains(normalizedQuery) ||
            normalize(packageName).contains(normalizedQuery)
    }

    /**
     * Keeps the launcher grid within its supported column range.
     */
    fun parseColumns(value: Int): Int = value.coerceIn(3, 6)

    /**
     * Persists hidden app package names without retaining a mutable set.
     */
    fun saveHiddenApps(
        preferences: SharedPreferences,
        packageNames: Set<String>
    ) {
        preferences.edit()
            .putStringSet("hidden_apps", packageNames.toSet())
            .apply()
    }

    /**
     * Reads hidden app package names as a defensive copy.
     */
    fun readHiddenApps(
        preferences: SharedPreferences
    ): Set<String> {
        return preferences.getStringSet("hidden_apps", emptySet())
            ?.toSet()
            ?: emptySet()
    }
}
