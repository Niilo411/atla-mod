# Atla Mod — Project Context

A Minecraft mod (NeoForge, MC 1.21.1) featuring elemental bending abilities with
a progression/upgrade system. Mod ID: `atlamod`. Base package: `com.minecraft.atlamod`.

## Stack & Architecture

- **Loader:** NeoForge 21.1.248 (not Forge — API differs, e.g. `CustomPacketPayload` +
  `StreamCodec` for networking, `AttachmentType` instead of old Capabilities)
- **Player data:** Stored via a NeoForge **data attachment** (`ModAttachments.BENDING_DATA`),
  backed by `BendingData.java`. Holds: main/active element, unlocked elements, xp, level,
  chi (resource pool), unlocked abilities, 8 equipped ability slots, and various transient
  "is currently doing X" flags (e.g. `isFireLeaping`, `isBreathingFire`).
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

## Ability System (refactored — use this for all new abilities)

We moved off a giant switch statement in `AbilityHandler` to a registry pattern:

- `abilities/Ability.java` — interface: `getName()`, `getChiCost()`, `getXpReward()`,
  `execute(player, data)`, optional `getCooldownTicks()`
- `abilities/ChanneledAbility.java` — extends `Ability` for held-key abilities:
  `onStart()`, `onTick()`, `onStop()`
- `abilities/AbilityRegistry.java` — `Map<String, Ability>`, populated once via
  `AbilityRegistry.bootstrap()` (call this from `Atlamod`'s constructor)
- One class per ability, organized by element package: `abilities/fire/FireLeap.java`,
  `abilities/fire/FireBreath.java`, etc.
- `AbilityHandler.java` is now a thin dispatcher: looks up the ability in the registry,
  handles the shared chi-cost/xp/cooldown bookkeeping, then calls the ability's own logic.

**Known gap to close:** channeled-ability "which one is currently active" tracking is
still a single stopgap boolean (`data.isBreathingFire()`) rather than a general
`data.getActiveChanneledAbility()` string. Fine while Fire Breath is the only channeled
ability — needs generalizing before adding a second one (e.g. Water Stream, Air Tornado).

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

- Fire Offensive path abilities done: Fire Leap, Fire Whip, Fireball, **Fire Breath**
  (just implemented — channeled cone-damage ability, ignites ground, drains 15 chi/sec)
- Just refactored `AbilityHandler` from a switch statement into the registry pattern above
- 53 more abilities left across Fire/Water/Air/Earth × 4 paths
- Previously built with Gemini; switched to Claude as primary coding partner because
  Gemini was getting inconsistent on a project this size

## Working Style

- Prefers step-by-step guidance and practical, hands-on troubleshooting over abstract explanation
- Comfortable with a full class rewrite if it's a clear improvement over patching —
  don't hesitate to propose one, changes are easy to revert with Ctrl+Z / git
- Building solo (well — solo + AI), so keep explanations of *why* a change was made,
  not just the diff
