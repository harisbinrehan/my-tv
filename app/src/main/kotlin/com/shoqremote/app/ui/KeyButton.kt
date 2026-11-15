package com.shoqremote.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shoqremote.atv.Direction

private const val LONG_PRESS_MS = 450L
private const val REPEAT_DELAY_MS = 380L
private const val REPEAT_EVERY_MS = 90L

/**
 * A remote key. A tap is one press. Holding a [repeat] key (arrows, volume) presses it again every 90 ms, which
 * scrolls lists predictably; holding any other key is a long press until release, like the real remote.
 */
@Composable
fun KeyButton(
    onKey: (Int, Int) -> Unit,
    code: Int,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Palette.Raised,
    brush: Brush? = null,
    repeat: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val send by rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    val outlined = brush == null && color != Color.Transparent
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .then(if (outlined) Modifier.border(1.dp, Palette.Line, shape) else Modifier)
            .background(if (pressed) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    send(code, Direction.SHORT)
                    true
                }
            }
            .pointerInput(code, repeat) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (repeat) {
                        send(code, Direction.SHORT)
                        var wait = REPEAT_DELAY_MS
                        while (withTimeoutOrNull(wait) { waitForUpOrCancellation(); true } == null) {
                            send(code, Direction.SHORT)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            wait = REPEAT_EVERY_MS
                        }
                    } else {
                        var released = false
                        val quick = withTimeoutOrNull(LONG_PRESS_MS) {
                            released = waitForUpOrCancellation() != null
                            true
                        }
                        if (quick == true) {
                            if (released) send(code, Direction.SHORT)
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            send(code, Direction.START_LONG)
                            waitForUpOrCancellation()
                            send(code, Direction.END_LONG)
                        }
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Calls [onPress] on touch, then again every 70 ms while held; [onPress] learns whether it's a repeat. */
fun Modifier.holdToRepeat(onPressed: (Boolean) -> Unit, onPress: (repeat: Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onPressed(true)
            onPress(false)
            var wait = REPEAT_DELAY_MS
            while (withTimeoutOrNull(wait) { waitForUpOrCancellation(); true } == null) {
                onPress(true)
                wait = 70L
            }
            onPressed(false)
        }
    }
