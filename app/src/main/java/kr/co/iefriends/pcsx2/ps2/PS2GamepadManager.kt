package kr.co.iefriends.pcsx2.ps2

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import kr.co.iefriends.pcsx2.NativeApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

private const val GAMEPAD_PREFS_NAME = "ps2_gamepad_mappings"
private const val GAMEPAD_MAPPING_PREFIX = "device_mapping_"
private const val ANALOG_DEADZONE = 0.18f
private const val TRIGGER_DEADZONE = 0.08f
private const val HAT_THRESHOLD = 0.45f

enum class PS2GamepadAction(val title: String, val englishTitle: String, val nativeCode: Int?) {
    DPAD_UP("方向上", "DPad Up", KeyEvent.KEYCODE_DPAD_UP),
    DPAD_DOWN("方向下", "DPad Down", KeyEvent.KEYCODE_DPAD_DOWN),
    DPAD_LEFT("方向左", "DPad Left", KeyEvent.KEYCODE_DPAD_LEFT),
    DPAD_RIGHT("方向右", "DPad Right", KeyEvent.KEYCODE_DPAD_RIGHT),
    CROSS("叉", "Cross", KeyEvent.KEYCODE_BUTTON_A),
    CIRCLE("圈", "Circle", KeyEvent.KEYCODE_BUTTON_B),
    SQUARE("方块", "Square", KeyEvent.KEYCODE_BUTTON_X),
    TRIANGLE("三角", "Triangle", KeyEvent.KEYCODE_BUTTON_Y),
    SELECT("选择", "Select", KeyEvent.KEYCODE_BUTTON_SELECT),
    START("开始", "Start", KeyEvent.KEYCODE_BUTTON_START),
    L1("左肩键1", "L1", KeyEvent.KEYCODE_BUTTON_L1),
    R1("右肩键1", "R1", KeyEvent.KEYCODE_BUTTON_R1),
    L2("左扳机", "L2", KeyEvent.KEYCODE_BUTTON_L2),
    R2("右扳机", "R2", KeyEvent.KEYCODE_BUTTON_R2),
    L3("左摇杆按下", "L3", KeyEvent.KEYCODE_BUTTON_THUMBL),
    R3("右摇杆按下", "R3", KeyEvent.KEYCODE_BUTTON_THUMBR),
    MENU("菜单", "Menu", null);

    companion object {
        val remappableActions: List<PS2GamepadAction> = values().toList()
    }
}

data class PS2GamepadDevice(
    val id: Int,
    val deviceKey: String,
    val name: String,
)

data class PS2GamepadUiState(
    val connectedControllers: List<PS2GamepadDevice> = emptyList(),
    val activeControllerId: Int? = null,
    val mappingDialogVisible: Boolean = false,
    val mappingDialogAutoPrompt: Boolean = false,
    val editingDevice: PS2GamepadDevice? = null,
    val editingBindings: Map<PS2GamepadAction, Int> = emptyMap(),
    val capturingAction: PS2GamepadAction? = null,
) {
    val hasConnectedController: Boolean
        get() = connectedControllers.isNotEmpty()

    val activeController: PS2GamepadDevice?
        get() = connectedControllers.firstOrNull { it.id == activeControllerId } ?: connectedControllers.firstOrNull()
}

sealed interface PS2GamepadEvent {
    data object OpenMenu : PS2GamepadEvent
}

