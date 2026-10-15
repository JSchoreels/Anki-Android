// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.ui.windows.reviewer.AnswerButton
import com.ichi2.themes.Themes
import java.util.WeakHashMap

/**
 * kuma3: the Anki XP chrome on top of the XP day theme (theme_xp.xml has the colours). Restyles views the theme
 * can't reach: toolbars become Luna title bars with caption-button actions, Material buttons become XP push buttons,
 * the deck list becomes a white Explorer list, every screen gets the skin's CRT scanlines. One call from AnkiActivity.onPostCreate.
 */
object XpChrome {
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
        watchWindows()
        dressWindow(activity.window.decorView)
        if (activity is DeckPicker) {
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
        // the Player box, with the other devices' parts (kuma3 Skins, the tablet): without them the level counts this phone only
        activity.launchCatchingTask {
            withCol { XpGame.readNotes(this) }
            XpHome.resume()
        }
    }

    private var watching = false

    /**
     * The XP looks and the CRT on every window the app opens, not only activities: popup menus, bottom sheets and dialogs are windows
     * of their own. Swaps WindowManagerGlobal's list of root views for one that sees each new window (what Square's
     * curtains library does). False when the platform refuses, then only activities get them.
     */
    @SuppressLint("PrivateApi") // on purpose: the one way to see every window; falls back to activities if refused
    private fun watchWindows(): Boolean {
        if (watching) return true
        watching =
            runCatching {
                val global = Class.forName("android.view.WindowManagerGlobal")
                val instance = global.getMethod("getInstance").invoke(null)
                val field = global.getDeclaredField("mViews").apply { isAccessible = true }

                @Suppress("UNCHECKED_CAST")
                val old = field.get(instance) as ArrayList<View>
                field.set(
                    instance,
                    object : ArrayList<View>(old) {
                        override fun add(element: View): Boolean {
                            if (isOn()) dressWindow(element)
                            return super.add(element)
                        }
                    },
                )
            }.onFailure { timber.log.Timber.w(it, "Anki XP: CRT on dialogs and menus") }.isSuccess
        return watching
    }

    /**
     * XP looks (title bars, buttons, lists) and the CRT on a window's root view. An activity gets the whole CRT; a
     * menu or dialog sits over one, so it gets only scanlines, only on its first solid background (not on its shadow
     * or the dimmed screen), in step with the screen's lines underneath.
     */
    private val dressedWindows = WeakHashMap<View, Unit>()

    private fun dressWindow(root: View) {
        if (dressedWindows.put(root, Unit) != null) return // from install and again from the window hook
        val dressed = WeakHashMap<View, Unit>()
        // a window opened under Anki XP keeps the look only while the theme is still Anki XP
        root.viewTreeObserver.addOnGlobalLayoutListener { if (isOn()) walk(root, dressed) }
        var on: View? = null
        val crt = Crt(root.context)
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val screen = (root.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION
            crt.rim = screen
            val target = if (screen) root else solid(root) ?: return@addOnGlobalLayoutListener
            if (target !== on) {
                on?.overlay?.remove(crt)
                target.overlay.add(crt)
                on = target
            }
            val at = IntArray(2).also { target.getLocationOnScreen(it) }
            crt.place(target.width, target.height, at[1])
        }
    }

    /** the first view with a background that isn't see-through */
    private fun solid(v: View): View? {
        val bg = v.background
        if (bg != null && !(bg is ColorDrawable && bg.alpha == 0)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) solid(v.getChildAt(i))?.let { return it }
        return null
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
            // a title view of its own (Statistics deck name and its ▾): white like the toolbar title
            v is TextView && parent is Toolbar -> {
                v.setTextColor(Color.WHITE)
                TextViewCompat.setCompoundDrawableTintList(v, WHITE_ICONS)
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
                (v.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(px(ctx, 6f))
            }
        }
    }

    private fun ViewGroup.MarginLayoutParams.setMargins(m: Int) = setMargins(m, m, m, m)

    fun px(
        context: android.content.Context,
        dp: Float,
    ) = (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    /** The skin's CRT look: a dark line every 3dp and a darker [rim]. Drawn over the window, never blurs text. */
    private class Crt(
        context: android.content.Context,
    ) : Drawable() {
        var rim = false
        private val period = px(context, 3f).coerceAtLeast(3)
        private val lines =
            Paint().apply {
                val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
                for (y in 0 until (period / 3).coerceAtLeast(1)) bmp.setPixel(0, y, Color.argb(30, 0, 0, 0))
                shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        private val edge = Paint()

        /** [w]×[h] at [top] px from the screen's top: the lines stay on the same screen rows in every window */
        fun place(
            w: Int,
            h: Int,
            top: Int,
        ) {
            lines.shader.setLocalMatrix(Matrix().apply { setTranslate(0f, -(top % period).toFloat()) })
            setBounds(0, 0, w, h)
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            val r = Math.hypot(bounds.width().toDouble(), bounds.height().toDouble()).toFloat() / 2
            if (r <= 0f) return
            edge.shader =
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
            if (!XpGame.crt || !isOn()) return
            canvas.drawRect(bounds, lines)
            if (rim) canvas.drawRect(bounds, edge)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
