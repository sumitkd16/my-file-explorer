package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.extension.putParcelableArrayListExtra
import com.raival.compose.file.explorer.common.ui.Isolate
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.coil.canUseCoil
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileItem
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ViewConfigs
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ViewType
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListFontSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListIconSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListSpace
import com.raival.compose.file.explorer.viewer.MediaViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.FilesList(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(Modifier.weight(1f)) {
        if (tab.activeFolderContent.isEmpty() && !tab.isLoading) {
            EmptyFolderContent(tab)
        }

        if (preferencesManager.disablePullDownToRefresh) {
            FilesListContent(tab)
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.openFolder(tab.activeFolder, true, true)
                    coroutineScope.launch {
                        delay(100)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                FilesListContent(tab)
            }
        }

        LoadingOverlay(tab)
    }
}

@Composable
private fun EmptyFolderContent(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = stringResource(
                when {
                    !tab.activeFolder.canRead -> R.string.cant_access_content
                    else -> R.string.empty
                }
            ),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        if (tab.activeFolder.canRead && !preferencesManager.showHiddenFiles) {
            Space(12.dp)
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = stringResource(R.string.empty_without_hidden_files),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingOverlay(tab: FilesTab) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = tab.isLoading
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { }
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun FilesListContent(tab: FilesTab) {
    when (tab.viewConfig.viewType) {
        ViewType.LIST -> FilesListColumns(tab)
        ViewType.GRID -> FilesListGrid(tab)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListColumns(tab: FilesTab) {
    val context = LocalContext.current
    val mediaFiles = tab.activeFolderContent.filter { isMediaFile(it.getFileExtension()) }.map { it.toFileItem() }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
    ) {
        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uid }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            val mediaFileIndex = mediaFiles.indexOfFirst { it.path == item.uniquePath }

            ColumnFileItem(
                item = item,
                index = index,
                tab = tab,
                context = context,
                currentItemPath = currentItemPath,
                isSelectedItem = isSelectedItem,
                onSelection = { isSelectedItem = it },
                viewConfigs = tab.viewConfig,
                mediaFiles = mediaFiles,
                initialPosition = mediaFileIndex
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListGrid(tab: FilesTab) {
    val context = LocalContext.current
    val mediaFiles = tab.activeFolderContent.filter { isMediaFile(it.getFileExtension()) }.map { it.toFileItem() }

    // Calculate optimal column count based on screen size and gallery mode
    val optimalColumnCount = if (tab.viewConfig.galleryMode) {
        // For gallery mode, use more columns for better density
        calculateOptimalColumnCount()
    } else {
        // For regular grid, use standard column count
        tab.viewConfig.columnCount
    }

    // Performance: Use rememberLazyGridState for better control
    val lazyGridState = rememberLazyGridState()

    // Performance: Load more files when scrolling near the end
    LaunchedEffect(lazyGridState.firstVisibleItemIndex) {
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val lastVisibleIndex = visibleItems.last().index
            val totalItems = tab.activeFolderContent.size

            // Load more if we're near the end and there might be more content
            if (lastVisibleIndex >= totalItems - 20) { // Load when 20 items from end
                tab.loadMoreFilesIfNeeded(lastVisibleIndex)
            }
        }
    }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(optimalColumnCount),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uid }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            val mediaFileIndex = mediaFiles.indexOfFirst { it.path == item.uniquePath }

            GridFileItem(
                item = item,
                index = index,
                tab = tab,
                context = context,
                itemPath = currentItemPath,
                isSelected = isSelectedItem,
                onSelection = { isSelectedItem = it },
                viewConfigs = tab.viewConfig,
                mediaFiles = mediaFiles,
                initialPosition = mediaFileIndex,
                columnCount = optimalColumnCount
            )
        }

        // Performance: Show loading indicator at the bottom when loading more
        if (tab.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

// Calculate optimal column count based on screen density
@Composable
private fun calculateOptimalColumnCount(): Int {
    // For gallery mode, aim for 3-5 columns depending on screen size
    return 3 // As per your suggestion - 3 columns for better thumbnail size
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    context: Context,
    currentItemPath: String,
    isSelectedItem: Boolean,
    onSelection: (Boolean) -> Unit,
    viewConfigs: ViewConfigs,
    mediaFiles: List<FileItem>,
    initialPosition: Int
) {
    var isBookmarked by remember { mutableStateOf(false) }
    var fileDetails by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Load bookmark status and file details
    LaunchedEffect(currentItemPath) {
        checkBookmarkStatus(currentItemPath) { bookmarked ->
            isBookmarked = bookmarked
        }

        // Load file details in background
        coroutineScope.launch {
            val details = withContext(Dispatchers.IO) {
                item.getDetails()
            }
            fileDetails = details
        }
    }

    fun toggleSelection() {
        if (tab.selectedFiles.containsKey(currentItemPath)) {
            tab.selectedFiles.remove(currentItemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[currentItemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.onSelectionChange()
    }

    fun handleClick() {
        if (tab.selectedFiles.isNotEmpty()) {
            toggleSelection()
        } else {
            val extension = item.getFileExtension().lowercase()
            if (isMediaFile(extension)) {
                openMediaFileWithSwipe(context, item, mediaFiles, initialPosition)
            } else if (item.isFile()) {
                tab.openFile(context, item)
            } else {
                tab.openFolder(item, false)
            }
        }
    }

    fun handleLongClick() {
        handleLongClick(tab, currentItemPath, item, index)
        onSelection(true)
    }

    FileItemRow(
        item = item,
        fileDetails = fileDetails,
        onItemClick = { handleClick() },
        onLongClick = { handleLongClick() },
        isSelected = isSelectedItem,
        isHighlighted = tab.highlightedFiles.contains(currentItemPath),
        fontSize = getFileListFontSize(tab.activeFolder),
        iconSize = getFileListIconSize(tab.activeFolder),
        space = getFileListSpace(tab.activeFolder),
        viewConfigs = viewConfigs,
        mediaFiles = mediaFiles,
        initialPosition = initialPosition,
        isBookmarked = isBookmarked
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    context: Context,
    itemPath: String,
    isSelected: Boolean,
    onSelection: (Boolean) -> Unit,
    viewConfigs: ViewConfigs,
    mediaFiles: List<FileItem>,
    initialPosition: Int,
    columnCount: Int = 3 // Default to 3 columns as per your preference
) {
    var isBookmarked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load bookmark status
    LaunchedEffect(itemPath) {
        checkBookmarkStatus(itemPath) { bookmarked ->
            isBookmarked = bookmarked
        }
    }

    fun toggleSelection() {
        if (tab.selectedFiles.containsKey(itemPath)) {
            tab.selectedFiles.remove(itemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[itemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.onSelectionChange()
    }

    fun handleClick() {
        if (tab.selectedFiles.isNotEmpty()) {
            toggleSelection()
        } else {
            val extension = item.getFileExtension().lowercase()
            if (isMediaFile(extension)) {
                openMediaFileWithSwipe(context, item, mediaFiles, initialPosition)
            } else if (item.isFile()) {
                tab.openFile(context, item)
            } else {
                tab.openFolder(item, false)
            }
        }
    }

    fun handleLongClick() {
        handleLongClick(tab, itemPath, item, index)
        onSelection(true)
    }

    // Calculate optimal padding based on column count
    val itemPadding = when (columnCount) {
        in 1..3 -> 4.dp  // More space for fewer columns
        in 4..5 -> 2.dp  // Less space for more columns
        else -> 1.dp     // Minimal space for many columns
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (viewConfigs.galleryMode) Modifier.aspectRatio(1f) else Modifier)
            .combinedClickable(
                onClick = { handleClick() },
                onLongClick = { handleLongClick() }
            )
            .padding(itemPadding)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)
                } else if (tab.highlightedFiles.contains(itemPath)) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                } else {
                    Color.Unspecified
                },
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image/Icon container - optimized for better space usage
            Box(
                modifier = if (viewConfigs.galleryMode) Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(2.dp)
                else Modifier
                    .size((getFileListIconSize(tab.activeFolder) * 2).dp)
            ) {
                FileIcon(
                    item = item,
                    size = if (viewConfigs.galleryMode) {
                        // Use larger size for gallery mode to fill available space
                        120.dp // Increased from previous smaller size
                    } else {
                        (getFileListIconSize(tab.activeFolder) * 1.8).dp // Slightly larger for regular grid
                    },
                    viewConfigs = viewConfigs,
                    onClick = { handleClick() },
                    onLongClick = { handleLongClick() }
                )

                // Bookmark indicator for grid
                if (isBookmarked) {
                    Icon(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                            .padding(2.dp),
                        imageVector = Icons.Rounded.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Bookmarked"
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center),
                            imageVector = Icons.Rounded.CheckCircle,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null
                        )
                    }
                }
            }

            // File name - only show if not in gallery mode or if media names are not hidden
            if (!viewConfigs.galleryMode || (viewConfigs.galleryMode && !viewConfigs.hideMediaNames)) {
                Spacer(modifier = Modifier.height(4.dp))
                val fontSize = if (viewConfigs.galleryMode) {
                    // Slightly smaller font for gallery mode to save space
                    getFileListFontSize(tab.activeFolder) * 0.7
                } else {
                    getFileListFontSize(tab.activeFolder) * 0.8
                }

                Text(
                    text = item.displayName,
                    fontSize = fontSize.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = if (viewConfigs.galleryMode) 1 else 2,
                    lineHeight = (fontSize + 2).sp,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (tab.highlightedFiles.contains(itemPath)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Function to open media files with our swipe viewer
private fun openMediaFileWithSwipe(context: Context, item: ContentHolder, mediaFiles: List<FileItem>, initialPosition: Int) {
    val intent = Intent(context, MediaViewerActivity::class.java).apply {
        putParcelableArrayListExtra("media_files", ArrayList<FileItem>(mediaFiles))
        putExtra("initial_position", initialPosition)
    }
    context.startActivity(intent)
}

private fun isMediaFile(extension: String): Boolean {
    val lowerCaseExtension = extension.lowercase()
    return lowerCaseExtension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")
}

private fun ContentHolder.toFileItem(): FileItem {
    return FileItem(
        path = this.uniquePath,
        name = this.displayName,
        isDirectory = this.isFolder,
        lastModified = this.lastModified,
        size = this.size,
        extension = this.getFileExtension()
    )
}

private fun handleLongClick(
    tab: FilesTab,
    currentItemPath: String,
    item: ContentHolder,
    index: Int
) {
    val isFirstSelection = tab.selectedFiles.isEmpty()
    val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
    val isNewSelection = !isAlreadySelected

    // Always add the item to selection on long click
    if (isNewSelection) {
        tab.selectedFiles[currentItemPath] = item
        tab.lastSelectedFileIndex = index
        tab.onSelectionChange()
    }

    // Show file options menu
    tab.toggleFileOptionsMenu(item)
}

// Bookmark helper function
private suspend fun checkBookmarkStatus(filePath: String, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val bookmarks = com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getBookmarks()
            val isBookmarked = bookmarks.any { it.uniquePath == filePath }
            onResult(isBookmarked)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}