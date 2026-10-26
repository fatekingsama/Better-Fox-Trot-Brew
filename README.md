# Better Fox Trot Brew

**English** | [简体中文](README_zh-CN.md)

Target: Minecraft 1.21.1 / NeoForge. Based on [Fox Trot Brew](https://github.com/WitherRedstone/Fox-Trot-Brew), branch `1.21.1-neoforge`, with the 0.1.2 improvements ported from our `1.20.1-forge` branch.

## Version support

This branch ports automated feeding, pressing, bucket collection, hand inventory support and tub rotation to Minecraft 1.21.1 / NeoForge 21.1.224 / Java 21. A player gameplay smoke test passed with NeoForge 21.1.224, Touhou Little Maid 1.5.2 and Kaleidoscope Tavern 1.0.1; comprehensive edge-case coverage is not claimed. Use the Forge branch for Minecraft 1.20.1.

## 0.1.2 behavior

- Select the **Better Fruit Tub Brewing / 果盆** maid task.
- Supply fruit and empty buckets in the maid backpack or either hand, leaving space for juice buckets.
- Full tubs are collected before other available work, including full tubs with no remaining fruit.
- Collection and feeding can approach adjacent walkable positions; pressing also checks the position above the tub.
- With no available work, the maid retains the task and uses the default idle movement behavior.
- Empty ingredient slots are refilled from the backpack or hands, respecting recipe/fluid compatibility and slot capacity.
- A basin containing only residual juice is not stomped without usable ingredients.
- One visit lasts at most 12 seconds; 4 seconds without ingredient/fluid progress ends a visit.
- Completed visits wait 2 seconds before reselection; stalled/unreachable tubs wait 5 seconds.
- Travel times out after 10 seconds. Search respects the maid restriction and does not load missing chunks.
- Ingredients and bucket results are inserted into the backpack first, then available hand slots. Unexpected insertion failure drops the remainder beside the maid instead of deleting it.

This version does not harvest vines or transfer supplies to/from external chests. Multiple maids have no explicit basin reservation; interaction is serialized by the game server.

## Installation and existing saves

Use `better_fox_trot_brew` as the mod ID. This is an independent fork; it does not replace the original task ID. Do not load this fork alongside the original Fox Trot Brew: they still share Java class names. Back up and remove the original jar, then reselect the improved task for affected maids. Back up the world before gameplay testing.

No automatic installation into the current game is performed by the build.

## Build and checks

Use Java 21 toolchains and the included Gradle 9.2.1 wrapper. Run `gradlew build`. With dependencies cached, use `gradlew --offline build` and your configured Gradle user home. `check` includes `policyRegression`, ten standalone decision/timeout checks without an additional test framework.

This port passed an offline build, ten standalone regression checks and a player gameplay smoke test. The standalone checks do not cover pathfinding/jumping, inventory transfers, fluid refresh or multiple maids. Run the following acceptance cases on 1.21.1.

### Gameplay acceptance cases

1. Two basins: fewer than eight grapes in the first, usable grapes in the second. The maid must leave the exhausted first basin.
2. Full juice basin with zero fruit: one empty bucket produces exactly one juice bucket and removes exactly 1000 mB.
3. Repeat with a single empty bucket in an otherwise full backpack; its slot can hold the result.
4. No bucket, or a stack of buckets with no result space: no juice/bucket loss; another workable basin is selected.
5. Empty basin and backpack grapes: grapes transfer without duplication and are pressed normally.
6. Residual juice plus incompatible fruit: no mixing or endless jumps.
7. Blocked basin: timeout and selection of another basin.
8. Several usable basins: bounded visits; no basin monopolizes the maid indefinitely.
9. Put empty buckets in the main hand, then the offhand: both should supply collection, with results stored in the backpack first.

## Attribution

Original authors: ChinaEX123 / Wither_Redstone and Fvue233. Original MIT copyright and license are retained in `LICENSE` and included in the built jar. Existing Java package names are retained to preserve provenance. Fork behavior changes are maintained as Better Fox Trot Brew.
