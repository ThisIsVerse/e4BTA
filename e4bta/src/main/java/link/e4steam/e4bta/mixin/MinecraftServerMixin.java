package link.e4steam.e4bta.mixin;

import link.e4steam.e4bta.ServerSteamLifecycle;
import link.e4steam.steam.SteamRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow public int argPort;
    @Shadow public net.minecraft.core.net.PropertyManager propertyManager;
    @Shadow public net.minecraft.server.net.PlayerList playerList;
    @Shadow public int maxPlayers;
    @Shadow public String name;
    @Shadow public String motd;

    @Unique private int e4steam$presenceTicks;

    @Inject(method = "startServer", at = @At("RETURN"))
    private void e4steam$startServerTunnel(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        MinecraftServer self = (MinecraftServer) (Object) this;
        int port = argPort >= 0 ? argPort : propertyManager.getIntProperty("server-port", 25565);
        ServerSteamLifecycle.start(self.getMinecraftDir().toPath(), port);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void e4steam$stopServerTunnel(CallbackInfo ci) {
        ServerSteamLifecycle.stop();
    }

    @Inject(method = "doTick", at = @At("TAIL"))
    private void e4steam$updatePresence(CallbackInfo ci) {
        if (++e4steam$presenceTicks < 100) return;
        e4steam$presenceTicks = 0;
        MinecraftServer server = (MinecraftServer) (Object) this;
        int players = playerList == null ? 0 : playerList.playerEntities.size();
        SteamRuntime.get().updateHostDetails(name, motd, server.getMinecraftVersion(), 32769, players, maxPlayers);
    }
}
