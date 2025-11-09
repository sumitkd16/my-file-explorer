package com.raival.compose.file.explorer.screen.main.tab.files.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.SEARCH
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchManager {
    // --- CHANGED to TextFieldValue to store cursor position ---
    var searchQuery by mutableStateOf(TextFieldValue(emptyString))
    var searchOptions by mutableStateOf(SearchOptions())
    var isSearching by mutableStateOf(false)

    // This progress is no longer needed for MediaStore, but we'll keep it for the UI
    var searchProgress by mutableFloatStateOf(0f)
    var currentSearchingFile by mutableStateOf(emptyString) // Kept for UI compatibility

    val searchResults = mutableStateListOf<SearchResult>()

    private var searchJob: Job? = null

    // --- FUNCTION SIGNATURE UPDATED ---
    fun startSearch(tab: FilesTab, onSearchComplete: (shouldExpand: Boolean) -> Unit) {
        val queryText = searchQuery.text
        if (queryText.isEmpty()) {
            clearResults()
            return
        }

        // --- SAVE TO SEARCH HISTORY ---
        try {
            val currentHistory = globalClass.preferencesManager.searchHistory.toMutableList()
            currentHistory.remove(queryText) // Remove if already exists, to move it to the top
            currentHistory.add(0, queryText) // Add to the front
            // Limit the history to a reasonable number, e.g., 20
            val newHistory = currentHistory.take(20).toSet()
            globalClass.preferencesManager.searchHistory = newHistory
        } catch (e: Exception) {
            logger.logError("Failed to save search history: ${e.message}")
        }
        // --- END OF HISTORY SAVE ---

        searchJob?.cancel()
        clearResults()
        isSearching = true
        searchProgress = -1f // Set to indeterminate progress
        currentSearchingFile = globalClass.getString(R.string.searching)

        searchJob = tab.scope.launch {
            try {
                // --- THIS IS THE NEW, FAST LOGIC ---
                val results = StorageProvider.globalSearchByFilename(
                    query = queryText,
                    options = searchOptions
                )

                val searchResultList = results.map { fileHolder ->
                    SearchResult(
                        file = fileHolder,
                        matchType = SearchResult.MatchType.FILENAME // All matches are filename matches
                    )
                }

                searchResults.addAll(searchResultList)
                // --- END OF NEW LOGIC ---

            } catch (_: CancellationException) {
                // Search was cancelled
            } catch (e: Exception) {
                // Handle other errors
                logger.logError(e)
                globalClass.showMsg(globalClass.getString(R.string.something_went_wrong))
            } finally {
                isSearching = false
                searchProgress = 1f // Mark as complete
                currentSearchingFile = emptyString

                // --- NEW LOGIC TO REUSE SEARCH TAB ---
                val isActiveTabSearch = tab.activeFolder is VirtualFileHolder &&
                        (tab.activeFolder as VirtualFileHolder).type == SEARCH

                withContext(Dispatchers.Main) {
                    if (isActiveTabSearch) {
                        // We are already on a search tab, just reload it
                        tab.reloadFiles()
                        onSearchComplete(false) // false = "do not expand/create new tab"
                    } else {
                        // We are on Home/Internal, so expand to a new search tab
                        onSearchComplete(true) // true = "do expand"
                    }
                }
            }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        isSearching = false
        searchProgress = 0f
        currentSearchingFile = emptyString
    }

    fun clearResults() {
        searchResults.clear()
        searchProgress = 0f
        currentSearchingFile = emptyString
    }

    // --- NEW FUNCTION TO REMOVE FROM HISTORY ---
    fun removeFromHistory(query: String) {
        try {
            val currentHistory = globalClass.preferencesManager.searchHistory.toMutableSet()
            if (currentHistory.remove(query)) {
                globalClass.preferencesManager.searchHistory = currentHistory
            }
        } catch (e: Exception) {
            logger.logError("Failed to remove from search history: ${e.message}")
        }
    }
    // --- END OF NEW FUNCTION ---

    fun onExpand() {
        globalClass.mainActivityManager.addTabAndSelect(
            FilesTab(VirtualFileHolder(SEARCH))
        )
    }

    // --- All old, slow functions have been removed ---

    // We keep this for compatibility, but our new search doesn't use it.
    private fun getTotalResultsCount(): Int {
        return searchResults.size
    }

    // --- THIS IS THE NEW FUNCTION THAT FIXES YOUR BUG ---
    fun clearAllState() {
        searchQuery = TextFieldValue(emptyString)
        searchResults.clear()
        isSearching = false
        searchProgress = 0f
        currentSearchingFile = emptyString
    }
    // --- END OF NEW FUNCTION ---
}