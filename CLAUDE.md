# Atla Mod — Project Context

A Minecraft mod (NeoForge, MC 1.21.1) featuring elemental bending abilities with
a progression/upgrade system. Mod ID: `atlamod`. Base package: `com.minecraft.atlamod`.

## Stack & Architecture

- **Loader:** NeoForge 21.1.248 (not Forge — API differs, e.g. `CustomPacketPayload` +
  `StreamCodec` for networking, `AttachmentType` instead of old Capabilities)
- **Player data:** Stored via a NeoForge **data attachment** (`ModAttachments.BENDING_DATA`),
  backed by `BendingData.java`. Holds: main/active element, unlocked elements, xp, level,
  chi (resource pool), unlocked abilities, 8 equipped ability slots, and various transient
  "is currently doing X" flags (e.g. `isFireLeaping`), and `activeChanneledAbility`/
  `activeTwoPhaseAbility` for the two multi-tick ability shapes.
- **Networking:** Custom packets under `com.minecraft.atlamod.network`, registered once
  in `ModNetworking.register()`. **Critical gotcha we've hit twice:** registration calls
  (`registrar.playToServer(...)` / `playToClient(...)`) must be top-level statements
  directly inside `register()` — NEVER nested inside another packet's handler lambda.
  Doing so throws `UnsupportedOperationException: Cannot register payload X after
  registration phase` at runtime, sometimes not until that code path first executes.
  Always double check indentation/nesting when adding a new packet registration.
- **Client input:** `ClientEvents.onClientTick` polls keybinds every client tick.
  - Instant-use abilities: `KeyMapping.consumeClick()` → sends `UseAbilityPacket(slot)`
  - Held/channeled abilities: `KeyMapping.isDown()`, sent only **on change** →
    `AbilityHoldPacket(slot, isHeld)` (mirrors the older `MeditatePacket` pattern)
- **Server tick loop:** `ServerEvents.onPlayerTick` (`PlayerTickEvent.Post`) is where all
  per-tick ongoing-ability logic lives (cooldown ticking, chi regen, fire leap particles,
  meditation, fire breath, etc). New "ongoing ability" logic goes here as a guarded
  `if (data.isXyz()) { ... }` block, or via the ability registry system below.

## Ability System (registry pattern — use this for all new abilities)

`AbilityHandler` is a thin dispatcher; ability effects live in one class each under
`abilities/<element>/`. There are **three ability shapes**, all in `abilities/`:

- `Ability.java` — base interface: `getName()`, `getChiCost()`, `getXpReward()`,
  `execute(player, data)`, plus optional `getCooldownTicks()` and
  `canStart(player, data)`. `getKey()` defaults to the lowercased name and is used
  as both the registry key and the cooldown key.
- `ChanneledAbility.java` — held-key abilities (Fire Breath): `onStart()`, `onTick()`,
  `onStop()`, `getChiPerSecond()`, `getXpPerSecond()`.
- `TwoPhaseAbility.java` — charge-then-left-click abilities (Fireball): `onRelease()`.
- `AbilityRegistry.java` — `Map<String, Ability>`, populated once via
  `AbilityRegistry.bootstrap()`, called from `Atlamod`'s constructor.
- `AbilitySupport.java` — shared chi/XP/sync helpers (`consumeChiAndGiveXp`,
  `grantXp`, `syncData`). XP threshold per level is `XP_PER_LEVEL` (200).

**The dispatcher owns everything shared**, so ability classes only hold their effect:
cooldown gating, the `canStart` precondition (checked *before* chi is spent, so a
blocked cast is free), chi cost, XP reward, arming/clearing two-phase abilities,
the channeling lifecycle, and syncing to the client.

Two rules worth knowing:
- **Two-phase cooldowns start on release, not on cast** — otherwise the timer would
  run down while the player is still holding the charge.
- **Channeled abilities are driven entirely by the dispatcher's tick**: it drains
  `getChiPerSecond()` (spread exactly across the 20 ticks, so rates like 25/sec
  that aren't whole numbers per tick neither drift nor stutter), trickles
  `getXpPerSecond()` once a second, enforces `getMaxDurationTicks()`, stops the
  channel when chi runs out, and syncs every 4 ticks to avoid flooding packets.
- **A channel's cooldown starts when it ENDS, not when it starts**, and every exit
  route goes through `stopChannel()` — key release, chi exhaustion, duration cap —
  so the cooldown applies uniformly. Holding the key past the cap is not a way to
  dodge it, and the auto-stop doesn't double-apply when the key is finally released.

Adding an ability = write the class, register it in `AbilityRegistry.bootstrap()`.
Nothing in `AbilityHandler` should need to change.

Per-tick state that isn't channeled (e.g. Fire Leap's fire trail, which ends itself
on landing) still lives on the ability class as a `static tick(player, data)`, called
from a guarded block in `ServerEvents.onPlayerTick`.

