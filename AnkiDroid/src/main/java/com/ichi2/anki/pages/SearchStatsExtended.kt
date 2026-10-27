// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.R
import com.ichi2.anki.RwkvOffline
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.withProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * kuma3: the desktop "Search Stats Extended" add-on (JSchoreels' fork, with the Calibration, UM+ and RWKV
 * comparison graphs) on the statistics page. Its page code is bundled as is in `assets/sse/` (built by
 * kuma3-build/sse_build.sh); this file is its Python half: the values it is started with and the endpoints it
 * posts to (`/_anki/<name>`, JSON in and out), answered from the collection. FSRS's predictions per review come
 * from the core's FSRS cache (`FsrsCalibrationFinalFit`, the test folds of `RebuildFsrsReviewRetrievabilityCache`),
 * RWKV's from the backend's replay (`RwkvOfflineCalibration`).
 */
object SearchStatsExtended {
    private const val PREFS_KEY = "kuma3_sse_config"
    private const val LANG = "en_GB"
    private const val DAY_SECONDS = 86_400L

    /** Starts the add-on on the loaded page; it then wraps the page's next graphs request. */
    suspend fun inject(webView: WebView) {
        val assets = webView.context.assets
        val script =
            withContext(Dispatchers.IO) {
                fun asset(name: String) = assets.open("sse/$name").bufferedReader().use { it.readText() }
                val config = JSONObject(asset("config.json"))
                val saved = AnkiDroidApp.sharedPrefs().getString(PREFS_KEY, null)?.let(::JSONObject)
                saved?.keys()?.forEach { config.put(it, saved.get(it)) }
                val other = withCol { other(this, asset("$LANG.ftl")) }
                // once per page: the page can report "finished" twice
                "if (!window.kuma3Sse) { window.kuma3Sse = true;" +
                    "window.bridgeCommand = window.bridgeCommand || function () {};" +
                    "const css = ${JSONObject.quote(asset("stats.min.css"))};" +
                    "const SSEconfig = $config;" +
                    "const SSEother = $other;" +
                    asset("stats.min.js") +
                    "\n}"
            }
        webView.evaluateJavascript(script, null)
    }

    private fun other(
        col: Collection,
        ftl: String,
    ): JSONObject {
        val scheduling = col.getPreferences().scheduling
        val configs = JSONObject()
        col.decks.allConfig().forEach { configs.put(it.id.toString(), it.jsonObject) }
        val configIds = JSONObject()
        val decks = JSONObject(col.backend.getAllDecksLegacy().toStringUtf8())
        decks.keys().forEach { configIds.put(it, decks.getJSONObject(it).optLong("conf", 1)) }
        return JSONObject()
            .put("rollover", scheduling.rollover)
            .put("learn_ahead_secs", scheduling.learnAheadSecs)
            .put("selected_deck_id", col.decks.current().id)
            .put("deck_configs", configs)
            .put("deck_config_ids", configIds)
            .put("days_elapsed", col.sched.today)
            .put("lang", LANG)
            .put("available_langs", JSONArray(listOf(LANG)))
            .put("lang_ftl", ftl)
            .put("fallback_ftl", ftl)
    }

