package link.e4steam.steam;

import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamFriendsCallback;
import com.codedisaster.steamworks.SteamAPICall;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamResult;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.Mirror;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;

/** Steam lobby, friends, overlay and invite state. Called only by the Steam worker. */
final class SteamLobbyManager implements AutoCloseable {
    static final int VANILLA_LOBBY_CAPACITY = 8;
    static final int VANILLA_MAX_GUESTS = VANILLA_LOBBY_CAPACITY - 1;
    static final int HOST_LOBBY_MAX_ATTEMPTS = 6;

    private static final String KEY_PROTOCOL = "e4steam_protocol";
    private static final String KEY_MINECRAFT = "e4steam_minecraft";
    private static final String KEY_ENDPOINT = "e4steam_endpoint";
    private static final String PROTOCOL_VERSION = Byte.toString(SteamProtocol.VERSION);
    private static final String LOBBY_CONNECT_PREFIX = "e4steam-lobby:";
    private static final String RICH_ENDPOINT = "e4bta_endpoint";
    private static final String RICH_NAME = "e4bta_name";
    private static final String RICH_MOTD = "e4bta_motd";
    private static final String RICH_VERSION = "e4bta_version";
    private static final String RICH_PROTOCOL = "e4bta_protocol";
    private static final String RICH_PLAYERS = "e4bta_players";
    private static final String RICH_MAX_PLAYERS = "e4bta_max_players";
    private static final long GUEST_JOIN_TIMEOUT_MILLIS = 30_000;

    private final SteamRuntime runtime;
    private final String minecraftVersion = MinecraftVersion.current();
    private final SteamFriends friends;
    private final SteamMatchmaking matchmaking;

    private SteamSession pendingHostOwner;
    private SteamAccessMode pendingHostAccessMode;
    private SteamAddress pendingHostAddress;
    private CompletableFuture<Long> pendingHostResult;
    private int pendingHostAttempts;
    private boolean pendingHostCanceled;
    private SteamSession queuedHostOwner;
    private SteamAccessMode queuedHostAccessMode;
    private SteamAddress queuedHostAddress;
    private CompletableFuture<Long> queuedHostResult;
    private SteamSession hostLobbyOwner;
    private SteamAccessMode hostLobbyAccessMode;
    private long hostLobbyId;
    private long guestLobbyId;
    private long guestHostSteamId;
    private long guestInviterSteamId;
    private SteamGuestJoinState guestJoinState;
    private String guestEndpoint;
    private long requestedLobbyId;
    private long requestedFriendId;
    private long requestedJoinDeadlineMillis;
    private String pendingDirectEndpoint;
    private CompletableFuture<Boolean> pendingDirectResult;
    private long pendingDirectHostId;
    private long pendingDirectDeadlineMillis;
    private final Set<Long> canceledJoinLobbyIds = new HashSet<>();

