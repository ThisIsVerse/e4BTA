package link.e4steam.e4bta.mixin;

import link.e4steam.e4bta.E4BtaGameOptions;
import net.minecraft.client.gui.options.data.OptionsPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OptionsPages.class, remap = false)
public abstract class OptionsPagesE4BtaMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private static void e4steam$registerOptionsPage(CallbackInfo ci) {
        E4BtaGameOptions.registerPage();
    }
}
