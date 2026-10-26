// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.kuma3.lofi

import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.kuma3.Kuma3Settings

/**
 * The Lofi button in the top bar of the deck list and the reviewer (the add-on's toolbar button):
 * tap = play / pause (bright while playing, the track in its tooltip), long-press = [menu]: next
 * track, volume, source, offline downloads, own folder.
 */
object LofiButton {
    /** Adds the button to an activity's options menu (call from onCreateOptionsMenu). */
    fun addTo(
        activity: AppCompatActivity,
        menu: Menu,
    ) {
        if (!Kuma3Settings.lofi) return
        val button =
            ImageButton(activity, null, androidx.appcompat.R.attr.actionButtonStyle).apply {
                setImageResource(R.drawable.ic_kuma3_lofi)
                contentDescription = activity.getString(R.string.kuma3_lofi)
                setOnClickListener { Lofi.toggle() }
                setOnLongClickListener {
                    menu(activity)
                    true
                }
            }

        fun refresh() {
            button.alpha = if (Lofi.isPlaying) 1f else 0.55f
            TooltipCompat.setTooltipText(
                button,
                Lofi.trackTitle?.let { activity.getString(R.string.kuma3_lofi_tooltip_track, it) }
                    ?: activity.getString(R.string.kuma3_lofi_tooltip),
            )
        }
        refresh()
        val listener = ::refresh
        Lofi.listeners += listener
        // the reviewer rebuilds its menu for every card: drop this button's listener with it
        button.addOnAttachStateChangeListener(
            object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) = Unit

                override fun onViewDetachedFromWindow(v: android.view.View) {
                    Lofi.listeners -= listener
                }
            },
        )
        menu
            .add(Menu.NONE, R.id.action_kuma3_lofi, Menu.NONE, R.string.kuma3_lofi)
            .setActionView(button)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun menu(activity: AppCompatActivity) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val track = TextView(activity).apply { text = Lofi.trackTitle ?: activity.getString(R.string.kuma3_lofi_nothing_playing) }
        val volumeLabel = TextView(activity).apply { text = activity.getString(R.string.kuma3_lofi_volume) }
        val volume =
            SeekBar(activity).apply {
                max = 100
                progress = Lofi.volume
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            if (fromUser) Lofi.volume = progress
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                    },
                )
            }
        val layout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad / 2, pad, 0)
                addView(track)
                addView(volumeLabel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(volume)
                gravity = Gravity.CENTER_VERTICAL
            }
        val sources = Lofi.Source.entries
        lateinit var dialog: AlertDialog
        dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.kuma3_lofi)
                .setView(layout)
                .setSingleChoiceItems(
                    sources.map { activity.getString(it.label) }.toTypedArray(),
                    sources.indexOf(Lofi.source),
                ) { _, which ->
                    pick(activity, sources[which])
                    dialog.dismiss()
                }.setPositiveButton(R.string.kuma3_lofi_next) { _, _ -> Lofi.next() }
                .setNeutralButton(R.string.kuma3_lofi_downloads) { _, _ -> downloads(activity) }
                .setNegativeButton(if (Lofi.isPlaying) R.string.kuma3_lofi_pause else R.string.kuma3_lofi_play) { _, _ -> Lofi.toggle() }
                .show()
    }

    private fun pick(
        activity: AppCompatActivity,
        source: Lofi.Source,
    ) {
        when {
            source == Lofi.Source.FOLDER -> chooseFolder(activity)
            source == Lofi.Source.DOWNLOADED && Lofi.downloadedCount() == 0 -> {
                showThemedToast(activity, activity.getString(R.string.kuma3_lofi_nothing_downloaded), true)
                downloads(activity)
            }
            else -> Lofi.source = source
        }
    }

    /** Offline: queue tracks in Android's download manager (about 3–8 MB each), or delete them. */
    private fun downloads(activity: AppCompatActivity) {
        val choices =
            listOf(
                activity.getString(R.string.kuma3_lofi_download_chillhop, 20) to { Lofi.download(Lofi.Source.CHILLHOP, 20) },
                activity.getString(R.string.kuma3_lofi_download_chillhop, 50) to { Lofi.download(Lofi.Source.CHILLHOP, 50) },
                activity.getString(R.string.kuma3_lofi_download_lofigirl, 20) to { Lofi.download(Lofi.Source.LOFIGIRL, 20) },
                activity.getString(R.string.kuma3_lofi_download_lofigirl, 50) to { Lofi.download(Lofi.Source.LOFIGIRL, 50) },
            )
        AlertDialog
            .Builder(activity)
            .setTitle(activity.getString(R.string.kuma3_lofi_download_title, Lofi.downloadedCount()))
            .setItems(choices.map { it.first }.toTypedArray()) { _, which ->
                val queued = choices[which].second()
                val downloaded = activity.getString(R.string.kuma3_lofi_source_downloaded)
                showThemedToast(activity, activity.getString(R.string.kuma3_lofi_downloading, queued, downloaded), false)
            }.setNegativeButton(R.string.kuma3_lofi_delete_downloads) { _, _ -> Lofi.deleteDownloads() }
            .show()
    }

    private fun chooseFolder(activity: AppCompatActivity) {
        val launcher =
            activity.activityResultRegistry.register("kuma3_lofi_folder", ActivityResultContracts.OpenDocumentTree()) { tree ->
                if (tree != null) Lofi.useFolder(tree)
            }
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = launcher.unregister()
            },
        )
        launcher.launch(Lofi.folder)
    }
}
