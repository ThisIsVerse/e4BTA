package link.e4steam.e4bta.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamConnectionProgress;
import link.e4steam.e4bta.SteamDiagnostics;
import link.e4steam.steam.SteamClientBridge;
import net.minecraft.client.gui.ScreenConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** Rewrites Steam pseudo-hostnames to temporary loopback TCP bridges. */
@Mixin(ScreenConnecting.class)
public abstract class ScreenConnectingMixin {
    @Unique
    private volatile boolean e4steam$steamConnection;

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
                e4steam$steamConnection = true;
                SteamDiagnostics.connecting(host);
                SteamConnectionProgress.update("Finding Steam host...");
                boolean ready = SteamRuntime.get()
                        .prepareDirectConnect(host)
                        .get(35, TimeUnit.SECONDS);
                if (!ready) {
                    throw new IllegalStateException("Steam did not join the host lobby");
                }
                InetSocketAddress local = SteamClientBridge.open(address);
                SteamConnectionProgress.update("Connecting to the BTA server...");
                args.set(1, local.getAddress().getHostAddress());
                args.set(2, local.getPort());
            } catch (Exception exception) {
                SteamDiagnostics.failed(exception);
                E4steamClient.LOGGER.error("Could not create Steam loopback bridge", exception);
                Throwable detail = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException(
                        "Could not initialize Steam networking: " + detail.getMessage(),
                        exception
                );
            }
        });
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void e4steam$renderProgress(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!e4steam$steamConnection) {
            return;
        }
        ScreenConnecting screen = (ScreenConnecting) (Object) this;
        screen.drawStringCenteredShadow(
                screen.fontRenderer,
                SteamConnectionProgress.message(),
                screen.width / 2,
                screen.height / 2 + 24,
                0xA0A0A0
        );
    }
}
