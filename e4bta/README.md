# e4BTA

This Better Than Adventure port is maintained by Verse and is based on
Kamilchik's e4steam project. The port is distributed under Apache License 2.0;
the original and third-party attribution notices are included with each JAR.

This universal Babric mod tunnels a BTA dedicated server through Steam Networking Messages (Spacewar, App ID 480). It is designed for hosts who cannot port-forward. The same JAR runs on the dedicated server and every joining client.

## Important architecture difference

BTA single-player does not run an integrated server and has no modern Open to LAN listener. The host therefore runs a normal BTA dedicated server. The server-side mod publishes its own listening port through Steam. Guests install the same mod and connect through a Steam lobby invitation or the server's `e4mc://...` address.

## Install

1. Install Better Than Adventure 8.0.1 with Fabric Loader `0.18.4-bta.11` and HalpLibe 6.1.4 or newer.
2. Put `e4BTA-0.6.0+bta-8.0.1.jar` in the dedicated server's `mods` folder.
3. Put the same JAR in every guest client's `mods` folder.
4. Start Steam and sign in on the server computer. The server process uses Steam's client API and therefore needs an interactive signed-in Steam session; it is not an anonymous SteamCMD server.
5. Start the dedicated server normally. After its TCP listener binds, the log prints `Steam tunnel ready` and an `e4mc://...` share address.
6. Send that address to a Steam friend. They can enter it as a BTA server address; no router port forwarding is required.

On the dedicated server, the mod creates `config/e4bta-server.properties`:

```properties
enabled=true
accessMode=FRIENDS_ONLY
```

The tunnel automatically uses `server-port` from `server.properties` or the server's `--port` argument. `FRIENDS_ONLY` permits the server Steam account's friends to use the copied address. `INVITE_ONLY` requires Steam lobby membership and is mainly useful when the server process has a working Steam overlay.

Clients create `config/e4bta.properties`. The same settings are available in BTA's Options menu under **e4BTA**:

```properties
autoHost=false
hostPort=25565
accessMode=FRIENDS_ONLY
autoStartSteam=true
```

Client-side hosting defaults to disabled because the dedicated server normally owns the tunnel. `autoStartSteam` controls whether e4BTA opens Steam when it is not already running. Guests can enter an `e4mc://...` value as the server address; BTA's port field is ignored for Steam addresses.

## e4BTA Voice

[e4BTA Voice](https://github.com/ThisIsVerse/e4BTA-Voice) is an optional proximity-voice addon. Version 0.6.0 provides the shared Steam voice and settings integration it requires. When the addon is installed, its voice toggle and hotkeys appear directly on the e4BTA options page, while V opens the full voice overlay in game.

## Build

The repository intentionally compiles the loader-independent Steam transport from `../common/src/main/java`; the BTA module contains only BTA-specific lifecycle, UI, configuration, and mixins.

If Signalum Maven is unavailable, the checked-in loader jar in `libs/` is the official Turnip Labs `0.18.4-bta.11` release. From the supplied BTA template directory:

```powershell
.\gradlew.bat -p .\e4bta clean build
```

The output is written to `build/libs/`.

## Current scope

- Windows and Linux x64 are supported by the bundled Steamworks natives inherited from e4steam.
- The Steam tunnel now runs directly inside the dedicated server; no host game client is required.
- The server needs a normal signed-in Steam desktop session. This implementation uses Spacewar's lobby/client APIs, not anonymous Steam GameServer authentication.
- BTA protocol compatibility is deliberately exact (`8.0.1`); modern Minecraft e4steam clients cannot join BTA hosts.
- Status, the share address, and failures are printed in the dedicated-server log. The mod does not reproduce modern Minecraft's Open to LAN screen because BTA has no integrated server to open.
