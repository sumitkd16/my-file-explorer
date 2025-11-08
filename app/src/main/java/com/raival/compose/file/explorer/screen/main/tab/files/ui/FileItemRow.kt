package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
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
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileItem
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ViewConfigs
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListFontSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListIconSize
import com.raival.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListSpace
import com.raival.compose.file.explorer.viewer.MediaViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    isBookmarked: Boolean = false
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
                onLongClick = { onLongClick?.invoke() }
            )

            // Bookmark indicator
            if (isBookmarked) {
                Icon(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd),
                    imageVector = Icons.Rounded.Bookmark,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Bookmarked"
                )
            }
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
    onLongClick: () -> Unit = {}
) {
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

// Bookmark functions
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