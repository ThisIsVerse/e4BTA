package link.e4steam;

import link.e4steam.steam.SteamAccessMode;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Human-editable BTA configuration. */
public record Config(boolean autoHost, int hostPort, SteamAccessMode accessMode, boolean autoStartSteam) {
    public Config {
        if (hostPort < 1 || hostPort > 65535) hostPort = 25565;
        if (accessMode == null || accessMode == SteamAccessMode.LOCAL_ONLY) {
            accessMode = SteamAccessMode.FRIENDS_ONLY;
        }
    }

    private static Path path() {
        return Minecraft.getMinecraft().getMinecraftDir().toPath()
                .resolve("config").resolve("e4bta.properties");
    }

    public static Config load() {
        Path path = path();
        Properties values = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IOException exception) {
                E4steamClient.LOGGER.warn("Could not read {}", path, exception);
            }
        }

        boolean autoHost = Boolean.parseBoolean(values.getProperty("autoHost", "false"));
        int port = parsePort(values.getProperty("hostPort", "25565"));
        SteamAccessMode mode = parseMode(values.getProperty("accessMode", "FRIENDS_ONLY"));
        boolean autoStartSteam = Boolean.parseBoolean(values.getProperty("autoStartSteam", "true"));

        Config config = new Config(autoHost, port, mode, autoStartSteam);
        config.save();
        return config;
    }

    public void save() {
        Path path = path();
        Properties values = new Properties();
        values.setProperty("autoHost", Boolean.toString(autoHost));
        values.setProperty("hostPort", Integer.toString(hostPort));
        values.setProperty("accessMode", accessMode.name());
        values.setProperty("autoStartSteam", Boolean.toString(autoStartSteam));
        values.setProperty("# accessMode options", "FRIENDS_ONLY or INVITE_ONLY");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                values.store(output, "e4BTA");
            }
        } catch (IOException exception) {
            E4steamClient.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535 ? port : 25565;
        } catch (RuntimeException ignored) {
            return 25565;
        }
    }

    private static SteamAccessMode parseMode(String value) {
        try {
            SteamAccessMode mode = SteamAccessMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return mode == SteamAccessMode.LOCAL_ONLY ? SteamAccessMode.FRIENDS_ONLY : mode;
        } catch (RuntimeException ignored) {
            return SteamAccessMode.FRIENDS_ONLY;
        }
    }
}
