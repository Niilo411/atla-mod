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
- Masterclass: Drown-20 (was "Water bubble" in the design doc), water breathing-0, Tsunami-35

### Air path (from design doc)
- Defensive: Air pull-10, Air jump-5, Air Aura, Wind (the design doc had "Airpush" here;
  it and Balanced's "Air pull" were SWAPPED, so the two names still both exist)
- Offensive: Air splinters-10, Air cannon-10, wind tunnel-15
- Balanced: Air scooter-3s, Airpush-10, Air spout-10
- Masterclass: breathless-10, Tornado-15, Flight-5s, Air beam-5s

### Earth path (from design doc)
- Defensive: Earth wall-5, Earth pillar-5, Earth armor-15
- Offensive: Earth spike-5, Splinters-5, Earth block-5, Earth trap-10
- Balanced: Mine-1, Earth dig-4s, Earth grab-15
- Masterclass: Earthquake-15, Ravine-15, Earth sink-15


## Waterbending resources

- **Water Canteen** (`WaterCanteenItem`): crafted from 3 sticks (top), string either
  side of an empty middle, 3 leather (bottom). Right click at or in open water to
  fill; each waterbending ability used away from water drinks one unit.
- **The water level IS the durability value.** 20 units, so one ability costs exactly
  the 5% the design asks for, and the vanilla durability bar becomes the gauge for
  free (recoloured blue, and always visible so an empty canteen still looks like a
  canteen). It is NEVER damaged through `hurtAndBreak` — the damage value is set
  directly, so running dry leaves an empty canteen rather than destroying it.
- **`Ability.requiresWater()`** (default false) is the hook. The dispatcher checks it
  in `performCast` BEFORE chi is spent, so a bender caught dry loses nothing for
  trying. `WaterSupply.tryConsume` then: free if open water is within 15 blocks,
  otherwise one unit from a canteen in the inventory or off-hand, otherwise refused
  with a message.
- **The 15-block search walks expanding shells, not a flat triple loop.** Standing at
  the edge of a lake is the common case and bails out almost immediately; only a
  genuinely dry cast pays for the full 31-cube.
- No water abilities exist yet, so `requiresWater()` is infrastructure waiting to be
  used — nothing overrides it until the Water paths are built.
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
- Fire Balanced path COMPLETE:
  - Ignite (lights whatever you look at up to 20 blocks;
    its fire burns at 2x for 30s. Aimed at a furnace/blast furnace/smoker it fuels
    that instead, burning 15s. 50 chi, 5 xp, no cooldown. `canStart` refuses the cast
    when nothing is in view, so looking at the sky costs nothing)
  - Fire Spikes (2s hold to charge, then ~25 fire blocks scattered randomly out to
    15 blocks, even across the area rather than bunched near the player; burns at
    2x for 30s. 100 chi, 10 xp, no cooldown)
  - Fire Rocket (channeled flight at 0.03 fly speed vs vanilla creative's 0.05, no
    height limit; flame venting from the feet; 15 chi/sec, 5 xp/sec, no cooldown.
    Fall damage applies normally — the height you gain is yours to survive)
  - Taller fire (PASSIVE — equip it in the Passives tab; ability-laid fire becomes
    2 blocks tall. Affects Firewall, Fire Ring, Fire Spikes, and Fire Blow when it
    exists, since all of them go through BendingFire.placeGrounded)
- **Fire Rocket owns flight outright**: the keybind is the ONLY thing that starts or
  ends it. Two vanilla behaviours fight that and are both undone in `onTick` —
  double-tapping space is vanilla's flight toggle for anyone with `mayfly`, and the
  client clears flight whenever the player is on the ground. `keepFlying()` re-asserts
  the flag, and touching down also earns an upward kick, because re-asserting alone
  would trade packets with the client every tick while grounded. The same kick fires
  on start, or the player would be granted flight while still standing on the ground
  and the client would switch it straight back off.
- **Flight flags are persisted, so they need a safety net**: Fire Rocket grants
  flight via `player.getAbilities().mayfly/flying`, which `Abilities.addSaveData`
  writes to player NBT. Disconnecting or dying mid-flight means `onStop()` never
  runs, which would leave permanent creative flight. `FireRocket.stopFlight()` is
  called from BOTH the login and respawn handlers in `ServerEvents`, and skips
  players actually in creative/spectator so it can't strip legitimate flight.
- Fire Masterclass path COMPLETE (gated behind the other three):
  - blue fire (PASSIVE — all ability fire and flame particles turn blue, and every
    fire ability deals double damage. Standing in blue fire burns for a flat 6.0
    (3 hearts) a hit instead of scaling off normal fire. No chi, no xp)
  - Fire blow (CHARGED up to 10s, and unlike the others it FIRES ON RELEASE at
    whatever strength it reached. Erupts a fanning wall of flame ahead: reach 4->16
    blocks, 4->12 blocks WIDE at the far end, damage 4->20, flame columns 2->6 high,
    all scaling with charge. Lays
    fire through `BendingFire.placeGrounded`, so Taller Fire and blue fire both
    apply to it. 150 chi, 20 xp, 1s cooldown)
  - Fire immunity (PASSIVE — cancels every `IS_FIRE` source aimed at the wearer:
    fire, lava, magma, burning, and every fire ability including their own blue
    fire. Also clears fire ticks each tick, since burning and being hurt by it are
    separate in Minecraft and cancelling only the damage leaves you visibly alight)
  - Fire Rain (instant cast that then runs for 30s — a countdown on BendingData
    ticked by `FireRain.tick`, the Fire Leap pattern, NOT a channel. Burning sky out
    to a 50-block cylinder, 0.5 hearts/sec to every living thing under it INCLUDING the
    caster. 1000 chi, instant 20 xp, 60s cooldown)
- **Fire Rain costs more chi than a new bender can hold.** `getMaxChi()` is
  `500 + level*100`, so its 1000 cost is uncastable until level 5 — deliberate for
  the last ability in the tree, but it is a gate, not a bug, if it ever looks like
  one. It also damages the caster, which pairs it with Fire immunity.
- **Particle density is a packet-count problem, not a number to raise.** A directed
  velocity requires `count = 0`, which is one particle per packet, so "more particles"
  the obvious way costs 20 packets/sec each. Fire Rain gets its volume from batched
  calls instead — `count = 70` buys a whole layer for one packet, at the cost of
  random velocities, so those layers hang and flicker rather than fall. The rationed
  directed ones supply the falling streaks on top. Same trick applies to any future
  large-area effect.
- **`ChargedAbility.firesOnRelease()`** (default false) makes an early release cast a
  weaker version instead of throwing the charge away, with `getMinimumChargeTicks()`
  as the floor below which a stray tap still costs nothing. Abilities scale off
  `BendingData.getLastChargeTicks()`, recorded by the dispatcher immediately before
  the cast — the live counter has to be cleared first so the key release that follows
  cannot fire the ability a second time.
- **Blue fire needs its own block too.** Vanilla `SOUL_FIRE` — the only blue fire in
  the game — only survives on soul sand or soul soil, so it can't be laid anywhere
  else. `BendingFireBlock` (which replaced `TallFireBlock`) carries two properties:
  `BLUE` for colour and `STACKED` for role. A stacked block dies with the fire below
  it; an unstacked one burns out on its own after 30s. Four blockstate variants map
  to two models — vanilla's `fire_0` and `soul_fire_0` textures on a `block/cross`.
- **One place decides the colour**: `BendingFire.flame(data)` returns FLAME or
  SOUL_FIRE_FLAME. NOTHING names a flame particle directly any more — not the
  abilities, and not the two per-tick visual blocks in `ServerEvents` (the Fire Whip
  trail and the armed two-phase ball), which were missed the first time and stayed
  orange under Blue Fire. Plain
  orange fire stays vanilla `Blocks.FIRE` so it keeps spreading as before; only blue
  fire uses the custom block, which does NOT spread.
- **Blue Fire's damage boost is keyed on the ATTACKER** and limited to `IS_FIRE` only,
  in the `LivingIncomingDamageEvent` handler. That tag with a player behind it is a close
  match for "a fire ability": all five damaging fire abilities go through
  `damageSources().inFire()`, while the fire a player can cause without bending — a Fire
  Aspect burn, a lit block, spilled lava — arrives with no attacker and never qualifies.
  Water abilities use `indirectMagic`/`drown`, so they are untouched.
  Explosions were briefly included to catch Fireball and were too broad: that doubled any
  explosion the player caused, TNT included. Fireball instead raises its OWN explosion
  power (1 -> 2) at cast time when blue fire is equipped: the ability knows what it is
  throwing, where the damage handler cannot tell one explosion from another. Note power
  drives block destruction too, so a blue fireball digs a bigger hole where mob griefing
  is on.
- **Passive abilities** (`abilities/PassiveAbility.java`): never cast — being in a
  passive slot IS the activation, and whatever they affect asks
  `data.hasPassiveEquipped(key)`. No chi, no XP: there is no moment of use to hang
  either on. `AbilityHandler.executeAbility` refuses them alongside channels and
  charges, so a passive in a keybind slot does nothing rather than burning chi.
  4 slots, stored in `BendingData.equippedPassives` (persisted, `"EMPTY"` sentinel
  like the 8 ability slots) and synced by `SyncPassivesPacket`. They are unlocked
  through the ordinary skill tree, then slotted in the menu's **Passives** tab.
  `UpgradeMenuScreen` now has three tabs on an `int activeTab` rather than the old
  `isEquipTab` boolean, with one shared `drawTabs()` instead of two copies.
- **Element icons**: `client/ElementIcons.java` maps an element to a PNG under
  `assets/atlamod/textures/gui/elements/`. Only elements listed in its `ICONS` map
  are drawn — anything else keeps the old "?" box, because a texture Minecraft cannot
  find renders as the magenta checkerboard, which looks far more broken than a
  placeholder. Source PNGs are expected to be **256x256**; the icon is scaled through
  the pose stack rather than by blit's arguments, since blit's width arguments set the
  source region as well as the drawn size and so cannot resize on their own.
  Used by BOTH the element selection screen and the HUD badge.
  Adding an element = drop the PNG in that folder + one line in `ICONS`.
  NOTE: source art must be a REAL PNG. The fire emblem arrived as a JPEG carrying a
  `.png` extension and was re-encoded (JDK `ImageIO`) to a genuine 256x256 ARGB PNG.
- **Passives are excluded from the ability equip tab.** They sit in the same path
  arrays as everything else, so the equip list picked them up until
  `UpgradeMenuScreen.equippableAbilities()` started filtering them out — offering a
  keybind for something `AbilityHandler` refuses to cast. That method also replaced
  two identical copies of the element filter, one in the render pass and one in the
  click handler.
- **Passive slots need syncing at BOTH login and respawn.** `copyOnDeath` keeps them
  server-side so they keep working, but the client's copy is rebuilt on respawn — miss
  the packet there and the menu shows every slot empty while the passives are still
  running. `PlayerEvent.Clone` also has to copy them by hand: that event fires on
  dimension change too, where `copyOnDeath` does not apply.
- **The damage handler has an order that matters** (`ServerEvents.onIncomingDamage`):
  shield cancel -> Blue Fire's attacker-side doubling -> then, ONLY for damage with
  no causing entity, the block-contact rules (blue fire's flat 6.0, then
  `BendingFire`'s per-position multipliers). The no-entity guard is what stops a Fire
  Whip hit on a mob that happens to be standing in bending fire having the ability's
  own damage overwritten by the block's.
- **Taller Fire needs a custom block too.** The second block cannot be vanilla fire:
  `FireBlock#canSurvive` needs a face-sturdy block below or a flammable neighbour,
  and a fire block is neither, so stacked vanilla fire deletes itself on its first
  scheduled tick. `BendingFireBlock extends BaseFireBlock`, which does NOT override
  `canSurvive` — so it survives anywhere. It burns and ignites like real fire, counts
  as `IS_FIRE` so `BendingFire`'s multipliers still apply, doesn't spread, and
  schedules a tick every 40 ticks to remove itself once the fire underneath is gone.
  First block assets in the project: `blockstates/bending_fire.json` +
  `models/block/bending_fire*.json` (`block/cross` wearing vanilla fire textures).
- `BendingFire.placeGrounded()` is now the single fire-laying helper for Firewall,
  Fire Ring and Fire Spikes — they had a copy each, and Taller Fire needed all three
  to change together.
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
- Water Masterclass path COMPLETE (gated behind the other three):
  - Drown (renamed from the design doc's "Water bubble"; CHARGED up to 5s and fires on
    release like Fire blow. Pops every air bubble and then keeps the victim without air
    — 5s of drowning at a 1s charge, 15s at 5s, one heart a second throughout. 250 chi,
    15 xp, 30s cooldown)
  - water breathing (PASSIVE — air is topped up every tick rather than granted as a
    potion effect, so nothing can dispel it and no timer is ever shown. Also makes the
    bender immune to Drown)
  - Tsunami (CHARGED 3s, then a wall of water 9 across and 4 high rolls 20 blocks out,
    hitting everything once for 24.0 and carrying it along — enough to one-shot a
    zombie, and `indirect_magic` bypasses armour so a geared one dies the same. Moves
    1 block every 2 ticks, so 20 blocks takes 2s; the wall is 2 slices deep, which is
    NOT arbitrary — see the flooding invariant below. 750 chi, 25 xp, no cooldown)
- **Tsunami is Water Sphere in reverse and borrows its trick**: blocks are placed AND
  cleared with `Block.UPDATE_CLIENTS`, no neighbour updates. Dropping a wall of water in
  the ordinary way would have every block of it try to flow, and a wave that spread on
  its own would flood whatever it crossed and never leave. The wave is a moving BODY —
  four slices laid at the front, taken up at the back — so it travels rather than filling
  in behind. Only air is replaced, each column finds its own footing so it rides terrain,
  and each victim is struck once however many slices wash over it.
- **Tsunami has a timing invariant that will flood the world if broken.** Placing a water
  block schedules it to spread 5 ticks later, and `Block.UPDATE_CLIENTS` does NOT suppress
  that — the block schedules its own tick, not a neighbour's. A slice is only safe while
  it is taken up again before that fires, so `BODY_DEPTH * ADVANCE_EVERY` must stay UNDER
  5. Break it and the wave spawns real flowing water that nothing is tracking, which stays
  long after the wave has gone. Slowing the wave to a step every 2 ticks while still 4
  slices deep did exactly that; depth is 2 now.
- **Tsunami costs more chi than a new bender can hold**, like Fire Rain: `getMaxChi()` is
  `500 + level*100`, so 750 needs level 3. A gate, not a bug.
- **water breathing answers Drown.** A bender who cannot run out of air cannot be
  drowned, so `Drownings` DROPS a victim wearing it rather than ignoring them — otherwise
  the two masterclass abilities would spend fifteen seconds setting the same air value
  back and forth every tick, and the victim would watch their bubbles flicker.
- **Drowning is driven by `Drownings`, not left to vanilla.** Vanilla only drowns what is
  underwater and refills its air the instant it is not, so emptying someone's lungs on dry
  land would do nothing — the bubbles would be back next tick. The tracker holds air at
  zero and deals the damage on vanilla's own one-heart-a-second beat, wherever the victim
  is standing. A second cast on the same victim replaces the first rather than stacking.
- Drown picks its victim by distance from the aim LINE rather than by a raycast, so a cast
  does not have to be pixel-perfect on something that is moving; nearest along the line
  wins. `canStart` runs both when the charge begins and again when it lands, so a target
  breaking line of sight during the wind-up costs the bender nothing.
- Water Balanced path COMPLETE:
  - Water Manipulation (look at a water SOURCE block and press the key to take hold of
    it; it rides your crosshair until left click sets it down. 50 chi, 5 xp, no
    cooldown)
  - Water Surf (channeled; lifts you to the surface and lays INVISIBLE footing in the
    air just above it so you
    can run across, with Speed I — Speed II via the "Swift Current" upgrade, 10 levels.
    10 chi/sec, 3 xp/sec, no cooldown. Must be started from in the water)
  - Water Sphere (channeled; holds the water back in a 5-block sphere so oceans can be
    walked through. Water closes in behind as the bender moves and the whole pocket
    fills in on release. 2 chi/sec, 2 xp/sec, no cooldown)
- **The sphere changes blocks WITHOUT neighbour updates** (`Block.UPDATE_CLIENTS`), which
  is load-bearing. Emptying a block mid-ocean the ordinary way tells every neighbouring
  water block to reconsider itself and they flow straight back in — the pocket would
  fight the sea every tick and churn its whole boundary. Suppressing the update leaves
  the surrounding water believing nothing happened.
- Every emptied block is remembered so it can be put back; otherwise a bender could
  drain an ocean by walking across the bottom of it. Restoring skips anything no longer
  air, so building inside your own pocket survives. Closed on release, death, logout,
  level unload and dimension change.
- **Two separate passes, and they run at different rates.** The pocket already claimed
  is re-cleared EVERY tick (~515 lookups) because water does creep back in and that is
  what a bender actually sees. The expensive outward scan (~1300) only runs when they
  move to a new block, plus a forced rescan each second. Doing only the outward scan on
  a timer meant someone standing still watched the sea close on them and snap away again.
- **Surfing lays an invisible platform ABOVE the water, not ice in it.** `SurfPlatformBlock`
  is one sixteenth of a block tall, renders nothing (`RenderShape.INVISIBLE`) and has no
  outline, and sits in the AIR block over a water source — so the water is untouched and
  still visible, and the bender appears to run on it. Frosted ice works and is how vanilla
  does it, but it plainly looks like ice. Each platform removes itself on a scheduled tick.
- Either way a REAL block carries the player, which is the important part: the client walks
  on it normally. Pinning the player to the waterline every tick would have the server
  correcting the client constantly — the rubber-band that made Fire Rocket's old height
  cap feel bad.
- **`abilities/HeldBlocks.java` is the block-moving system**, written element-agnostic
  because earthbending is expected to lean on it — nothing in it knows what the block
  is. The block is genuinely REMOVED on grab and put back on place, so it moves rather
  than being copied. Visual is a `FallingBlockEntity` with gravity off and its `time`
  pinned at 0 (its own timer would otherwise drop it as an item); fluids have no model
  to render so they fall back to particles.
- **Every way a carry can end puts the block back**: place, no-room release, death,
  disconnect, level unload, and changing dimension mid-carry. The block is out of the
  world while held, so any missed path would silently destroy it — which is a far worse
  failure than an awkward drop.
- Water Offensive path COMPLETE:
  - Water ball (hold 2s to gather, then LEFT CLICK to throw — the same
    ChargedAbility + TwoPhaseAbility pairing Fireball uses. 6.0 damage and a shove on
    hit. 50 chi, 5 xp, 2s cooldown from the throw)
  - Water stream (must be LOOKING at water within 20 blocks; tears a stream out and
    holds it for a 3s window, then left click to throw for 8.0 damage. 100 chi, 8 xp,
    NO cooldown — the 3s window and the 100 chi are the whole limit. Chi is spent on
    the DRAW, so letting the window lapse costs the cast)
  - Water Bullets (three bullets held ready, fired ONE PER CLICK at 8.0 damage each,
    2.6 blocks/tick. No window — they keep until used. 100 chi, 10 xp, 2s cooldown
    starting from the LAST of the three)
- **An armed ability can hold several shots**: `TwoPhaseAbility.getShots()` (default 1,
  so Fireball, Water ball and Water stream are unchanged). The slot stays armed until
  every shot is spent and the cooldown waits for the LAST one, so a partly used ability
  is still held rather than thrown away by its first click. The HUD meter shows shots
  remaining when there is more than one, time remaining when there is a window, and
  simply "ready" otherwise.
- **An armed two-phase ability can now expire**: `TwoPhaseAbility.getArmedDurationTicks()`
  (0 = waits indefinitely, which is Fireball and Water ball) plus `onArmedExpire()`.
  `AbilityHandler.tickArmedTwoPhase` runs the countdown, applies the cooldown on expiry
  — the chi was already spent at arm time — and drains the HUD meter as the window
  closes, so the bar doubles as the timer.
- Water stream does NOT remove the water block it draws from. Draining sources would
  let a bender empty a pond a stream at a time, and with infinite sources it would only
  refill, so the pull is drawn rather than performed. Like Water heal it also opts out
  of `requiresWater()`: needing a body of water in SIGHT is stronger than the generic
  15-block rule, so charging a canteen unit for water in plain view would be nonsense.
- **An armed two-phase ability draws itself** via `TwoPhaseAbility.onArmedTick()`.
  `ServerEvents` used to draw flame for anything armed, which rendered a gathered body
  of water as fire — what is being held differs per ability, so the tick loop cannot
  get it right for all of them.
- **Projectiles are tracked, not entities**: `BendingProjectiles` keeps shots in flight in
  a static list, advances them from `ServerTickEvent.Post`, and draws them purely with
  particles. A custom projectile entity would need its own `EntityType` and a client
  renderer — and an entity spawning without a renderer takes the client down, which is
  a bad thing to ship untested. A mass of water is better drawn as particles than as
  any model anyway. Now serves Air splinters too — see the Spec/Style notes further
  down, added when it was generalised out of the water package.
  `LevelEvent.Unload` drops that level's shots, since nothing else holds them and a
  static list would otherwise keep a dead `ServerLevel` alive for the whole session.
- Water Defensive path COMPLETE:
  - Water shield (channeled; same shape and cost as Fire Shield — 25 chi/sec, 1 xp/sec,
    no cooldown, no cap, 200 chi to start — and gets its invulnerability from the same
    `grantsInvulnerability()` override. Four wheeling masses of water; anything within
    2.2 blocks gets Slowness II)
  - Water push (60-degree forward cone reaching 8 blocks, ~6 block knockback plus 1s of
    Slowness I. NO damage — pure control. 100 chi, 10 xp, 2s cooldown)
  - Water heal (channeled; Regeneration I while standing IN water, II with the "Potent
    Healing" upgrade. 15 chi/sec, 7 xp/sec,
    no cooldown. Refuses to start on dry land and ends the moment you wade out)
- **`ChanneledAbility.canContinue()`** (default true) is checked every tick and stops the
  channel when it returns false — for conditions that lapse while the key is still held,
  like Water heal's "must be standing in water".
- **Refreshing a MobEffect every tick stops it working.** Re-adding replaces the instance
  and resets its internal counter, and regeneration only heals on ticks where that counter
  comes round — so a per-tick refresh gives a permanent regeneration icon that never heals.
  Water heal re-applies only once the previous instance has EXPIRED — a 50 tick
  instance is exactly one Regen I beat, so this reproduces vanilla's rate exactly and
  leaves a stronger potion regen alone. Applies to any future
  effect-granting ability.
- **Ability upgrades**: abilities declare their own via `Ability.getUpgrades()`
  returning `AbilityUpgrade(key, name, description, cost)` — so an upgrade lives beside
  the code that reads it rather than in a table that has to be kept in step. Bought
  with levels, stored in `BendingData.unlockedUpgrades` (persisted + synced by
  `SyncUpgradesPacket`), checked with `data.hasUpgrade(key)`.
- **Right click an ability node** in the skill tree to pop its upgrades out beside it;
  right click again, or off a node, to close. The panel's left clicks are taken BEFORE
  `super.mouseClicked`, or the node buttons underneath would swallow them. Vanilla
  `Button` only accepts button 0, so right clicks reach the screen handler untouched.
- `BuyUpgradePacket` re-checks everything the menu checked — ability unlocked, upgrade
  real and belonging to that ability, not already owned, levels affordable. The client
  applies the purchase locally for an immediate response, but is not trusted.
- **Water heal has the first one**: "Potent Healing" (10 levels) raises it to
  Regeneration II AND lets the bender heal on snow (`BlockTags.SNOW` — layers, blocks
  and powder snow, underfoot or stood in).
  The effect DURATION is derived from the amplifier (`50 >> amplifier`),
  because regeneration's heal interval halves at II — a fixed duration would have left
  the upgrade claiming to heal twice as fast without doing so. Verified at exactly 50
  ticks per heal at I and 25 at II.
  Water heal itself sets `requiresWater()` FALSE: its own "must be standing in it" test
  is strictly stronger than the generic 15-block rule, so the canteen check would only
  ever bite in the case the snow upgrade exists to allow — charging a unit for water
  the bender is stood on.
- **Rooting**: `ChanneledAbility.rootsPlayer()` (both shields) pins the player. Done on
  BOTH sides — the server zeroes horizontal motion (leaving downward alone, so a shield
  is not also a hover), and `RootedPacket` tells the client to stop taking movement
  input. Server-side alone would leave the client walking and being corrected every
  tick, which rubber-bands instead of holding still. `ClientRootState` is a client
  static, so login AND respawn clear it — dying while shielded would otherwise leave a
  player who cannot move.
- **Water shield's four "blocks" are dense particle clusters, not real water.** Water
  source blocks placed and moved every tick would flood everything around the player,
  and keep flowing after the shield moved on. The slowing is applied directly to nearby
  entities, so the gameplay half does not depend on how the water is drawn.
- **Held abilities take canteen water once per activation, not per tick.** The check
  lives in `startChannel` as well as `performCast`; per-tick draw would empty a full
  canteen in one second.
- Air Defensive path COMPLETE:
  - Air pull (the design doc's Defensive "Airpush", renamed — its name was SWAPPED with
    Balanced's "Air pull", so Balanced #2 is now "Airpush" and nothing was lost. Both
    slots still exist, only the two names traded places). Water push and Fire push
    reversed: a 60-degree forward cone reaching 12 blocks that DRAGS everything in it
    towards the bender and leaves it Disoriented for 5s. No damage at all — pure
    control, like Water push. 100 chi, 10 xp, 2s cooldown. Pull speed SCALES with
    distance (`0.35 + 0.07 * d`, capped at 1.1) because knockback is an impulse
    decaying under drag: one flat value tuned for a target 3 blocks away leaves one at
    12 blocks barely stirring. Targets already within 1.5 blocks are only disoriented,
    or they'd be yanked straight through the bender and out the far side.
  - Air jump (CHARGED 2s and FIRES ON RELEASE, like Fire blow — the whole point is
    picking a height, so a charge that only paid out when full would waste 19 of the
    20 blocks. 5 blocks at the shortest press up to 20 at a full hold, and ZERO fall
    damage on the way down. 100 chi, 10 xp, no cooldown)
  - Air Aura (channeled; a shell of racing wind that turns PROJECTILES aside and
    cancels fall damage, but NOT melee — getting close enough to swing is how you
    beat it. 5 chi/sec, 1 xp/sec, no cooldown, no cap. Roots the bender, until its
    one upgrade "Buffeting Wind" (10 levels) adds melee protection AND frees them to
    walk while it is up, and doubles the rate to 10 chi/sec. XP stays at 1/sec)
  - Wind (one enormous gust across everything the bender can SEE — 20 blocks, one
    heart each and 20 SECONDS of Slowness I. Hits players as well as mobs. 150 chi,
    15 xp, 30s cooldown. The damage is a scratch; twenty seconds of Slowness on a
    whole screenful at once is the actual weapon)
- Air Offensive path COMPLETE:
  - Air splinters (CHARGE 2s to gather, then SIX splinters loosed one per left click.
    1.5 hearts each, 1s of Slowness I on hit, and they fly at 3.2 blocks/tick — faster
    than anything else the mod throws. 50 chi, 5 xp, 10s cooldown from the LAST of the
    six. Both held shapes at once, the way Fireball is, but with `getShots()` = 6 so
    the slot stays armed until they are all spent)
  - Air cannon (CHARGE 4s, then ONE shot for 7 hearts on a left click. Wide hit
    radius of 1.2 so a blast that took four seconds to build does not need pinpoint
    aim, and a full block of knockback. 100 chi, 10 xp, 10s cooldown from the shot.
    The opposite trade to Air splinters beside it: six quick cuts, or one blow that
    ends most things)
  - wind tunnel (CHANNELED funnel of wind: everything in a 12-block cone in front is
    shoved back, held at 5s of Slowness I, and worn down at 1 heart a SECOND. 10
    chi/sec, 2 xp/sec, no cooldown, no cap. The damage is the least of it — nothing
    caught in it can close the distance while it runs)
- Air Balanced path IN PROGRESS:
  - Air scooter (CHANNELED; a ball of air under the feet carrying the bender half a
    block off the ground and a little faster than sprint-jumping. 5 chi/sec,
    0.5 xp/sec, no cooldown, no cap)
- **Air scooter is built ENTIRELY out of mechanics the CLIENT simulates**, and that is
  the whole design. Holding a player at a height by setting their position server-side
  is the rubber band that made Fire Rocket's old height cap feel awful. Nothing in the
  scooter corrects the player at all — instead it is four vanilla mechanics the client
  already knows how to run:
  - a REAL invisible platform half a block above the ground carries them,
  - a **step height** attribute bonus (+0.6, so 1.2 total) glides them up over a rise —
    vanilla's own step-up is a smooth climb, not a hop, so it looks like the scooter
    riding over the lip,
  - **Slow Falling** gives the gentle drop when the ground falls away (and, usefully,
    no fall damage),
  - **Speed II** makes the ride quicker than running: ~7.9 blocks/sec sprinting against
    ~7.1 for sprint-jumping.
- **The step-height modifier is TRANSIENT** (`addTransientModifier`), so it is never
  written to player NBT. A permanent one would need the same login-and-respawn safety
  net Fire Rocket's flight flags do; this one cannot outlive the session however the
  channel ends. Worth preferring for any future attribute an ability grants.
- **The ground scan is deliberately SHALLOW** (4 blocks down). Ride off a cliff and
  there is simply no footing to lay, so the bender drifts down under Slow Falling until
  the ground is back within reach — which IS the slow descent the ability wants. A deep
  scan would build a floor in mid-air over the drop instead.
- **The scan skips the scooter's own platforms when looking for ground**, or each tick
  would build on the last and walk the bender steadily up into the sky.
- **`SurfPlatformBlock` now carries a `HEIGHT` property** (1 sixteenth for Water Surf's
  waterline, 8 for the scooter's half block) rather than being duplicated into a second
  invisible block. Same class, same `surf_platform` id — the name is now a little
  narrower than what it does.
- **`getXpPerSecond()` is a `double`, and XP is spread per tick like chi.** 0.5/sec is
  not expressible as an int, so the dispatcher now differences a running total against
  the CHANNEL's tick count (`xpForTick`) exactly the way `chiCostForTick` does — one XP
  on the 40th tick of the channel and nothing on the 39 before it. Whole rates are
  unchanged in total; they are simply trickled through the second instead of landing in
  a lump on the 20th tick.
- **Wind tunnel SETS the push velocity each tick, it does not add to it.** A force
  added every tick accelerates without limit; a wind has a speed it pushes things at.
  It falls off with distance (0.65 blocks/tick at the mouth down to 0.15 at 12), and
  vertical motion is left ALONE — setting y every tick would hold the target hovering.
  A target on the ground gets a skim of lift instead so it slides rather than grinds.
- **Only players get `hurtMarked` after a shove.** A player's client owns their
  movement and ignores server-side velocity unless it is pushed to them; a mob is
  simulated on the server, so marking it just sends a motion packet every tick for
  nothing. Worth copying for any future per-tick push over a whole cone.
- **Its damage lands on an explicit one-second beat** (`getChannelTicks() % 20`), not
  every tick. Per-tick hits would be spaced out by vanilla's invulnerability frames
  anyway, but that is working by accident — and the accident stops holding the moment
  another source of damage resets those frames.
- **Slowness is topped up, not re-applied every tick.** Every `addEffect` sends an
  update packet, so refreshing a whole cone of targets 20 times a second is pure noise
  on the wire; wind tunnel only re-applies once the instance has dropped below 80 of
  its 100 ticks. (For counter-driven effects, re-applying is worse than noisy — it
  breaks them outright. See Water heal.)
- **Air cannon takes the aim AFTER the wind-up**, like Fireball and Water ball, rather
  than firing itself the moment the charge fills the way Fire spikes does. Four seconds
  is a long time to hold a line on something that is moving, and the ability is a
  single shot with nothing to show for a miss.
- **An air shot's burst scales off its `hitRadius`.** That figure is already the mod's
  measure of how big the thing is, so a splinter pops and a cannon round bursts without
  needing a second Style or a separate size field. Water was left on its fixed figures
  rather than being retuned for the sake of it.
- **`WaterProjectiles` is now `abilities/BendingProjectiles`** — moved out of the water
  package and made element-agnostic like `HeldBlocks`, since Air splinters, Air cannon
  and wind tunnel all need it. Nothing in it knows what it is carrying.
- **A shot is described by a `Spec` record**, declared once per ability as a constant,
  rather than by eight positional arguments at the call site. It carries speed,
  lifetime, damage, hit radius, knockback, a `Style` (WATER or AIR, which is the only
  thing that differs in how it is drawn) and an optional on-hit effect.
- **The on-hit effect is a `Supplier<MobEffectInstance>`, not an instance.** A
  MobEffectInstance carries its own countdown once applied, so one shared between six
  hits would be six references to the same ticking object.
- **Shots now sweep their path instead of only testing the far end.** A tick's
  movement is walked in steps of at most 0.9 blocks, checking blocks and entities at
  each. Air splinters cross over 3 blocks a tick, which under the old single test at
  the destination would step clean through a wall — and past anything standing in
  front of it — without either ever being tested. This also fixes the same latent hole
  in Water Bullets, which travel 2.6.
- **"On your screen" is implemented as a view cone PLUS a line-of-sight check.**
  The cone is `dot >= 0.4`, about 66 degrees — deliberately WIDER than the real view
  frustum (Minecraft's default 70-degree vertical FOV is ~106 across on a widescreen,
  so ~53 degrees half angle), because something at the very edge of the screen should
  be caught rather than feel unfairly missed, and the player's FOV slider is a client
  preference the server cannot see. `player.hasLineOfSight` is what stops the gust
  going through terrain and hitting things in the cave below.
- **Wind uses `indirectMagic`, not the vanilla `wind_charge` damage type**, tempting
  as the latter was. `wind_charge` is in the `is_projectile` tag, which would make
  Wind reducible by Projectile Protection and blockable by our own Air Aura.
  `indirectMagic` also bypasses armour, so its flat one heart is one heart on a geared
  target, and it stays clear of the tags other abilities key off.
- **`ChanneledAbility.blocks(data, source)` is the finer-grained `grantsInvulnerability`.**
  The old all-or-nothing boolean could not express "arrows yes, swords no, and yes to
  fall damage as well", so `AbilityHandler.blocksDamage` now asks the ability per
  damage source. The DEFAULT reproduces the old behaviour exactly — everything except
  `IS_FALL` — so Fire Shield and Water Shield are untouched and still only override
  `grantsInvulnerability()`. `BYPASSES_INVULNERABILITY` (the void, `/kill`) is still
  handled by the dispatcher and no ability gets a say in it.
- **`getChiPerSecond()` now takes the player's `BendingData`**, along with the two
  figures derived from it (`getMaxChiPerTick`, `getMinimumChiToStart`), because a
  channel's rate can depend on what the player owns: Air Aura costs 5 chi/sec, and 10
  once Buffeting Wind is bought. Every other channel just ignores the argument. Done
  as a signature change rather than an overload with a default, so there is only ever
  one answer to "what does this cost" and no pair of methods that can disagree.
- **`rootsPlayer()` now takes the player's `BendingData`**, because rooting can be
  conditional: Air Aura pins the bender until Buffeting Wind is bought and then lets
  them move. The two shields just ignore the argument.
- **Air Aura's upgrade TRADES the hole in the defence for mobility rather than only
  adding to it.** A defence that stopped everything AND let you walk AND cost 5 chi a
  second would have nothing left to pay; dropping the root is what the melee coverage
  is bought with, and the rate doubles on top of that.
- **Melee is "has an attacker behind it and did not fly there"** — an attacker entity,
  not `IS_PROJECTILE`, not `IS_EXPLOSION`. That covers swords, zombie fists and thorns
  while leaving TNT out, since sheltering from an arrow is no reason to shelter from a
  blast. Everything with no attacker at all (fire, lava, drowning, poison, suffocation)
  lands normally: the aura is a shell against things aimed at you, not a bubble.
- **A blocked projectile still ARRIVES** — the damage event is cancelled, so the arrow
  deals nothing and imparts no knockback, but it still flies to the player and sticks.
  Actually deflecting projectiles would mean catching them as entities in `onTick`,
  which is a separate job from cancelling their damage.
- Air Aura is deliberately cheap (5 chi/sec against the shields' 25) and has no
  duration cap, so a 500-chi bender holds the base version for about 100 seconds — chi
  regen is re-delayed every tick by the channel, so it drains at the full rate
  throughout and never refills while up. The doubled rate is what stops the upgraded
  version being a free walking immunity: 10/sec halves that to ~50 seconds at level 0,
  and it is the ONLY brake, since there is still no cooldown and no cap. If it ever
  needs another, `getMaxDurationTicks()` is the remaining lever.
- **Air jump solves for its launch velocity instead of lerping it.** Height is not
  proportional to launch speed — drag means doubling the speed more than doubles the
  climb (0.42 -> 1.25 blocks, 1.0 -> 5.9, 2.0 -> 20.0) — so lerping the VELOCITY
  between two hand-picked values would land "half charged" well short of half height.
  It lerps the HEIGHT and binary-searches `peakOf()`, which just runs vanilla's own
  `v = (v - 0.08) * 0.98` step, for the speed that reaches it. That is what makes the
  block count it shows on the action bar while charging true.
- **The charge scale is measured from `getMinimumChargeTicks()`, not from zero.**
  Anything below the minimum never fires, so scaling from zero would make the smallest
  jump the ability can actually produce come out ABOVE its stated 5-block floor.
- **Air jump's fall protection is TWO guards, and the fallDistance one is the real
  one.** Cancelling `LivingFallEvent` alone was not enough in practice: it only works
  if the protection window is still open at the exact moment the landing is processed,
  and that is a tick-ordering question — `serverlevel.tick()` (where `AirJump.tick`
  runs, via `PlayerTickEvent.Post`) happens BEFORE `getConnection().tick()`, which is
  where a player's movement and therefore their landing and fall damage are handled.
  Holding `player.fallDistance` at zero every tick of the flight needs no such
  assumption: fall damage is computed from that banked distance, so there is nothing
  to convert into damage whichever tick notices the landing. The event cancel stays
  as the second guard, because it is also what suppresses the landing thud and dust.
- **The window closes on "has actually left the ground", not on a grace period.**
  `BendingData.airJumpLeftGround` is set the first tick the server sees the player
  airborne, and only then can touching down close the window. A timer-based grace was
  guessing at how long the client takes to apply the launch; this waits for it. A jump
  that never gets airborne at all (cast under a low ceiling) is dropped after
  `LAUNCH_TIMEOUT`, so it can't sit on free fall protection either.
- **`AIR_TIME` (400 ticks) is the backstop** — if a landing is somehow never seen at
  all, the protection expires by itself rather than lasting the session. The flags are
  transient, so a relog clears them regardless.
- Air jump can be cast in MID-AIR, which chains: nothing checks for ground. Chi is the
  only limit (100 a jump against `500 + level*100`), and it is very much an airbender
  thing to do — but it is a deliberate choice, not an oversight, and one `canStart`
  returning `player.onGround()` would close it.
- **Disorientation is the mod's first real MobEffect** (`ModEffects` +
  `DisorientationEffect`, a `DeferredRegister<MobEffect>` on `Registries.MOB_EFFECT`
  registered from the Atlamod constructor). Nothing in vanilla reverses a player's
  controls, so it could not be a repurposed vanilla effect — and registering it
  properly buys the whole status-effect UI for free: inventory icon, timer, particles,
  and automatic syncing to the affected player's client.
- **The effect class itself holds NO behaviour, deliberately.** Movement input exists
  only on the CLIENT — the server sees the resulting motion, not which key produced it
  — so the reversal is done in `ClientEvents.onMovementInput`
  (`MovementInputUpdateEvent`, NeoForge game bus, client only), which just asks
  `hasEffect(DISORIENTATION)`. Vanilla already syncs the effect to the owning client,
  so no packet of our own is needed. Any future "changes how the player controls"
  effect should follow the same split.
- **Both the impulses AND the direction booleans are flipped.** `forwardImpulse` /
  `leftImpulse` are what actually move the player, but `Input.up/down/left/right` are
  what vanilla reads for things like sprint detection — flipping only the impulses
  gives a player who walks backwards while still sprinting the way they pressed.
- **Mobs receive Disorientation but are unaffected by it** — they have no keys to
  reverse. Air pull applies it to everything it catches anyway, so the effect shows on
  them and future logic can key off it.
- A registered MobEffect needs `assets/atlamod/textures/mob_effect/<name>.png` (18x18)
  or the inventory shows the magenta checkerboard — same trap as the element icons.
- **FIRE IS COMPLETE — all 16 abilities across all 4 paths.**
- **WATER IS COMPLETE — all 12 abilities across all 4 paths.** 18 left across
  Air/Earth × 4 paths
- Previously built with Gemini; switched to Claude as primary coding partner because
  Gemini was getting inconsistent on a project this size

## Working Style

- Prefers step-by-step guidance and practical, hands-on troubleshooting over abstract explanation
- Comfortable with a full class rewrite if it's a clear improvement over patching —
  don't hesitate to propose one, changes are easy to revert with Ctrl+Z / git
- Building solo (well — solo + AI), so keep explanations of *why* a change was made,
  not just the diff
