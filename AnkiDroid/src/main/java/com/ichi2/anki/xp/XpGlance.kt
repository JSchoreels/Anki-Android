// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.xp

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.pages.PageFragment
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Anki XP on the Statistics page: an "At a glance" group box above Today with the four numbers a quick check
 * needs (due today, due tomorrow, studied today, retention against the deck's target), for the deck the page
 * shows, then a Player box with the reward (level, today's XP, streak, goal: the Home Player box's numbers). Read here in Kotlin and injected into the backend page, so pages.css draws it like every other
 * section. Only with the Anki XP day theme.
 */
object XpGlance {
    private class Glance(
        val due: Int,
        val tomorrow: Int,
        val reps: Int,
        val minutes: Int,
        val passed: Int,
        val reviewed: Int,
        val target: Double,
    )

    fun show(
        page: PageFragment,
        deckName: String,
    ) {
        if (!XpChrome.isOn()) return
        XpGame.init(page.requireContext())
        page.lifecycleScope.launch {
            val g =
                runCatching {
                    withCol {
                        XpGame.readNotes(this)
                        read(this, deckName)
                    }
                }.onFailure { Timber.w(it, "Anki XP: reading the glance") }.getOrNull()
                    ?: return@launch
            runCatching { page.webViewLayout.evaluateJavascript(script(page.requireContext(), g, deckName), null) }
        }
    }

    private fun read(
        col: Collection,
        deckName: String,
    ): Glance? {
        val did = col.decks.idForName(deckName) ?: return null
        val ids = col.decks.deckAndChildIds(did).joinToString(",")
        val node = col.sched.deckDueTree().find(did)
        val cutoff = col.sched.dayCutoff
        val start = cutoff - 86_400
        val today = col.sched.today
        val tomorrow =
            col.db.queryScalar(
                "select count() from cards where did in ($ids) and ((queue in (2, 3) and due = ?) or (queue = 1 and due >= ? and due < ?))",
                today + 1,
                cutoff,
                cutoff + 86_400,
            )
        var reps = 0
        var ms = 0L
        col.db
            .query(
                "select count(), coalesce(sum(time), 0) from revlog where id > ? and type not in (4, 5) and cid in (select id from cards where did in ($ids))",
                start * 1000,
            ).use {
                if (it.moveToNext()) {
                    reps = it.getInt(0)
                    ms = it.getLong(1)
                }
            }
        // Anki's own retention: each card's first review of the day with an interval of a day or more
        var passed = 0
        var reviewed = 0
        col.db
            .query(
                """select ease from revlog where id in (select min(id) from revlog where id > ? and ease > 0
                and (lastIvl >= 1 or lastIvl <= -86400) and cid in (select id from cards where did in ($ids))
                group by cid, (id / 1000 - ?) / 86400)""",
                (cutoff - 30 * 86_400) * 1000,
                cutoff,
            ).use {
                while (it.moveToNext()) {
                    reviewed++
                    if (it.getInt(0) > 1) passed++
                }
            }
        val target =
            col.decks
                .configDictForDeckId(did)
                .jsonObject
                .optDouble("desiredRetention", 0.9)
        val due = node?.let { it.newCount + it.lrnCount + it.revCount } ?: 0
        return Glance(due, tomorrow, reps, (ms / 60_000).toInt(), passed, reviewed, target)
    }

    private fun script(
        c: Context,
        g: Glance,
        deckName: String,
    ): String {
        val goal = (g.target * 100).roundToInt()
        val retentionLabel = c.getString(R.string.kuma3_xp_glance_retention)
        val retention =
            if (g.reviewed == 0) {
                cell("–", retentionLabel, c.getString(R.string.kuma3_xp_glance_no_reviews))
            } else {
                val pct = (100.0 * g.passed / g.reviewed).roundToInt()
                val below = pct < goal
                cell(
                    "$pct%",
                    retentionLabel,
                    c.getString(if (below) R.string.kuma3_xp_glance_target_below else R.string.kuma3_xp_glance_target_met, goal),
                    if (below) "below" else "met",
                )
            }
        val minutes = if (g.reps > 0) c.getString(R.string.kuma3_xp_minutes, g.minutes) else ""
        val html =
            """<div class="position-relative"><h1>${esc(c.getString(R.string.kuma3_xp_glance_title))}</h1></div>
            <div class="xp-glance">
            ${cell("${g.due}", c.getString(R.string.kuma3_xp_glance_due_today))}
            ${cell("${g.tomorrow}", c.getString(R.string.kuma3_xp_glance_due_tomorrow))}
            ${cell("${g.reps}", c.getString(R.string.kuma3_xp_glance_studied), minutes)}
            $retention
            </div>
            <p class="xp-glance-deck">${esc(deckName)}</p>"""
        val p = XpGame.g
        val lv = XpGame.level(p.xp)
        val longest = XpGame.longestStreak
        val player =
            """<div class="position-relative"><h1>${esc(c.getString(R.string.kuma3_xp_player))}</h1></div>
            <div class="xp-glance">
            ${cell("Lv ${lv + 1}", c.getString(R.string.kuma3_xp_glance_level), "${p.xp - XpGame.levelStart(lv)}/${100 * (lv + 1)} XP")}
            ${cell(
                "+${p.today.xp}",
                c.getString(R.string.kuma3_xp_glance_xp_today),
                if (p.today.best > 0) c.getString(R.string.kuma3_xp_glance_best_combo, p.today.best) else "",
            )}
            ${cell(
                "🔥 ${p.streak}",
                c.getString(R.string.kuma3_xp_glance_day_streak),
                if (longest > p.streak) {
                    c.getString(R.string.kuma3_xp_glance_longest, longest)
                } else if (p.streak > 0) {
                    c.getString(R.string.kuma3_xp_glance_your_longest)
                } else {
                    ""
                },
            )}
            ${cell(
                "${p.reps}/${p.goal}",
                c.getString(R.string.kuma3_xp_glance_goal),
                if (p.goalHit) {
                    c.getString(R.string.kuma3_xp_glance_goal_done)
                } else {
                    c.getString(
                        R.string.kuma3_xp_glance_reps_to_go,
                        p.goal - p.reps,
                    )
                },
                if (p.goalHit) "met" else "",
            )}
            </div>
            <p class="xp-glance-deck">${esc(c.getString(R.string.kuma3_xp_glance_all_decks))}</p>"""
        // The graphs render after the page loads, so wait for the first section; keep the two boxes and refresh them.
        return """(function () {
            var boxes = [["xp-glance", ${JSONObject.quote(html)}], ["xp-player", ${JSONObject.quote(player)}]], tries = 0;
            (function place() { boxes.forEach(function (b) {
                var box = document.getElementById(b[0]);
                if (!box) {
                    // the page's first section, not one of these boxes, so both go above it in order
                    var first = document.querySelector(".container:not([id^=xp-]) > .position-relative > h1");
                    if (!first) { if (b[0] === "xp-glance" && tries++ < 50) setTimeout(place, 200); return; }
                    var sec = first.closest(".container");
                    box = document.createElement("div");
                    box.id = b[0];
                    box.className = "container";
                    sec.parentNode.insertBefore(box, sec);
                }
                box.innerHTML = b[1];
            }); })();
        })();"""
    }

    private fun cell(
        value: String,
        label: String,
        note: String = "",
        state: String = "",
    ) = """<div class="xp-glance-cell $state"><b>${esc(value)}</b><span>${esc(label)}</span>""" +
        (if (note.isEmpty()) "" else "<small>${esc(note)}</small>") + "</div>"

    private fun esc(s: String) = android.text.TextUtils.htmlEncode(s)
}
