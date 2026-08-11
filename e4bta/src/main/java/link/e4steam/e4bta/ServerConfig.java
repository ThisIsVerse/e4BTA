package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAccessMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public record ServerConfig(boolean enabled, SteamAccessMode accessMode) {
    public static ServerConfig load(Path serverDirectory) {
        Path path = serverDirectory.resolve("config").resolve("e4bta-server.properties");
        Properties values = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IOException exception) {
                E4steamClient.LOGGER.warn("Could not read {}", path, exception);
            }
        }
        boolean enabled = Boolean.parseBoolean(values.getProperty("enabled", "true"));
        SteamAccessMode mode = parseMode(values.getProperty("accessMode", "FRIENDS_ONLY"));
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("accessMode", mode.name());
        values.setProperty("# accessMode options", "FRIENDS_ONLY or INVITE_ONLY");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                values.store(output, "e4BTA dedicated server");
            }
        } catch (IOException exception) {
            E4steamClient.LOGGER.warn("Could not write {}", path, exception);
        }
        return new ServerConfig(enabled, mode);
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
