# TurboGolem v1.2

[English](#english) | [中文](#chinese)

---

<h2 id="english">🇬🇧 English</h2>

Turbo-charge copper golems on your Paper 26.2 server. Boost sorting speed, enable multi-chest parallel processing, and deploy stationary super golems for instant item classification.

### Quick Example

```
1. Place a copper chest on the ground
2. Stand on it and type: /supergolem 16
3. Put items into the copper chest
4. Watch them flow to nearby chests/hoppers
```

### Features

#### Regular Copper Golems
| Feature | Vanilla | TurboGolem |
|---------|---------|------------|
| Scan interval | ~7s | **3s** |
| Items/transfer | 16 | **8** (1-64) |
| Chests/scan | 10 | **3** (configurable) |
| Target containers | Chests | All containers |
| Oxidation states | ❌ | ✅ All + waxed |

#### Super Golems (`/supergolem`)
- Stationary, killable, no collision
- 0.5s scan, custom radius
- 10s particle trails

### Commands

| Command | Description |
|---------|-------------|
| `/supergolem [radius]` | Spawn super golem |
| `/turbogolem reload` | Hot-reload config |
| `/turbogolem info` | Show status |
| `/turbogolem scan` | Force scan |
| `/turbogolem killgolems` | Remove all super golems |

### Permissions

| Permission | Default |
|------------|---------|
| `turbogolem.admin` | `op` |

### Install

1. Drop `TurboGolem-v1.2.jar` into `plugins/`
2. Restart or `/plugman load TurboGolem`
3. Config at `plugins/TurboGolem/config.yml`

### Requirements

- Paper 1.21.4 / 26.2+
- Java 25+

---

<h2 id="chinese">🇨🇳 中文</h2>

为 Paper 26.2 服务器加速铜傀儡。提升分拣速度、支持多箱子并发处理、部署固定式超级铜傀儡实现物品瞬间分类。

### 快速上手

```
1. 在地上放一个铜箱子
2. 站上去输入：/supergolem 16
3. 往铜箱子里丢物品
4. 观察物品自动流到周围的箱子/漏斗里
```

### 特性

#### 普通铜傀儡
| 特性 | 原版 | TurboGolem |
|------|------|------------|
| 扫描间隔 | ~7秒 | **3秒** |
| 单次搬运 | 16个 | **8个**（可调1-64） |
| 处理箱子数 | 10个 | **3个**（可配置） |
| 目标容器 | 仅箱子 | 全容器 |
| 氧化铜支持 | ❌ | ✅ 所有状态+涂蜡 |

#### 超级铜傀儡 (`/supergolem`)
- 固定不动、可击杀、无碰撞
- 0.5秒扫描、自定义半径
- 10秒粒子拖尾特效

### 命令

| 命令 | 说明 |
|------|------|
| `/supergolem [半径]` | 生成超级铜傀儡 |
| `/turbogolem reload` | 热重载配置 |
| `/turbogolem info` | 查看状态 |
| `/turbogolem scan` | 强制扫描 |
| `/turbogolem killgolems` | 清除所有超级铜傀儡 |

### 权限

| 权限 | 默认 |
|------|------|
| `turbogolem.admin` | `op` |

### 安装

1. 把 `TurboGolem-v1.2.jar` 放入 `plugins/` 文件夹
2. 重启服务器或 `/plugman load TurboGolem`
3. 配置文件自动生成在 `plugins/TurboGolem/config.yml`

### 环境要求

- Paper 1.21.4 / 26.2+
- Java 25+

### 设计理念

和朋友一起玩高版本插件生存服，物品种类越来越复杂——矿物、建材、农田产出、红石组件、食物。原版铜傀儡搬运太慢，完全跟不上工业化分类的节奏。TurboGolem 让它成为自动化物流的核心引擎。

---

<p align="center">Made with ❤️ by alpaca_yyy · <a href="https://mc.alpacayyy.top">mc.alpacayyy.top</a></p>
