// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.TextUtils.htmlEncode
import android.view.WindowManager
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.R
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
                // a wrap_content WebView in a dialog can re-measure itself forever (the session report jumped up and
                // down): once loaded, pin it to the page height, at most 70% of the screen (taller pages scroll)
                @Suppress("SetJavaScriptEnabled") // our own HTML only
                settings.javaScriptEnabled = true
                webViewClient =
                    object : android.webkit.WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            view.evaluateJavascript("document.body.offsetHeight") { css ->
                                val h = css.toFloatOrNull() ?: return@evaluateJavascript
                                val dm = view.resources.displayMetrics
                                view.layoutParams?.let {
                                    it.height = minOf(h * dm.density, dm.heightPixels * 0.7f).toInt()
                                    view.layoutParams = it
                                }
                            }
                        }
                    }
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
            .setNegativeButton(R.string.kuma3_xp_close, null)
            .show()
    }

    private fun days(
        context: Context,
        n: Int,
    ) = context.resources.getQuantityString(R.plurals.kuma3_xp_days, n, n)

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
        val hint =
            context.getString(
                R.string.kuma3_xp_quests_hint,
                XpGame.QUEST_XP,
                g.xp,
                XpGame.level(g.xp) + 1,
                g.today.best,
                g.record,
                g.goal,
            )
        window(context, context.getString(R.string.kuma3_xp_quests_title), "$list<p class=hint>${esc(hint)}</p>") {
            setPositiveButton(R.string.kuma3_xp_trophies) { _, _ -> trophies(context) }
            setNeutralButton(R.string.kuma3_xp_more) { _, _ -> more(context) }
        }
    }

    private fun more(context: Context) {
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, "Anki XP"))
            .setItems(
                arrayOf(context.getString(R.string.kuma3_xp_statistics), context.getString(R.string.kuma3_xp_settings)),
            ) { _, i -> if (i == 0) stats(context) else settings(context) }
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
        window(context, context.getString(R.string.kuma3_xp_trophies_title, n, XpGame.TROPHIES.size), html)
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
        val longest = XpGame.longestStreak
        val lv = XpGame.level(g.xp)
        val rows =
            listOf(
                context.getString(R.string.kuma3_xp_today) to context.getString(R.string.kuma3_xp_reps_of_goal, g.reps, g.goal),
                context.getString(R.string.kuma3_xp_streak) to if (g.streak > 0) "🔥 ${days(context, g.streak)}" else "0",
                context.getString(R.string.kuma3_xp_longest_streak) to days(context, longest),
                context.getString(R.string.kuma3_xp_best_combo_ever) to g.record,
                context.getString(R.string.kuma3_xp_level) to context.getString(R.string.kuma3_xp_level_xp, lv + 1, g.xp),
                context.getString(R.string.kuma3_xp_total_reps) to g.days.values.sumOf { it.reps },
                context.getString(R.string.kuma3_xp_days_studied) to g.days.values.count { it.reps > 0 },
                context.getString(R.string.kuma3_xp_devices) to 1 + XpGame.others.size,
            )
        window(
            context,
            context.getString(R.string.kuma3_xp_statistics),
            "<div class=chart><b style='bottom:${100 * g.goal / top}%'></b>$bars</div>" +
                "<div class=cx><span>${esc(context.getString(R.string.kuma3_xp_30_days_ago))}</span>" +
                "<span>${esc(context.getString(R.string.kuma3_xp_today_lower))}</span></div>" +
                rows.joinToString("", "<table class=report>", "</table>") { (k, v) -> "<tr><td>${esc(k)}</td><td>${esc(v)}</td></tr>" },
        )
    }

    fun settings(context: Context) {
        fun onOff(on: Boolean) = context.getString(if (on) R.string.kuma3_xp_on else R.string.kuma3_xp_off)
        val items =
            arrayOf(
                context.getString(R.string.kuma3_xp_setting_sounds, onOff(XpGame.sound)),
                context.getString(R.string.kuma3_xp_setting_vibration, onOff(XpGame.vibrate)),
                context.getString(R.string.kuma3_xp_setting_crt, onOff(XpGame.crt)),
                context.getString(R.string.kuma3_xp_setting_goal, XpGame.g.goal),
                context.getString(R.string.kuma3_xp_setting_speed, XpGame.limit),
            )
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, context.getString(R.string.kuma3_xp_settings_title)))
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
            }.setNegativeButton(R.string.kuma3_xp_close, null)
            .show()
    }

    private fun goal(context: Context) {
        val goals = (2..30).map { it * 10 }
        AlertDialog
            .Builder(context)
            .setCustomTitle(titleBar(context, context.getString(R.string.kuma3_xp_goal_title)))
            .setSingleChoiceItems(goals.map { "$it" }.toTypedArray(), goals.indexOf(XpGame.g.goal)) { d, i ->
                XpGame.setGoal(goals[i])
                d.dismiss()
                settings(context)
            }.show()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pending: Runnable? = null

    /**
     * The deck report after a session, shown once on the deck list. Called after every deck list reload (it reloads
     * more than once after reviews, each time behind "Please wait..."): shown 1.5 s after the last one, once the deck
     * list is in front (not when recreated after Settings: it would be lost with the old one) and nothing is loading.
     */
    fun summary(activity: AppCompatActivity) {
        if (XpGame.session?.finished != true) return
        pending?.let(handler::removeCallbacks)
        pending =
            Runnable {
                pending = null
                if (activity.isFinishing || activity.isDestroyed) return@Runnable
                val busy =
                    !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                        com.ichi2.anki.AnkiDroidApp.instance.progressDialogShown ||
                        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
                if (busy) summary(activity) else report(activity)
            }.also { handler.postDelayed(it, 1500) }
    }

    private fun report(context: Context) {
        val s = XpGame.session ?: return
        if (!s.finished) return
        XpGame.session = null
        val g = XpGame.g
        val perfect = s.reps >= 20
        if (perfect) XpGame.checkTrophies(listOf("perfect")).also { XpGame.save() }
        XpSound.chime(listOf(523.0, 659.0, 784.0, 1047.0, 784.0, 1047.0), XpSound.Wave.SQUARE)
        val mins = max(1, ((TimeManager.time.intTimeMS() - s.start) / 60000).toInt())
        val rows =
            listOf(
                context.getString(R.string.kuma3_xp_report_reps) to s.reps,
                context.getString(R.string.kuma3_xp_report_xp) to "+${g.xp - s.xp0}",
                context.getString(R.string.kuma3_xp_report_best_combo) to
                    if (s.best > 0 && s.best >= g.record) context.getString(R.string.kuma3_xp_report_record, s.best) else "${s.best}",
                context.getString(R.string.kuma3_xp_report_correct) to "${Math.round(100.0 * (s.reps - s.again) / s.reps)}%",
                context.getString(R.string.kuma3_xp_report_average) to
                    if (s.thinkN > 0) "%.1f s".format(s.think / s.thinkN / 1000.0) else "-",
                context.getString(R.string.kuma3_xp_report_time) to context.getString(R.string.kuma3_xp_minutes, mins),
                context.getString(R.string.kuma3_xp_streak) to
                    if (g.streak >
                        0
                    ) {
                        "🔥 ${days(context, g.streak)}"
                    } else {
                        context.getString(R.string.kuma3_xp_report_goal_today, g.reps, g.goal)
                    },
                context.getString(R.string.kuma3_xp_level) to XpGame.level(g.xp) + 1,
            )
        val sweep = XpGame.TROPHIES.first { it.id == "perfect" }.name
        window(
            context,
            context.getString(R.string.kuma3_xp_report_title),
            "<div class=q>${esc(
                context.getString(R.string.kuma3_xp_report_well_done),
            )}${if (perfect) " · 💎 ${esc(sweep)}" else ""}</div>" +
                rows.joinToString("", "<table class=report>", "</table>") { (k, v) -> "<tr><td>${esc(k)}</td><td>${esc(v)}</td></tr>" },
        )
    }
}
