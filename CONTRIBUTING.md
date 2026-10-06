# Development

## Modules

- `neoforge-1.21.1`: Minecraft 1.21.1, NeoForge, JDK 21.
- `forge-1.20.1`: Minecraft 1.20.1, Forge, JDK 17.

Each module has its own Gradle wrapper and is built independently. Set `JAVA_HOME` to the appropriate JDK before running commands. Use `./gradlew` instead of `.\gradlew.bat` on Linux or macOS.

From the module directory:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Release JARs are written to `build/libs/`. Keep build outputs, local Gradle caches and development worlds out of Git.

## Code structure

Sources use `com.archiangel.quadhotbar`. Client-only screens, input handling and rendering live in `client`; Minecraft hooks live in `mixin` and are registered in `quadhotbar.mixins.json`. Config, networking, server menus and player storage live in the base package.

Never eagerly load client GUI classes on a dedicated server. Keep changes to network codecs compatible with the declared protocol, or explicitly version the protocol when changing the wire format.

Both modules use inventory network protocol 11. Hotbar snapshots include up to three inventory pages to cover eight visible hotbars, and container overviews use a separate server-authoritative session while leaving the original menu open. Forge transports packets through SimpleChannel; `quadhotbar:protocol` negotiates the schema, while the recognizable legacy `quadhotbar:main` envelope lets old clients reach the short login rejection. Update both client and server when testing against older beta builds.

To run the optional Corpse integration tests, add `-PcompatCorpseJar=<absolute path to a matching Corpse JAR>` to `runGameTestServer` or `runClient`. Use NeoForge 1.21.1 fixtures with NeoForge and Forge 1.20.1 fixtures with Forge. The dependency is development-only and is not bundled. Death inventories store slot-address metadata only; the grave/corpse mod remains the sole owner of the actual item stacks.

GraveStone's death-storage and original-slot recovery checks use `-PcompatGravestoneJar=<absolute path to a matching JAR>`. Test the two death mods separately: both handle death drops, so enabling both is not a substitute for verifying either integration. Corpse has an inventory overview adapter. Both mods' native recovery restores items to their original inventory pages and slots, including hidden pages without enabling them. Occupied slots are handled like native recovery: displaced items fill available enabled slots; overflow remains in the corpse or is returned to GraveStone for its native drop handling. Old death inventories without page metadata keep their native behavior.

Player inventory pages are server-owned and saved with the player in that world. Reducing visible pages must not erase stored items. Changes to inventory handling must preserve the cursor stack, avoid duplicate storage, and respect death and `keepInventory` behavior.


## Server permissions and client preferences

Installed clients with incompatible QuadHotbar network protocols are rejected during the NeoForge configuration handshake or Forge login handshake, before registry/player/inventory synchronization. This includes the 1.0.x releases and older incompatible 1.1.0 beta builds. The disconnect uses Minecraft's ordinary screen, names the server's actual mod version, and includes an English/Russian fallback so older clients do not need the new translation keys. Operator/whitelist exemptions never bypass protocol checks. Clients advertising no QuadHotbar channels retain the existing optional-mod negotiation behavior; unrelated mods keep the loader's own negotiation checks. Forge also stops current clients from joining old QuadHotbar servers before their incompatible packet schemas can interact.

