package link.e4steam.e4bta;

import link.e4steam.steam.SteamConnectionProgress;

public final class SteamDiagnostics {
    private static volatile String endpoint;
    private static volatile String failure;

    private SteamDiagnostics() {
    }

    public static void connecting(String value) {
        endpoint = value;
        failure = null;
    }

    public static void failed(Throwable throwable) {
        failure = throwable == null ? "Unknown failure" : throwable.toString();
    }

    public static String endpoint() {
        return endpoint;
    }

    public static String report() {
        return "e4BTA diagnostics\n"
                + "Minecraft: 8.0.1\n"
                + "Connection stage: " + SteamConnectionProgress.message() + "\n"
                + "Last endpoint: " + (endpoint == null ? "none" : endpoint) + "\n"
                + "Last failure: " + (failure == null ? "none" : failure);
    }
}
