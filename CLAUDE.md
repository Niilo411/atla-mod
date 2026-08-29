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
- Masterclass: breathless-10, Tornado-15, Flight-5s (the design doc's "Air beam" was
  DROPPED — it is gone from the skill tree and will not be built)

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

## Earthbending

- **`abilities/earth/EarthWorks.java` holds earthbending's shared rule**, and every
  earth ability that moves blocks should go through it: an ability may only ever fill
  AIR, and whatever it fills it takes back afterwards. Between those two, no earth
  ability can destroy anything or leave anything behind. That matters far more for
  earth than for the other elements — a wall that simply stayed would be an infinite
  block supply and would litter the world with every cast.
- **Earth SLIDES rather than appearing**, using `FallingBlockEntity` the same way
  HeldBlocks does: a real entity so the block is genuinely visible in motion, gravity
  off and its own `time` pinned at 0 so it never drops itself as an item or places
  itself. The real block is only set when the slide LANDS, so a block is never in two
  places at once and nothing is ever stood on half of one.
- **Raised earth MIRRORS the ground it came from** (`materialFor`), so a wall out of a
  hillside looks like the hillside instead of every ability everywhere producing the
  same brown blocks. Two exceptions, both load-bearing: anything that FALLS is swapped
  for dirt, since a wall of sand would collapse the instant it went up, and anything
  that is not plain diggable full-block ground falls back to dirt rather than
  duplicating whatever a player happened to be standing on.
- **A block only sinks back if it is still the block we put there.** Someone may have
  mined it, built over it, or had another ability replace it in the meantime, and
  removing whatever occupies the space now would be exactly the griefing the air-only
  rule exists to prevent.

## Current Status

- Fire Offensive path COMPLETE: Fire Leap, Fire Whip, Fireball (hold 2s to build,
  then LEFT CLICK to throw; 100 chi + 10 xp on completing the charge, 2s cooldown
  from the throw), Fire Breath
  (channeled cone of flame, damages + ignites entities in a 6-block line;
  25 chi/sec, 2 xp/sec, 10s max duration, 15s cooldown after it ends)
- Fire Defensive path COMPLETE:
  - Fire Push (6.0 damage, sets everything it catches alight for 5s, ~6 block
    knockback in a 60-degree forward cone reaching 8 blocks, 100 chi, 2s cooldown,
    5 xp)
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
- **Ability icons**: `client/AbilityIcons.java` maps an ability to a PNG under
  `assets/atlamod/textures/gui/abilities/`, drawn on its skill tree node in place of
  the old "?" box. Same shape and same rules as `ElementIcons` below — only abilities
  listed in its map are drawn, because a texture Minecraft cannot find renders as the
  magenta checkerboard. With 60+ abilities and 7 pictures the "?" fallback is the
  NORMAL case here rather than an edge case.
  Sources are expected to be **256x256**, scaled through the pose stack.
  The map KEY is the display name lowercased — the same key the registry and the
  cooldowns use — so the tree, the registry and the icon table cannot drift apart
  without the icon simply not appearing.
  **File names are separate from ability names on purpose**: a ResourceLocation path
  may only hold lowercase letters, digits and a little punctuation, so "Fire whip"
  has to live on disk as `fire_whip.png`. Spaces and capitals in a texture file name
  throw at load rather than failing quietly.
  Adding art = the PNG in that folder + one `put()` line.
  The equip and passives tabs deliberately do NOT show them: their rows are 70x20 with
  a centred name in them already, and an icon would need those laid out again.
- **Element icons**: `client/ElementIcons.java` maps an element to a PNG under
  `assets/atlamod/textures/gui/elements/`. Only elements listed in its `ICONS` map
  are drawn — anything else keeps the old "?" box, because a texture Minecraft cannot
  find renders as the magenta checkerboard, which looks far more broken than a
  placeholder. Source PNGs are expected to be **256x256**; the icon is scaled through
  the pose stack rather than by blit's arguments, since blit's width arguments set the
  source region as well as the drawn size and so cannot resize on their own.
  Used by BOTH the element selection screen and the HUD badge.
  Adding an element = drop the PNG in that folder + one line in `ICONS`.
  ALL FOUR base elements have art now; the sub-elements are still on the "?" box.
  NOTE: source art must be a REAL PNG. The FIRST fire emblem arrived as a JPEG carrying
  a `.png` extension and had to be re-encoded (JDK `ImageIO`) to a genuine 256x256 ARGB
  PNG. Every emblem since has been checked at the file level before being put in — PNG
  signature `89 50 4E 47`, and 256x256 read straight out of the IHDR chunk — which is
  worth doing every time rather than trusting the extension.
  NOTE ALSO: the FILE NAME has to be lowercase with no spaces, the same rule the
  ability icons document. The air and earth emblems arrived as `Air_icon.png` and
  `Earth icon.png`, neither of which is a legal ResourceLocation path — a capital or a
  space there throws at load rather than failing quietly, so they are renamed on the
  way in rather than worked around in the map.
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
- Commands: `/bend add|remove <targets> <element>` and `/bend level <targets> <amount>`.
  Note `/bend level` bumps level without touching xp, so the two can drift.
- **Every `/bend` command names WHO it acts on, and the target is not optional.** They
  used to act on whoever typed them, which made them useless for setting anyone up on
  a server. The argument is `EntityArgument.players()`, so `@s` is the self case and
  `@a` does everybody in one go. There is deliberately no second "just me" form beside
  it: Brigadier would have to tell `/bend add fire` from `/bend add Steve fire` by
  trying one branch and falling through to the other, and a mistyped element name would
  then be reported as a missing player. All three report what they did and to how many,
  since a command acting on somebody else is otherwise silent to the person who ran it.
- **EVERY `/bend` command needs permission level 2**, which is exactly "cheats enabled
  in singleplayer, or op on a server" — vanilla ties those two together, so one check
  covers both. The `requires()` sits on the ROOT rather than on each subcommand, so
  anything added under `/bend` later cannot be left ungated by being forgotten, and the
  whole command is hidden from the suggestion list for anyone who cannot use it rather
  than being offered and then refusing.
- **Picking your element starts you at level 1**, not 0, granted in
  `ElementChoicePacket`'s handler. Two details there: the "is this their first
  choice" test has to be read BEFORE `setMainElement`, which sets `hasChosenElement`
  itself, and the handler has to send `SyncStatsPacket` back — the client sets the
  element locally for an instant response, but the level is decided server-side and
  the HUD would otherwise read "Lvl: 0" until the next stat sync. Level 1 also means
  600 max chi rather than 500, since `getMaxChi()` is `500 + level*100`.
  Only applies to a FIRST choice, so it can't be farmed by re-sending the packet —
  which also means saves that had already chosen an element before this existed stay
  at level 0 and need `/bend level 1`.

## Lightningbending (the first SUB-element)

Not one of the four: it is not chosen at the start, it has only TWO paths instead of
four, and it is earned rather than picked.

- **Getting it**: a village WEAPONSMITH sells the Lightningbending Scroll for 64
  copper ingots. Right click to read it. It only opens for a bender who has completed
  **2 or more fire paths**; anyone else is refused and KEEPS the scroll, because a
  scroll that burned itself on a failed reading would cost 64 copper to discover a
  requirement the game never stated. A successful reading calls a bolt down in front
  of the reader and adds `lightning` to their unlocked elements — the `[Y]` switcher
  is generic over that list, so it appears there with no extra work.
- **The trade is registered at weaponsmith levels 1 AND 3.** A villager picks only a
  couple of trades at random from each level's pool, so a single entry would be a coin
  flip per weaponsmith. Two entries give a fresh smith a good chance and a levelled one
  a second, without the same trade appearing twice in one tier.
- **Two paths map onto the tree's LEFT and RIGHT arms**, so `UpgradeMenuScreen` needed
  nothing but two lines in `getOffensive`/`getDefensive`. The top and bottom arms come
  back empty for lightning and simply draw nothing. The existing "finish one path
  before starting another" rule then applies unchanged, since `isPathComplete` returns
  false for an empty array and the masterclass gate is never reachable.
- **Both scrolls ask `ElementPaths.completedPaths`** — see the note on that class under
  Icebending. The lightning scroll originally kept its own copy of fire's path lists,
  since `UpgradeMenuScreen` is client-only and the check has to run on the server; the
  ice scroll needing the same thing for water is what finally paid for extracting them.
- Left path: Lightning redirection, Lightning aura, Lightning Jump, Lightning Strength
- Right path: Lightning bolt, Lightning ball, Lightning stun, Lightning Swarm

- **`Lightning.java` holds the element's shared parts**: the Lightning Strength damage
  bonus, and the two ways to put a bolt in the world. `visualStrike` is what nearly
  everything uses — a real bolt would add five points of damage nobody asked for and
  set the world alight every time a bender moved. `realStrike` exists for exactly one
  caller, Lightning bolt's "Storm Caller" upgrade, where calling down actual lightning
  IS the upgrade.
- **Lightning Strength's damage bonus is applied BY THE ABILITIES** (`Lightning.damage`),
  not in the incoming-damage handler where Blue Fire's lives. Blue Fire could be done
  there because `IS_FIRE` with a player behind it is a usable signature for "a fire
  ability did this". Lightning damages through `indirectMagic`, which Wind and every
  projectile in the mod also use — keying off it there would quietly buff half the
  other elements too.
- Its other two halves are elsewhere for the same reason: the Speed is topped up from
  the player tick (`LightningStrength.tick`), and the doubled chi regen is applied in
  `ServerEvents`' regen block, the only place that knows how much is being handed back.
- **Stunned is the mod's second custom MobEffect**, and needed to be one for the same
  reason Disorientation did: nothing in vanilla stops a player walking. It is split in
  two — the client throws the player's input away in `ClientEvents.onMovementInput`
  (movement input only exists there, and vanilla syncs the effect to its owner), and
  `ServerEvents.onEntityTick` zeroes velocity and stops navigation. That server half is
  the WHOLE of it for mobs, which have no keys to take away. Downward motion is left
  alone in both, so a stun is not also a hover.
- **`ScreenFlashPacket` / `ClientFlash`** are the stun's white-out, following
  `EarthquakePacket` / `ClientShake` exactly: a flash has no server-side existence, so
  the server can only ask. Counted down on the client TICK, not in the HUD layer, which
  runs once per FRAME — and the layer is handed a `DeltaTracker`, not a float, so the
  partial tick has to be asked for.
- **`BendingProjectiles.Spec` gained a generic `onImpact` hook**, fired from `burst()`
  — the single place a spent shot already funnels through however it ended (target,
  wall, or running out of life). That is what "summons lightning on what it hits OR
  where it lands" needs, and it is a `BiConsumer` rather than a lightning flag so the
  generic projectile class stays element-agnostic.
- **Lightning redirection is a CHANNEL and a TWO-PHASE in one class**, and the seam
  between them is the two phases. `absorb()` is called from `BendingProjectiles.strike`
  — the only place that knows a bolt was about to land on somebody — and ends the
  channel BEFORE arming, which is what stops the chi and the xp at phase 2 exactly as
  the design asks. It returns WITHOUT `burst()`, deliberately: the shot never landed, so
  a caught Storm Caller bolt must not call real lightning down on the catcher.
- `AbilityHandler` gained two public helpers for this: `armTwoPhase` (arm without
  casting, since catching a bolt is not a cast the catcher paid for) and `endChannel`
  (stop a channel in response to something in the world rather than to a key release).
- **The caught bolt's strength is remembered on `BendingData.caughtLightning`**, because
  the shot is gone by the time the left click comes. A redirected bolt hits for whatever
  it was going to hit the catcher for. Transient, so a relog mid-hold falls back to an
  ordinary bolt's damage rather than firing for nothing.
- **Only PROJECTILES can be redirected.** The aura and the ball are fields of current
  rather than something thrown at anyone, and there is nothing in flight to take hold of.
- **`LightningBalls` is Tornado's behaviour, not Air spout's**: every ball has an owner
  and rides their crosshair, so there is no such thing as one that was set down and left.
  Moved TOWARDS the crosshair at a capped speed rather than snapped to it, or flicking
  the view would teleport it twenty-five blocks in a tick.
- It iterates a SNAPSHOT, like every other manager that can kill something — a player
  killed by a ball fires `LivingDeathEvent`, whose handler calls `forgetPlayer`, which
  removes from the very list being walked.
- **A ball does NOT spare its owner**, and passing `null` to `getEntities` is what makes
  that work — the first argument is the entity to SKIP. It is a hazard hanging in a
  place, like an Air spout, and steering yours into your own face should hurt.
- Lightning aura and Lightning ball both damage on an explicit ONE-SECOND beat rather
  than per tick. Per-tick hits would mostly be swallowed by invulnerability frames, but
  that is working by accident — the moment anything else resets those frames they would
  hit far harder than advertised. Same reasoning as wind tunnel.
- **Lightning Jump walks its path a block at a time** rather than teleporting straight
  to the far end, which is what stops it being a way through walls: the walk stops at
  the first square with nowhere to stand, so a jump into a cliff puts the bender against
  it rather than inside or beyond it. The aim is FLATTENED first — following the pitch
  would bury them in the floor when looking down.
- **Lightning Swarm gathers its targets BEFORE striking any of them**, because the
  damage depends on how many there are. Hitting them as they were found would make the
  first target's damage depend on nothing and the last one's on everything.

### Lightning upgrades

- **Lightning stun — "Lasting Shock" (10 levels)**: the stun holds 6 seconds instead
  of 3. The COOLDOWN is deliberately untouched: 6 seconds of hold against 25 of
  cooldown is still a window rather than a lock, which is the shape the ability keeps
  however much is spent on it.
- **Lightning Swarm — "Unbroken Storm" (25 levels)**: every target takes the full 25
  however many there are, the reach doubles to 20 blocks, and the CHI COST rises from
  150 to 1000. Priced at more than
  twice the stun's upgrade because it does not add a number, it REMOVES the ability's
  own drawback — the sharing was the whole trade, and without it the swarm is "one
  target hard" and "a crowd hard" at the same time over four times the area.
- Both read through `data.hasUpgrade(key)` in small helpers (`stunTicks`, `radius`,
  `damageFor`) rather than inline at the call site, so the base figure and the upgraded
  one sit next to each other and the constants keep meaning the unupgraded value.

### The lightning wind-up

- **Every lightning ability serves a MINIMUM one-second wind-up**
  (`Lightning.MINIMUM_CHARGE_TICKS`). Nothing in the element fires on the press. It is
  a minimum, not a fixed figure — Lightning bolt already takes five seconds and keeps
  them.
