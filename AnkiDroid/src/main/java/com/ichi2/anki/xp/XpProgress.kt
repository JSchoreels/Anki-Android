// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.R
import com.ichi2.utils.dp
import com.ichi2.utils.setPaddingRelative

/**
 * kuma3: AnkiDroid's progress dialog ("Processing...") as an XP progress window: Luna title bar, the XP frame, the
 * message beside the flag and a sunken bar with green blocks running through it. One call from
 * withProgressDialog in place of `dialog.show()`.
 */
object XpProgress {
    private val AMOUNT = Regex("""(\d+)/(\d+)\s*$""")

    @Suppress("DEPRECATION") // ProgressDialog, like its caller
    fun show(dialog: android.app.ProgressDialog) {
        if (!XpChrome.isOn()) return dialog.show()
        dialog.setCustomTitle(XpWindows.titleBar(dialog.context, dialog.context.getString(R.string.kuma3_xp_please_wait)))
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.xp_dialog)
        val spinner = dialog.findViewById<ProgressBar>(android.R.id.progress) ?: return
        val message = dialog.findViewById<TextView>(android.R.id.message)
        val row = spinner.parent as? LinearLayout ?: return
        spinner.visibility = View.GONE
        row.orientation = LinearLayout.VERTICAL
        message?.flagged()
        row.addBlocks(14)
    }

    /** An AlertDialog builder (the media sync window) as an XP progress window: Luna title and the green blocks */
    fun dress(
        builder: androidx.appcompat.app.AlertDialog.Builder,
        title: String,
    ) {
        if (!XpChrome.isOn()) return
        val context = builder.context
        builder.setCustomTitle(XpWindows.titleBar(context, title))
        builder.setView(
            LinearLayout(context).apply {
                setPaddingRelative(20.dp, 6.dp, 20.dp, 6.dp)
                addBlocks(0)
            },
        )
    }

    /** LoadingDialogFragment's dialog (Check media, …) as the same XP window; the message keeps the id R.id.text */
    fun loadingDialog(
        context: Context,
        message: String,
        cancellable: Boolean,
    ): androidx.appcompat.app.AlertDialog {
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(20.dp, 18.dp, 20.dp, 18.dp)
                addView(
                    TextView(context).apply {
                        id = R.id.text
                        text = message
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        flagged()
                    },
                )
            }
        val blocks = body.addBlocks(14)
        val remaining =
            TextView(context).apply {
                textSize = 14f
                setTextColor(Color.BLACK)
                visibility = View.GONE
            }
        body.addView(
            remaining,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin =
                    8.dp.toPx(context)
            },
        )
        // A message ending in "done/total" (ProgressManager amounts): fill the bar and estimate the time left
        var firstAt = 0L
        var firstDone = 0L
        body.findViewById<TextView>(R.id.text).doAfterTextChanged { text ->
            val m = AMOUNT.find(text ?: "") ?: return@doAfterTextChanged
            val done = m.groupValues[1].toLong()
            val total = m.groupValues[2].toLong().coerceAtLeast(1)
            blocks.fraction = done.toFloat() / total
            val now = SystemClock.uptimeMillis()
            if (firstAt == 0L) {
                firstAt = now
                firstDone = done
                return@doAfterTextChanged
            }
            if (done <= firstDone) return@doAfterTextChanged
            val secs = (now - firstAt) / 1000.0 / (done - firstDone) * (total - done)
            remaining.visibility = View.VISIBLE
            remaining.text =
                with(remaining.resources) {
                    when {
                        secs < 60 -> secs.toInt().coerceAtLeast(1).let { getQuantityString(R.plurals.kuma3_xp_seconds_left, it, it) }
                        else -> (secs / 60).toInt().let { getQuantityString(R.plurals.kuma3_xp_minutes_left, it, it) }
                    }
                }
        }
        return androidx.appcompat.app.AlertDialog
            .Builder(context)
            .setCustomTitle(XpWindows.titleBar(context, context.getString(R.string.kuma3_xp_please_wait)))
            .setView(body)
            .setCancelable(cancellable)
            .create()
            .apply { setCanceledOnTouchOutside(cancellable) }
    }

    /** a progress message in black beside the XP flag */
    private fun TextView.flagged() {
        setTextColor(Color.BLACK)
        setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.xp_flag), null, null, null)
        compoundDrawablePadding = 10.dp.toPx(context)
    }

    /** the blocks bar, full width and 20 dp high, [top] dp below the view above it */
    private fun LinearLayout.addBlocks(top: Int) =
        Blocks(context).also {
            addView(
                it,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20.dp.toPx(context)).apply {
                    topMargin =
                        top.dp.toPx(context)
                },
            )
        }

    /** XP's "working" bar: a white sunken box with a run of green blocks sliding through it */
    private class Blocks(
        context: Context,
    ) : View(context) {
        private val d = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val start = SystemClock.uptimeMillis()

        /** < 0: unknown, the run of blocks slides; else the bar fills with blocks like XP's copy dialog */
        var fraction = -1f
            set(v) {
                field = v.coerceIn(0f, 1f)
                invalidate()
            }

        @SuppressLint("DrawAllocation") // ponytail: small per-frame allocations; preallocate the Paints/RectFs if a profile shows GC jank
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.shader = null
            paint.color = Color.rgb(0x68, 0x68, 0x68)
            canvas.drawRoundRect(0f, 0f, w, h, 3 * d, 3 * d, paint)
            paint.color = Color.WHITE
            canvas.drawRoundRect(d, d, w - d, h - d, 2 * d, 2 * d, paint)
            val block = 8 * d
            val gap = 2 * d
            val run = 8 * (block + gap)
            // the run enters on the left, crosses, leaves on the right, every 2 s
            val x = -run + ((SystemClock.uptimeMillis() - start) % 2000) / 2000f * (w + run)
            paint.shader =
                LinearGradient(
                    0f,
                    3 * d,
                    0f,
                    h - 3 * d,
                    intArrayOf(
                        Color.rgb(0xac, 0xed, 0xad),
                        Color.rgb(0x4c, 0xda, 0x50),
                        Color.rgb(0x2e, 0xd3, 0x30),
                        Color.rgb(0x42, 0xd8, 0x45),
                    ),
                    null,
                    Shader.TileMode.CLAMP,
                )
            canvas.save()
            canvas.clipRect(3 * d, 3 * d, w - 3 * d, h - 3 * d)
            if (fraction >= 0) {
                val end = 3 * d + (w - 6 * d) * fraction
                var left = 3 * d
                while (left < end) {
                    canvas.drawRect(left, 3 * d, minOf(left + block, end), h - 3 * d, paint)
                    left += block + gap
                }
            } else {
                for (i in 0 until 8) {
                    val left = x + i * (block + gap)
                    canvas.drawRect(left, 3 * d, left + block, h - 3 * d, paint)
                }
                postInvalidateOnAnimation()
            }
            canvas.restore()
        }
    }
}