    val methods: Map<String, CollectionBackendInterface> =
        mapOf<String, CollectionBackendInterface>(
            "cardSearch" to { bytes -> JSONArray(findCards(bytes.decodeToString())).toBytes() },
            "cardData" to { bytes -> cardData(longs(JSONArray(bytes.decodeToString()))) },
            "fsrsPresetData" to { bytes -> fsrsPresetData(longs(JSONArray(bytes.decodeToString()))) },
            "revlogs" to { bytes -> revlogs(JSONObject(bytes.decodeToString())) },
            "fsrsReviewRetrievability" to { bytes -> fsrsReviewRetrievability(JSONObject(bytes.decodeToString())) },
            // the page's own FSRS replay (ts-fsrs, only the period shown): not kept
            "cacheFsrsReviewRetrievability" to { _ -> """{"stored":0}""".toByteArray() },
            "rwkvReviewRetrievability" to { bytes -> rwkvReviewRetrievability(JSONObject(bytes.decodeToString())) },
            // ponytail: RWKV Memorised over time is the desktop's per-day replay of every card; the page
            // then shows FSRS only
            "startRwkvMemorisedHistory" to { _ -> MEMORISED_UNAVAILABLE },
            "rwkvMemorisedHistoryProgress" to { _ -> MEMORISED_UNAVAILABLE },
            "cancelRwkvMemorisedHistory" to { _ -> """{"cancelled":false,"checkpointed":false}""".toByteArray() },
            "writeConfig" to { bytes -> writeConfig(JSONObject(bytes.decodeToString())) },
            "openLocaleFolder" to { _ -> ByteArray(0) },
        )

    private val MEMORISED_UNAVAILABLE =
        """{"phase":"unavailable","done":true,"error":"RWKV Memorised history is desktop-only."}""".toByteArray()

    private fun JSONArray.toBytes() = toString().toByteArray()

    private fun longs(array: JSONArray) = List(array.length()) { array.getLong(it) }

    private val CARD_COLUMNS =
        listOf(
            "id",
            "nid",
            "did",
            "ord",
            "mod",
            "usn",
            "type",
            "queue",
            "due",
            "ivl",
            "factor",
            "reps",
            "lapses",
            "left",
            "odue",
            "odid",
            "flags",
            "data",
        )

    private val REVLOG_COLUMNS = listOf("id", "cid", "ease", "ivl", "lastIvl", "time", "factor", "type")

    private fun Collection.cardData(cids: List<Long>): ByteArray =
        rows(CARD_COLUMNS, cids) { "select ${CARD_COLUMNS.joinToString()} from cards where id in ($it)" }

    private fun Collection.revlogs(request: JSONObject): ByteArray {
        val lower = revlogLowerLimit(request.optInt("day_range"))
        return rows(REVLOG_COLUMNS, longs(request.getJSONArray("cids")), sortByFirst = true) {
            "select ${REVLOG_COLUMNS.joinToString()} from revlog where cid in ($it) and id > $lower"
        }
    }

    /**
     * `{"columns": [...], "data": [[...], ...]}` for the rows [sql] gives for [ids] (asked in chunks: one
     * statement with every id of a big collection is too long for SQLite), text in the last column.
     */
    private fun Collection.rows(
        columns: List<String>,
        ids: List<Long>,
        sortByFirst: Boolean = false,
        sql: (String) -> String,
    ): ByteArray {
        val rows = mutableListOf<Pair<Long, String>>()
        for (chunk in ids.chunked(10_000)) {
            db.query(sql(chunk.joinToString(","))).use { cursor ->
                while (cursor.moveToNext()) {
                    val row =
                        columns.indices.joinToString(",", "[", "]") { i ->
                            if (columns[i] == "data") JSONObject.quote(cursor.getString(i)) else cursor.getLong(i).toString()
                        }
                    rows.add(cursor.getLong(0) to row)
                }
            }
        }
        if (sortByFirst) rows.sortBy { it.first }
        return buildString {
            append("""{"columns":""").append(JSONArray(columns)).append(""","data":[""")
            rows.joinTo(this, ",") { it.second }
            append("]}")
        }.toByteArray()
    }

    /** The add-on's `_revlog_lower_limit`: reviews newer than [dayRange] days, all for 0. */
    private fun Collection.revlogLowerLimit(dayRange: Int): Long {
        if (dayRange <= 0) return 0
        val rollover = getPreferences().scheduling.rollover
        val today = (TimeManager.time.intTime() - rollover * 3600L) / DAY_SECONDS.toDouble()
        return ((today - dayRange) * DAY_SECONDS * 1000).toLong()
    }

