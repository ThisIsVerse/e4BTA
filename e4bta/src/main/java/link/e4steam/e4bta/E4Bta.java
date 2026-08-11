package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import net.fabricmc.api.ClientModInitializer;

public final class E4Bta implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        E4steamClient.init();
        E4steamClient.LOGGER.info("e4BTA initialized");
    }
}