- **Lightning bolt hits for 16, cut from the design's 20.** Still the hardest single
  projectile in the mod, but a full-health player now survives one rather than being
  ended by a shot they may never have seen coming.
- **Lightning Strength is the one exception, and barely one**: a passive is never cast,
  so there is no moment of use to put a wind-up in front of.
- The four cast shapes became `ChargedAbility` (Jump, stun, Swarm, ball). That needed
  no client change at all: the client already sends BOTH `UseAbilityPacket` and
  `AbilityHoldPacket` on every key press, and `executeAbility` refuses charged shapes
  so the hold path picks them up.
- **A toggle must switch OFF instantly, so `startCharge` now checks `isActive` at the
  very top** — before the held-ability guard, the cooldown and the chi check, exactly
  where `performCast` checks it. Lightning ball costs a second to send out; calling it
  back is immediate. Switching something off must never be refused, charged for, or
  made to sit through the wind-up again. The key release that follows lands in
  `cancelCharge`, which finds no charging ability and returns — the same guard that
  stops a fired charge double-firing.
- **Channels get `ChanneledAbility.getWindupTicks()` instead**, because a channel
  cannot also be a charge: the dispatcher allows only one held shape at a time, and a
  channel is already "hold the key". So the wind-up is a quiet opening stretch — chi
  IS still drained and the duration cap IS still counting, `onTick` simply is not
  called and `onWindupTick` is called instead. Holding through it is a real
  commitment, not a free run-up. Default 0, so every channel outside lightning is
  untouched.
- **`isReady(data)` is worth asking from outside the tick**, and Lightning redirection
  is why: its `absorb()` refuses during the wind-up, so the catch has to be raised
  BEFORE the bolt is thrown rather than in reaction to seeing one coming. That is the
  wind-up doing real work rather than being decoration.
- Lightning aura's one-second damage beat is measured from the END of the wind-up
  (`(channelTicks - windup) % HIT_EVERY`), so the first shock lands exactly as the aura
  comes up instead of wherever the beat happened to fall during it.
- `Lightning.gather()` draws the wind-up for all six, so the whole element reads the
  same way while charging.

### `getChiCost` now takes the player's BendingData

- Changed across all 55 abilities so Lightning Swarm can cost 150 ordinarily and
  **1000 with Unbroken Storm**. Done as a SIGNATURE change rather than an overload with
  a default — the same call already made for `getChiPerSecond` — so there is only ever
  one answer to "what does this cost" and no pair of methods that can disagree. The
  compiler verified every site; the three readers are all in `AbilityHandler`.
- This is a different mechanism from Mine's, which is still the right one there: Mine's
  price scales with how long its charge was held, which is not knowable until `execute`
  runs, so its `getChiCost` is a base and the extra is taken inside the cast. Swarm's
  price depends only on what the player OWNS, which is knowable up front — so it can be
  gated and taken the ordinary way, and the "Not enough Chi! (Requires 1000)" message
  is then correct for free.
- **An upgraded Swarm is uncastable below level 5**, since `getMaxChi()` is
  `500 + level*100`. A gate rather than a bug, and the same one Fire Rain and Tsunami
  have — anyone with 25 levels to spend on the upgrade is long past it.

## Icebending (the second SUB-element)

Same shape as lightning: two paths, not chosen at the start, unlocked with a scroll.

