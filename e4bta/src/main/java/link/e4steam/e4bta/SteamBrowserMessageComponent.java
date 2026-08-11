package link.e4steam.e4bta;

import net.minecraft.client.gui.paged.PageComponent;
import net.minecraft.client.gui.paged.ScreenPaged;

public final class SteamBrowserMessageComponent implements PageComponent {
    private final String message;

    public SteamBrowserMessageComponent(String message) {
        this.message = message;
    }

    @Override public int getHeight() { return 48; }

    @Override
    public void render(ScreenPaged screen, int x, int y, int width, int mouseX, int mouseY) {
        screen.drawStringCenteredShadow(screen.fontRenderer, message, x + width / 2, y + 18, 0xA0A0A0);
    }

    @Override public boolean onMouseClick(int a, int b, int c, int d, int e, int f) { return false; }
    @Override public boolean onMouseMove(int a, int b, int c, int d, int e) { return false; }
    @Override public boolean onMouseRelease(int a, int b, int c, int d, int e, int f) { return false; }
    @Override public void onKeyPress(int key, char character) { }
    @Override public boolean matchesSearchTerm(String term) { return false; }
}
