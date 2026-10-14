// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SortOrder
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
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
 * in SharedPreferences; the note is written when the reviewer closes ([writeNote]: a raw write, which clears
 * Anki's undo history, so never during reviews).
 */
object XpGame {
    const val PROGRESS = "🐻 Anki XP progress"
    private const val DECK = "kumapie"
    private const val SEP = "\u001f"
    const val QUEST_XP = 100
    val MILESTONES = listOf(10, 25, 50, 100)
    val LIMITS = listOf(5, 8, 10, 15, 20)

    private lateinit var prefs: SharedPreferences
    var mine = JSONObject()
    var others: Map<String, JSONObject> = emptyMap()
    lateinit var g: Merged
    var combo = 0
    var recordShown = false
    var session: Session? = null
    private val bosses = JSONObject()

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

    /** The combo time limit in seconds */
    var limit: Int
        get() = prefs.getInt("limit", 10)
        set(v) = prefs.edit { putInt("limit", v) }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("kuma3_xp", Context.MODE_PRIVATE)
        device =
            prefs.getString("device", null)
                ?: "kuma3-${Build.MODEL}-${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
                    .also { prefs.edit { putString("device", it) } }
        mine = prefs.getString("mine", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        prefs.getString("bosses", null)?.let { s ->
            runCatching { JSONObject(s) }.getOrNull()?.let { o -> o.keys().forEach { bosses.put(it, o.get(it)) } }
        }
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

        fun studied(k: String) = (days[k]?.reps ?: 0) > 0
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
        val m =
            Merged(
                ach = ach,
                bosses = parts.sumOf { it.optInt("bosses") },
                xp = parts.sumOf { it.optInt("xp") },
                record = parts.maxOf { it.optInt("record") },
                goal = goal,
                day = day,
                today = days[day] ?: Day(),
                streak = 0,
                days = days,
            )
        // days in a row with a review, up to today (or yesterday: today still has time)
        var streak = 0
        var i = if (m.studied(day)) 0 else 1
        while (m.studied(dayKey(i))) {
            streak++
            i++
        }
        return Merged(m.ach, m.bosses, m.xp, m.record, m.goal, m.day, m.today, streak, m.days)
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
            Quest("reps", "Do $reps reps", reps, g.reps),
            Quest("combo", "Reach a $combo-rep combo", combo, g.today.best),
            Quest("fast", "$fast answers in under $limit s", fast, g.today.fast),
        )
    }

    class Trophy(
        val id: String,
        val icon: String,
        val name: String,
        val desc: String,
        val ok: () -> Boolean,
    )

    private fun totalReps() = g.days.values.sumOf { it.reps }

    val TROPHIES =
        listOf(
            Trophy("goal", "🎯", "Goal getter", "Reach the daily goal") { g.goalHit },
            Trophy("overtime", "⏱️", "Overtime", "Double the daily goal in a day") { g.reps >= 2 * g.goal },
            Trophy("speed", "🏎️", "Speedy", "50 fast answers in a day") { g.today.fast >= 50 },
            Trophy("combo10", "⚡", "On a roll", "A 10-rep combo") { g.record >= 10 },
            Trophy("combo25", "⚡", "Unstoppable", "A 25-rep combo") { g.record >= 25 },
            Trophy("combo50", "🌩️", "Lightning", "A 50-rep combo") { g.record >= 50 },
            Trophy("combo100", "💯", "Hundred in a row", "A 100-rep combo") { g.record >= 100 },
            Trophy("streak7", "🔥", "One week", "A 7-day streak") { g.streak >= 7 },
            Trophy("streak30", "🔥", "One month", "A 30-day streak") { g.streak >= 30 },
            Trophy("streak100", "🔥", "Hundred days", "A 100-day streak") { g.streak >= 100 },
            Trophy("reps1000", "📚", "Thousand", "1,000 reps here") { totalReps() >= 1000 },
            Trophy("reps10000", "🏛️", "Ten thousand", "10,000 reps here") { totalReps() >= 10000 },
            Trophy("level10", "⭐", "Level 10", "Reach level 10") { level(g.xp) + 1 >= 10 },
            Trophy("level25", "🌟", "Level 25", "Reach level 25") { level(g.xp) + 1 >= 25 },
            Trophy("perfect", "💎", "Flawless", "Finish a deck (20+ reps) without Again") { false },
            Trophy("boss", "👑", "Boss slayer", "Beat a boss card") { g.bosses >= 1 },
            Trophy("boss10", "👑", "Boss hunter", "Beat 10 boss cards") { g.bosses >= 10 },
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

    fun startSession() {
        session = Session(g.xp, now())
    }

    /** What an answer did, for the screen and the sounds */
    class Result(
        val xp: Int,
        val ease: Int,
        val mult: Double,
        val brokeCombo: Int,
        val multUp: Boolean,
        val boss: Boolean,
        val wins: List<Pair<String, String>>,
        val levelUp: Boolean,
    )

    fun breakCombo(): Int {
        val broke = combo
        combo = 0
        recordShown = false
        return broke
    }

    fun score(
        ease: Int,
        thinkMs: Long,
        deckId: Long,
        cardId: Long,
    ): Result {
        newDay()
        val fast = thinkMs < limit * 1000L
        val lv = level(g.xp)
        val m0 = mult()
        val wasHit = g.goalHit
        val record = g.record
        val firstToday = g.reps == 0
        val broke = if (ease == 1 || !fast) breakCombo() else 0
        if (ease > 1 && fast) combo++
        val boss = isBoss(deckId, cardId)
        val xp = if (ease == 1) 2 else Math.round(10 * mult()).toInt() * (if (boss) 3 else 1)
        val t = today()
        t.put("reps", t.optInt("reps") + 1)
        if (fast && ease > 1) t.put("fast", t.optInt("fast") + 1)
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
        val wins = mutableListOf<Pair<String, String>>()
        if (boss && ease > 1) {
            beatBoss(deckId)
            g = merge()
            wins += "👑 Boss defeated!" to "Triple XP: +$xp."
        } else if (boss) {
            wins += "👑 The boss won this round" to "It comes back later today: beat it then for ×3 XP."
        }
        if (combo in MILESTONES) wins += "$combo combo!" to "$combo in a row. Keep going!"
        if (combo > record && !recordShown && record >= 5) {
            wins += "New record!" to "Best combo ever: $combo and counting."
            recordShown = true
        }
        if (firstToday) {
            wins +=
                "🔥 ${g.streak}-day streak" to (if (g.streak > 1) "Another day in a row. Keep it going!" else "Day 1. Come back tomorrow!")
        }
        if (g.goalHit && !wasHit) wins += "Daily goal done!" to "Overtime: every rep now gives 1.5× XP."
        for (q in quests()) {
            if (q.have < q.need || q.id in g.today.got) continue
            val got = t.optJSONArray("got") ?: JSONArray().also { t.put("got", it) }
            got.put(q.id)
            t.put("xp", t.optInt("xp") + QUEST_XP)
            mine.put("xp", mine.optInt("xp") + QUEST_XP)
            g = merge()
            wins += "Quest complete!" to "${q.text} · +$QUEST_XP XP"
        }
        val levelUp = level(g.xp) > lv
        if (levelUp) wins += "Level ${level(g.xp) + 1}!" to "Keep the combo going."
        for (tr in checkTrophies()) wins += "🏆 ${tr.icon} ${tr.name}" to tr.desc
        save()
        return Result(xp, ease, mult() * (if (boss) 3 else 1), broke, mult() > m0 && combo > 0, boss, wins, levelUp)
    }

    // ---------------------------------------------------------------- the shared progress note

    /** every other device's part (kuma3 Skins' and the other phones'/tablets') */
    fun readNotes(col: Collection) {
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
                if (col.getNote(nid).fields[1] == data) return
                // raw write: an undoable update would put "Update Note" on the reviewer's Undo
                col.db.execute("update notes set flds = ?, mod = ?, usn = -1 where id = ?", device + SEP + data, now() / 1000, nid)
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
