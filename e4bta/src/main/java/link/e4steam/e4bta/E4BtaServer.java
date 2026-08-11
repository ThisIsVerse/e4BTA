package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Common entrypoint; actual hosting begins after the dedicated server binds. */
public final class E4BtaServer implements ModInitializer {
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            E4steamClient.LOGGER.info("e4steam dedicated-server support initialized");
        }
    }
}
