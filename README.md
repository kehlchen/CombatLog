# CombatLog

A lightweight and highly configurable **combat log prevention plugin** for Paper 26.3.

Hit a player and you're both **in combat**. Log out while the timer is running and you die. No more escaping fights by closing the game.

## Features

- **Combat tagging** – attacker and victim are tagged on PvP damage (melee, arrows, tridents, other projectiles and lingering potion clouds). Each side can be toggled separately.
- **Punishment on logout** – players who disconnect in combat are killed, with an optional lightning effect and a custom broadcast message. You can also run console commands on combat log (`%player%`, `%opponent%`), e.g. to charge a fine.
- **Command blocking** – block all commands while in combat (with an allow list) or only specific commands (blacklist). Aliases and `minecraft:` prefixes are detected automatically.
- **Movement restrictions** – each one can be switched on or off in the config:
  - Flying
  - Elytra gliding
  - Ender pearls (the pearl stays in your inventory)
  - Chorus fruit
  - Nether/End portals
  - Any other teleport cause
- **Live timer** – action bar and/or boss bar countdown, plus start/end sounds.
- **Smart handling** – kicks, timeouts and server shutdowns can be excluded from punishment; killing your opponent ends your combat immediately.
- **Fully customizable messages** using [MiniMessage](https://docs.advntr.dev/minimessage/format.html).
- **World filter** – enable or disable combat per world (whitelist or blacklist).
- Ignores creative/spectator players and NPCs (optional).

## Commands

| Command | Description |
|---|---|
| `/combatlog reload` | Reload the config |
| `/combatlog status [player]` | Show combat status |
| `/combatlog tag <player> [seconds]` | Put a player in combat |
| `/combatlog untag <player>` | Remove a player from combat |
| `/combatlog list` | List all players currently in combat |

Alias: `/combat`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `combatlog.admin` | Use `/combatlog` | OP |
| `combatlog.bypass.tag` | Never gets tagged | – |
| `combatlog.bypass.logout` | No punishment on logout | – |
| `combatlog.bypass.commands` | May use commands in combat | – |
| `combatlog.bypass.restrictions` | May fly, glide and teleport in combat | – |
| `combatlog.bypass.*` | All of the above | – |

## Configuration

Everything is configured in `plugins/CombatLog/config.yml`: combat duration, timer refresh on hit, minimum damage, command mode, restrictions, displays, sounds and all messages. Use `/combatlog reload` to apply changes without restarting.

## Requirements

- Paper 26.3
- Java 25

**Note:** with the `keepInventory` gamerule enabled, players killed for combat logging won't drop their items.
