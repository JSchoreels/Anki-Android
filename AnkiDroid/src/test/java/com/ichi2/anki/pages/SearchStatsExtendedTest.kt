// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
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
        val reviewId = 1_700_000_000_000L
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
}
