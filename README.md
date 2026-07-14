# TurboGolem v1.1

Turbo-charge copper golems on your Paper 26.2 server — faster multi-chest sorting, larger batches, and stationary super golems.

## Features

### Regular Copper Golems
- **2x sorting speed** — scans every 3s instead of ~7s
- **Larger batches** — configurable items per transfer
- **Extended chest detection** — up to 10 copper chests per scan
- **Supports all container types** — chests, hoppers, barrels, droppers, dispensers, shulker boxes
- **All oxidation states** — works with normal, exposed, weathered, and oxidized copper chests
- **Visual feedback** — happy villager particles during sorting

### Super Golems (`/supergolem`)
- Stationary, invulnerable, no-collision copper golems
- Instant sorting from the copper chest directly beneath
- Customizable search radius per golem
- Separate particle effects (electric spark on source, happy villager on destination)
- 0.5s scan interval for ultra-fast sorting

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/turbogolem info` | `turbogolem.admin` | Show plugin status and tracked golems |
| `/turbogolem scan` | `turbogolem.admin` | Force a manual scan cycle |
| `/turbogolem reload` | `turbogolem.admin` | Hot-reload configuration |
| `/supergolem [radius]` | `turbogolem.admin` | Spawn a stationary super golem |

## Installation

1. Download `TurboGolem-v1.1.jar`
2. Place in your server's `plugins/` folder
3. Restart or load with PlugMan: `/plugman load TurboGolem`
4. Configuration auto-generates at `plugins/TurboGolem/config.yml`

## Requirements

- **Paper 1.21.4 / 26.2** (uses Copper Golem from The Copper Age update)
- Java 25+

## Configuration

See `config.yml` for all options with detailed comments. Hot-reload with `/turbogolem reload`.

## Building from Source

```bash
javac -cp paper-api-26.2.jar:adventure-api.jar:... \
  -sourcepath src \
  -d . src/com/alpaca/turbogolem/TurboGolem.java
jar cf TurboGolem.jar plugin.yml config.yml -C . com/
```

## License

MIT — do whatever you want, attribution appreciated.

---

Made with ❤️ by alpaca_yyy · [mc.alpacayyy.top](https://mc.alpacayyy.top)
