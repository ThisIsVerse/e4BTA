package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;

import java.nio.file.Path;

public final class ServerSteamLifecycle {
    private ServerSteamLifecycle() {}

    public static synchronized void start(Path serverDirectory, int port) {
        if (E4steamClient.session != null) return;
        ServerConfig config = ServerConfig.load(serverDirectory);
        if (!config.enabled()) {
            E4steamClient.LOGGER.info("Dedicated-server Steam tunnel is disabled");
            return;
        }
        SteamRuntime.preloadCompatibilityClasses();
        SteamSession created = new SteamSession(port, config.accessMode());
        E4steamClient.session = created;
        created.startAsync();
        E4steamClient.LOGGER.info("Starting dedicated-server Steam tunnel for port {}", port);
    }

    public static synchronized void stop() {
        E4steamClient.shutdown();
    }
}