    private fun Collection.fsrsPresetData(cids: List<Long>): ByteArray {
        val out = JSONObject()
        // one preset lookup per preset, not per card
        for (cards in backend.getFsrsPresetIdsForCards(cids).groupBy({ it.presetId }, { it.cardId }).values) {
            val p = backend.getFsrsPresetForCard(cards.first())
            val preset =
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("fsrsVersion", p.fsrsVersionValue)
                    .put("params", JSONArray(p.paramsList))
                    .put("desiredRetention", p.desiredRetention.toDouble())
                    .put("historicalRetention", p.historicalRetention.toDouble())
                    .put("ignoreRevlogsBeforeDate", p.ignoreRevlogsBeforeDate)
                    .put("fsrsDynamicDesiredRetentionEnabled", p.fsrsDynamicDesiredRetentionEnabled)
                    .put("fsrsDynamicDesiredRetentionParams", JSONArray(p.fsrsDynamicDesiredRetentionParamsList))
                    .put("fsrsDynamicDesiredRetentionWeights", JSONArray(p.fsrsDynamicDesiredRetentionWeightsList))
                    .put("fsrsDynamicDesiredRetentionAvgDrs", JSONArray(p.fsrsDynamicDesiredRetentionAvgDrsList))
                    .put("fsrsDynamicDesiredRetentionFsrsEqWeights", JSONArray(p.fsrsDynamicDesiredRetentionFsrsEqWeightsList))
                    .put("fsrsDynamicDesiredRetentionFsrsEqDrs", JSONArray(p.fsrsDynamicDesiredRetentionFsrsEqDrsList))
                    .put("fsrsDynamicDesiredRetentionMin", p.fsrsDynamicDesiredRetentionMin.toDouble())
                    .put("fsrsDynamicDesiredRetentionMax", p.fsrsDynamicDesiredRetentionMax.toDouble())
            cards.forEach { out.put(it.toString(), preset) }
        }
        return out.toString().toByteArray()
    }

    /** The page's asked-for reviews: its ids, or every review of its cards in its period. */
    private fun Collection.askedReviews(request: JSONObject): List<Long> =
        longs(request.optJSONArray("revlog_ids") ?: JSONArray()).ifEmpty {
            val lower = revlogLowerLimit(request.optInt("day_range"))
            longs(request.optJSONArray("cids") ?: JSONArray()).chunked(10_000).flatMap { chunk ->
                db.queryLongList("select id from revlog where cid in (${chunk.joinToString(",")}) and id > $lower")
            }
        }

    private fun toast(message: String) {
        val context = AnkiDroidApp.instance
        Handler(Looper.getMainLooper()).post { showThemedToast(context, message, shortLength = false) }
    }

    private fun predictions(
        column: String,
        rows: Map<Long, Number>,
        warning: String? = null,
    ): ByteArray {
        val data = rows.entries.joinToString(",") { "[${it.key},${it.value}]" }
        val warnings = listOfNotNull(warning).joinToString(",", "[", "]") { JSONObject.quote(it) }
        return """{"column":${if (rows.isEmpty()) "null" else "\"$column\""},"data":[$data],"warnings":$warnings}"""
            .toByteArray()
    }

    // The core's FSRS predictions per review (the fork's cache, kept in a file next to the collection and
    // attached to it; the table is made by its first write). Rows: final_fit = each preset's current
    // parameters over the whole history; validation_fold = FSRS trained only on the reviews before that
    // fold, as the desktop's calibration graph uses; post_optimization = made when the card was answered.
    private const val FSRS_TABLE = "search_stats_fsrs_review_retrievability"
    private const val FSRS_CACHE = "retrievability_cache.$FSRS_TABLE"
    private const val FIT_KEY = "kuma3_sse_fsrs_fit"

    /** A final fit or test-fold rebuild is running (one at a time). */
    private val fitting = AtomicBoolean(false)

    private fun Collection.hasFsrsCache() =
        db.queryScalar("select count() from retrievability_cache.sqlite_master where name = '$FSRS_TABLE'") > 0