- **Getting it**: a village FISHERMAN sells the Icebending Scroll for one Heart of the
  Sea, at trade levels 1 and 3 (same two-entry reasoning as the weaponsmith's). Right
  click to read; the scroll burns itself and lays a 5x5 patch of snow around the reader
  as confirmation.
- **It needs 2 completed WATERbending paths**, exactly as the lightning scroll needs 2
  fire paths. Ice comes out of water the way lightning comes out of fire, so the gate is
  deliberately identical. A reader who is short keeps the scroll rather than burning it.
- **`abilities/ElementPaths.java` holds every element's path tables**, in COMMON code.
  They used to live in `UpgradeMenuScreen` alone, which was fine while the tree was only
  ever drawn — it stopped being fine when the scrolls needed to ask "has this player
  finished two paths of X?" on the SERVER. The first scroll kept a copy; a second would
  have made two copies of two different tables, all kept in step by hand. The menu now
  delegates to the shared tables like everything else, so adding an ability to a path is
  one edit again.
- `ElementPaths.isComplete` treats an EMPTY path as never complete, and that matters:
  without it a sub-element's two missing arms would each count as finished and any
  "how many paths are done" check would start at two.
- Left path: icicles, Freeze, Ice over, Ice barrage
- **Every two-phase ability needs `canStart` to refuse while one is already armed**, and
  icicles was the one that did not have it. Pressing the key with shards already
  gathered simply gathered them again — another 100 chi, the armed slot reset, and
  nothing thrown. Worth checking on any new two-phase ability, since the armed state
  waits indefinitely and there is never a case where re-summoning is what was wanted.
- Right path: Ice sphere, Ice Bomb, Freezing Beam, Ice Breath

- **`IceWorks` is icebending's `EarthWorks`** and keeps the same rule, which matters as
  much here: an ability may only fill AIR, and takes back exactly what it filled.
  Without it Ice over would pave a permanent rink every fifty seconds. `IceWorks` has a
  SECOND half for that one though — `freezeOver` deliberately breaks the air-only rule
  and turns the ground itself to ice, remembering the block it replaced and giving that
  exact block back. Laying a sheet on TOP of the ground raised the floor a block
  wherever it went: a step to trip over at the edge of the area, and a sealed doorway
  wherever it met one. Freezing the ground is what "the ground goes over" means, and it
  leaves the world exactly as tall as it was. That makes it `MetalWorks.lay`'s shape
  rather than this class's usual one, and it carries the same refusals — never over
  another ability's ice (the second timer would restore the FIRST one's ice as "the
  original"), never a block entity, never a fluid. What it does NOT
  share is the sliding — earth rises out of the ground as a FallingBlockEntity, where
  ice simply forms where the cold is.
- **Everything structural uses PACKED ice, never plain ice.** Plain ice MELTS on a
  random tick near light and leaves WATER — at which point `IceWorks` sees a block that
  is not the one it placed, correctly refuses to remove it, and the ability has quietly
  flooded somebody's build. Packed ice never melts and is just as slippery. Snow is safe
  for the opposite reason: it melts to AIR, which is where it was going anyway.
- **Freeze's damage immunity is not a balance decision, it is what makes the ability
  work.** Two blocks of ice put a solid block where the victim's eyes are, and vanilla
  suffocates anything in that position — a victim who could be hurt would simply be
  killed by their own shell. `Frozens` owns both halves and always ends them together.
  Checked FIRST in the damage handler, above the shield cancel, since a shell is more
  absolute than any other rule; `BYPASSES_INVULNERABILITY` still lands.
- That also makes Freeze genuinely double-edged, which is the point: ten seconds where
  a target can neither act nor be finished. Freezing something your allies are killing
  is a mistake.
- **Ice barrage is built out of falling PROJECTILES**, not a tracker of its own — that
  is what the generic `onImpact` hook added for Lightning bolt is for. Each icicle is an
  ordinary shot launched straight down: the projectile system already sweeps its path
  (so it cannot fall through a roof), already carries the damage, and already calls back
  wherever it stops. The ability only supplies what to plant.
- Its icicles are scattered by AREA, not by radius — picking a uniform radius bunches
  everything near the caster, since a ring at r=30 holds ten times the ground of one at
  r=3. The square root spreads them evenly.
- **0 to 6 targets are SINGLED OUT per cast** and get 2 to 6 aimed icicles each, on top
  of the scattered forty. The scatter alone is exactly that — with bad luck a whole
  barrage could miss everything, which after a five second wind-up on a twenty second
  cooldown reads as the ability not working. But guaranteeing a hit on EVERYTHING in a
  thirty block radius made it an unavoidable wipe of any group, so the count is rolled
  fresh each cast and that many targets are drawn at random, WITHOUT replacement — six
  distinct targets at most. A roll of zero is possible and leaves the cast entirely to
  the scatter. The aimed ones spawn only 3 blocks above the target's head:
  at 1.6 blocks a tick that lands in about two ticks, and a sprinting player covers
  under 0.3 a tick, so nothing can walk out from under them. No special case in the
  projectile system was needed — just a much shorter drop.
- **Those aimed icicles PIERCE invulnerability frames**, and that is load-bearing:
  vanilla ignores a second hit of equal size within ten ticks, so without it only the
  first of each handful would land and the rest would be silently discarded. Same trap
  Earth Splinters documents.
- The guarantee holds for anything under OPEN SKY. An icicle aimed at somebody stood
  under a roof bursts on the roof, which is inherent to an ability that drops things
  from above rather than a gap in the aiming.
- **The dripstone tip goes on LAST.** A downward-pointing dripstone needs something
  solid ABOVE it to hang from, so a tip placed while that space is still air breaks
  itself the instant it lands and the icicle comes out headless.
- **`IceBombs` deliberately does NOT use `HeldBlocks`**, despite the carry looking
  identical. HeldBlocks takes a REAL block out of the world and is careful to put it
  back, because losing one would destroy terrain; an ice bomb is summoned out of nothing
  and is meant to be destroyed at the end, so every guarantee HeldBlocks offers would
  work against it.
- **It still hit the `FallingBlockEntity.fall` trap**, which is worth remembering: that
  method CLEARS the block at the position it spawns in. HeldBlocks gets away with it
  because the block it names has just been removed on purpose. Summoning one out of
  nothing had to save and restore whatever was there, or every cast deleted whatever was
  floating three blocks in front of the bender.
- Ice bomb, Lightning ball and Air spout all pass `null` to `getEntities` rather than
  the owner: the first argument is the entity to SKIP, and all three are hazards put in
  a place rather than spells aimed at somebody. Standing in your own blast should hurt.
- **A thrown bomb outlives its bender's presence; a carried one does not.** Once it is
  out of their hands it is a hazard with its own clock, and should still go off if they
  log out — the same distinction `AirSpouts` draws between a Tornado and a placed spout.
- **Ice Breath reuses lightning's Stunned effect**, which is the second cross-element
  reuse in the mod (the first being breathless borrowing `Drownings`). A second of stun
  every two seconds is the real weapon; four hp a second is modest by comparison.
- Lightning aura, Lightning ball, Freezing Beam and Ice Breath all damage on an explicit
  ONE-SECOND beat rather than per tick, for the reason wind tunnel documents: per-tick
  hits are only spaced out by invulnerability frames, and that stops holding the moment
  anything else resets them.

### Two figures that were NOT in the design

- **Ice Bomb's chi, xp and cooldown** (100 / 10 / 5s) — the design gives it none.
- **Its FUSE is now 2s, cut from 5.** Five seconds was long enough for anything with
  legs to walk out of a four-block blast and come back afterwards, which left the bomb
  doing nothing but marking a square of ground as briefly unpleasant. The delay is
  still the ability — it cannot be aimed at anything moving — just a shorter one.
- **Ice Bomb's blast damage** (8.0 in a 4-block radius) — the design says only "a ton of
  ice particles". A thing called a bomb that did nothing but sparkle read as an omission
  rather than a decision, so it hits. Both are flagged INVENTED in the source.

### One interpretation

**Freezing Beam** is specced as firing "when holding left click". There is no
held-left-click signal in the mod — a left click reaches the server as a single event —
so the click STARTS the beam and it runs its twenty seconds on its own. That also reads
better with the stated duration, which nobody could otherwise reach without holding a
mouse button down for twenty seconds.

A running beam is **switched off by pressing its own keybind again**, through the same
`isActive`/`deactivate` hook Tornado, Air scooter and Lightning ball use. That hook is
checked at the very top of `startCharge`, before the held-ability guard, the cooldown
and the chi — which is the whole reason it exists here: the beam runs twenty seconds
behind a ten second cooldown, so a cancel routed through the ordinary cast path would
be refused as "on cooldown" for the first half of its life and would charge another
150 chi when it finally worked. Cancelling also skips the three second wind-up, since
nothing needs building to put something down.

## Soundbending (the third SUB-element)

Same shape as lightning and ice: two paths, not chosen at the start, unlocked with a
scroll gated behind two completed paths of the parent element — AIR here.

- **Getting it**: a village FLETCHER sells the Soundbending Scroll for 32 feathers, at
  trade levels 1 and 3. Reading it needs 2 completed airbending paths, plays a loud
  screech, and destroys the scroll. A reader who is short keeps it.
- Left path: Bass Bounce, Sound boosting, Sound wall, Sound Leap
- Right path: Roar, Deafen, Compressed punches, Bass waves

### Sound boosting is the broadest passive in the mod

It touches TWO WHOLE ELEMENTS rather than one ability — every air and sound ability
gets +25% damage, +25% effect duration, and −25% cooldown and charge time. Its four
halves live in three places because no single hook could carry all of them:

- **Damage and effect durations are applied BY the abilities** (`Sound.damage`,
  `Sound.duration`). A damage-handler rule could not tell an air ability from any other
  `indirectMagic` in the mod — the same trap Lightning Strength documents.
- **Cooldowns and charge times are applied by the DISPATCHER** (`Sound.shorten`, via
  `AbilityHandler.cooldownFor` / `chargeTicksFor`). Those are read in five places no
  ability class touches, so asking each ability to shorten its own would be a rule the
  next one added would quietly break. This half therefore covers every air and sound
  ability automatically, including ones added later.
- **`ElementPaths.elementOf` is what decides who qualifies.** Whichever tree an ability
  sits in IS its element — there is no separate label on the ability that could
  disagree with the tree.
- The HUD charge meter fills against the SHORTENED total, so the bar matches the charge
  actually being served.

### Deafened is the mod's third custom effect

- Like Disorientation and Stunned it holds NO behaviour, for the same reason: sound
  exists only on the CLIENT. `ClientEvents.onPlaySound` cancels every sound the game
  tries to play while the local player carries it. Not filtered by category — muting
  effects but leaving music would be a half-deafness nobody asked for.
- Mobs receive it and are unaffected, exactly as they are by Disorientation.
- **Deafen's bending lockout is a SEPARATE mechanism**, and stays one even now that
  both halves run 10 seconds (the deafness was cut from 25, which ran on well after
  the half that actually decides a fight had lapsed). The deafness is a MobEffect and
  lands on mobs too; the lockout is a transient counter on `BendingData` checked at the
  top of both dispatcher entry points, and only means anything to a bender. Two effects
  for one ability would also be two icons for a single thing.
- **Only a key PRESS is refused while locked out, never a release.** A channel already
  running when the lockout lands has to be able to be let go of, or it would drain chi
  until it ran dry.

### The rest

- **A bass wave stuns for ONE second, cut from three.** A wave goes out every four
  seconds, so at a three second hold the stun was very nearly continuous for anything
  that stayed in range — each wave landed while the last one's hold was still running.
  At one second the waves punctuate rather than lock.
- **Roar hits for 4 hp and disorients for 5s** (cut from 10s, and it had no damage at
  all). Still mostly control — two hearts across a whole crowd is a scratch — but a
  shout that took nothing off anything read as half an ability beside the rest of the
  path. The damage goes through `Sound.damage`, so Sound boosting raises it.
- **Bass waves PINS the bender for the fifteen seconds it throws**, using the same two
  halves every rooting channel does — `AbilityHandler.setRooted` for the client and
  `holdStill` on the server every tick, both made public for it since rooting was
  previously a channel-only affair. The pin lifts the moment the LAST wave goes out
  rather than when it lands, so leftover rings finish travelling on their own time.
  Every route out releases (cancelled, finished, dead, gone), because ClientRootState
  is a client static and a missed release leaves a player who cannot move.
- **Compressed punches' cooldown starts when it ENDS, not when it starts** — the
  channel rule rather than the toggle one, and deliberately so: with the cooldown
  running from the cast, a bender could hold it for its full thirty seconds and switch
  straight back on the moment it dropped. `CompressedPunches.stop` is the single exit
  all three endings go through (pressed off, out of time, out of chi), so the thirty
  seconds applies uniformly. That matters most for the deliberate toggle-off, which
  reaches it through `deactivate` — the dispatcher's toggle path skips the cooldown
  stamp entirely, so without stamping it there that route would be free.
- At 25 chi a second its thirty second cap costs 750 chi to run dry, which is more than
  a bender below level 3 can hold at all. The cap and the pool between them make the
  full thirty seconds something to grow into.
- **Compressed punches is two effects, not one.** The WAVE goes out on every left click
  whether it connects or not (fired from `LeftClickTriggerPacket`, before the two-phase
  routing, so an armed ability does not swallow the punch); the harder direct hit is
  applied in the damage handler, where melee damage is actually decided. Set rather
  than added, so it does not stack with a weapon.
- **Both its figures were halved together** (wave 6 -> 3, punch 10 -> 5), because being
  two effects is exactly why: a connecting punch was throwing a free ranged attack AND
  raising the melee, so it was being counted twice at full strength.
- **Compressed punches and Sound wall are TOGGLES billed by the second**, which the
  dispatcher's channel billing does not reach — they are charged from the player tick
  by `ServerEvents.chargeSoundToggle` on the same one-second beat, and switch
  themselves off when the chi runs out. Spending goes through `consumeChi`, so the
  regen delay is re-armed each second exactly as it is for a channel; a toggle that
  regenerated its own upkeep would be free.
- **A bass wave is a growing RING, not an area hit.** Each thing is struck once as the
  wave passes over it, caught in the shell between last tick's radius and this one's.
  That is the whole difference between a wave and an aura. Waves already travelling
  finish their flight after the fifteen seconds are up rather than vanishing mid-air.
- **Sound wall places no blocks at all** — it is particles, and it stops movement by
  pushing back rather than by colliding. Anything inside the plane is shoved out the
  side it came in on, every tick, which reads as solid without a single block existing.
  Projectiles are discarded outright: an arrow that kept flying but did no damage would
  still stick in whoever is behind the wall. The caster passes through freely, since
  cover you cannot get behind is a cage.
- **Bass Bounce is Fire Leap's shape**: cast, hop, and the SLAM fires on landing from a
  countdown on BendingData. It reuses Air jump's "has actually left the ground" guard,
  which is needed for the same reason — the server applies the launch but the CLIENT
  moves the player, so a landing test without it fires on the launch itself.
- **Bass Bounce and Sound Leap both SOLVE for their launch speed** rather than using a
  tuned constant, via `AirJump.speedForHeight` and Sound Leap's own `reachOf`. Drag
  means distance and height are not proportional to launch speed, so a hand-picked
  value would be wrong at every size but one.
- **Sound Leap reuses Air jump's fall protection wholesale** — the same `airJumpTicks`
  countdown, left-ground guard and `LivingFallEvent` cancel. It is a LAUNCH, not a
  teleport like Lightning Jump, so the bender really crosses the ground and a wall in
  the way stops them.

### Figures that were NOT in the design

- **Sound wall's costs** (10 chi/sec, 100 to raise, 1 xp/sec) — the design gives it no
  numbers at all. Flagged INVENTED in the source, like Ice Bomb's.

## Metalbending (the fourth SUB-element)

Same shape as the other three: two paths, not chosen at the start, unlocked with a
scroll gated behind two completed paths of the parent element — EARTH here, as metal
is refined out of earth.

- **Getting it**: a village MASON sells the Metalbending Scroll for 4 iron blocks, at
  trade levels 1 and 3. Reading it lays a 2x2 floor of unbreakable metal under the
  reader that gives the ground back after fifteen seconds, and destroys the scroll.
- Left path: Metal armor, Crush, Metal shield, Extract
- Right path: Tough knuckles, Bullets, Stone walls, Armor pierce

- **`BendingMetalBlock` is a block of our own because it has to be UNBREAKABLE.** Every
  metal ability that places blocks is borrowing them and takes them back, and a bender
  who could mine their own shield would have an infinite iron supply — the same
  argument earthbending makes for never leaving raised ground behind. Negative destroy
  speed and a huge blast resistance is how vanilla makes bedrock unbreakable, and it is
  what this borrows. It wears vanilla's iron block texture, so it needs no art.
- **`MetalWorks` differs from `IceWorks` in one way that matters: it does NOT only fill
  air.** Metal is laid OVER whatever is there, so it remembers the block it replaced and
  puts that exact block back rather than leaving a hole. That makes it
  `EarthWorks.openFor`'s shape rather than `IceWorks`'.
- It refuses to lay over another ability's metal, which is not paranoia: the second
  block's timer would restore the FIRST one's metal as "the original", and the ground
  would turn to iron a cast at a time. Block entities and fluids are refused for the
  usual reasons.
- **A level unloading RESTORES rather than drops**, which matters more here than for ice
  or earth: a floor of unbreakable metal made permanent by leaving the dimension could
  not be removed by anything in the game.

### Metal armor needed TWO effects, and the reason is arithmetic

Vanilla scales an ADD_VALUE attribute modifier by `(amplifier + 1)`, so one
registration can only ever produce a base and its double. Iron's 15 points and
diamond's 20 are not in that relationship and no base gives both — so
`METAL_ARMOR` (15) and `METAL_ARMOR_DIAMOND` (20) are separate registrations, and the
Diamond Plating upgrade swaps which is applied rather than raising an amplifier. The
other is removed first, or buying the upgrade mid-suit would leave a bender wearing
both at 35 points.

### The rest

- **Metal shield is Sound wall's heavier twin**, priced identically at the design's
  request, and the difference is what it is MADE of: sound is particles that shove
  things back, this is REAL blocks that collide the way any wall does — no pushing
  logic at all. Being real blocks is also what gives it the throw.
- It is a TOGGLE and a TWO-PHASE at once, which nothing else in the mod is: the slot
  key raises and lowers it, the left click throws it.
- **Its 2s cooldown therefore has to be stamped from three places**, because the
  dispatcher's cast path stamps neither shape: the throw goes through the two-phase
  release, and pressed-off and out-of-chi both come through `MetalShields.drop`, which
  stamps it exactly as `CompressedPunches.stop` does. Switching it off is still never
  refused — `isActive` is checked above the cooldown gate — so the wait only ever
  applies to putting it back up. It had no cooldown at all, which made dropping and
  re-raising free.
- **The shield is rebuilt from scratch every tick** — plates taken back and laid again
  wherever it now is. Simpler than working out which blocks moved, and it means the
  ground is always given back correctly however fast the crosshair swings.
- **The THROW is drawn with particles, not by flying the real blocks.** Moving a
  five-by-three wall through the world a step at a time would be fifteen block updates
  a tick, and the moment it crossed anything it would either overwrite it or stop dead.
- **Bullets is Air splinters' shape at four times the count**: the charge SUMMONS
  twenty slugs and the armed slot spends them ONE PER LEFT CLICK. That makes it a
  magazine, which is the opposite of Icicles beside it in icebending — one click there
  spends five shards at once.
- **Pressing its key again puts the magazine down.** Twenty shots is a long time to be
  committed to one ability, so it uses the isActive/deactivate hook the toggles use,
  which the dispatcher checks at the very top of `startCharge` — before the cooldown
  gate and before anything is spent, so cancelling is always possible and always free.
  The chi is NOT refunded: it was spent summoning them, and that happened.
- `AbilityHandler.clearArmedTwoPhase` is the inverse of `armTwoPhase`, added for it.
  The two second cooldown is stamped on the way out as well, exactly as it would be on
  the last shot — otherwise cancelling and re-summoning would be a way round the wait
  rather than a way out of the ability.
- **Bullets pierce invulnerability frames, which is not optional at this rate of fire.**
  Vanilla ignores a second hit of equal size within ten ticks, so without it a bender
  clicking quickly would have most of the magazine silently discarded.
- **Armor pierce is deliberately NOT gated on having a target**, which makes it the one
  precision ability in the mod that charges for a miss. Every other one refuses the
  cast when nothing is in view so aiming at the sky is free; an ability whose whole
  identity is being hard to land would have no teeth if the game refunded every failure.
  Its tolerance is 0.4 against the usual 2.0 — "needs perfect precision" has to be true
  of the hitbox, not just the description.
- It DESTROYS the armour rather than dropping it, which is the design's word and the
  right one: dropping would let the victim pick their own set straight back up and hand
  an attacker a free one off anybody they hit.
- **Tough knuckles only applies to an EMPTY hand**, and is raised TO rather than set:
  the passive is about punching, and letting it apply while holding a weapon would make
  it a flat damage buff that happened to be called knuckles. Compressed punches wins
  where both are in play, since that is an ability being actively paid for by the
  second where this is a permanent floor.
- **Extract is a CHANNEL, not a toggle**, and the difference is more than the input:
  the dispatcher already owns every part of running one — draining the chi spread
  evenly across each second, trickling the xp, stopping when the chi runs out, and
  syncing on its own schedule. All of that was being done by hand from the player tick
  while it was a toggle, along with two transient fields on BendingData; converting it
  deleted the lot. Its yield counts off `getChannelTicks()`, so letting go and starting
  again restarts the two seconds rather than resuming a part-paid one.
- **Extract takes nothing out of the world.** The iron is drawn from the ground in the
  fiction but no block is touched — an ability that really consumed ground would be
  either a duplication glitch or a way to delete terrain depending on which half went
  wrong. Mine remains earthbending's ability for actually digging.
- **Crush and Stone walls are both built out of `EarthGrabs`**, which was generalised
  to travel in EITHER direction for them. Earth grab comes HOME (20 -> 5), Stone walls
  goes AWAY (2 -> 20), and Crush points the same wave sideways and launches it twice,
  once from each side of the corridor, so the two converge. Whatever a wave catches is
  now carried in its OWN direction of travel rather than always inward, which is what
  makes all three read as one mechanism used three ways.
- **Neither is made of metal, and that is deliberate**: both use `EarthWorks.materialFor`
  so the walls are whatever the ground actually is. A metalbender conjuring walls of
  iron out of nothing would be a different ability — what these do is take hold of the
  ground, which is why they borrow earthbending's wave whole.
- **Crush pins its victims before the walls close**, and that gap is the only warning
  anyone gets. It starts three blocks out rather than at the bender's feet, so they are
  not standing inside their own crush.
- **Stone walls damages ONCE, up front**, to everything in the corridor the wall is
  about to cross — not per tick as it travels, which would multiply six hearts by
  however long the wall took to get there. The thrown Metal shield does the same.
- **A thrown Metal shield is a real travelling WALL too**, built out of the same
  `EarthGrabs` wave — but launched UNGROUNDED. That is the one thing an earth wave
  cannot do: every other caller wants each column to find its own footing so the wall
  rides the terrain, where a thrown slab should hang on the line it was aimed along and
  go wherever it was pointed, straight up included. `EarthGrabs.launch` now takes that
  as a flag, along with a per-wave WIDTH.
- **Widths differ on purpose.** Earth grab keeps its seven columns because it is a net
  being dragged home and wants to catch everything; Stone walls is five and the shield
  is three, because both are aimed at something and should have to be aimed.
- **Armor pierce is a real PROJECTILE, not a line trace**, and that fixes the two things
  wrong with the first attempt: there was nothing at all to see, and its aim tolerance
  of 0.4 blocks was so tight it essentially never found a target, so the ability
  appeared to do nothing whatsoever. It is still the most demanding shot in the mod —
  a 0.35 hit radius against Air splinters' 1.0 — but it is demanding rather than
  impossible, and a miss now visibly misses.
- It also has a SECOND PHASE now, like Fireball: the charge sharpens the rod, the rod
  hangs on the crosshair drawn as a short segment along the line it will fly, and the
  left click throws it.
- **`BendingProjectiles.Spec` gained `onHitEntity`** for it, alongside the existing
  `onImpact`. Taking somebody's armour needs the VICTIM, which a position-based hook
  cannot give. The rod carries no damage of its own, so the either/or the design asks
  for actually holds: armour is destroyed OR 8 damage is dealt, never both.

### Figures that were NOT in the design

- **Crush's chi, xp, cooldown and damage** — the design gives this ability no numbers at
  all. Flagged INVENTED in the source, like Ice Bomb's and Sound wall's.

## Combustionbending (the fifth SUB-element)

The steepest of the five, and the only one that can kill its own bender by accident.

- **Getting it**: a village ARMORER sells the Combustionbending Scroll for 32
  gunpowder, at trade levels 1 and 3. Reading it needs **ALL FOUR** firebending paths,
  not the two every other scroll asks for — combustion is the end of the fire road
  rather than a branch off it. It destroys the scroll and sets four sticks of primed
  TNT down in a square around the reader.
- **Those four sticks are REAL primed TNT with a full fuse**, and they will wreck the
  ground and anything else standing there — but NOT the reader. A short blast-immunity
  window (`BendingData.blastImmuneTicks`, 140 ticks, comfortably past a TNT fuse) is
  set as the scroll is read and checked first in the damage handler. Blowing up the
  person who just finished all four fire paths is a poor reward; frightening everything
  around them is the point.
- **Combustion Beam is billed by the SECOND** (15 chi/sec) from the player tick, the
  same way Sound wall and Metal shield are, and switches itself off when the chi runs
  out. A toggle that cost a lump sum and then ran forever would have no limit beyond
  the bender remembering to stop.
- **Combustion nuke costs 1000 chi**, which makes it uncastable below level 5 since
  `getMaxChi()` is `500 + level*100` — the same gate Fire Rain, Tsunami and an upgraded
  Lightning Swarm have.
- **The nuke no longer one-shots, and the cap could NOT be done by lowering the power.**
  Vanilla's explosion formula gives roughly `14 * power` damage at the centre, so any
  power a player could live through would be a fraction of one stick of TNT — and the
  demolition is what the ability is for. `Combustion.capped(Runnable)` instead runs the
  whole line of four blasts with a flag up, and the damage handler clamps `IS_EXPLOSION`
  damage to what the victim has left of `CAP_FRACTION` (0.8) of their MAXIMUM health.
  Cumulative across the cast rather than per blast, since anything near the middle of
  the line is caught by two or three; a flag is enough because an explosion deals its
  damage synchronously inside `level.explode`. Applied AFTER Combustion resistance, so
  the passive still helps. A target already hurt still dies — the cap is measured
  against the health they could have had, not what is left.
- **`UpgradeMenuScreen.drawFitted` shrinks a name to fit its box** rather than letting
  it spill over its neighbours. Every slot and list row in the equip and passive tabs is
  70 pixels wide, which is comfortable for "Ignite" and hopeless for "Combustion
  bombardment" — at full size that one is nearly twice the width of its own box and runs
  straight through the two beside it. Scaled through the pose stack, since there is only
  one font; anything still too wide at half size is cut and given an ellipsis, so a row
  is never wider than the thing it labels.
- Left path: Combustion bombardment, Explosive combustion, Combustion Beam,
  Combustion nuke
- Right path: Combustion resistance (the design says "Wip" beneath it)

### The misfire is the element

- **Every combustion ability serves a MINIMUM two second wind-up**
  (`Combustion.MINIMUM_CHARGE_TICKS`), the same shape lightning has.
- **Letting go early is a MISFIRE**, and this is what makes the element its own thing.
  Every other charged ability in the mod treats an abandoned charge as free — chi is
  only checked at the start and taken when the cast lands. Combustion instead drops one
  primed TNT on the bender with a ten tick fuse. Raising a charge is a commitment made
  before the key goes down rather than after.
- Implemented per ability through `onChargeCancel`, which the dispatcher already calls
  on an early release. No dispatcher change was needed.
- **The toggle is exempt from its own misfire, and had to be.** Combustion Beam's second
  press goes through `isActive`/`deactivate`, which is checked at the very top of
  `startCharge` — before the cooldown, before the chi, and crucially before the wind-up.
  A cancel that reached `onChargeCancel` would blow the bender up for switching their
  own ability off.
- Combustion resistance is the other exception and barely one: a passive is never cast,
  so there is no moment of use to put a wind-up in front of.

### The rest

- **`Style.COMBUSTION` is the first projectile style meant to leave a LINE**, not a puff
  at the shot's current position. Its particles are given no velocity at all, so the
  white stripe stays exactly where the charge went — which is the element's signature
  and the reason it could not reuse an existing style.
- **The two projectile abilities carry no damage of their own.** Both pass 0 and do
  their work through the `onImpact` hook, setting off a real explosion where they land:
  an explosion already damages what is near it, and a shot that also hit for a figure of
  its own would be counting the same blow twice.
- **Explosion powers are interpretation, not specification.** One stick of TNT is power
  4.0, and radius goes roughly as the power rather than as its cube — so "4 tnt" is 8.0
  and "7 tnt" is 12.0 rather than 16 and 28. Both are flagged in the source.
- **Combustion Beam leaves from ABOVE the eyes, not from them.** A beam starting at
  exactly the camera's own position is invisible to the bender firing it — every
  particle spawns inside their head and is culled — so from the inside the ability
  looked like it did nothing at all. The whole ORIGIN moves, not just the drawing: the
  line that is drawn and the line that burns have to stay the same line, or the beam
  hits things it visibly missed. It is also the truer picture, since the charge comes
  off the third eye.
- **A misfire says the explosion went off in the bender's HEAD**, not in their hands —
  a combustion charge is gathered behind the third eye and thrown from there, so a
  failed one has nowhere else to go.
- **Combustion Beam eats one block every six ticks**, not a tunnel at once. The design
  says "slowly" and it is the right call: a beam that deleted a corridor instantly would
  be a digging tool, where taking a block at a time makes it something to hold on a
  target while it works. Blocks are DESTROYED rather than dropped, the same call Earth
  dig makes and for the same reason.
- **Combustion nuke's four blasts start a quarter of the way out**, not at the bender's
  feet. At power 12 a blast underneath them is simply suicide, so the loop runs from 1
  rather than 0.
- Its ten second wind-up plays a rising tone once a second. Anything within range of
  four twelve-power explosions deserves to hear it coming.
- **Combustion resistance is applied in the damage handler**, because vanilla has no
  explosion-resistance attribute to modify — blast protection is an enchantment, not a
  number anything can be given. Keyed on the whole `IS_EXPLOSION` tag, so a bender's own
  charges, their misfires, TNT, creepers and beds are all covered at once.
- It is deliberately NOT immunity. A bender who could ignore their own nuke would have
  the wind-up as its only cost, and a misfire that could be shrugged off would stop
  being a reason to finish what you started.

### Figures that were NOT in the design

- **Combustion bombardment's blast size** — the design gives it a count but no power.
- **Everything about Combustion resistance** — the design names it and says "Wip". The
  reading taken is the obvious one, and all of it is flagged INVENTED in the source.

## Bloodbending (the sixth SUB-element)

The only element with a rule about who it may be used ON, and the only one with an
experience track of its own.

- **Getting it**: a village CLERIC sells the Bloodbending Scroll for 5 rabbit feet, at
  trade levels 1 and 3. Reading it needs **ALL FOUR** waterbending paths — like the
  combustion scroll and unlike the other four, because bloodbending is the end of the
  water road rather than a branch off it. It burns itself and rains blood over a 5x5
  patch, which is purely particles: the element is unpleasant enough without the unlock
  being a hazard.
- Left path: Blood freeze, Blood Slow, Blood suck, Blood manipulation
- Right path: Blood strength, Flesh shield (the design says "wip" for a third)

### A SECOND experience track

- **`BendingData.bloodXp` / `bloodLevel` are entirely separate from the ordinary
  level**, and have to be: Blood strength decides who may bend whom by comparing two
  players' blood levels, and a figure that also went up from firebending would make the
  comparison meaningless. 200 blood xp is a blood level, the same rate the main track
  uses.
- **Bloodbending abilities pay into it and NOT the ordinary one.** Every one of them
  returns 0 from `getXpPerSecond` (channels) or `getXpReward` (casts) and calls
  `Blood.grantXp` itself instead — the dispatcher's reward goes to the main level,
  which is the wrong pot. Blood freeze and Flesh shield briefly paid 10 into BOTH, by
  returning a reward AND granting the same figure to blood; each now has its own
  `BLOOD_XP` constant so the two cannot be confused again.
- It needed its own packet (`SyncBloodPacket`), like the passives, upgrades and Avatar
  ones, because `SyncBendingDataPacket` is already at the six fields
  `StreamCodec.composite` takes. Synced on login, respawn and dimension change, and
  every time xp is earned.
- Persisted, and copied by hand in `PlayerEvent.Clone` as well as by `copyOnDeath` —
  that event also fires on a dimension change, where `copyOnDeath` does not.

### The pecking order

- **`Blood.canBend` is the single place the rule lives**, and every bloodbending ability
  that picks a target calls it, so the next one added cannot forget. A lower-level
  bloodbender cannot touch a higher-level one; mobs have no blood level and are always
  fair game.
- **The protection is tied to the TARGET carrying Blood strength**, not merely having
  unlocked it. The level accumulates either way, but a passive that worked from the
  inventory would not be a passive — and it makes the defence a real choice of slot.
  That reading is an INTERPRETATION: the design does not say.
- A refusal is TOLD rather than silent, because "nothing happened" is indistinguishable
  from a broken ability and the reason is something the caster can act on.

### The rest

- **Blood freeze needs a tracker for its BLEEDING, not for its hold.** The hold is a
  Stunned effect and needs no help; two hp a second for FIVE seconds has to land five
  times, once a second, rather than as one blow on the cast. `BloodHolds` is that, and
  nothing more. The hold was raised from the design's 2s to 5s — at two seconds behind
  a one second wind-up the element's opener was barely longer than its own cast — which
  also takes its total bleeding from 4 to 10.
- **Blood Slow re-aims every tick; Blood manipulation does NOT.** That difference is
  deliberate — a sweep across a group wants to follow the crosshair, where a puppet
  dropped by glancing away would be unusable for the thing it exists to do.
- **`BloodPuppets` is deliberately not a ticking tracker.** A puppet lives exactly as
  long as the channel holding it, so the ability drives it from its own `onTick` and
  this only remembers who holds whom. Every other bloodbending manager ticks because it
  outlives its cast.
- **Blood suck heals directly rather than through Regeneration.** Re-applying that
  effect every tick breaks it outright — the trap Water heal documents — and a straight
  trade should land exactly when the damage does rather than on a beat of its own.
- At 100 chi a second it is the most expensive channel in the mod, and it is ALSO
  capped at 3 seconds (`getMaxDurationTicks`). The price alone stopped being a ceiling
  once a bender's pool grew: a straight trade of somebody else's health for your own
  wins any fight it is allowed to run to the end of. Three seconds is six hearts moved
  across, for 300 chi and ten seconds of waiting.
- **Flesh shield costs 100 CHI.** The design doc said xp, which was a typo — it was
  briefly built that way and taken in `execute`, since the dispatcher only knows how to
  spend chi. It now goes through the ordinary path like everything else, and refuses
  for free when there is nobody to take.
- **The wall follows the crosshair**, rebuilt every tick wherever the bender now points,
  which is Metal shield's behaviour done with bodies instead of blocks. It stands 30
  seconds and can be put down at any point in them through `isActive`/`deactivate`.
- **Mobs are moved with `setPos`, players with a rate-limited teleport.** A mob is
  simulated on the server so setPos is free and rigid; a player's position lives on
  their own client and the only way to override it is a teleport packet. Sending twenty
  of those a second is exactly the rubber-banding Fire Rocket's old height cap suffered,
  so a player body is only teleported once it has actually drifted half a block out of
  its slot — which, while Stunned, means when the BENDER turns rather than every tick.
- **Absorbed damage is split evenly across whoever is still alive in the wall**, so a
  bigger shield spreads a blow further — the only reason to gather more than one body.
  Invulnerability frames are cleared before each, or most of a simultaneous hit on the
  whole wall would be discarded.
- A shield of corpses is no shield: with nobody left alive the blow lands on the bender.
- The pecking order applies to being MADE into a shield too — a stronger bloodbender is
  not somebody's cover.

### Figures that were NOT in the design

- **Blood Slow's slowness level** (IV) and **Blood freeze's reach** — the design gives
  neither.
- **Whether Blood strength must be EQUIPPED to protect** — see above.

## Lavabending (the seventh SUB-element)

The second element to come out of earthbending, and the one with the most dangerous
material in the game to hand.

- **Getting it**: a village SHEPHERD sells the Lavabending Scroll for 5 nether bricks,
  at trade levels 1 and 3. Reading it needs **ALL FOUR** earthbending paths — like the
  combustion and blood scrolls and unlike the first four, because lava is the end of the
  earth road rather than a branch off it. It burns itself and sets four blocks of
  temporary lava down around the reader, which cool away after five seconds.
- Those four blocks are OUR lava, not the real thing, so they take nothing with them.
  That is the opposite call to the combustion scroll's four sticks of live TNT, and
  deliberately so: a misfire is combustion's whole character, where lavabending's is
  that the lava is always given back.
- Left path: Lava river, Lava geyser, Lava sinkhole, Lava tsunami
- Right path: Lava wall, Lava resistance, Lava throw, lava rain

### The block is the element

- **`BendingLavaBlock` exists because REAL LAVA FLOWS**, and that single fact decides
  the shape of everything else. Placing a lava source spawns flowing lava into every
  space beside and below it, none of which any tracker knows about — so a "temporary"
  wall of real lava would leave a permanent lava field behind it, burn down whatever was
  nearby, and be impossible to take back. Tsunami's flooding note documents the same trap
  for water, where the timing could just about be worked around at five ticks; lava's
  spread delay is thirty, and nothing in this element is short enough to beat it.
- So it does not flow, does not spread, and schedules itself no tick. Everything else
  about it is deliberately lava: **no collision** so things fall in rather than standing
  on it, light level 15, and it burns and hurts whatever is inside it through
  `entityInside`. It wears vanilla's `lava_still` texture on a plain cube, which ships
  animated, so the block animates for free and needs no art.
- **It is UNBREAKABLE**, for the same reason `BendingMetalBlock` is: every ability that
  places it is borrowing it, and a bender who could mine their own lava would have an
  infinite supply.
- **`noCollission` also settles two questions that would otherwise need code.** An empty
  collision shape makes `isSolid()` false, so `Lava.footing` never treats our own lava as
  ground and a wave cannot climb its own back; and it makes `blocksMotion()` false, so
  nothing suffocates in it — Minecraft only smothers something inside a block that blocks
  motion.
- **`LavaWorks` is lavabending's `IceWorks`** and keeps the same rule, which matters more
  here than anywhere else in the mod: an ability may only ever fill AIR, and whatever it
  fills it takes back. Lava is the one material a bender could use to erase a build
  outright.
- What it does NOT share with IceWorks is the material question, because there isn't one.
  Ice has to be PACKED ice or it melts to water and floods somebody's build; our lava is
  a block of our own that does nothing on its own at all.

### Lava throw is the one exception, and it is the design's word

- **Lava throw places REAL vanilla lava and does not go through `LavaWorks`.** The design
  says "permanent" in so many words, so its four blobs leave lava that flows, spreads,
  and will never be taken away. It is the only ability in the element that changes the
  world, and it is priced for it — four blocks, and five seconds before another four.
- Real lava also means the blobs only ever land in AIR. A blob that overwrote whatever it
  struck would make a five second cooldown into a demolition tool. Landing against
  something solid therefore only burns, which is the honest outcome: the lava hit the
  wall rather than becoming it.

### No element-wide wind-up

Lightning has one and combustion has one because the design gave every ability in those
elements a charge. The lava design gives a charge time to exactly two of its eight — Lava
throw's two seconds and lava rain's five — so the other six go off on the press like
ordinary casts, and there is no `MINIMUM_CHARGE_TICKS` here to be the odd one out.

### Lava resistance is deliberately NOT Fire immunity

- The design's words are "fire resistance to Lava only", and the "only" is the whole
  ability. Fire immunity over in the fire masterclass cancels the entire `IS_FIRE` tag;
  this cancels exactly ONE damage type, so a lavabender wearing it still burns in an
  ordinary fire, still takes a Fire Breath in the face, and still cooks on magma. What
  they can do is walk through their own work — which an element whose every ability drops
  a hazard at the bender's own feet needs. It is Combustion resistance's counterpart, not
  Fire immunity's.
- **One damage type covers everything because `Lava.scorch` uses vanilla's own lava
  source.** Our block hurts through `damageSources().lava()` rather than through a bending
  source, so the passive is one check against `DamageTypes.LAVA` instead of a list of
  ability names — and our lava is indistinguishable from the real thing to everything else
  in the game as a bonus.
- **Two halves, in two places, exactly as Fire immunity needs.** Damage is cancelled in
  the damage handler; the BURNING is cleared in the player tick, because cancelling only
  the damage leaves a bender standing in lava taking nothing while visibly ablaze. The
  fire ticks are cleared only while they are actually IN lava, which is what keeps it
  "lava only" — and they leave with nothing alight because it was cleared on the way, so
  stepping out is clean without the check ever reaching beyond lava.

### The rest

- **Lava river lays its trail and keeps it; Lava tsunami carries its wall along.** That is
  the difference between the two shapes and it is worth knowing which is which. The river
  hands every block straight to `LavaWorks` with its own timer, so it drains from the near
  end first exactly as it was laid; the wave is `Tsunamis`' moving BODY, laid at the front
  and taken up at the back, and keeps its own slices because it takes them up on its own
  schedule as it moves rather than on a timer each was given.
- **The lava wave does NOT have water's flooding invariant.** That whole comment —
  `BODY_DEPTH * ADVANCE_EVERY` must stay under 5 — exists because vanilla water schedules
  itself to spread five ticks after placement and `UPDATE_CLIENTS` does not suppress that.
  Our lava schedules itself nothing, so the body is three slices deep at a step every two
  ticks with no risk at all. That freedom is exactly what the custom block bought.
- **A river stops at a wall rather than skipping it.** If the MIDDLE column can find no
  footing the river ends there; only the two side columns are allowed to fail
  individually. Its ground scan is asymmetric on purpose (1 up, 4 down) — lava pours
  downhill happily and climbs nothing.
- **A geyser has no owner**, the same distinction `AirSpouts` draws between a Tornado and
  a placed spout: it is a hazard put in a place with a clock of its own, so it keeps
  erupting whether or not the bender is still standing there, still in the level, or still
  logged in. And it throws EVERYONE — `null` to `getEntities` is what makes that work,
  since that argument is the entity to SKIP.
- **Lava geyser is Air spout's shape** (arm the slot, one geyser per left click) because
  "create 3" wants three PLACED things. A cast that dropped all three at once would bunch
  them within a couple of blocks and give the bender no say in where any went.
- **Lava throw is CHARGED and TWO-PHASE**, the way Fireball is: two seconds gathers the
  four blobs, the left click throws them. That separation matters more here than for
  most — this is the one ability in the element that changes the world permanently, so
  taking the aim after the wind-up rather than during it means four blocks of lava go
  where the bender chose rather than wherever they happened to be pointing when the
  timer filled.
- **Lava tsunami charges for five seconds.** Thirty blocks of moving lava should be
  something the people near it can see coming. Letting go early just cancels and costs
  nothing — this is not combustion, and lavabending has no misfire.
- **Lava sinkhole does NOT require a target.** The design says "under a player or mob"
  and it started out refusing the cast with nobody in view, which made it unusable on
  an empty field — no way to test it, and no way to lay it as ground work ahead of a
  fight. A body is the PREFERRED aim now, falling back to the ground under the look,
  which is the same call Air spout makes: something already paid for has to happen
  somewhere.
- **Lava sinkhole borrows BOTH halves**: the pit is dug with `EarthWorks.openFor`, which
  is Earth sink's own trick, and the lava is poured in with `LavaWorks`. Both are given
  back.
- **Its two timers are NOT the same length, and that is load-bearing.** EarthWorks only
  closes a hole back up into EMPTY space — somebody may have built in it. So the lava has
  to be gone BEFORE the ground returns, or the ground would find our own lava in the way,
  refuse to close, and leave a permanent pit. Lava 300 ticks, ground 320.
- **lava rain caps each puddle TWICE** — at five seconds, and again at whatever is left of
  the storm. The second cap is what makes the design's "after that it all disappears" true
  exactly: a drop landing in the storm's last second leaves lava for one second. It also
  means the puddles cool continuously instead of every one of them vanishing on the same
  tick, which would be both a visible pop and a spike of block updates.
- Its drops are scattered by AREA, not by radius — the square root, the same trick Ice
  barrage uses, since a ring at r=15 holds five times the ground of one at r=3. Two drops
  a tick rather than enough to fill the circle: seven hundred columns of light-emitting
  block alive at once would be asking the light engine to relight the neighbourhood every
  tick.
- **A rain drop leaves no puddle if the space is taken, and there is no fallback.** Our
  lava does not block a projectile, so a drop landing where an earlier one has already
  puddled falls straight through it and reports the puddle's own position; retrying a
  block higher would stack a second one on top and a storm would build towers.
- **lava rain does not spare its caster's feet.** The drops themselves skip the owner —
  that falls out of how `BendingProjectiles` picks targets — but the lava they leave is
  lava, and a bender standing in the middle of their own storm burns in it. Which is what
  Lava resistance is for, and pairs the two the way Fire Rain pairs with Fire immunity.
- **`Style.LAVA`** was added to `BendingProjectiles` for the throw and the rain. Its
  particles are given no velocity because the LAVA particle already falls on its own, so a
  blob leaves a short trail without a second style having to be invented for one. Both
  users carry their real payload in the impact hook rather than in the style.
- **Lava tsunami's four hp is the BONUS, not the damage.** The lava does its own work
  through the block for as long as anything is inside it; the four is what the wave adds
  on top for being hit by it.

### Figures that were NOT in the design

- **Lava wall's height** (3) — the design gives it none.
- **Lava river's reach** (20 blocks) and **how long its lava lasts** (15s) — the design
  gives neither.
- **Lava geyser's reach** (20 blocks) and **how often it erupts** (every 2s) — the design
  says only "spews out lava".
- **Lava sinkhole's reach** (20 blocks), **width** (5 across) and **depth** (4).
- **Combustion bombardment's blast size** is the nearest precedent for all of these:
  named but unnumbered in the design, so flagged INVENTED in the source.

## The Avatar

Four commands, covered by the permission gate on the `/bend` root:
- `/bend avatar <player>` — names that player the Avatar
- `/bend avatar cycle start` — begins the cycle at earth
- `/bend avatar remove` — takes the title off whoever holds it
- `/bend avatar cycle stop` — ends the cycle and leaves nobody the Avatar

- **The Avatar's state is split in two, and the split is the design.** Per-player
  facts (are you the Avatar, lives left, which elements you had first) live on
  `BendingData`, where every check reaches them cheaply and `copyOnDeath` carries
  them through a death. Facts about the WORLD (is the cycle running, which element
  it has reached, who holds the title) live on `avatar/AvatarState`, a LEVEL
  attachment on the overworld — `ModAttachments.AVATAR_STATE`. Same mechanism as
  `BENDING_DATA` with a level as the holder, so it serialises with the world for
  free: `ServerLevel`'s constructor calls `LevelAttachmentsSavedData.init`, whose
  `isDirty()` always returns true, so mutating the state in place is enough to save
  it. No `SavedData` of our own was needed.
- **There is only ever ONE Avatar.** `grant` revokes the previous holder first, so
  naming a second one is a handover rather than a second title.
- **The title is tracked by UUID as well as by the flag, and the duplication is
  load-bearing.** The flag is what everything reads, but it lives on the player —
  which is unreachable while they are logged out. The UUID on `AvatarState` is what
  lets `/bend avatar remove` and `cycle stop` revoke an OFFLINE Avatar: the command
  clears the UUID, and `Avatar.checkOnLogin` strips the stale flag the moment they
  come back.
- **Becoming the Avatar SNAPSHOTS the elements you already had** (`preAvatarElements`,
  persisted). Losing it restores exactly that set. The obvious alternative — "keep
  only the main element" — would quietly destroy anything the player had earned or
  been granted with `/bend add` beforehand, which is real data loss from what is
  meant to be a reversible flag.
- **The cycle runs earth -> fire -> air -> water and then round again**, picking at
  random among ONLINE players whose `getMainElement()` (their FIRST chosen element)
  matches. The index wraps via `Math.floorMod` rather than being clamped.
- **An element nobody can claim is SKIPPED, not waited on.** Earth with no
  earthbender online moves straight to fire, fire to air, air to water. `findAvatar`
  runs at most ONE full lap, and that bound is what makes it safe: four advances come
  back round to where it started, so a lap that finds nobody at all leaves the cycle
  exactly as it was rather than spinning the index.
- **The just-fallen Avatar is filtered out of the handover — unless they are the only
  candidate.** That only bites when the lap comes all the way back to their own
  element, i.e. nobody online bends anything else. The title should pass ON where it
  can, but putting them back when they are alone is what stops a single-player world
  ending up with a running cycle that can never have an Avatar again.
- **Nobody qualifying AT ALL is a WAIT, not a failure**, and now means exactly that:
  an empty server, or one where nobody has chosen an element. The search retries
  every 5 seconds from the server tick, so the title lands on the first qualifying
  player to log in. `cycle start` says so explicitly rather than reporting success
  and appearing to do nothing. The search costs a player-list walk only while there
  is no Avatar; a cycle with one in place does no work at all.
- **The element is NOT named when the cycle turns on a death.** The search skips
  past empty elements, so where it lands is not necessarily the next one along —
  `grant` announces whoever it settles on, and only a search that finds nobody needs
  a line of its own.
- **`/bend avatar remove` does NOT stop or advance the cycle.** The Avatar was taken
  away, not defeated, so the search resumes on the SAME element. `cycle stop` is the
  command for ending the cycle, and it also sweeps every online player rather than
  only the tracked one, since "remove all avatars" is what it promises.
- **Three lives, spent in `LivingDeathEvent`.** The decrement runs on the DYING
  player's data, which is correct: that event fires before `PlayerEvent.Clone`, so
  it is carried onto the new body along with everything else. The avatar fields are
  copied by hand in `Clone` as well as by `copyOnDeath`, because that event also
  fires on a dimension change where `copyOnDeath` does not — without it, walking
  into the Nether would cost a player the title.
- On the third death the title is stripped and, if the cycle is running, it advances
  to the next element and looks for a new Avatar immediately. A hand-named Avatar who
  runs out simply stops being one.
- **The last stand ("Avatar State"): Resistance II, Regeneration II and Glowing
  below 3 hearts**, taken back off above it. Glowing is what makes an Avatar in
  trouble unmistakable to everyone else, not just to themselves.
- **The "Avatar State Active" line is derived entirely CLIENT-SIDE** from the health
  and the Avatar flag, both of which the client already has. The server applies the
  buffs off those same two facts, so a packet announcing the state would be a second
  source of truth that could only ever disagree with the first. It mirrors the
  server's condition exactly, `isAlive()` included — without that a dead Avatar reads
  as being in the Avatar State behind the death screen. Drawn right under the lives
  hearts, top right.
- Regeneration is only re-applied once the previous instance has
  EXPIRED, with its duration derived from the amplifier (`50 >> amplifier`) — the
  same trap Water heal works around, for the same reason: re-adding replaces the
  instance and resets the counter it heals on, so a per-tick refresh gives a
  permanent icon that never heals. Resistance is not counter-driven and is simply
  topped up, but still not every tick, since each `addEffect` is a packet.
- **The buffs are only removed if we were the ones who applied them** — the
  amplifier is checked as well as the transient `avatarBuffed` flag, so a potion the
  player drank is left alone. Water heal and the last stand cooperate rather than
  fight, since both gate on `getEffect(REGENERATION) == null`.
- `Avatar.tick` runs for every player every tick and the non-Avatar path is two
  boolean reads, because taking the buffs off when the title is lost is as much its
  job as putting them on.
- **The HUD counter uses the VANILLA heart sprites** (`hud/heart/full` and
  `hud/heart/container`), not a text glyph: they are the game's own idiom for lives,
  they follow the player's resource pack, and a spent life leaves its empty container
  behind so the row keeps its width. Drawn top right, and only for the Avatar.
- `SyncAvatarPacket` is the client's only source for this — a packet of its own
  because `SyncBendingDataPacket` is already at the six fields
  `StreamCodec.composite` takes, the same reason passives and upgrades have theirs.
  Sent on login, respawn and dimension change as well as on every change, since the
  client's copy is rebuilt in all three.
- Water Masterclass path COMPLETE (gated behind the other three):
  - Drown (renamed from the design doc's "Water bubble"; CHARGED up to 5s and fires on
    release like Fire blow. Pops every air bubble and then keeps the victim without air
    — 5s of drowning at a 1s charge, 15s at 5s, one heart a second throughout, and it
    ENDS EARLY the moment the bender loses sight of them. 250 chi, 15 xp, 30s cooldown)
  - water breathing (PASSIVE — air is topped up every tick rather than granted as a
    potion effect, so nothing can dispel it and no timer is ever shown. Also makes the
    bender immune to Drown)
  - Tsunami (CHARGED 3s, then a wall of water 9 across and 4 high rolls 20 blocks out,
    hitting everything once for 20.0 and carrying it along — still exactly enough to
    one-shot a zombie (cut from 24.0), and `indirect_magic` bypasses armour so a geared
    one dies the same. Moves
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
  - Water Surf (TOGGLE, and Air scooter's twin — press once to get on, again to get
    off. Carried across the WATERLINE wherever you look, LYING FLAT along it.
    Ends when the water does. 10 chi/sec, 3 xp/sec, no cooldown. "Swift Current"
    (10 levels) doubles the speed)
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
- **Water Surf lies FLAT, and getting a player into a pose takes THREE mechanisms.**
  This is worth knowing before trying it again for anything else. `Player#updatePlayerPose`
  recomputes the pose from scratch every tick on BOTH sides, so `setPose` alone is
  undone immediately — the same trap that killed `Pose.SITTING` for Air scooter.
  - The SERVER is settled with NeoForge's `Player#setForcedPose`, which
    `updatePlayerPose` returns from before looking at anything else.
  - Everyone WATCHING is settled with `setSwimming(true)`. That is a shared flag, so it
    syncs — and `RemotePlayer#aiStep` never calls `updateSwimming`, so unlike a real
    player's it survives on their clients and their own `updatePlayerPose` reaches
    `Pose.SWIMMING` on its own. The server DOES clear it each tick (vanilla holds that a
    passenger is never swimming), so `Rides.advance` re-asserts it every tick.
  - The rider's OWN client is the one copy neither reaches, and `ClientEvents` forces
    the pose there from a POST tick, after the player's own tick has had its say.
  `BendingSeat` carries a synched `LAYING` flag purely so that client half knows to.
  Kept separate from `seated`, because sitting is a question the VEHICLE answers
  (`shouldRiderSit`) where a pose belongs to the player.
- **Water Surf used to lay invisible platform blocks; it does not any more.** That
  approach (a `SurfPlatformBlock` sliver in the air above each water source, so a REAL
  block carried the player and the client walked on it normally) was the right answer
  while the ability was a channel the player walked around in. Once it became a ride,
  the seat entity does the same job better — a passenger is moved BY its vehicle, so
  there is nothing for the server to correct in the first place. The block is gone.
  The underlying lesson stands and is why both rides exist: pinning a player's position
  every tick has the server correcting the client constantly, which is the rubber-band
  that made Fire Rocket's old height cap feel bad.
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
- **Cold water (PASSIVE, Water Offensive)**: ice and snow count as a bending source,
  free like open water, but ONLY the block the bender is standing directly on. Water's
  whole constraint is supply, and this widens it without removing it — a frozen lake or
  a snowfield becomes bendable ground where before it was as dry as a desert. The
  "directly on" rule is what keeps it a passive rather than a free pass: open water
  works from fifteen blocks, this from zero.
- It is a third source inside `WaterSupply.tryConsume`, checked BEFORE the canteen so a
  bender stood on ice does not quietly drain one while surrounded by bendable ground.
  It reads `BlockTags.ICE` and `BlockTags.SNOW` rather than a list of blocks, and checks
  the block at the feet as well as the one below — a snow LAYER is walked on rather than
  stood above, so refusing that would fail on the most obvious case it exists for.
- **Adding it made water's Offensive path four abilities rather than three**, which
  means anyone who had "completed" that path no longer has until they buy it. That
  matters beyond the tree: the Icebending Scroll gates on two completed water paths.
- Water Offensive path COMPLETE:
  - Water ball (hold 2s to gather, then LEFT CLICK to throw — the same
    ChargedAbility + TwoPhaseAbility pairing Fireball uses. 6.0 damage and a shove on
    hit. 50 chi, 5 xp, 2s cooldown from the throw)
  - Water stream (must be LOOKING at water within 20 blocks; tears a stream out and
    holds it for a 3s window, then left click to throw for 8.0 damage. 100 chi, 8 xp,
    10s cooldown from the throw — it had none, but a bender stood beside a pond has
    effectively unlimited chi, so "the 3s window and the 100 chi are the whole limit"
    came out as a stream every three seconds forever. Chi is spent on the DRAW, so
    letting the window lapse costs the cast — and stamps the cooldown too)
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
  - Air cannon (CHARGE 4s, then ONE shot for 3.5 hearts on a left click — halved from
    7, which ended almost anything outright. Wide hit
    radius of 1.2 so a blast that took four seconds to build does not need pinpoint
    aim, and a full block of knockback. 100 chi, 10 xp, 10s cooldown from the shot.
    The opposite trade to Air splinters beside it: six quick cuts, or one heavy blow)
  - wind tunnel (CHANNELED funnel of wind: everything in a 12-block cone in front is
    held at 5s of Slowness I, and shoved back and worn down at 1 heart on a ONE-SECOND
    beat — the shove rides the same beat the damage does rather than running every
    tick, which used to hold everything caught permanently airborne. 10
    chi/sec, 2 xp/sec, no cooldown, no cap. The damage is the least of it — nothing
    caught in it can close the distance while it runs)
- Air Balanced path COMPLETE:
  - Air scooter (TOGGLE — press once to get on, again to get off. Carries the rider
    where they LOOK at sprint speed, exactly ONE block off the ground, gliding down
    over drops and climbing over rises. Stops dead over water. 5 chi/sec, 1 xp/sec,
    no cooldown. One upgrade, "Slipstream" (10 levels), doubles the travel speed)
  - Airpush (Air pull turned around: same 60-degree cone reaching 12 blocks, same 5s
    of Disorientation, but everything caught is thrown AWAY — and unlike the pull,
    this one hurts, for 2 hearts. 100 chi, 10 xp, 2s cooldown)
  - Air spout (CHARGE 3s, then THREE small tornadoes set down one per left click,
    wherever the bender is looking out to 20 blocks. Each stands 10 blocks high for
    60 SECONDS, throwing anything that walks into it up and around. 150 chi, 15 xp,
    15s cooldown from the LAST of the three)
- Air Masterclass path IN PROGRESS (gated behind the other three, which are all done):
  - breathless (CHARGED up to 3s, fires on release like Drown. Pulls the air out of a
    victim's lungs: every tick of charge buys FIVE of suffocation, so 1s held is 5s
    suffered and the 3s ceiling is 15s, at one heart a second throughout — and like
    Drown it ends early if the bender loses sight of them. Also leaves
    them Disoriented for 5s. 150 chi, 15 xp, 30s cooldown)
  - Tornado (TOGGLE — press to raise, press again to put it down, whether or not its
    30 seconds have run. An Air spout grown up: TWICE the height (20 blocks) and twice
    the throwing strength, and it follows the bender's CROSSHAIR instead of standing
    where it was put. 250 chi, 25 xp, 10s cooldown)
  - Flight (PASSIVE — equip it in the Passives tab. Grants vanilla creative flight at
    exactly HALF speed, 0.025 against creative's 0.05. No chi, no XP, like every other
    passive — what it buys is permanent, which is what makes it a masterclass unlock)
- **Flight grants the permission but does NOT force the player airborne**, which is the
  opposite of Fire Rocket. The rocket re-asserts `flying` every tick so only its
  keybind can end it; this is CREATIVE flight, and a creative flier is free to land and
  take off again with a double-tap of space. Forcing the flag would take that away.
- **`BendingData.passiveFlightGranted` exists because the flight flags are PERSISTED**
  (`Abilities#addSaveData`). Something has to remember that WE opened them, or
  unequipping the passive in mid-air would leave permanent creative flight — the same
  trap Fire Rocket has, and it shares the login/respawn safety nets. The flag is
  transient, so a relog clears it and the tick simply re-grants.
- **Flight stands aside while Fire Rocket is channelling** (`FireRocket.KEY`), since
  both write the same flags and set different speeds. Creative and spectator players
  are never touched in either direction.
- **Flight has a ceiling of 120 blocks ABOVE SEA LEVEL**, taken from
  `level.getSeaLevel()` rather than a fixed Y so it means the same thing in a dimension
  that sits at a different height (the Nether's is 32, not 63).
- **The ceiling cancels the CLIMB; it does not pin the player.** Only upward motion is
  taken away, only while actually flying, and horizontal flight at the ceiling is left
  completely alone. Pinning a position server-side is exactly what made Fire Rocket's
  old height cap rubber-band — the client owns the player's movement and simply
  disagrees — where cancelling the climb reads as a ceiling to push against. A bender
  already above the line is not shoved down either; they just cannot go higher.
- **AIR IS COMPLETE — 12 abilities across all 4 paths.** The design doc's fourth
  masterclass entry, "Air beam", was dropped by decision and removed from the tree.
- Earth Defensive path COMPLETE:
  - Earth wall (HELD: one block of height per second, capping itself at 7 and ending
    the channel there. 6 blocks across the bender's facing, 2 blocks in front,
    following Firewall's geometry. Slides up smoothly, stands 30 seconds, slides back
    down. Flat 50 chi and 5 xp however tall it ends up, 1s cooldown from release)
  - Earth pillar (Earth wall narrowed to ONE column, and otherwise identical — same
    7 ceiling, same 30 seconds standing. Rises half again as fast though — 13 ticks a
    block against the wall's 20. 10 chi, 1 xp, 1s cooldown)
  - Earth armor (+10 ARMOR points for 120 seconds, worn as a suit of stone drawn over
    whatever the bender already has on. 150 chi, 15 xp, 150s cooldown)
- Earth Offensive path COMPLETE:
  - Earth spike (TAPPED, not held. A single column driven up in 2 TICKS wherever the
    bender is LOOKING — 3 blocks tall with a stalagmite tip — hurting anything within
    1.8 blocks — the whole ring of neighbouring blocks — for 4.5 hearts as it comes
    up, the CASTER included. Stands 5 seconds, then sinks. 100 chi, 5 xp, 5s cooldown
    — raised from 1s, at which it could be laid almost continuously under a target)
  - Splinters (Air splinters' heavier twin: CHARGE 2s, then SIX shards of stone thrown
    one per left click at 3.5 blocks/tick. 1.5 hearts each, cut from 2.5 — it takes
    all SIX to bring a zombie down now rather than four. Tight 0.5 hit radius — "needs good aim" has to be
    true of the hitbox, not just the description. 100 chi, 10 xp, 10s cooldown from
    the last of the six)
  - Earth block (pulls a real block OUT of the ground onto the crosshair, then throws
    it on a left click for 2.5 hearts. No particles anywhere in it — the block itself
    is what you see, the whole way. 50 chi, 5 xp, 1s cooldown from the throw)
  - Earth trap (ONE slab closes over the feet of EVERYTHING in sight within 20 blocks,
    holding it for 10 seconds. 150 chi, 15 xp, 30s cooldown — the same price, range
    and "what is on screen" test as Wind, which is what an ability that reaches a whole
    screenful costs)
- Earth Balanced path COMPLETE:
  - Mine (CHARGED up to 5s, FIRES ON RELEASE. A tap breaks the one block you are
    looking at; a full charge takes 30, working outward from it. 10 chi and 1 xp for
    the tap, plus 10 chi and 1 xp for every whole second held — so a full dig is 60
    chi and 6 xp. No cooldown. Blocks DROP, because a mining ability that destroyed
    what it broke would be a demolition ability)
    Two upgrades: "Obsidian Breaker" (10 levels) then "Timber" (20, behind it)
- Earth Masterclass path COMPLETE (gated behind the other three):
  - Earthquake (30 SECONDS of Slowness II and Disorientation on everything within 20
    blocks, in every direction. No damage and no displacement — half a minute of both
    effects at once is a fight already decided. The camera SHAKE is only the first 5
    seconds of that. 150 chi, 15 xp, 30s cooldown)
  - Ravine (tears the ground open in front of the bender — 10 blocks out, 5 deep, 5
    across. Permanent: nothing is put back and nothing drops. 200 chi, 20 xp, 50s
    cooldown)
  - Earth sink (Ravine's cleverer sibling: opens a pit 12 long, 6 wide and 7 deep in
    front, hits everything over it for 4 hearts on the way in, and then CLOSES THE
    GROUND BACK over whatever fell in. 250 chi, 25 xp, 100s cooldown)
- **Earth sink BORROWS the world where Ravine keeps it.** Same pit, but every block is
  remembered and put back a few seconds later, so the landscape afterwards is exactly
  as it was — with whatever fell in now inside it. The burial is the real weapon; the
  blow on cast is only an opener, and vanilla suffocation does the killing.
- **`EarthWorks.openFor` is the inverse of `raiseFor`** — take a block now, give it back
  on a timer — and one `restore` flag on the same waiting list serves both directions.
  Raised earth sinks away when its time is up; taken earth comes back.
- **The ground only closes into EMPTY space.** Somebody may have built in the hole while
  it was open, and closing over their work would be exactly the griefing the earth rule
  exists to prevent. An entity standing there is a different matter, and is the whole
  point of the ability.
- **An unloading level settles everything mid-timer**, in both directions, so a pit
  cannot be made permanent by the simple trick of leaving the dimension while it is open.
- **The blow is dealt BEFORE the ground goes**, while victims are still standing where
  the ability was aimed. A moment later they are falling, and a box drawn around the
  surface would start missing them.
- **Ravine is the one earth ability that does NOT put the world back.** Everything else
  in the element borrows — a wall stands and sinks, a spike rises and goes, a grab lays
  its slices and takes them up. A ravine is permanent, and the two and a half minute
  cooldown is what that is really paying for.
- **It drops nothing.** At over a hundred blocks a cast that would be a hundred items to
  wade through, and this is not a mining ability — Mine is the one that gives you the
  blocks. Fluids are left alone too: breaking them only drains whatever is sitting
  nearby, and a ravine that fills on its own when it opens into water is a far more
  interesting outcome than an empty trench.
- **It starts a block OUT, not underfoot**, so a bender does not drop into their own
  ravine the instant they open it.
- **The caster is spared the EFFECTS but not the SHAKE**, which is the whole character
  of the ability. `getEntities(player, box)` already excludes them from the sweep — the
  same argument that was a bug for Earth spike is exactly right here — and the shake
  packet is then sent to them separately.
- **Camera shake has no server-side existence**, so `EarthquakePacket` asks each client
  to do it and `ClientShake` counts it down. Applied through
  `ViewportEvent.ComputeCameraAngles`, which moves the VIEW only — nudging the player
  entity instead would have the server arguing about where they are.
- **The shake is counted down on the client TICK, not in the camera event.** That event
  fires once per FRAME, so counting there would run a 30 second shake down at whatever
  rate the machine happens to render. The partial tick is folded into the offset
  instead, so it stays smooth at any framerate.
- Three different frequencies across yaw, pitch and roll, because a single sine on one
  axis reads as a rocking boat within about a second.
- **Mine is the only ability whose PRICE scales with its charge**, and the dispatcher
  cannot do that: it knows one chi figure per ability. So `getChiCost` is the BASE only
  — gated and taken the usual way — and the per-second extra is taken in `execute`,
  where `getLastChargeTicks` is finally known.
- **A bender who cannot afford the whole charge gets the part they can.** `execute`
  clamps the seconds to what their chi covers rather than refusing: they held the key
  in good faith, and doing nothing at all after five seconds is the worse answer.
  - Earth dig (TOGGLE — the bender becomes a drill and goes underground, steered by
    the MOUSE like the other rides. 5 chi/sec, 1 xp/sec, no cooldown. Has to be started
    looking DOWN at solid ground; ends by itself when it SURFACES. About 7 blocks a
    second — faster than a sprint, because it is earthbending's way of travelling)
  - Earth grab (a wall of ground rises 20 blocks out and rolls back IN to 5, hauling
    every mob and player it washes over back to the bender's feet. Made of whatever the
    far ground is; refuses water. No damage at all — pure displacement. 150 chi, 15 xp,
    20s cooldown)
- **Earth grab is Tsunami inverted, and reuses its shape deliberately.** A moving BODY
  of two slices, laid at the leading edge and taken up at the trailing one, so the wall
  TRAVELS rather than leaving a wall behind it. The one real difference is direction:
  the front counts DOWN, because this wave comes home rather than rolling away.
- **Blocks go in and out with `Block.UPDATE_CLIENTS`, no neighbour updates** — the same
  call Tsunami makes, for a different reason. Water needs it so the wave does not start
  flowing on its own; earth needs it so a wave passing under gravel does not bring a
  hillside down behind it. Only AIR is replaced either way, so nothing is destroyed.
- **The haul runs EVERY tick, not once per step.** Something caught early is carried
  the whole way in; shoving it once would leave it behind as the wave moved on.
- Earth grab does no damage whatsoever. What the bender does with a mob suddenly
  deposited at their feet is the point of the ability.
- **Earth dig is a third `Rides.Kind`**, which is what let it steer like Air Scooter
  and Water Surf for almost nothing. Two hooks were added to `Kind` for it: `velocity`,
  because a drill is the ONE ride where up and down belong to the camera rather than to
  the terrain, and `beforeMove`, so it can take out the blocks it is about to occupy —
  the whole seat's box, not one block, or the corners catch and it grinds.
- **"Am I underground?" is NOT "is there rock at my head".** The obvious test cannot
  work for a drill: it takes those blocks out ITSELF, so a tick later it is always
  standing in the air it just made, and the ride ended the instant it started. What
  matters is whether there is still world OVERHEAD, which `level.canSeeSky` answers —
  a tunnel keeps its ceiling, a surfaced drill does not.
- **`Ride.submerged` is the same guard Air jump's `airJumpLeftGround` is.** A drill
  begins on the surface with open sky above it, so "you have surfaced" cannot be
  allowed to end the ride until it has been underground at least once. A drill that
  never manages to bury itself is dropped after `BURROW_GRACE`.
- **The drill has to beat running or nobody would use it.** It is earthbending's
  travel ability, so it moves at about 7 blocks a second against a sprint's 5.6. It was
  briefly SLOWER than walking, which made it useless for the one thing it exists to do.
- **It does NOT drop what it tunnels through.** At seven blocks a second that would be
  hundreds of item entities a trip — a tunnel full of rubble to wade back through, and
  a mining tool by accident. Earth dig is for travelling; Mine is the ability that
  gives you the blocks. Fluids are skipped entirely, since they do not block the seat
  and breaking them would let a drill quietly empty an ocean on its way past.
- **Every cooldown message shows the seconds left, including instant casts.** The
  charge and channel paths always did; the plain cast path did not, which made a long
  cooldown indistinguishable from a broken one — Earth sink said nothing for two
  minutes and then quietly worked, and "it never comes back" is the only fair reading
  of that from outside. There is no separate cooldown bug: `tickCooldowns` decrements
  every key once per player tick, unconditionally, at the top of `onPlayerTick`.
- **A manager tick that can KILL something must iterate a SNAPSHOT of its list.** This
  crashed the server for real: dying to fall damage during Earth dig fired
  LivingDeathEvent, whose handler calls `Rides.forgetPlayer`, which removed from the
  very list `Rides.tickAll` was walking — `ConcurrentModificationException`, straight
  out of the server tick loop. Fixed in `Rides`, `EarthTraps` and `AirSpouts`, all of
  which move, mount or throw entities and so can reach back into themselves. A plain
  iterator is only safe when nothing downstream can call back in, and in this codebase
  almost nothing qualifies.
- **No ride banks fall damage.** `Rides.advance` zeroes the rider's `fallDistance` every
  tick: they are a passenger being carried by the seat, so anything counted against
  them is the vehicle moving, not a fall they took.
- **Getting off a drill grants Slow Falling for five seconds**, because a ride usually
  ends partway up its own shaft and that is a long drop nobody chose. Vanilla resets
  fall distance every tick the effect is held, so it needs no flag of ours and sees
  itself out.
- **Running out of chi underground is deliberately NOT made safe.** The ride simply
  stops and leaves the bender standing inside rock, and vanilla suffocation does the
  rest. Digging deeper than you can pay to get out of is supposed to be a real risk.
- **Starting requires BOTH a downward aim and solid ground.** Without the aim test a
  drill begun while looking at the horizon bores off sideways through a hillside;
  without the ground test it can be started in mid-air, where it would instantly meet
  the "in open air" condition and switch off again having taken a tick's chi.
- **The upgrade panel drew TWO tooltips at once.** It is painted on top of the ability
  nodes, so the node underneath still counted itself hovered and rendered its own
  tooltip through the panel's. `mouseOverUpgradePanel` now suppresses the node tooltip
  whenever the pointer is inside the panel, and the click handler shares the same
  bounds check rather than keeping its own copy.
- **And it had the ability ART bleeding through it.** The panel pops out BESIDE its own
  node, so it lands on top of whichever nodes sit to that side — and since almost no
  ability has a picture yet, what came through was a row of "?" placeholders sitting
  behind the upgrade text. Two halves to the fix: the background was `0xF0101010`, six
  percent see-through for no reason, and is now opaque; and a node covered by the panel
  draws its box and outline but NOT its icon. The box stays so a node only half covered
  still looks like itself — it is the art that had to go.
- `upgradePanelBounds()` is the single source for the panel's rectangle now, since
  three things need it: the hover test, the click test, and the node decoration keeping
  out from underneath it.
- **Mine cannot take everything.** Netherite blocks are refused at any price — the one
  flat no in the ability — and bedrock and friends are already out on negative hardness.
  Obsidian (with crying obsidian, since gating one and not the other would only look
  like an oversight) waits on the "Obsidian Breaker" upgrade at 10 levels, and every
  kind of log — the `LOGS` tag, so stems, wood and hyphae too — waits on "Timber" at 20,
  which itself waits on Obsidian Breaker.
- **`AbilityUpgrade` now has a `requires` field**, the key of an upgrade that must be
  owned first, which is how Timber sits behind Obsidian Breaker. Enforced in
  `BuyUpgradePacket` and not merely greyed out in the menu — the client is only ever
  asking. The menu shows "Buy <name> first" on a locked row and refuses the click.
- **Blocks are taken nearest-first** from a 7x7x7 box around the aimed block, sorted by
  distance, so a dig always starts where the bender pointed and grows into a rough ball
  rather than taking an arbitrary corner. Anything with a negative destroy speed
  (bedrock, portal frames) is skipped, and so is anything holding a fluid.
- **The trap holds its victim the way the RIDES do: by making them a PASSENGER.** A
  passenger's own movement input is never consulted — the vehicle decides where they
  are — so a trapped player simply cannot walk and a trapped mob cannot either, with no
  effects to fight over and nothing for the server to correct. It reuses `BendingSeat`
  with `seated` false, so they stand rather than sit.
- **That replaced a first attempt built out of Slowness VII and the shields'
  `RootedPacket`**, which needed two different mechanisms for players and mobs and left
  a client-side flag that could stick if a release was ever missed. The seat needs
  neither, and is stronger.
- **Vanilla lets a passenger dismount whenever it likes**, so `EntityMountEvent` is
  cancelled for trap seats — without it the ability would last exactly as long as it
  took to press shift. `EarthTraps` drops the seat from its list BEFORE releasing
  anyone, so a genuine release is never refused by its own guard.
- **The stone matches the ground by NAME**: the slab for `x` is almost always `x_slab`,
  which covers stone, cobblestone, every wood, sandstone and deepslate with no table to
  maintain. Ground with no slab at all — dirt and grass, mainly — falls back to the
  GROUND BLOCK itself rather than a stand-in stone, because looking like what is
  underneath matters more than being half height. Nothing suffocates either way:
  Minecraft only smothers something whose EYES are inside a block.
- **`Aiming.allInSight` is the shared "everything on screen" sweep**, extracted from
  Wind when Earth trap needed the same question answered. Cone plus a line-of-sight
  test, caster always excluded. Wind now calls it rather than keeping its own copy.
- **`EntityType.FALLING_BLOCK` is registered with `updateInterval(20)` — its position
  is broadcast ONCE A SECOND.** This is the single most misleading performance trap hit
  so far: any FallingBlockEntity moved by hand every tick teleports in one-second jumps
  on the client, which reads as severe lag while costing essentially nothing on the
  server. It is a sync RATE, not a load.
- **The fix is `entity.hasImpulse = true` every tick.** `ServerEntity.sendChanges`
  sends the position when the interval elapses OR when that flag is set, and clears it
  after each send — so it has to be set again every tick, not once. Applied in
  `HeldBlocks.follow`, `EarthWorks.park` and the `Style.BLOCK` draw, which fixes the
  carried block, every earth slide (wall, pillar, spike) and the thrown block together.
  Water Manipulation had the same stutter all along and is fixed by the same line.
- Worth remembering before reaching for a custom entity: a moving-block ability that
  looks laggy is far more likely to be this than anything expensive.
- **Earth block is all one REAL block, start to finish.** `HeldBlocks` genuinely
  removes it (so the hole is visible), shows it with a FallingBlockEntity that follows
  the crosshair (so the block is visible in hand), and the throw hands that SAME entity
  to the projectile rather than discarding and respawning one — so what flies is
  visibly the block that was picked up, with no blink.
- **`HeldBlocks.take` ends a carry WITHOUT placing**, handing the block and its live
  display entity to the caller. It is the one method in that class that can lose a
  block: whoever takes it owns putting it back. `BendingProjectiles.landBlock` is that
  other half — a thrown block is set down where it stops, or popped as an item if the
  space is taken, because a throw that deleted its own block would make the ability a
  quiet way to dig holes.
- **Adopting the existing entity also avoids a real hazard.** `FallingBlockEntity.fall`
  CLEARS the block at the position it spawns in, so spawning a fresh one at the throw
  point would delete whatever happened to be there. HeldBlocks only gets away with
  calling it because the block it names has just been removed anyway.
- **`Style.BLOCK` is the one projectile style that is not particles.** Nothing is drawn
  for it; the shot just moves its entity, re-parking it each tick so vanilla's own
  falling-block timer never lands it or drops it as an item.
- Earth block refuses anything with a block entity, anything unbreakable, and anything
  that is not a full cube — a bender should not be able to throw somebody's chest.
- **"Four kills a zombie" only holds because the shot PIERCES INVULNERABILITY FRAMES.**
  Vanilla ignores a second hit of equal size within ten ticks of the first, so six
  shards landing in quick succession would have five of them do nothing at all, and the
  promise would only be true for someone who carefully paused half a second between
  clicks. `Spec.piercesInvulnerability` clears the timer before the hit. It also
  depends on `indirectMagic` bypassing armour, or a zombie's own two points would
  quietly stretch four hits into five.
- **`Style.STONE` draws shots with BLOCK particles**, which carry the real stone
  texture and tumble — a shard of rock rather than a puff with a damage number. Kept to
  a tight cluster with almost no spread so a shot reads as ONE fragment travelling
  rather than a trail of dust.
- **Earth spike goes up at the CROSSHAIR, not at the bender's feet**, unlike the wall
  and the pillar. A spike that could only appear an arm's length away would be a
  defensive ability with a damage number on it. `Aiming.groundUnderLook` does the
  aiming, the same call Air spout and Tornado use.
- **Earth spike hits its OWN caster, and passing `null` to `getEntities` is what makes
  that work.** The first argument to `Level.getEntities` is the entity to SKIP, so the
  obvious `getEntities(player, box)` quietly excludes the bender from their own search
  — which is right for something you throw and wrong for something you put in the
  ground. It also made the ability untestable on yourself: standing on your own spike
  produced nothing but suffocation damage from the block, and no radius change could
  ever have fixed it. Any future ability that places a HAZARD rather than aiming one
  needs the same null.
- **Earth spike is deliberately over-rewarded for landing.** It hits for 4.5 hearts,
  half again what a 3-heart spike would be worth, and catches a radius of 1.8 rather
  than 1.0 — the whole ring of neighbouring blocks, since a block away diagonally is
  1.41 from the centre and a body at the far edge of one is further still. At a radius
  of 1.0 only something standing almost exactly on the spike was caught, which for an
  ability aimed at a patch of floor under a moving target was punishing twice.
- **Its speed is the point**: 2 ticks a block against the wall's 8. A wall easing up
  over most of a second is fine for cover; a spike doing that could be stepped off
  before it arrived. `EarthWorks.riseInto` takes the slide length as an argument for
  exactly this.
- **`EarthWorks.raiseFor` is for earth that is placed and FORGOTTEN** — it rises,
  stands, and sinks itself with nothing having to remember it. Earth wall and pillar
  deliberately do NOT use it: their standing is one part of a longer life that
  EarthWalls has to own anyway. Earth block and Earth trap should.
- **The tip is vanilla pointed dripstone**, UP + TIP, which survives on anything solid
  beneath it — the earth block under it qualifies, so it does not need propping.
- **Earth armor is a registered MobEffect carrying an ATTRIBUTE MODIFIER**, not a
  countdown on BendingData. `MobEffect.addAttributeModifier(Attributes.ARMOR, ...,
  ADD_VALUE)` means vanilla applies and removes the ten points in step with the effect
  itself — the duration, the removal, the cleanup on death and the inventory timer all
  come free, and "adds ten on TOP of existing armor" is simply what ADD_VALUE means.
- **The stone look is a RENDER LAYER, not a change of equipment** (`EarthArmorLayer`,
  hung on both player renderers in `ModEntityRenderers`). That is what lets the ability
  keep its promise about existing armor: the real gear is untouched underneath and
  merely hidden, rather than being swapped out and needing to be given back.
- **Mob effects are NOT synced to onlookers**, and this is the trap the visual had to
  work around: vanilla sends a player's effects only to that player, so a stone suit
  driven off `hasEffect` would be visible to nobody but its wearer. `EarthArmorPacket`
  carries the state to everyone tracking them, broadcast from the player tick ONLY when
  it changes, plus `PlayerEvent.StartTracking` so anyone who walks up to (or logs in
  near) an already-armored bender is told as well. Any future ability whose look has to
  be seen by others needs the same two halves.
- **A respawned player REUSES its entity id**, on both sides (`PlayerList.respawn` calls
  `setId`, and the client's `handleRespawn` copies the old id onto the new LocalPlayer).
  That is what made Earth armor's stone suit survive death: the client's set is keyed on
  entity id and nothing cleared it, while the per-tick broadcast could not notice —
  `earthArmorShown` is transient, so it comes back false and the change detector sees no
  change to report. Told explicitly on death AND on respawn instead. Anything else keyed
  on entity id across a death needs the same treatment.
- `ClientEarthArmor` is also cleared on leaving a world, since ids start again in the
  next one and whoever inherited the number would otherwise turn up wearing stone.
- The armor sheet is vanilla's **cobblestone**, tiled 4x2 across the 64x32 armor layout
  with each tile sampled at its own random offset (wrapping) so the repeat does not line
  up into a visible grid. Generated from the game's own texture, which is worth knowing
  if this mod is ever published — that is Mojang's art sitting in the jar.
- **The rise RATE is per-wall, not a constant.** `EarthWalls.TICKS_PER_LAYER` is only
  the default now: each `Wall` carries its own figure, supplied by
  `RaisedEarth.ticksPerLayer()`. Earth pillar overrides it to 13 against the wall's 20,
  because a wall is cover and taking its time is part of what it is, where a pillar is
  a step and a bender who wants to be four blocks higher wants to be there now.
- **Getting off an Earth dig throws you clear**, along the look. A drill ends by
  breaking out of the ground, and being set down to stand in the hole it just made is a
  flat note to finish on.
- **The UPWARD kick is the figure that matters there, not the distance.** A drill bored
  straight up surfaces at the top of its own shaft, where a small hop simply drops the
  bender back down the hole they came out of — there is nowhere else to land. Running
  vanilla's own step (`v = (v - 0.08) * 0.98`) forward, a lift of 1.0 climbs about six
  blocks and buys roughly two seconds of air, which is the real point: enough time to
  steer sideways onto solid ground. Slow Falling is already on them by then and
  stretches it further. It is a FLOOR rather than a fixed amount, so a drill that
  surfaced sideways gets the same time to pick a landing.
- **`RaisedEarth` is the shared base for both.** A subclass supplies only three things:
  what it is called, what it costs, and WHICH COLUMNS to raise. The chi, the cap, the
  lifecycle and the fact that it outlives its own channel are identical for anything
  that pulls earth up and belong in one place — Earth pillar is a 40-line class because
  of it, and Earth armor should be able to be one too.
- **The HEIGHT CAP is `canContinue`, not a duration.** Returning false once
  `EarthWalls` stops growing ends the channel exactly when the seventh layer lands,
  where a `getMaxDurationTicks` would be a second figure that has to be kept in step
  with the height and would silently drift out of it.
- **Raised earth charges its chi in `onStart`, not per second.** A channel's chi is a
  RATE, and this one is a single price for the whole wall however long the key is
  held. `getMinimumChiToStart` is the gate; `onStart` does the spending.
- **A wall outlives its channel by half a minute**, so `EarthWalls` owns all three of
  its lives — growing, standing, sinking — and the ability class owns almost nothing.
  If the bender logs out or dies mid-raise the wall finishes at whatever height it
  reached rather than growing forever with nobody to let go of the key.
- **Columns with no ground are left OUT of the wall** rather than aborting it, which is
  what makes a wall thrown across a chasm shorter instead of broken.
- **`canStart` was never being called for CHANNELED abilities** until Earth wall needed
  it — `startChannel` checked cooldown, chi and water but skipped the precondition that
  `performCast` has always run. Water Surf and Water Sphere's "you must be in water"
  tests had therefore never fired at all; only Water Heal appeared to work, and that
  was its `canContinue` stopping the channel a tick after it started rather than the
  refusal doing its job. Fixed in `startChannel`, in the same position performCast
  uses.
- **`Ability.isActive` / `deactivate` is the toggle hook**, and it is checked at the
  very top of `performCast`, BEFORE the cooldown gate and before anything is spent.
  That ordering is the whole reason it exists: Tornado runs 30 seconds behind a 10
  second cooldown, so a cancel routed through the ordinary cast path would be refused
  as "on cooldown" for the first third of its life — and would charge another 250 chi
  when it did work. Air scooter was moved onto the same hook, so "toggle" is now a real
  thing in the codebase rather than a coincidence of zero costs.
- **Tornado and Air spout are ONE implementation** (`AirSpouts`), differing only in
  their numbers and in whether they have an owner. A `null` owner means a placed spout
  that stays where it was put; an owner means a Tornado that steers. Everything else —
  catching, throwing, drawing, timing out — is shared.
- **A Tornado is MOVED toward the crosshair at a capped speed, not snapped to it.**
  Flicking the view across the sky should drive the column, not teleport it thirty
  blocks in a tick. The cap is 1.0 blocks/tick — 20 a second, comfortably faster than
  a sprint (raised from 0.45, which played as sluggish).
- **A Tornado dies with its owner's presence** — death, disconnect, dimension change,
  or the owner simply not being in that level any more. Placed spouts are deliberately
  NOT touched by the same cleanup: those are hazards left in a place with their own
  clock, where a Tornado is something actively being held up.
- Air spout's cooldown was cut from 100s to **15s**.
- **breathless REUSES `Drownings`.** Suffocation is the same thing whether the air was
  replaced by water or simply taken away, so there is one implementation of "hold this
  thing's air at nothing and hurt it on vanilla's beat" rather than two. Three
  consequences worth knowing: **the LINE-OF-SIGHT rule is shared** — a drowning carries
  its caster's UUID and ends the moment the bender can no longer see the victim, which
  makes breaking line of sight the counter-play to both and covers a caster who died,
  logged out or left the level in one test; a second cast replaces the first rather than stacking
  (that is Drownings' rule), and the water masterclass passive **water breathing
  answers breathless too** — a bender who cannot run out of air cannot be smothered
  either. That cross-element counter is a happy accident of the reuse, but it is a
  sensible one and was kept deliberately.
- **breathless is the faster, nastier half of the pair.** Drown takes 5s of charge to
  reach 15s of drowning and costs 250 chi; breathless reaches the same ceiling in 3s
  for 150, and adds Disorientation on top. Drown's compensation is that it is a water
  ability and gets its reach for free near open water.
- **`abilities/Aiming.java` is the shared "what am I pointing at" helper**, extracted
  when breathless needed the same target-picking Drown had. Nearest LIVING thing to the
  aim LINE rather than a raycast, so a cast does not have to be pixel-perfect on
  something moving, and nearest-along-the-line wins so a bender hits what is in front
  rather than something further off that is better aligned. Anything else wanting a
  single target (Earth grab, for one) should use it rather than growing a third copy.
- **A spout is neither an entity nor a block, so it is tracked** (`AirSpouts`, the
  Drownings/Tsunamis pattern). It is a column of moving air: an entity would need an
  EntityType and a client renderer for something that is only ever particles, and a
  block would need a blockstate for something that does not occupy the world so much as
  churn through it.
- **Lift is applied EVERY tick something is inside**, not once on entry, which is what
  carries a victim up the whole column and flings them out of the top instead of giving
  them a single hop. `fallDistance` is cleared while they are in it, so the fall that
  counts is the one from where they leave.
- **A spout throws EVERYONE, its owner included.** It is a hazard put down in a place,
  not a spell aimed at somebody, and one that politely stepped around the bender who
  placed it would be a strange thing to walk into.
- **Air spout's aim falls back to the ground rather than refusing.** The dispatcher
  counts the CLICK, not the outcome, so the shot is already spent by the time
  `onRelease` runs — aiming at open sky has to put a spout somewhere (the ground under
  the end of the look) or it would silently cost the bender a third of the ability.
- **`abilities/Rides.java` carries both Air scooter and Water Surf**, generalised out
  of the old AirScooters when Water Surf was rebuilt in the scooter's image. The two
  differ in only three things, all held on the `Kind` enum: where the surface IS (a
  block above solid ground, or the waterline), what makes the ride STOP (crossing
  water, or running out of it), and what it costs. Seating the rider, steering by the
  crosshair, billing and the half dozen ways a ride can end are written once.
- **The seat is `BendingSeat`** (was AirScooterSeat), and it carries a SYNCHED
  `seated` flag because `LivingEntityRenderer` asks the VEHICLE, not the passenger,
  which pose to draw — so the answer has to exist client-side. Air scooter rides
  seated; Water Surf rides standing, because nobody surfs sitting down.
- **Water Surf is a TOGGLE now, not a channel**, and is carried by a real entity like
  the scooter. The old version laid invisible platform blocks under the bender every
  tick; the ride does the same job better, since a passenger is moved BY its vehicle
  and the server never has to correct the client at all. `SurfPlatformBlock` was its
  only user and has been deleted along with its blockstate and model.
- **Water Surf's "Swift Current" upgrade now doubles the ride's SPEED** rather than
  granting Speed II, since the ride's pace is no longer the player's walking speed.
  Same key, same cost, so anyone who bought it keeps it.
- **Water Sphere no longer requires being in water.** It simply finds nothing to hold
  back on dry land and waits until there is some — which lets a bender raise the sphere
  on the shore and walk in, rather than having to dive first and open it while already
  drowning.
- **Air scooter is a TOGGLE, so it is a plain `Ability`, not a `ChanneledAbility`.**
  The click dispatch (`UseAbilityPacket` / `consumeClick`) fires once per press, which
  is what a toggle wants; the hold dispatch built for Fire Breath reports key STATE and
  is the wrong shape entirely. `execute()` just flips: riding -> stop, otherwise start.
- **The rider genuinely rides an entity, because nothing else gives a seated player.**
  `Pose.SITTING` is useless here on two counts: `Player#updatePlayerPose` recomputes the
  pose from scratch every tick on both sides, and the player model's seated pose is
  driven by `LivingEntityRenderer` reading `isPassenger()` — not by the pose at all.
- **Riding also solves the movement problem for free.** A passenger is carried by its
  vehicle, so the server steers the seat without ever contradicting the client about
  where the player is — no rubber-banding — and the player's own WASD is simply not
  consulted, which is what "moves where you look, not where you press" requires.
  `AirScooterSeat` deliberately does NOT override `getControllingPassenger`.
- **The seat is never saved** (`shouldBeSaved()` returns false). That is the whole
  answer to orphaned entities: a crash mid-ride leaves nothing on disk to come back as
  an invisible passenger-less thing no code remembers owning.
- **The seat's box is exactly a player's 0.6 x 1.8, and that is load-bearing.**
  Passengers do not collide themselves, so the seat is what collides with the world on
  the rider's behalf — without a real box a scooter rides through walls, and with a
  SHORTER box it happily carries the rider into a one-block gap and suffocates them
  against the ceiling.
- **A surface is "solid with space above it".** That definition is what stops the
  scooter walking up sheer walls: every block of a five-block wall has another block on
  top of it, so none qualifies, the search falls through to the ground the wall stands
  on, and the seat bumps into the wall the way it should. Ground is read both underfoot
  and `LOOK_AHEAD_TICKS` ahead, which is what turns "stops dead at a step" into "rides
  up over it".
- **Every route out of a ride goes through `AirScooters.stop`/`forgetPlayer`** — key
  press, chi running out, shifting off the seat (vanilla lets a passenger dismount and
  that is treated as a normal way to end, not an error), death, disconnect, dimension
  change, level unload. There is no other way to end one, so none of them can leave a
  player stuck seated or a seat orphaned.
- **Casting any other ability dismounts the scooter first** (`AbilityHandler`
  `dismountScooter`, called from both entry points). Chosen over refusing the cast:
  bending while seated on a server-steered entity is a lot of surface for odd
  interactions (rooting channels that cannot root a passenger, flight fighting the
  seat), and blocking abilities outright risks a player who feels stuck and cannot work
  out why nothing fires. On the HOLD entry point it only fires when a held ability is
  STARTING — a key RELEASE must never dismount, or the scooter's own key would turn the
  toggle off the instant it was pressed.
- Air scooter refuses to start if the player is already a passenger, rather than
  stealing them out of a boat or off a horse.
- **Airpush and Air pull share their geometry on purpose**, so knowing one teaches the
  other. The push scaling is the REVERSE of the pull's, though: a pull has to reach
  further to bring a distant target all the way in (speed rises with distance), where a
  gust is strongest where it leaves the hands and is spread thin by the far end (speed
  falls with distance, 1.05 down to 0.45).
- **Airpush shoves AFTER it damages.** `hurt()` applies its own knockback, so setting
  the motion first would have the ability's throw quietly overwritten by a much smaller
  vanilla one. Any future ability that both damages and moves things needs the same
  ordering.
- **Hovering a full block has a headroom cost worth knowing.** HOVER is the RIDER'S
  FEET, and the seat is a player's 1.8 tall on top of that, so a scooter needs about
  2.8 blocks of clearance to pass — an ordinary two-high doorway is too low to ride
  through. That follows from the height, not from a bug, and the fix if it ever grates
  is to hover lower, NOT to shrink the seat (which is what stops the rider being
  carried into a gap and suffocated).
- **Water ends the ride**, checked both when starting and every tick. "Over water"
  means looking straight down from the rider's feet and meeting water before anything
  solid — so a pond with a stone bed counts, since the water is what would be crossed.
  Refused at the start too, or toggling on at the water's edge would cut out a tick
  later and read as the key not working.
- **`getXpPerSecond()` is a `double`, and XP is spread per tick like chi.** Nothing
  needs the fraction any more (Air scooter was rebuilt as a toggle at a whole 1/sec and
  bills itself), but it is kept: a sub-1 rate is
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
- **`Style.AIR` shots are exempt from gravity, and nothing else is.** Every other shot
  in the mod is a mass of something being thrown and should arc, but a blade of
  compressed air visibly drooping over its flight reads as the shot dying rather than
  travelling — and Air splinters cross 45 blocks, far enough for the sag to be the
  first thing anyone notices. Done in `advance` off the style rather than as a field on
  `Spec`, since "air does not fall" is a fact about the element and not about one
  ability. Air splinters and Air cannon are the only two users.
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
- **WATER IS COMPLETE — all 12 abilities across all 4 paths.**
- **EARTH IS COMPLETE — all 13 abilities across all 4 paths.**
- **ALL FOUR ELEMENTS ARE BUILT**, and seven sub-elements on top of them — 106
  registrations in `AbilityRegistry.bootstrap()`.
  Everything from here is tuning, upgrades and polish rather than new paths.
- Previously built with Gemini; switched to Claude as primary coding partner because
  Gemini was getting inconsistent on a project this size

## Working Style

- Prefers step-by-step guidance and practical, hands-on troubleshooting over abstract explanation
- Comfortable with a full class rewrite if it's a clear improvement over patching —
  don't hesitate to propose one, changes are easy to revert with Ctrl+Z / git
- Building solo (well — solo + AI), so keep explanations of *why* a change was made,
  not just the diff
