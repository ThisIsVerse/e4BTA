package link.e4steam.e4bta.mixin;

import net.minecraft.core.net.packet.PacketContainerClick;
import net.minecraft.core.net.packet.PacketUseOrPlaceItemStack;
import net.minecraft.core.player.inventory.menu.MenuGuidebook;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.net.handler.PacketHandlerServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recovers from BTA clients that close the guidebook without sending the
 * matching container-close packet. In that state the server keeps the empty
 * MenuGuidebook active, so later inventory clicks address nonexistent slots
 * and normal block interactions (notably beds) can appear unusable.
 */
@Mixin(PacketHandlerServer.class)
public abstract class PacketHandlerServerMixin {
    @Shadow private PlayerServer playerEntity;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void e4steam$recoverStaleGuidebookClick(PacketContainerClick packet, CallbackInfo ci) {
        if (!(playerEntity.containerMenu instanceof MenuGuidebook)) return;

        // This packet refers to the now-closed guidebook window, so discard it
        // after restoring and synchronizing the player's real inventory menu.
        playerEntity.closeCraftingGui();
        ci.cancel();
    }

    @Inject(method = "handleUseOrPlaceItem", at = @At("HEAD"))
    private void e4steam$recoverStaleGuidebookUse(PacketUseOrPlaceItemStack packet, CallbackInfo ci) {
        if (playerEntity.containerMenu instanceof MenuGuidebook) {
            playerEntity.closeCraftingGui();
        }
    }
}
