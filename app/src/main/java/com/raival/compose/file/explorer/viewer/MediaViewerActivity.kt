@file:OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)

package com.raival.compose.file.explorer.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.extension.getParcelableArrayListExtra
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileItem
import com.raival.compose.file.explorer.screen.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

// *** HELPER FUNCTIONS ***

private fun isImage(extension: String): Boolean {
    return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif")
}

private fun isVideo(extension: String): Boolean {
    return extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")
}

private fun isAudio(extension: String): Boolean {
    return extension in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "amr", "opus", "mid", "midi")
}

private suspend fun checkBookmarkStatus(filePath: String, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val bookmarks = globalClass.preferencesManager.bookmarks
            val isBookmarked = bookmarks.any { it == filePath }
            onResult(isBookmarked)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

private suspend fun toggleBookmark(filePath: String, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                onResult(false)
                return@withContext
            }
            val currentBookmarks = globalClass.preferencesManager.bookmarks.toMutableSet()
            val isCurrentlyBookmarked = currentBookmarks.any { it == filePath }

            if (isCurrentlyBookmarked) {
                currentBookmarks.remove(filePath)
                globalClass.preferencesManager.bookmarks = currentBookmarks
                onResult(true)
            } else {
                currentBookmarks.add(filePath)
                globalClass.preferencesManager.bookmarks = currentBookmarks
                onResult(true)
            }
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

private suspend fun toggleFavorite(filePath: String, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                onResult(false)
                return@withContext
            }
            val currentFavorites = globalClass.preferencesManager.favorites.toMutableSet()
            val isCurrentlyFavorited = currentFavorites.any { it == filePath }

            if (isCurrentlyFavorited) {
                currentFavorites.remove(filePath)
                globalClass.preferencesManager.favorites = currentFavorites
                onResult(true)
            } else {
                currentFavorites.add(filePath)
                globalClass.preferencesManager.favorites = currentFavorites
                onResult(true)
            }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

private suspend fun shareFile(context: android.content.Context, filePath: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension.lowercase()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when {
                    isImage(extension) -> "image/*"
                    isVideo(extension) -> "video/*"
                    isAudio(extension) -> "audio/*"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
            }
        } catch (e: Exception) {
            // Silent failure
        }
    }
}

private suspend fun renameFile(filePath: String, newName: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val oldFile = File(filePath)
            if (!oldFile.exists()) return@withContext false
            val newFile = File(oldFile.parent, newName)
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            false
        }
    }
}

private suspend fun moveToTrash(
    context: android.content.Context,
    filePath: String,
    onResult: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                onResult(false)
                return@withContext
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val contentResolver: ContentResolver = context.contentResolver
                val uri = getMediaUri(contentResolver, file)
                if (uri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_TRASHED, 1)
                    }
                    val updated = contentResolver.update(uri, values, null, null)
                    onResult(updated > 0)
                } else {
                    val deleted = file.delete()
                    onResult(deleted)
                }
            } else {
                val deleted = file.delete()
                onResult(deleted)
            }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

private fun getMediaUri(contentResolver: ContentResolver, file: File): Uri? {
    val extension = file.extension.lowercase()
    val collection = when {
        isImage(extension) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        isVideo(extension) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }
        isAudio(extension) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        }
        else -> return null
    }

    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val selection = "${MediaStore.MediaColumns.DATA}=?"
    val selectionArgs = arrayOf(file.absolutePath)

    try {
        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    if (bytes <= 0) return "0 B"
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// --- NEW, SAFER HELPER FUNCTION (NO PERMISSIONS NEEDED) ---
private fun isExternalAudioDeviceActive(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // Check for any Bluetooth, Wired, or USB audio output
        devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    } else {
        // Deprecated but necessary for older phones
        @Suppress("DEPRECATION")
        audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn
    }
}
// --- END NEW ---

// *** END OF HELPER FUNCTIONS ***

