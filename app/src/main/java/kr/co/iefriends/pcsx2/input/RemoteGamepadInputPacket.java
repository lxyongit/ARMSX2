package kr.co.iefriends.pcsx2.input;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 远程手柄输入数据包。
 *
 * 标准 JSON 结构：
 * {
 *   "version": 1,
 *   "deviceKey": "remote-pad-1",
 *   "sourceId": "peer-a",
 *   "controllerIndex": -1,
 *   "events": [
 *     {
 *       "type": "button",
 *       "code": 96,
 *       "pressed": true,
 *       "value": 255
 *     },
 *     {
 *       "type": "stick",
 *       "stick": "left",
 *       "x": 0.45,
 *       "y": -0.20
 *     },
 *     {
 *       "type": "trigger",
 *       "trigger": "r2",
 *       "value": 0.80
 *     },
 *     {
 *       "type": "hat",
 *       "x": -1,
 *       "y": 0
 *     }
 *   ]
 * }
 *
 * 字段说明：
 * - version：协议版本，当前固定为 1。
 * - deviceKey：单个远程控制器的稳定标识。
 * - sourceId：远端节点或会话标识。
 * - controllerIndex：目标本地手柄槽位，传 -1 表示自动路由。
 * - events：按顺序应用的一组输入事件。
 */
public final class RemoteGamepadInputPacket {
    public static final int CONTROLLER_INDEX_AUTO = -1;

    /** 协议版本。 */
    public final int version;

    /** 远程控制器设备的稳定标识。 */
    @Nullable public final String deviceKey;

    /** 远端节点或上游会话标识。 */
    @Nullable public final String sourceId;

    /** 本地目标手柄槽位，或使用 {@link #CONTROLLER_INDEX_AUTO} 表示自动路由。 */
    public final int controllerIndex;

    /** 按顺序应用的远程输入事件列表。 */
    @NonNull public final List<RemoteInputEvent> events;

