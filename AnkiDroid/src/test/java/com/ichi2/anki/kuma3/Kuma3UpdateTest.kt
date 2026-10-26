// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.kuma3

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric: Kuma3Update reads a preference when it loads
@RunWith(AndroidJUnit4::class)
class Kuma3UpdateTest : RobolectricTest() {
    private val hash = "6a170b93a923342e9740695b0963d6354ba009fbbfd095cecf717ab4de6c4189"
    private val other = "0".repeat(64)

    @Test
    fun readsTheApkLineOfSha256Sums() {
        assertEquals(hash, Kuma3Update.expectedSha256("$hash  kuma3-anki-arm64.apk\n"))
        assertEquals(hash, Kuma3Update.expectedSha256("$other  other.apk\r\n$hash  kuma3-anki-arm64.apk"))
        assertNull(Kuma3Update.expectedSha256("$other  other.apk"))
        assertNull(Kuma3Update.expectedSha256("not-a-hash  kuma3-anki-arm64.apk"))
    }

    @Test
    fun picksTheReleaseNotesInTheirLanguage() {
        // as the workflow publishes them: English notes + template, ---, Spanish notes + template
        val body =
            "**Changes since release 24**\r\n\r\n- **A** thing `x`.\r\n\r\n**Which file?**\r\n- stuff\r\n\r\n---\r\n\r\n" +
                "**Cambios desde la versión 24**\r\n\r\n- **Una** cosa.\r\n\r\n**¿Qué archivo?**\r\n- cosas"
        assertEquals("Changes since release 24\n\n- A thing x.", Kuma3Update.notesFor(body, spanish = false))
        assertEquals("Cambios desde la versión 24\n\n- Una cosa.", Kuma3Update.notesFor(body, spanish = true))
        // no Spanish notes: the Spanish half is only the template, so the English ones
        val noSpanish = "**Changes**\n\n- A.\n\n**Which file?**\n- x\n\n---\n\n**¿Qué archivo?**\n- y"
        assertEquals("Changes\n\n- A.", Kuma3Update.notesFor(noSpanish, spanish = true))
    }
}
