// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.core.content.res.ResourcesCompat
import com.ichi2.anki.R

/**
 * kuma3: parts of the XP message box (layout/xp_alert_dialog.xml). The icon picks its kind from the dialog it sits
 * in: an error or a delete gets the red ✖, a warning the yellow triangle, a question (two or more buttons) the blue
 * "?", anything else the blue "i". No message (lists, custom views): no icon.
 */
class XpDialogIcon(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private enum class Kind { ERROR, WARNING, QUESTION, INFO }

    private var kind = Kind.INFO
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = ResourcesCompat.getFont(context, R.font.xp_tahoma_bold)
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // the dialog fills its views before showing; decide once they are there
        post {
            val root = rootView
            val message =
                root
                    .findViewById<TextView>(android.R.id.message)
                    ?.text
                    ?.toString()
                    .orEmpty()
            if (message.isBlank()) {
                visibility = GONE
                return@post
            }
            val buttons =
                listOf(android.R.id.button1, android.R.id.button2, android.R.id.button3).count {
                    root.findViewById<View>(it)?.visibility ==
                        VISIBLE
                }
            val m = message.lowercase()
            kind =
                when {
                    ERROR.containsMatchIn(m) -> Kind.ERROR
                    WARNING.containsMatchIn(m) -> Kind.WARNING
                    buttons >= 2 || m.trimEnd().endsWith("?") -> Kind.QUESTION
                    else -> Kind.INFO
                }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2
        if (kind == Kind.WARNING) {
            val path =
                Path().apply {
                    moveTo(w / 2, h * 0.04f)
                    lineTo(w * 0.98f, h * 0.94f)
                    lineTo(w * 0.02f, h * 0.94f)
                    close()
                }
            paint.shader = null
            paint.color = Color.rgb(0x8a, 0x6d, 0x00)
            canvas.drawPath(path, paint)
            paint.shader =
                RadialGradient(
                    w * 0.4f,
                    h * 0.35f,
                    r * 1.4f,
                    Color.rgb(0xff, 0xe8, 0x6a),
                    Color.rgb(0xf5, 0xc4, 0x00),
                    Shader.TileMode.CLAMP,
                )
            canvas.save()
            canvas.scale(0.9f, 0.9f, w / 2, h * 0.62f)
            canvas.drawPath(path, paint)
            canvas.restore()
            glyph.color = Color.BLACK
            glyph.textSize = h * 0.55f
            canvas.drawText("!", w / 2, h * 0.85f, glyph)
            return
        }
        val (light, mid, dark, symbol) =
            when (kind) {
                Kind.ERROR -> listOf(Color.rgb(0xff, 0x9a, 0x8a), Color.rgb(0xe0, 0x2a, 0x10), Color.rgb(0x9a, 0x13, 0x00), 0)
                Kind.QUESTION -> listOf(Color.rgb(0x8f, 0xb8, 0xff), Color.rgb(0x1f, 0x5f, 0xd6), Color.rgb(0x0b, 0x3a, 0x9e), 1)
                else -> listOf(Color.rgb(0x9f, 0xd0, 0xff), Color.rgb(0x2b, 0x7b, 0xe0), Color.rgb(0x0f, 0x4a, 0xa8), 2)
            }
        paint.shader =
            RadialGradient(w * 0.35f, h * 0.3f, r * 1.3f, intArrayOf(light, mid, dark), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2, h / 2, r * 0.96f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.06f
        paint.color = Color.WHITE
        canvas.drawCircle(w / 2, h / 2, r * 0.9f, paint)
        paint.style = Paint.Style.FILL
        glyph.color = Color.WHITE
        glyph.textSize = h * 0.62f
        val text = listOf("✕", "?", "i")[symbol]
        canvas.drawText(text, w / 2, h / 2 - (glyph.ascent() + glyph.descent()) / 2, glyph)
    }

    private companion object {
        val ERROR = Regex("error|fail|could ?n.t|unable|delete|remove|corrupt|invalid|not a valid")
        val WARNING = Regex("warning|may take|cannot be undone|can't be undone|lose|overwrite|reset|forget|full sync|one-way")
    }
}

/** The red close box of the XP message box: Cancel when the dialog has one, else Back (cancels the dialog). */
class XpDialogClose(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        contentDescription = context.getString(android.R.string.cancel)
        setOnClickListener {
            val cancel = rootView.findViewById<View>(android.R.id.button2)
            if (cancel != null && cancel.visibility == VISIBLE) {
                cancel.performClick()
            } else {
                // Back: AppCompat dialogs (ComponentDialog) handle it through their OnBackPressedDispatcher, which
                // cancels the dialog; key events are no longer used for Back on recent Android
                val owner = rootView.findViewTreeOnBackPressedDispatcherOwner()
                if (owner != null) {
                    owner.onBackPressedDispatcher.onBackPressed()
                } else {
                    val t = android.os.SystemClock.uptimeMillis()
                    rootView.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
                    rootView.dispatchKeyEvent(
                        KeyEvent(
                            t,
                            t,
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_BACK,
                            0,
                            0,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,
                            0,
                            KeyEvent.FLAG_TRACKING,
                        ),
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val box = RectF(d, d, width - d, height - d)
        paint.shader =
            RadialGradient(
                box.right * 0.9f,
                box.bottom * 0.9f,
                box.width(),
                intArrayOf(
                    Color.rgb(0xcc, 0x46, 0x00),
                    Color.rgb(0xdc, 0x65, 0x27),
                    Color.rgb(0xcd, 0x75, 0x46),
                    Color.rgb(0xff, 0xcc, 0xb2),
                ),
                floatArrayOf(0f, 0.55f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(box, 3 * d, 3 * d, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = d
        canvas.drawRoundRect(box, 3 * d, 3 * d, paint)
        paint.strokeWidth = 2.4f * d
        val p = box.width() * 0.3f
        canvas.drawLine(box.left + p, box.top + p, box.right - p, box.bottom - p, paint)
        canvas.drawLine(box.right - p, box.top + p, box.left + p, box.bottom - p, paint)
    }
}
