# Additional Pipes for BuildCraft Community Edition

## About the project

Additional Pipes for BuildCraft Community Edition is a community effort to bring the classic **Additional Pipes** addon to modern Minecraft versions supported by **BuildCraft Community Edition (BCCE)**.

The goal is to restore the addon's additional pipe and transport mechanics while keeping their original gameplay identity and integrating them with BCCE's modern systems.

**Development status:** The multi-version build infrastructure is in place, but the original pipes and other gameplay features have not been ported yet.

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
