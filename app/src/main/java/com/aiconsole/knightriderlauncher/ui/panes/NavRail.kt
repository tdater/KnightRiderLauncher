package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.UiSounds
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal
import com.aiconsole.knightriderlauncher.ui.theme.KittGrid
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.assistantName

/**
 * Side rail for landscape head units: KITT on top, then one console button per home page.
 * On a 7"–10" landscape screen, height is what's scarce, so page selection moves from a row of
 * tabs above the dock to the side, where the width is spare. Every button stays a large touch
 * target (the rail splits the full height between them).
 */
@Composable
fun NavRail(
    labels: List<String>,
    current: Int,
    onSelect: (Int) -> Unit,
    onKitt: () -> Unit,
    kittSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1.5f)
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, KittGrid, MaterialTheme.shapes.small)
                .clickable {
                    UiSounds.click()
                    onKitt()
                }
                .padding(4.dp)
        ) {
            VoiceModulator(active = kittSpeaking, modifier = Modifier.fillMaxWidth().weight(1f))
            Text(
                assistantName(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = ScannerRed,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        labels.forEachIndexed { index, label ->
            KittButton(
                label = label,
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = ScannerRed,
                active = index == current
            )
        }
    }
}
