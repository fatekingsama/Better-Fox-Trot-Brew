# Better Fox Trot Brew

Minecraft 1.20.1 / Forge. Based on [Fox Trot Brew](https://github.com/WitherRedstone/Fox-Trot-Brew), branch `1.20.1-forge`.

## 0.1.0 behavior

- Select the **Better Fruit Tub Brewing / 果盆酿造（改进版）** maid task.
- Supply fruit and empty buckets in the maid backpack, leaving space for juice buckets.
- Full tubs are collected before other available work, including full tubs with no remaining fruit.
- Empty ingredient slots are refilled from the backpack, respecting recipe/fluid compatibility and slot capacity.
- A basin containing only residual juice is not stomped without usable ingredients.
- One visit lasts at most 12 seconds; 4 seconds without ingredient/fluid progress ends a visit.
- Completed visits wait 2 seconds before reselection; stalled/unreachable tubs wait 5 seconds.
- Travel times out after 10 seconds. Search respects the maid restriction and does not load missing chunks.
- Ingredients and bucket results are returned to the backpack. Unexpected insertion failure drops the remainder beside the maid instead of deleting it.

This version does not harvest vines or transfer supplies to/from external chests. Multiple maids have no explicit basin reservation; interaction is serialized by the game server.

## Installation and existing saves

Use `better_fox_trot_brew` as the mod ID. This is an independent fork; it does not replace the original task ID. If both mods are installed, choose the improved task explicitly. If removing the original mod, reselect the improved task for affected maids. Back up the world before gameplay testing.

No automatic installation into the current game is performed by the build.

## Build and checks

Use Java 17 toolchains and the included Gradle 8.8 wrapper. Run `gradlew build`. With dependencies cached, use `gradlew --offline build` and your configured Gradle user home. `check` includes `policyRegression`, ten standalone decision/timeout checks without an additional test framework.

Gameplay validation is still required: pathfinding/jumping, backpack bucket transfers, visible fluid refresh and multiple maids are not covered by the standalone policy checks.

### Gameplay acceptance cases

1. Two basins: fewer than eight grapes in the first, usable grapes in the second. The maid must leave the exhausted first basin.
2. Full juice basin with zero fruit: one empty bucket produces exactly one juice bucket and removes exactly 1000 mB.
3. Repeat with a single empty bucket in an otherwise full backpack; its slot can hold the result.
4. No bucket, or a stack of buckets with no result space: no juice/bucket loss; another workable basin is selected.
5. Empty basin and backpack grapes: grapes transfer without duplication and are pressed normally.
6. Residual juice plus incompatible fruit: no mixing or endless jumps.
7. Blocked basin: timeout and selection of another basin.
8. Several usable basins: bounded visits; no basin monopolizes the maid indefinitely.

## Attribution

Original authors: ChinaEX123 / Wither_Redstone and Fvue233. Original MIT copyright and license are retained in `LICENSE` and included in the built jar. Existing Java package names are retained to preserve provenance. Fork behavior changes are maintained as Better Fox Trot Brew.
