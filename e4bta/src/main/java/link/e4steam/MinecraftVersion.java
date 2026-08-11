package link.e4steam;

/** Lobby compatibility discriminator. BTA clients must match exactly. */
public final class MinecraftVersion {
    private MinecraftVersion() {}

    public static String current() {
        return "bta-8.0.1";
    }
}
