package link.e4steam;

/** Minimal platform adapter used by the shared Steam runtime. */
public final class Agnos {
    private Agnos() {}

    public static boolean isClient() {
        // The shared e4steam runtime historically uses this as a guard for
        // "a process allowed to own Steam". The BTA dedicated-server port is
        // deliberately allowed to do so as well.
        return true;
    }

    public static boolean isDedicatedServer() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
                == net.fabricmc.api.EnvType.SERVER;
    }

    public static java.nio.file.Path configDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("config");
    }
}
