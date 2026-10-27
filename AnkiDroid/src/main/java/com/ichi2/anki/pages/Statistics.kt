// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.view.View
import androidx.core.content.ContextCompat.getSystemService
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.databinding.PageStatisticsBinding
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.withProgress
import com.ichi2.anki.xp.XpGlance
import dev.androidbroadcast.vbpd.viewBinding
import org.json.JSONObject
import timber.log.Timber

class Statistics : PageFragment(R.layout.page_statistics) {
    override val pagePath: String = "graphs"
    private val binding by viewBinding(PageStatisticsBinding::bind)

    /** The PrintJob launched by this Fragment */
    // After killing the app, printManager.printJobs can still list active jobs
    private var pendingPrintJob: PrintJob? = null

    // kuma3: the deck is applied once, when both the page and the deck name are ready
    private var pageLoaded = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the back arrow when requested (e.g. hosted in bottom nav)
        if (arguments?.getBoolean(ARG_HIDE_BACK_BUTTON) == true) {
            binding.toolbar.navigationIcon = null
        }

        binding.deckName.setOnClickListener {
            startDeckSelection(allowAll = false, allowFiltered = false, skipEmptyDefault = true)
        }

        binding.toolbar.apply {
            menu.findItem(R.id.action_export_stats).title = CollectionManager.TR.statisticsSavePdf()
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_export_stats) {
                    exportWebViewContentAsPDF()
                }
                // kuma3: FSRS test folds for the calibration graphs
                if (item.itemId == R.id.action_kuma3_calibration_folds) SearchStatsExtended.prepareFolds(this@Statistics)
                true
            }
        }
        registerDeckSelectedHandler(action = ::onDeckSelected)
        requireActivity().launchCatchingTask {
            withProgress {
                val deckName =
                    savedInstanceState?.getString(KEY_DECK_NAME, null) ?: withCol { decks.current().name }
                binding.deckName.text = deckName
                if (pageLoaded) changeDeck(deckName)
            }
        }
    }

    /** Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        if (pendingPrintJob?.isActive == true) {
            Timber.w("Duplicate print attempted - skipping")
            showSnackbar(CommonString.already_in_progress)
            return
        }
        Timber.i("Saving Stats to PDF")
        val printManager = getSystemService(requireContext(), PrintManager::class.java) ?: return
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter =
            webViewLayout.createPrintDocumentAdapter(jobName) ?: run {
                Timber.w("Skipping stats PDF export; WebView is destroyed")
                return
            }
        pendingPrintJob =
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build(),
            )
    }

    // kuma3: once the page has loaded, apply the deck (unless it is still being read) and draw Anki XP's
    // "At a glance" and Player boxes (xp/XpGlance.kt, from changeDeck)
    override fun onCreateWebViewClient(savedInstanceState: Bundle?) =
        super.onCreateWebViewClient(savedInstanceState).apply {
            onPageFinishedCallbacks.add { webView ->
                // kuma3: Search Stats Extended first, so it sees the deck's graphs request
                requireActivity().launchCatchingTask {
                    SearchStatsExtended.inject(webView)
                    pageLoaded = true
                    binding.deckName.text
                        .toString()
                        .takeIf { it.isNotEmpty() }
                        ?.let(::changeDeck)
                }
            }
        }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DECK_NAME, binding.deckName.text.toString())
    }

    /**
     * Updates the ui with the new selected deck. Doesn't change the backend.
     *
     * This method includes a workaround to change the deck in the webview by finding the text box
     * and replacing the deck name with the selected deck name from the dialog and updating the
     * stats. See issue #3394 in Anki repository.
     **/
    private fun changeDeck(selectedDeckName: String) {
        binding.deckName.text = selectedDeckName
        // kuma3: escape the name for the search (\ " * _) and quote it for JavaScript
        val search = JSONObject.quote("deck:\"" + selectedDeckName.replace(Regex("([\\\\\"*_])"), "\\\\$1") + "\"")
        val javascriptCode =
            """
            (function set(tries) {
                var textBox = document.getElementById("statisticsSearchText");
                // kuma3: the page draws the box after it loads; wait for it instead of failing on null
                if (!textBox) { if (tries < 50) setTimeout(function () { set(tries + 1); }, 100); return; }
                textBox.value = $search;
                textBox.dispatchEvent(new Event("input", { bubbles: true }));
                textBox.dispatchEvent(new Event("change"));
            })(0);
            """.trimIndent()
        webViewLayout.evaluateJavascript(javascriptCode, null)
        XpGlance.show(this, selectedDeckName) // kuma3
    }

    companion object {
        const val ARG_HIDE_BACK_BUTTON = "hideBackButton"
        private const val KEY_DECK_NAME = "key_deck_name"
    }
}

private val PrintJob.isActive: Boolean
    get() = !isCompleted && !isFailed && !isCancelled
