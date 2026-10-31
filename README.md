# Additional Pipes for BuildCraft Community Edition

## About the project

Additional Pipes for BuildCraft Community Edition is a community effort to bring the classic **Additional Pipes** addon to modern Minecraft versions supported by **BuildCraft Community Edition (BCCE)**.

The goal is to restore the addon's additional pipe and transport mechanics while keeping their original gameplay identity and integrating them with BCCE's modern systems.

**Development status:** Several classic pipes are implemented through BCCE API v2, including Distribution and Water Pump. Other original gameplay features are still being ported. All seven target builds are configured; individual in-game functionality should be tested on the matching BCCE release.

## Supported versions

| Target | Loader |
| --- | --- |
| `1.19.2-forge` | Forge |
| `1.20.1-forge` | Forge |
| `1.21.1-neoforge` | NeoForge |
| `1.21.11-neoforge` | NeoForge |
| `26.1.2-neoforge` | NeoForge |
| `26.2-neoforge` | NeoForge |
| `26.3-neoforge` | NeoForge |

These are the configured build targets. Gameplay support will be available as the port progresses.

## BCCE API v2

Additional Pipes is being developed as a separate **BCCE addon** using the loader-neutral **API v2 (2.1)**.

The addon depends on BCCE's `buildcraftlib` and `buildcrafttransport` modules. BuildCraft is not bundled into Additional Pipes; the matching BCCE version must be installed separately.

Shared gameplay code will use public API contracts wherever possible, while Forge- and NeoForge-specific integration stays in platform-specific sources.

### Build dependency (Maven)

