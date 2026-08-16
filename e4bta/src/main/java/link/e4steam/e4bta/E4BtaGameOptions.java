package link.e4steam.e4bta;

import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.e4bta.mixin.OptionsPageRegistryAccessor;
import net.minecraft.client.gui.options.components.BooleanOptionComponent;
import net.minecraft.client.gui.options.components.IntegerOptionComponent;
import net.minecraft.client.gui.options.components.OptionsCategory;
import net.minecraft.client.gui.options.components.ToggleableOptionComponent;
import net.minecraft.client.gui.options.data.OptionsPage;
import net.minecraft.client.gui.options.data.OptionsPages;
import net.minecraft.client.gui.options.data.OptionsPageRegistry;
import net.minecraft.client.option.GameSettings;
import net.minecraft.client.option.OptionBoolean;
import net.minecraft.client.option.OptionEnum;
import net.minecraft.client.option.OptionInteger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class E4BtaGameOptions {
    private static final Set<String> ADDONS = new HashSet<>();
    private static OptionBoolean autoHost;
    private static OptionInteger hostPort;
    private static OptionEnum<AccessMode> accessMode;
    private static OptionBoolean autoStartSteam;
    private static OptionsPage page;
    private static boolean initialized;
    private static boolean registered;

    private E4BtaGameOptions() {}

    public static synchronized void initialize() {
        if (initialized || E4steamClient.config() == null) return;
        initialized = true;
        Config config = E4steamClient.config();
        autoHost = GameSettings.register(new OptionBoolean("e4bta.autoHost", config.autoHost()));
        hostPort = GameSettings.register(new OptionInteger("e4bta.hostPort", config.hostPort()));
        accessMode = GameSettings.register(new OptionEnum<>("e4bta.accessMode", AccessMode.class,
                AccessMode.from(config.accessMode())));
        autoStartSteam = GameSettings.register(
                new OptionBoolean("e4bta.autoStartSteam", config.autoStartSteam()));

        autoHost.addOnChangeCallback((minecraft, option) -> apply());
        hostPort.addOnChangeCallback((minecraft, option) -> apply());
        accessMode.addOnChangeCallback((minecraft, option) -> apply());
        autoStartSteam.addOnChangeCallback((minecraft, option) -> apply());
    }

    public static synchronized void registerPage() {
        initialize();
        if (!initialized) return;
        if (!registered) {
            OptionsCategory networking = new OptionsCategory("gui.options.page.e4bta.category.settings")
                    .withComponent(new BooleanOptionComponent(autoHost))
                    .withComponent(new IntegerOptionComponent(hostPort))
                    .withComponent(new ToggleableOptionComponent<>(accessMode))
                    .withComponent(new BooleanOptionComponent(autoStartSteam));
            page = new OptionsPage("gui.options.page.e4bta.title", E4BtaIcons.steamFriends().getDefaultStack())
                    .withComponent(networking);
            OptionsPages.register(page);
            registered = true;
        }
        placeAfterCatalyst();
    }

    public static synchronized void addAddonCategory(String id, OptionsCategory category) {
        registerPage();
        if (page != null && ADDONS.add(id)) page.withComponent(category);
    }

    private static void apply() {
        int port = hostPort.value;
        if (port < 1 || port > 65535) {
            port = E4steamClient.config().hostPort();
            hostPort.value = port;
        }
        E4steamClient.updateConfig(new Config(
                autoHost.value,
                port,
                accessMode.value.steamMode,
                autoStartSteam.value
        ));
    }

    private static void placeAfterCatalyst() {
        OptionsPageRegistry registry = OptionsPageRegistry.getInstance();
        List<OptionsPage> pages = ((OptionsPageRegistryAccessor) (Object) registry).e4steam$getPages();
        int catalyst = -1;
        for (int index = 0; index < pages.size(); index++) {
            if ("gui.options.page.catalyst".equals(pages.get(index).getTranslationKey())) {
                catalyst = index;
                break;
            }
        }
        if (catalyst < 0 || pages.indexOf(page) == catalyst + 1) return;
        pages.remove(page);
        pages.add(Math.min(catalyst + 1, pages.size()), page);
    }

    private enum AccessMode {
        FRIENDS_ONLY(SteamAccessMode.FRIENDS_ONLY),
        INVITE_ONLY(SteamAccessMode.INVITE_ONLY);

        private final SteamAccessMode steamMode;

        AccessMode(SteamAccessMode steamMode) {
            this.steamMode = steamMode;
        }

        private static AccessMode from(SteamAccessMode mode) {
            return mode == SteamAccessMode.INVITE_ONLY ? INVITE_ONLY : FRIENDS_ONLY;
        }
    }
}
