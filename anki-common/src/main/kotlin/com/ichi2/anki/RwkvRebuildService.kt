// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Keeps the app running while the backend rebuilds the RWKV state ([RwkvOffline]): without a
 * foreground service Android freezes the app seconds after it leaves the screen, and the rebuild
 * stops until the user comes back (seen on a Pixel 8a: stuck at 32% for 13 min). Its notification is
 * the "Preparing RWKV" progress one. Does no work itself.
 */
class RwkvRebuildService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, RwkvOffline.PROGRESS_NOTIFICATION_ID, RwkvOffline.preparingNotification(this), type)
        return START_NOT_STICKY
    }

    /** Android 15's daily dataSync limit: the rebuild goes on as before, frozen when off screen. */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        stopSelf()
    }

    companion object {
        /** False when Android doesn't allow it (the app isn't in front); the rebuild runs anyway. */
        fun start(context: Context): Boolean =
            try {
                ContextCompat.startForegroundService(context, Intent(context, RwkvRebuildService::class.java))
                true
            } catch (e: IllegalStateException) {
                Timber.w(e, "RWKV rebuild service not started")
                false
            }

        fun stop(context: Context) {
            context.stopService(Intent(context, RwkvRebuildService::class.java))
        }
    }
}
