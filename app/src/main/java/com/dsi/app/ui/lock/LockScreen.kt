package com.dsi.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A plain white screen divided into four equal, invisible touch targets.
 *
 * Correct sequence (bottom-right, top-left, top-right) navigates onward. Anything else
 * does nothing at all: no vibration, no sound, no message, no visual change. The screen
 * is indistinguishable before and after a wrong tap, which is the entire point.
 *
 * The quadrants are laid out with weights rather than fixed sizes, so they stay exactly
 * half the width and half the height on any screen size, aspect ratio or orientation.
 * Each target is at least a quarter of the display, far above the 48dp minimum.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // Hidden from accessibility services and screen readers: an unlabelled grid
            // announced as four buttons would give the mechanism away.
            .clearAndSetSemantics { }
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            QuadrantTarget(Quadrant.TOP_LEFT, viewModel, onUnlocked, Modifier.weight(1f))
            QuadrantTarget(Quadrant.TOP_RIGHT, viewModel, onUnlocked, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            QuadrantTarget(Quadrant.BOTTOM_LEFT, viewModel, onUnlocked, Modifier.weight(1f))
            QuadrantTarget(Quadrant.BOTTOM_RIGHT, viewModel, onUnlocked, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuadrantTarget(
    quadrant: Quadrant,
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.White)
            // detectTapGestures fires on up, not down, so a drag across quadrants while
            // scrolling or handing the phone over does not register as a tap.
            .pointerInput(quadrant) {
                detectTapGestures(
                    onTap = { if (viewModel.onTap(quadrant)) onUnlocked() }
                )
            }
    )
}
