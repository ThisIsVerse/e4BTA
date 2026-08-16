package link.e4steam.e4bta;

import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.core.item.Item;
import net.minecraft.core.util.collection.NamespaceID;

public final class E4BtaIcons {
    private static Item steamFriends;

    private E4BtaIcons() {}

    public static synchronized Item steamFriends() {
        if (steamFriends == null) {
            steamFriends = new Item(
                    NamespaceID.getTemp("e4bta", "steam_friends_icon"),
                    "steam_friends_icon",
                    Item.highestItemId + 1
            );
            ItemModelDispatcher.getInstance().addDispatch(
                    new ItemModelStandard(steamFriends, "e4bta").setIcon("e4bta:item/steam_friends")
            );
        }
        return steamFriends;
    }
}
