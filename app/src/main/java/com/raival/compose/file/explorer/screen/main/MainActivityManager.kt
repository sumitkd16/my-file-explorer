package com.raival.compose.file.explorer.screen.main

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.fromJson
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.padEnd
import com.raival.compose.file.explorer.common.printFullStackTrace
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toJson
import com.raival.compose.file.explorer.screen.main.model.GithubRelease
import com.raival.compose.file.explorer.screen.main.startup.StartupTab
import com.raival.compose.file.explorer.screen.main.startup.StartupTabType
import com.raival.compose.file.explorer.screen.main.startup.StartupTabs
import com.raival.compose.file.explorer.screen.main.tab.Tab
import com.raival.compose.file.explorer.screen.main.tab.apps.AppsTab
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.SEARCH
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.raival.compose.file.explorer.screen.main.tab.home.HomeTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.max
import kotlin.math.min

class MainActivityManager {
    val managerScope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(MainActivityState())
    val state = _state.asStateFlow()

    var newUpdate: GithubRelease? = null

    fun setup() {
        managerScope.launch {
            _state.update {
                it.copy(
                    storageDevices = StorageProvider.getStorageDevices(globalClass)
                )
            }
        }
    }

    fun removeOtherTabs(tabIndex: Int) {
        if (tabIndex isNot _state.value.selectedTabIndex) return

        val tabToKeep = _state.value.tabs[tabIndex]
        val tabsToRemove = _state.value.tabs.filter { it != tabToKeep }

        tabsToRemove.forEach { it.onTabRemoved() }

        var isSearchTab = false
        if (tabToKeep is FilesTab) {
            if (tabToKeep.activeFolder is VirtualFileHolder) {
                if ((tabToKeep.activeFolder as VirtualFileHolder).type == SEARCH) {
                    isSearchTab = true
                }
            }
        }

        if (!isSearchTab) {
            globalClass.searchManager.clearAllState()
        }

        _state.update {
            it.copy(
                tabs = listOf(tabToKeep),
                selectedTabIndex = 0
            )
        }
    }

    fun removeTabAt(index: Int) {
        if (_state.value.tabs.size <= 1) return

        val tabToRemove = _state.value.tabs[index]
        val currentSelectedIndex = _state.value.selectedTabIndex

        var isSearchTab = false
        if (tabToRemove is FilesTab) {
            if (tabToRemove.activeFolder is VirtualFileHolder) {
                if ((tabToRemove.activeFolder as VirtualFileHolder).type == SEARCH) {
                    isSearchTab = true
                }
            }
        }
        if (isSearchTab) {
            globalClass.searchManager.clearAllState()
        }

        if (currentSelectedIndex == index) {
            tabToRemove.onTabStopped()
        }
        tabToRemove.onTabRemoved()

        val newSelectedTabIndex = if (index < currentSelectedIndex) {
            currentSelectedIndex - 1
        } else if (index > currentSelectedIndex) {
            currentSelectedIndex
        } else {
            max(0, index - 1)
        }

        _state.update {
            it.copy(
                tabs = _state.value.tabs.filterIndexed { i, _ ->
                    i isNot index
                },
                selectedTabIndex = newSelectedTabIndex
            )
        }

        if (index == currentSelectedIndex) {
            val newSelectedTab = _state.value.tabs[newSelectedTabIndex]
            if (newSelectedTab.isCreated) {
                newSelectedTab.onTabResumed()
            } else {
                newSelectedTab.onTabStarted()
            }
        }
    }

    fun addTabAndSelect(tab: Tab, index: Int = -1) {
        val currentActiveTab = getActiveTab()

        currentActiveTab?.onTabStopped()

        val validatedIndex = if (index isNot -1) {
            max(0, min(index, _state.value.tabs.lastIndex + 1))
        } else {
            _state.value.tabs.lastIndex + 1
        }

        _state.update {
            it.copy(
                tabs = _state.value.tabs + tab,
                selectedTabIndex = validatedIndex
            )
        }

        if (tab.isCreated) {
            tab.onTabResumed()
        } else {
            tab.onTabStarted()
        }
    }

    fun selectTabAt(index: Int, skipTabRefresh: Boolean = false) {
        val validatedIndex = if (index isNot -1) {
            max(0, min(index, _state.value.tabs.lastIndex))
        } else {
            _state.value.tabs.lastIndex
        }

        val currentSelectedIndex = _state.value.selectedTabIndex
        val currentActiveTab = getActiveTab()

        if (validatedIndex == currentSelectedIndex) {
            if (!skipTabRefresh) {
                currentActiveTab?.onTabResumed()
            }
        } else {
            currentActiveTab?.onTabStopped()

            _state.update {
                it.copy(selectedTabIndex = validatedIndex)
            }

            val newSelectedTab = _state.value.tabs[validatedIndex]
            if (newSelectedTab.isCreated) {
                newSelectedTab.onTabResumed()
            } else {
                newSelectedTab.onTabStarted()
            }
        }
    }