    SteamLobbyManager(SteamRuntime runtime) {
        this.runtime = runtime;
        friends = new SteamFriends(new SteamFriendsCallback() {
            @Override
            public void onGameLobbyJoinRequested(SteamID lobby, SteamID friend) {
                requestJoin(lobby, friend);
            }

            @Override
            public void onGameRichPresenceJoinRequested(SteamID friend, String connect) {
                if (connect == null) {
                    return;
                }
                long friendId = SteamNativeHandle.getNativeHandle(friend);
                Optional<SteamAddress> direct = SteamAddress.tryParse(connect);
                if (direct.isPresent()
                        && direct.get().steamId() == friendId
                        && friends.getFriendRelationship(friend) == SteamFriends.FriendRelationship.Friend) {
                    E4steamClient.acceptSteamInvite(connect, friends.getFriendPersonaName(friend));
                    return;
                }
                if (!connect.startsWith(LOBBY_CONNECT_PREFIX)) {
                    return;
                }
                try {
                    long lobbyId = Long.parseUnsignedLong(connect.substring(LOBBY_CONNECT_PREFIX.length()));
                    requestJoin(SteamID.createFromNativeHandle(lobbyId), friend);
                } catch (NumberFormatException ignored) {
                    E4steamClient.LOGGER.debug("Ignored an invalid Steam rich-presence join string");
                }
            }

            @Override
            public void onFriendRichPresenceUpdate(SteamID friend, int appId) {
                if (SteamNativeHandle.getNativeHandle(friend) == pendingDirectHostId) {
                    joinPendingDirectLobby(friend);
                }
            }
        });
        matchmaking = new SteamMatchmaking(new SteamMatchmakingCallback() {
            @Override
            public void onLobbyCreated(SteamResult result, SteamID lobby) {
                handleLobbyCreated(result, lobby);
            }

            @Override
            public void onLobbyEnter(
                    SteamID lobby,
                    int chatPermissions,
                    boolean blocked,
                    SteamMatchmaking.ChatRoomEnterResponse response
            ) {
                handleLobbyEnter(lobby, response);
            }

            @Override
            public void onLobbyDataUpdate(SteamID lobby, SteamID member, boolean success) {
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                if (success && guestLobbyId == lobbyId && guestEndpoint == null) {
                    resolveGuestEndpoint();
                }
            }

            @Override
            public void onLobbyChatUpdate(
                    SteamID lobby,
                    SteamID changedUser,
                    SteamID makingChange,
                    SteamMatchmaking.ChatMemberStateChange stateChange
            ) {
                if (stateChange == SteamMatchmaking.ChatMemberStateChange.Entered) {
                    return;
                }
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                long userId = SteamNativeHandle.getNativeHandle(changedUser);
                if (hostLobbyOwner != null && hostLobbyId == lobbyId) {
                    if (userId == runtime.steamIdValue()) {
                        loseHostLobby("Steam removed the host from its lobby");
                        return;
                    }
                    runtime.closeRemoteBridges(userId);
                }
                if (guestLobbyId == lobbyId
                        && (guestHostSteamId == userId || userId == runtime.steamIdValue())) {
                    loseGuestLobby();
                }
            }

            @Override
            public void onLobbyKicked(SteamID lobby, SteamID admin, boolean disconnected) {
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                if (hostLobbyOwner != null && hostLobbyId == lobbyId) {
                    loseHostLobby("Steam closed the host lobby");
                } else if (guestLobbyId == lobbyId) {
                    loseGuestLobby();
                }
            }
        });
    }

    CompletableFuture<Long> createHostLobby(
            SteamSession owner,
            SteamAccessMode accessMode,
            SteamAddress address
    ) {
        CompletableFuture<Long> result = new CompletableFuture<>();
        if (accessMode == SteamAccessMode.LOCAL_ONLY) {
            result.completeExceptionally(new IOException("Local-only mode does not create a Steam lobby"));
            return result;
        }
        if (hostLobbyOwner != null || queuedHostOwner != null) {
            result.completeExceptionally(new IOException("A Steam lobby is already active"));
            return result;
        }

        if (pendingHostOwner != null) {
            if (!pendingHostCanceled) {
                result.completeExceptionally(new IOException("A Steam lobby is already being created"));
            } else {
                queuedHostOwner = owner;
                queuedHostAccessMode = accessMode;
                queuedHostAddress = address;
                queuedHostResult = result;
            }
            return result;
        }

        issueHostCreate(owner, accessMode, address, result, 0);
        return result;
    }

    void stopHosting(SteamSession owner) {
        if (pendingHostOwner == owner) {
            // Steam's lobby-created callback does not identify its API call.
            // Keep this canceled request as a tombstone so a late callback
            // cannot be mistaken for a newer hosting session.
            pendingHostCanceled = true;
            pendingHostResult.completeExceptionally(new IOException("Steam hosting was stopped"));
        }
        if (queuedHostOwner == owner) {
            CompletableFuture<Long> result = queuedHostResult;
            clearQueuedHost();
            result.completeExceptionally(new IOException("Steam hosting was stopped"));
        }
        if (hostLobbyOwner == owner) {
            if (hostLobbyId != 0) {
                SteamID lobby = SteamID.createFromNativeHandle(hostLobbyId);
                matchmaking.setLobbyJoinable(lobby, false);
                matchmaking.leaveLobby(lobby);
            }
            clearHostLobby();
            friends.clearRichPresence();
        }
    }

