package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileItem
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
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
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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

    val optimalColumnCount = if (tab.viewConfig.galleryMode) {
        calculateOptimalColumnCount()
    } else {
        tab.viewConfig.columnCount
    }

    val lazyGridState = rememberLazyGridState()

    LaunchedEffect(lazyGridState.firstVisibleItemIndex) {
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val lastVisibleIndex = visibleItems.last().index
            val totalItems = tab.activeFolderContent.size

            if (lastVisibleIndex >= totalItems - 20) {
                tab.loadMoreFilesIfNeeded(lastVisibleIndex)
            }
        }
    }

    val gridSpacing = if (tab.viewConfig.galleryMode && tab.viewConfig.hideMediaNames) {
        0.5.dp
    } else {
        1.dp
    }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(optimalColumnCount),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing)
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

@Composable
private fun calculateOptimalColumnCount(): Int {
    return 3
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
    var isFavorited by remember { mutableStateOf(false) }
    var fileDetails by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentItemPath) {
        checkBookmarkStatus(currentItemPath) { bookmarked ->
            isBookmarked = bookmarked
        }

        checkFavoriteStatus(currentItemPath) { favorited ->
            isFavorited = favorited
        }

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
        isBookmarked = isBookmarked,
        isFavorited = isFavorited,
        currentFolder = tab.activeFolder
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
    columnCount: Int = 3
) {
    var isBookmarked by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isVideo = isVideoFile(item.getFileExtension())
    var videoDuration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemPath) {
        checkBookmarkStatus(itemPath) { bookmarked ->
            isBookmarked = bookmarked
        }

        checkFavoriteStatus(itemPath) { favorited ->
            isFavorited = favorited
        }

        if (isVideo) {
            videoDuration = getVideoDuration(item.uniquePath)
        }
    }

    // Determine which icons to show based on current folder
    val showBookmarkIcon = when {
        tab.activeFolder is VirtualFileHolder && (tab.activeFolder as VirtualFileHolder).type == VirtualFileHolder.BOOKMARKS -> false
        else -> isBookmarked
    }

    val showFavoriteIcon = when {
        tab.activeFolder is VirtualFileHolder && (tab.activeFolder as VirtualFileHolder).type == VirtualFileHolder.FAVORITES -> false
        else -> isFavorited
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

    val itemPadding = if (viewConfigs.galleryMode && viewConfigs.hideMediaNames) {
        0.dp
    } else {
        2.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (viewConfigs.galleryMode) Modifier.aspectRatio(1f) else Modifier)
            .combinedClickable(
                onClick = { handleClick() },
                onLongClick = { handleLongClick() }
            )
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)
                } else if (tab.highlightedFiles.contains(itemPath)) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                } else {
                    Color.Unspecified
                },
                shape = RoundedCornerShape(if (viewConfigs.galleryMode && viewConfigs.hideMediaNames) 0.dp else 4.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = if (viewConfigs.galleryMode) Modifier
                    .fillMaxWidth()
                    .weight(1f)
                else Modifier
                    .size((getFileListIconSize(tab.activeFolder) * 2).dp)
            ) {
                Isolate {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .graphicsLayer { alpha = if (item.isHidden()) 0.4f else 1f }
                    ) {
                        var useCoil by remember(item.uid) {
                            mutableStateOf(canUseCoil(item))
                        }

                        if (useCoil) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                model = ImageRequest
                                    .Builder(globalClass)
                                    .data(item)
                                    .build(),
                                filterQuality = FilterQuality.Low,
                                contentScale = if (viewConfigs.cropThumbnails) ContentScale.Crop else ContentScale.Fit,
                                contentDescription = null,
                                onError = { useCoil = false }
                            )
                        } else {
                            FileContentIcon(item)
                        }

                        // Video overlay with reduced transparency (8% instead of 15%)
                        if (isVideo && videoDuration != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.08f))
                            )
                        }
                    }
                }

                // BOTTOM-LEFT: Favorite & Bookmark Icons with Pill Background
                if (showFavoriteIcon || showBookmarkIcon) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite icon (white heart) - shown first
                        if (showFavoriteIcon) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Favorited",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        // Bookmark icon (white bookmark) - shown second
                        if (showBookmarkIcon) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Bookmarked",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // TOP-RIGHT: Video duration badge (only for videos)
                if (isVideo && videoDuration != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = videoDuration!!,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Selection overlay
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

            if (!viewConfigs.galleryMode || (viewConfigs.galleryMode && !viewConfigs.hideMediaNames)) {
                Spacer(modifier = Modifier.height(2.dp))

                val fontSize = if (viewConfigs.galleryMode) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

private fun isVideoFile(extension: String): Boolean {
    return FileMimeType.videoFileType.contains(extension.lowercase())
}

private suspend fun getVideoDuration(filePath: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            duration?.let {
                val durationMs = it.toLong()
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                val hours = durationMs / (1000 * 60 * 60)

                if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%d:%02d", minutes, seconds)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

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

    if (isNewSelection) {
        tab.selectedFiles[currentItemPath] = item
        tab.lastSelectedFileIndex = index
        tab.onSelectionChange()
    }

    tab.toggleFileOptionsMenu(item)
}

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

private suspend fun checkFavoriteStatus(filePath: String, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val favorites = globalClass.preferencesManager.favorites
            val isFavorited = favorites.any { it == filePath }
            onResult(isFavorited)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}