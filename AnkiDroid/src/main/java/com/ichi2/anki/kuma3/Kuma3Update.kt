// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.kuma3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.web.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * In-app updates from the GitHub releases of kuma3-anki-app (Settings > kuma3 > Updates).
 *
 * A release is newer when GitHub says its tag is ahead of the commit this APK was built from
 * ([BuildConfig.GIT_COMMIT_HASH]): a kuma3-dev build newer than the latest release is not offered
 * an older one. The APK is downloaded to the cache and handed to Android's installer, which only
 * accepts it over this app when it carries the same release key.
 */
object Kuma3Update {
    private const val REPO = "Pedrubik2000/kuma3-anki-app"
    private const val APK = "kuma3-anki-arm64.apk"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val LAST_CHECK = "kuma3_update_last_check"

    class Release(
        val name: String,
        val tag: String,
        val notes: String,
        val apkUrl: String,
    )

    /** Debug builds have another app id and key: a release can't install over them. */
    val available: Boolean get() = !BuildConfig.DEBUG

    /** The commit this APK was built from. */
    private val builtFrom = BuildConfig.GIT_COMMIT_HASH

    /** The automatic daily check (Settings > kuma3 > Updates). */
    val autoCheck by Prefs.booleanPref(R.string.kuma3_update_check_key, true)

    private val client by lazy {
        HttpFetcher
            .getOkHttpBuilder(fakeUserAgent = false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun getJson(url: String): JSONObject =
        client
            .newCall(
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            ).execute()
            .use {
                if (!it.isSuccessful) throw IOException("GitHub: HTTP ${it.code}")
                JSONObject(it.body.string())
            }

    /** The latest release if it is newer than this build, else null. Blocking: call from Dispatchers.IO. */
    fun newerRelease(): Release? {
        val latest = getJson("https://api.github.com/repos/$REPO/releases/latest")
        val tag = latest.getString("tag_name")
        // "ahead": the tag has commits this build hasn't
        val status = getJson("https://api.github.com/repos/$REPO/compare/$builtFrom...$tag").getString("status")
        if (status != "ahead") return null
        val assets = latest.getJSONArray("assets")
        val apk =
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == APK } ?: return null
        return Release(
            name = latest.optString("name", tag),
            tag = tag,
            // this release's own English notes: the Spanish follows <!-- es -->, and the workflow's
            // template ("Which file?", ...) follows the notes; shown as plain text
            notes =
                latest
                    .optString("body")
                    .substringBefore("<!-- es -->")
                    .substringBefore("**Which file?**")
                    .replace("**", "")
                    .replace("`", "")
                    .trim(),
            apkUrl = apk.getString("browser_download_url"),
        )
    }

    /** From the deck list: at most once a day, quietly unless there is an update. */
    fun checkDaily(activity: Activity) {
        if (!available || !autoCheck) return
        val prefs = activity.getSharedPreferences("kuma3_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_CHECK, 0) < DAY_MS) return
        prefs.edit { putLong(LAST_CHECK, now) }
        check(activity, quiet = true)
    }

    /** Checks now and offers the update; [quiet] says nothing when there is none or on errors. */
    fun check(
        activity: Activity,
        quiet: Boolean = false,
    ) {
        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            val release =
                try {
                    withContext(Dispatchers.IO) { newerRelease() }
                } catch (e: Exception) {
                    Timber.w(e, "kuma3 update check")
                    if (!quiet) toast(activity, "Couldn't check for updates: ${e.message}")
                    return@launch
                }
            if (release == null) {
                if (!quiet) toast(activity, "kuma3 Anki is up to date")
                return@launch
            }
            if (activity.isFinishing) return@launch
            AlertDialog
                .Builder(activity)
                .setTitle("${release.name} is available")
                .setMessage(release.notes.take(700))
                .setPositiveButton("Update") { _, _ -> download(activity, release) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun download(
        activity: Activity,
        release: Release,
    ) {
        // Android asks once per app: "Allow from this source"
        if (!activity.packageManager.canRequestPackageInstalls()) {
            // offered again when the deck list is next shown, not only tomorrow
            activity.getSharedPreferences("kuma3_update", Context.MODE_PRIVATE).edit { remove(LAST_CHECK) }
            toast(activity, "Allow kuma3 Anki to install updates, then come back: the update is offered again")
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
            return
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { setPadding(48, 24, 48, 0) }
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle("Downloading ${release.name}")
                .setView(bar)
                .setCancelable(false)
                .show()
        val owner = activity as LifecycleOwner
        owner.lifecycleScope.launch {
            val file = File(activity.cacheDir, "kuma3-update/$APK")
            try {
                withContext(Dispatchers.IO) {
                    file.parentFile!!.mkdirs()
                    client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val total = response.body.contentLength()
                        response.body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    done += read
                                    if (total > 0) bar.post { bar.progress = (done * 100 / total).toInt() }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "kuma3 update download")
                dialog.dismiss()
                file.delete()
                toast(activity, "Download failed: ${e.message}")
                return@launch
            }
            dialog.dismiss()
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.apkgfileprovider", file)
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun toast(
        context: Context,
        text: String,
    ) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
