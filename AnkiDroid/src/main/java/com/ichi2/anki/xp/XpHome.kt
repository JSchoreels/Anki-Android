// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.widgets.DeckAdapter

/**
 * kuma3: the Anki XP deck list as XP group boxes: Player (level, XP, streak, quests), Decks (only the decks with
 * cards due; the finished ones fold into one row), Activity (heatmap) and RWKV recall. Stock list with XP off.
 */
object XpHome {
    private var player: Player? = null
    private var done: Done? = null
    private var today: Today? = null

    /** the deck search is open: show every deck */
    var searching = false
        set(v) {
            if (field == v) return
            field = v
            done?.resubmit()
        }

    /** the deck picker's adapter: [footers] are (label, adapter) in order after the deck list */
    fun adapter(
        picker: DeckPicker,
        list: RecyclerView,
        decks: DeckAdapter,
        footers: List<Pair<String, RecyclerView.Adapter<*>>>,
    ): RecyclerView.Adapter<*> {
        if (!XpChrome.isOn()) {
            player = null
            done = null
            today = null
            return ConcatAdapter(listOf(decks) + footers.map { it.second })
        }
        val p = Player(picker).also { player = it }
        val d = Done(decks).also { done = it }
        // the "Studied … today" line and the RWKV forecast: a box after RWKV recall, not a bar at the bottom
        val t = Today().also { today = it }
        picker.deckPickerBinding.reviewSummaryTextView.visibility = View.GONE
        val rest = footers.take(2) + ("Today" to t) + footers.drop(2)
        val labels = mutableMapOf<RecyclerView.Adapter<*>, String>(p to "Player", decks to "Decks", d to "Decks")
        rest.forEach { labels[it.second] = it.first }
        val concat = ConcatAdapter(listOf(p, decks, d) + rest.map { it.second })
        list.addItemDecoration(Boxes(picker, concat, labels))
        return concat
    }

    /** DeckPicker.onDeckListChanged */
    fun submit(
        decks: DeckAdapter,
        data: List<DisplayDeckNode>,
        hasSubDecks: Boolean,
    ) {
        val d = done
        if (d == null || d.decks !== decks) return decks.submit(data, hasSubDecks)
        d.last = data
        d.hasSubDecks = hasSubDecks
        d.resubmit()
    }

    /** RwkvForecast.show: the summary line, with its colours and links */
    fun summary(text: CharSequence) {
        today?.let {
            it.text = text
            @Suppress("NotifyDataSetChanged") // zero or one row
            it.notifyDataSetChanged()
        }
    }

    /** DeckPicker.onResume, after the game's new day */
    fun resume() {
        player?.notifyItemChanged(0)
    }

    private fun due(n: DisplayDeckNode) = n.newCount + n.lrnCount + n.revCount

    private fun px(
        context: Context,
        dp: Float,
    ) = (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun text(
        context: Context,
        sp: Float,
    ) = TextView(context).apply {
        textSize = sp
        setTextColor(Color.BLACK)
        typeface = ResourcesCompat.getFont(context, R.font.xp_tahoma)
    }

    private class Holder(
        val frame: FrameLayout,
    ) : RecyclerView.ViewHolder(frame)

    private fun frame(parent: ViewGroup) =
        Holder(
            FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            },
        )

