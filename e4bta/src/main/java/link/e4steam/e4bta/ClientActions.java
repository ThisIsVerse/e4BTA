package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenConnecting;

import java.util.concurrent.ConcurrentLinkedQueue;

/** All hard references to client-only BTA classes live behind this boundary. */
public final class ClientActions {
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    private ClientActions() {}

    public static void acceptSteamInvite(String endpoint, String hostName) {
        SteamRuntime.get().beginGuestConnect(endpoint).whenComplete((accepted, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(accepted)) {
                E4steamClient.showSteamJoinFailure(
                        failure == null ? "Invitation expired" : failure.getMessage());
                return;
            }
            TASKS.add(() -> {
                Minecraft minecraft = Minecraft.getMinecraft();
                E4steamClient.LOGGER.info("Joining {} through Steam ({})", hostName, endpoint);
                minecraft.displayScreen(new ScreenConnecting(minecraft, endpoint, 25565));
            });
        });
    }

    public static void showFailure(String message) {
        TASKS.add(() -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.hudIngame != null) {
                minecraft.hudIngame.addChatMessage("[e4steam] " + message);
            }
        });
    }

    public static void tick() {
        for (Runnable task; (task = TASKS.poll()) != null;) {
            try {
                task.run();
            } catch (Throwable throwable) {
                E4steamClient.LOGGER.error("Client task failed", throwable);
            }
        }
    }
}
