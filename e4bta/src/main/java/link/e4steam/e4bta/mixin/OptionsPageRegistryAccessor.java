package link.e4steam.e4bta.mixin;

import net.minecraft.client.gui.options.data.OptionsPage;
import net.minecraft.client.gui.options.data.OptionsPageRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = OptionsPageRegistry.class, remap = false)
public interface OptionsPageRegistryAccessor {
    @Accessor("pages")
    List<OptionsPage> e4steam$getPages();
}
