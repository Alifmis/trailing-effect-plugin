# TrailingEffectPlugin

A Folia-compatible Minecraft plugin (1.21.11) that adds configurable particle trailing effects to players as they move.

## Commands

```
/trailing <player> add    <particle>   — assign a trail to a player
/trailing <player> delete <particle>   — remove a trail from a player
```

- **Requires OP permission** (or console).
- Full **tab-completion** for player names, `add`/`delete`, and particle names.
- The `delete` subcommand only suggests particles currently active on the target player.

## How It Works

- Each player can have **multiple concurrent trails** (one particle type each).
- Trails are **persisted** across server restarts in `plugins/TrailingEffectPlugin/trails.yml`.
- Particles spawn on every **block movement** (ignoring head-rotation only).
- Uses `PlayerMoveEvent` — particles fire on the correct Folia region thread.

## File Structure

```
src/main/java/org/alifmis/trailingeffectplugin/
├── TrailingEffectPlugin.java       # Plugin lifecycle & registration
├── command/
│   └── TrailingCommand.java        # Brigadier command with tab-completion
├── manager/
│   └── TrailManager.java           # Player→Particle mapping + YAML persistence
└── listener/
    └── PlayerMoveListener.java     # Spawns particles on movement
```

## Building

```bash
./gradlew build
```

JAR is produced at `build/libs/trailing-effect-plugin-1.0-SNAPSHOT.jar`.

## Running Locally

```bash
./gradlew runServer
```

The `run-paper` plugin downloads and starts a Minecraft server with your plugin loaded.

## Dependencies

- Paper API `1.21.11-R0.1-SNAPSHOT` (also runs on Folia)
- Java 21
- Gradle (wrapper included)

## Configuration

No extra configuration is needed. Trails are stored automatically in:
```
plugins/TrailingEffectPlugin/trails.yml
```

You can edit this file while the server is offline to bulk-add/remove trails.

## Available Particles

Any `Particle` enum value works. Common examples:

- `flame`, `heart`, `note`, `portal`, `enchanted_hit`, `dripping_water`, `soul_fire_flame`, `witch`, `happy_villager`, `cloud`, `dust`, `end_rod`, `glow`, `sculk_soul`, `totem_of_undying`, `electric_spark`, `dripping_obsidian_tear`, `cherry_leaves`, `spore_blossom_air`

Run `/trailing <player> add <Tab>` in-game to see the full list.
