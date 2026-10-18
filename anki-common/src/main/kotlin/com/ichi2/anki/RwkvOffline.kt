// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.ankiweb.rsdroid.Backend
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * RWKV-Instant on the phone.
 *
 * The backend does the work: once [prepare] has handed it the model, it loads the model state and
 * brings it up to date with the review history on its own thread (the collection stays usable, with
 * the standard order, meanwhile), and scores review cards itself whenever a review queue or the
 * deck counts are built (see `rslib/src/scheduler/rwkv/offline.rs`). Nothing else in the app calls
 * RWKV. The backend keeps the state in `collection.rwkv-offline` next to the collection, so a start
 * normally replays only the reviews since it was saved.
 */
object RwkvOffline {
    private const val MODEL_ASSET = "rwkv/model.bin"

    /** Size of the bundled model (the desktop fork's `RWKV_trained_on_5000_10000.bin`). */
    private const val MODEL_BYTES = 11_082_637L

    /**
     * "Rebuilding RWKV" is shown only when this many reviews are replayed: a rebuild of the state
     * (first start, after an update), not a normal start.
     */
    private const val TOAST_AFTER_REPLAYED = 200L

    /**
     * How often, and how long, [refreshWhenReady] checks whether the backend's build is done. A full
     * rebuild of a 200k-review history takes ~35 s on a Pixel 8a; 10 minutes covers slow phones.
     */
    private const val READY_POLL_MS = 500L

    /** How long [prepareAfterOpen] leaves the collection to a first screen that isn't the deck list. */
    private const val FIRST_SCREEN_MS = 10_000L
    private const val READY_POLLS = 1200

    /** The progress notification, also [RwkvRebuildService]'s. */
    const val PROGRESS_NOTIFICATION_ID = 0x6b33 // "k3"
    private const val READY_NOTIFICATION_ID = 0x6b34

    /** The app module's NotificationChannel.SYNC (low importance), which this module cannot see. */
    private const val PROGRESS_CHANNEL = "Synchronization"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watcher: Job? = null
    private val readyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the backend's model state is installed; the deck list redraws its counts. */
    val ready: SharedFlow<Unit> = readyFlow

    /**
     * Queues [prepare] behind the open of the collection: whoever opened it (often the main
     * thread, e.g. BootService) doesn't wait for the backend's read of the review history, seconds
     * on a big collection (4M reviews: ~7 s on a Pixel 8a). It waits for the deck list's first
     * load ([deckListShown], at most [FIRST_SCREEN_MS]), which would otherwise queue behind it
     * ("Processing..." for 2-3 s at each start on a Tab S7+ with 3.8M reviews); [ready] redraws
     * the list with RWKV's counts anyway. [now]: don't wait (a settings change).
     */
    fun prepareAfterOpen(
        context: Context,
        now: Boolean = false,
    ) {
        scope.launch {
            if (!now) withTimeoutOrNull(FIRST_SCREEN_MS) { deckListShownFlow.first() }
            CollectionManager.withOpenColOrNull { prepare(context, backend) }
        }
    }

    private val deckListShownFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The deck list has drawn its counts: [prepareAfterOpen] may take the collection. */
    fun deckListShown() {
        deckListShownFlow.tryEmit(Unit)
    }

    /**
     * Must run on the collection queue, after the collection is opened.
     * On failure the standard review order applies.
     */
    private fun prepare(
        context: Context,
        backend: Backend,
    ) {
        try {
            val started = SystemClock.elapsedRealtime()
            val model = installModel(context)
            val replayed =
                backend.rwkvPrepareOffline(modelPath = model.absolutePath, statePrecision = statePrecision(context))
            Timber.i("RWKV prepare: replaying %s reviews in the background; %d ms", replayed, SystemClock.elapsedRealtime() - started)
            val announced = replayed >= TOAST_AFTER_REPLAYED && toastEnabled(context)
            if (announced) {
                showToast(context, "Rebuilding RWKV ($replayed reviews): standard order until done")
            }
            refreshWhenReady(context, replayed, announced)
        } catch (e: Exception) {
            Timber.w(e, "RWKV preparation failed; using the standard scheduler")
            showToast(context, "RWKV unavailable: standard review order")
        }
    }

    /**
     * The deck list was drawn with the standard counts while the backend built the model state on its
     * own thread: once that is done, the status call installs it, and [ready] redraws the deck list
     * with RWKV's counts. Not a ChangeManager change: an open reviewer would swap its current card (its
     * queue switches to RWKV's at the next answer anyway).
     */
    private fun refreshWhenReady(
        context: Context,
        replayed: Long,
        announced: Boolean,
    ) {
        val previous = watcher
        watcher =
            scope.launch {
                // its `finally` stops the service: before this one starts it
                previous?.cancelAndJoin()
                val started = SystemClock.elapsedRealtime()
                val progress = ReplayProgress(context)
                // a rebuild, not a normal start: keep the app running until it is done
                val service = replayed >= TOAST_AFTER_REPLAYED && RwkvRebuildService.start(context)
                try {
                    // ponytail: polls (the backend can't call back); a callback from the build thread if it matters
                    var polls = 0
                    while (true) {
                        delay(READY_POLL_MS)
                        val status =
                            CollectionManager.withOpenColOrNull {
                                backend.rwkvOfflineInstantPassStep(restart = false, statusOnly = true)
                            } ?: return@launch
                        if (status.available) {
                            Timber.i("RWKV ready: refreshing the deck counts")
                            readyFlow.tryEmit(Unit)
                            if (announced) {
                                val seconds = (SystemClock.elapsedRealtime() - started + 999) / 1000
                                notifyReady(context, "Rebuilt from $replayed reviews in $seconds s: reviews use RWKV's order")
                            }
                            return@launch
                        }
                        progress.update(status.replayDone, status.replayTotal)
                        // a replay still under way may take longer than the polls (4M reviews: ~10 min)
                        if (++polls >= READY_POLLS && status.replayTotal == 0L) return@launch
                    }
                } finally {
                    progress.cancel()
                    if (service) RwkvRebuildService.stop(context)
                }
            }
    }

