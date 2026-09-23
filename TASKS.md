# Task Queue

The loop takes the first unchecked `- [ ]` and does only that.
`- [x]` done · `BLOCKED: <reason>` tried and could not.

One line per task, small enough to finish in one pass:
`- [ ] <file>:<line> — <what is wrong> — <what to change it to>`

Refill with 04_HUNTING.md when the Open list empties. Never treat empty as done.

Completed tasks live in `.claude/archive/TASKS_ARCHIVE.md` (moved there 2026-09-10 so this
file stays readable). `asked.py` searches that archive too, so a subject answered there still
counts as answered — do not re-queue it.

## Open

- [x] **Player report 1: Freshness timer frozen on world-generated blocks** — FIXED pass 221 (commit 12dcbb2): BlockSpoilageNetworking routes untracked blocks through getSpoilageState which registers them lazily and ages from chunk birth time. HUD countdown now advances. Verified runClient — mod 1.0.46 registered, 0 InjectionError lines.
- [x] **Player report 2: Right-click inserts rotten item into crafting slot** — FIXED pass 223 (commit 7f96ae1): ScreenHandlerMixin uses FoodSpoilageUtil.worstSliceContainsRotten(stack, n) helper. Right-click (QUICK_CRAFT) uses N=1; every other click type uses whole stack. 8 new tests. Runtime verified runClient — mod 1.0.47 registered, 0 InjectionError lines.
- [x] **Player report 3: Placed cake loses spoilage** — FIXED pass 224 (commit 9975181): GourdBlockMixin places rotten cake (sets state ROTTEN with -1 expiration), CakeEatMixin reads tracked state on eat and applies stale/rotten effects. CandleCakeEatMixin covers candle cake path. IsEatenInPlaceTest pins CakeBlock detection. Suite 70/0/0.

- [x] L13: **shulker box contents age** — FIXED pass 1184 (ContainerAgingSweepMixin, commit 309ac1b): live readback rotten_count:1. — RCON-driven: place a shulker box, insert an apple with fresh_expirations:[100L] via data modify, wait 15s, read back. Expected: rotten_count:1 (ages). If unchanged: the box is a freezer — same gap shape as the hopper (ShulkerBoxBlockEntity.tick is animation-only, ShulkerBoxBlockEntity.java:62-64). Control: same apple in a chest beside it.
- [x] L13: **decorated pot contents age** — FIXED pass 1184 (ContainerAgingSweepMixin covers ContainerSingleItem too): live readback rotten_count:1. — RCON-driven: place a decorated pot, insert an apple with fresh_expirations:[100L] via data modify (pot holds one item), wait 15s, read back. Expected: rotten_count:1. If unchanged: gap — DecoratedPotBlock has no server ticker at all. Control: same apple in a chest.
- [x] L13: **ender chest contents age** — FIXED pass 1185 (PlayerEnderChestMixin, commit 260de7a): live fresh->stale transition observed in the ender chest. — RCON-driven: put an apple with fresh_expirations:[100L] into a player's ender chest via data modify on the player's EnderItems, wait 15s, read back. Expected: rotten_count:1. If unchanged: gap — Player.tick calls inventory.tick() on the 36 main slots only (Player.java:448, Inventory.java:242-249); PlayerEnderChestContainer is never ticked. Control: same apple in the player's main inventory.
- [x] L13: **dispenser-stored food ages** — FIXED pass 1184 (same sweep): live readback rotten_count:1. — RCON-driven: place a dispenser, insert an apple with fresh_expirations:[100L], wait 15s, read back. Expected: rotten_count:1. If unchanged: gap — DispenserBlock has no server ticker (grep found none); the dispenseFrom mixins only reconcile counts on eject. Control: same apple in a chest.
- [x] L13: **barrel-stored food ages** — FIXED pass 1184 (same sweep): live readback rotten_count:1. — RCON-driven: place a barrel, insert an apple with fresh_expirations:[100L], wait 15s, read back. Expected: rotten_count:1. If unchanged: gap — BarrelBlockEntity has no serverTick (grep found none) and no mixin covers it. Control: same apple in a chest.