    void openHostInviteOverlay(SteamSession owner) throws IOException {
        if (hostLobbyOwner != owner) {
            throw new IOException("Steam lobby is not ready");
        }
        requireOverlay();
        if (hostLobbyId == 0) {
            openFriendsOverlayCompat();
            return;
        }
        friends.activateGameOverlayInviteDialog(SteamID.createFromNativeHandle(hostLobbyId));
    }

    void openFriendsOverlay() throws IOException {
        requireOverlay();
        openFriendsOverlayCompat();
    }

    boolean allows(SteamSession owner, long remoteSteamId) {
        if (hostLobbyOwner != owner) {
            return false;
        }
        return isAllowedHostPeer(remoteSteamId);
    }

    boolean mayAcceptPeer(long remoteSteamId) {
        if (guestLobbyId != 0 && guestHostSteamId == remoteSteamId) {
            return true;
        }
        return hostLobbyOwner != null && isAllowedHostPeer(remoteSteamId);
    }

    void forEachKnownSessionPeer(LongConsumer consumer) {
        if (guestLobbyId != 0 && guestHostSteamId != 0) {
            consumer.accept(guestHostSteamId);
        }

        if (hostLobbyOwner == null || hostLobbyId == 0) {
            return;
        }
        SteamID lobbyId = SteamID.createFromNativeHandle(hostLobbyId);
        int memberCount = matchmaking.getNumLobbyMembers(lobbyId);
        for (int index = 0; index < memberCount; index++) {
            SteamID member = matchmaking.getLobbyMemberByIndex(lobbyId, index);
            if (member == null) {
                continue;
            }
            long remoteSteamId = SteamNativeHandle.getNativeHandle(member);
            if (remoteSteamId != 0
                    && remoteSteamId != runtime.steamIdValue()
                    && friends.getFriendRelationship(member) == SteamFriends.FriendRelationship.Friend) {
                consumer.accept(remoteSteamId);
            }
        }
    }

    boolean keepsRuntimeAlive() {
        return (pendingHostOwner != null && !pendingHostCanceled)
                || queuedHostOwner != null
                || hostLobbyOwner != null
                || guestLobbyId != 0
                || requestedLobbyId != 0;
    }

    void clientBridgeOpened(long remoteSteamId) {
        if (guestLobbyId != 0 && guestHostSteamId == remoteSteamId) {
            guestJoinState.connected();
        }
    }

    void clientBridgeClosed(long remoteSteamId, boolean anotherBridgeExists) {
        if (guestLobbyId != 0 && guestHostSteamId == remoteSteamId && !anotherBridgeExists) {
            leaveGuestLobby();
        }
    }

    void cancelGuestJoin() {
        leaveGuestLobby();
    }

    boolean claimGuestInvite(String endpoint) {
        if (guestLobbyId == 0
                || endpoint == null
                || !endpoint.equals(guestEndpoint)
                || !guestJoinState.claim()) {
            return false;
        }
        return true;
    }

    boolean beginGuestConnect(String endpoint) {
        if (guestLobbyId != 0
                && endpoint != null
                && endpoint.equals(guestEndpoint)) {
            return guestJoinState.beginConnect(
                    System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS
            );
        }
        return false;
    }

    CompletableFuture<Boolean> prepareDirectConnect(String endpoint) {
        SteamConnectionProgress.update("Finding Steam friend...");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (parsed.isEmpty()) {
            result.complete(false);
            return result;
        }

        SteamID host = SteamID.createFromNativeHandle(parsed.get().steamId());
        if (friends.getFriendRelationship(host) != SteamFriends.FriendRelationship.Friend) {
            result.completeExceptionally(new IOException("The Steam host is not in your friends list"));
            return result;
        }
        if (guestLobbyId != 0 && endpoint.equals(guestEndpoint)) {
            result.complete(beginGuestConnect(endpoint));
            return result;
        }

        if (pendingDirectResult != null) {
            pendingDirectResult.completeExceptionally(new IOException("A different Steam connection replaced this one"));
        }
        pendingDirectEndpoint = endpoint;
        pendingDirectResult = result;
        pendingDirectHostId = parsed.get().steamId();
        pendingDirectDeadlineMillis = System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS;
        if (!joinPendingDirectLobby(host)) {
            SteamConnectionProgress.update("Refreshing Steam lobby details...");
            friends.requestFriendRichPresence(host);
        }
        return result;
    }

