package link.e4steam;

import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** BTA-facing lifecycle and UI facade used by the shared Steam code. */
public final class E4steamClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("e4BTA");
    public static volatile SteamSession session;
    private static volatile SteamRuntime.Activity clientActivity;
    private E4steamClient() {}

    public static void init() {
        SteamRuntime.preloadCompatibilityClasses();
        startClientRuntime();
        Config config = Config.load();
        if (config.autoHost()) {
            SteamSession created = new SteamSession(config.hostPort(), config.accessMode());
            session = created;
            created.startAsync();
        }
    }

    private static void startClientRuntime() {
        if (Agnos.isDedicatedServer() || clientActivity != null) {
            return;
        }
        SteamRuntime runtime = SteamRuntime.get();
        SteamRuntime.Activity activity = runtime.acquireActivity();
        clientActivity = activity;
        Thread thread = new Thread(() -> {
            try {
                runtime.awaitReady();
                LOGGER.info("Steam client integration is ready");
            } catch (Throwable throwable) {
                if (clientActivity == activity) {
                    clientActivity = null;
                }
                activity.close();
                LOGGER.error("Could not initialize Steam client integration", throwable);
            }
        }, "e4bta-client-start");
        thread.setDaemon(true);
        thread.start();
    }

    public static void acceptSteamInvite(String endpoint, String hostName) {
        if (Agnos.isDedicatedServer()) {
            LOGGER.debug("Dedicated server ignored a Steam invitation for {}", endpoint);
            return;
        }
        if (SteamAddress.tryParse(endpoint).isEmpty()) {
            showSteamJoinFailure("Invalid Steam address");
            return;
        }
        link.e4steam.e4bta.ClientActions.acceptSteamInvite(endpoint, hostName);
    }

    public static void showSteamJoinFailure(String detail) {
        String message = detail == null || detail.isBlank() ? "Unknown Steam error" : detail;
        LOGGER.warn("Could not join Steam-hosted BTA server: {}", message);
        if (Agnos.isDedicatedServer()) {
            return;
        }
        link.e4steam.e4bta.ClientActions.showFailure(message);
    }

    public static void tickClientTasks() {
        link.e4steam.e4bta.ClientActions.tick();
    }

    public static void shutdown() {
        SteamSession current = session;
        session = null;
        if (current != null) {
            current.stop();
        }
        SteamRuntime.Activity activity = clientActivity;
        clientActivity = null;
        if (activity != null) {
            activity.close();
        }
        SteamRuntime.get().shutdown();
    }
}
