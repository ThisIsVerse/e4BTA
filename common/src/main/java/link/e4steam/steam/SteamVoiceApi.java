package link.e4steam.steam;

import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamUser;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** Steam-thread-safe access to Steam's microphone codec owned by e4BTA. */
public final class SteamVoiceApi {
    private static final int MAX_COMPRESSED = 64 * 1024;
    private static final int MAX_PCM = 256 * 1024;
    private final SteamRuntime runtime;

    SteamVoiceApi(SteamRuntime runtime) { this.runtime = runtime; }

    public CompletableFuture<Void> startRecording() {
        return runtime.runOnSteamThread(user -> { user.startVoiceRecording(); return null; });
    }

    public CompletableFuture<Void> stopRecording() {
        return runtime.runOnSteamThread(user -> { user.stopVoiceRecording(); return null; });
    }

    public CompletableFuture<byte[]> captureAvailable() {
        return runtime.runOnSteamThread(user -> {
            int[] available = new int[1];
            SteamUser.VoiceResult result = user.getAvailableVoice(available);
            if (result != SteamUser.VoiceResult.OK || available[0] <= 0) return new byte[0];
            int size = Math.min(available[0], MAX_COMPRESSED);
            ByteBuffer encoded = ByteBuffer.allocateDirect(size);
            int[] written = new int[1];
            SteamUser.VoiceResult voiceResult = user.getVoice(encoded, written);
            if (voiceResult != SteamUser.VoiceResult.OK || written[0] <= 0) return new byte[0];
            byte[] bytes = new byte[Math.min(written[0], size)];
            encoded.position(0);
            encoded.get(bytes);
            return bytes;
        });
    }

    public CompletableFuture<Pcm> decompress(byte[] compressed) {
        byte[] copy = compressed.clone();
        return runtime.runOnSteamThread(user -> decompress(user, copy));
    }

    private static Pcm decompress(SteamUser user, byte[] compressed) throws SteamException {
        int sampleRate = user.getVoiceOptimalSampleRate();
        ByteBuffer encoded = ByteBuffer.allocateDirect(compressed.length).put(compressed);
        encoded.flip();
        ByteBuffer pcm = ByteBuffer.allocateDirect(MAX_PCM);
        int[] written = new int[1];
        SteamUser.VoiceResult result = user.decompressVoice(encoded, pcm, written, sampleRate);
        if (result != SteamUser.VoiceResult.OK || written[0] <= 0) return new Pcm(sampleRate, new byte[0]);
        byte[] bytes = new byte[Math.min(written[0], MAX_PCM)];
        pcm.position(0);
        pcm.get(bytes);
        return new Pcm(sampleRate, bytes);
    }

    public record Pcm(int sampleRate, byte[] samples) {}
}
