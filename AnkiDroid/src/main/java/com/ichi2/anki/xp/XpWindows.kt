// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.TextUtils.htmlEncode
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.common.time.TimeManager
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

/**
 * kuma3: the Anki XP windows (Daily Quests, Statistics, Trophies, Settings, the deck report), as XP dialogs with
 * the skin's HTML inside. Opened from the game bar in reviews and the taskbar button on the deck list.
 */
object XpWindows {
    private const val CSS = """
@font-face{font-family:T;src:url("file:///android_res/font/xp_tahoma.ttf")}
@font-face{font-family:T;font-weight:bold;src:url("file:///android_res/font/xp_tahoma_bold.ttf")}
html,body{margin:0;background:#ece9d8;color:#000;font:15px T,Tahoma,sans-serif}
body{padding:10px 14px}
.quest{display:block;padding:3px 0}.quest.ok{color:#008000}
.hint{color:#555;font-size:.9em;line-height:1.4;margin:8px 0 0}
.report{width:100%;border-collapse:collapse;background:#fff;border:1px solid #7f9db9;margin-top:8px}
.report td{padding:5px 10px;border-bottom:1px solid #eee}.report td:last-child{text-align:right;font-weight:bold}
.chart{position:relative;height:110px;display:flex;align-items:flex-end;gap:2px;padding:0 2px;background:#fff;border:1px solid #7f9db9}
.chart i{flex:1;min-height:1px;background:#6f97d8}.chart i.hit{background:#2ed330}.chart i.today{outline:1px solid #000}
.chart b{position:absolute;left:0;right:0;border-top:1px dashed #c00000}
.cx{display:flex;justify-content:space-between;color:#555;font-size:.85em}
.trophies{display:grid;grid-template-columns:repeat(auto-fill,minmax(96px,1fr));gap:6px}
.tro{border:1px solid #7f9db9;background:#fff;padding:6px;text-align:center;font-size:.85em;line-height:1.3}
.tro .i{font-size:1.8em}.tro.off{opacity:.45;filter:grayscale(1)}
.q{font-size:1.3em;text-align:center;margin:6px 0}
"""

