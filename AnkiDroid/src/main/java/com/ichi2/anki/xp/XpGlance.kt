// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.xp

import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.pages.PageFragment
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Anki XP on the Statistics page: an "At a glance" group box above Today with the four numbers a quick check
 * needs (due today, due tomorrow, studied today, retention against the deck's target), for the deck the page
 * shows. Read here in Kotlin and injected into the backend page, so pages.css draws it like every other
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
        page.lifecycleScope.launch {
            val g =
                runCatching { withCol { read(this, deckName) } }.onFailure { Timber.w(it, "Anki XP: reading the glance") }.getOrNull()
                    ?: return@launch
            runCatching { page.webViewLayout.evaluateJavascript(script(g, deckName), null) }
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
                "select count(), coalesce(sum(time), 0) from revlog where id > ? and cid in (select id from cards where did in ($ids))",
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
        g: Glance,
        deckName: String,
    ): String {
        val goal = (g.target * 100).roundToInt()
        val retention =
            if (g.reviewed == 0) {
                cell("–", "retention, last 30 days", "no reviews yet")
            } else {
                val pct = (100.0 * g.passed / g.reviewed).roundToInt()
                val below = pct < goal
                cell(
                    "$pct%",
                    "retention, last 30 days",
                    "target $goal% · ${if (below) "below" else "on target"}",
                    if (below) "below" else "met",
                )
            }
        val html =
            """<div class="position-relative"><h1>At a glance</h1></div>
            <div class="xp-glance">
            ${cell("${g.due}", "due today")}
            ${cell("${g.tomorrow}", "due tomorrow")}
            ${cell("${g.reps}", "studied today", if (g.reps > 0) "${g.minutes} min" else "")}
            $retention
            </div>
            <p class="xp-glance-deck">${esc(deckName)}</p>"""
        // The graphs render after the page loads, so wait for the first section; keep one box and refresh it.
        return """(function () {
            var html = ${JSONObject.quote(html)}, tries = 0;
            (function place() {
                var box = document.getElementById("xp-glance");
                if (!box) {
                    var first = document.querySelector(".container > .position-relative > h1");
                    if (!first) { if (tries++ < 50) setTimeout(place, 200); return; }
                    // the first section, or its .row wrapper where the page has one (deck options does, graphs don't)
                    var sec = first.closest(".container");
                    var anchor = sec.parentElement.classList.contains("row") ? sec.parentElement : sec;
                    box = document.createElement("div");
                    box.id = "xp-glance";
                    box.className = "container";
                    var node = box;
                    if (anchor !== sec) {
                        node = document.createElement("div");
                        node.className = anchor.className;
                        node.appendChild(box);
                    }
                    anchor.parentNode.insertBefore(node, anchor);
                }
                box.innerHTML = html;
            })();
        })();"""
    }

    private fun cell(
        value: String,
        label: String,
        note: String = "",
        state: String = "",
    ) = """<div class="xp-glance-cell $state"><b>${esc(value)}</b><span>${esc(label)}</span>""" +
        (if (note.isEmpty()) "" else "<small>${esc(note)}</small>") + "</div>"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
