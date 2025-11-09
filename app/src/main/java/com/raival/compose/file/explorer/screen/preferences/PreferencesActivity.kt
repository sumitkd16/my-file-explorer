package com.raival.compose.file.explorer.screen.preferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.App
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.fromJson
import com.raival.compose.file.explorer.common.toJson
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.preferences.ui.AppInfoContainer
import com.raival.compose.file.explorer.screen.preferences.ui.AppearanceContainer
import com.raival.compose.file.explorer.screen.preferences.ui.BackgroundPlayContainer // <-- NEW IMPORT
import com.raival.compose.file.explorer.screen.preferences.ui.BehaviorContainer
import com.raival.compose.file.explorer.screen.preferences.ui.FileListContainer
import com.raival.compose.file.explorer.screen.preferences.ui.FileOperationContainer
import com.raival.compose.file.explorer.screen.preferences.ui.PreferenceItem
import com.raival.compose.file.explorer.screen.preferences.ui.RecentFilesContainer
import com.raival.compose.file.explorer.screen.preferences.ui.SingleChoiceDialog
import com.raival.compose.file.explorer.screen.preferences.ui.TextEditorContainer
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class PreferencesActivity : BaseActivity() {

    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    private lateinit var exportFavoritesLauncher: ActivityResultLauncher<Intent>
    private lateinit var importFavoritesLauncher: ActivityResultLauncher<Intent>

    private val prefs by lazy { App.globalClass.preferencesManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerLaunchers()
        checkPermissions()
    }

    private fun registerLaunchers() {
        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportBookmarks(uri)
                }
            }
        }

        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importBookmarks(uri)
                }
            }
        }

        exportFavoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportFavorites(uri)
                }
            }
        }

        importFavoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importFavorites(uri)
                }
            }
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                SafeSurface {
                    // This is the Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(color = colorScheme.surfaceContainer)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null
                            )
                        }
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(id = R.string.preferences),
                                fontSize = 21.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    SingleChoiceDialog()

                    // This is the main scrolling list of settings
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppearanceContainer()

                        // --- NEW CONTAINER IN CORRECT POSITION ---
                        BackgroundPlayContainer()
                        // --- END NEW ---

                        FileListContainer()
                        FileOperationContainer()
                        BehaviorContainer()
                        RecentFilesContainer()
                        TextEditorContainer()
                        AppInfoContainer()

                        BackupContainer(
                            onExportClick = { startExport() },
                            onImportClick = { startImport() },
                            onExportFavoritesClick = { startExportFavorites() },
                            onImportFavoritesClick = { startImportFavorites() }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BackupContainer(
        onExportClick: () -> Unit,
        onImportClick: () -> Unit,
        onExportFavoritesClick: () -> Unit,
        onImportFavoritesClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.backup_and_restore),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PreferenceItem(
                label = stringResource(id = R.string.export_bookmarks),
                supportingText = stringResource(id = R.string.export_bookmarks_summary),
                icon = Icons.Rounded.Download,
                onClick = onExportClick
            )
            PreferenceItem(
                label = stringResource(id = R.string.import_bookmarks),
                supportingText = stringResource(id = R.string.import_bookmarks_summary),
                icon = Icons.Rounded.Upload,
                onClick = onImportClick
            )
            PreferenceItem(
                label = stringResource(id = R.string.export_favorites),
                supportingText = stringResource(id = R.string.export_favorites_summary),
                icon = Icons.Rounded.Download,
                onClick = onExportFavoritesClick
            )
            PreferenceItem(
                label = stringResource(id = R.string.import_favorites),
                supportingText = stringResource(id = R.string.import_favorites_summary),
                icon = Icons.Rounded.Upload,
                onClick = onImportFavoritesClick
            )
        }
    }

    // --- Bookmarks Functions ---
    private fun startExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "file-explorer-bookmarks.json")
        }
        exportLauncher.launch(intent)
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun exportBookmarks(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bookmarksSet = prefs.bookmarks
                val jsonString = bookmarksSet.toJson()

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Bookmarks exported!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importBookmarks(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }

                if (jsonString.isNullOrBlank()) {
                    throw Exception("File is empty or invalid.")
                }

                val importedBookmarks = fromJson<Set<String>>(jsonString)

                if (importedBookmarks != null) {
                    val currentBookmarks = prefs.bookmarks
                    val combinedBookmarks = currentBookmarks + importedBookmarks
                    prefs.bookmarks = combinedBookmarks

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PreferencesActivity, "Bookmarks imported and merged!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("Failed to parse backup file.")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Favorites Functions ---
    private fun startExportFavorites() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "file-explorer-favorites.json")
        }
        exportFavoritesLauncher.launch(intent)
    }

    private fun startImportFavorites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importFavoritesLauncher.launch(intent)
    }

    private fun exportFavorites(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val favoritesSet = prefs.favorites
                val jsonString = favoritesSet.toJson()

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Favorites exported!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importFavorites(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }

                if (jsonString.isNullOrBlank()) {
                    throw Exception("File is empty or invalid.")
                }

                val importedFavorites = fromJson<Set<String>>(jsonString)

                if (importedFavorites != null) {
                    val currentFavorites = prefs.favorites
                    val combinedFavorites = currentFavorites + importedFavorites
                    prefs.favorites = combinedFavorites

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PreferencesActivity, "Favorites imported and merged!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("Failed to parse backup file.")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PreferencesActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}