class PS2GamepadManager(context: Context) : InputManager.InputDeviceListener {
    private val appContext = context.applicationContext
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val preferences = appContext.getSharedPreferences(GAMEPAD_PREFS_NAME, Context.MODE_PRIVATE)
    private val notifiedDeviceKeys = mutableSetOf<String>()
    private val analogStates = mutableMapOf<Int, Int>()
    private val digitalStates = mutableMapOf<Int, Boolean>()
    private val defaultBindings = linkedMapOf(
        PS2GamepadAction.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
        PS2GamepadAction.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        PS2GamepadAction.DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        PS2GamepadAction.DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        PS2GamepadAction.CROSS to KeyEvent.KEYCODE_BUTTON_A,
        PS2GamepadAction.CIRCLE to KeyEvent.KEYCODE_BUTTON_B,
        PS2GamepadAction.SQUARE to KeyEvent.KEYCODE_BUTTON_X,
        PS2GamepadAction.TRIANGLE to KeyEvent.KEYCODE_BUTTON_Y,
        PS2GamepadAction.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        PS2GamepadAction.START to KeyEvent.KEYCODE_BUTTON_START,
        PS2GamepadAction.L1 to KeyEvent.KEYCODE_BUTTON_L1,
        PS2GamepadAction.R1 to KeyEvent.KEYCODE_BUTTON_R1,
        PS2GamepadAction.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        PS2GamepadAction.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        PS2GamepadAction.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        PS2GamepadAction.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
        PS2GamepadAction.MENU to KeyEvent.KEYCODE_BUTTON_MODE,
    )
    private val _state = MutableStateFlow(PS2GamepadUiState())
    private val _events = MutableSharedFlow<PS2GamepadEvent>(extraBufferCapacity = 8)
    private var started = false

    val state = _state.asStateFlow()
    val events = _events.asSharedFlow()

    fun start() {
        if (started) {
            return
        }
        started = true
        inputManager.registerInputDeviceListener(this, null)
        refreshConnectedControllers()
        _state.value.activeController?.let { maybeShowConnectionHint(it) }
    }

    fun stop() {
        if (!started) {
            return
        }
        started = false
        inputManager.unregisterInputDeviceListener(this)
        releaseAllInputs()
    }

    fun releaseAllInputs() {
        analogStates.clear()
        digitalStates.clear()
        NativeApp.resetKeyStatus()
    }

    fun showMappingDialog(autoPrompt: Boolean = false) {
        val device = _state.value.activeController ?: _state.value.connectedControllers.firstOrNull() ?: return
        _state.value = _state.value.copy(
            mappingDialogVisible = true,
            mappingDialogAutoPrompt = autoPrompt,
            editingDevice = device,
            editingBindings = loadRuntimeBindings(device.deviceKey),
            capturingAction = null,
        )
        releaseAllInputs()
    }

    fun dismissMappingDialog() {
        _state.value = _state.value.copy(
            mappingDialogVisible = false,
            mappingDialogAutoPrompt = false,
            editingDevice = null,
            editingBindings = emptyMap(),
            capturingAction = null,
        )
        releaseAllInputs()
    }

    fun beginCapture(action: PS2GamepadAction) {
        if (!_state.value.mappingDialogVisible) {
            return
        }
        _state.value = _state.value.copy(capturingAction = action)
        releaseAllInputs()
    }

    fun clearBinding(action: PS2GamepadAction) {
        val updatedBindings = _state.value.editingBindings.toMutableMap()
        updatedBindings.remove(action)
        _state.value = _state.value.copy(editingBindings = updatedBindings)
    }

    fun restoreSuggestedBindings() {
        _state.value = _state.value.copy(
            editingBindings = defaultBindings.toMap(),
            capturingAction = null,
        )
    }

    fun saveCurrentMapping() {
        val editingDevice = _state.value.editingDevice ?: return
        val json = JSONObject()
        _state.value.editingBindings.forEach { (action, keyCode) ->
            json.put(action.name, keyCode)
        }
        preferences.edit()
            .putString(mappingPreferenceKey(editingDevice.deviceKey), json.toString())
            .apply()
        Toast.makeText(appContext, "已保存 ${editingDevice.name} 的手柄映射", Toast.LENGTH_SHORT).show()
        dismissMappingDialog()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val currentState = _state.value
        val editingDevice = currentState.editingDevice
        if (currentState.mappingDialogVisible && editingDevice != null) {
            val eventDevice = event.device ?: return false
            if (eventDevice.id != editingDevice.id) {
                return false
            }

            markActiveController(eventDevice.id)
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val capturingAction = currentState.capturingAction
                val keyCode = event.keyCode
                if (capturingAction != null && isBindableKeyCode(keyCode)) {
                    assignBinding(capturingAction, keyCode)
                }
            }
            return true
        }