    /** The test folds were made ([prepareFolds]): the graphs then use them, as the desktop does. */
    private fun Collection.hasFsrsFolds() =
        hasFsrsCache() &&
            db.queryScalar("select exists (select 1 from $FSRS_CACHE where sample_role = 'validation_fold')") == 1

    /**
     * FSRS's prediction before each of [ids], the newest row per review (the add-on's
     * `_read_fsrs_calibration_retrievability_cache`): with [folds] a test-fold or answer prediction, unless
     * a newer final fit replaced it; without, a final fit or answer one.
     */
    private fun Collection.fsrsRows(
        ids: List<Long>,
        folds: Boolean,
    ): Map<Long, Double> {
        val out = HashMap<Long, Double>()
        if (!hasFsrsCache()) return out
        val used = if (folds) "'validation_fold','post_optimization'" else "'final_fit','post_optimization'"
        for (chunk in ids.chunked(10_000)) {
            db
                .query(
                    """
                    with latest as (
                        select revlog_id, max(updated_at) as updated_at from $FSRS_CACHE
                        where revlog_id in (${chunk.joinToString(",")})
                            and sample_role in ('final_fit','validation_fold','post_optimization')
                        group by revlog_id)
                    select c.revlog_id, c.prediction from $FSRS_CACHE c
                    join latest l on l.revlog_id = c.revlog_id and l.updated_at = c.updated_at
                    where c.sample_role in ($used)
                    """,
                ).use { cursor -> while (cursor.moveToNext()) out[cursor.getLong(0)] = cursor.getDouble(1) }
        }
        return out
    }

    /**
     * FSRS's prediction before each asked-for review, from the core (FSRS-7 with the whole history, as the
     * desktop): its test folds once made, else its final fit. Without either the page replays FSRS itself
     * (ts-fsrs over the period shown only, which put long-known cards back to new: the flat line nyawo saw).
     */
    private fun Collection.fsrsReviewRetrievability(request: JSONObject): ByteArray {
        val ids = askedReviews(request)
        val folds = hasFsrsFolds()
        val rows = fsrsRows(ids, folds)
        Timber.i("FSRS calibration: %d of %d asked-for reviews (test folds: %b)", rows.size, ids.size, folds)
        if (folds || !refitIfStale(ids.size, rows.size)) return predictions(FSRS_TABLE, rows)
        val message = AnkiDroidApp.instance.getString(R.string.kuma3_sse_fsrs_computing)
        toast(message)
        return predictions(FSRS_TABLE, rows, message)
    }

    /**
     * Starts the core's final fit on its own thread (tens of seconds for millions of reviews) when the FSRS
     * settings changed since the last one, or when a tenth of the asked-for reviews have no prediction and
     * there are reviews since (answers made here get theirs when answered). Returns whether one is running.
     */
    private fun Collection.refitIfStale(
        asked: Int,
        found: Int,
    ): Boolean {
        val settings =
            decks
                .allConfig()
                .joinToString(";") { config ->
                    val json = config.jsonObject
                    json
                        .keys()
                        .asSequence()
                        .filter { "fsrs" in it.lowercase() || it == "ignoreRevlogsBeforeDate" }
                        .sorted()
                        .joinToString { "$it=${json.get(it)}" }
                }.hashCode()
                .toLong()
        val newest = db.queryLongScalar("select max(id) from revlog")
        val prefs = AnkiDroidApp.sharedPrefs()
        val last = prefs.getString(FIT_KEY, null)?.split(":")?.mapNotNull(String::toLongOrNull)
        val stale = last?.getOrNull(0) != settings || (found < asked * 9 / 10 && last.getOrNull(1) != newest)
        if (!stale || !fitting.compareAndSet(false, true)) return fitting.get()
        applicationScope.launch(Dispatchers.IO) {
            try {
                val rows = CollectionManager.getBackend().fsrsCalibrationFinalFit()
                Timber.i("FSRS calibration: final fit of %d reviews", rows)
                prefs.edit().putString(FIT_KEY, "$settings:$newest").apply()
            } catch (e: Exception) {
                Timber.w(e, "FSRS calibration: final fit failed")
            } finally {
                fitting.set(false)
            }
        }
        return true
    }

