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
`abilities/<element>/`. There are **four ability shapes**, all in `abilities/`:

- `Ability.java` — base interface: `getName()`, `getChiCost()`, `getXpReward()`,
  `execute(player, data)`, plus optional `getCooldownTicks()` and
  `canStart(player, data)`. `getKey()` defaults to the lowercased name and is used
  as both the registry key and the cooldown key.
- `ChanneledAbility.java` — held-key abilities (Fire Breath): `onStart()`, `onTick()`,
  `onStop()`, `getChiPerSecond()`, `getXpPerSecond()`.
- `ChargedAbility.java` — hold-to-wind-up abilities (Fireball, Fire Spikes):
  `getChargeTicks()`, `onChargeStart/Tick/Cancel()`. The payload is the ordinary
  `execute()`, run through the same `performCast()` path an instant cast uses. Chi
  is only CHECKED at charge start and spent when the cast lands, so letting go
  early is free.
- `TwoPhaseAbility.java` — arm-then-left-click abilities: `onRelease()`.
- **The two combine.** Fireball implements BOTH: the charge builds it, and what the
  completed charge produces is the armed two-phase slot, which the left click then
  throws. `performCast` arms two-phase abilities and deliberately skips the cooldown
  for them, so Fireball's cooldown starts on the throw rather than when it is built.
  Releasing the slot key after a full charge does NOT disarm it — `cancelCharge`
  only ever touches the charging slot.
- `AbilityRegistry.java` — `Map<String, Ability>`, populated once via
  `AbilityRegistry.bootstrap()`, called from `Atlamod`'s constructor.
- `AbilitySupport.java` — shared chi/XP/sync helpers (`consumeChiAndGiveXp`,
  `grantXp`, `syncData`). XP threshold per level is `XP_PER_LEVEL` (200).

**The dispatcher owns everything shared**, so ability classes only hold their effect:
cooldown gating, the `canStart` precondition (checked *before* chi is spent, so a
blocked cast is free), chi cost, XP reward, arming/clearing two-phase abilities,
the channeling lifecycle, and syncing to the client.

Rules worth knowing:
- **Two-phase cooldowns start on release, not on cast** — otherwise the timer would
  run down while the player is still holding the charge.
- **Only one held ability at a time**, across both held shapes: `startChannel` and
  `startCharge` each refuse if either a channel or a charge is already running.
- **A charge clears its state BEFORE casting**, so the key release that inevitably
  follows finds nothing to cancel and the ability cannot fire twice.
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

- Fire Offensive path COMPLETE: Fire Leap, Fire Whip, Fireball (hold 2s to build,
  then LEFT CLICK to throw; 100 chi + 10 xp on completing the charge, 2s cooldown
  from the throw), Fire Breath
  (channeled cone of flame, damages + ignites entities in a 6-block line;
  25 chi/sec, 2 xp/sec, 10s max duration, 15s cooldown after it ends)
- Fire Defensive path COMPLETE:
  - Fire Push (6.0 damage, ~6 block knockback in a 60-degree forward cone
    reaching 8 blocks, 100 chi, 2s cooldown, 5 xp)
  - Fire Shield (channeled; cancels incoming damage while held EXCEPT fall and
    void/kill, 25 chi/sec = 50 per 2s, 1 xp/sec, no cooldown, no duration cap;
    needs 200 chi banked to START — a gate, not a cost, nothing is deducted for it
    and it keeps running below 200 once up)
  - Firewall (6-block line of fire laid across the ground 2 blocks ahead,
    perpendicular to facing; 30 chi, 1s cooldown, 10 xp. Only ever replaces air,
    so it cannot grief blocks)
  - Fire Ring (30-block continuous ring of fire at radius 4 around the player;
    100 chi, 2s cooldown, 8 xp. Its fire burns at 3x normal for 30s — see below)