## Open

- [x] `PlatformSilentFallback`: FIXED (pass 1174): all 8 bare catches in SpoilageEnhancedPlatform now log — supplier throws, non-CNFE reflection failures, and the final relative-path fallback each emit a line. Suite 684/0/0; fresh launch (79 mods, Done 23.233s) confirms happy path unchanged and config written to the right directory (mtime this run).
- [x] `RecipeScannerSilentSkip`: FIXED (pass 1175): both counters (thrown at line 217, unreadable at line 96) now increment on every pass, not just pass 1; summary lines say 'across N passes'. Proven live: general.log shows '1080 unreadable results across 5 passes' (old code would have shown only pass-1's 216).
- [x] `DynamicFoodBlockCacheSilentPoison`: FIXED (pass 1177): probeThrew guard skips the NO_FOOD_DROP write; Ripeness record gained a 'probed' component and ripenessOf returns unprobed answers uncached (deriveRipeness fell through to a WRONG age rule after a failed flag probe and cached it). Pinned by UnprobedAnswerNotCachedTest (3 tests); suite 687/0/0; live drive: berry bush + pumpkin broke with correct ROTTEN/STALE stamps, no probe exceptions.
- [x] `AutoFoodDetectorUnboundComponents`: REFUTED (pass 1178): detectAllFoodItems (the only affected method) has zero main-code callers; the real scan runs after components bind (first server tick post RecipeManager.apply); isSpoilable's unboundOut guard already prevents caching provisional answers; builtInRegistryHolder() is a field read that cannot NPE. Live: farmersdelight CONSUMABLE-only drinks registered via c:drinks tag.
- [x] BlockSpoilageDataCorruptLoad: REFUTED (pass 1179): a wrong-type entry loads as FRESH/-1 which ages to STALE/ROTTEN on first ask (BlockSpoilageData.java:608) — conservative, never a resurrection. Out-of-range ordinal fixed by pass 133; per-entry catch isolates siblings (pinned by BlockSpoilageDataSaveLoadTest).

<!-- Seeded 2026-09-10 for lens L14 (foreign content). Every subject was checked with
     `python "E:/_claude_ops/asked.py" <words>` and came back NOT ASKED. Deploy the pack with
     `modpack.ps1 -Deploy` before the first one and `-Remove` when you leave the lens. -->

- [x] L14: **detection sweep — better_mcdonalds_mod (33 items).** The remaining third of the
  original detection sweep: food that gets NO timer, and non-food that wrongly gets one.
  Probe via item entity spawn + component readback with armor stand chunk keeper.
  The 33-item namespace is the smallest unprobed food mod. Report the three counts with
  examples. No fix in this pass — sizing the problem.

- [x] L14: **RightClickHarvest end-to-end.** `BlockPopResourceMixin` +
  `BlockDropSpoilageHandler.stampPending(level, pos, stack)` were changed on 2026-09-10 to
  cover direct `Block.popResource` callers, and the commit claims this covers any harvest
  mod. It was never driven with `rightclickharvest` actually loaded. Deploy the pack,
  right-click-harvest a vanilla crop and a modded crop, and read the dropped stack's
  component both times. Verifying a claim someone else made counts here because the claim is
  untested — but say in the entry which of the two cases you drove.

- [x] L14: **TPS with the pack loaded — and fix the baseline first.** A reading was taken by
  hand on 2026-09-10 with the pack deployed on a freshly started world: **0.1 ms average,

- [x] L1: **Six log files appear in every player's game directory, five of them always empty.**
  `enable_logging` and `enableTraceLogging` both default to `true`, and the logger opens
  `general.log`, `trace.log`, `hud.log`, `network.log`, `data.log` and `events.log` on start.
  Measured on 2026-09-22 from the dev runs: `general.log` holds 258 bytes on the client and
  1009 on the server, all of it startup lines (component registration, recipe scan, mixins),
  and the other five are **0 bytes**. So this is not a performance problem — writing is async
  on a bounded queue and nothing logs per tick — it is clutter shipped to users: a folder of
  empty files in `.minecraft`. Open each writer lazily, on the first line actually written to
  that category. Prove it by deleting `spoilage_enhanced_logs/` in a run directory, starting
  the game, and showing which files exist afterwards; the positive control is that
  `general.log` must still be created and still contain the startup lines.

- [x] L1: verify lazy log creation in run/fabric with a real launch — delete run/fabric/spoilage_enhanced_logs/, start the client, list the folder; general.log must exist with the startup lines and the other five must not.
- [x] L1: **Drive the Forge and NeoForge servers and record what they say.** On 2026-09-22 the
  mod was published announcing three loaders while only Fabric had ever been started, and a real
  Forge client died at `MixinInitialisationError: ... compatibility level JAVA_25 which is not
  recognised`. `options.release` and the mixin config are both pinned at 21 now, and
  `loadercheck.py` gates publishing on it — but the static check compares two numbers and
  cannot say the game starts. Run `loader_launch.ps1 -Loader forge` and `-Loader neoforge`
  (AGENT_ENV.md, "Verifying the mod loads on Forge and NeoForge"), and put the verdict and one
  copied line from each server log in the entry. If either says anything other than STARTED,
  that is the next task and it outranks everything in this queue.
# [x] src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java:388 — touch() does two map lookups (containsKey + get) — replace with single get() that returns non-null
# [x] src/main/java/com/spoilageenhanced/client/TooltipTextCache.java:115 — put() does two map lookups (containsKey + put) — replace with single put() that returns previous value
- [x] src/main/java/com/spoilageenhanced/config/SpoilageConfig.java:528 — isSpoilable does item_durations.containsKey(idStr) then get() — replace with single get() and null check (L5 render path)
- [x] src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java:332 — isTracked uses containsKey; replace with get() != null (L5 render path)
- [x] src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java:506 — hasChunkBirthTime uses containsKey; replace with get() != null (L5 render path)
- [x] src/main/java/com/spoilageenhanced/config/SpoilageConfig.java:968 — clampHandEditedValues comment says "clamp per-item durations so extreme values... don't wrap when multiplied or added" but only clamps <= 0, not huge values near Long.MAX_VALUE — fix comment or add clamp (L12 claim drift)
- [x] src/main/java/com/spoilageenhanced/util/FoodSpoilageUtil.java:1243 — classifyLegacyAge returns age < freshDuration + staleDuration ? STALE : ROTTEN but the overflow guard at line 1240 returns STALE forever — verify this is intentional and document why ROTTEN is never returned on overflow (L12 claim drift)

## Open (L5 render path refill - top yield 86.7%)

- [ ] src/main/java/com/spoilageenhanced/config/SpoilageConfig.java:1026 — migrateConfig uses item_durations.keySet().removeIf(id -> !baseline.item_durations.containsKey(id)) — replace with single get() check (L5 render path)
- [ ] src/main/java/com/spoilageenhanced/config/SpoilageConfig.java:1030 — migrateConfig uses tracked_blocks.keySet().removeIf(id -> !baseline.tracked_blocks.containsKey(id)) — replace with single get() check (L5 render path)
- [ ] src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java:82 — registerAll uses BuiltInRegistries.BLOCK.containsKey(Identifier.parse(blockId)) — replace with get() != null (L5 render path)
- [ ] src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java:83 — registerAll uses BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemId)) — replace with get() != null (L5 render path)
- [ ] src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java:457 — getChunkBirthTime uses getOrDefault(pos.pack(), -1L) — replace with get() and null check (L5 render path)
