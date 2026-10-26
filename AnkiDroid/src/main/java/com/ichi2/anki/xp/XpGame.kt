// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SortOrder
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * kuma3: the Anki XP game, a port of the kuma3 Skins "Anki XP" skin's rules (skins/xp/index.html in
 * kuma3-skins-catalog). Hard/Good/Easy pay 10 XP × the multiplier, Again 2 XP; the combo grows on Hard/Good/Easy
 * answered within the time limit; ×1.5 after the daily goal; boss cards ×3; three daily quests; trophies.
 *
 * Progress is shared with kuma3 Skins: every device keeps its own part `mine` = {xp, record, goal: [reps, set at ms],
 * days: {day: {reps, fast, best, xp, got, goal}}, ach: {id: ms}, bosses} in its own `🐻 Anki XP progress` note
 * (fields Device, Data; card suspended in the kumapie deck); [g] is every part merged. Between answers `mine` lives
 * in SharedPreferences; the note is written when the reviewer pauses or closes ([writeNote]: no undo
 * entry, and Anki's undo stays since core 2466c29f2).
 */
object XpGame {
    const val PROGRESS = "🐻 Anki XP progress"
    private const val DECK = "kumapie"
    const val QUEST_XP = 100

    /** points per answer, any grade; within the clock (speed bonus) */
    const val POINTS = 10
    const val FAST_POINTS = 15

    /** this long on one card (minus time outside the app) and the combo is over */
    const val IDLE_MS = 5 * 60_000L

    val MILESTONES = listOf(10, 25, 50, 100)
    val LIMITS = listOf(5, 8, 10, 15, 20)

    private lateinit var prefs: SharedPreferences
    var mine = JSONObject()
    var others: Map<String, JSONObject> = emptyMap()
    lateinit var g: Merged
    var combo = 0
    var recordShown = false
    var session: Session? = null
    private var bosses = JSONObject()

    lateinit var device: String
        private set

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit { putBoolean("sound", v) }
    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(v) = prefs.edit { putBoolean("vibrate", v) }
    var crt: Boolean
        get() = prefs.getBoolean("crt", true)
        set(v) = prefs.edit { putBoolean("crt", v) }

    /** The speed bonus time in seconds (the clock): a flip within it, then Good or Easy, earns [FAST_POINTS] */
    var limit: Int
        get() = prefs.getInt("limit", 10)
        set(v) = prefs.edit { putInt("limit", v) }

    /** The game's texts (res/values/kuma3_strings.xml, Spanish in values-es) */
    private val app get() = AnkiDroidApp.instance

    private fun s(
        @StringRes id: Int,
        vararg args: Any,
    ) = app.getString(id, *args)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("kuma3_xp", Context.MODE_PRIVATE)
        device =
            prefs.getString("device", null)
                ?: "kuma3-${Build.MODEL}-${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
                    .also { prefs.edit { putString("device", it) } }
        mine = prefs.getString("mine", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        bosses = prefs.getString("bosses", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        g = merge()
    }

    // ---------------------------------------------------------------- days, merge

    private fun now() = TimeManager.time.intTimeMS()

    /** Anki's day starts at 4 am; same format as the skin ("2026-10-14", no zero padding) */
    fun dayKey(back: Int = 0): String {
        val t =
            Instant
                .ofEpochMilli(now())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .minusHours(4)
                .minusDays(back.toLong())
        return "${t.year}-${t.monthValue}-${t.dayOfMonth}"
    }

    class Day(
        var reps: Int = 0,
        var fast: Int = 0,
        var best: Int = 0,
        var xp: Int = 0,
        val got: MutableList<String> = mutableListOf(),
        var goal: Int = 0,
    )

    class Merged(
        val ach: Map<String, Long>,
        val bosses: Int,
        val xp: Int,
        val record: Int,
        val goal: Int,
        val day: String,
        val today: Day,
        val streak: Int,
        val days: Map<String, Day>,
    ) {
        val reps get() = today.reps
        val goalHit get() = hit(day)

        fun hit(k: String) = days[k]?.let { it.reps >= (if (it.goal > 0) it.goal else goal) } == true
    }

    fun merge(): Merged {
        val parts = listOf(mine) + others.values
        val days = mutableMapOf<String, Day>()
        for (p in parts) {
            val pd = p.optJSONObject("days") ?: continue
            for (k in pd.keys()) {
                val v = pd.optJSONObject(k) ?: continue
                val d = days.getOrPut(k) { Day() }
                d.reps += v.optInt("reps")
                d.fast += v.optInt("fast")
                d.xp += v.optInt("xp")
                d.best = max(d.best, v.optInt("best"))
                d.goal = max(d.goal, v.optInt("goal"))
                v.optJSONArray("got")?.let { a -> for (i in 0 until a.length()) a.optString(i).let { if (it !in d.got) d.got.add(it) } }
            }
        }
        var goal = 100
        var goalAt = -1L
        for (p in parts) {
            val a = p.optJSONArray("goal") ?: continue
            if (a.optLong(1) > goalAt) {
                goal = a.optInt(0, 100)
                goalAt = a.optLong(1)
            }
        }
        val day = dayKey()
        val ach = mutableMapOf<String, Long>()
        for (p in parts) {
            val pa = p.optJSONObject("ach") ?: continue
            for (k in pa.keys()) ach[k] = min(ach[k] ?: Long.MAX_VALUE, pa.optLong(k))
        }
        // the review streak, as the heatmap counts it (read with the notes), plus today once reviewed here
        val repsHere = mine.optJSONObject("days")?.optJSONObject(day)?.optInt("reps") ?: 0
        return Merged(
            ach = ach,
            bosses = parts.sumOf { it.optInt("bosses") },
            xp = parts.sumOf { it.optInt("xp") },
            record = parts.maxOf { it.optInt("record") },
            goal = goal,
            day = day,
            today = days[day] ?: Day(),
            streak = reviewStreak + if (!reviewedToday && repsHere > 0) 1 else 0,
            days = days,
        )
    }

    /** this device's part for today */
    private fun today(): JSONObject {
        val days = mine.optJSONObject("days") ?: JSONObject().also { mine.put("days", it) }
        return days.optJSONObject(dayKey()) ?: JSONObject()
            .put("reps", 0)
            .put("fast", 0)
            .put("best", 0)
            .put("xp", 0)
            .put("got", JSONArray())
            .put("goal", g.goal)
            .also { days.put(dayKey(), it) }
    }

    fun save() {
        g = merge()
        prefs.edit { putString("mine", mine.toString()) }
    }

    fun newDay() {
        if (g.day != dayKey()) {
            g = merge()
            combo = 0
        }
    }

    fun level(xp: Int) = floor((sqrt(1 + 0.08 * xp) - 1) / 2).toInt()

    /** XP where level [lv] (0-based) starts: the inverse of [level]; the level is 100·(lv+1) XP long */
    fun levelStart(lv: Int) = 50 * lv * (lv + 1)

    fun mult(): Double = min(5, 1 + combo / 5) * (if (g.reps >= g.goal) 1.5 else 1.0)

    fun setGoal(goal: Int) {
        mine.put("goal", JSONArray().put(max(10, goal)).put(now()))
        today().put("goal", max(10, goal))
        save()
    }

    // ---------------------------------------------------------------- quests, trophies

    class Quest(
        val id: String,
        val text: String,
        val need: Int,
        val have: Int,
    )

    fun quests(): List<Quest> {
        var h = 7L
        for (c in g.day) h = (h * 31 + c.code) and 0xFFFFFFFFL
        val reps = Math.round(g.goal * listOf(1.2, 1.5, 2.0)[(h % 3).toInt()]).toInt()
        val combo = listOf(15, 20, 25, 30)[(h % 4).toInt()]
        val fast = Math.round(g.goal * listOf(0.4, 0.5, 0.6)[((h shr 3) % 3).toInt()]).toInt()
        return listOf(
            Quest("reps", s(R.string.kuma3_xp_quest_reps, reps), reps, g.reps),
            Quest("combo", s(R.string.kuma3_xp_quest_combo, combo), combo, g.today.best),
            Quest("fast", s(R.string.kuma3_xp_quest_fast, fast), fast, g.today.fast),
        )
    }

    class Trophy(
        val id: String,
        val icon: String,
        private val nameOf: () -> String,
        private val descOf: () -> String,
        val ok: () -> Boolean,
    ) {
        val name get() = nameOf()
        val desc get() = descOf()
    }

    private fun trophy(
        id: String,
        icon: String,
        @StringRes name: Int,
        desc: () -> String,
        ok: () -> Boolean,
    ) = Trophy(id, icon, { s(name) }, desc, ok)

    private fun thousands(n: Int) = NumberFormat.getIntegerInstance().format(n)

    private fun totalReps() = g.days.values.sumOf { it.reps }

    val TROPHIES =
        listOf(
            trophy("goal", "🎯", R.string.kuma3_xp_trophy_goal, { s(R.string.kuma3_xp_trophy_goal_desc) }) { g.goalHit },
            trophy("overtime", "⏱️", R.string.kuma3_xp_trophy_overtime, { s(R.string.kuma3_xp_trophy_overtime_desc) }) {
                g.reps >= 2 * g.goal
            },
            trophy("speed", "🏎️", R.string.kuma3_xp_trophy_speed, { s(R.string.kuma3_xp_trophy_speed_desc) }) { g.today.fast >= 50 },
            trophy("combo10", "⚡", R.string.kuma3_xp_trophy_combo10, { s(R.string.kuma3_xp_trophy_combo_desc, 10) }) { g.record >= 10 },
            trophy("combo25", "⚡", R.string.kuma3_xp_trophy_combo25, { s(R.string.kuma3_xp_trophy_combo_desc, 25) }) { g.record >= 25 },
            trophy("combo50", "🌩️", R.string.kuma3_xp_trophy_combo50, { s(R.string.kuma3_xp_trophy_combo_desc, 50) }) { g.record >= 50 },
            trophy("combo100", "💯", R.string.kuma3_xp_trophy_combo100, { s(R.string.kuma3_xp_trophy_combo_desc, 100) }) {
                g.record >= 100
            },
            trophy("streak7", "🔥", R.string.kuma3_xp_trophy_streak7, { s(R.string.kuma3_xp_trophy_streak_desc, 7) }) { g.streak >= 7 },
            trophy("streak30", "🔥", R.string.kuma3_xp_trophy_streak30, { s(R.string.kuma3_xp_trophy_streak_desc, 30) }) {
                g.streak >= 30
            },
            trophy("streak100", "🔥", R.string.kuma3_xp_trophy_streak100, { s(R.string.kuma3_xp_trophy_streak_desc, 100) }) {
                g.streak >= 100
            },
            trophy("reps1000", "📚", R.string.kuma3_xp_trophy_reps1000, { s(R.string.kuma3_xp_trophy_reps_desc, thousands(1000)) }) {
                totalReps() >= 1000
            },
            trophy("reps10000", "🏛️", R.string.kuma3_xp_trophy_reps10000, { s(R.string.kuma3_xp_trophy_reps_desc, thousands(10000)) }) {
                totalReps() >= 10000
            },
            Trophy("level10", "⭐", { s(R.string.kuma3_xp_trophy_level, 10) }, { s(R.string.kuma3_xp_trophy_level_desc, 10) }) {
                level(g.xp) + 1 >= 10
            },
            Trophy("level25", "🌟", { s(R.string.kuma3_xp_trophy_level, 25) }, { s(R.string.kuma3_xp_trophy_level_desc, 25) }) {
                level(g.xp) + 1 >= 25
            },
            trophy("perfect", "💎", R.string.kuma3_xp_trophy_perfect, { s(R.string.kuma3_xp_trophy_perfect_desc) }) { false },
            trophy("boss", "👑", R.string.kuma3_xp_trophy_boss, { s(R.string.kuma3_xp_trophy_boss_desc) }) { g.bosses >= 1 },
            trophy("boss10", "👑", R.string.kuma3_xp_trophy_boss10, { s(R.string.kuma3_xp_trophy_boss10_desc) }) { g.bosses >= 10 },
        )

    /** newly unlocked trophies (saved into mine.ach) */
    fun checkTrophies(extra: List<String> = emptyList()): List<Trophy> {
        val out = mutableListOf<Trophy>()
        for (t in TROPHIES) {
            if (g.ach.containsKey(t.id) || !(t.id in extra || t.ok())) continue
            (mine.optJSONObject("ach") ?: JSONObject().also { mine.put("ach", it) }).put(t.id, now())
            g = merge()
            out += t
        }
        return out
    }

    // ---------------------------------------------------------------- boss cards

    /** Per deck per day: its due card with the most lapses (1+); ×3 XP when answered right. */
    fun pickBoss(
        col: Collection,
        deckId: Long,
    ) {
        val key = deckId.toString()
        if (bosses.optJSONObject(key)?.optString("day") == dayKey()) return
        val id =
            runCatching {
                col
                    .findCards(
                        "did:$deckId is:due -is:suspended -is:buried prop:lapses>0",
                        SortOrder.AfterSqlOrderBy("c.lapses desc"),
                    ).firstOrNull()
            }.onFailure { Timber.w(it, "Anki XP boss") }.getOrNull()
        bosses.put(key, JSONObject().put("day", dayKey()).put("card", id ?: 0L).put("beaten", false))
        prefs.edit { putString("bosses", bosses.toString()) }
    }

    fun isBoss(
        deckId: Long,
        cardId: Long,
    ): Boolean {
        val b = bosses.optJSONObject(deckId.toString()) ?: return false
        return b.optString("day") == dayKey() && !b.optBoolean("beaten") && b.optLong("card") == cardId
    }

    private fun beatBoss(deckId: Long) {
        bosses.optJSONObject(deckId.toString())?.put("beaten", true)
        prefs.edit { putString("bosses", bosses.toString()) }
        mine.put("bosses", mine.optInt("bosses") + 1)
    }

    // ---------------------------------------------------------------- scoring

    class Session(
        val xp0: Int,
        val start: Long,
    ) {
        var reps = 0
        var again = 0
        var best = 0
        var think = 0L
        var thinkN = 0
        var finished = false
    }

    /** true once ever: the reviewer explains the combo clock the first time it runs */
    fun firstClock(): Boolean {
        if (prefs.getBoolean("clockHint", false)) return false
        prefs.edit { putBoolean("clockHint", true) }
        return true
    }

    fun startSession() {
        session = Session(g.xp, now())
    }

    /** What an answer did, for the screen and the sounds */
    class Result(
        val xp: Int,
        val points: Int,
        val mult: Double,
        val brokeCombo: Int,
        val multUp: Boolean,
        val wins: List<Win>,
        val levelUp: Boolean,
    )

    /** A balloon after an answer; [kind] picks its sound */
    class Win(
        val title: String,
        val msg: String,
        val kind: WinKind = WinKind.OTHER,
    )

    enum class WinKind { LEVEL, BOSS, TROPHY, COMBO, OTHER }

    fun breakCombo(): Int {
        val broke = combo
        combo = 0
        recordShown = false
        return broke
    }

    /** The game before one answer, so an undone answer gives back its rep, XP, quests and boss */
    private class Before(
        val ease: Int,
        val mine: String,
        val bosses: String,
        val combo: Int,
    )

    private val answers = ArrayDeque<Before>()

    /** tryUndo undid an "Answer Card": the game as it was before that answer */
    fun undoAnswer() {
        if (!::prefs.isInitialized) return
        val last = answers.removeLastOrNull() ?: return
        mine = JSONObject(last.mine)
        bosses = JSONObject(last.bosses)
        prefs.edit { putString("bosses", bosses.toString()) }
        combo = last.combo
        session?.let {
            it.reps--
            if (last.ease == 1) it.again--
        }
        save()
    }

    /**
     * One answer. Honest grading never costs anything: every grade earns the same points and grows the combo, so
     * pressing Hard instead of Again never pays (it would feed FSRS/RWKV a lie). Speed only adds: a flip within the
     * clock that you then pass with Good or Easy earns [FAST_POINTS], so flipping before recalling doesn't pay. The
     * combo breaks only when the reviewer closes or after [IDLE_MS] away.
     */
    fun score(
        ease: Int,
        thinkMs: Long,
        idle: Boolean,
        deckId: Long,
        cardId: Long,
    ): Result {
        newDay()
        answers.addLast(Before(ease, mine.toString(), bosses.toString(), combo))
        if (answers.size > 50) answers.removeFirst()
        val fast = thinkMs < limit * 1000L && ease >= 3
        val lv = level(g.xp)
        val m0 = mult()
        val wasHit = g.goalHit
        val record = g.record
        val firstToday = g.reps == 0
        val broke = if (idle) breakCombo() else 0
        combo++
        val boss = isBoss(deckId, cardId)
        val points = if (fast) FAST_POINTS else POINTS
        val mult = mult() * (if (boss) 3 else 1)
        val xp = Math.round(points * mult).toInt()
        val t = today()
        t.put("reps", t.optInt("reps") + 1)
        if (fast) t.put("fast", t.optInt("fast") + 1)
        t.put("best", max(t.optInt("best"), combo))
        t.put("xp", t.optInt("xp") + xp)
        t.put("goal", g.goal)
        mine.put("xp", mine.optInt("xp") + xp)
        mine.put("record", max(mine.optInt("record"), combo))
        g = merge()
        session?.let {
            it.reps++
            if (ease == 1) it.again++
            it.best = max(it.best, combo)
            if (thinkMs < 60000) {
                it.think += thinkMs
                it.thinkN++
            }
        }
        val wins = mutableListOf<Win>()
        if (boss && ease > 1) {
            beatBoss(deckId)
            g = merge()
            wins += Win(s(R.string.kuma3_xp_win_boss_defeated), s(R.string.kuma3_xp_win_boss_defeated_msg, xp), WinKind.BOSS)
        } else if (boss) {
            wins += Win(s(R.string.kuma3_xp_win_boss_won), s(R.string.kuma3_xp_win_boss_won_msg, xp))
        }
        if (combo in
            MILESTONES
        ) {
            wins += Win(s(R.string.kuma3_xp_win_combo, combo), s(R.string.kuma3_xp_win_combo_msg, combo), WinKind.COMBO)
        }
        if (combo > record && !recordShown && record >= 5) {
            wins += Win(s(R.string.kuma3_xp_win_record), s(R.string.kuma3_xp_win_record_msg, combo))
            recordShown = true
        }
        if (firstToday) {
            wins +=
                Win(
                    app.resources.getQuantityString(R.plurals.kuma3_xp_streak_title, g.streak, g.streak),
                    s(if (g.streak > 1) R.string.kuma3_xp_win_streak_more else R.string.kuma3_xp_win_streak_first),
                )
        }
        if (g.goalHit && !wasHit) wins += Win(s(R.string.kuma3_xp_win_goal), s(R.string.kuma3_xp_win_goal_msg))
        for (q in quests()) {
            if (q.have < q.need || q.id in g.today.got) continue
            val got = t.optJSONArray("got") ?: JSONArray().also { t.put("got", it) }
            got.put(q.id)
            t.put("xp", t.optInt("xp") + QUEST_XP)
            mine.put("xp", mine.optInt("xp") + QUEST_XP)
            g = merge()
            wins += Win(s(R.string.kuma3_xp_win_quest), "${q.text} · +$QUEST_XP XP")
        }
        val levelUp = level(g.xp) > lv
        if (levelUp) wins += Win(s(R.string.kuma3_xp_win_level, level(g.xp) + 1), s(R.string.kuma3_xp_win_level_msg), WinKind.LEVEL)
        for (tr in checkTrophies()) wins += Win("🏆 ${tr.icon} ${tr.name}", tr.desc, WinKind.TROPHY)
        save()
        return Result(xp, points, mult, broke, mult() > m0 && combo > 0, wins, levelUp)
    }

    // ---------------------------------------------------------------- the shared progress note

    /** The one streak: days in a row with a review in the collection (the heatmap's Current streak). */
    private var reviewStreak = 0
    private var reviewedToday = false
    var longestStreak = 0
        private set

    /** every other device's part (kuma3 Skins' and the other phones'/tablets'), and the review streak */
    fun readNotes(col: Collection) {
        runCatching {
            val past =
                com.ichi2.anki.heatmap.HeatmapData
                    .past(col)
            val reviews =
                com.ichi2.anki.heatmap
                    .HeatmapData(java.time.LocalDate.now(), past, emptyMap())
            reviewStreak = reviews.currentStreak
            longestStreak = reviews.longestStreak
            reviewedToday = (past[0] ?: 0) > 0
        }.onFailure { Timber.w(it, "Anki XP: reading the review streak") }
        runCatching {
            val out = mutableMapOf<String, JSONObject>()
            for (nid in col.findNotes("\"note:$PROGRESS\"")) {
                val f = col.getNote(nid).fields
                if (f.size >= 2 && f[0].isNotEmpty() && f[0] != device) runCatching { out[f[0]] = JSONObject(f[1]) }
            }
            others = out
            g = merge()
        }.onFailure { Timber.w(it, "Anki XP: reading the progress notes") }
    }

    /** this device's part into its note (made with its note type the first time) */
    fun writeNote(col: Collection) {
        runCatching {
            val data = mine.toString()
            val nt = col.notetypes.byName(PROGRESS)
            val nid = nt?.let { col.findNotes("\"note:$PROGRESS\"").firstOrNull { col.getNote(it).fields[0] == device } }
            if (nid != null) {
                val note = col.getNote(nid)
                if (note.fields[1] == data) return
                note.setItem("Data", data)
                // no undo entry: the reviewer's Undo stays on the answer (so no changes to hand to the UI)
                @Suppress("CheckResult")
                col.updateNote(note, skipUndoEntry = true)
                return
            }
            val type =
                nt ?: col.notetypes.new(PROGRESS).also { m ->
                    col.notetypes.addField(m, col.notetypes.newField("Device"))
                    col.notetypes.addField(m, col.notetypes.newField("Data"))
                    col.notetypes.add_template(
                        m,
                        col.notetypes.newTemplate("Card 1").apply {
                            qfmt = "{{Device}}"
                            afmt =
                                "{{FrontSide}}<hr id=answer>{{Data}}"
                        },
                    )
                    col.notetypes.add(m)
                }
            val note = col.newNote(col.notetypes.byName(PROGRESS) ?: type)
            note.setItem("Device", device)
            note.setItem("Data", data)
            note.tags = mutableListOf("anki-xp")
            col.addNote(note, col.decks.id(DECK))
            col.sched.suspendCards(note.cardIds(col))
        }.onFailure { Timber.w(it, "Anki XP: writing the progress note") }
    }
}