Edit `config/quadhotbar-server.toml` with filesystem access to the server (or the local world's host), then restart the server/world. No in-game command edits this policy. On Forge this host-owned file is deliberately registered as COMMON, not as an automatically synced/world-writable SERVER config: only each player's effective permissions are sent to their client. Operators bypass global restrictions by default; set `exemptOperators = false` to change that. The nickname whitelist grants the same exemption independently of operator status. These are permission exemptions, not extra items, and never change ownership or world storage. `deathPageCompatibility` remains a world-wide integration setting.

The lists are empty by default. Uncomment the examples and replace Player1/Player2 with actual usernames to grant exemptions and then apply individual restrictions:

```toml
maxInventoryPages = 2
maxHotbarRows = 4
exemptOperators = true
unrestrictedPlayers = []
playerOverrides = []
# Example: unrestrictedPlayers = ["Player1", "Player2"]
# Example: playerOverrides = ["Player1; maxInventoryPages=2; maxHotbarRows=4", "Player2; allowPageOverview=false"]
```

Names are case-insensitive. Rules apply **after** operator/whitelist exemption; unspecified keys retain that player's defaults. Rules can also target non-whitelisted players. Repeated rules for the same name are processed in list order; later keys override earlier ones. Accepted keys are `allowFreedom`, `allowPageOverview`, `showOverviewButton`, `allowContainerOverview`, `overviewLayout` (`CLIENT`, `DOCKED`, `STANDALONE`), `maxInventoryPages` (1–100), `maxHotbarRows` (2–8), `maxOverviewWidth` (9, 18 or 27), and `maxOverviewHeight` (1–50). Full-hotbar-page alignment still applies. Invalid rules are rejected by config validation. Exemptions use the server's authenticated player name and operator status; on an offline-mode server, nickname-based trust is only as strong as that server's authentication setup.

Client preferences live in the root keys of `config/quadhotbar-client.toml` and apply both in singleplayer and on remote servers. Joining a server never selects a default profile or overwrites these values. Server policy temporarily limits effective settings; disconnecting removes those limits and restores the personal settings. Saving the settings screen preserves personal preferences, not server-imposed values. The old `[multiplayer]` section is retained for compatibility but is no longer used. Keybinds remain shared in Minecraft Controls; items always belong to the player in the current world. LAN guests use their own personal preferences under the host's server policy.

## Checks

The inventory page overview is enabled by default, but requires Freedom and at least two effective inventory pages. Its master toggle stays available when the overview itself is disabled; dependent controls are disabled without erasing their preferences. With `autoOpenOverview` enabled, `overviewLastOpen` remembers a manual panel close/open across inventory openings and game restarts. The inventory key and Escape close the whole inventory without pausing auto-open; only the panel's close button or separate overview binding hide just the panel. The inventory key takes priority if both bindings overlap. External chest/death-container panels and server-forced closes do not change this preference.

Overview width is stored in nine-slot increments (9, 18, 27), for docked and standalone windows alike. Effective GUI scale 1 permits 27; scale 2 permits 18; scale 3 or larger permits only 9. Auto uses its resolved scale, and screen fit and server width limits still apply. Temporary display limits do not overwrite the saved preference. Pages fill rows by default; column-first order and starting at the right are client preferences. Layout only changes visual coordinates, never page identity or item storage.

Regression fixtures in both modules live in `src/gametest/java` and are excluded from release JARs:

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient -PoverviewSmoke=true
.\gradlew.bat runClient -PoverviewSmoke=true -PoverviewRestartCheck=true
```

The client smoke test creates a disposable development world and exercises overview interactions in creative and survival. The restart check must follow a successful smoke run: it compares the loaded window position and wheel scroll step with the settings recorded by that run. These tests use the module's `run/client` directory, not a launcher instance.

Both modules have server regression tests and a real-client smoke run. Optional death-mod checks only execute when their fixture is installed; run Corpse and GraveStone separately. Forge fixtures use an in-memory server policy to isolate tests from its COMMON config file watcher. Verify the reobfuscated Forge release JAR on a dedicated server as well as the development runtime.

## Style

Use four-space indentation and the repository's `.editorconfig`. Java sources are formatted with [google-java-format 1.24.0](https://github.com/google/google-java-format/releases/tag/v1.24.0) in AOSP mode (`--aosp`). Keep comments focused on invariants and non-obvious Minecraft behavior. New visible text needs matching keys in `en_us.json` and `ru_ru.json`; slot and layout keys may be constructed dynamically.

Keep each behavioral change separate from broad formatting or architectural changes where practical. Do not remove template attribution or license notices as part of cleanup.
