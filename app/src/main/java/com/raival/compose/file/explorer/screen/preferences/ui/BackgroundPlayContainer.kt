package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.App
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.preferences.PreferencesManager

@Composable
fun BackgroundPlayContainer() {
    val prefs = App.globalClass.preferencesManager

    val backgroundPlayChoices = remember {
        listOf(
            R.string.background_play_always_on,
            R.string.background_play_bluetooth,
            R.string.background_play_off
        ).map { App.globalClass.getString(it) }
    }

    val currentSelectionSummary = when (prefs.backgroundPlayMode) {
        PreferencesManager.BACKGROUND_PLAY_BLUETOOTH -> stringResource(R.string.background_play_bluetooth)
        PreferencesManager.BACKGROUND_PLAY_OFF -> stringResource(R.string.background_play_off)
        else -> stringResource(R.string.background_play_always_on)
    }

    Column(
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.media), // "Media" title
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        PreferenceItem(
            label = stringResource(id = R.string.background_play),
            supportingText = currentSelectionSummary,
            icon = Icons.Rounded.PlayCircleOutline,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = App.globalClass.getString(R.string.background_play),
                    description = App.globalClass.getString(R.string.background_play_summary),
                    choices = backgroundPlayChoices,
                    selectedChoice = prefs.backgroundPlayMode,
                    onSelect = { choice ->
                        prefs.backgroundPlayMode = choice
                        prefs.singleChoiceDialog.dismiss()
                    }
                )
            }
        )
    }
}