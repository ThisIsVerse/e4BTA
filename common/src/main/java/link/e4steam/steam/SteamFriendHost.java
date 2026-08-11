package link.e4steam.steam;

/** A Steam friend currently advertising an e4BTA server. */
public record SteamFriendHost(
        long steamId,
        String name,
        String endpoint,
        String serverName,
        String motd,
        String version,
        int protocol,
        int players,
        int maxPlayers,
        int avatarWidth,
        int avatarHeight,
        byte[] avatarRgba
) {
}
