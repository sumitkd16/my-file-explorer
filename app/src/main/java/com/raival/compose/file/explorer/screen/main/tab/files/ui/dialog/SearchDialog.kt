package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope // <-- IMPORT ADDED
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.block
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.search.SearchManager
import com.raival.compose.file.explorer.screen.main.tab.files.search.SearchOptions
import kotlinx.coroutines.delay // <-- IMPORT ADDED
import kotlinx.coroutines.launch // <-- IMPORT ADDED

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    val searchManager = globalClass.searchManager

    if (show) {
        val useDarkIcons = !isSystemInDarkTheme()
        var showAdvancedOptions by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope() // <-- SCOPE ADDED FOR ANIMATION DELAY

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
                usePlatformDefaultWidth = false
            )
        ) {
            // Status bar color fix
            val color = MaterialTheme.colorScheme.surfaceContainerHigh
            val systemUiController = rememberSystemUiController()
            DisposableEffect(systemUiController, useDarkIcons) {
                systemUiController.setStatusBarColor(color = color, darkIcons = useDarkIcons)
                onDispose {}
            }

            // --- AUTO-FOCUS & CURSOR-POSITION EFFECT (WITH DELAY) ---
            LaunchedEffect(show) {
                if (show) {
                    delay(150) // <-- DELAY TO ALLOW DIALOG TO ANIMATE IN
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    // Set cursor to end of text
                    searchManager.searchQuery = searchManager.searchQuery.copy(
                        selection = TextRange(searchManager.searchQuery.text.length)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .statusBarsPadding()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(0.dp)
                    )
            ) {
                // Search Header
                SearchHeader(
                    searchManager = searchManager,
                    focusRequester = focusRequester,
                    onBackClick = onDismissRequest,
                    onSearchClick = {
                        // --- ANIMATION-SMOOTHING LOGIC ---
                        keyboardController?.hide()
                        scope.launch {
                            delay(100) // Give keyboard time to start hiding
                            searchManager.startSearch(tab) { shouldExpand ->
                                if (shouldExpand) {
                                    searchManager.onExpand()
                                }
                                onDismissRequest()
                            }
                        }
                    },
                    onAdvancedToggle = { showAdvancedOptions = !showAdvancedOptions }
                )

                // Advanced Options
                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AdvancedOptionsPanel(
                        options = searchManager.searchOptions,
                        onOptionsChange = { searchManager.searchOptions = it }
                    )
                }

                // Search Progress
                AnimatedVisibility(
                    visible = searchManager.isSearching,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchProgressPanel(searchManager = searchManager)
                }

                // --- NEW SEARCH HISTORY UI ---
                AnimatedVisibility(
                    visible = !searchManager.isSearching,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SearchHistoryList(
                        searchManager = searchManager,
                        onHistoryClick = { query ->
                            // Set query and execute search
                            searchManager.searchQuery = TextFieldValue(
                                text = query,
                                selection = TextRange(query.length)
                            )
                            // --- ANIMATION-SMOOTHING LOGIC ---
                            keyboardController?.hide()
                            scope.launch {
                                delay(100) // Give keyboard time
                                searchManager.startSearch(tab) { shouldExpand ->
                                    if (shouldExpand) {
                                        searchManager.onExpand()
                                    }
                                    onDismissRequest()
                                }
                            }
                        },
                        onRemoveClick = { query ->
                            searchManager.removeFromHistory(query)
                        }
                    )
                }

                // --- OLD `SearchResultsSection` IS COMPLETELY REMOVED ---
            }
        }
    }
}

@Composable
private fun SearchHeader(
    searchManager: SearchManager,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAdvancedToggle: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .block(
                    borderSize = 0.dp,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                value = searchManager.searchQuery,
                onValueChange = { searchManager.searchQuery = it },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_query),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingIcon = {
                    Row {
                        if (searchManager.isSearching) {
                            IconButton(
                                onClick = {
                                    if (searchManager.isSearching) {
                                        searchManager.stopSearch()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchClick() }),
                singleLine = true,
            )

            IconButton(onClick = onAdvancedToggle) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun AdvancedOptionsPanel(
    options: SearchOptions,
    onOptionsChange: (SearchOptions) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.advanced_options),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )

        Space(size = 12.dp)

        // Search Options Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OptionSwitch(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.ignore_case),
                checked = options.ignoreCase,
                onCheckedChange = { onOptionsChange(options.copy(ignoreCase = it)) },
                icon = Icons.Rounded.TextFields
            )

            OptionSwitch(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.use_regex),
                checked = options.useRegex,
                onCheckedChange = { onOptionsChange(options.copy(useRegex = it)) },
                icon = Icons.Rounded.Code
            )
        }

        Space(size = 8.dp)

        // Search Options Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OptionSwitch(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.by_extension),
                checked = options.searchByExtension,
                onCheckedChange = { onOptionsChange(options.copy(searchByExtension = it)) },
                icon = Icons.Rounded.Extension
            )

            OptionSwitch(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.in_content),
                checked = options.searchInFileContent,
                onCheckedChange = { onOptionsChange(options.copy(searchInFileContent = it)) },
                icon = Icons.Rounded.Description
            )
        }

        Space(size = 12.dp)

        // Max Results Options
        Column {
            Text(
                text = stringResource(
                    R.string.max_results,
                    if (options.maxResults == -1) globalClass.getString(R.string.unlimited) else options.maxResults.toString()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Space(size = 8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val maxResultsOptions = listOf(10, 100, 1000, 5000, 10000)
                val labels = listOf("10", "100", "1K", "5k", "10K")

                maxResultsOptions.forEachIndexed { index, value ->
                    TextButton(
                        onClick = { onOptionsChange(options.copy(maxResults = value)) },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            containerColor = if (options.maxResults == value)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (options.maxResults == value)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (options.maxResults == value) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    modifier: Modifier = Modifier,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun SearchProgressPanel(searchManager: SearchManager) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            if (searchManager.searchProgress > 0) {
                Text(
                    text = "${(searchManager.searchProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Space(size = 8.dp)

        if (searchManager.searchProgress > -1) {
            LinearProgressIndicator(
                progress = { searchManager.searchProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        }


        if (searchManager.currentSearchingFile.isNotEmpty()) {
            Space(size = 8.dp)
            Text(
                text = searchManager.currentSearchingFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- NEW COMPOSABLE FOR SEARCH HISTORY ---
@Composable
fun SearchHistoryList(
    searchManager: SearchManager,
    onHistoryClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit
) {
    // Read the history from preferences
    val history = globalClass.preferencesManager.searchHistory.toList().reversed()

    if (history.isEmpty()) {
        // You can add a placeholder here if you want
        // e.g., Text("No recent searches", modifier = Modifier.padding(16.dp))
        return
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(
            text = "Recent Searches",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(history, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHistoryClick(query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Space(size = 16.dp)
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Space(size = 8.dp)
                    IconButton(
                        onClick = { onRemoveClick(query) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}