#!/usr/bin/env bash
# Renames the mod "Atla mod" -> "Elemental Paths" in the repository it is run from.
# The same script is committed to every build's branch so they all get the identical
# mapping; the mapping itself lives in rename-to-elemental-paths.pl.
#
#   bash tools/rename-to-elemental-paths.sh     (from the repository root, clean tree)
#
# Steps: rewrite every tracked text file, move every file whose path changes (packages,
# assets/<modid>, data/<modid>, renamed classes and textures), update the namespace inside
# structure .nbt files (parsed, never edited as bytes), and add the README disclaimer.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
TOOLS=tools
SELF="$TOOLS/rename-to-elemental-paths.sh|$TOOLS/rename-to-elemental-paths.pl|$TOOLS/NbtNamespace.java"

# 1. Text content. Binary files (textures, structures, the wrapper jar) are never opened here.
git ls-files -z \
  | tr '\0' '\n' \
  | grep -Ev "^($SELF)$" \
  | grep -Ei '\.(java|json|json5|md|toml|gradle|properties|cfg|accesswidener|classtweaker|mcmeta|txt|yml|yaml|mcfunction|lang)$|(^|/)(gradle\.properties|settings\.gradle)$' \
  | tr '\n' '\0' \
  | xargs -0 -r perl "$TOOLS/rename-to-elemental-paths.pl" content

# 2. Paths. Every tracked file whose path the mapping changes is moved with git mv.
git ls-files | grep -Ev "^($SELF)$" > .rename-old.txt
perl "$TOOLS/rename-to-elemental-paths.pl" path < .rename-old.txt > .rename-new.txt
paste -d '\t' .rename-old.txt .rename-new.txt | while IFS=$'\t' read -r old new; do
  [ "$old" = "$new" ] && continue
  mkdir -p "$(dirname "$new")"
  git mv "$old" "$new"
done
rm -f .rename-old.txt .rename-new.txt
# Directories left empty by the moves.
find src -type d -empty -delete 2>/dev/null || true

# 3. Structure templates: atlamod:* block or entity ids inside the NBT.
mapfile -t NBT < <(git ls-files '*.nbt')
if [ "${#NBT[@]}" -gt 0 ]; then
  java "$TOOLS/NbtNamespace.java" atlamod elemental_paths "${NBT[@]}"
fi

# 4. The README disclaimer, once.
DISCLAIMER="Elemental Paths is an unofficial, free fan project and is not affiliated with Mojang, Microsoft, or any other company."
if [ -f README.md ] && ! grep -qF "$DISCLAIMER" README.md; then
  printf '\n%s\n' "$DISCLAIMER" >> README.md
fi

git add -A
echo "Rename applied. Review with: git diff --cached --stat"
