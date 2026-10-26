// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.Toast
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.RwkvOffline
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * kuma3: the desktop "Search Stats Extended" add-on (JSchoreels' fork, with the Calibration, UM+ and RWKV
 * comparison graphs) on the statistics page. Its page code is bundled as is in `assets/sse/` (built by
 * kuma3-build/sse_build.sh); this file is its Python half: the values it is started with and the endpoints it
 * posts to (`/_anki/<name>`, JSON in and out), answered from the collection. RWKV's numbers come from the
 * backend's replay (`RwkvOfflineCalibration`).
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
            // ponytail: no FSRS cache; the page replays FSRS itself each time (the desktop caches its
            // optimizer's validation folds, which the phone doesn't run)
            "fsrsReviewRetrievability" to { _ -> """{"column":null,"data":[]}""".toByteArray() },
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
        val today = (System.currentTimeMillis() / 1000 - rollover * 3600L) / DAY_SECONDS.toDouble()
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

    /**
     * RWKV's prediction before each asked-for review, as the desktop without FSRS validation folds: the newest
     * 30% of the answers (`_rwkv_calibration_fold_role_maps`). The backend records them with a replay in the
     * background the first time (minutes for millions of reviews), with the rebuild notification; the page
     * shows FSRS only until then.
     */
    private fun Collection.rwkvReviewRetrievability(request: JSONObject): ByteArray {
        val ids =
            longs(request.optJSONArray("revlog_ids") ?: JSONArray()).ifEmpty {
                val lower = revlogLowerLimit(request.optInt("day_range"))
                longs(request.optJSONArray("cids") ?: JSONArray()).chunked(10_000).flatMap { chunk ->
                    db.queryLongList("select id from revlog where cid in (${chunk.joinToString(",")}) and id > $lower")
                }
            }
        val out = backend.rwkvOfflineCalibration(reviewIds = ids)
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
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            return """{"column":null,"data":[],"warnings":[${JSONObject.quote(message)}]}""".toByteArray()
        }
        Timber.i("RWKV calibration: %d of %d asked-for reviews", out.reviewIdsCount, ids.size)
        val data = (0 until out.reviewIdsCount).map { "[${out.getReviewIds(it)},${out.getPredictions(it)}]" }
        return """{"column":${if (data.isEmpty()) "null" else "\"rwkv_offline\""},"data":[${data.joinToString(",")}]}"""
            .toByteArray()
    }

    private fun writeConfig(request: JSONObject): ByteArray {
        val prefs = AnkiDroidApp.sharedPrefs()
        val saved = prefs.getString(PREFS_KEY, null)?.let(::JSONObject) ?: JSONObject()
        saved.put(request.getString("key"), request.get("value"))
        prefs.edit().putString(PREFS_KEY, saved.toString()).apply()
        return ByteArray(0)
    }
}
