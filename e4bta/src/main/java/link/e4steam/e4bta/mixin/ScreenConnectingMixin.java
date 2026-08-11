package link.e4steam.e4bta.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamClientBridge;
import net.minecraft.client.gui.ScreenConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.net.InetSocketAddress;

/** Rewrites Steam pseudo-hostnames to temporary loopback TCP bridges. */
@Mixin(ScreenConnecting.class)
public abstract class ScreenConnectingMixin {
    @ModifyArgs(
            method = "lambda$new$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/net/handler/PacketHandlerClient;<init>(Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V"
            )
    )
    private void e4steam$resolveSteamAddress(Args args) {
        String host = args.get(1);
        SteamAddress.tryParse(host).ifPresent(address -> {
            try {
                InetSocketAddress local = SteamClientBridge.open(address);
                args.set(1, local.getAddress().getHostAddress());
                args.set(2, local.getPort());
            } catch (Exception exception) {
                E4steamClient.LOGGER.error("Could not create Steam loopback bridge", exception);
                throw new IllegalStateException("Could not initialize Steam networking", exception);
            }
        });
    }
}
