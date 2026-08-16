package link.e4steam.steam;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Public low-latency addon channel sharing e4BTA's authenticated Steam session. */
public final class SteamAddonChannel implements AutoCloseable {
    public static final int MAX_CHANNEL_BYTES = 64;
    public static final int MAX_PAYLOAD_BYTES = SteamProtocol.MAX_DATAGRAM_SIZE - 1 - MAX_CHANNEL_BYTES;

    @FunctionalInterface
    public interface Listener {
        void onMessage(long remoteSteamId, byte[] payload);
    }

    private final SteamRuntime runtime;
    private final String name;
    private final byte[] encodedName;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    SteamAddonChannel(SteamRuntime runtime, String name) {
        this.runtime = runtime;
        this.name = validateName(name);
        this.encodedName = this.name.getBytes(StandardCharsets.UTF_8);
    }

    public String name() { return name; }

    public AutoCloseable listen(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) throw new IllegalStateException("Channel is closed");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public boolean sendToHost(byte[] payload) {
        return !closed && runtime.sendAddonToHost(encode(payload));
    }

    public int sendToGuests(byte[] payload, long excludedSteamId) {
        return closed ? 0 : runtime.sendAddonToGuests(encode(payload), excludedSteamId);
    }

    void dispatch(long remoteSteamId, byte[] payload) {
        if (closed) return;
        for (Listener listener : listeners) {
            try {
                listener.onMessage(remoteSteamId, Arrays.copyOf(payload, payload.length));
            } catch (Throwable throwable) {
                runtime.logAddonFailure(name, throwable);
            }
        }
    }

    private byte[] encode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Addon payload must be 1.." + MAX_PAYLOAD_BYTES + " bytes");
        }
        return ByteBuffer.allocate(1 + encodedName.length + payload.length)
                .put((byte) encodedName.length).put(encodedName).put(payload).array();
    }

    static Decoded decode(byte[] packet) {
        if (packet == null || packet.length < 3) return null;
        int length = Byte.toUnsignedInt(packet[0]);
        if (length == 0 || length > MAX_CHANNEL_BYTES || packet.length <= 1 + length) return null;
        String channel = new String(packet, 1, length, StandardCharsets.UTF_8);
        try {
            validateName(channel);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return new Decoded(channel, Arrays.copyOfRange(packet, 1 + length, packet.length));
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name");
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_CHANNEL_BYTES || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid addon channel: " + name);
        }
        return name;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            listeners.clear();
            runtime.removeAddonChannel(name, this);
        }
    }

    record Decoded(String channel, byte[] payload) {}
}