        val combinedSources = event.source or (event.device?.sources ?: 0)
        if (!isGameControllerEvent(event.device, combinedSources)) {
            return false
        }
        val device = event.device?.toGamepadDevice() ?: return false
        markActiveController(device.id)

        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }

        val action = loadRuntimeBindings(device.deviceKey).entries.firstOrNull { it.value == event.keyCode }?.key
            ?: return false

        if (action == PS2GamepadAction.MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                _events.tryEmit(PS2GamepadEvent.OpenMenu)
            }
            return true
        }

        val nativeCode = action.nativeCode ?: return true
        NativeApp.setPadButton(nativeCode, 0, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        val combinedSources = event.source or (event.device?.sources ?: 0)
        if (!isGameControllerEvent(event.device, combinedSources)) {
            return false
        }
        val device = event.device?.toGamepadDevice() ?: return false
        markActiveController(device.id)

        if (_state.value.mappingDialogVisible) {
            return true
        }

        handleLeftStick(event)
        handleRightStick(event)
        handleTriggers(event)
        handleHat(event)
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        if (!isGameControllerEvent(device, device.sources)) {
            return
        }
        refreshConnectedControllers(preferredControllerId = deviceId)
        val gamepadDevice = device.toGamepadDevice()
        maybeShowConnectionHint(gamepadDevice)
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val removedDevice = _state.value.connectedControllers.firstOrNull { it.id == deviceId }
        val removedEditingDevice = _state.value.editingDevice?.id == deviceId
        refreshConnectedControllers()
        releaseAllInputs()
        if (removedDevice != null) {
            notifiedDeviceKeys.remove(removedDevice.deviceKey)
            Toast.makeText(appContext, "手柄已断开: ${removedDevice.name}", Toast.LENGTH_SHORT).show()
        }
        if (removedEditingDevice) {
            dismissMappingDialog()
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)
        if (device == null || !isGameControllerEvent(device, device.sources)) {
            onInputDeviceRemoved(deviceId)
            return
        }
        refreshConnectedControllers(preferredControllerId = deviceId)
    }

    private fun refreshConnectedControllers(preferredControllerId: Int? = _state.value.activeControllerId) {
        val controllers = mutableListOf<PS2GamepadDevice>()
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            if (!isGameControllerEvent(device, device.sources)) {
                continue
            }
            controllers.add(device.toGamepadDevice())
        }
        controllers.sortBy { controller -> controller.name.lowercase() }

        val currentState = _state.value
        val nextActiveId = when {
            preferredControllerId != null && controllers.any { controller -> controller.id == preferredControllerId } -> preferredControllerId
            currentState.activeControllerId != null && controllers.any { controller -> controller.id == currentState.activeControllerId } -> currentState.activeControllerId
            else -> controllers.firstOrNull()?.id
        }
        val nextEditingDevice = currentState.editingDevice?.let { editing ->
            controllers.firstOrNull { controller -> controller.id == editing.id }
        }

        _state.value = currentState.copy(
            connectedControllers = controllers,
            activeControllerId = nextActiveId,
            mappingDialogVisible = if (currentState.mappingDialogVisible && nextEditingDevice == null) false else currentState.mappingDialogVisible,
            mappingDialogAutoPrompt = if (nextEditingDevice == null) false else currentState.mappingDialogAutoPrompt,
            editingDevice = nextEditingDevice,
            editingBindings = if (nextEditingDevice == null) emptyMap() else currentState.editingBindings,
            capturingAction = if (nextEditingDevice == null) null else currentState.capturingAction,
        )
    }

    private fun maybeShowConnectionHint(device: PS2GamepadDevice) {
        if (!notifiedDeviceKeys.add(device.deviceKey)) {
            return
        }
        _state.value = _state.value.copy(activeControllerId = device.id)
        Toast.makeText(appContext, "已连接手柄: ${device.name}，可在菜单中修改映射", Toast.LENGTH_SHORT).show()
    }

    private fun markActiveController(deviceId: Int) {
        val currentState = _state.value
        if (currentState.activeControllerId == deviceId) {
            return
        }
        if (currentState.connectedControllers.none { it.id == deviceId }) {
            return
        }
        _state.value = currentState.copy(activeControllerId = deviceId)
    }

    private fun assignBinding(action: PS2GamepadAction, keyCode: Int) {
        val updatedBindings = _state.value.editingBindings.toMutableMap()
        val duplicateAction = updatedBindings.entries.firstOrNull { it.value == keyCode }?.key
        if (duplicateAction != null) {
            updatedBindings.remove(duplicateAction)
        }
        updatedBindings[action] = keyCode
        _state.value = _state.value.copy(
            editingBindings = updatedBindings,
            capturingAction = null,
        )
    }

    private fun handleLeftStick(event: MotionEvent) {
        val xAxis = getCenteredAxis(event, MotionEvent.AXIS_X)
        val yAxis = getCenteredAxis(event, MotionEvent.AXIS_Y)
        sendAnalog(111, xAxis)
        sendAnalog(113, -xAxis)
        sendAnalog(112, yAxis)
        sendAnalog(110, -yAxis)
    }

    private fun handleRightStick(event: MotionEvent) {
        var xAxis = getCenteredAxis(event, MotionEvent.AXIS_RX)
        var yAxis = getCenteredAxis(event, MotionEvent.AXIS_RY)
        if (xAxis == 0f && yAxis == 0f) {
            xAxis = getCenteredAxis(event, MotionEvent.AXIS_Z)
            yAxis = getCenteredAxis(event, MotionEvent.AXIS_RZ)
        }
        sendAnalog(121, xAxis)
        sendAnalog(123, -xAxis)
        sendAnalog(122, yAxis)
        sendAnalog(120, -yAxis)
    }

    private fun handleTriggers(event: MotionEvent) {
        var leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        var rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        if (leftTrigger == 0f) {
            leftTrigger = event.getAxisValue(MotionEvent.AXIS_BRAKE)
        }
        if (rightTrigger == 0f) {
            rightTrigger = event.getAxisValue(MotionEvent.AXIS_GAS)
        }
        sendAnalog(KeyEvent.KEYCODE_BUTTON_L2, normalizeTrigger(leftTrigger), TRIGGER_DEADZONE)
        sendAnalog(KeyEvent.KEYCODE_BUTTON_R2, normalizeTrigger(rightTrigger), TRIGGER_DEADZONE)
    }

    private fun handleHat(event: MotionEvent) {
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        setDigitalState(KeyEvent.KEYCODE_DPAD_LEFT, hatX < -HAT_THRESHOLD)
        setDigitalState(KeyEvent.KEYCODE_DPAD_RIGHT, hatX > HAT_THRESHOLD)
        setDigitalState(KeyEvent.KEYCODE_DPAD_UP, hatY < -HAT_THRESHOLD)
        setDigitalState(KeyEvent.KEYCODE_DPAD_DOWN, hatY > HAT_THRESHOLD)
    }

    private fun setDigitalState(nativeCode: Int, pressed: Boolean) {
        val previous = digitalStates[nativeCode]
        if (previous == pressed) {
            return
        }
        digitalStates[nativeCode] = pressed
        NativeApp.setPadButton(nativeCode, 0, pressed)
    }

    private fun sendAnalog(nativeCode: Int, signedValue: Float, deadzone: Float = ANALOG_DEADZONE) {
        val normalized = signedValue.coerceIn(0f, 1f)
        val filtered = if (normalized < deadzone || normalized.isNaN()) 0f else normalized
        val scaledValue = (filtered * 255f).roundToInt().coerceIn(0, 255)
        val previousValue = analogStates[nativeCode]
        if (previousValue == scaledValue) {
            return
        }
        analogStates[nativeCode] = scaledValue
        NativeApp.setPadButton(nativeCode, scaledValue, scaledValue > 0)
    }

    private fun getCenteredAxis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val motionRange = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > motionRange.flat) value else 0f
    }

    private fun normalizeTrigger(rawValue: Float): Float {
        if (rawValue.isNaN()) {
            return 0f
        }
        if (rawValue < 0f) {
            return ((rawValue + 1f) * 0.5f).coerceIn(0f, 1f)
        }
        return rawValue.coerceIn(0f, 1f)
    }

    private fun hasStoredMapping(deviceKey: String): Boolean {
        return preferences.contains(mappingPreferenceKey(deviceKey))
    }

    private fun loadRuntimeBindings(deviceKey: String): Map<PS2GamepadAction, Int> {
        return loadStoredBindings(deviceKey) ?: defaultBindings.toMap()
    }

    private fun loadStoredBindings(deviceKey: String): Map<PS2GamepadAction, Int>? {
        val rawJson = preferences.getString(mappingPreferenceKey(deviceKey), null) ?: return null
        return try {
            val json = JSONObject(rawJson)
            val loadedBindings = mutableMapOf<PS2GamepadAction, Int>()
            PS2GamepadAction.values().forEach { action ->
                if (json.has(action.name)) {
                    loadedBindings[action] = json.getInt(action.name)
                }
            }
            loadedBindings.toMap()
        } catch (_: Exception) {
            null
        }
    }

    private fun mappingPreferenceKey(deviceKey: String): String {
        return GAMEPAD_MAPPING_PREFIX + deviceKey
    }

    private fun isBindableKeyCode(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER -> false
            else -> true
        }
    }

    private fun isGameControllerEvent(device: InputDevice?, source: Int): Boolean {
        if (device == null || device.isVirtual) {
            return false
        }

        val hasGamepadSource =
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!hasGamepadSource) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !device.isExternal) {
            return false
        }

        val hasFaceButtons = device.hasKeys(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
        ).any { pressed -> pressed }
        val hasAnalogAxis =
            device.getMotionRange(MotionEvent.AXIS_X, source) != null ||
                device.getMotionRange(MotionEvent.AXIS_Y, source) != null ||
                device.getMotionRange(MotionEvent.AXIS_Z, source) != null ||
                device.getMotionRange(MotionEvent.AXIS_RX, source) != null

        return hasFaceButtons || hasAnalogAxis
    }

    private fun InputDevice.toGamepadDevice(): PS2GamepadDevice {
        val stableKey = descriptor.takeIf { it.isNotBlank() } ?: name
        return PS2GamepadDevice(
            id = id,
            deviceKey = stableKey,
            name = name,
        )
    }

    companion object {
        const val AXIS_SUMMARY = "左摇杆使用 X/Y，右摇杆优先使用 RX/RY，若手柄不支持则回退到 Z/RZ。L2/R2 自动读取扳机轴。"

        fun keyCodeLabel(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> "A"
                KeyEvent.KEYCODE_BUTTON_B -> "B"
                KeyEvent.KEYCODE_BUTTON_X -> "X"
                KeyEvent.KEYCODE_BUTTON_Y -> "Y"
                KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
                KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
                KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
                KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
                KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
                KeyEvent.KEYCODE_BUTTON_START -> "Start"
                KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
                KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
                KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
                KeyEvent.KEYCODE_DPAD_UP -> "DPad Up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "DPad Down"
                KeyEvent.KEYCODE_DPAD_LEFT -> "DPad Left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "DPad Right"
                else -> KeyEvent.keyCodeToString(keyCode)
                    .removePrefix("KEYCODE_")
                    .replace('_', ' ')
            }
        }
    }
}