    private RemoteGamepadInputPacket(
            int version,
            @Nullable String deviceKey,
            @Nullable String sourceId,
            int controllerIndex,
            @NonNull List<RemoteInputEvent> events
    ) {
        this.version = version;
        this.deviceKey = deviceKey;
        this.sourceId = sourceId;
        this.controllerIndex = controllerIndex;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    @NonNull
    public static RemoteGamepadInputPacket fromJson(@NonNull String rawJson) throws JSONException {
        return fromJson(new JSONObject(rawJson));
    }

    @NonNull
    public static RemoteGamepadInputPacket fromJson(@NonNull JSONObject json) throws JSONException {
        final int version = json.optInt("version", 1);
        final String deviceKey = optNullableString(json, "deviceKey");
        final String sourceId = optNullableString(json, "sourceId");
        final int controllerIndex = parseControllerIndex(json);
        final List<RemoteInputEvent> events = parseEvents(json);

        return new RemoteGamepadInputPacket(version, deviceKey, sourceId, controllerIndex, events);
    }

    public boolean hasEvents() {
        return !events.isEmpty();
    }

    @Nullable
    public String getRoutingKey() {
        return firstNonEmpty(deviceKey, sourceId);
    }

    private static int parseControllerIndex(@NonNull JSONObject json) {
        return hasNonNull(json, "controllerIndex")
                ? json.optInt("controllerIndex", CONTROLLER_INDEX_AUTO)
                : CONTROLLER_INDEX_AUTO;
    }

    @NonNull
    private static List<RemoteInputEvent> parseEvents(@NonNull JSONObject json) throws JSONException {
        final ArrayList<RemoteInputEvent> events = new ArrayList<>();
        final JSONArray array = json.optJSONArray("events");
        if (array == null) {
            return events;
        }

        for (int i = 0; i < array.length(); i++) {
            final JSONObject eventJson = array.optJSONObject(i);
            if (eventJson != null) {
                events.add(RemoteInputEvent.fromJson(eventJson));
            }
        }
        return events;
    }

    private static boolean hasNonNull(@NonNull JSONObject json, @NonNull String key) {
        return json.has(key) && !json.isNull(key);
    }

    @Nullable
    private static String optNullableString(@NonNull JSONObject json, @NonNull String key) {
        if (!hasNonNull(json, key)) {
            return null;
        }
        final String value = json.optString(key, null);
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static int clampToByteRange(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clampUnit(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static float clampTrigger(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int clampHat(float value) {
        if (value > 0.5f) {
            return 1;
        }
        if (value < -0.5f) {
            return -1;
        }
        return 0;
    }

    public enum EventType {
        /** 数字按键输入。 */
        BUTTON,

        /** 模拟摇杆输入。 */
        STICK,

        /** 模拟扳机输入。 */
        TRIGGER,

        /** 方向键帽输入。 */
        HAT,

        /** 重置当前解析到的控制器全部输入状态。 */
        RESET;

        @NonNull
        static EventType fromJson(@Nullable String rawType) throws JSONException {
            if (rawType == null) {
                throw new JSONException("Remote input event missing type");
            }
            final String normalized = rawType.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "button":
                    return BUTTON;
                case "stick":
                    return STICK;
                case "trigger":
                    return TRIGGER;
                case "hat":
                    return HAT;
                case "reset":
                    return RESET;
                default:
                    throw new JSONException("Unknown remote input event type: " + rawType);
            }
        }
    }

    public enum StickTarget {
        LEFT,
        RIGHT;

        @NonNull
        static StickTarget fromJson(@Nullable String rawTarget) throws JSONException {
            if (rawTarget == null) {
                throw new JSONException("Stick event missing target");
            }
            final String normalized = rawTarget.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "left":
                    return LEFT;
                case "right":
                    return RIGHT;
                default:
                    throw new JSONException("Unknown stick target: " + rawTarget);
            }
        }
    }

    public enum TriggerTarget {
        L2,
        R2;

        @NonNull
        static TriggerTarget fromJson(@Nullable String rawTarget) throws JSONException {
            if (rawTarget == null) {
                throw new JSONException("Trigger event missing target");
            }
            final String normalized = rawTarget.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "l2":
                    return L2;
                case "r2":
                    return R2;
                default:
                    throw new JSONException("Unknown trigger target: " + rawTarget);
            }
        }
    }

    public abstract static class RemoteInputEvent {
        /** JSON 里的事件类型标识。 */
        @NonNull public final EventType type;

        RemoteInputEvent(@NonNull EventType type) {
            this.type = type;
        }

        @NonNull
        public static RemoteInputEvent fromJson(@NonNull JSONObject json) throws JSONException {
            final EventType type = EventType.fromJson(json.optString("type", null));
            switch (type) {
                case BUTTON:
                    return ButtonEvent.fromJson(json);
                case STICK:
                    return StickEvent.fromJson(json);
                case TRIGGER:
                    return TriggerEvent.fromJson(json);
                case HAT:
                    return HatEvent.fromJson(json);
                case RESET:
                default:
                    return new ResetEvent();
            }
        }
    }

    public static final class ButtonEvent extends RemoteInputEvent {
        /** 传给模拟器映射层的 Android 或 native 按键码。 */
        public final int code;

        /** 当前按键是否处于按下状态。 */
        public final boolean pressed;

        /** 模拟压感值，范围 0..255。 */
        public final int value;

        ButtonEvent(int code, boolean pressed, int value) {
            super(EventType.BUTTON);
            this.code = code;
            this.pressed = pressed;
            this.value = value;
        }

        @NonNull
        public static ButtonEvent fromJson(@NonNull JSONObject json) throws JSONException {
            Integer code = null;
            if (hasNonNull(json, "code")) {
                code = json.optInt("code");
            }
            if (code == null) {
                throw new JSONException("Button event missing code");
            }

            final boolean hasValue = hasNonNull(json, "value");
            int value = 0;
            if (hasNonNull(json, "value")) {
                value = clampToByteRange(json.optInt("value", 0));
            }

            final boolean pressed = hasNonNull(json, "pressed") ? json.optBoolean("pressed") : value > 0;
            if (pressed && value == 0 && !hasValue) {
                value = 255;
            }
            if (!pressed) {
                value = 0;
            }

            return new ButtonEvent(code, pressed, value);
        }
    }

    public static final class StickEvent extends RemoteInputEvent {
        /** 当前事件作用到哪一个摇杆。 */
        @NonNull public final StickTarget stick;

        /** 水平轴值，范围 -1..1。 */
        public final float x;

        /** 垂直轴值，范围 -1..1。 */
        public final float y;

        StickEvent(@NonNull StickTarget stick, float x, float y) {
            super(EventType.STICK);
            this.stick = stick;
            this.x = x;
            this.y = y;
        }

        @NonNull
        public static StickEvent fromJson(@NonNull JSONObject json) throws JSONException {
            final String rawTarget = optNullableString(json, "stick");
            final StickTarget stick = StickTarget.fromJson(rawTarget);
            final float x = clampUnit((float) json.optDouble("x", 0.0));
            final float y = clampUnit((float) json.optDouble("y", 0.0));
            return new StickEvent(stick, x, y);
        }
    }

    public static final class TriggerEvent extends RemoteInputEvent {
        /** 当前事件作用到哪一个扳机。 */
        @NonNull public final TriggerTarget trigger;

        /** 扳机压感值，范围 0..1。 */
        public final float value;

        TriggerEvent(@NonNull TriggerTarget trigger, float value) {
            super(EventType.TRIGGER);
            this.trigger = trigger;
            this.value = value;
        }

        @NonNull
        public static TriggerEvent fromJson(@NonNull JSONObject json) throws JSONException {
            final String rawTarget = optNullableString(json, "trigger");
            final TriggerTarget trigger = TriggerTarget.fromJson(rawTarget);
            final float value = clampTrigger((float) json.optDouble("value", 0.0));
            return new TriggerEvent(trigger, value);
        }
    }

    public static final class HatEvent extends RemoteInputEvent {
        /** 水平方向键值，只允许 -1、0、1。 */
        public final int x;

        /** 垂直方向键值，只允许 -1、0、1。 */
        public final int y;

        HatEvent(int x, int y) {
            super(EventType.HAT);
            this.x = x;
            this.y = y;
        }

        @NonNull
        public static HatEvent fromJson(@NonNull JSONObject json) {
            final int x = clampHat((float) json.optDouble("x", 0.0));
            final int y = clampHat((float) json.optDouble("y", 0.0));
            return new HatEvent(x, y);
        }
    }

    public static final class ResetEvent extends RemoteInputEvent {
        ResetEvent() {
            super(EventType.RESET);
        }
    }
}