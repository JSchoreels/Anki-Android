// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

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
import com.ichi2.anki.R

/**
 * kuma3: AnkiDroid's progress dialog ("Processing...") as an XP progress window: Luna title bar, the XP frame, the
 * message beside the flag and a sunken bar with green blocks running through it. One call from
 * withProgressDialog in place of `dialog.show()`.
 */
object XpProgress {
    @Suppress("DEPRECATION") // ProgressDialog, like its caller
    fun show(dialog: android.app.ProgressDialog) {
        if (!XpChrome.isOn()) return dialog.show()
        dialog.setCustomTitle(XpWindows.titleBar(dialog.context, "Please wait..."))
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.xp_dialog)
        val spinner = dialog.findViewById<ProgressBar>(android.R.id.progress) ?: return
        val message = dialog.findViewById<TextView>(android.R.id.message)
        val row = spinner.parent as? LinearLayout ?: return
        val d = dialog.context.resources.displayMetrics.density
        spinner.visibility = View.GONE
        row.orientation = LinearLayout.VERTICAL
        message?.apply {
            setTextColor(Color.BLACK)
            setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.xp_flag), null, null, null)
            compoundDrawablePadding = (10 * d).toInt()
        }
        row.addView(
            Blocks(dialog.context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (20 * d).toInt()).apply { topMargin = (14 * d).toInt() },
        )
    }

    /** An AlertDialog builder (the media sync window) as an XP progress window: Luna title and the green blocks */
    fun dress(
        builder: androidx.appcompat.app.AlertDialog.Builder,
        title: String,
    ) {
        if (!XpChrome.isOn()) return
        val context = builder.context
        val d = context.resources.displayMetrics.density
        builder.setCustomTitle(XpWindows.titleBar(context, title))
        builder.setView(
            LinearLayout(context).apply {
                setPadding((20 * d).toInt(), (6 * d).toInt(), (20 * d).toInt(), (6 * d).toInt())
                addView(Blocks(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (20 * d).toInt()))
            },
        )
    }

    /** LoadingDialogFragment's dialog (Check media, …) as the same XP window; the message keeps the id R.id.text */
    fun loadingDialog(
        context: Context,
        message: String,
        cancellable: Boolean,
    ): androidx.appcompat.app.AlertDialog {
        val d = context.resources.displayMetrics.density
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
                addView(
                    TextView(context).apply {
                        id = R.id.text
                        text = message
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            ContextCompat.getDrawable(context, R.drawable.xp_flag),
                            null,
                            null,
                            null,
                        )
                        compoundDrawablePadding = (10 * d).toInt()
                    },
                )
                addView(
                    Blocks(context),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (20 * d).toInt()).apply { topMargin = (14 * d).toInt() },
                )
            }
        return androidx.appcompat.app.AlertDialog
            .Builder(context)
            .setCustomTitle(XpWindows.titleBar(context, "Please wait..."))
            .setView(body)
            .setCancelable(cancellable)
            .create()
            .apply { setCanceledOnTouchOutside(cancellable) }
    }

    /** XP's "working" bar: a white sunken box with a run of green blocks sliding through it */
    private class Blocks(
        context: Context,
    ) : View(context) {
        private val d = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val start = SystemClock.uptimeMillis()

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
            for (i in 0 until 8) {
                val left = x + i * (block + gap)
                canvas.drawRect(left, 3 * d, left + block, h - 3 * d, paint)
            }
            canvas.restore()
            postInvalidateOnAnimation()
        }
    }
}
