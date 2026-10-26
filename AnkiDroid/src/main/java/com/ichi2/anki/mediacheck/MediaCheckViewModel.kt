/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.mediacheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** The operation [MediaCheckViewModel] is running, shown as a progress message by the UI. */
enum class MediaCheckProgress {
    CHECKING_MEDIA,
    ADDING_TAGS,
    DELETING_MEDIA,
}

// TODO: handle errors in the ViewModel instead of returning a Deferred for the UI to await
@NeedsTest("Test the media check process i.e. the buttons and views")
class MediaCheckViewModel :
    ViewModel(),
    HasProgress<MediaCheckProgress> {
    override val progressManager = ProgressManager<MediaCheckProgress>()

    val mediaCheckResult: StateFlow<CheckMediaResponse?>
        field = MutableStateFlow<CheckMediaResponse?>(null)

    private val deletedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val taggedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)

    val deletedFiles: Int
        get() = deletedFilesCount.value

    val taggedFiles: Int
        get() = taggedFilesCount.value

    fun tagMissing(tag: String): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.ADDING_TAGS) {
                val taggedNotes =
                    undoableOp {
                        tags.bulkAdd(mediaCheckResult.value?.missingMediaNotesList ?: listOf(), tag)
                    }
                taggedFilesCount.value = taggedNotes.count
            }
        }

    fun checkMedia(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.CHECKING_MEDIA) {
                mediaCheckResult.value = withCol { keepAudioTags(media.check()) }
            }
        }

    /**
     * kuma3: emptied file by file, so the dialog shows "done/total" (thousands of files took minutes with only
     * "Processing..."); the backend's emptyTrash then removes anything left.
     */
    fun deleteTrash(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.DELETING_MEDIA) {
                val trash = withCol { File(media.dir.parentFile, "media.trash") }
                val files = withContext(Dispatchers.IO) { trash.listFiles().orEmpty() }
                files.toList().chunked(DELETE_BATCH).forEachIndexed { i, batch ->
                    withContext(Dispatchers.IO) { batch.forEach { it.delete() } }
                    updateProgress(
                        amount = ProgressContext.Amount(minOf((i + 1L) * DELETE_BATCH, files.size.toLong()), files.size.toLong()),
                    )
                }
                withCol { media.emptyTrash() }
            }
        }

    fun restoreTrash(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress { withCol { media.restoreTrash() } }
        }

    // kuma3: deleted in batches, so the dialog shows "done/total" (a few thousand files took minutes with no sign of progress)
    fun deleteUnusedMedia(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.DELETING_MEDIA) {
                val unused = mediaCheckResult.value?.unusedList ?: listOf()
                var done = 0L
                for (batch in unused.chunked(DELETE_BATCH)) {
                    withCol { media.trashFiles(batch) }
                    done += batch.size
                    updateProgress(amount = ProgressContext.Amount(done, unused.size.toLong()))
                }
                deletedFilesCount.value = unused.size
            }
        }

    companion object {
        private const val DELETE_BATCH = 100

        /** A report line: the backend drops the bidi isolation marks around values */
        private fun line(s: String) = withoutUnicodeIsolation(s)

        private val AUDIO_TAG = Regex("""\[audio:\s*([^\]]+?)\s*]""")

        /**
         * kuma3: files played by `[audio:file]` tags (kuma3's own audio, see card_template / the German cards) are in
         * use, but Anki's media check only knows `[sound:]` and HTML: without this, "Delete unused" removed them
         * (2026-10-14: 64 card audio files, also from AnkiWeb).
         */
        private fun Collection.keepAudioTags(result: CheckMediaResponse): CheckMediaResponse {
            val used =
                db
                    .queryStringList("select flds from notes where flds like '%[audio:%'")
                    .flatMapTo(HashSet()) { flds -> AUDIO_TAG.findAll(flds).map { it.groupValues[1] } }
            if (used.isEmpty() || result.unusedList.none { it in used }) return result
            val unused = result.unusedList.filter { it !in used }
            // the report's lines as the backend writes them, in the app's language
            val keptLines = result.unusedList.filter { it in used }.mapTo(HashSet()) { line(TR.mediaCheckUnusedFile(it)) }
            val countLine = line(TR.mediaCheckUnusedCount(result.unusedList.size))
            val report =
                result.report
                    .lines()
                    .filterNot { line(it) in keptLines }
                    .map { if (line(it) == countLine) line(TR.mediaCheckUnusedCount(unused.size)) else it }
                    .joinToString("\n")
            return result
                .toBuilder()
                .clearUnused()
                .addAllUnused(unused)
                .setReport(report)
                .build()
        }
    }
}
