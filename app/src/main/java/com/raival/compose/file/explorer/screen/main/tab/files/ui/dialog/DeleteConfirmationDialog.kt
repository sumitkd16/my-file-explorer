package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val context = LocalContext.current
        val targetFiles by remember(tab.id, tab.activeFolder.uniquePath) {
            mutableStateOf(tab.selectedFiles.map { it.value }.toList())
        }

        val bottomOptionsBarState = tab.bottomOptionsBarState.collectAsState()

        AlertDialog(
            onDismissRequest = { onDismissRequest() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        tab.unselectAllFiles()

                        // --- THIS IS THE NEW UNIVERSAL TRASH LOGIC ---
                        tab.scope.launch {
                            var failedCount = 0
                            targetFiles.forEach { fileHolder ->
                                if (fileHolder is LocalFileHolder) {
                                    // Call the Universal Trash function
                                    moveToTrash(context, fileHolder.file.absolutePath) { success ->
                                        if (!success) {
                                            failedCount++
                                        }
                                    }
                                }
                            }

                            // After the loop, show a message if any failed
                            withContext(Dispatchers.Main) {
                                if (failedCount > 0) {
                                    globalClass.showMsg("$failedCount files could not be moved to trash.")
                                }
                                // Manually reload the file list since files are now in the trash
                                tab.reloadFiles()
                            }
                        }
                        // --- END OF NEW LOGIC ---
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) { Text(stringResource(R.string.dismiss)) }
            },
            title = { Text(text = stringResource(R.string.delete_confirmation)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.delete_confirmation_message)
                    )

                    // --- REMOVED ALL CHECKBOXES for internal recycle bin ---
                }
            }
        )
    }
}

// --- HELPER FUNCTIONS COPIED FROM MediaViewerActivity.kt ---

private fun isImage(extension: String): Boolean {
    return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif")
}

private fun isVideo(extension: String): Boolean {
    return extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")
}

private fun isAudio(extension: String): Boolean {
    return extension in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "amr", "opus", "mid", "midi")
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
                    // Fallback for non-media files (e.g., .txt) which can't be "trashed"
                    // In a real app, you'd use ACTION_TRASH_FILE Intent Sender
                    // For now, we'll just delete, as the internal bin is removed.
                    val deleted = file.delete()
                    onResult(deleted)
                }
            } else {
                // On older Android, just delete the file
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
    // Determine which collection to query based on file type
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
        else -> return null // Not a standard media file, can't be "trashed" via MediaStore
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