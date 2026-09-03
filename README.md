# Atla Mod

A Minecraft mod that adds Avatar-style **elemental bending** — four elements, seven
sub-elements, and 107 abilities behind a progression tree you unlock as you play.

Bending here is not a set of spells on a hotbar. Each element has its own resource
rules, its own way of moving through the world, and its own idea of what a fight looks
like. A firebender fights at range and sets the ground alight. A waterbender has to
find water. An earthbender rearranges the terrain and gives it back afterwards. A
bloodbender can only be stopped by another bloodbender.

![Build](https://github.com/Niilo411/atla-mod/actions/workflows/build.yml/badge.svg)

> **Status:** in active development. Playable and feature-complete across all four
> elements and all seven sub-elements, but not yet released on Modrinth and not yet
> versioned for public consumption. Expect rough edges.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248+ |
| Java | 21+ |

## Installation

1. Install [NeoForge 21.1.248+](https://neoforged.net/) for Minecraft 1.21.1.
2. Download `atlamod-<version>.jar` from
   [Releases](https://github.com/Niilo411/atla-mod/releases).
3. Drop it into your `mods` folder.
4. Launch the NeoForge profile.

Both singleplayer and servers are supported. On a server the jar goes in the server's
`mods` folder as well as each player's.

## Getting started

1. Launch a world with the mod installed. You'll be asked to **choose a starting
   element** — fire, water, air, or earth. This is your *main* element and it decides
   which one the Avatar cycle can pick you for later.
2. Press **N** to open the bending menu. This is where you spend XP on abilities,
   buy upgrades, and bind what you've unlocked.
3. Unlock an ability in the **skill tree** tab, then bind it in the **equip** tab.
4. Press its key and bend.

Abilities cost **chi**, a pool that refills on its own — but not for three seconds
after you spend any, so you cannot fund a cheap ability indefinitely. Your maximum
chi grows with your level (`500 + level × 100`), which is why a few late abilities
are simply uncastable until you have grown into them.

Hold **M** to meditate: it roots you in place and trickles XP.

## Controls

| Key | Does |
|---|---|
| `Z` `X` `C` `V` | Ability slots 1–4 |
| `N` | Bending menu — skill tree, equip, passives |
| `Y` | Switch active element (once you have more than one) |
| `M` | Meditate (hold) |
| Left click | Throw or fire an ability you are holding |

Abilities come in a few shapes and the key behaves accordingly. Some fire on the
press. Some are **held** — a channel that runs while you keep the key down. Some
**charge**, winding up while held and either firing when full or firing weaker if you
let go early. Some **arm**, building something that waits on your crosshair until you
**left click** to throw it. And some are **toggles**, where pressing the key again
puts the ability away.

## Progression

Each element has **four paths** — Offensive, Defensive, Balanced, and a **Masterclass**
locked until the other three are finished. Abilities cost XP to unlock, and many carry
their own **upgrades** bought with levels: right click an ability in the tree to see them.

**Passives** are a separate slot type. They are never cast — having one equipped *is*
the effect. Blue fire recolours every flame you make and doubles your fire damage;
Fire immunity cancels every fire source aimed at you; Flight grants creative flight at
half speed.

### The four elements

| Element | Its character |
|---|---|
| **Fire** | Reach and area denial. Lays fire that burns hotter than the real thing, and the masterclass turns it blue and doubles the damage. |
| **Water** | Supply is the constraint — you bend from open water, from ice and snow with the right passive, or from a canteen you have to fill. |
| **Earth** | Moves the world and puts it back. Walls, pillars, pits and traps, none of which leave anything behind. |
| **Air** | Mobility and control. Very little of it does much damage; nearly all of it decides where everyone else is standing. |

### The seven sub-elements

Sub-elements are not chosen at the start. Each is **earned** by buying a scroll from a
villager and reading it, and each is gated behind progress in its parent element. Four
branch off after two completed paths; three are the *end* of their element's road and
need all four.

| Sub-element | From | Costs | Requires |
|---|---|---|---|
| **Lightning** | Weaponsmith | 64 copper ingots | 2 fire paths |
| **Ice** | Fisherman | 1 heart of the sea | 2 water paths |
| **Sound** | Fletcher | 32 feathers | 2 air paths |
| **Metal** | Mason | 4 iron blocks | 2 earth paths |
| **Combustion** | Armorer | 32 gunpowder | **all 4** fire paths |
| **Blood** | Cleric | 5 rabbit feet | **all 4** water paths |
| **Lava** | Shepherd | 5 nether bricks | **all 4** earth paths |

Each has two paths rather than four. Read a scroll you do not qualify for and you
keep it — the requirement is stated, not punished.

A few of them change how the element plays rather than just adding to it.
**Lightning** makes every ability serve a wind-up, so nothing fires on the press.
**Combustion** goes further: letting go of a charge early is a *misfire* that drops
live TNT on you. **Blood** keeps an experience track entirely of its own, and a
bloodbender cannot bend anyone whose blood level is higher than theirs.

## The Avatar

One player at a time can hold the title. They get three lives and a last stand —
Resistance, Regeneration and a visible glow below three hearts — and when the third
life is spent the title passes on.

Run the cycle with `/bend avatar cycle start` and it moves earth → fire → air → water,
picking at random among online players whose main element matches. An element nobody
can claim is skipped rather than waited on. Or name someone directly with
`/bend avatar <player>`.

## Commands

All of `/bend` needs permission level 2 — cheats in singleplayer, or op on a server.

```
/bend add <targets> <element>
/bend remove <targets> <element>
/bend level <targets> <amount>
/bend avatar <player>
/bend avatar remove
/bend avatar cycle start
/bend avatar cycle stop
```

The target is never optional, so these work for setting other people up on a server —
`@s` for yourself, `@a` for everyone.

## Building from source

```
git clone https://github.com/Niilo411/atla-mod.git
cd atla-mod
./gradlew build
```

The jar lands in `build/libs/`. `./gradlew runClient` launches a dev client.

**Build with JDK 21.** Gradle here cannot parse the build scripts under a newer JVM —
a JDK 26 on your `PATH` fails with `Unsupported class file major version 70`. If your
default Java is newer, point `JAVA_HOME` at a 21 for the build:

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

`tools/` holds throwaway generators that are not on the build path — see its README.

## License

Currently **All Rights Reserved** (`mod_license` in `gradle.properties`), which is the
NeoForge template's default rather than a considered choice, and is likely to change
before any public release.

All textures in this repository are the project's own. The armor sheets are generated
procedurally by `tools/GenArmor.java`. Where the mod appears to use vanilla art —
bending fire, bent lava, bent metal, the scroll items — those are model files
*referencing* a vanilla texture path; no Minecraft assets are redistributed here.

The mod is built against Mojang's official mappings, which carry their own license.
See [Mojang.md](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).

Not affiliated with or endorsed by Mojang, Microsoft, Nickelodeon, or Paramount.