Gradle downloads the matching BCCE release directly from the **[Modrinth Maven repository](https://support.modrinth.com/en/articles/8801191-modrinth-maven)**. You do **not** need to download BCCE JARs, run an import script, or populate a `local-deps` directory.

The pinned BCCE version and repository are configured in `build-config/common.properties`:

```properties
common.bcce.version=8.0.24
common.bcce.repository=https://api.modrinth.com/maven
common.bcce.group=maven.modrinth
common.bcce.module=z3Aj5eOl
```

Every Gradle subproject selects its own *Minecraft version + loader* publication. For example, `1.20.1-forge` uses `maven.modrinth:z3Aj5eOl:8.0.24+1.20.1+forge`, while `26.3-neoforge` uses `maven.modrinth:z3Aj5eOl:8.0.24+26.3+neoforge`. The dependency is mapped by ForgeGradle for Forge targets. NeoForge targets add it to the compile classpath and development runtime without embedding it in the addon JAR.

To build all seven targets, run `./build-all.sh` on Linux/macOS or `./build-all.ps1` on Windows. Builds run independently for the three Stonecutter generations. CI also resolves BCCE from Maven and requires no repository secrets, variables, or preinstalled BCCE archives.

To test a newer BCCE release without editing the pinned version, pass `-Pbcce.version=8.0.25` to a generation's Gradle wrapper. This works only when matching BCCE files have been published for **every** target being built. The installed mod must still match the target's Minecraft version and loader.

The Modrinth Maven serves the published BCCE mod JAR, not a standalone API-only artifact, and does not provide transitive dependency metadata. BuildCraft remains an **external required runtime mod** (`buildcraftlib`, `buildcrafttransport`, etc.) and is never bundled into Additional Pipes.

## Multi-version build and source architecture

Like BCCE, Additional Pipes uses three Stonecutter/Gradle build generations:

- **old** — Minecraft 1.19.2 and 1.20.1;
- **1.21.X** — Minecraft 1.21.1 and 1.21.11;
- **26.X** — Minecraft 26.1.2, 26.2 and 26.3.

Each generation has its own build configuration and Gradle Wrapper under `builds/<generation>`.

Every target is assembled from five source layers, with later layers able to override earlier ones:

```text
source-shared
+ source-families/<family>
+ source-platforms/<loader>
+ source-family-platforms/<family>/<loader>
+ version-src/<target>
```

Shared code belongs in `source-shared`, while differences between Minecraft generations and mod loaders are isolated in their respective layers. Target configuration is centralized in `build-config/targets.properties`.

The goal is consistent player-visible behaviour across versions, even where Minecraft or loader changes require different implementations.

## Issue reports

Please use the repository issue tracker when reporting bugs or suggesting features.

For bug reports, include:

- the Minecraft version;
- the mod loader and its version;
- the Additional Pipes and BCCE versions;
- clear reproduction steps;
- relevant logs, including a crash report for crashes.

## Credits

### Original Additional Pipes

- [GitHub](https://github.com/tcooc/AdditionalPipesBC)

Special thanks to **Zeldo**, **DaStormBringer**, and the original Additional Pipes contributors for their work on the addon.

## Forge 1.19.2 creative-tab registration

On this target, BCCE's legacy `ItemPipeHolder` is hard-coded to the BuildCraft Pipes creative tab.
Additional Pipes uses a single subclassed item per API v2 pipe type and associates that **same registered item**
with BCCE's `PipeRegistry`. Do not call `BuildCraftServices.PIPES.createItem()` before constructing the subclass:
that creates an additional unregistered `Item` and Forge fails during registry freeze with
`Some intrusive holders were not added to registry`. The exception is specific to item identity, not pipe type IDs.

The test `scripts/tests/test_creative_tab_1192.py` models Forge intrusive-holder tracking and ensures
all 14 API pipe items are registered exactly once and stay in the Additional Pipes tab.

## Distribution Pipe and Water Pump Pipe

Both items use their original Additional Pipes IDs and are registered as **real BCCE pipe types** rather
than decorative blocks. Existing recipes, textures and the Additional Pipes creative tab are preserved.

### Distribution Pipe (`additionalpipes:pipe_items_distribution`)

- Right-click the pipe to configure its **six directional quotas** (DOWN, UP, NORTH, SOUTH, WEST, EAST).
  Use the `+`/`-` buttons to adjust the number of items directed to each side, up to 4096.
- Incoming stacks are routed to **one enabled side at a time** in cyclic order. Counts are measured
  in items, not in travelling stacks: a stack of 8 consumes 8 units of that side's quota.
- A quota of zero disables an output. Outputs that are disconnected are skipped. At least one
  nonzero quota must remain. The selected output and counter survive world saves/reloads.
- The GUI uses server-authoritative updates; only players near the actual pipe can edit its quotas.

### Water Pump Pipe (`additionalpipes:pipe_fluids_water_pump`)

- Place directly **above a still source block of vanilla water**. Each server tick it inserts
  water into its BCCE fluid flow from below, subject to available pipe capacity.
- Default output **90 mB/tick**, configurable with `waterPumpWaterPerTick` in Additional Pipes' config.
- Does not consume the water source and does not require MJ/FE. The downward face is an intake,
  not a pipe/tank connection.

### Validation

`python3 -m unittest discover -s scripts/tests -p 'test_*.py'` executes service, packet,
content-registration, Distribution quota and mocked Water Pump runtime checks.
`python3 scripts/validate_targets.py` checks source/layout consistency for the seven targets.
These checks are **not a substitute** for building with the full Minecraft/Forge/NeoForge toolchains
and testing both pipes in-game.

## GUI text rendering

All Additional Pipes configuration/container screens draw text without shadows on the light
panel backgrounds. In Forge 1.19.2, using the legacy `GuiComponent.drawString` would draw a
dark offset shadow, making small captions look like duplicated/overlapping glyphs. The old
screens now call `Font.draw` directly; 1.20+ keeps shadow-free `GuiGraphics.drawString`
and 26.X keeps shadow-free `GuiGraphicsExtractor.text`.

`GuiTextLayout.fit` also bounds single-line titles, inventory captions and Teleport ledger
labels by measured font pixels. Longer translations show `...` instead of drawing over
buttons or outside the panel. Run `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`
for the GUI text regression alongside the existing functional tests.
