
package com.glassline.launcher

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp

/**
 * Stores lightweight app usage statistics and provides search-ranking scores.
 */
class AppUsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences(
        "glassline_usage",
        Context.MODE_PRIVATE
    )

    @Volatile
    private var snapshot: Map<String, Any?> = emptyMap()

    @Synchronized
    fun refreshFromDisk() {
        snapshot = prefs.all.toMap()
    }

    @Synchronized
    fun record(
        app: LauncherActivity.LaunchableApp,
        now: Long = System.currentTimeMillis()
    ) {
        val key = key(app)
        val labelKey = "label:${normalize(app.label)}"
        val countKey = "count:$key"

        val nextCount =
            ((snapshot[countKey] as? Int) ?: 0)
                .plus(1)
                .coerceAtMost(100_000)

        val nextLabelCount =
            ((snapshot[labelKey] as? Int) ?: 0)
                .plus(1)
                .coerceAtMost(100_000)

        snapshot = snapshot + mapOf(
            "last:$key" to now,
            countKey to nextCount,
            labelKey to nextLabelCount
        )

        prefs.edit()
            .putLong("last:$key", now)
            .putInt(countKey, nextCount)
            .putInt(labelKey, nextLabelCount)
            .apply()
    }

    fun score(
        app: LauncherActivity.LaunchableApp,
        now: Long = System.currentTimeMillis()
    ): Double {
        val values = snapshot
        val key = key(app)

        val last = values["last:$key"] as? Long ?: 0L
        val count = (values["count:$key"] as? Int ?: 0).toDouble()
        val labelCount =
            (values["label:${normalize(app.label)}"] as? Int ?: 0)
                .toDouble()

        val ageDays = if (last == 0L) {
            365.0
        } else {
            (now - last).coerceAtLeast(0L) / 86_400_000.0
        }

        val recency = if (last == 0L) {
            0.0
        } else {
            exp(-ageDays / 3.0)
        }

        val frequency =
            (count + labelCount * 0.25) * exp(-ageDays / 14.0)

        return recency * 4.0 + frequency
    }

    /**
     * Ranks search matches:
     * 3 = app name or package starts with the query
     * 2 = a whitespace-separated word starts with the query
     * 1 = the query occurs elsewhere in the name or package
     * 0 = no match
     *
     * Word-boundary ranking uses whitespace-separated words so that a
     * compound such as "calendarwidget" is not treated as a separate
     * "calendar" word. Substring matches still receive a score of 1.
     */
    fun queryMatchStrength(
        app: LauncherActivity.LaunchableApp,
        query: String
    ): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0

        val label = normalize(app.label)
        val packageName = normalize(app.packageName)

        // Highest priority: the complete searchable string starts with the query.
        if (
            label.startsWith(q) ||
            packageName.startsWith(q)
        ) {
            return 3
        }

        // Next: a distinct whitespace-separated word starts with the query.
        val labelWords = label.split(Regex("\\s+"))
        val packageWords = packageName.split(Regex("\\s+"))

        if (
            labelWords.any { it.startsWith(q) } ||
            packageWords.any { it.startsWith(q) }
        ) {
            return 2
        }

        // Lowest positive priority: a substring match.
        return if (
            label.contains(q) ||
            packageName.contains(q)
        ) {
            1
        } else {
            0
        }
    }

    private fun key(
        app: LauncherActivity.LaunchableApp
    ): String =
        "${app.packageName}/${app.activityName}@${app.userSerial}"

    private fun normalize(value: String): String =
        Normalizer.normalize(
            Normalizer.normalize(value.trim(), Normalizer.Form.NFC),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
