package link.e4steam.e4bta;

import link.e4steam.E4steamClient;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.texture.stitcher.AtlasStitcher;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;

import java.io.IOException;
import java.net.URISyntaxException;

public final class E4Bta implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerTextures();
        E4steamClient.init();
        E4steamClient.LOGGER.info("e4BTA initialized");
    }

    private static void registerTextures() {
        for (AtlasStitcher stitcher : TextureRegistry.stitcherMap.values()) {
            try {
                TextureRegistry.initializeAllFiles("e4bta", stitcher, true);
            } catch (IOException | URISyntaxException exception) {
                E4steamClient.LOGGER.error("Could not register e4BTA textures", exception);
            }
        }
    }
}