    // --- FIXED VERSION USING VERSION 1 LOGIC ---
    fun replaceCurrentTabWith(tab: Tab, keepCurrentTabAsParent: Boolean = false) {
        val currentActiveTab = getActiveTab()
        val currentTabIndex = _state.value.selectedTabIndex

        // --- VERSION 1 GHOST TAB FIX: Handle replace (Home button) ---
        if (currentActiveTab is FilesTab && tab is HomeTab) {
            var isCurrentTabSearch = false
            if (currentActiveTab.activeFolder is VirtualFileHolder) {
                if ((currentActiveTab.activeFolder as VirtualFileHolder).type == SEARCH) {
                    isCurrentTabSearch = true
                }
            }

            if (isCurrentTabSearch) {
                // Clear search memory
                globalClass.searchManager.clearAllState()

                // Find and remove ghost tab
                val ghostTabIndex = currentTabIndex - 1
                if (ghostTabIndex >= 0) {
                    val ghostTab = _state.value.tabs[ghostTabIndex]
                    if (ghostTab is FilesTab && ghostTab.isTemporaryForSearch) {
                        // Remove ghost tab first
                        removeTabAt(ghostTabIndex)
                        // After removal, indices shift, so we don't need further adjustment
                        return
                    }
                }
            }
        }
        // --- END VERSION 1 LOGIC ---

        currentActiveTab?.apply {
            onTabStopped()
            if (!keepCurrentTabAsParent) onTabRemoved()
        }

        if (keepCurrentTabAsParent) {
            tab.parentTab = currentActiveTab
        }

        _state.update {
            it.copy(
                tabs = _state.value.tabs.mapIndexed { index, oldTab ->
                    if (index == _state.value.selectedTabIndex) tab else oldTab
                }
            )
        }

        if (tab.isCreated) {
            tab.onTabResumed()
        } else {
            tab.onTabStarted()
        }
    }

    fun jumpToFile(file: LocalFileHolder, context: Context) {
        openFile(file, context)
    }

    private fun openFile(file: LocalFileHolder, context: Context) {
        if (file.exists()) {
            addTabAndSelect(FilesTab(file, context))
        }
    }

    fun resumeActiveTab() {
        getActiveTab()?.onTabResumed()
    }

    fun onResume() {
        resumeActiveTab()
    }

    fun onStop() {
        getActiveTab()?.onTabStopped()
    }

    fun getActiveTab(): Tab? {
        return if (_state.value.tabs.isNotEmpty()) {
            _state.value.tabs[_state.value.selectedTabIndex]
        } else {
            null
        }
    }

    fun canExit(): Boolean {
        val tabs = _state.value.tabs
        val selectedTabIndex = _state.value.selectedTabIndex

        if (tabs.isEmpty()) {
            return true
        }

        if (getActiveTab()!!.onBackPressed()) {
            return false
        }

        if (getActiveTab()!!.parentTab != null) {
            replaceCurrentTabWith(getActiveTab()!!.parentTab!!, false)
            return false
        }

        if (getActiveTab() !is HomeTab && !globalClass.preferencesManager.skipHomeWhenTabClosed) {
            replaceCurrentTabWith(HomeTab())
            return false
        }

        if (tabs.size > 1 && selectedTabIndex isNot 0 && globalClass.preferencesManager.closeTabOnBackPress) {
            removeTabAt(selectedTabIndex)
            return false
        }

        if (tabs.size == 1 && !allTextEditorFileInstancesSaved()) {
            _state.update {
                it.copy(
                    showSaveEditorFilesDialog = true
                )
            }
            return false
        }

        return true
    }

    private fun allTextEditorFileInstancesSaved(): Boolean {
        globalClass.textEditorManager.fileInstanceList.forEach {
            if (it.requireSave) return false
        }
        return true
    }

    fun ignoreTextEditorFiles() {
        globalClass.textEditorManager.fileInstanceList.clear()
    }

    fun saveTextEditorFiles(onFinish: () -> Unit) {
        _state.update {
            it.copy(
                isSavingFiles = true
            )
        }

        managerScope.launch {
            globalClass.textEditorManager.fileInstanceList.apply {
                forEach {
                    if (it.requireSave) {
                        it.file.writeText(it.content.toString())
                    }
                }
                clear()
            }

            _state.update {
                it.copy(
                    isSavingFiles = false
                )
            }

            onFinish()
        }
    }

    fun saveSession() {
        val startupTabs = arrayListOf<StartupTab>()
        _state.value.tabs.forEach { tab ->
            when (tab) {
                is FilesTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = tab.activeFolder.uniquePath
                        )
                    )
                }