    List<SteamFriendHost> listFriendHosts() {
        List<SteamFriendHost> hosts = new ArrayList<>();
        int count = friends.getFriendCount(SteamFriends.FriendFlags.Immediate);
        for (int index = 0; index < count; index++) {
            SteamID friend = friends.getFriendByIndex(index, SteamFriends.FriendFlags.Immediate);
            if (friend == null) {
                continue;
            }
            String endpoint = friends.getFriendRichPresence(friend, RICH_ENDPOINT);
            Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
            long steamId = SteamNativeHandle.getNativeHandle(friend);
            if (parsed.isPresent() && parsed.get().steamId() == steamId) {
                int avatarHandle = friends.getMediumFriendAvatar(friend);
                if (avatarHandle <= 0) {
                    friends.requestUserInformation(friend, false);
                }
                SteamRuntime.Avatar avatar = runtime.readAvatar(avatarHandle);
                hosts.add(new SteamFriendHost(
                        steamId,
                        friends.getFriendPersonaName(friend),
                        endpoint,
                        presence(friend, RICH_NAME, friends.getFriendPersonaName(friend) + "'s server"),
                        presence(friend, RICH_MOTD, "Hosting through Steam"),
                        presence(friend, RICH_VERSION, minecraftVersion),
                        presenceInt(friend, RICH_PROTOCOL, 32769),
                        presenceInt(friend, RICH_PLAYERS, 0),
                        presenceInt(friend, RICH_MAX_PLAYERS, 0),
                        avatar.width(), avatar.height(), avatar.rgba()
                ));
            } else {
                friends.requestFriendRichPresence(friend);
            }
        }
        return List.copyOf(hosts);
    }

    void updateHostDetails(String name, String motd, String version, int protocol, int players, int maxPlayers) {
        if (hostLobbyOwner == null) {
            return;
        }
        friends.setRichPresence(RICH_NAME, safePresence(name, "BTA Server"));
        friends.setRichPresence(RICH_MOTD, safePresence(motd, "Hosting through Steam"));
        friends.setRichPresence(RICH_VERSION, safePresence(version, minecraftVersion));
        friends.setRichPresence(RICH_PROTOCOL, Integer.toString(protocol));
        friends.setRichPresence(RICH_PLAYERS, Integer.toString(Math.max(0, players)));
        friends.setRichPresence(RICH_MAX_PLAYERS, Integer.toString(Math.max(0, maxPlayers)));
    }

