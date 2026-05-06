package kr.co.iefriends.pcsx2.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kr.co.iefriends.pcsx2.NativeApp;

public final class InputDeviceRoutingManager {
    public static final int AUTO_ASSIGNMENT = -1;

    private static final String PREFS_NAME = "controller_routing_prefs";
    private static final String KEY_PREFIX = "pad_route_";
    private static final int DEFAULT_PAD_COUNT = 8;

    private static SharedPreferences sPrefs;

    private InputDeviceRoutingManager() {
    }

    public static synchronized void init(@NonNull Context context) {
        if (sPrefs == null) {
            sPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static synchronized void assignDeviceToPad(@NonNull String deviceKey, int padIndex) {
        if (sPrefs == null) {
            return;
        }

        if (padIndex == AUTO_ASSIGNMENT) {
            clearDeviceAssignment(deviceKey);
            return;
        }

        final int clampedPadIndex = clampPadIndex(padIndex);
        final SharedPreferences.Editor editor = sPrefs.edit();
        for (Map.Entry<String, ?> entry : sPrefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX) || entry.getKey().equals(prefKey(deviceKey))) {
                continue;
            }
            final Object value = entry.getValue();
            if (value instanceof Integer && ((Integer) value) == clampedPadIndex) {
                editor.remove(entry.getKey());
            }
        }
        editor.putInt(prefKey(deviceKey), clampedPadIndex).apply();
    }

    public static synchronized void clearDeviceAssignment(@NonNull String deviceKey) {
        if (sPrefs == null) {
            return;
        }
        sPrefs.edit().remove(prefKey(deviceKey)).apply();
    }

    public static synchronized int getStoredPadIndex(@NonNull String deviceKey) {
        return getStoredPadIndexInternal(deviceKey);
    }

    public static synchronized int getAssignedPadIndex(@Nullable InputDevice device) {
        if (device == null) {
            return 0;
        }
        return getAssignedPadIndex(getDeviceKey(device));
    }

    public static synchronized int getAssignedPadIndex(@NonNull String deviceKey) {
        final List<String> connectedDeviceKeys = getConnectedGamepadDeviceKeys();
        if (!connectedDeviceKeys.contains(deviceKey)) {
            connectedDeviceKeys.add(deviceKey);
        }
        return computeAssignedPadIndex(deviceKey, connectedDeviceKeys);
    }

    @NonNull
    public static synchronized List<ConnectedDeviceRoute> getConnectedDeviceRoutes() {
        final List<InputDevice> devices = getConnectedGameControllerDevices();
        final List<String> connectedDeviceKeys = new ArrayList<>();
        for (InputDevice device : devices) {
            connectedDeviceKeys.add(getDeviceKey(device));
        }

        final List<ConnectedDeviceRoute> routes = new ArrayList<>();
        for (InputDevice device : devices) {
            final String deviceKey = getDeviceKey(device);
            routes.add(new ConnectedDeviceRoute(
                    device.getId(),
                    deviceKey,
                    device.getName(),
                    computeAssignedPadIndex(deviceKey, connectedDeviceKeys),
                    getStoredPadIndexInternal(deviceKey) != AUTO_ASSIGNMENT));
        }
        return routes;
    }

    @NonNull
    public static String getDeviceKey(@NonNull InputDevice device) {
        final String descriptor = device.getDescriptor();
        if (descriptor != null && !descriptor.isEmpty()) {
            return descriptor;
        }
        return device.getName();
    }

    public static boolean isGameControllerDevice(@Nullable InputDevice device) {
        if (device == null || device.isVirtual()) {
            return false;
        }

        final int sources = device.getSources();
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) ||
                ((sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD);
    }