                is AppsTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.APPS
                        )
                    )
                }

                else -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.HOME
                        )
                    )
                }
            }
        }
        globalClass.preferencesManager.lastSessionTabs = StartupTabs(startupTabs).toJson()
    }

    fun loadStartupTabs() {
        managerScope.launch {
            val startupTabs: StartupTabs =
                if (globalClass.preferencesManager.rememberLastSession)
                    fromJson(globalClass.preferencesManager.lastSessionTabs)
                        ?: StartupTabs.default()
                else fromJson(globalClass.preferencesManager.startupTabs)
                    ?: StartupTabs.default()

            val tabs = arrayListOf<Tab>()
            val index = 0

            startupTabs.tabs.forEachIndexed { _, tab ->
                val newTab = when (tab.type) {
                    StartupTabType.FILES -> FilesTab(LocalFileHolder(File(tab.extra)))
                    StartupTabType.APPS -> AppsTab()
                    else -> HomeTab()
                }
                tabs.add(newTab)
            }

            _state.update {
                it.copy(
                    tabs = tabs,
                    selectedTabIndex = index
                )
            }

            tabs.forEachIndexed { tabIndex, tab ->
                if (tabIndex == index) {
                    if (tab.isCreated) {
                        tab.onTabResumed()
                    } else {
                        tab.onTabStarted()
                    }
                }
            }
        }
    }

    fun checkForUpdate() {
        fetchGithubReleases { releases ->
            val latestRelease = releases.firstOrNull() ?: return@fetchGithubReleases
            val latestVersionName = latestRelease.tagName

            try {
                val currentVersionName = globalClass.packageManager.getPackageInfo(
                    globalClass.packageName,
                    0
                ).versionName ?: emptyString

                val latestVersion = parseVersion(latestVersionName)
                val currentVersion = parseVersion(currentVersionName)

                if (isNewerVersion(latestVersion, currentVersion)) {
                    newUpdate = latestRelease
                    _state.update { it.copy(hasNewUpdate = true) }
                    showMsg(R.string.new_update_available)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isNewerVersion(latestVersion: List<Int>, currentVersion: List<Int>): Boolean {
        val componentCount = max(latestVersion.size, currentVersion.size)
        val latestPadded = latestVersion.padEnd(componentCount, 0)
        val currentPadded = currentVersion.padEnd(componentCount, 0)

        for (i in 0 until componentCount) {
            if (latestPadded[i] > currentPadded[i]) {
                return true
            }
            if (latestPadded[i] < currentPadded[i]) {
                return false
            }
        }
        return false
    }

    private fun parseVersion(versionName: String): List<Int> {
        return versionName.removePrefix("v")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
    }

    fun fetchGithubReleases(
        onResult: (List<GithubRelease>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "https://api.github.com/repos/Raival-e/Prism-File-Explorer/releases"
            var releases = emptyList<GithubRelease>()

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Prism-File-Explorer")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        releases = Gson().fromJson(
                            InputStreamReader(
                                inputStream
                            ),
                            object : TypeToken<List<GithubRelease>>() {}.type
                        )
                    }
                }
            } catch (_: UnknownHostException) {
                logger.logInfo(globalClass.getString(R.string.check_for_updates_failed_no_internet_connection))
            } catch (_: ConnectException) {
                logger.logInfo(globalClass.getString(R.string.check_for_updates_failed_failed_to_connect_to_server))
            } catch (e: Exception) {
                logger.logError(e.printFullStackTrace())
            }

            onResult(releases)
        }
    }

    fun onSearchClicked() {
        val activeTab = getActiveTab()

        if (activeTab is FilesTab) {
            activeTab.isTemporaryForSearch = false
            activeTab.toggleSearchPenal(true)
        } else {
            val internalStorage = StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder
            val newFilesTab = FilesTab(internalStorage)

            newFilesTab.isTemporaryForSearch = true

            addTabAndSelect(newFilesTab)
            newFilesTab.toggleSearchPenal(true)
        }
    }

    fun closeCurrentTabAndRevertToHome() {
        val currentTabIndex = _state.value.selectedTabIndex

        if (currentTabIndex != 0) {
            removeTabAt(currentTabIndex)

            if (_state.value.tabs.size > 0 && _state.value.selectedTabIndex != 0) {
                selectTabAt(0)
            }

            globalClass.searchManager.clearAllState()
        }
    }

    fun toggleJumpToPathDialog(show: Boolean) {
        _state.update {
            it.copy(
                showJumpToPathDialog = show
            )
        }
    }

    fun toggleAppInfoDialog(show: Boolean) {
        _state.update {
            it.copy(
                showAppInfoDialog = show
            )
        }
    }

    fun toggleSaveEditorFilesDialog(show: Boolean) {
        _state.update {
            it.copy(
                showSaveEditorFilesDialog = show
            )
        }
    }

    fun toggleStartupTabsDialog(show: Boolean) {
        _state.update {
            it.copy(
                showStartupTabsDialog = show
            )
        }
    }

    fun reorderTabs(from: Int, to: Int) {
        _state.update {
            it.copy(
                tabs = _state.value.tabs.toMutableList().apply {
                    add(to, removeAt(from))
                },
                selectedTabIndex = _state.value.tabs.indexOf(getActiveTab())
            )
        }
    }

    fun updateHomeToolbar(title: String, subtitle: String) {
        _state.update {
            it.copy(title = title, subtitle = subtitle)
        }
    }
}