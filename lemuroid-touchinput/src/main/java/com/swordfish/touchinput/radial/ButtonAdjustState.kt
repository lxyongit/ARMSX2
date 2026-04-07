package com.swordfish.touchinput.radial

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Shared state for per-group button position adjustment.
 * Holds offsets for each button group and provides drag handling in adjust mode.
 */
class ButtonAdjustState(
    private val _offsets: SnapshotStateMap<String, Pair<Float, Float>> = androidx.compose.runtime.mutableStateMapOf(),
) {
    var isAdjusting by mutableStateOf(false)

    fun getOffset(groupId: String): Pair<Float, Float> = _offsets[groupId] ?: Pair(0f, 0f)

    fun onDrag(groupId: String, dx: Float, dy: Float) {
        val current = _offsets[groupId] ?: Pair(0f, 0f)
        _offsets[groupId] = Pair(current.first + dx, current.second + dy)
    }

    fun setOffset(groupId: String, offsetX: Float, offsetY: Float) {
        if (offsetX != 0f || offsetY != 0f) {
            _offsets[groupId] = Pair(offsetX, offsetY)
        }
    }

    fun clearAllOffsets() {
        _offsets.clear()
    }

    fun getAllOffsets(): Map<String, Pair<Float, Float>> = _offsets.toMap()

    companion object {
        // Primary controls
        const val ID_PRIMARY_LEFT = "primary_left"
        const val ID_PRIMARY_RIGHT = "primary_right"

        // Shoulder buttons
        const val ID_SHOULDER_L = "shoulder_l"
        const val ID_SHOULDER_L2 = "shoulder_l2"
        const val ID_SHOULDER_R = "shoulder_r"
        const val ID_SHOULDER_R2 = "shoulder_r2"

        // Function buttons
        const val ID_SELECT = "select"
        const val ID_START = "start"
        const val ID_MENU = "menu"

        // Analog sticks
        const val ID_LEFT_ANALOG = "left_analog"
        const val ID_RIGHT_ANALOG = "right_analog"
        const val ID_THUMBL = "thumb_l"
        const val ID_THUMBR = "thumb_r"
    }
}

val LocalButtonAdjustState = compositionLocalOf { ButtonAdjustState() }

/**
 * Returns a Modifier that applies saved offset and drag handling for a button group.
 * In adjust mode, the control is draggable and all touch events trigger drag (not button actions).
 * In normal mode, only the saved offset is applied.
 */
@Composable
fun adjustableModifier(groupId: String): Modifier {
    val adjustState = LocalButtonAdjustState.current
    val isAdjusting = adjustState.isAdjusting
    return Modifier
        .offset {
            val offset = adjustState.getOffset(groupId)
            IntOffset(offset.first.roundToInt(), offset.second.roundToInt())
        }
        .pointerInput(isAdjusting) {
            if (isAdjusting) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    adjustState.onDrag(groupId, dragAmount.x, dragAmount.y)
                }
            }
        }
}
