// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.ui.windows.reviewer.AnswerButton
import com.ichi2.themes.Themes
import java.util.WeakHashMap

/**
 * kuma3: the Anki XP chrome on top of the XP day theme (theme_xp.xml has the colours). Restyles views the theme
 * can't reach: toolbars become Luna title bars with caption-button actions, Material buttons become XP push buttons,
 * the deck list becomes a white Explorer list, the deck picker gets the taskbar (Start opens the side menu) and
 * every screen gets the skin's CRT scanlines. One call from AnkiActivity.onPostCreate.
 */
object XpChrome {
    const val TASKBAR_DP = 40

    /** toolbar icons: white, half white when disabled */
    private val WHITE_ICONS =
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.argb(128, 255, 255, 255), Color.WHITE),
        )

    private var startupPlayed = false

    fun isOn() = Themes.currentTheme == DayTheme.XP

    fun install(activity: AnkiActivity) {
        if (!isOn()) return
        XpGame.init(activity)
        val decor = activity.window.decorView
        val dressed = WeakHashMap<View, Unit>()
        decor.viewTreeObserver.addOnGlobalLayoutListener { walk(decor, dressed) }
        val crt = Crt(activity.resources.displayMetrics.density)
        decor.overlay.add(crt)
        decor.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> crt.setBounds(0, 0, v.width, v.height) }
        if (activity is DeckPicker) {
            addTaskbar(activity)
            if (!startupPlayed) {
                startupPlayed = true
                XpSound.startup()
            }
        }
    }

    /** DeckPicker.onResume: the Player box's level and streak */
    fun resume(activity: DeckPicker) {
        if (!isOn()) return
        XpGame.newDay()
        XpHome.resume() // the Player box
    }

    /** the Retro CRT setting changed: redraw the open screen */
    fun crtChanged(context: android.content.Context) {
        (context as? android.app.Activity)?.window?.decorView?.invalidate()
    }

    /** an XP push button (the heatmap's < T > Month) */
    fun pushButton(v: TextView) {
        if (!isOn()) return
        v.background = ContextCompat.getDrawable(v.context, R.drawable.xp_button_ripple)
        v.setTextColor(Color.BLACK)
    }

    /** px the taskbar takes above the navigation bar (0 when there is none) */
    fun taskbarHeight(activity: AnkiActivity): Int =
        if (activity.findViewById<View?>(R.id.xp_taskbar)?.isVisible ==
            true
        ) {
            px(activity.resources.displayMetrics.density, TASKBAR_DP)
        } else {
            0
        }

    private fun walk(
        v: View,
        dressed: WeakHashMap<View, Unit>,
    ) {
        if (v is WebView) return
        if (!dressed.containsKey(v)) {
            dressed[v] = Unit
            dress(v)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), dressed)
    }

    private fun dress(v: View) {
        val ctx = v.context
        val parent = v.parent
        // Material's letter spacing spreads Tahoma out; XP has none
        if (v is TextView) v.letterSpacing = 0f
        when {
            v is Toolbar -> {
                v.background = ContextCompat.getDrawable(ctx, R.drawable.xp_titlebar)
                // white on Luna blue, also on toolbars outside the app bar theme (congrats, other pages)
                v.setTitleTextColor(Color.WHITE)
                v.setSubtitleTextColor(Color.WHITE)
                v.overflowIcon?.setTint(Color.WHITE)
                (v as? MaterialToolbar)?.setNavigationIconTint(Color.WHITE)
            }
            parent is Toolbar && v is ImageButton -> v.setColorFilter(Color.WHITE)
            // a text action ("Custom study"): white bold text, no caption box
            parent is ActionMenuView && v is TextView && v.text.isNotEmpty() -> {
                v.setTextColor(Color.WHITE)
                v.typeface = ResourcesCompat.getFont(ctx, R.font.xp_tahoma_bold)
                v.setShadowLayer(1f, 1.5f, 1.5f, Color.rgb(0x0f, 0x10, 0x89))
                TextViewCompat.setCompoundDrawableTintList(v, WHITE_ICONS)
            }
            parent is ActionMenuView -> {
                v.background = ContextCompat.getDrawable(ctx, R.drawable.xp_caption)
                if (v is TextView) TextViewCompat.setCompoundDrawableTintList(v, WHITE_ICONS)
                if (v is ImageView) v.setColorFilter(Color.WHITE)
            }
            v is TextView && parent is Toolbar -> {
                v.typeface = ResourcesCompat.getFont(ctx, R.font.xp_tahoma_bold)
                v.setShadowLayer(1f, 1.5f, 1.5f, Color.rgb(0x0f, 0x10, 0x89))
            }
            // the side menu as the Start menu's white list
            v is NavigationView -> {
                v.setBackgroundColor(Color.WHITE)
                val colors =
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(Color.rgb(0x21, 0x5d, 0xc6), Color.BLACK),
                    )
                v.itemTextColor = colors
                v.itemIconTintList = colors
            }
            v is MaterialButton && v !is AnswerButton && v.text.isNotEmpty() -> {
                v.backgroundTintList = null
                v.background = ContextCompat.getDrawable(ctx, R.drawable.xp_button_ripple)
                v.setTextColor(Color.BLACK)
                v.cornerRadius = 0
            }
            v.id == R.id.deck_name && v is TextView -> {
                v.typeface = Typeface.create(v.typeface, Typeface.NORMAL)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            v.id == R.id.decks -> {
                v.background = ContextCompat.getDrawable(ctx, R.drawable.xp_list)
                (v.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(px(ctx.resources.displayMetrics.density, 6))
            }
            v.id == R.id.review_summary_text_view -> {
                v.background = ContextCompat.getDrawable(ctx, R.drawable.xp_status)
                (v as TextView).gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                v.setPaddingRelative(px(ctx.resources.displayMetrics.density, 8), v.paddingTop, v.paddingEnd, v.paddingBottom)
            }
        }
    }

    private fun ViewGroup.MarginLayoutParams.setMargins(m: Int) = setMargins(m, m, m, m)

    private fun addTaskbar(activity: DeckPicker) {
        val root = activity.findViewById<View>(R.id.deck_picker_content_wrapper)?.parent as? CoordinatorLayout ?: return
        val bar = LayoutInflater.from(activity).inflate(R.layout.xp_taskbar, root, false)
        root.addView(
            bar,
            CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity =
                    android.view.Gravity.BOTTOM
            },
        )
        bar.findViewById<View>(R.id.xp_start).setOnClickListener { activity.onNavigationPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
    }

    private fun px(
        density: Float,
        dp: Int,
    ) = (dp * density + 0.5f).toInt()

    /** The skin's CRT look: a dark line every 3dp and a darker rim. Drawn over the window, never blurs text. */
    private class Crt(
        density: Float,
    ) : Drawable() {
        private val lines =
            Paint().apply {
                val period = px(density, 3).coerceAtLeast(3)
                val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
                for (y in 0 until (period / 3).coerceAtLeast(1)) bmp.setPixel(0, y, Color.argb(30, 0, 0, 0))
                shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        private val rim = Paint()

        override fun onBoundsChange(bounds: Rect) {
            val r = Math.hypot(bounds.width().toDouble(), bounds.height().toDouble()).toFloat() / 2
            if (r <= 0f) return
            rim.shader =
                RadialGradient(
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    r,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(80, 0, 0, 0)),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }

        override fun draw(canvas: Canvas) {
            if (!XpGame.crt) return
            canvas.drawRect(bounds, lines)
            canvas.drawRect(bounds, rim)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