    /**
     * The statistics' "Calibration on test folds" (the desktop's "prepare calibration data"): FSRS trained
     * once per test fold (minutes for a big collection; a progress dialog that can be cancelled), then the
     * page again, whose RWKV request starts the replay recording the whole history.
     */
    fun prepareFolds(fragment: PageFragment) {
        val activity = fragment.requireActivity()
        AlertDialog
            .Builder(activity)
            .setTitle(R.string.kuma3_sse_folds)
            .setMessage(R.string.kuma3_sse_folds_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                fragment.launchCatchingTask {
                    if (!fitting.compareAndSet(false, true)) {
                        showThemedToast(activity, activity.getString(R.string.kuma3_sse_fsrs_computing), shortLength = false)
                        return@launchCatchingTask
                    }
                    try {
                        activity.withProgress(
                            extractProgress = {
                                text = activity.getString(R.string.kuma3_sse_folds)
                                amount =
                                    progress.computeParams
                                        .takeIf { progress.hasComputeParams() && it.total > 0 }
                                        ?.let { ProgressContext.Amount(it.current.toLong(), it.total.toLong()) }
                            },
                        ) {
                            withContext(Dispatchers.IO) { CollectionManager.getBackend().rebuildFsrsReviewRetrievabilityCache() }
                        }
                    } finally {
                        fitting.set(false)
                    }
                    fragment.webViewLayout.reload()
                }
            }.show()
    }

    /**
     * RWKV's prediction before each asked-for review. As the desktop: with FSRS test folds, on the reviews
     * FSRS has a prediction for (so both lines are on the same reviews; RWKV then records the whole
     * history), else on the newest 30% of the answers (`_rwkv_calibration_fold_role_maps`). The backend
     * records them with a replay in the background the first time (minutes for millions of reviews), with
     * the rebuild notification; the page shows FSRS only until then.
     */
    private fun Collection.rwkvReviewRetrievability(request: JSONObject): ByteArray {
        val ids = askedReviews(request)
        val folds = hasFsrsFolds()
        val out = backend.rwkvOfflineCalibration(reviewIds = ids, allReviews = folds)
        val context = AnkiDroidApp.instance
        if (out.started) RwkvOffline.watchRebuild(context, out.replayTotal)
        if (!out.available) {
            val message =
                if (out.computing) {
                    val percent = if (out.replayTotal > 0) out.replayDone * 100 / out.replayTotal else 0
                    context.getString(R.string.kuma3_sse_rwkv_computing, percent)
                } else {
                    context.getString(R.string.kuma3_sse_rwkv_off)
                }
            toast(message)
            return predictions("rwkv_offline", emptyMap(), message)
        }
        val fsrs = if (folds) fsrsRows(ids, folds = true).keys else null
        val rows =
            (0 until out.reviewIdsCount)
                .filter { fsrs == null || out.getReviewIds(it) in fsrs }
                .associate { out.getReviewIds(it) to out.getPredictions(it) }
        Timber.i("RWKV calibration: %d of %d asked-for reviews (test folds: %b)", rows.size, ids.size, folds)
        return predictions("rwkv_offline", rows)
    }

    private fun writeConfig(request: JSONObject): ByteArray {
        val prefs = AnkiDroidApp.sharedPrefs()
        val saved = prefs.getString(PREFS_KEY, null)?.let(::JSONObject) ?: JSONObject()
        saved.put(request.getString("key"), request.get("value"))
        prefs.edit().putString(PREFS_KEY, saved.toString()).apply()
        return ByteArray(0)
    }
}
