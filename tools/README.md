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
