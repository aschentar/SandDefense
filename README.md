# SandDefense

An addon for [ProjectKorra](https://projectkorra.com) that adds four defensive and movement abilities for Sandbenders.

[![Status: Beta](https://img.shields.io/badge/status-beta-yellow.svg)](#known-issues)
[![License: LGPL v2.1](https://img.shields.io/badge/license-LGPL--2.1-blue.svg)](LICENSE)
[![Server: Purpur 1.21.11](https://img.shields.io/badge/tested%20on-Purpur%201.21.11-green.svg)](https://purpurmc.org)

[GIF soon]

SandDefense is a beta addon. I tested it on my own server running Purpur 1.21.11, but not every situation. If something breaks, please open an [issue](https://github.com/aschentar/SandDefense/issues). Follow the repository for updates.

I have played a lot on bending servers, and Earthbending is my favorite element. I am also developing my own bending-themed project, and I want to keep building in this direction. I hope you like SandDefense. If you have suggestions, get in touch through [Issues](https://github.com/aschentar/SandDefense/issues) or by email: Aschentar@macaw.me.

## Abilities

| Ability | How to use |
| :--- | :--- |
| **SandArmor** | Bind it and select its slot, then sneak while standing on sand or sandstone. You get sand armor, Speed and Resistance for 30 seconds. |
| **SandShield** | Hold sneak to raise a dome that blocks projectiles. Left-click to throw a disc that pushes entities away and deflects attacks coming straight at you. |
| **DustVeil** | Left-click to raise a curved wall of dust. Projectiles lose speed when they cross it, and entities that walk through get blindness and slowness. |
| **DuneDrift** | Left-click on sand to dash. Sneak to start or stop surfing across sand, red sand and sandstone. |

### Getting started

The abilities belong to the Sand subelement of Earthbending, so a player needs Earthbending and Sand to use them. Bind an ability to a slot with `/bending bind <ability>`, for example `/bending bind SandArmor`. `/bending help <ability>` shows what each one does.

Cooldowns, durations and speeds are currently set in the code and cannot be changed without rebuilding. A config file is planned for a later version.

## Requirements

* Java 21 or newer
* Paper, Purpur or a fork of them (tested only on Purpur 1.21.11)
* [ProjectKorra](https://projectkorra.com) 1.12.0 (the version I tested with)
* Optional: [BendingGUI](https://github.com/StrangeOne101/BendingGUI)

## Installation

1. Make sure ProjectKorra is installed and working.
2. Download the jar from the [Releases](https://github.com/aschentar/SandDefense/releases) page.
3. Put it in your server's `plugins/` folder.
4. Restart the server.

### BendingGUI descriptions (optional)

If you use BendingGUI, add this to `plugins/BendingGUI/language.yml`:

```yaml
Abilities:
  SandArmor: "Encase yourself in sand armor for extra speed and damage resistance."
  SandShield: "Raise a protective dome of sand, or throw a disc that deflects incoming attacks."
  DustVeil: "Raise a curved wall of dust that slows projectiles and blinds enemies who cross it."
  DuneDrift: "Dash across the sand and surf the dunes."
```

Then reload BendingGUI with `/gui reload`, in game or from the console.

## Known issues

No bugs found so far. Settings are not configurable yet (see above). If you find a problem, please report it in [Issues](https://github.com/aschentar/SandDefense/issues).

## Building from source

You need JDK 21.

```bash
git clone https://github.com/aschentar/SandDefense.git
cd SandDefense
./gradlew clean build
```

The jar will be in `build/libs/`.

## Credits

**DuneDrift** is based on the original ability by [Wehilante](https://projectkorra.com/forum/members/wehilante.51905/), published by him as a public addon and used here with his permission. I modified it: changed the movement code, added the collapsing-sand particles, and adjusted speeds, cooldowns and the way surfing stops.

## License

Copyright (C) 2026 aschentar. Released under the GNU Lesser General Public License v2.1, see [LICENSE](LICENSE).
