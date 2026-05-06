package kr.co.iefriends.pcsx2.input;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class RemoteInputReceiver {
    public interface PacketHandler {
        boolean handle(@NonNull RemoteGamepadInputPacket packet);
    }

    public interface ErrorListener {
        void onRemoteInputError(@Nullable String rawPayload, @NonNull Exception error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable private volatile PacketHandler packetHandler;
    @Nullable private volatile ErrorListener errorListener;

    public void setPacketHandler(@Nullable PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Nullable
    public PacketHandler getPacketHandler() {
        return packetHandler;
    }

    public void setErrorListener(@Nullable ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public boolean hasPacketHandler() {
        return packetHandler != null;
    }

    public boolean receiveJson(@Nullable String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return false;
        }

        try {
            return receivePacket(RemoteGamepadInputPacket.fromJson(rawJson));
        } catch (JSONException e) {
            notifyError(rawJson, e);
            return false;
        }
    }

    public boolean receiveJsonObject(@Nullable JSONObject json) {
        if (json == null) {
            return false;
        }

        try {
            return receivePacket(RemoteGamepadInputPacket.fromJson(json));
        } catch (JSONException e) {
            notifyError(json.toString(), e);
            return false;
        }
    }

    public boolean receivePacket(@Nullable RemoteGamepadInputPacket packet) {
        if (packet == null || !packet.hasEvents()) {
            return false;
        }

        final PacketHandler handler = packetHandler;
        if (handler == null) {
            return false;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return handler.handle(packet);
        }

        return mainHandler.post(() -> {
            final PacketHandler currentHandler = packetHandler;
            if (currentHandler != null) {
                currentHandler.handle(packet);
            }
        });
    }

    private void notifyError(@Nullable String rawPayload, @NonNull Exception error) {
        final ErrorListener listener = errorListener;
        if (listener != null) {
            listener.onRemoteInputError(rawPayload, error);
        }
    }
}