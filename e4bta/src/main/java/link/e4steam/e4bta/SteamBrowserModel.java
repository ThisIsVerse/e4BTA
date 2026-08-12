package link.e4steam.e4bta;

import link.e4steam.steam.SteamFriendHost;
import link.e4steam.steam.SteamRuntime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SteamBrowserModel {
    public record Snapshot(List<SteamFriendHost> hosts, boolean loading, String error, long generation) {
    }

    private static final AtomicBoolean REFRESHING = new AtomicBoolean();
    private static final long REFRESH_INTERVAL = 10_000;
    private static final long STARTUP_RETRY_INTERVAL = 1_000;
    private static final long STARTUP_GRACE_PERIOD = 30_000;
    private static volatile Snapshot snapshot = new Snapshot(List.of(), true, null, 0);
    private static volatile long lastRefresh;
    private static volatile long startupGraceDeadline;

    private SteamBrowserModel() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (startupGraceDeadline == 0) {
            startupGraceDeadline = now + STARTUP_GRACE_PERIOD;
        }
        long interval = snapshot.loading() ? STARTUP_RETRY_INTERVAL : REFRESH_INTERVAL;
        if ((!force && now - lastRefresh < interval) || !REFRESHING.compareAndSet(false, true)) {
            return;
        }
        Snapshot before = snapshot;
        snapshot = new Snapshot(before.hosts(), true, null, before.generation() + 1);
        SteamRuntime.get().listFriendHosts().whenComplete((hosts, failure) -> {
            lastRefresh = System.currentTimeMillis();
            long generation = snapshot.generation() + 1;
            if (failure == null) {
                List<SteamFriendHost> sorted = hosts.stream()
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList();
                snapshot = new Snapshot(sorted, false, null, generation);
            } else if (System.currentTimeMillis() < startupGraceDeadline) {
                snapshot = new Snapshot(List.of(), true, null, generation);
            } else {
                snapshot = new Snapshot(List.of(), false,
                        failure.getMessage() == null ? "Steam discovery failed" : failure.getMessage(), generation);
            }
            REFRESHING.set(false);
        });
    }
}
