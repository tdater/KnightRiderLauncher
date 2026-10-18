package com.aiconsole.knightriderlauncher.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.Gamepad
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/**
 * On-screen game controls for head units without a controller: D-pad, A/B/X/Y, L/R and
 * Select/Start over the bottom corners of the game. Every button works like the same button on
 * a controller (see [BrowserSession.touchButton]): a standard gamepad button and/or the mapped
 * keyboard key. Several buttons can be held at once. Taps between the controls reach the page.
 */
@Composable
fun TouchPad(session: BrowserSession, modifier: Modifier = Modifier) {
    val press = { code: Int, down: Boolean -> session.touchButton(code, down) }
    Box(modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PadButton("L", Gamepad.BUTTON_L1, press, 64.dp, 40.dp, RoundedCornerShape(10.dp))
            PadButton("R", Gamepad.BUTTON_R1, press, 64.dp, 40.dp, RoundedCornerShape(10.dp))
        }
        // D-pad, bottom left.
        Column(Modifier.align(Alignment.BottomStart), horizontalAlignment = Alignment.CenterHorizontally) {
            PadButton("▲", Gamepad.DPAD_UP, press)
            Row {
                PadButton("◀", Gamepad.DPAD_LEFT, press)
                Spacer(Modifier.size(56.dp))
                PadButton("▶", Gamepad.DPAD_RIGHT, press)
            }
            PadButton("▼", Gamepad.DPAD_DOWN, press)
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("SELECT", Gamepad.BUTTON_SELECT, press, 72.dp, 34.dp, RoundedCornerShape(17.dp))
            PadButton("START", Gamepad.BUTTON_START, press, 72.dp, 34.dp, RoundedCornerShape(17.dp))
        }
        // Face buttons, bottom right, in the usual diamond.
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally) {
            PadButton("Y", Gamepad.BUTTON_Y, press, shape = CircleShape)
            Row {
                PadButton("X", Gamepad.BUTTON_X, press, shape = CircleShape)
                Spacer(Modifier.size(56.dp))
                PadButton("A", Gamepad.BUTTON_A, press, shape = CircleShape)
            }
            PadButton("B", Gamepad.BUTTON_B, press, shape = CircleShape)
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    keyCode: Int,
    press: (Int, Boolean) -> Unit,
    width: Dp = 56.dp,
    height: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(width, height)
            .padding(2.dp)
            .clip(shape)
            .background(if (held) ScannerRed.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, ScannerRed.copy(alpha = if (held) 1f else 0.6f), shape)
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    held = true
                    press(keyCode, true)
                    waitForUpOrCancellation()
                    held = false
                    press(keyCode, false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = DashWhite.copy(alpha = 0.9f))
    }
}