@OptIn(ExperimentalFoundationApi::class)
class MediaViewerActivity : ComponentActivity() {
    private val mediaFiles by lazy {
        intent.getParcelableArrayListExtra<FileItem>("media_files") ?: emptyList()
    }
    private val initialPosition by lazy {
        intent.getIntExtra("initial_position", 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MediaViewerScreen(
                        mediaFiles = mediaFiles,
                        initialPosition = initialPosition,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<FileItem>,
    initialPosition: Int,
    onBack: () -> Unit
) {
    var currentMediaList by remember { mutableStateOf(mediaFiles.toList()) }
    val pagerState = rememberPagerState(initialPage = initialPosition) { currentMediaList.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBookmarked by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    var currentFilePath by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showUI by remember { mutableStateOf(false) }

    var showThreeDotMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // --- LIFECYCLE & PLAYER MANAGEMENT ---
    val prefs = globalClass.preferencesManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val players = remember { mutableStateMapOf<Int, Player>() }

    // This BroadcastReceiver listens for headphone/bluetooth plug/unplug events
    val audioRouteReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_HEADSET_PLUG ||
                    intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {

                    val player = players[pagerState.currentPage] ?: return

                    if (prefs.backgroundPlayMode == PreferencesManager.BACKGROUND_PLAY_BLUETOOTH && player.playWhenReady) {
                        // Check if an external device is *still* active. If not, pause.
                        if (!isExternalAudioDeviceActive(context)) {
                            player.pause()
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        context.registerReceiver(audioRouteReceiver, filter)
        onDispose {
            context.unregisterReceiver(audioRouteReceiver)
        }
    }

    // This observer handles onPause (Home button / screen lock)
    DisposableEffect(lifecycleOwner, players, pagerState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val player = players[pagerState.currentPage] ?: return@LifecycleEventObserver

                if (!player.playWhenReady) return@LifecycleEventObserver

                when (prefs.backgroundPlayMode) {
                    PreferencesManager.BACKGROUND_PLAY_OFF -> {
                        player.pause()
                    }
                    PreferencesManager.BACKGROUND_PLAY_BLUETOOTH -> {
                        if (!isExternalAudioDeviceActive(context)) {
                            player.pause()
                        }
                    }
                    PreferencesManager.BACKGROUND_PLAY_ALWAYS_ON -> {
                        // Let it play
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // --- END ---

    val windowInsetsController = remember(context) {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, context.window.decorView)
        }
    }

    LaunchedEffect(windowInsetsController) {
        windowInsetsController?.isAppearanceLightStatusBars = false
        windowInsetsController?.isAppearanceLightNavigationBars = false
    }

    LaunchedEffect(showUI) {
        windowInsetsController?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showUI) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(Unit) {
        if (currentMediaList.isNotEmpty() && initialPosition < currentMediaList.size) {
            currentFilePath = currentMediaList[initialPosition].path
            checkBookmarkStatus(currentFilePath) { bookmarked ->
                isBookmarked = bookmarked
            }
            checkFavoriteStatus(currentFilePath) { favorited ->
                isFavorited = favorited
            }
        }
    }

    LaunchedEffect(pagerState.settledPage, currentMediaList) {
        if (currentMediaList.isNotEmpty() && pagerState.settledPage < currentMediaList.size) {
            currentFilePath = currentMediaList[pagerState.settledPage].path
            checkBookmarkStatus(currentFilePath) { bookmarked ->
                isBookmarked = bookmarked
            }
            checkFavoriteStatus(currentFilePath) { favorited ->
                isFavorited = favorited
            }
            errorMessage = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController?.isAppearanceLightStatusBars = true
            windowInsetsController?.isAppearanceLightNavigationBars = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets(0.dp))
            .background(Color.Black)
    ) {
        if (currentMediaList.isEmpty()) {
            LaunchedEffect(Unit) {
                delay(100)
                onBack()
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
            ) { page ->
                if (page >= currentMediaList.size) return@HorizontalPager
                val fileItem = currentMediaList[page]
                val file = File(fileItem.path)

                MediaContent(
                    file = file,
                    pagerState = pagerState,
                    pageIndex = page,
                    showUI = showUI,
                    onToggleUI = { showUI = !showUI },
                    onError = { error ->
                        errorMessage = "Cannot load: ${file.name}"
                    },
                    players = players
                )
            }
        }

        val fadeSpec = tween<Float>(durationMillis = 250, easing = LinearEasing)

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = fadeSpec),
            exit = fadeOut(animationSpec = fadeSpec),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopOverlay(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isBookmarked = isBookmarked,
                    isFavorited = isFavorited,
                    showThreeDotMenu = showThreeDotMenu,
                    onBack = onBack,
                    onBookmarkToggle = {
                        scope.launch {
                            toggleBookmark(currentFilePath) { success ->
                                if (success) {
                                    isBookmarked = !isBookmarked
                                }
                            }
                        }
                    },
                    onFavoriteToggle = {
                        scope.launch {
                            toggleFavorite(currentFilePath) { success ->
                                if (success) {
                                    isFavorited = !isFavorited
                                }
                            }
                        }
                    },
                    onThreeDotClick = { showThreeDotMenu = true },
                    onDismissMenu = { showThreeDotMenu = false },
                    onShare = {
                        scope.launch {
                            shareFile(context, currentFilePath)
                        }
                        showThreeDotMenu = false
                    },
                    onInfo = {
                        showInfoDialog = true
                        showThreeDotMenu = false
                    },
                    onRename = {
                        showRenameDialog = true
                        showThreeDotMenu = false
                    },
                    onDelete = {
                        showDeleteDialog = true
                        showThreeDotMenu = false
                    }
                )
            }
        }

        if (showInfoDialog) {
            val currentFileItem = currentMediaList.getOrNull(pagerState.currentPage)
            if (currentFileItem != null) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("File Properties") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Name: ${currentFileItem.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("Size: ${formatFileSize(currentFileItem.size)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Modified: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(currentFileItem.lastModified)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Path: ${currentFileItem.path}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }

        if (showRenameDialog) {
            val currentFileItem = currentMediaList.getOrNull(pagerState.currentPage)
            var newName by remember { mutableStateOf(currentFileItem?.name ?: "") }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename") },
                text = {
                    Column {
                        Text("Enter new name:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank() && currentFileItem != null) {
                                scope.launch {
                                    val success = renameFile(currentFileItem.path, newName)
                                    if (success) {
                                        val oldFile = File(currentFileItem.path)
                                        val newFile = File(oldFile.parent, newName)
                                        currentFilePath = newFile.absolutePath
                                        val currentIndex = pagerState.currentPage
                                        currentMediaList = currentMediaList.toMutableList().apply {
                                            set(currentIndex, FileItem(
                                                path = newFile.absolutePath,
                                                name = newFile.name,
                                                isDirectory = newFile.isDirectory,
                                                lastModified = newFile.lastModified(),
                                                size = newFile.length(),
                                                extension = newFile.extension
                                            ))
                                        }
                                    } else {
                                        errorMessage = "Failed to rename file"
                                    }
                                }
                            }
                            showRenameDialog = false
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Move to trash?") },
                text = { Text("This item will be moved to trash and deleted after 30 days.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                moveToTrash(context, currentFilePath) { success ->
                                    if (success) {
                                        val currentIndex = pagerState.currentPage
                                        currentMediaList = currentMediaList.toMutableList().apply {
                                            removeAt(currentIndex)
                                        }
                                        if (currentMediaList.isEmpty()) {
                                            onBack()
                                        }
                                    } else {
                                        errorMessage = "Failed to delete"
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Move to trash")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun TopOverlay(
    modifier: Modifier = Modifier,
    isBookmarked: Boolean,
    isFavorited: Boolean,
    showThreeDotMenu: Boolean,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onThreeDotClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LEFT SIDE ---
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // --- RIGHT SIDE ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorited) "Remove favorite" else "Add favorite",
                        tint = if (isFavorited) Color(0xFFE53935) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (isBookmarked) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = onThreeDotClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showThreeDotMenu,
                        onDismissRequest = onDismissMenu,
                        offset = DpOffset(0.dp, 0.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = onShare,
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Info") },
                            onClick = onInfo,
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = onRename,
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = onDelete,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaContent(
    file: File,
    pagerState: PagerState,
    pageIndex: Int,
    showUI: Boolean,
    onToggleUI: () -> Unit,
    onError: (String) -> Unit,
    players: MutableMap<Int, Player>
) {
    if (!file.exists() || !file.canRead()) {
        onError("File not accessible")
        return
    }
    val extension = file.extension.lowercase()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isImage(extension) -> {
                ZoomableImage(
                    file = file,
                    onToggleUI = onToggleUI,
                    onError = onError
                )
            }
            isVideo(extension) -> {
                VideoPlayer(
                    uri = Uri.fromFile(file),
                    pagerState = pagerState,
                    pageIndex = pageIndex,
                    showUI = showUI,
                    onToggleUI = onToggleUI,
                    onError = onError,
                    players = players
                )
            }
            isAudio(extension) -> {
                AudioPlayer(
                    file = file,
                    pagerState = pagerState,
                    pageIndex = pageIndex,
                    onToggleUI = onToggleUI,
                    onError = onError,
                    players = players
                )
            }
            else -> {
                Text(
                    text = "Unsupported: .$extension",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    onToggleUI: () -> Unit,
    onError: (String) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun calculateMaxOffset(currentScale: Float): Pair<Float, Float> {
        val maxX = if (currentScale > 1f) (currentScale - 1f) * 1000f else 0f
        val maxY = if (currentScale > 1f) (currentScale - 1f) * 1000f else 0f
        return maxX to maxY
    }

    fun constrainOffset(x: Float, y: Float, currentScale: Float): Pair<Float, Float> {
        val (maxX, maxY) = calculateMaxOffset(currentScale)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var initialDistance = 0f
                    var isPinching = false
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.size
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)

                            if (pointerCount >= 2 && initialDistance == 0f) {
                                initialDistance = centroidSize
                            }
                            val distanceChanged = if (initialDistance > 0f) {
                                abs(centroidSize - initialDistance)
                            } else 0f
                            val significantZoom = abs(zoomChange - 1f) > 0.01f
                            val significantDistance = distanceChanged > 15f

                            if (pointerCount >= 2 && (significantZoom || significantDistance)) {
                                isPinching = true
                            }
                            val isZoomedIn = scale > 1.01f
                            val shouldHandle = isPinching || isZoomedIn

                            if (shouldHandle) {
                                event.changes.forEach { it.consume() }
                                if (pointerCount >= 2) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    if (abs(newScale - scale) > 0.001f) {
                                        val scaleRatio = newScale / scale
                                        val newOffsetX = offsetX * scaleRatio
                                        val newOffsetY = offsetY * scaleRatio
                                        val (constrainedX, constrainedY) = constrainOffset(
                                            newOffsetX,
                                            newOffsetY,
                                            newScale
                                        )
                                        scale = newScale
                                        offsetX = constrainedX
                                        offsetY = constrainedY
                                        scope.launch {
                                            animScale.snapTo(scale)
                                            animOffsetX.snapTo(offsetX)
                                            animOffsetY.snapTo(offsetY)
                                        }
                                    }
                                } else if (pointerCount == 1 && isZoomedIn) {
                                    val newOffsetX = offsetX + panChange.x
                                    val newOffsetY = offsetY + panChange.y
                                    val (constrainedX, constrainedY) = constrainOffset(
                                        newOffsetX,
                                        newOffsetY,
                                        scale
                                    )
                                    offsetX = constrainedX
                                    offsetY = constrainedY
                                    scope.launch {
                                        animOffsetX.snapTo(offsetX)
                                        animOffsetY.snapTo(offsetY)
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                    initialDistance = 0f
                    isPinching = false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleUI()
                    },
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale > 1.1f) {
                                launch {
                                    animScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    animOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    animOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                val targetScale = 2.5f
                                val viewportCenterX = size.width / 2f
                                val viewportCenterY = size.height / 2f

                                val tapPointShiftX = (tapOffset.x - viewportCenterX) * (targetScale - 1f)
                                val tapPointShiftY = (tapOffset.y - viewportCenterY) * (targetScale - 1f)

                                val newOffsetX = -tapPointShiftX
                                val newOffsetY = -tapPointShiftY

                                val (constrainedX, constrainedY) = constrainOffset(
                                    newOffsetX,
                                    newOffsetY,
                                    targetScale
                                )
                                launch {
                                    animScale.animateTo(
                                        targetValue = targetScale,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    animOffsetX.animateTo(
                                        targetValue = constrainedX,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    animOffsetY.animateTo(
                                        targetValue = constrainedY,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                scale = targetScale
                                offsetX = constrainedX
                                offsetY = constrainedY
                            }
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animScale.value
                    scaleY = animScale.value
                    translationX = animOffsetX.value
                    translationY = animOffsetY.value
                },
            contentScale = ContentScale.Fit,
            onError = {
                onError("Failed to load image")
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoPlayer(
    uri: Uri,
    pagerState: PagerState,
    pageIndex: Int,
    showUI: Boolean,
    onToggleUI: () -> Unit,
    onError: (String) -> Unit,
    players: MutableMap<Int, Player>
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        try {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = false
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            players[pageIndex] = player
            player
        } catch (e: Exception) {
            null
        }
    }

    if (exoPlayer != null) {
        var currentPosition by remember { mutableStateOf(0L) }
        var totalDuration by remember { mutableStateOf(0L) }
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableStateOf(0f) }
        var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
        var currentVolume by remember { mutableStateOf(1f) }

        var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
        var isPlayerRendered by remember { mutableStateOf(false) }

        var isActuallyPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
        var userPlayIntent by remember { mutableStateOf(exoPlayer.playWhenReady) } // <-- ICON SYNC FIX

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                    isActuallyPlaying = isPlayingChange
                    userPlayIntent = isPlayingChange // <-- ICON SYNC FIX
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                }

                override fun onVolumeChanged(volume: Float) {
                    isMuted = volume == 0f
                    if (volume > 0f) {
                        currentVolume = volume
                    }
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    super.onVideoSizeChanged(size)
                    if (size != VideoSize.UNKNOWN) {
                        videoSize = size
                    }
                }

                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    isPlayerRendered = true
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
            }
        }

        LaunchedEffect(isActuallyPlaying, isSeeking) {
            while (isActuallyPlaying && !isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                delay(100)
            }
        }

        // --- AUTOPLAY-ON-SWIPE FIX ---
        LaunchedEffect(pagerState.settledPage, pageIndex) {
            val isSettledPage = pagerState.settledPage == pageIndex

            if (isSettledPage) {
                // When we swipe TO a new page, always play it.
                exoPlayer.playWhenReady = true
            } else {
                // If we are swiping AWAY, always pause.
                exoPlayer.pause()
            }

            if (isSettledPage && (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_ENDED)) {
                exoPlayer.seekTo(0)
            }
        }
        // --- END FIX ---

        LaunchedEffect(Unit) {
            if (pagerState.currentPage == pageIndex) {
                exoPlayer.playWhenReady = true
                userPlayIntent = true
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    players.remove(pageIndex)
                    exoPlayer.stop()
                    exoPlayer.release()
                } catch (e: Exception) {
                    // Silent cleanup
                }
            }
        }

        val isVideoReady = isPlayerRendered && videoSize != VideoSize.UNKNOWN
        val playerAlpha by animateFloatAsState(
            targetValue = if (isVideoReady) 1f else 0f,
            animationSpec = tween(durationMillis = 150)
        )
        val thumbAlpha by animateFloatAsState(
            targetValue = if (isVideoReady) 0f else 1f,
            animationSpec = tween(durationMillis = 150)
        )

        val playerModifier = if (videoSize != VideoSize.UNKNOWN) {
            Modifier.aspectRatio(videoSize.width / videoSize.height.toFloat().coerceAtLeast(0.001f))
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val tapZonePercent = 0.20f
                    detectTapGestures(
                        onTap = { onToggleUI() },
                        onDoubleTap = { tapOffset ->
                            val screenWidth = size.width
                            when {
                                tapOffset.x < screenWidth * tapZonePercent -> {
                                    val newPos = (exoPlayer.currentPosition - 5000).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                }
                                tapOffset.x > screenWidth * (1f - tapZonePercent) -> {
                                    val newPos = (exoPlayer.currentPosition + 5000).coerceAtMost(totalDuration)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Video Thumbnail",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(thumbAlpha),
                contentScale = ContentScale.Fit,
                onError = { onError("Failed to load video thumbnail") }
            )

            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        exoPlayer.setVideoSurfaceView(this)
                    }
                },
                modifier = playerModifier.alpha(playerAlpha)
            )

            val fadeSpec = tween<Float>(durationMillis = 250, easing = LinearEasing)
            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn(animationSpec = fadeSpec),
                exit = fadeOut(animationSpec = fadeSpec),
                modifier = Modifier.fillMaxSize()
            ) {
                UnifiedMediaControls(
                    isPlaying = userPlayIntent,
                    isMuted = isMuted,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    isSeeking = isSeeking,
                    seekPosition = seekPosition,
                    onPlayPauseToggle = {
                        userPlayIntent = !userPlayIntent
                        exoPlayer.playWhenReady = userPlayIntent
                    },
                    onMuteToggle = {
                        if (isMuted) {
                            exoPlayer.volume = currentVolume
                        } else {
                            currentVolume = exoPlayer.volume
                            exoPlayer.volume = 0f
                        }
                    },
                    onSeekChange = { newPos ->
                        isSeeking = true
                        seekPosition = newPos
                    },
                    onSeekFinished = {
                        isSeeking = false
                        exoPlayer.seekTo(seekPosition.toLong())
                        currentPosition = seekPosition.toLong()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        onError("Failed to create video player")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioPlayer(
    file: File,
    pagerState: PagerState,
    pageIndex: Int,
    onToggleUI: () -> Unit,
    onError: (String) -> Unit,
    players: MutableMap<Int, Player>
) {
    val context = LocalContext.current
    val uri = Uri.fromFile(file)

    val exoPlayer = remember {
        try {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
                playWhenReady = false
            }
            players[pageIndex] = player
            player
        } catch (e: Exception) {
            null
        }
    }

    if (exoPlayer != null) {
        var currentPosition by remember { mutableStateOf(0L) }
        var totalDuration by remember { mutableStateOf(0L) }
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableStateOf(0f) }
        var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
        var currentVolume by remember { mutableStateOf(1f) }

        var isActuallyPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
        var userPlayIntent by remember { mutableStateOf(exoPlayer.playWhenReady) } // <-- ICON SYNC FIX

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                    isActuallyPlaying = isPlayingChange
                    userPlayIntent = isPlayingChange // <-- ICON SYNC FIX
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                }

                override fun onVolumeChanged(volume: Float) {
                    isMuted = volume == 0f
                    if (volume > 0f) {
                        currentVolume = volume
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
            }
        }

        LaunchedEffect(isActuallyPlaying, isSeeking) {
            while (isActuallyPlaying && !isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                delay(100)
            }
        }

        // --- AUTOPLAY-ON-SWIPE FIX ---
        LaunchedEffect(pagerState.settledPage, pageIndex) {
            val isSettledPage = pagerState.settledPage == pageIndex

            if (isSettledPage) {
                // When we swipe TO a new page, always play it.
                exoPlayer.playWhenReady = true
            } else {
                // If we are swiping AWAY, always pause.
                exoPlayer.pause()
            }
        }
        // --- END FIX ---

        LaunchedEffect(Unit) {
            if (pagerState.currentPage == pageIndex) {
                exoPlayer.playWhenReady = true
                userPlayIntent = true
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                players.remove(pageIndex)
                exoPlayer.stop()
                exoPlayer.release()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleUI() })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.offset(y = (-48).dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Audio File",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), CircleShape)
                        .padding(24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            UnifiedMediaControls(
                isPlaying = userPlayIntent,
                isMuted = isMuted,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                isSeeking = isSeeking,
                seekPosition = seekPosition,
                onPlayPauseToggle = {
                    userPlayIntent = !userPlayIntent
                    exoPlayer.playWhenReady = userPlayIntent
                },
                onMuteToggle = {
                    if (isMuted) {
                        exoPlayer.volume = currentVolume
                    } else {
                        currentVolume = exoPlayer.volume
                        exoPlayer.volume = 0f
                    }
                },
                onSeekChange = { newPos ->
                    isSeeking = true
                    seekPosition = newPos
                },
                onSeekFinished = {
                    isSeeking = false
                    exoPlayer.seekTo(seekPosition.toLong())
                    currentPosition = seekPosition.toLong()
                },
                modifier = Modifier.fillMaxSize(),
                alwaysVisibleGradient = true
            )
        }
    } else {
        onError("Failed to initialize audio player")
    }
}

@Composable
private fun UnifiedMediaControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isSeeking: Boolean,
    seekPosition: Float,
    onPlayPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    alwaysVisibleGradient: Boolean = false
) {
    var sliderContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val durationAsFloat = remember(totalDuration) { totalDuration.toFloat().coerceAtLeast(1.0f) }

    Box(modifier = modifier) {
        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (alwaysVisibleGradient) {
                        Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatDuration(if (isSeeking) seekPosition.toLong() else currentPosition),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.width(42.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .onSizeChanged { sliderContainerSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val widthPx = sliderContainerSize.width.toFloat()
                                if (widthPx > 0) {
                                    val newPos = (offset.x / widthPx) * durationAsFloat
                                    onSeekChange(newPos.coerceIn(0f, durationAsFloat))
                                    onSeekFinished()
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val widthPx = sliderContainerSize.width.toFloat()
                                    if (widthPx > 0) {
                                        val newPos = (offset.x / widthPx) * durationAsFloat
                                        onSeekChange(newPos.coerceIn(0f, durationAsFloat))
                                    }
                                },
                                onDragEnd = {
                                    onSeekFinished()
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val widthPx = sliderContainerSize.width.toFloat()
                                    if (widthPx > 0) {
                                        val newPos = (change.position.x / widthPx) * durationAsFloat
                                        onSeekChange(newPos.coerceIn(0f, durationAsFloat))
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )

                    val progress =
                        (if (isSeeking) seekPosition else currentPosition.toFloat()) / durationAsFloat

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Color.White)
                    )

                    val thumbOffsetDp = with(density) {
                        (sliderContainerSize.width * progress.coerceIn(0f, 1f)).toDp() - 6.dp
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                            .size(12.dp)
                            .background(Color.White, CircleShape)
                    )
                }

                Text(
                    text = formatDuration(totalDuration),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.width(42.dp)
                )

                IconButton(
                    onClick = onMuteToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}