## Progression System

4-path upgrade tree per element: **Offensive, Defensive, Balanced, Masterclass**
(Masterclass locked behind unlocking the other 3 paths). Each ability has an XP cost
to unlock, shown in the design doc as the number after the ability name.

Elements: **Fire, Water, Air, Earth** — each with its own 4-path ability list.

### Fire path (from design doc)
- Offensive: Fire leap-5, Fire whip-5, Fireball-10, Fire Breath-2s
- Defensive: Fire push-5, Fire shield-10, Firewall-10, Fire ring-15
- Balanced: Ignite-2, Fire spikes, Fire rocket-4s, Taller fire-0
- Masterclass: blue fire-2X xp, Fire blow-30, Fire immunity-0, Fire Rain

### Water path (from design doc)
- Defensive: Water shield-3s, Water push-10, Water heal-4s
- Offensive: Water ball-5, Water stream-10, Water Bullets-15
- Balanced: Water Manipulation-5, Water Surf-4s, Water Sphere-4s
- Masterclass: Water bubble-20, water breathing-0, Tsunami-35

### Air path (from design doc)
- Defensive: Airpush-10, Air jump-5, Air Aura, Wind
- Offensive: Air splinters-10, Air cannon-10, wind tunnel-15
- Balanced: Air scooter-3s, Air pull-10, Air spout-10
- Masterclass: breathless-10, Tornado-15, Flight-5s, Air beam-5s

### Earth path (from design doc)
- Defensive: Earth wall-5, Earth pillar-5, Earth armor-15
- Offensive: Earth spike-5, Splinters-5, Earth block-5, Earth trap-10
- Balanced: Mine-1, Earth dig-4s, Earth grab-15
- Masterclass: Earthquake-15, Ravine-15, Earth sink-15

## Current Status

- Fire Offensive path COMPLETE: Fire Leap, Fire Whip, Fireball, Fire Breath
  (channeled cone of flame, damages + ignites entities in a 6-block line;
  25 chi/sec, 2 xp/sec, 10s max duration, 15s cooldown after it ends)
- Fire Defensive path in progress:
  - Fire Push (6.0 damage, ~6 block knockback in a 60-degree forward cone
    reaching 8 blocks, 100 chi, 2s cooldown, 5 xp)
  - Fire Shield (channeled; cancels ALL incoming damage while held, 25 chi/sec
    = 50 per 2s, 10 xp/sec, no cooldown, no duration cap — chi is the only limit)
- **Invulnerability is registry-driven**: `ChanneledAbility.grantsInvulnerability()`
  plus a `LivingIncomingDamageEvent` cancel in `ServerEvents`. Deliberately NOT
  `Entity#setInvulnerable`, which persists in player NBT and would leave anyone who
  logged out mid-shield invincible forever. Water Shield / Earth Armor get this for
  free by overriding the one method.
- Fire Shield is the SECOND channeled ability, so the generalised
  `activeChanneledAbility` tracking is now actually load-bearing: only one channel
  can run at a time, and releasing one channel's key can't stop the other.
- The UI needs no work per ability: all four path arrays in `UpgradeMenuScreen`
  already list every planned ability, and unlock cost, tree gating and the equip
  screen are all generic. A new ability = the class + one `register()` line, as
  long as its `getName()` matches the menu string case-insensitively.
- `AbilityHandler` now uses the registry pattern above (was a switch statement).
  The old "channeled tracking is a single boolean" gap is closed —
  `BendingData.getActiveChanneledAbility()` is a general string.
- Commands: `/bend add|remove <element>` and `/bend level <amount>`.
  Note `/bend level` bumps level without touching xp, so the two can drift.
- 51 more abilities left across Fire/Water/Air/Earth × 4 paths
- Previously built with Gemini; switched to Claude as primary coding partner because
  Gemini was getting inconsistent on a project this size

## Working Style

- Prefers step-by-step guidance and practical, hands-on troubleshooting over abstract explanation
- Comfortable with a full class rewrite if it's a clear improvement over patching —
  don't hesitate to propose one, changes are easy to revert with Ctrl+Z / git
- Building solo (well — solo + AI), so keep explanations of *why* a change was made,
  not just the diff
