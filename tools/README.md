# tools

Throwaway generators. Not part of the mod, not on the build path — run by hand with
`java <file>.java` (JDK 21+ single-file source mode) when their output needs remaking.

## GenArmor.java

Generates the four armor-layer sheets under
`src/main/resources/assets/atlamod/textures/models/armor/`:

- `stone_layer_1.png`, `stone_layer_2.png` — Earth armor
- `metal_layer_1.png`, `metal_layer_2.png` — Metal armor

```
java tools/GenArmor.java src/main/resources/assets/atlamod/textures/models/armor
```

Everything is procedural and **seeded**, so the output is byte-for-byte reproducible —
and the sheets are this project's own art rather than anyone else's. That is the point
of it: the stone sheets it replaced were tiled out of vanilla's cobblestone, which put
Mojang's pixels in the jar.

Both patterns wrap, so they tile with no seam at the edges of the sheet, and both are
deliberately isotropic — no full-width lines. Each region of a 64x32 armor sheet maps to
a different body part, so anything drawn across the whole sheet slashes over the helmet
and down an arm in unrelated places.

## SpiritTextures.java

Recolours vanilla textures into the spirit ore, shard and armor textures under
`src/main/resources/assets/atlamod/textures/`:

- `block/spirit_ore.png` — from emerald ore
- `item/spirit_shard.png` — from the amethyst shard
- `item/spirit_{helmet,chestplate,leggings,boots}.png` — from chainmail's item icons
- `models/armor/spirit_armor_layer_{1,2}.png` — from chainmail's armor sheets

```
java tools/SpiritTextures.java build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar src/main/resources/assets/atlamod/textures
```

Reads straight out of the vanilla jar, so there is no half-extracted copy of Mojang's
assets to commit by accident, and nothing vanilla is ever written back out.

**Two operations, chosen per texture by measuring its hue histogram rather than by eye.**
`shift` is a SELECTIVE HUE REPLACEMENT: only pixels whose hue already falls in a measured
band move to teal, keeping their saturation and brightness exactly, and every other pixel
is copied byte for byte. That is what emerald ore (71% grey stone, colour only at 116-148
degrees) and the amethyst shard (260-279 with a pink tail at 320-329) get.

`tint` COLOURISES A GREYSCALE, because chainmail's armor has no hue at all — 100% of its
opaque pixels are within 0.12 saturation of grey, so a hue replacement has nothing to find.
Brightness is preserved, so the mail pattern and the outlines survive untouched. Its
saturation is set higher (0.55) than a bright source would need, because chainmail is dark:
mean brightness 0.65, never above 0.80.

**Chainmail rather than diamond for the armor**, and that is the one real judgement call
here: diamond's armor is ALREADY teal, measured at 167-177 degrees, so recolouring it to
180 would have produced a suit indistinguishable from a diamond one.

Deterministic — re-running it over the same jar reproduces the shipped files byte for byte.
