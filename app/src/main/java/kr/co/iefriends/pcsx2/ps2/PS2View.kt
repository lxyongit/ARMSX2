package kr.co.iefriends.pcsx2.ps2

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kr.co.iefriends.pcsx2.NativeApp
import kr.co.iefriends.pcsx2.R
import com.swordfish.touchinput.radial.ButtonAdjustState
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.LocalButtonAdjustState
import androidx.compose.runtime.CompositionLocalProvider
import com.swordfish.touchinput.radial.layouts.PS2Left
import com.swordfish.touchinput.radial.layouts.PS2Right
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import android.view.KeyEvent
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File

private const val PS2_TOUCH_LAYOUT_PREFS = "ps2_touch_layout"
private const val PS2_TOUCH_LAYOUT_KEY_PREFIX = "button_offsets_"

private fun getTouchLayoutPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(PS2_TOUCH_LAYOUT_PREFS, Context.MODE_PRIVATE)
}

private fun getTouchLayoutKey(orientation: Int): String {
    val suffix = if (orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"
    return "$PS2_TOUCH_LAYOUT_KEY_PREFIX$suffix"
}

private fun loadButtonOffsets(preferences: SharedPreferences, layoutKey: String): Map<String, Pair<Float, Float>> {
    val raw = preferences.getString(layoutKey, null).orEmpty()
    if (raw.isBlank()) {
        return emptyMap()
    }

    return try {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val groupId = keys.next()
                val item = root.optJSONObject(groupId) ?: continue
                val offsetX = item.optDouble("x", 0.0).toFloat()
                val offsetY = item.optDouble("y", 0.0).toFloat()
                if (offsetX != 0f || offsetY != 0f) {
                    put(groupId, offsetX to offsetY)
                }
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun saveButtonOffsets(
    preferences: SharedPreferences,
    layoutKey: String,
    offsets: Map<String, Pair<Float, Float>>,
) {
    if (offsets.isEmpty()) {
        preferences.edit().remove(layoutKey).apply()
        return
    }

    val root = JSONObject()
    offsets.forEach { (groupId, offset) ->
        root.put(
            groupId,
            JSONObject()
                .put("x", offset.first.toDouble())
                .put("y", offset.second.toDouble()),
        )
    }

    preferences.edit().putString(layoutKey, root.toString()).apply()
}

private fun applyButtonOffsets(
    adjustState: ButtonAdjustState,
    offsets: Map<String, Pair<Float, Float>>,
) {
    adjustState.clearAllOffsets()
    offsets.forEach { (groupId, offset) ->
        adjustState.setOffset(groupId, offset.first, offset.second)
    }
}

@Composable
fun PS2View(
    biosFolder: String,
    ps2BaseFolder: String,
    gameFile: String,
    cheatsPath: String,
    gamepadManager: PS2GamepadManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val touchLayoutPreferences = remember(context) { getTouchLayoutPreferences(context) }
    val touchLayoutKey = remember(configuration.orientation) { getTouchLayoutKey(configuration.orientation) }
    val buttonAdjustState = remember { ButtonAdjustState() }
    
    var vmStarted by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAdjustButtons by remember { mutableStateOf(false) }
    var savedButtonOffsets by remember(touchLayoutKey) { mutableStateOf(emptyMap<String, Pair<Float, Float>>()) }
    val gamepadState by gamepadManager.state.collectAsState()

    LaunchedEffect(touchLayoutKey) {
        val savedOffsets = loadButtonOffsets(touchLayoutPreferences, touchLayoutKey)
        savedButtonOffsets = savedOffsets
        applyButtonOffsets(buttonAdjustState, savedOffsets)
        showAdjustButtons = false
    }

    SideEffect {
        buttonAdjustState.isAdjusting = showAdjustButtons
    }

    LaunchedEffect(gamepadManager) {
        gamepadManager.events.collect { event ->
            if (event is PS2GamepadEvent.OpenMenu && !showAdjustButtons) {
                showMenu = true
            }
        }
    }

    if (showMenu && !showAdjustButtons) {
        PS2Menu(
            cheatsPath = cheatsPath,
            ps2BaseFolder = ps2BaseFolder,
            hasConnectedController = gamepadState.hasConnectedController,
            connectedControllerName = gamepadState.activeController?.name.orEmpty(),
            onOpenControllerMapping = { gamepadManager.showMappingDialog() },
            onAdjustButtonPositions = if (gamepadState.hasConnectedController) {
                null
            } else {
                {
                    savedButtonOffsets = buttonAdjustState.getAllOffsets()
                    showAdjustButtons = true
                    showMenu = false
                }
            },
            onDismiss = { showMenu = false }
        )
    }

    if (gamepadState.mappingDialogVisible) {
        PS2GamepadMappingDialog(
            uiState = gamepadState,
            onDismiss = { gamepadManager.dismissMappingDialog() },
            onSave = { gamepadManager.saveCurrentMapping() },
            onRestoreSuggested = { gamepadManager.restoreSuggestedBindings() },
            onBeginCapture = { action -> gamepadManager.beginCapture(action) },
            onClearBinding = { action -> gamepadManager.clearBinding(action) },
            onDialogKeyEvent = { nativeKeyEvent -> gamepadManager.handleKeyEvent(nativeKeyEvent) }
        )
    }

    DisposableEffect(Unit) {
        val cheatsDir = File(ps2BaseFolder, "cheats")
        if (cheatsDir.exists()) {
            cheatsDir.listFiles()?.forEach {
                if (it.isFile && it.name.endsWith(".pnach")) {
                    it.delete()
                }
            }
        }

        NativeApp.initializeOnce(context)
        NativeApp.initialize(ps2BaseFolder, android.os.Build.VERSION.SDK_INT)
        
        onDispose {
            NativeApp.onNativeSurfaceDestroyed()
            NativeApp.shutdownAndWait()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Ensure the surface view is set so that the window compositor knows its ordering.
                            // The reference project handles this by putting the SurfaceView in a FrameLayout.
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            NativeApp.onNativeSurfaceChanged(holder.surface, width, height)
                            
                            // Start VM only after the surface is bound for the first time
                            if (!vmStarted) {
                                vmStarted = true
                                val thread = Thread {
                                    Thread.sleep(500) // Mimic PSX2 delay for Native app to settle
                                    NativeApp.runVMThread(gameFile)
                                }
                                NativeApp.setEmuThread(thread)
                                thread.start()
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            NativeApp.onNativeSurfaceDestroyed()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (gamepadState.hasConnectedController) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.drawer_toggle_content_description),
                        tint = Color.White,
                    )
                }
            }
        } else {
            PadKit(
                modifier = Modifier.fillMaxSize(),
                onInputEvents = { events ->
                    if (!showAdjustButtons) {
                        events.forEach { event ->
                            when (event) {
                                is InputEvent.Button -> {
                                    if (event.id == KeyEvent.KEYCODE_BUTTON_MODE && event.pressed && !showAdjustButtons) {
                                        showMenu = true
                                    } else if (event.id != KeyEvent.KEYCODE_BUTTON_MODE) {
                                        val value = if (event.pressed) 255 else 0
                                        NativeApp.setPadButton(event.id, value, event.pressed)
                                    }
                                }
                                is InputEvent.DiscreteDirection, is InputEvent.ContinuousDirection -> {
                                    val id = if (event is InputEvent.DiscreteDirection) event.id else (event as InputEvent.ContinuousDirection).id
                                    val direction = if (event is InputEvent.DiscreteDirection) event.direction else (event as InputEvent.ContinuousDirection).direction

                                    val xAxis = if (direction.x.isNaN()) 0f else direction.x
                                    val yAxis = if (direction.y.isNaN()) 0f else -direction.y

                                    if (id == 0) {
                                        val dpadUp = yAxis < -0.1f
                                        val dpadDown = yAxis > 0.1f
                                        val dpadLeft = xAxis < -0.1f
                                        val dpadRight = xAxis > 0.1f

                                        NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_UP, if (dpadUp) 255 else 0, dpadUp)
                                        NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_DOWN, if (dpadDown) 255 else 0, dpadDown)
                                        NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_LEFT, if (dpadLeft) 255 else 0, dpadLeft)
                                        NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_RIGHT, if (dpadRight) 255 else 0, dpadRight)
                                    } else if (id == 1) {
                                        if (xAxis < 0f) {
                                            val v = (abs(xAxis) * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(113, v, v > 25)
                                            NativeApp.setPadButton(111, 0, false)
                                        } else {
                                            val v = (xAxis * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(111, v, v > 25)
                                            NativeApp.setPadButton(113, 0, false)
                                        }

                                        if (yAxis < 0f) {
                                            val v = (abs(yAxis) * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(110, v, v > 25)
                                            NativeApp.setPadButton(112, 0, false)
                                        } else {
                                            val v = (yAxis * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(112, v, v > 25)
                                            NativeApp.setPadButton(110, 0, false)
                                        }
                                    } else if (id == 2) {
                                        if (xAxis < 0f) {
                                            val v = (abs(xAxis) * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(123, v, v > 25)
                                            NativeApp.setPadButton(121, 0, false)
                                        } else {
                                            val v = (xAxis * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(121, v, v > 25)
                                            NativeApp.setPadButton(123, 0, false)
                                        }

                                        if (yAxis < 0f) {
                                            val v = (abs(yAxis) * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(120, v, v > 25)
                                            NativeApp.setPadButton(122, 0, false)
                                        } else {
                                            val v = (yAxis * 255f).roundToInt().coerceIn(0, 255)
                                            NativeApp.setPadButton(122, v, v > 25)
                                            NativeApp.setPadButton(120, 0, false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                CompositionLocalProvider(
                    LocalLemuroidPadTheme provides LemuroidPadTheme(),
                    LocalButtonAdjustState provides buttonAdjustState,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val mockSettings = TouchControllerSettingsManager.Settings()
                        PS2Left(settings = mockSettings)
                        PS2Right(settings = mockSettings)
                    }
                }
            }

            if (showAdjustButtons) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.ps2_adjust_button_positions_hint),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        TextButton(
                            onClick = {
                                applyButtonOffsets(buttonAdjustState, savedButtonOffsets)
                                showAdjustButtons = false
                            },
                        ) {
                            Text(stringResource(R.string.ps2_cancel))
                        }
                        OutlinedButton(
                            onClick = {
                                savedButtonOffsets = emptyMap()
                                applyButtonOffsets(buttonAdjustState, emptyMap())
                                saveButtonOffsets(touchLayoutPreferences, touchLayoutKey, emptyMap())
                                showAdjustButtons = false
                            },
                        ) {
                            Text(stringResource(R.string.ps2_reset))
                        }
                        Button(
                            onClick = {
                                val currentOffsets = buttonAdjustState.getAllOffsets()
                                saveButtonOffsets(touchLayoutPreferences, touchLayoutKey, currentOffsets)
                                savedButtonOffsets = currentOffsets
                                showAdjustButtons = false
                            },
                        ) {
                            Text(stringResource(R.string.ps2_save))
                        }
                    }
                }
            }
        }
    }
}