    @SuppressLint("SetJavaScriptEnabled")
    private fun window(
        context: Context,
        title: String,
        html: String,
        build: AlertDialog.Builder.() -> Unit = {},
    ): AlertDialog {
        val web =
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#ece9d8"))
                loadDataWithBaseURL(
                    "file:///android_asset/",
                    "<!doctype html><meta charset=utf-8><style>$CSS</style>$html",
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        return AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, title))
            .setView(web)
            .apply(build)
            .setNegativeButton("Close", null)
            .show()
    }

    /** a Luna title bar with the red close box, for every Anki XP window */
    fun titleBar(
        context: Context,
        title: String,
    ) = android.widget.TextView(context).apply {
        val d = context.resources.displayMetrics.density
        text = title
        setTextColor(Color.WHITE)
        textSize = 17f
        typeface =
            androidx.core.content.res.ResourcesCompat
                .getFont(context, com.ichi2.anki.R.font.xp_tahoma_bold)
        setShadowLayer(1f, 1.5f, 1.5f, Color.rgb(0x0f, 0x10, 0x89))
        setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        background =
            androidx.core.content.res.ResourcesCompat
                .getDrawable(context.resources, com.ichi2.anki.R.drawable.xp_titlebar, null)
    }

    private fun esc(s: Any) = htmlEncode(s.toString())

    fun quests(context: Context) {
        XpGame.init(context)
        XpGame.newDay()
        val g = XpGame.g
        val list =
            XpGame.quests().joinToString("") { q ->
                val ok = q.id in g.today.got
                "<span class='quest${if (ok) " ok" else ""}'>${if (ok) "✔" else "☐"} ${esc(
                    q.text,
                )} (${minOf(q.have, q.need)}/${q.need})</span>"
            }
        window(
            context,
            "Daily Quests",
            "$list<p class=hint>Each quest: +${XpGame.QUEST_XP} XP · ${g.xp} XP total · level ${XpGame.level(g.xp) + 1} · " +
                "best combo today ${g.today.best} · best ever ${g.record} · daily goal ${g.goal} reps</p>",
        ) {
            setPositiveButton("Trophies") { _, _ -> trophies(context) }
            setNeutralButton("More") { _, _ -> more(context) }
        }
    }

    private fun more(context: Context) {
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, "Anki XP"))
            .setItems(arrayOf("Statistics", "Settings")) { _, i -> if (i == 0) stats(context) else settings(context) }
            .show()
    }

    fun trophies(context: Context) {
        val g = XpGame.g
        val n = XpGame.TROPHIES.count { g.ach.containsKey(it.id) }
        val html =
            XpGame.TROPHIES.joinToString("", "<div class=trophies>", "</div>") { t ->
                val at = g.ach[t.id]
                "<div class='tro${if (at == null) " off" else ""}'><div class=i>${t.icon}</div><b>${esc(t.name)}</b><br>${esc(t.desc)}" +
                    (at?.let { "<br><small>${esc(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)))}</small>" } ?: "") +
                    "</div>"
            }
        window(context, "Trophies · $n of ${XpGame.TROPHIES.size}", html)
    }

    /** the last 30 days of reps (green = goal done) and the totals, from every device's merged parts */
    fun stats(context: Context) {
        XpGame.newDay()
        val g = XpGame.g
        val days = (29 downTo 0).map { XpGame.dayKey(it) }
        val top = max(1, maxOf(g.goal, days.maxOf { g.days[it]?.reps ?: 0 }))
        val bars =
            days
                .mapIndexed { i, k ->
                    val r = g.days[k]?.reps ?: 0
                    "<i class='${if (g.hit(k)) "hit" else ""}${if (i == 29) " today" else ""}' style='height:${100 * r / top}%'></i>"
                }.joinToString("")
        var longest = 0
        var run = 0
        for (i in 400 downTo 0) {
            run = if (g.studied(XpGame.dayKey(i))) run + 1 else 0
            longest = max(longest, run)
        }
        val lv = XpGame.level(g.xp)
        val rows =
            listOf(
                "Today" to "${g.reps}/${g.goal} reps",
                "Streak" to if (g.streak > 0) "🔥 ${g.streak} day${if (g.streak > 1) "s" else ""}" else "0",
                "Longest streak" to "$longest day${if (longest == 1) "" else "s"}",
                "Best combo ever" to g.record,
                "Level" to "${lv + 1} (${g.xp} XP)",
                "Total reps" to g.days.values.sumOf { it.reps },
                "Days studied" to g.days.values.count { it.reps > 0 },
                "Devices" to 1 + XpGame.others.size,
            )
        window(
            context,
            "Statistics",
            "<div class=chart><b style='bottom:${100 * g.goal / top}%'></b>$bars</div>" +
                "<div class=cx><span>30 days ago</span><span>today</span></div>" +
                rows.joinToString("", "<table class=report>", "</table>") { (k, v) -> "<tr><td>$k</td><td>${esc(v)}</td></tr>" },
        )
    }

    fun settings(context: Context) {
        val items =
            arrayOf(
                "Game sounds: ${if (XpGame.sound) "on" else "off"}",
                "Vibration: ${if (XpGame.vibrate) "on" else "off"}",
                "Retro CRT screen: ${if (XpGame.crt) "on" else "off"}",
                "Daily goal: ${XpGame.g.goal} reps",
                "Combo time limit: ${XpGame.limit} s",
            )
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, "Anki XP Settings"))
            .setItems(items) { _, i ->
                when (i) {
                    0 -> XpGame.sound = !XpGame.sound
                    1 -> XpGame.vibrate = !XpGame.vibrate
                    2 -> XpGame.crt = !XpGame.crt
                    3 -> return@setItems goal(context)
                    4 -> XpGame.limit = XpGame.LIMITS[(XpGame.LIMITS.indexOf(XpGame.limit) + 1) % XpGame.LIMITS.size]
                }
                if (i == 0) XpSound.blip(3, 0)
                if (i == 1) XpSound.buzz(context, "3")
                if (i == 2) XpChrome.crtChanged(context)
                settings(context)
            }.setNegativeButton("Close", null)
            .show()
    }

    private fun goal(context: Context) {
        val goals = (2..30).map { it * 10 }
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, "Daily goal (reps)"))
            .setSingleChoiceItems(goals.map { "$it" }.toTypedArray(), goals.indexOf(XpGame.g.goal)) { d, i ->
                XpGame.setGoal(goals[i])
                d.dismiss()
                settings(context)
            }.show()
    }

    /** the deck report after a session (shown once on the deck list) */
    fun summary(context: Context) {
        val s = XpGame.session ?: return
        if (!s.finished) return
        XpGame.session = null
        val g = XpGame.g
        val perfect = s.reps >= 20 && s.again == 0
        if (perfect) XpGame.checkTrophies(listOf("perfect")).also { XpGame.save() }
        XpSound.chime(listOf(523.0, 659.0, 784.0, 1047.0, 784.0, 1047.0), XpSound.Wave.SQUARE)
        val mins = max(1, ((TimeManager.time.intTimeMS() - s.start) / 60000).toInt())
        val rows =
            listOf(
                "Reps" to s.reps,
                "XP earned" to "+${g.xp - s.xp0}",
                "Best combo" to "${s.best}${if (s.best > 0 && s.best >= g.record) " (record!)" else ""}",
                "Correct" to "${Math.round(100.0 * (s.reps - s.again) / s.reps)}%",
                "Average answer" to if (s.thinkN > 0) "%.1f s".format(s.think / s.thinkN / 1000.0) else "-",
                "Time" to "$mins min",
                "Streak" to if (g.streak > 0) "🔥 ${g.streak} day${if (g.streak > 1) "s" else ""}" else "goal ${g.reps}/${g.goal} today",
                "Level" to XpGame.level(g.xp) + 1,
            )
        window(
            context,
            "Session report",
            "<div class=q>✔ Well done${if (perfect) " · 💎 Flawless" else ""}</div>" +
                rows.joinToString("", "<table class=report>", "</table>") { (k, v) -> "<tr><td>$k</td><td>${esc(v)}</td></tr>" },
        )
    }
}
