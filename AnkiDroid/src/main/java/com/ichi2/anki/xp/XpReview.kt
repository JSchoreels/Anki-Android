// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Decks
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

/**
 * kuma3: the Anki XP game in the reviewer (only with the Anki XP day theme). Reviewer calls [question],
 * [audioDone], [answerShown], [answered], [pause]/[resume] and [finish]; this keeps the combo clock, scores with
 * [XpGame] and shows the game bar, the pop-ups, the balloons and the sounds.
 */
class XpReview private constructor(
    private val activity: AnkiActivity,
) {
    private val bar = XpBar(activity)
    private val overlay = activity.findViewById<FrameLayout>(android.R.id.content)
    private val handler = Handler(Looper.getMainLooper())
    private var cardId = 0L
    private var cardReps = 0
    private var deckId = 0L
    private var shown = true
    private var clockOn = false
    private var clockStart = 0L
    private var pausedAt = 0L
    private var broke = false
    private var thinkMs = 0L
    private var token = 0

    private val tick =
        object : Runnable {
            override fun run() {
                handler.postDelayed(this, 100)
                if (shown || !clockOn || pausedAt != 0L) return
                val limit = XpGame.limit * 1000L
                val before = bar.timeLeft
                val left = (limit - (SystemClock.uptimeMillis() - clockStart)).coerceAtLeast(0)
                bar.setClock(left.toFloat() / limit, true)
                if (left in 1..3000 && (left + 999) / 1000 < (before * limit + 999).toLong() / 1000 && XpGame.combo >= 3) {
                    XpSound.chime(listOf(1200.0), XpSound.Wave.SINE, 0.2)
                }
                if (left == 0L && !broke) {
                    broke = true
                    comboBroken(XpGame.breakCombo())
                    bar.invalidate()
                }
            }
        }

    init {
        // The game strip covers AnkiDroid's counts bar under the title (Anki XP "maximized" reviewer): it draws the
        // live counts and the flag/mark icons of the views underneath, so the reviewer's own logic is untouched.
        activity.findViewById<android.widget.RelativeLayout>(R.id.top_bar)?.let { top ->
            top.setPadding(0, 0, 0, 0)
            top.background = ResourcesCompat.getDrawable(activity.resources, R.drawable.xp_status, null) // also under the side insets
            bar.counts = listOf(R.id.new_number, R.id.learn_number, R.id.review_number).mapNotNull { activity.findViewById<TextView>(it) }
            bar.icons = listOf(R.id.flag_icon, R.id.mark_icon).mapNotNull { activity.findViewById<android.widget.ImageView>(it) }
            bar.counts.forEach { tv -> tv.addTextChangedListener(afterTextChanged = { bar.invalidate() }) }
            top.addView(bar, android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        }
        bar.setOnClickListener { XpWindows.quests(activity) }
        XpGame.startSession()
        activity.launchCatchingTask {
            withCol {
                XpGame.readNotes(this)
                XpGame.pickBoss(this, decks.selected())
            }
            bar.invalidate()
        }
        handler.post(tick)
    }

    fun question(card: Card) {
        XpGame.undone(card.id, card.reps) // an undone answer takes its rep and XP back
        cardId = card.id
        cardReps = card.reps
        deckId = card.did
        // the deck's name in the Luna title bar
        runCatching { activity.supportActionBar?.title = Decks.basename(activity.getColUnsafe.decks.name(card.did)) }
        shown = false
        broke = false
        thinkMs = 0
        val hasAudio = runCatching { card.renderOutput(activity.getColUnsafe).questionAvTags.isNotEmpty() }.getOrDefault(false)
        clockOn = !hasAudio
        clockStart = SystemClock.uptimeMillis()
        bar.setClock(1f, clockOn)
        // the clock waits for the word audio, or 5 s if it never reports back
        val t = ++token
        if (hasAudio) handler.postDelayed({ if (t == token) audioDone() }, 5000)
        if (XpGame.isBoss(deckId, cardId)) balloon("👑 BOSS CARD", "Failed ${card.lapses}×: ×3 XP if you get it right.")
    }

    fun audioDone() {
        if (shown || clockOn) return
        clockOn = true
        clockStart = SystemClock.uptimeMillis()
        bar.setClock(1f, true)
    }

    fun answerShown() {
        if (shown) return
        shown = true
        thinkMs = if (clockOn) SystemClock.uptimeMillis() - clockStart else 0
        bar.setClock(-1f, false)
    }

    fun answered(ease: Int) {
        val r = XpGame.score(ease, thinkMs, deckId, cardId, cardReps)
        XpSound.blip(ease, XpGame.combo)
        XpSound.buzz(activity, ease.toString())
        if (ease == 1) pop("+${r.xp} XP") else pointsMult(r.mult, r.xp)
        comboBroken(r.brokeCombo)
        if (r.multUp) {
            XpSound.chime(listOf(660.0, 880.0))
            shake(XpGame.mult().toInt())
        }
        if (XpGame.combo in XpGame.MILESTONES) XpSound.buzz(activity, "milestone")
        r.wins.forEachIndexed {
            i,
            (title, msg),
            ->
            handler.postDelayed({ win(title, msg, r.levelUp && title.startsWith("Level")) }, 250L * i)
        }
        bar.juice(r.multUp)
    }

    fun pause() {
        if (pausedAt == 0L) pausedAt = SystemClock.uptimeMillis()
    }

    fun resume() {
        if (pausedAt != 0L) clockStart += SystemClock.uptimeMillis() - pausedAt
        pausedAt = 0
    }

    /** the reviewer closes: the combo ends, the session report waits for the deck list, the note is written */
    fun finish() {
        handler.removeCallbacksAndMessages(null)
        XpGame.breakCombo()
        XpGame.session?.let { if (it.reps > 0) it.finished = true }
        applicationScope.launch { runCatching { withCol { XpGame.writeNote(this) } } }
    }

    // ---------------------------------------------------------------- effects

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun comboBroken(was: Int) {
        if (was < 3) return
        pop("COMBO BROKEN ×$was", Color.rgb(0xff, 0x30, 0x30), 26f)
        XpSound.slide()
        XpSound.buzz(activity, "break")
    }

    private fun win(
        title: String,
        msg: String,
        levelUp: Boolean,
    ) {
        balloon(title, msg)
        when {
            levelUp -> XpSound.chime(listOf(523.0, 784.0, 1047.0, 1568.0))
            title.startsWith(
                "🏆",
            ) || title.startsWith("👑 Boss") -> XpSound.chime(listOf(659.0, 784.0, 988.0, 1319.0, 1568.0), XpSound.Wave.SQUARE)
            title.endsWith("combo!") -> XpSound.chime(listOf(523.0, 659.0, 784.0, 1047.0, 1319.0), XpSound.Wave.SQUARE)
            else -> XpSound.chime(listOf(784.0, 988.0, 1175.0))
        }
    }

    private fun bottomOffset() = (activity.findViewById<View>(R.id.bottom_area_layout)?.height ?: 0) + dp(24)

    private fun textView(
        text: String,
        color: Int,
        sp: Float,
    ) = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = ResourcesCompat.getFont(activity, R.font.xp_tahoma_bold)
        setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
    }

    private fun addOverlay(
        v: View,
        gravity: Int,
        bottom: Int,
    ) = overlay.addView(
        v,
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity).apply {
            bottomMargin =
                bottom
        },
    )

    private fun pop(
        text: String,
        color: Int = Color.rgb(0xff, 0xd7, 0x00),
        sp: Float = 20f,
    ) {
        val v = textView(text, color, sp)
        addOverlay(v, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottomOffset() + dp(50))
        v
            .animate()
            .translationY(-dp(50).toFloat())
            .alpha(0f)
            .setDuration(900)
            .withEndAction { overlay.removeView(v) }
    }

    /** the skin's Points × Mult: a blue 10 and a red ×m slide in, slam into the total, which counts up and flies off */
    private fun pointsMult(
        mult: Double,
        xp: Int,
    ) {
        fun chip(
            text: String,
            bg: Int,
        ) = textView(text, Color.WHITE, 22f).apply {
            setPadding(dp(8), dp(1), dp(8), dp(1))
            background =
                GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(2), Color.BLACK)
                }
        }
        val row =
            LinearLayout(activity).apply {
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
        val c = chip("10", Color.rgb(0x00, 0x9d, 0xff))
        val x = chip("×${if (mult % 1.0 == 0.0) mult.toInt().toString() else "%.1f".format(mult)}", Color.rgb(0xfe, 0x5f, 0x55))
        row.addView(c)
        row.addView(
            x,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart =
                    dp(6)
            },
        )
        addOverlay(row, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottomOffset() + dp(50))
        c.translationX = -dp(60).toFloat()
        x.translationX = dp(60).toFloat()
        c.animate().translationX(0f).setDuration(200)
        x.animate().translationX(0f).setDuration(200)
        handler.postDelayed({
            row.removeAllViews()
            val t = textView("+0 XP", Color.rgb(0xff, 0xd7, 0x00), 28f)
            row.addView(t)
            ValueAnimator.ofFloat(0f, 1f).setDuration(300).apply {
                addUpdateListener { t.text = "+${Math.round(xp * (1 - Math.pow(1.0 - it.animatedFraction, 3.0)))} XP" }
                start()
            }
            t.scaleX = 1.8f
            t.scaleY = 1.8f
            t
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
        }, 380)
        handler.postDelayed({
            row.animate().translationY(-overlay.height * 0.6f).scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(350).withEndAction {
                overlay.removeView(row)
            }
        }, 950)
    }

    /** an XP balloon tip, bottom right; several stack up */
    fun balloon(
        title: String,
        msg: String,
    ) = Companion.balloon(activity, title, msg, bottomOffset())

    /** window shake and a white flash, stronger at each multiplier step (s = 1..5) */
    private fun shake(s: Int) {
        val target = overlay.getChildAt(0) ?: return
        ValueAnimator.ofFloat(0f, 1f).setDuration(200L + 80L * s).apply {
            addUpdateListener {
                val k = it.animatedFraction
                val amp = dp(2) * s * (1 - k)
                target.translationX = (amp * cos(k * 9 * PI)).toFloat()
                target.translationY = (amp * 0.5 * cos(k * 7 * PI + 1)).toFloat()
            }
            doOnEndReset(target)
            start()
        }
        val flash = View(activity).apply { setBackgroundColor(Color.WHITE) }
        overlay.addView(flash, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        flash.alpha = 0.35f
        flash
            .animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { overlay.removeView(flash) }
    }

    private fun ValueAnimator.doOnEndReset(v: View) =
        addListener(
            object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v.translationX = 0f
                    v.translationY = 0f
                }
            },
        )

    companion object {
        /** null unless the Anki XP day theme is on */
        fun attach(activity: AnkiActivity): XpReview? {
            if (!XpChrome.isOn()) return null
            XpGame.init(activity)
            return XpReview(activity)
        }

        fun balloon(
            activity: AnkiActivity,
            title: String,
            msg: String,
            bottom: Int,
        ) {
            val overlay = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
            val d = activity.resources.displayMetrics.density
            val b =
                TextView(activity).apply {
                    text =
                        android.text.SpannableStringBuilder().apply {
                            append(title, android.text.style.StyleSpan(Typeface.BOLD), 0)
                            append("\n")
                            append(msg)
                        }
                    setTextColor(Color.BLACK)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
                    background = ResourcesCompat.getDrawable(resources, R.drawable.xp_tooltip, null)
                    elevation = 6 * d
                    maxWidth = (260 * d).toInt()
                }
            val stacked = (0 until overlay.childCount).count { overlay.getChildAt(it).tag == "xp-balloon" }
            b.tag = "xp-balloon"
            overlay.addView(
                b,
                FrameLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END,
                    ).apply {
                        bottomMargin = bottom + (stacked * 68 * d).toInt()
                        marginEnd = (10 * d).toInt()
                    },
            )
            b.alpha = 0f
            b.animate().alpha(1f).setDuration(150)
            b.postDelayed({
                b
                    .animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction { overlay.removeView(b) }
            }, 3500)
        }
    }
}

