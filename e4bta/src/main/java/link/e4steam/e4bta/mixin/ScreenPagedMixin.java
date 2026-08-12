package link.e4steam.e4bta.mixin;

import net.minecraft.client.gui.paged.ScreenPaged;
import net.minecraft.core.lang.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScreenPaged.class)
public abstract class ScreenPagedMixin {
    @Redirect(
            method = "drawPagesListItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/lang/I18n;translateKey(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String e4steam$translateSteamPage(I18n language, String key) {
        if ("gui.e4bta.steam_servers".equals(key)) {
            return "Steam Friends";
        }
        return language.translateKey(key);
    }
}