    /** Lv 4 [=====     ] 45/500 XP, then streak, quests, today's reps and Trophies */
    private class Player(
        val picker: DeckPicker,
    ) : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = 1

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = frame(parent)

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val c = holder.frame.context
            XpGame.init(c)
            val g = XpGame.g
            val lv = XpGame.level(g.xp)
            // level L starts at 50·L·(L+1) XP (the inverse of XpGame.level)
            val from = 50 * lv * (lv + 1)
            val span = 100 * (lv + 1)
            val quests = XpGame.quests()
            val got = quests.count { it.id in g.today.got }
            val pad = px(c, 10f)
            val top =
                LinearLayout(c).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(c, 16f).apply {
                            text = "Lv ${lv + 1}"
                            typeface = ResourcesCompat.getFont(c, R.font.xp_tahoma_bold)
                        },
                    )
                    addView(
                        Bar(c, (g.xp - from).toFloat() / span),
                        LinearLayout.LayoutParams(0, px(c, 12f), 1f).apply {
                            marginStart = pad
                            marginEnd = pad
                        },
                    )
                    addView(text(c, 13f).apply { text = "${g.xp - from}/$span XP" })
                }
            val bottom =
                LinearLayout(c).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, pad / 2, 0, 0)
                    addView(
                        text(c, 13f).apply {
                            text =
                                "🔥 ${g.streak} day${if (g.streak == 1) "" else "s"}   Quests $got/${quests.size}   Today ${g.reps}/${g.goal}"
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        text(c, 13f).apply {
                            text = "Trophies ▸"
                            setTextColor(Color.rgb(0x1d, 0x4f, 0xa8))
                            setOnClickListener { XpWindows.trophies(picker) }
                        },
                    )
                }
            holder.frame.removeAllViews()
            holder.frame.addView(
                LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad / 2, pad, pad / 2)
                    addView(top)
                    addView(bottom)
                    setOnClickListener { XpWindows.quests(picker) }
                },
            )
        }
    }

    /** an XP progress bar: blue fill in a white trough */
    private class Bar(
        context: Context,
        val f: Float,
    ) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.rgb(0x31, 0x6a, 0xc5)
            canvas.drawRect(1f, 1f, 1 + (width - 2) * f.coerceIn(0f, 1f), height - 1f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(0x7f, 0x9d, 0xb9)
            canvas.drawRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, paint)
        }
    }

    /** "▸ 5 decks done for today": shows or folds the decks with nothing due */
    private class Done(
        val decks: DeckAdapter,
    ) : RecyclerView.Adapter<Holder>() {
        var last: List<DisplayDeckNode> = emptyList()
        var hasSubDecks = false
        private var open = false
        private var hidden = 0

        fun resubmit() {
            val waiting = last.filter { due(it) > 0 }
            val was = hidden
            // nothing due anywhere: the whole list, not one fold row
            hidden = if (searching || waiting.isEmpty()) 0 else last.size - waiting.size
            decks.submit(if (open || hidden == 0) last else waiting, hasSubDecks)
            if ((was > 0) != (hidden > 0)) {
                @Suppress("NotifyDataSetChanged") // zero or one row
                notifyDataSetChanged()
            } else if (hidden > 0) {
                notifyItemChanged(0)
            }
        }

        override fun getItemCount() = if (hidden > 0) 1 else 0

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = frame(parent)

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val c = holder.frame.context
            holder.frame.removeAllViews()
            holder.frame.addView(
                text(c, 14f).apply {
                    text = if (open) "▾ Hide $hidden decks done for today" else "▸ $hidden decks done for today"
                    setTextColor(Color.rgb(0x80, 0x80, 0x80))
                    setPadding(px(c, 16f), px(c, 10f), px(c, 16f), px(c, 10f))
                    setOnClickListener {
                        open = !open
                        resubmit()
                    }
                },
            )
        }
    }

    /** the "Studied … today" line and the RWKV forecast */
    private class Today : RecyclerView.Adapter<Holder>() {
        var text: CharSequence = ""

        override fun getItemCount() = if (text.isEmpty()) 0 else 1

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = frame(parent)

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val c = holder.frame.context
            holder.frame.removeAllViews()
            holder.frame.addView(
                text(c, 14f).apply {
                    text = this@Today.text
                    movementMethod =
                        android.text.method.LinkMovementMethod
                            .getInstance()
                    setPadding(px(c, 10f), px(c, 4f), px(c, 10f), px(c, 6f))
                },
            )
        }
    }

    /** XP group boxes: a thin rounded frame around each section with its label set into the top edge */
    private class Boxes(
        context: Context,
        val concat: ConcatAdapter,
        val labels: Map<RecyclerView.Adapter<*>, String>,
    ) : RecyclerView.ItemDecoration() {
        private val side = px(context, 8f)
        private val head = px(context, 20f)
        private val foot = px(context, 8f)
        private val frame =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = context.resources.displayMetrics.density
                color = Color.rgb(0xd0, 0xd0, 0xbf)
            }
        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x00, 0x46, 0xd5)
                textSize = px(context, 13f).toFloat()
                typeface = ResourcesCompat.getFont(context, R.font.xp_tahoma)
            }
        private val clear = Paint().apply { color = Color.WHITE }

        private fun labelAt(pos: Int): String? =
            if (pos < 0 || pos >= concat.itemCount) null else labels[concat.getWrappedAdapterAndPosition(pos).first]

        override fun getItemOffsets(
            out: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val pos = parent.getChildAdapterPosition(view)
            val l = labelAt(pos) ?: return
            out.left = side
            out.right = side
            if (labelAt(pos - 1) != l) out.top = head
            if (labelAt(pos + 1) != l) out.bottom = foot
        }

        override fun onDraw(
            c: Canvas,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val inset = side / 2f
            var i = 0
            while (i < parent.childCount) {
                val first = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(first)
                val l = labelAt(pos)
                if (l == null) {
                    i++
                    continue
                }
                var j = i
                while (j + 1 < parent.childCount && labelAt(parent.getChildAdapterPosition(parent.getChildAt(j + 1))) == l) j++
                val last = parent.getChildAt(j)
                val startsHere = labelAt(pos - 1) != l
                val endsHere = labelAt(parent.getChildAdapterPosition(last) + 1) != l
                val top = if (startsHere) first.top - head / 2f else -side.toFloat()
                val bottom = if (endsHere) last.bottom + foot / 2f else parent.height + side.toFloat()
                c.drawRoundRect(RectF(inset, top, parent.width - inset, bottom), side / 2f, side / 2f, frame)
                if (startsHere) {
                    val x = side * 2f
                    val w = label.measureText(l)
                    c.drawRect(x - 4, top - label.textSize / 2, x + w + 4, top + label.textSize / 2, clear)
                    c.drawText(l, x, top + label.textSize / 3, label)
                }
                i = j + 1
            }
        }
    }
}
