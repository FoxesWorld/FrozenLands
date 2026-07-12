# Take Some() Frozen Lands

![Java](https://img.shields.io/badge/Java-17%2B-darkorange?style=for-the-badge)
![jMonkeyEngine](https://img.shields.io/badge/jMonkeyEngine-3.9.0--stable-blue?style=for-the-badge)
![Gradle](https://img.shields.io/badge/Gradle-wrapper-02303A?style=for-the-badge)
![Status](https://img.shields.io/badge/status-alpha-yellow?style=for-the-badge)

**Frozen Lands** is a Take Some() Java / jMonkeyEngine game-runtime prototype: a cold open-world foundation with terrain, player control, physics, particles, save/runtime modules, provider systems, and a Java-backed Lua scripting bridge.

This repository is an **alpha development workspace**, not a finished game release. It is intended for engine/runtime development, gameplay-system prototyping, and modular game architecture work.

<img src=".github/Screenshot_4.png" height="533" width="800" alt="Frozen Lands runtime screenshot"/>

---

## Project status

Frozen Lands is currently focused on building the runtime base:

- 3D application bootstrap on top of jMonkeyEngine.
- Modular engine architecture through `modulesSrc`.
- Runtime command/event ABI for systems and providers.
- Player, terrain, particle, shader, world, and save modules.
- Lua scripts executed through a single Java core bridge.
- Manifest diagnostics for validating registered modules and commands.

The codebase changes quickly. Public releases, packaging, and stable modding contracts are not finalized yet.


---

## Repository timeline

| Marker | Date | Elapsed at this public refresh |
|---|---:|---:|
| Project birth / first repository commit | `2023-09-26 13:37:59 +03:00` (`29a1654`, `Logger Fix`) | `2 years, 8 months, 29 days, 19 hours, 55 minutes` |
| Previous published remote baseline before this refresh | `2023-09-26 13:37:59 +03:00` (`origin/main`, `29a1654`) | `2 years, 8 months, 29 days, 19 hours, 55 minutes` |
| Public README/runtime refresh timestamp | `2026-06-25 09:32:53 +03:00` | current snapshot |

---

## Screenshots

| Preview | Image |
|---|---|
| Runtime scene | <img src=".github/Screenshot_1.png" width="420" alt="Frozen Lands screenshot 1"/> |
| Terrain prototype | <img src=".github/Screenshot_2.png" width="420" alt="Frozen Lands screenshot 2"/> |
| World prototype | <img src=".github/Screenshot_3.png" width="420" alt="Frozen Lands screenshot 3"/> |
| Current public preview | <img src=".github/Screenshot_4.png" width="420" alt="Frozen Lands screenshot 4"/> |

---

## Technology stack

| Area | Technology |
|---|---|
| Language | Java |
| Build system | Gradle wrapper |
| Main class | `org.takesome.frozenlands.FrozenLands` |
| Engine runtime | jMonkeyEngine `3.9.0-stable` |
| Physics | Minie / Bullet |
| UI | Lemur / Lemur Proto |
| ECS / game infrastructure | SiO2, Zay-ES |
| Serialization | Jackson, Gson |
| Lua runtime | LuaJ JSE |
| Logging | Log4j / SLF4J |

---

## Repository layout

```text
.
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── core/src/main/java/org/takesome/frozenlands
│   ├── FrozenLands.java
│   └── engine
│       ├── Kernel.java
│       ├── EngineContext.java
│       ├── bootstrap
│       ├── config
│       ├── lua
│       ├── modules
│       ├── resources
│       └── world
├── assets/src/main/resources
│   ├── Models
│   ├── MatDefs
│   ├── sounds
│   ├── textures
│   ├── themes
│   └── ui
└── modulesSrc
    ├── bootstrap
    ├── core
    ├── particles
    ├── player
    ├── provider-core
    ├── provider-material
    ├── provider-model
    ├── provider-sound
    ├── save
    ├── shaders
    ├── terrain
    └── world
```

`modulesSrc` is part of the production source graph. Each module can contain Java sources, Lua API files, runtime config, and assets. Gradle discovers module Java source directories from `module.index.json` files.

---

## Runtime modules

Frozen Lands uses a command/event ABI around `ModuleRegistry` and `ProviderRegistry`.

| Module ID | Purpose |
|---|---|
| `engine.bootstrap` | Bootstrap configuration. |
| `engine.core` | Core runtime bridge, console routing, Lua script execution. |
| `engine.providers` | Provider registry descriptor exposed to Lua. |
| `engine.material` | Material loading and lookup. |
| `engine.model` | Model loading, attach/detach, and lookup. |
| `engine.sound` | Sound loading and playback. |
| `engine.world` | World runtime commands and spawn facade. |
| `engine.terrain` | Terrain chunks, height queries, spawn location. |
| `engine.shaders` | Post-processing and shadow settings. |
| `engine.particles` | Snow, transient particle effects, impacts. |
| `engine.player` | Player status, position, and warp commands. |
| `engine.save` | Snapshot, save, load, and save listing. |

---

## Lua scripting

Lua is embedded as an engine scripting layer. Scripts run through `engine.core` and call Java systems through one bridge.

Core script commands:

```text
engine.core script.manifest
engine.core script.list
engine.core script.read
engine.core script.run
engine.core script.autorun
```

Default script config:

```text
modulesSrc/core/assets/config/runtime.json
```

Default startup script:

```text
modulesSrc/core/assets/scripts/startup.lua
```

Example:

```lua
local core = require("engine.core")

core.emit("core.script.loaded", {
  script = script.name,
  path = script.path,
  autorun = args.autoRun == true
})

return true
```

Module facade example:

```lua
local particles = require("engine.particles")

particles.call("snow.enable", { enabled = true })
particles.call("snow.rate", { rate = 450 })
```

The embedded Lua runtime is intentionally sandboxed. Direct `io`, `os`, `debug`, `luajava`, `dofile`, and `loadfile` access is disabled.

---

## Requirements

- JDK 17 or newer.
- The Gradle wrapper included in this repository.
- Required Take Some() local Maven artifacts installed in Maven Local until all internal runtime packages are published publicly.
- Windows is the currently validated desktop development target for this workspace.

---

## Build and run

Build the project:

```bat
gradlew.bat clean build
```

Run the desktop application:

```bat
gradlew.bat run
```

On Unix-like systems, use `./gradlew` instead of `gradlew.bat` if all required runtime dependencies and native libraries are available for the platform.

---

## Runtime diagnostics

Frozen Lands includes a manifest reporter for checking registered providers, modules, and required commands.

Use these VM options for a manifest diagnostic run:

```text
-Dfrozenlands.runtimeManifest=true
-Dfrozenlands.runtimeManifestExit=true
```

The diagnostic output is useful after changing modules, providers, Lua API files, or the runtime command ABI.

---

## Development notes

- Keep `module.index.json` in sync with module assets, configs, and Lua API files.
- Keep Java package names under `org.takesome.frozenlands`.
- Treat `modulesSrc` as runtime source, not as a scratch directory.
- Keep module commands stable once Lua scripts or tools depend on them.
- Use `engine.core.script.run` for executing indexed Lua scripts instead of bypassing the bridge.

---

## License

No public license has been declared yet. Until a license is added, all rights are reserved by the repository owner.


## HFrame integration

FrozenLands can optionally load `org.takesome:hframe-core:0.1.0-SNAPSHOT` from Maven Local and run a short HFrame native startup probe with `-Dfrozenlands.hframe.enabled=true`. See `docs/HFRAME_INTEGRATION.md`.