/**
 * The game bar: combo clock on top, then the daily goal ring, reps/goal, streak, level, XP bar and the combo
 * (hot from 5, on fire from 10). Drawn by hand: one view, no layout.
 */
class XpBar(
    context: Context,
) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.xp_tahoma)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        }
    private val bold = Paint(text).apply { typeface = ResourcesCompat.getFont(context, R.font.xp_tahoma_bold) }

    /** the reviewer's count views (new, learn, due) and flag/mark icons, drawn at the right */
    var counts: List<TextView> = emptyList()
    var icons: List<android.widget.ImageView> = emptyList()

    /** fraction of the combo clock left, or < 0 when hidden */
    var timeLeft = -1f
        private set
    private var clockRunning = false
    private var juiceAt = 0L
    private var juiceBig = false

    init {
        background = ResourcesCompat.getDrawable(resources, R.drawable.xp_status, null)
    }

    fun setClock(
        left: Float,
        running: Boolean,
    ) {
        timeLeft = left
        clockRunning = running
        invalidate()
    }

    fun juice(big: Boolean) {
        juiceAt = SystemClock.uptimeMillis()
        juiceBig = big
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        XpGame.newDay()
        val g = XpGame.g
        val w = width.toFloat()
        // the combo clock along the bottom edge: blue while waiting for the word audio, green, red in the last 3 s
        val clockH = 4 * d
        val h = height.toFloat()
        if (timeLeft >= 0) {
            paint.shader = null
            paint.color = Color.rgb(0xc9, 0xc6, 0xb8)
            canvas.drawRect(0f, h - clockH, w, h, paint)
            paint.color =
                when {
                    !clockRunning -> Color.rgb(0x9d, 0xb3, 0xd6)
                    timeLeft * XpGame.limit <= 3 -> Color.rgb(0xe0, 0x20, 0x20)
                    else -> Color.rgb(0x2e, 0xd3, 0x30)
                }
            canvas.drawRect(0f, h - clockH, w * (if (clockRunning) timeLeft else 1f), h, paint)
        }
        val cy = (h - clockH) / 2
        val base = cy - (text.ascent() + text.descent()) / 2
        var x = 8 * d
        // goal ring
        val over = g.reps >= g.goal
        val p = if (over) min(1f, (g.reps - g.goal).toFloat() / g.goal) else g.reps.toFloat() / g.goal
        val r = 9 * d
        val ring = RectF(x, cy - r, x + 2 * r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4 * d
        paint.color = if (over) Color.rgb(0x2e, 0xd3, 0x30) else Color.rgb(0xc9, 0xc6, 0xb8)
        canvas.drawArc(ring, 0f, 360f, false, paint)
        paint.color = if (over) Color.rgb(0xff, 0xb3, 0x00) else Color.rgb(0x2e, 0xd3, 0x30)
        canvas.drawArc(ring, -90f, 360f * p, false, paint)
        paint.style = Paint.Style.FILL
        x += 2 * r + 6 * d

        fun word(
            s: String,
            pt: Paint = text,
        ) {
            pt.color = Color.BLACK
            canvas.drawText(s, x, base, pt)
            x += pt.measureText(s) + 8 * d
        }
        word("${g.reps}/${g.goal}")
        if (g.streak > 0) word("🔥${g.streak}")
        val lv = XpGame.level(g.xp)
        word("Lv ${lv + 1}", bold)
        // combo, right aligned, with its juice bounce
        val combo = XpGame.combo
        val mult = XpGame.mult()
        val multText = if (combo >= 5) "×${if (mult % 1.0 == 0.0) mult.toInt() else mult} " else ""
        val comboText = "combo $combo"
        val cp = Paint(if (combo >= 5) bold else text)
        cp.color =
            when {
                combo >= 25 -> Color.rgb(0xd0, 0, 0)
                combo >= 10 -> Color.rgb(0xe0, 0x70, 0)
                combo >= 5 -> Color.rgb(0xc0, 0, 0)
                else -> Color.BLACK
            }
        cp.textSize *=
            if (combo >= 25) {
                1.3f
            } else if (combo >= 10) {
                1.15f
            } else {
                1f
            }
        // counts (and the flag/mark icons) at the right end
        var rx = w - 8 * d
        for (tv in counts.asReversed()) {
            val s = tv.text?.toString().orEmpty()
            if (s.isEmpty() || tv.visibility == View.GONE) continue
            val cpaint = Paint(text).apply { color = tv.currentTextColor }
            rx -= cpaint.measureText(s)
            canvas.drawText(s, rx, base, cpaint)
            rx -= 6 * d
        }
        for (icon in icons.asReversed()) {
            val dr = icon.drawable ?: continue
            if (icon.visibility != View.VISIBLE) continue
            val size = 16 * d
            rx -= size
            dr.setBounds(rx.toInt(), (cy - size / 2).toInt(), (rx + size).toInt(), (cy + size / 2).toInt())
            dr.draw(canvas)
            rx -= 4 * d
        }
        val cw = cp.measureText(multText + comboText)
        val cx = rx - 6 * d - cw
        val k = (SystemClock.uptimeMillis() - juiceAt) / (if (juiceBig) 600f else 450f)
        canvas.save()
        if (k in 0f..1f) {
            val amp = if (juiceBig) 0.8 else 0.35
            val s = (1 + amp * exp(-4.0 * k) * cos(k * 3 * PI)).toFloat()
            canvas.rotate((if (juiceBig) -10 else -6) * exp(-4.0 * k).toFloat() * cos(k * 3 * PI).toFloat(), cx + cw / 2, cy)
            canvas.scale(s, s, cx + cw / 2, cy)
            postInvalidateOnAnimation()
        }
        if (combo >= 10 && multText.isNotEmpty()) {
            // the ×N on fire, taller as the combo grows
            val mw = cp.measureText(multText.trim())
            val h = (cp.textSize * (1.1f + min(1f, (combo - 10) / 40f) * 0.9f)) * (0.9f + Random.nextFloat() * 0.25f)
            paint.shader =
                LinearGradient(
                    0f,
                    base + 4 * d,
                    0f,
                    base + 4 * d - h,
                    intArrayOf(Color.rgb(0xff, 0x40, 0), Color.rgb(0xff, 0xb0, 0), Color.argb(0, 0xff, 0xf6, 0xa0)),
                    null,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRoundRect(RectF(cx - 3 * d, base + 4 * d - h, cx + mw + 3 * d, base + 4 * d), 4 * d, 4 * d, paint)
            paint.shader = null
            val white = Paint(cp).apply { color = Color.WHITE }
            white.setShadowLayer(3f, 1f, 1f, Color.rgb(0xa0, 0, 0))
            canvas.drawText(multText, cx, base, white)
            canvas.drawText(comboText, cx + cp.measureText(multText), base, cp)
            postInvalidateDelayed(80)
        } else {
            canvas.drawText(multText + comboText, cx, base, cp)
        }
        canvas.restore()
        // XP bar between the level and the combo
        val right = cx - 10 * d
        if (right - x > 30 * d) {
            val lo = XpGame.levelStart(lv)
            val frac = ((g.xp - lo).toFloat() / (100 * (lv + 1))).coerceIn(0f, 1f)
            val top = cy - 6 * d
            val bottom = cy + 6 * d
            paint.color = Color.rgb(0x68, 0x68, 0x68)
            canvas.drawRect(x, top, right, bottom, paint)
            paint.color = Color.WHITE
            canvas.drawRect(x + d, top + d, right - d, bottom - d, paint)
            paint.shader =
                LinearGradient(
                    0f,
                    top,
                    0f,
                    bottom,
                    intArrayOf(Color.rgb(0xa5, 0xc8, 0xff), Color.rgb(0x3a, 0x7c, 0xe8), Color.rgb(0x25, 0x63, 0xd8)),
                    null,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(x + d, top + d, x + d + (right - x - 2 * d) * frac, bottom - d, paint)
            paint.shader = null
        }
    }
}
