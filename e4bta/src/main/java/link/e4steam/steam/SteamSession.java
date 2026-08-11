package link.e4steam.steam;

import link.e4steam.E4steamClient;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One Steam tunnel exposing a local BTA dedicated server. */
public final class SteamSession {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Object lock = new Object();
    private final int localPort;
    private final SteamAccessMode accessMode;
    private final byte[] inviteToken = new byte[SteamAddress.TOKEN_LENGTH];
    private final AtomicBoolean startRequested = new AtomicBoolean();

    public volatile State state = State.STARTING;
    public volatile Throwable failureCause;
    private volatile SteamAddress address;
    private SteamRuntime.Activity activity;

    public SteamSession(int localPort, SteamAccessMode accessMode) {
        if (localPort < 1 || localPort > 65535) {
            throw new IllegalArgumentException("Invalid BTA server port: " + localPort);
        }
        this.localPort = localPort;
        this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
        RANDOM.nextBytes(inviteToken);
    }

    public int localPort() { return localPort; }
    public SteamAddress address() { return address; }
    public SteamAccessMode accessMode() { return accessMode; }

    public void startAsync() {
        if (!startRequested.compareAndSet(false, true)) return;
        Thread thread = new Thread(this::start, "e4bta-host-start");
        thread.setDaemon(true);
        thread.start();
    }

    public CompletableFuture<Void> openInviteOverlayAsync() {
        if (state != State.STARTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Steam host is not ready"));
        }
        try {
            return SteamRuntime.get().openHostInviteOverlay(this);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public void stop() {
        SteamRuntime.Activity oldActivity;
        synchronized (lock) {
            if (state == State.STOPPED || state == State.STOPPING) return;
            state = State.STOPPING;
            SteamRuntime.get().stopHosting(this);
            oldActivity = activity;
            activity = null;
            state = State.STOPPED;
        }
        if (oldActivity != null) oldActivity.close();
    }

    private void start() {
        SteamRuntime runtime = SteamRuntime.get();
        try {
            SteamRuntime.Activity acquired = runtime.acquireActivity();
            synchronized (lock) {
                if (state != State.STARTING) {
                    acquired.close();
                    return;
                }
                activity = acquired;
            }
            runtime.awaitReady();
            SteamAddress created = new SteamAddress(runtime.steamIdValue(), inviteToken);
            CompletableFuture<Long> lobby;
            synchronized (lock) {
                if (state != State.STARTING) return;
                address = created;
                runtime.startHosting(this, localPort, 0, inviteToken, accessMode);
                lobby = runtime.createHostLobby(this, accessMode, created);
            }
            lobby.get(75, TimeUnit.SECONDS);
            synchronized (lock) {
                if (state != State.STARTING) return;
                state = State.STARTED;
            }
            E4steamClient.LOGGER.info(
                    "Steam tunnel ready: local BTA server 127.0.0.1:{} is {} ({})",
                    localPort, created.inviteString(), accessMode);
        } catch (Throwable throwable) {
            runtimeFailed(throwable);
        }
    }

    void runtimeFailed(Throwable throwable) {
        SteamRuntime.Activity oldActivity;
        synchronized (lock) {
            if (state == State.STOPPED || state == State.STOPPING || state == State.UNHEALTHY) return;
            failureCause = throwable;
            state = State.UNHEALTHY;
            SteamRuntime.get().stopHosting(this);
            oldActivity = activity;
            activity = null;
        }
        if (oldActivity != null) oldActivity.close();
        E4steamClient.LOGGER.error("Could not start the BTA Steam tunnel", throwable);
    }

    public enum State { STARTING, STARTED, UNHEALTHY, STOPPING, STOPPED }
}