    private static int getStoredPadIndexInternal(@NonNull String deviceKey) {
        if (sPrefs == null || !sPrefs.contains(prefKey(deviceKey))) {
            return AUTO_ASSIGNMENT;
        }

        final int storedPadIndex = sPrefs.getInt(prefKey(deviceKey), AUTO_ASSIGNMENT);
        if (storedPadIndex < 0 || storedPadIndex >= getPadCount()) {
            sPrefs.edit().remove(prefKey(deviceKey)).apply();
            return AUTO_ASSIGNMENT;
        }
        return storedPadIndex;
    }

    private static int computeAssignedPadIndex(@NonNull String deviceKey, @NonNull List<String> connectedDeviceKeys) {
        final int explicitPadIndex = getStoredPadIndexInternal(deviceKey);
        if (explicitPadIndex != AUTO_ASSIGNMENT) {
            return explicitPadIndex;
        }

        final int padCount = getPadCount();
        final Set<Integer> reservedPadIndices = new HashSet<>();
        final List<String> autoAssignedDeviceKeys = new ArrayList<>();

        for (String connectedDeviceKey : connectedDeviceKeys) {
            final int assignedPadIndex = getStoredPadIndexInternal(connectedDeviceKey);
            if (assignedPadIndex != AUTO_ASSIGNMENT) {
                reservedPadIndices.add(assignedPadIndex);
            } else {
                autoAssignedDeviceKeys.add(connectedDeviceKey);
            }
        }

        final List<Integer> freePadIndices = new ArrayList<>();
        for (int padIndex = 0; padIndex < padCount; padIndex++) {
            if (!reservedPadIndices.contains(padIndex)) {
                freePadIndices.add(padIndex);
            }
        }

        final int devicePosition = autoAssignedDeviceKeys.indexOf(deviceKey);
        if (devicePosition >= 0 && devicePosition < freePadIndices.size()) {
            return freePadIndices.get(devicePosition);
        }
        return 0;
    }

    private static int clampPadIndex(int padIndex) {
        final int maxPadIndex = Math.max(0, getPadCount() - 1);
        return Math.min(Math.max(0, padIndex), maxPadIndex);
    }

    public static int getPadCount() {
        try {
            final int nativePadCount = NativeApp.getPadPortCount();
            if (nativePadCount > 0) {
                return nativePadCount;
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_PAD_COUNT;
    }

    @NonNull
    private static List<String> getConnectedGamepadDeviceKeys() {
        final List<String> deviceKeys = new ArrayList<>();
        for (InputDevice device : getConnectedGameControllerDevices()) {
            deviceKeys.add(getDeviceKey(device));
        }
        return deviceKeys;
    }

    @NonNull
    private static List<InputDevice> getConnectedGameControllerDevices() {
        final List<InputDevice> devices = new ArrayList<>();
        for (int deviceId : InputDevice.getDeviceIds()) {
            final InputDevice device = InputDevice.getDevice(deviceId);
            if (isGameControllerDevice(device)) {
                devices.add(device);
            }
        }

        Collections.sort(devices, new Comparator<InputDevice>() {
            @Override
            public int compare(InputDevice left, InputDevice right) {
                final String leftName = left.getName() != null ? left.getName().toLowerCase(Locale.ROOT) : "";
                final String rightName = right.getName() != null ? right.getName().toLowerCase(Locale.ROOT) : "";
                final int nameCompare = leftName.compareTo(rightName);
                if (nameCompare != 0) {
                    return nameCompare;
                }
                return Integer.compare(left.getId(), right.getId());
            }
        });
        return devices;
    }

    @NonNull
    private static String prefKey(@NonNull String deviceKey) {
        return KEY_PREFIX + deviceKey;
    }

    public static final class ConnectedDeviceRoute {
        public final int deviceId;
        @NonNull public final String deviceKey;
        @NonNull public final String name;
        public final int padIndex;
        public final boolean explicit;

        ConnectedDeviceRoute(int deviceId, @NonNull String deviceKey, @NonNull String name, int padIndex, boolean explicit) {
            this.deviceId = deviceId;
            this.deviceKey = deviceKey;
            this.name = name;
            this.padIndex = padIndex;
            this.explicit = explicit;
        }
    }
}