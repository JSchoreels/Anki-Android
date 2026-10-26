// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.heatmap

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.kuma3.FrameAdapter
import com.ichi2.anki.kuma3.Kuma3Cache
import com.ichi2.anki.kuma3.quietly
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.xp.XpChrome
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One row below the decks (DeckPicker puts it in a ConcatAdapter after the deck list): the
 * heatmap with its year/month buttons, caption and stats line. Reloads whenever the deck list
 * reloads its counts (on resume, after a sync).
 */
class HeatmapFooter(
    private val deckPicker: DeckPicker,
) : FrameAdapter() {
    private var data: HeatmapData? = null

    private val prefs = deckPicker.getSharedPreferences("heatmap", Context.MODE_PRIVATE)

    /** One month with large squares instead of the whole year; remembered between runs. */
    private var monthView = prefs.getBoolean("monthView", false)

    /** A day inside the year or month being shown; null for the current one. */
    private var shown: LocalDate? = null

    init {
        with(deckPicker) { viewModel.flowOfDecksReloaded.launchCollectionInLifecycleScope { reload() } }
    }

    private suspend fun reload() {
        // Settings > kuma3 > Review heatmap
        if (!com.ichi2.anki.kuma3.Kuma3Settings.heatmap) {
            if (data != null) {
                data = null
                @Suppress("NotifyDataSetChanged") // a single row
                notifyDataSetChanged()
            }
            return
        }
        quietly("heatmap") {
            val fresh = Kuma3Cache.get("heatmap") { HeatmapData.load() }
            if (fresh.sameAs(data)) return@quietly
            data = fresh
            @Suppress("NotifyDataSetChanged") // a single row
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = if (data == null) 0 else 1

    override fun onBindViewHolder(
        holder: FrameAdapter.Holder,
        position: Int,
    ) {
        val data = data ?: return
        val context = holder.frame.context
        val density = context.resources.displayMetrics.density
        val margin = (12 * density).toInt()
        val caption =
            TextView(context).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                alpha = 0.75f
                setPadding(0, margin / 2, 0, 0)
            }
        val grid = HeatmapGrid(context) { text -> caption.text = text }.apply { this.data = data }
        lateinit var zoom: TextView

        /** Draw the year or month around [shown] and name it under the grid. */
        fun showPeriod() {
            val day = shown ?: data.today
            if (monthView) {
                val month = YearMonth.from(day)
                grid.show(month.atDay(1), month.lengthOfMonth())
                caption.text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            } else {
                // kuma3: half a year, 18 weeks back and 8 ahead, instead of January to December (mostly empty)
                val first = day.minusWeeks(18)
                val last = day.plusWeeks(8)
                grid.show(first, (last.toEpochDay() - first.toEpochDay() + 1).toInt())
                val fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                caption.text = "${first.format(fmt)} – ${last.format(fmt)}"
            }
            zoom.text = deckPicker.getString(if (monthView) R.string.kuma3_heatmap_six_months else R.string.kuma3_heatmap_month)
        }

        /** < and > move one month in month view, half a year in the six-month view. */
        fun move(steps: Long) {
            val day = shown ?: data.today
            shown = if (monthView) day.withDayOfMonth(1).plusMonths(steps) else day.plusWeeks(26 * steps)
        }

        fun button(
            label: String,
            action: () -> Unit,
        ) = TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundColor(EMPTY)
            minWidth = (40 * density).toInt()
            setPadding(margin, margin / 3, margin, margin / 3)
            setOnClickListener {
                action()
                showPeriod()
            }
            XpChrome.pushButton(this) // kuma3: XP push buttons with the Anki XP day theme
        }
        zoom =
            button("") {
                monthView = !monthView
                prefs.edit().putBoolean("monthView", monthView).apply()
            }
        val buttons =
            LinearLayout(context).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, margin / 2)
                val spacing =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = (3 * density).toInt()
                        marginEnd = (3 * density).toInt()
                    }
                addView(button("<") { move(-1) }, spacing)
                addView(button(deckPicker.getString(R.string.kuma3_heatmap_today)) { shown = null }, spacing)
                addView(button(">") { move(1) }, spacing)
                addView(zoom, spacing)
            }
        showPeriod()

        holder.frame.removeAllViews()
        holder.frame.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(margin, margin * 2, margin, 0)
                addView(buttons)
                addView(grid)
                addView(caption)
                addView(
                    TextView(context).apply {
                        text = statsLine(data)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, margin / 2, 0, 0)
                    },
                )
            },
        )
    }

    /** "Daily average: 44 reviews   Days learned: 25%   ..." with the values in green. */
    private fun statsLine(data: HeatmapData): CharSequence {
        val line = SpannableStringBuilder()

        fun add(
            label: String,
            value: String,
            color: Int,
        ) {
            // no-break spaces inside an item, so a narrow screen wraps between items only
            if (line.isNotEmpty()) line.append("     ")
            line.append("$label: ".replace(' ', ' '))
            val start = line.length
            line.append(value.replace(' ', ' '))
            // the darkest green on a day theme's white list (6.9:1)
            val ink = if (com.ichi2.themes.Themes.currentTheme is com.ichi2.anki.settings.enums.DayTheme) GREENS[3] else color
            line.setSpan(ForegroundColorSpan(ink), start, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            line.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val r = deckPicker.resources

        fun days(count: Int) = r.getQuantityString(R.plurals.kuma3_heatmap_days, count, count)
        val average = NumberFormat.getIntegerInstance().format(data.dailyAverage)
        add(
            r.getString(R.string.kuma3_heatmap_daily_average),
            r.getQuantityString(R.plurals.kuma3_heatmap_reviews, data.dailyAverage, average),
            GREENS[2],
        )
        add(
            r.getString(R.string.kuma3_heatmap_days_learned),
            "${data.daysLearnedPercent}%",
            GREENS[
                if (data.daysLearnedPercent >=
                    50
                ) {
                    2
                } else {
                    1
                },
            ],
        )
        add(r.getString(R.string.kuma3_heatmap_longest_streak), days(data.longestStreak), GREENS[2])
        add(r.getString(R.string.kuma3_heatmap_current_streak), days(data.currentStreak), GREENS[if (data.currentStreak >= 7) 2 else 1])
        return line
    }
}