- Fire Balanced path in progress:
  - Ignite (lights whatever you look at up to 20 blocks;
    its fire burns at 2x for 30s. Aimed at a furnace/blast furnace/smoker it fuels
    that instead, burning 15s. 50 chi, 5 xp, no cooldown. `canStart` refuses the cast
    when nothing is in view, so looking at the sky costs nothing)
  - Fire Spikes (2s hold to charge, then ~25 fire blocks scattered randomly out to
    15 blocks, even across the area rather than bunched near the player; burns at
    2x for 30s. 100 chi, 10 xp, no cooldown)
  - Fire Rocket (channeled creative-style flight at 0.03 fly speed vs vanilla 0.05,
    capped 10 blocks above ground, flame venting from the feet; 15 chi/sec, 5 xp/sec,
    no cooldown. No fall damage while lit, plus 100 ticks of grace after it cuts out
    so the descent it caused can't kill you)
- **Flight flags are persisted, so they need a safety net**: Fire Rocket grants
  flight via `player.getAbilities().mayfly/flying`, which `Abilities.addSaveData`
  writes to player NBT. Disconnecting or dying mid-flight means `onStop()` never
  runs, which would leave permanent creative flight. `FireRocket.stopFlight()` is
  called from BOTH the login and respawn handlers in `ServerEvents`, and skips
  players actually in creative/spectator so it can't strip legitimate flight.
- **Fall damage is removed by zeroing `fallDistance`**, not by cancelling a damage
  event — if the distance never accumulates, the damage is never calculated, so
  there is nothing to intercept. `BendingData.fallImmunityTicks` counts the grace
  window down in the tick loop.
- **Bending fire that burns hotter**: `abilities/BendingFire.java` remembers which
  fire blocks an ability placed (dimension + pos -> expiry), and the
  `LivingIncomingDamageEvent` handler multiplies `IS_FIRE` damage for anything
  standing on one, each fire carrying its own multiplier (Fire Ring 3x, Ignite 2x).
  Positions are tracked because vanilla has nowhere to hang "this
  fire is special" — a custom block would need a blockstate, model, texture and its
  own spread rules just to change a damage number. Entries self-expire, so fire that
  burns out stops counting on its own. Firewall could opt in with one `mark()` call.
- **Invulnerability is registry-driven**: `ChanneledAbility.grantsInvulnerability()`
  gates `AbilityHandler.blocksDamage(data, source)`, which a
  `LivingIncomingDamageEvent` handler in `ServerEvents` consults to cancel damage.
  It exempts `IS_FALL` and `BYPASSES_INVULNERABILITY`, so gravity, the void and
  `/kill` still land. Deliberately NOT `Entity#setInvulnerable`, which persists in
  player NBT and would leave anyone who logged out mid-shield invincible forever.
  Water Shield / Earth Armor get all of this by overriding the one method.
- **Access transformer**: `src/main/resources/META-INF/accesstransformer.cfg` opens
  `AbstractFurnaceBlockEntity.litTime`/`litDuration` (package-private, and the
  `ContainerData` exposing them is protected — there is no public "burn for N ticks"
  API). NeoForge auto-detects the file at that path, so no build.gradle change is
  needed, and it ships inside the jar so it applies in production too.
- Fire Shield is the SECOND channeled ability, so the generalised
  `activeChanneledAbility` tracking is now actually load-bearing: only one channel
  can run at a time, and releasing one channel's key can't stop the other.
- **Charge meter HUD**: `ChargeStatusPacket` (server -> client) feeds
  `client/ClientChargeState`, a static the `ModHudOverlay` layer reads to draw a bar
  at top centre — filling while charging, then "ready — left click to throw" once
  armed. `AbilityHandler.syncChargeStatus()` READS the state rather than being told
  it, so call sites only signal "something changed" and can't disagree about what to
  show. Sent on charge start/cancel/complete, on the left-click release, every 2
  ticks while charging, and on login to clear a bar left stale by a relog.
- **Chi regen is delayed after spending**: passive regen (1% of max per second) is
  held off for `BendingData.CHI_REGEN_DELAY_TICKS` (60 ticks / 3s) after any chi is
  spent, so regen can't bankroll a cheap ability indefinitely. The delay is armed
  inside `BendingData.consumeChi()` itself, so every ability gets it without having
  to remember — and channels re-arm it every tick, meaning they now drain at their
  full rate rather than rate-minus-regen, and only start refilling 3s after release.
- The UI needs no work per ability: all four path arrays in `UpgradeMenuScreen`
  already list every planned ability, and unlock cost, tree gating and the equip
  screen are all generic. A new ability = the class + one `register()` line, as
  long as its `getName()` matches the menu string case-insensitively.
- `AbilityHandler` now uses the registry pattern above (was a switch statement).
  The old "channeled tracking is a single boolean" gap is closed —
  `BendingData.getActiveChanneledAbility()` is a general string.
- Commands: `/bend add|remove <element>` and `/bend level <amount>`.
  Note `/bend level` bumps level without touching xp, so the two can drift.
- 46 more abilities left across Fire/Water/Air/Earth × 4 paths
- Previously built with Gemini; switched to Claude as primary coding partner because
  Gemini was getting inconsistent on a project this size

## Working Style

- Prefers step-by-step guidance and practical, hands-on troubleshooting over abstract explanation
- Comfortable with a full class rewrite if it's a clear improvement over patching —
  don't hesitate to propose one, changes are easy to revert with Ctrl+Z / git
- Building solo (well — solo + AI), so keep explanations of *why* a change was made,
  not just the diff
