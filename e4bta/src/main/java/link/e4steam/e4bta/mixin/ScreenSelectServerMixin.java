package link.e4steam.e4bta.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamFriendHost;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.e4bta.SteamBrowserModel;
import link.e4steam.e4bta.SteamBrowserMessageComponent;
import link.e4steam.e4bta.SteamDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenConnecting;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.paged.Page;
import net.minecraft.client.gui.paged.ScreenPaged;
import net.minecraft.client.gui.server.ScreenSelectServer;
import net.minecraft.client.gui.server.ServerEntry;
import net.minecraft.client.gui.server.ServerEntryComponent;
import net.minecraft.client.render.texture.TextureBuffered;
import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.core.item.Item;
import net.minecraft.core.util.collection.NamespaceID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.awt.image.BufferedImage;

@Mixin(ScreenSelectServer.class)
public abstract class ScreenSelectServerMixin extends ScreenPaged {
    @Unique
    private static Page e4steam$steamPage;

    @Shadow public ButtonElement buttonAddServer;
    @Shadow public ButtonElement buttonEdit;
    @Shadow public ButtonElement buttonDelete;
    @Shadow public ButtonElement buttonRefresh;
    @Shadow public ServerEntry selectedEntry;
    @Unique private long e4steam$browserGeneration = -1;

    private ScreenSelectServerMixin() {
        super(null, null, null, "", new String[0]);
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void e4steam$registerSteamPage(CallbackInfo ci) {
        Item steamIcon = new Item(
                NamespaceID.getTemp("e4bta", "steam_friends_icon"),
                "steam_friends_icon",
                Item.highestItemId + 1
        );
        ItemModelDispatcher.getInstance().addDispatch(
                new ItemModelStandard(steamIcon, "e4bta").setIcon("e4bta:item/steam_friends")
        );
        e4steam$steamPage = ScreenSelectServer.CREATE_WORLD_PAGES.register(
                new Page("gui.e4bta.steam_servers", steamIcon.getDefaultStack())
        );
    }

    @Inject(method = "refreshPages", at = @At("TAIL"))
    private void e4steam$refreshSteamFriends(CallbackInfo ci) {
        SteamBrowserModel.refresh(true);
        e4steam$populateSteamPage();
    }

    @Unique
    private void e4steam$populateSteamPage() {
        e4steam$steamPage.clearComponents();
        SteamBrowserModel.Snapshot snapshot = SteamBrowserModel.snapshot();
        e4steam$browserGeneration = snapshot.generation();
        ScreenSelectServer screen = (ScreenSelectServer) (Object) this;
        UUID selectedId = selectedEntry == null ? null : selectedEntry.uuid;
        ServerEntry refreshedSelection = null;
        if (snapshot.hosts().isEmpty()) {
            String message = snapshot.loading() ? "Refreshing Steam friends..."
                    : snapshot.error() != null ? "Steam discovery failed: " + snapshot.error()
                    : "No Steam friends are currently hosting e4BTA.";
            e4steam$steamPage.withComponent(new SteamBrowserMessageComponent(message));
            return;
        }
        for (SteamFriendHost host : snapshot.hosts()) {
                UUID id = UUID.nameUUIDFromBytes(("e4bta:" + Long.toUnsignedString(host.steamId()))
                        .getBytes(StandardCharsets.UTF_8));
                ServerEntry entry = new ServerEntry(id, host.endpoint());
                entry.overrideName = host.serverName();
                entry.baseName = host.name();
                entry.motd = host.motd();
                entry.version = host.version();
                entry.protocolVersion = host.protocol();
                entry.playerCount = host.players();
                entry.playerCap = host.maxPlayers();
                entry.isUp = true;
                entry.showIp = false;
                BufferedImage avatar = e4steam$avatar(host);
                if (avatar != null) entry.icon = new TextureBuffered(avatar, false, false, false);
                e4steam$steamPage.withComponent(new ServerEntryComponent(screen, entry));
                if (entry.uuid.equals(selectedId)) refreshedSelection = entry;
        }
        if (selectedId != null) {
            screen.setSelectedEntry(refreshedSelection);
            screen.setupButtons();
        }
    }

    @Unique
    private static BufferedImage e4steam$avatar(SteamFriendHost host) {
        if (host.avatarWidth() <= 0 || host.avatarHeight() <= 0 || host.avatarRgba().length == 0) return null;
        BufferedImage image = new BufferedImage(host.avatarWidth(), host.avatarHeight(), BufferedImage.TYPE_INT_ARGB);
        byte[] rgba = host.avatarRgba();
        for (int y = 0; y < host.avatarHeight(); y++) for (int x = 0; x < host.avatarWidth(); x++) {
            int offset = (y * host.avatarWidth() + x) * 4;
            int color = ((rgba[offset + 3] & 255) << 24) | ((rgba[offset] & 255) << 16)
                    | ((rgba[offset + 1] & 255) << 8) | (rgba[offset + 2] & 255);
            image.setRGB(x, y, color);
        }
        return image;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void e4steam$refreshAutomatically(CallbackInfo ci) {
        if (selectedPage != e4steam$steamPage) return;
        buttonDelete.visible = false;
        buttonDelete.enabled = false;
        SteamBrowserModel.refresh(false);
        if (e4steam$browserGeneration != SteamBrowserModel.snapshot().generation()) e4steam$populateSteamPage();
    }

    @Inject(method = "setupButtons", at = @At("TAIL"))
    private void e4steam$hideEditingButtons(CallbackInfo ci) {
        if (selectedPage == e4steam$steamPage) {
            buttonAddServer.visible = true;
            buttonAddServer.enabled = SteamDiagnostics.endpoint() != null;
            buttonAddServer.displayString = "Reconnect";
            buttonEdit.visible = true;
            buttonEdit.enabled = true;
            buttonEdit.displayString = "Copy Diagnostics";
            buttonDelete.visible = false;
            buttonDelete.enabled = false;
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void e4steam$steamButtons(ButtonElement button, CallbackInfo ci) {
        if (selectedPage != e4steam$steamPage) return;
        if (button == buttonRefresh) {
            SteamBrowserModel.refresh(true);
            e4steam$populateSteamPage();
            ci.cancel();
        } else if (button == buttonDelete) {
            ci.cancel();
        } else if (button == buttonAddServer && SteamDiagnostics.endpoint() != null) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.displayScreen(new ScreenConnecting(minecraft, SteamDiagnostics.endpoint(), 25565));
            ci.cancel();
        } else if (button == buttonEdit) {
            Minecraft.getMinecraft().copyToClipboard(SteamDiagnostics.report());
            buttonEdit.displayString = "Copied!";
            ci.cancel();
        }
    }
}