    private String presence(SteamID friend, String key, String fallback) {
        String value = friends.getFriendRichPresence(friend, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private int presenceInt(SteamID friend, String key, int fallback) {
        try {
            return Integer.parseInt(presence(friend, key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String safePresence(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= 120 ? result : result.substring(0, 120);
    }

    void cleanup(long now) {
        if (pendingDirectResult != null
                && requestedLobbyId == 0
                && guestLobbyId == 0
                && pendingDirectDeadlineMillis <= now) {
            pendingDirectResult.completeExceptionally(new IOException(
                    "Steam could not find your friend's e4BTA lobby"
            ));
            clearPendingDirect();
            return;
        }
        if (requestedLobbyId != 0 && requestedJoinDeadlineMillis <= now) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinLobbyTimeout"));
            return;
        }
        if (guestLobbyId != 0 && guestJoinState.expired(now)) {
            if (runtime.hasClientBridgeForRemote(guestHostSteamId)) {
                guestJoinState.connected();
                return;
            }
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinConnectTimeout"));
        }
    }

    private void handleLobbyCreated(SteamResult result, SteamID lobby) {
        SteamSession owner = pendingHostOwner;
        SteamAccessMode accessMode = pendingHostAccessMode;
        SteamAddress address = pendingHostAddress;
        CompletableFuture<Long> hostResult = pendingHostResult;
        int attempts = pendingHostAttempts;
        boolean canceled = pendingHostCanceled;
        clearPendingHost();
        long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
        if (owner == null) {
            if (lobbyId != 0) {
                matchmaking.leaveLobby(lobby);
            }
            return;
        }
        if (canceled) {
            if (lobbyId != 0) {
                matchmaking.leaveLobby(lobby);
            }
            issueQueuedHostCreate();
            return;
        }
        if (shouldRetryHostCreation(result, attempts)) {
            E4steamClient.LOGGER.warn(
                    "Steam could not create the lobby ({}); retrying ({}/{})",
                    result,
                    attempts + 1,
                    HOST_LOBBY_MAX_ATTEMPTS
            );
            issueHostCreate(owner, accessMode, address, hostResult, attempts);
            return;
        }
        if (result != SteamResult.OK || lobbyId == 0) {
            if (!hostResult.isDone()) {
                hostResult.completeExceptionally(new IOException("Steam lobby creation failed: " + result));
            } else {
                E4steamClient.LOGGER.warn(
                        "Steam lobby creation failed with {}; continuing friends-only sharing by address",
                        result
                );
            }
            issueQueuedHostCreate();
            return;
        }

        boolean metadataReady = matchmaking.setLobbyJoinable(lobby, false)
                && matchmaking.setLobbyData(lobby, KEY_PROTOCOL, PROTOCOL_VERSION)
                && matchmaking.setLobbyData(lobby, KEY_MINECRAFT, minecraftVersion)
                && matchmaking.setLobbyData(lobby, KEY_ENDPOINT, address.inviteString())
                && matchmaking.setLobbyJoinable(lobby, true);
        if (!metadataReady) {
            matchmaking.leaveLobby(lobby);
            if (!hostResult.isDone()) {
                hostResult.completeExceptionally(new IOException("Steam rejected e4steam lobby metadata"));
            } else {
                E4steamClient.LOGGER.warn(
                        "Steam rejected lobby metadata; continuing friends-only sharing by address"
                );
            }
            issueQueuedHostCreate();
            return;
        }

        hostLobbyOwner = owner;
        hostLobbyAccessMode = accessMode;
        hostLobbyId = lobbyId;
        friends.clearRichPresence();
        friends.setRichPresence("status", "Hosting a Minecraft LAN world");
        friends.setRichPresence(RICH_ENDPOINT, address.inviteString());
        if (accessMode == SteamAccessMode.FRIENDS_ONLY) {
            friends.setRichPresence("connect", LOBBY_CONNECT_PREFIX + Long.toUnsignedString(lobbyId));
        }
        hostResult.complete(lobbyId);
    }

    private void issueHostCreate(
            SteamSession owner,
            SteamAccessMode accessMode,
            SteamAddress address,
            CompletableFuture<Long> result,
            int completedAttempts
    ) {
        pendingHostOwner = owner;
        pendingHostAccessMode = accessMode;
        pendingHostAddress = address;
        pendingHostResult = result;
        pendingHostAttempts = completedAttempts + 1;
        pendingHostCanceled = false;
        long call;
        try {
            call = createLobbyCompat(
                    matchmaking,
                    accessMode == SteamAccessMode.FRIENDS_ONLY,
                    VANILLA_LOBBY_CAPACITY
            );
        } catch (ReflectiveOperationException exception) {
            clearPendingHost();
            result.completeExceptionally(new IOException("Steam lobby compatibility call failed", exception));
            issueQueuedHostCreate();
            return;
        }
        if (call == 0) {
            clearPendingHost();
            result.completeExceptionally(new IOException("Steam rejected the lobby creation request"));
            issueQueuedHostCreate();
        }
    }

    static boolean shouldRetryHostCreation(SteamResult result, int completedAttempts) {
        boolean temporaryNetworkFailure = result == SteamResult.Timeout
                || result == SteamResult.NoConnection
                || result == SteamResult.ServiceUnavailable
                || result == SteamResult.Busy;
        return temporaryNetworkFailure && completedAttempts < HOST_LOBBY_MAX_ATTEMPTS;
    }

    static boolean canStartBeforeLobby(SteamAccessMode accessMode) {
        return false;
    }

    private void issueQueuedHostCreate() {
        if (pendingHostOwner != null || hostLobbyOwner != null || queuedHostOwner == null) {
            return;
        }
        SteamSession owner = queuedHostOwner;
        SteamAccessMode accessMode = queuedHostAccessMode;
        SteamAddress address = queuedHostAddress;
        CompletableFuture<Long> result = queuedHostResult;
        clearQueuedHost();
        issueHostCreate(owner, accessMode, address, result, 0);
    }

    private void clearPendingHost() {
        pendingHostOwner = null;
        pendingHostAccessMode = null;
        pendingHostAddress = null;
        pendingHostResult = null;
        pendingHostAttempts = 0;
        pendingHostCanceled = false;
    }

    private void clearQueuedHost() {
        queuedHostOwner = null;
        queuedHostAccessMode = null;
        queuedHostAddress = null;
        queuedHostResult = null;
    }

    private void clearHostLobby() {
        hostLobbyOwner = null;
        hostLobbyAccessMode = null;
        hostLobbyId = 0;
    }

    private void clearGuestLobby() {
        guestLobbyId = 0;
        guestHostSteamId = 0;
        guestInviterSteamId = 0;
        guestJoinState = null;
        guestEndpoint = null;
    }

    private static long createLobbyCompat(
            SteamMatchmaking matchmaking,
            boolean friendsOnly,
            int capacity
    ) throws ReflectiveOperationException {
        Class<?> steamInterface = matchmaking.getClass().getSuperclass();
        Field callback = steamInterface.getDeclaredField("callback");
        callback.setAccessible(true);
        long callbackHandle = callback.getLong(matchmaking);

        Class<?> nativeType = Class.forName("com.codedisaster.steamworks.SteamMatchmakingNative");
        Method createLobby = nativeType.getDeclaredMethod("createLobby", long.class, int.class, int.class);
        createLobby.setAccessible(true);
        // Steamworks lobby type ordinals: Private=0, FriendsOnly=1.
        return (Long) createLobby.invoke(null, callbackHandle, friendsOnly ? 1 : 0, capacity);
    }

    private static void openFriendsOverlayCompat() throws IOException {
        try {
            Class<?> nativeType = Class.forName("com.codedisaster.steamworks.SteamFriendsNative");
            Method activate = nativeType.getDeclaredMethod("activateGameOverlay", String.class);
            activate.setAccessible(true);
            activate.invoke(null, "Friends");
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Steam friends overlay compatibility call failed", exception);
        }
    }

    private void requestJoin(SteamID lobby, SteamID friend) {
        SteamConnectionProgress.update("Joining Steam lobby...");
        long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
        if (lobbyId == 0 || (hostLobbyOwner != null && hostLobbyId == lobbyId)) {
            return;
        }
        if (canceledJoinLobbyIds.contains(lobbyId)) {
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinCancelPending"));
            return;
        }
        if (requestedLobbyId == lobbyId || guestLobbyId == lobbyId) {
            return;
        }
        if (guestLobbyId != 0
                && (guestEndpoint != null
                || guestJoinState.isConnected()
                || runtime.hasClientBridgeForRemote(guestHostSteamId))) {
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinCurrentSession"));
            return;
        }
        if (guestLobbyId != 0 || requestedLobbyId != 0) {
            leaveGuestLobby();
        }
        requestedLobbyId = lobbyId;
        requestedFriendId = SteamNativeHandle.getNativeHandle(friend);
        requestedJoinDeadlineMillis = System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS;
        SteamAPICall call = matchmaking.joinLobby(lobby);
        if (call == null || !call.isValid()) {
            requestedLobbyId = 0;
            requestedFriendId = 0;
            requestedJoinDeadlineMillis = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinRejected"));
        }
    }

    private void handleLobbyEnter(SteamID lobby, SteamMatchmaking.ChatRoomEnterResponse response) {
        long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
        if (canceledJoinLobbyIds.remove(lobbyId)) {
            matchmaking.leaveLobby(lobby);
            return;
        }
        if (hostLobbyOwner != null && hostLobbyId == lobbyId) {
            return;
        }
        if (requestedLobbyId != lobbyId) {
            matchmaking.leaveLobby(lobby);
            return;
        }
        requestedLobbyId = 0;
        requestedJoinDeadlineMillis = 0;
        if (response != SteamMatchmaking.ChatRoomEnterResponse.Success) {
            requestedFriendId = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable(
                    "text.e4steam_minecraft.joinLobbyEnterFailed",
                    response
            ));
            return;
        }

        SteamID owner = matchmaking.getLobbyOwner(lobby);
        long ownerId = owner == null ? 0 : SteamNativeHandle.getNativeHandle(owner);
        if (ownerId == 0
                || ownerId == runtime.steamIdValue()
                || friends.getFriendRelationship(owner) != SteamFriends.FriendRelationship.Friend) {
            matchmaking.leaveLobby(lobby);
            requestedFriendId = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinOwnerNotFriend"));
            return;
        }
        guestLobbyId = lobbyId;
        guestHostSteamId = ownerId;
        guestInviterSteamId = requestedFriendId;
        guestJoinState = new SteamGuestJoinState(System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS);
        guestEndpoint = null;
        requestedFriendId = 0;
        resolveGuestEndpoint();
    }

    private void resolveGuestEndpoint() {
        if (guestLobbyId == 0 || guestEndpoint != null) {
            return;
        }
        SteamID lobby = SteamID.createFromNativeHandle(guestLobbyId);
        String protocol = matchmaking.getLobbyData(lobby, KEY_PROTOCOL);
        String minecraft = matchmaking.getLobbyData(lobby, KEY_MINECRAFT);
        String endpoint = matchmaking.getLobbyData(lobby, KEY_ENDPOINT);
        if (protocol == null || protocol.isBlank() || endpoint == null || endpoint.isBlank()) {
            matchmaking.requestLobbyData(lobby);
            return;
        }
        if (!PROTOCOL_VERSION.equals(protocol) || !minecraftVersion.equals(minecraft)) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinIncompatible"));
            return;
        }
        Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (parsed.isEmpty() || parsed.get().steamId() != guestHostSteamId) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinInvalidAddress"));
            return;
        }

        guestEndpoint = endpoint;
        SteamConnectionProgress.update("Steam lobby ready");
        if (pendingDirectResult != null) {
            CompletableFuture<Boolean> directResult = pendingDirectResult;
            String directEndpoint = pendingDirectEndpoint;
            clearPendingDirect();
            if (!endpoint.equals(directEndpoint)) {
                directResult.completeExceptionally(new IOException("The Steam lobby address changed; copy the new address"));
                leaveGuestLobby();
                return;
            }
            guestJoinState.waitForConfirmation();
            directResult.complete(beginGuestConnect(endpoint));
            return;
        }
        // The in-world confirmation has no countdown. Keep this lobby alive
        // until the user chooses Join, then beginGuestConnect() starts the
        // bounded Minecraft connection window.
        guestJoinState.waitForConfirmation();
        String hostName = friends.getFriendPersonaName(SteamID.createFromNativeHandle(guestHostSteamId));
        E4steamClient.acceptSteamInvite(endpoint, hostName);
    }

    private void requireOverlay() throws IOException {
        if (!runtime.isOverlayEnabledOnWorker()) {
            throw new IOException(
                    "Steam Overlay is unavailable. Add your Minecraft launcher to Steam and launch it from Steam first"
            );
        }
    }

    private boolean isAllowedHostPeer(long remoteSteamId) {
        SteamID remote = SteamID.createFromNativeHandle(remoteSteamId);
        if (friends.getFriendRelationship(remote) != SteamFriends.FriendRelationship.Friend) {
            return false;
        }

        // Friends-only deliberately permits the copied address as a fallback.
        // A private lobby is stricter: membership proves that Steam admitted
        // this friend through an invitation for the current hosting session.
        if (hostLobbyAccessMode == SteamAccessMode.FRIENDS_ONLY) {
            return true;
        }

        SteamID lobbyId = SteamID.createFromNativeHandle(hostLobbyId);
        int memberCount = matchmaking.getNumLobbyMembers(lobbyId);
        for (int index = 0; index < memberCount; index++) {
            SteamID member = matchmaking.getLobbyMemberByIndex(lobbyId, index);
            if (member != null && SteamNativeHandle.getNativeHandle(member) == remoteSteamId) {
                return true;
            }
        }
        return false;
    }

    private void loseHostLobby(String detail) {
        SteamSession lostOwner = hostLobbyOwner;
        if (lostOwner == null) {
            return;
        }
        clearHostLobby();
        friends.clearRichPresence();
        lostOwner.runtimeFailed(new IOException(detail));
    }

    private void loseGuestLobby() {
        if (guestLobbyId == 0) {
            return;
        }
        guestJoinState.loseLobby();
        runtime.closeRemoteBridges(guestHostSteamId);
        leaveGuestLobby();
        E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinLobbyClosed"));
    }

    private void leaveGuestLobby() {
        long currentLobbyId = guestLobbyId;
        SteamGuestJoinState currentJoinState = guestJoinState;
        long requested = requestedLobbyId;
        clearGuestLobby();
        requestedLobbyId = 0;
        requestedFriendId = 0;
        requestedJoinDeadlineMillis = 0;
        if (pendingDirectResult != null) {
            pendingDirectResult.complete(false);
            clearPendingDirect();
        }
        if (currentLobbyId != 0) {
            currentJoinState.cancel();
            matchmaking.leaveLobby(SteamID.createFromNativeHandle(currentLobbyId));
        } else if (requested != 0) {
            // LobbyEnter does not identify the JoinLobby API call. Preserve a
            // tombstone so a late callback cannot be accepted as a later
            // retry for the same lobby in this Steam runtime generation.
            canceledJoinLobbyIds.add(requested);
            matchmaking.leaveLobby(SteamID.createFromNativeHandle(requested));
        }
    }

    private boolean joinPendingDirectLobby(SteamID host) {
        String connect = friends.getFriendRichPresence(host, "connect");
        if (connect == null || !connect.startsWith(LOBBY_CONNECT_PREFIX)) {
            return false;
        }
        try {
            long lobbyId = Long.parseUnsignedLong(connect.substring(LOBBY_CONNECT_PREFIX.length()));
            requestJoin(SteamID.createFromNativeHandle(lobbyId), host);
            return true;
        } catch (NumberFormatException exception) {
            pendingDirectResult.completeExceptionally(new IOException("The host published an invalid Steam lobby", exception));
            clearPendingDirect();
            return true;
        }
    }

    private void clearPendingDirect() {
        pendingDirectResult = null;
        pendingDirectEndpoint = null;
        pendingDirectHostId = 0;
        pendingDirectDeadlineMillis = 0;
    }

    @Override
    public void close() {
        CompletableFuture<Long> pendingResult = pendingHostResult;
        boolean hadPendingHost = pendingHostOwner != null;
        clearPendingHost();
        if (hadPendingHost) {
            pendingResult.completeExceptionally(new IOException("Steam runtime stopped before creating the lobby"));
        }
        CompletableFuture<Long> queuedResult = queuedHostResult;
        boolean hadQueuedHost = queuedHostOwner != null;
        clearQueuedHost();
        if (hadQueuedHost) {
            queuedResult.completeExceptionally(new IOException("Steam runtime stopped before creating the lobby"));
        }
        if (hostLobbyOwner != null) {
            if (hostLobbyId != 0) {
                SteamID lobby = SteamID.createFromNativeHandle(hostLobbyId);
                matchmaking.setLobbyJoinable(lobby, false);
                matchmaking.leaveLobby(lobby);
            }
            clearHostLobby();
        }
        leaveGuestLobby();
        canceledJoinLobbyIds.clear();
        friends.clearRichPresence();
        matchmaking.dispose();
        friends.dispose();
    }

}
