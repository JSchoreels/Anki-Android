// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.pages.SearchStatsExtended.fsrsSettings
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SearchStatsExtendedTest : RobolectricTest() {
    private fun call(
        name: String,
        body: String,
    ): Any =
        JSONTokener(
            SearchStatsExtended.methods
                .getValue(name)
                .invoke(col, body.toByteArray())
                .decodeToString(),
        ).nextValue()

    @Test
    fun `cards, reviews and presets come back in the add-on's shapes`() {
        val cid = addBasicNote("Front", "Back").firstCard().id
        // two days before the test clock (2020-07-07)
        val reviewId = collectionTime.intTimeMS() - 2 * 86_400_000L
        // id, cid, usn, ease, ivl, lastIvl, factor, time, type
        col.db.execute("insert into revlog values (?, ?, -1, 3, 1, 0, 2500, 5000, 1)", reviewId, cid)

        assertEquals(cid, (call("cardSearch", "") as JSONArray).getLong(0))

        val cards = call("cardData", "[$cid]") as JSONObject
        assertEquals(18, cards.getJSONArray("columns").length())
        assertEquals(cid, cards.getJSONArray("data").getJSONArray(0).getLong(0))

        val revlogs = call("revlogs", """{"cids":[$cid],"day_range":0}""") as JSONObject
        val row = revlogs.getJSONArray("data").getJSONArray(0)
        assertEquals(listOf(reviewId, cid, 3L), (0..2).map { row.getLong(it) })
        // a review older than the asked-for days is left out
        val lastDay = call("revlogs", """{"cids":[$cid],"day_range":1}""") as JSONObject
        assertEquals(0, lastDay.getJSONArray("data").length())

        val presets = call("fsrsPresetData", "[$cid]") as JSONObject
        assertEquals("Default", presets.getJSONObject(cid.toString()).getString("name"))
    }

    @Test
    fun `FSRS predictions come from the core's test folds made with the current settings`() {
        val table = "retrievability_cache.search_stats_fsrs_review_retrievability"
        // the core's schema (storage/revlog/mod.rs); it makes the table when the collection opens
        col.db.execute(
            """create table if not exists $table (revlog_id integer not null, prediction real not null, source text not null,
               updated_at integer not null, sample_role text not null default 'final_fit',
               fold_index integer not null default -1, primary key (revlog_id, sample_role, fold_index, source))""",
        )

        fun row(
            id: Long,
            prediction: Double,
            updatedAt: Long,
            role: String,
        ) = col.db.execute("insert into $table values (?, ?, 'test', ?, ?, 0)", id, prediction, updatedAt, role)
        row(1, 0.5, 1, "final_fit")
        row(1, 0.7, 2, "validation_fold")
        // a final fit after the folds: new parameters, its fold prediction no longer counts
        row(2, 0.8, 1, "validation_fold")
        row(2, 0.6, 2, "final_fit")
        row(3, 0.9, 3, "post_optimization")

        fun predictions(): Map<Long, Double> {
            val out = call("fsrsReviewRetrievability", """{"cids":[],"day_range":0,"revlog_ids":[1,2,3,4]}""") as JSONObject
            val data = out.getJSONArray("data")
            return (0 until data.length()).associate { data.getJSONArray(it).let { r -> r.getLong(0) to r.getDouble(1) } }
        }
        // folds made with other FSRS settings: none (the calibration job makes them again)
        assertEquals(emptyMap(), predictions())
        AnkiDroidApp
            .sharedPrefs()
            .edit()
            .putLong(SearchStatsExtended.FOLDS_KEY, col.fsrsSettings())
            .apply()
        assertEquals(mapOf(1L to 0.7, 3L to 0.9), predictions())
    }

    @Test
    fun `the calibration compares reviews a day or more after the card's previous one`() {
        val cid = addBasicNote("Front", "Back").firstCard().id
        val day = 86_400_000L
        val now = collectionTime.intTimeMS()

        // id, cid, usn, ease, ivl, lastIvl, factor, time, type
        fun review(
            id: Long,
            ease: Int,
            type: Int = 1,
            factor: Int = 2500,
        ) = col.db.execute("insert into revlog values (?, ?, -1, ?, 1, 0, ?, 5000, ?)", id, cid, ease, factor, type)
        review(now - 10 * day, 3) // first review: not compared
        review(now - 10 * day + 60_000, 1) // same day
        review(now - 7 * day, 3, type = 3, factor = 0) // cram: skipped
        review(now - 5 * day, 1) // forgotten, 5 days later
        review(now - 2 * day, 3) // recalled
        val samples = call("kuma3CalibrationSamples", """{"cids":[$cid],"day_range":0}""") as JSONObject
        val data = samples.getJSONArray("data")
        val rows = (0 until data.length()).map { data.getJSONArray(it).let { r -> Triple(r.getLong(0), r.getLong(1), r.getInt(2)) } }
        assertEquals(listOf(Triple(now - 5 * day, cid, 0), Triple(now - 2 * day, cid, 1)), rows)
    }
}
