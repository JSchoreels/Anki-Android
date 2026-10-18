// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.heatmap

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Review heatmap for the whole collection, like the desktop "Review Heatmap" add-on: one calendar
 * year, greens for reviews done, greys for cards due on future days, a stats line below.
 *
 * Days are Anki days (the collection's day rollover), as offsets from today: 0 is today, -1
 * yesterday, 1 tomorrow.
 */
class HeatmapData(
    val today: LocalDate,
    /** Reviews per past day (offset <= 0). */
    val past: Map<Int, Int>,
    /** Cards due per day (offset >= 0; overdue and learning cards count as today). */
    val future: Map<Int, Int>,
) {
    private val studied = past.filter { (offset, count) -> offset <= 0 && count > 0 }

    val pastAverage: Double = studied.values.average().takeUnless { it.isNaN() } ?: 1.0
    val futureAverage: Double =
        future
            .filter { (offset, count) -> offset > 0 && count > 0 }
            .values
            .average()
            .takeUnless { it.isNaN() } ?: 1.0

    /** Reviews per studied day. */
    val dailyAverage: Int = if (studied.isEmpty()) 0 else Math.round(studied.values.sum().toDouble() / studied.size).toInt()

    /** Studied days as a share of the days since the first review. */
    val daysLearnedPercent: Int =
        if (studied.isEmpty()) 0 else Math.round(100.0 * studied.size / (1 - studied.keys.min())).toInt()

    val longestStreak: Int
    val currentStreak: Int

    init {
        var longest = 0
        var run = 0
        if (studied.isNotEmpty()) {
            for (offset in studied.keys.min()..0) {
                run = if (offset in studied) run + 1 else 0
                longest = maxOf(longest, run)
            }
        }
        longestStreak = longest
        // a streak is not broken by today not being studied yet
        var current = 0
        var offset = if (0 in studied) 0 else -1
        while (offset in studied) {
            current++
            offset--
        }
        currentStreak = current
    }

    /** Same days and counts: nothing to redraw. */
    fun sameAs(other: HeatmapData?) = other != null && other.today == today && other.past == past && other.future == future

    companion object {
        /** The days before today counted by [past], and the reviews before today they counted. */
        private class EarlierDays(
            val dayStart: Long,
            val reviews: Long,
            val newest: Long,
            val days: Map<Int, Int>,
        )

        private var earlierDays: EarlierDays? = null

        /**
         * Reviews per past Anki day, as offsets from today (0 = today). The days before today are
         * counted once, and again only after the day rolls over or reviews before today come or go
         * (a sync, a deletion); each call counts just today's. Counting every day each time took
         * 1.3 s for a 3.8M-review history on a Tab S7+, after every review.
         */
        fun past(col: Collection): Map<Int, Int> {
            val dayStart = col.sched.dayCutoff - 86_400 // start of today's Anki day
            val (reviews, newest) =
                col.db.query("select count(), coalesce(max(id), 0) from revlog where id < ?", dayStart * 1000).use {
                    it.moveToFirst()
                    it.getLong(0) to it.getLong(1)
                }
            val earlier =
                synchronized(this) { earlierDays }
                    ?.takeIf { it.dayStart == dayStart && it.reviews == reviews && it.newest == newest }
                    ?.days
                    ?: countEarlierDays(col, dayStart).also {
                        synchronized(this) { earlierDays = EarlierDays(dayStart, reviews, newest, it) }
                    }
            val today =
                col.db.queryScalar(
                    "select count() from revlog where id >= ? and id < ? and ease > 0",
                    dayStart * 1000,
                    (dayStart + 86_400) * 1000,
                )
            return if (today > 0) earlier + (0 to today) else earlier
        }

        private fun countEarlierDays(
            col: Collection,
            dayStart: Long,
        ): Map<Int, Int> {
            val shift = 86_400L * 100_000 // keeps the division flooring for past days
            val past = HashMap<Int, Int>()
            col.db
                .query(
                    "select (id / 1000 - ? + ?) / 86400, count() from revlog where ease > 0 and id < ? group by 1",
                    dayStart,
                    shift,
                    dayStart * 1000,
                ).use {
                    while (it.moveToNext()) past[(it.getLong(0) - 100_000).toInt()] = it.getInt(1)
                }
            return past
        }

        suspend fun load(): HeatmapData =
            withCol {
                val dayStart = sched.dayCutoff - 86_400
                val past = past(this)
                val today = sched.today
                val future = HashMap<Int, Int>()
                // review and day-learning cards: due is a day number
                db.query("select due, count() from cards where queue in (2, 3) and due <= ? group by due", today + 400).use {
                    while (it.moveToNext()) {
                        val offset = maxOf(0, it.getInt(0) - today)
                        future[offset] = (future[offset] ?: 0) + it.getInt(1)
                    }
                }
                val learning = db.queryScalar("select count() from cards where queue = 1")
                if (learning > 0) future[0] = (future[0] ?: 0) + learning
                HeatmapData(
                    today = Instant.ofEpochSecond(dayStart).atZone(ZoneId.systemDefault()).toLocalDate(),
                    past = past,
                    future = future,
                )
            }
    }
}
