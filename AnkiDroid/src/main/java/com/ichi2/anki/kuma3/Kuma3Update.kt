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
import java.security.MessageDigest
import java.util.Locale
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
    private const val DOWNLOADS = "kuma3-update"

    class Release(
        val name: String,
        val tag: String,
        /** The whole release body: [notesFor] picks this release's own notes from it. */
        val notes: String,
        val apkUrl: String,
        /** The release's SHA256SUMS.txt, if it has one. */
        val sumsUrl: String?,
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
        val files = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = files.firstOrNull { it.getString("name") == APK } ?: return null
        return Release(
            name = latest.optString("name", tag),
            tag = tag,
            notes = latest.optString("body"),
            apkUrl = apk.getString("browser_download_url"),
            sumsUrl = files.firstOrNull { it.getString("name") == "SHA256SUMS.txt" }?.getString("browser_download_url"),
        )
    }

    /** From the deck list: at most once a day, quietly unless there is an update. */
    fun checkDaily(activity: Activity) {
        // a downloaded update is no longer needed once the deck list is back
        File(activity.cacheDir, DOWNLOADS).deleteRecursively()
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
                    if (!quiet) toast(activity, activity.getString(R.string.kuma3_update_check_failed, e.message))
                    return@launch
                }
            if (release == null) {
                if (!quiet) toast(activity, activity.getString(R.string.kuma3_update_up_to_date))
                return@launch
            }
            if (activity.isFinishing) return@launch
            AlertDialog
                .Builder(activity)
                .setTitle(activity.getString(R.string.kuma3_update_available, release.name))
                .setMessage(notesFor(release.notes, Locale.getDefault().language == "es").take(700))
                .setPositiveButton(R.string.kuma3_update_install) { _, _ -> download(activity, release) }
                .setNegativeButton(R.string.kuma3_update_later, null)
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
            toast(activity, activity.getString(R.string.kuma3_update_allow_installs))
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
            return
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { setPadding(48, 24, 48, 0) }
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(activity.getString(R.string.kuma3_update_downloading, release.name))
                .setView(bar)
                .setCancelable(false)
                .show()
        val owner = activity as LifecycleOwner
        owner.lifecycleScope.launch {
            val file = File(activity.cacheDir, "$DOWNLOADS/$APK")
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
                    release.sumsUrl?.let { verify(file, it) }
                }
            } catch (e: Exception) {
                Timber.w(e, "kuma3 update download")
                dialog.dismiss()
                file.delete()
                val reason = if (e is DamagedDownload) activity.getString(R.string.kuma3_update_damaged) else e.message
                toast(activity, activity.getString(R.string.kuma3_update_download_failed, reason))
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

    private class DamagedDownload : IOException("SHA-256 differs")

    /**
     * This release's own notes from a release body, as plain text: English, or the Spanish after the
     * workflow's `---` line; the template (Which file? / ¿Qué archivo?) that follows each is left out.
     */
    internal fun notesFor(
        body: String,
        spanish: Boolean,
    ): String {
        val text = body.replace("\r\n", "\n")
        val separator = "\n---\n"
        val half = if (spanish && separator in text) text.substringAfter(separator) else text.substringBefore(separator)
        return half
            .substringBefore("**Which file?**")
            .substringBefore("**¿Qué archivo?**")
            .replace("**", "")
            .replace("`", "")
            .trim()
    }

    /** The SHA-256 that a SHA256SUMS.txt (`<hash>  <name>` lines) lists for [APK]. */
    internal fun expectedSha256(sums: String): String? =
        sums
            .lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size == 2 && it[1] == APK && it[0].length == 64 }
            ?.get(0)

    /** Throws unless [file] has the SHA-256 that the release's SHA256SUMS.txt lists for [APK]. */
    private fun verify(
        file: File,
        sumsUrl: String,
    ) {
        val sums =
            client.newCall(Request.Builder().url(sumsUrl).build()).execute().use {
                if (!it.isSuccessful) throw IOException("SHA256SUMS.txt: HTTP ${it.code}")
                it.body.string()
            }
        val expected = expectedSha256(sums) ?: throw IOException("$APK is not in SHA256SUMS.txt")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) throw DamagedDownload()
    }

    private fun toast(
        context: Context,
        text: String,
    ) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
