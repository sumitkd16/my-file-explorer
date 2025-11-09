package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.extension.putParcelableArrayListExtra
import com.raival.compose.file.explorer.common.ui.Isolate
import com.raival.compose.file.explorer.screen.main.tab.files.coil.canUseCoil
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileItem
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ViewConfigs
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListFontSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListIconSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListSpace
import com.raival.compose.file.explorer.viewer.MediaViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: ContentHolder,
    fileDetails: String,
    onItemClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    fontSize: Int = getFileListFontSize(),
    iconSize: Int = getFileListIconSize(),
    space: Int = getFileListSpace(),
    viewConfigs: ViewConfigs = ViewConfigs(),
    mediaFiles: List<FileItem> = emptyList(),
    initialPosition: Int = 0,
    isBookmarked: Boolean = false,
    isFavorited: Boolean = false,
    currentFolder: ContentHolder? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectionHighlightColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    onItemClick?.invoke()
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            )
            .background(
                color = if (isSelected) {
                    selectionHighlightColor
                } else if (isHighlighted) {
                    highlightColor
                } else {
                    Color.Unspecified
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File Icon
        Box(
            modifier = Modifier.size(iconSize.dp)
        ) {
            FileIcon(
                item = item,
                size = iconSize.dp,
                viewConfigs = viewConfigs,
                onClick = { onItemClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
                isBookmarked = isBookmarked,
                isFavorited = isFavorited,
                currentFolder = currentFolder
            )
        }

        Spacer(modifier = Modifier.width(space.dp))

        // File details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayName,
                    fontSize = fontSize.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    lineHeight = (fontSize + 2).sp,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                // Hidden file indicator
                if (item.isHidden()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        modifier = Modifier.size(12.dp),
                        imageVector = Icons.Rounded.VisibilityOff,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "Hidden"
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                modifier = Modifier.graphicsLayer { alpha = 0.7f },
                text = fileDetails,
                fontSize = (fontSize - 4).sp,
                maxLines = 1,
                lineHeight = (fontSize + 2).sp,
                overflow = TextOverflow.Ellipsis,
                color = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Selection indicator
        if (isSelected) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Rounded.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "Selected"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileIcon(
    item: ContentHolder,
    size: androidx.compose.ui.unit.Dp,
    viewConfigs: ViewConfigs,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isBookmarked: Boolean = false,
    isFavorited: Boolean = false,
    currentFolder: ContentHolder? = null
) {
    val isVideo = isVideoFile(item.getFileExtension())
    var videoDuration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.uid) {
        if (isVideo) {
            videoDuration = getVideoDuration(item.uniquePath)
        }
    }

    // Determine which icons to show based on current folder
    val showBookmarkIcon = when {
        currentFolder is VirtualFileHolder && currentFolder.type == VirtualFileHolder.BOOKMARKS -> false
        else -> isBookmarked
    }

    val showFavoriteIcon = when {
        currentFolder is VirtualFileHolder && currentFolder.type == VirtualFileHolder.FAVORITES -> false
        else -> isFavorited
    }

    Isolate {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .graphicsLayer { alpha = if (item.isHidden()) 0.4f else 1f },
        ) {
            var useCoil by remember(item.uid) {
                mutableStateOf(canUseCoil(item))
            }

            if (useCoil) {
                AsyncImage(
                    modifier = Modifier.size(size),
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

            if (!item.canRead) {
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .graphicsLayer { alpha = if (item.isHidden()) 0.4f else 1f },
                    imageVector = Icons.Rounded.Lock,
                    tint = Color.Red,
                    contentDescription = "No access"
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

private fun isMediaFile(extension: String): Boolean {
    val lowerCaseExtension = extension.lowercase()
    return lowerCaseExtension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")
}

private fun openMediaFileWithSwipe(context: Context, item: ContentHolder, mediaFiles: List<FileItem>, initialPosition: Int) {
    val intent = Intent(context, MediaViewerActivity::class.java).apply {
        putParcelableArrayListExtra("media_files", ArrayList<FileItem>(mediaFiles))
        putExtra("initial_position", initialPosition)
    }
    context.startActivity(intent)
}