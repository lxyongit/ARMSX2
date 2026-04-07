package com.swordfish.touchinput.radial.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class KeyMode {
    Simultaneous,
    Sequence
}

private fun findGLRetroView(view: View): View? {
    if (view.javaClass.name == "com.swordfish.libretrodroid.GLRetroView") return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findGLRetroView(child)
            if (result != null) return result
        }
    }
    return null
}

private fun expandKey(key: Int): List<Int> {
    return when (key) {
        -1 -> listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        -2 -> listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        -3 -> listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        -4 -> listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT)
        else -> listOf(key)
    }
}

@Composable
fun LemuroidCentralButtonOne(
    pressedState: State<Boolean>,
    label: String? = null,
    buttons: List<Int> = emptyList(),
    continuous: Boolean = false,
    interval: Long = 16L,
    keyMode: KeyMode = KeyMode.Simultaneous,
    labelScale: Float = 1.0f,
) {
    val view = LocalView.current
    val onEvent: (Int, Boolean) -> Unit = { key, pressed ->
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val rootView = view.rootView
        val glView = findGLRetroView(rootView)
        if (glView != null) {
            try {
                val methods = glView.javaClass.methods.filter { it.name == "sendKeyEvent" }
                val method = methods.find { it.parameterTypes.size == 3 }
                    ?: methods.find { it.parameterTypes.size == 2 }

                if (method != null) {
                    if (method.parameterTypes.size == 3) {
                        method.invoke(glView, action, key, 0)
                    } else {
                        method.invoke(glView, action, key)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val theme = LocalLemuroidPadTheme.current
    val currentOnEvent = rememberUpdatedState(onEvent)
    val currentButtons = rememberUpdatedState(buttons)

    LaunchedEffect(pressedState.value) {
        if (pressedState.value) {
            val eventHandler = currentOnEvent.value
            val btns = currentButtons.value

            if (eventHandler != null && btns.isNotEmpty()) {
                do {
                    when (keyMode) {
                        KeyMode.Sequence -> {
                            if (continuous) {
                                for (btn in btns) {
                                    val realKeys = expandKey(btn)
                                    try {
                                        realKeys.forEach { eventHandler(it, true) }
                                        delay(32L)
                                    } finally {
                                        withContext(NonCancellable) {
                                            realKeys.forEach { eventHandler(it, false) }
                                        }
                                    }
                                }
                                delay(interval)
                            } else {
                                withContext(NonCancellable) {
                                    for (btn in btns) {
                                        val realKeys = expandKey(btn)
                                        realKeys.forEach { eventHandler(it, true) }
                                        delay(32L)
                                        realKeys.forEach { eventHandler(it, false) }
                                    }
                                }
                            }
                        }
                        KeyMode.Simultaneous -> {
                            val allKeys = btns.flatMap { expandKey(it) }
                            if (continuous) {
                                allKeys.forEach { eventHandler(it, true) }
                                delay(interval)
                                allKeys.forEach { eventHandler(it, false) }
                                delay(interval)
                            } else {
                                allKeys.forEach { eventHandler(it, true) }
                                try {
                                    awaitCancellation()
                                } finally {
                                    withContext(NonCancellable) {
                                        allKeys.forEach { eventHandler(it, false) }
                                    }
                                }
                            }
                        }
                    }
                } while (continuous && isActive)
            }
        }
    }

    Box(modifier = Modifier.padding(theme.padding)) {
        LemuroidControlBackground()
        LemuroidButtonForeground(
            pressed = pressedState,
            label = label,
            labelScale = labelScale,
        )
    }
}