    /**
     * A notification with a progress bar while the backend replays the review history (the first
     * start, after an update): "Preparing RWKV: 42% · about 6 min left". Silent, on the app's
     * low-importance sync channel; not shown if notifications are off (the toast still is).
     */
    private class ReplayProgress(
        private val context: Context,
    ) {
        private val manager = NotificationManagerCompat.from(context)
        private var firstDone = -1L
        private var firstAt = 0L
        private var shownPercent = -1

        @SuppressLint("MissingPermission") // checked in canNotify
        fun update(
            done: Long,
            total: Long,
        ) {
            if (total == 0L || !canNotify()) return
            val now = SystemClock.elapsedRealtime()
            if (firstDone < 0) {
                firstDone = done
                firstAt = now
            }
            val percent = (done * 100 / total).toInt()
            if (percent == shownPercent) return
            shownPercent = percent
            val rate = (done - firstDone).toDouble() / (now - firstAt).coerceAtLeast(1)
            val left =
                if (done > firstDone) {
                    val minutes = ((total - done) / rate / 60_000).toInt()
                    if (minutes < 1) " · less than a minute left" else " · about $minutes min left"
                } else {
                    ""
                }
            val notification =
                NotificationCompat
                    .Builder(context, PROGRESS_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("Preparing RWKV: $percent%$left")
                    .setContentText("First start with this history ($total reviews). Reviews use the standard order until it is done.")
                    .setProgress(100, percent, false)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .build()
            manager.notify(PROGRESS_NOTIFICATION_ID, notification)
        }

        fun cancel() {
            if (shownPercent >= 0) manager.cancel(PROGRESS_NOTIFICATION_ID)
        }

        private fun canNotify() = canNotify(context, manager)
    }

    /** [RwkvRebuildService]'s notification until [ReplayProgress] shows the first percentage. */
    fun preparingNotification(context: Context): Notification =
        NotificationCompat
            .Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Preparing RWKV")
            .setContentText("Reviews use the standard order until it is done.")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()

    /**
     * The end of a rebuild that [prepare] announced: a notification (tapping it dismisses it), or a
     * toast when the app can't post notifications.
     */
    @SuppressLint("MissingPermission") // checked in canNotify
    private fun notifyReady(
        context: Context,
        text: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!canNotify(context, manager)) return showToast(context, "RWKV ready")
        val notification =
            NotificationCompat
                .Builder(context, PROGRESS_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("RWKV ready")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
        manager.notify(READY_NOTIFICATION_ID, notification)
    }

    private fun canNotify(
        context: Context,
        manager: NotificationManagerCompat,
    ) = manager.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )

    /**
     * Settings > kuma3 > 'Rebuilding RWKV' message (R.string.kuma3_rwkv_toast_key in the app module,
     * which this module cannot see; the app's default preferences file).
     */
    private fun toastEnabled(context: Context) = kuma3Prefs(context).getBoolean("kuma3_rwkv_toast", true)

    /**
     * Settings > kuma3 > RWKV state precision (R.string.kuma3_rwkv_precision_key): "f32", "int8" or
     * "int4" (card states; the backend keeps note states int8, whose int4 drift overflowed on long
     * histories); anything else: the backend's default, int8 like the desktop fork.
     */
    private fun statePrecision(context: Context) =
        kuma3Prefs(context).getString("kuma3_rwkv_precision", null)?.takeIf { it == "f32" || it == "int4" } ?: ""

    private fun kuma3Prefs(context: Context) = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    private fun installModel(context: Context): File {
        val dir = File(context.noBackupFilesDir, "rwkv")
        val model = File(dir, "model.bin")
        if (model.length() == MODEL_BYTES) return model

        dir.mkdirs()
        val tmp = File(dir, "model.bin.tmp")
        context.assets.open(MODEL_ASSET).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (tmp.length() != MODEL_BYTES) throw IOException("unexpected RWKV model size: ${tmp.length()}")
        model.delete()
        if (!tmp.renameTo(model)) throw IOException("could not install the RWKV model")
        return model
    }

    // the app's showThemedToast is in the app module, which this module cannot see
    @SuppressLint("DirectToastMakeTextUsage")
    private fun showToast(
        context: Context,
        text: String,
    ) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
