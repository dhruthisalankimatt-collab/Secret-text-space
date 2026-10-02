package com.dsi.app.ui.lock

import androidx.lifecycle.ViewModel

enum class Quadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Holds the in-progress tap sequence.
 *
 * Deliberately has no observable state: nothing about the sequence reaches the UI, so
 * there is nothing for the screen to react to and no way for a wrong tap to produce a
 * visible, audible or haptic change. The only output is the unlock callback.
 *
 * The sequence itself is never logged, and the ViewModel is cleared on unlock.
 */
class LockViewModel : ViewModel() {

    private val expected = listOf(
        Quadrant.BOTTOM_RIGHT,
        Quadrant.TOP_LEFT,
        Quadrant.TOP_RIGHT
    )

    private val entered = mutableListOf<Quadrant>()

    /** Returns true only when the full correct sequence has just been completed. */
    fun onTap(quadrant: Quadrant): Boolean {
        val index = entered.size
        if (quadrant != expected[index]) {
            // Silent reset. A tap that is wrong at position 0 may still be the start of a
            // fresh attempt, so re-test it against the first step before giving up.
            entered.clear()
            if (quadrant == expected[0]) entered.add(quadrant)
            return false
        }
        entered.add(quadrant)
        if (entered.size == expected.size) {
            entered.clear()
            return true
        }
        return false
    }

    fun reset() = entered.clear()

    override fun onCleared() {
        entered.clear()
        super.onCleared()
    }
}
