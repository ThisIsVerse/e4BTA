package link.e4steam.e4bta.mixin;

import link.e4steam.E4steamClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void e4steam$runClientTasks(CallbackInfo ci) {
        E4steamClient.tickClientTasks();
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void e4steam$shutdownSteam(CallbackInfo ci) {
        E4steamClient.shutdown();
    }
}
