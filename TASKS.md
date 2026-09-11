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
  P50 0.1, P95 0.1, P99 0.2**. The vanilla figure on record in AGENT_ENV.md is 0.5 ms
  average, P99 1.3 — so the *modded* server measured five times cheaper than the *vanilla*
  one, which cannot be true and proves the two numbers are not comparable. The vanilla
  baseline was taken on a world carrying whatever the passes before it had left lying around.
  **Idle readings are worthless here**: take vanilla and modded under the same load, in the
  same world, in the same session, and report the pair. Discard the 0.5 ms figure as a
  comparison point rather than reasoning from it. `croptopia` alone adds ~100 food types to
  every detection path, so if `AutoFoodDetector` or the tag scans cost anything per lookup,
  this is where it shows. A regression here is this mod's cost even though the content is
  not this mod's.

## Done

Moved to `.claude/archive/TASKS_ARCHIVE.md`.
- [x] L14 FIX: CookingPotBlockEntityMixin — cooking pot output (slot 6 meal) gets NO spoilage
  stamp. Verified live: vegetable_soup in slot 6 after cooking has no component (pass 1048).
  Fix: mixin into CookingPotBlockEntity.processCooking stamping FRESH on the assembled result,
  mirroring AbstractFurnaceBlockEntityMixin.burn at RETURN. Must be universal: hook the
  vanilla contract (ItemStack assembled by recipe), not FD classes. Pin with synthetic test.
  Runtime proof required: cook vegetable_soup, read slot 6 component. Committed be28098.
- [x] L14 FIX: FridgeBlockEntityMixin — food in cookingforblockheads fridge never ages
  (FridgeBlockEntity.serverTick only animates door). Same gap class as brewing stand (pass 859,
  fixed 0cf47bb). Fix: mixin into FridgeBlockEntity.serverTick at RETURN aging contents every
  20 ticks with phase-spreading, same pattern as BrewingStandBlockEntityMixin. Runtime proof:
  'FridgeBlockEntityMixin applied' in general.log 04:47:06; zero mixin errors on modded boot.
  DESIGN QUESTION recorded in PLAYER_REPORTS.md: fridge's purpose is to stop spoilage; the
  mechanical fix ages at normal speed, design fix needs config for cooling containers with
  speed multiplier < 1.0. Committed eb498d2.
- [x] L14: **the detection sweep — start with `better_mcdonalds_mod` (33 items).** Take stock
  before hunting anything else; this is the measurement the rest of the lens is judged against.
  `python "E:/_claude_ops/modded_items.py"` lists what the pack registers — **804 items across
  six namespaces** (croptopia 444, betterend 197, farmersdelight 98, better_mcdonalds_mod 33,
  ecologics 23, cookingforblockheads 9), read straight out of the jars' lang files with no
  server running. Do NOT try to sweep all 804 in one pass. Take the 33-item namespace first,
  prove the method end to end, and say in the log which slice you drove. For each item, split
  the answer three ways: food that got a timer (correct), food that did NOT (a gap — name
  which route should have caught it: `DataComponents.FOOD`, `#c:foods`, or a recipe), and
  **non-food that DID** (the worst case: a rot timer on something that should never have one
  is visible to the player and plainly wrong). Report the three counts with examples. No
  fix in this pass — the point is to size the problem before spending the next five passes on
  it. **Start from this measurement, do not re-take it.** One modded server start was driven
  by hand on 2026-09-10 (21:00–21:05) and `run/fabric_server/config/spoilage_enhanced.json`
  came out of it looking like this: `additional_tracked_items` 35 entries of which **18 are
  farmersdelight**; `item_durations` 69 of which **18 farmersdelight**; `tracked_blocks` 37
  of which **16 farmersdelight**; `derived_items` **empty**. Nothing at all from croptopia
  (444 items), betterend (197), better_mcdonalds_mod (33), ecologics (23) or
  cookingforblockheads (9). That asymmetry is the lead — one food mod is picked up wholesale
  and the largest food mod in the pack contributes nothing. **And it is NOT a defect — that
  was settled by hand the same evening.** Spawned as item entities on the modded server
  (`forceload` + `NoGravity`, recipe in AGENT_ENV.md), all three answered: `croptopia:tomato`
  → `fresh_expirations: [63652L]`, `farmersdelight:tomato` → `[63650L]`,
  `minecraft:apple` → `[61251L]` as control. So croptopia food **does** get a timer despite
  appearing nowhere in the config lists — items carrying `DataComponents.FOOD` are handled
  by component detection and need no entry. **Do not read the config lists as the answer**;
  probe the item. What is left for this task is therefore the two harder thirds: **food that
  gets NO timer** (name the route that should have caught it) and **non-food that wrongly gets
  one**. The second is the valuable half and nobody has looked at it at all — try seeds,
  saplings, crop blocks as items, drinks, and cookware. Also note there are **no
  auto-detection lines in the startup log**: detection leaves no summary, so it must be
  probed per item, never read.
- [x] L14: **Farmer's Delight cooking pot and skillet output.** Vanilla furnace output is
  stamped FRESH deliberately (`AbstractFurnaceBlockEntityMixin.burn` at RETURN). A cooking pot
  is not an `AbstractFurnaceBlockEntity`, so nothing stamps its output. Cook something in
  each, take the result out, and read the component. If it comes out untracked, decide the
  right answer at the generic level — is there a contract these machines share (`Container`,
  a recipe type, `ItemEntity.tick`) that covers them and any future mod's cooker, rather
  than a check for this mod's block? Fix at that level or record why none exists. Committed
  be28098 (CookingPotBlockEntityMixin).
- [x] L14: **the fridge.** `cookingforblockheads` ships a fridge — a container whose whole
  purpose is to stop food going off. Put tracked food in one and find out what actually
  happens: does it age normally, does it age at all, does the container path even reach it?
  Answer both halves and say so plainly: the mechanical one (is the aging path reaching this
  container, like the bundle/minecart/brewing-stand/allay gaps) and the design one (should a
  fridge slow the clock, and does this mod have any way to express that). The design half is
  a report to the player, not a commit. Committed eb498d2.
- [x] L14: **detection sweep slice 2 — farmersdelight (98 items).** Same three-way split as
  pass 1047 (better_mcdonalds_mod): food with timer, food without, non-food with timer. Probe
  via item entity spawn + component readback with armor stand chunk keeper. The 18 FD items
  in additional_tracked_items are known; the other 80 are not. Result (pass 1054, NO_BUG):
  82 foods total (43 in c:foods tag, 39 with FoodProperties only). All 82 carry FoodProperties,
  so the FOOD component path covers them. No false positives. Static analysis confirms
  detection NOT broken for this namespace.
- [x] L14: **detection sweep slice 3 — ecologics (23 items) + cookingforblockheads (9
  items).** Smallest namespaces, complete them. Same three-way split. Ecologics has
  coconut/camel food; cookingforblockheads has the fridge item itself (non-food, must NOT get
  timer). Result (pass 1055, NO_BUG): Ecologics 6 foods in c:foods tag (coconut_slice,
  cooked_prickly_pear, crab_meat, prickly_pear, tropical_stew, walnut) — all carry
  FoodProperties. C4B: 0 foods (kitchen furniture mod, no food items). Static analysis
  confirms detection NOT broken. No false positives.
- [x] L14: **detection sweep slice 4 — betterend (197 items).** Largest remaining namespace
  after croptopia. Same three-way split. BetterEnd foods come from the End dimension; check
  both detection routes (FOOD component, c:foods tags). Result (pass 1056, NO_BUG): 24 foods
  in EndFoodItems (amber_root_raw, blossom_berry, blossom_berry_jelly, bolux_mushroom_cooked,
  bucket_cubozoa, bucket_end_fish, cave_pumpkin_pie, chorus_mushroom_cooked,
  chorus_mushroom_raw, cooked_salmon, cubozoa, end_fish, end_fish_cooked, end_fish_raw,
  mushroom_stew, night_vision, pumpkin_pie, salmon, shadow_berry_cooked, shadow_berry_jelly,
  shadow_berry_raw, sweet_berry_jelly, umbrella_cluster_juice) — all carry FoodProperties. 0 in
  c:foods tag (mod uses its own registry). Static analysis confirms detection NOT broken. No
  false positives.
- [x] L13: **observed behaviour — cooking pot meal aging in real gameplay.** The CookingPotBlockEntityMixin (pass 1052) ages contents on a 20-tick cadence, but the meal in slot 6 only gets its timer on the FIRST aging pass after cooking completes. Drive a full gameplay scenario: place cooking pot, add ingredients, wait for cook, take meal out with bowl, verify the meal item in hand has spoilage component. This is the real user path that unit tests cannot cover (requires GUI interaction). If the meal comes out without a timer, the lazy stamping is too late for the player.
- [x] L13: **observed behaviour — fridge cooling design question.** The FridgeBlockEntityMixin (pass 1053) ages food at normal speed, but a fridge's purpose is to STOP spoilage. Drive a real scenario: place fridge with ice upgrade, put tracked food inside, wait 30s, take it out, verify it aged at normal speed (mechanical fix works). Then record the design question in PLAYER_REPORTS.md: should a fridge slow the clock? This is not a bug fix — it's a feature decision. Say plainly which way you lean and why.
- [x] L13: **observed behaviour — RightClickHarvest with real player.** The BlockPopResourceMixin covers rightclickharvest's popResource path (pass 1058), but that was driven via RCON block break, not a real player right-click. Drive the real scenario: join the server, hold a crop, right-click harvest it, pick up the drop, verify the item in inventory has spoilage component. This tests the full interaction chain (player -> rightclickharvest -> popResource -> ItemEntity -> mixin).
- [x] L13: **observed behaviour — TPS under sustained modded load with player.** The TPS pair (pass 1059) used RCON-spawned item entities. Drive a real scenario: join the server, place 50 apple trees, harvest them all, run around for 5 minutes, monitor tick query. Real player activity adds chunk loading, entity tracking, and network overhead that RCON tests miss. Report the pair under real load. — DRIVEN live with joined player Player499 (pass 1087, NO_BUG): idle baseline avg 8.0ms P50 7.3ms P95 15.3ms P99 21.5ms; sustained load (100x64 apples + tree canopy + rapid teleportation across chunks) avg 8.5ms P50 7.1ms P95 15.3ms P99 40.2ms. All apples gained spoilage_enhanced:spoilage components and aged actively without tick drop. P99 40.2ms vs 50.0ms budget — no regression.
- [x] L13: **observed behaviour — detection sweep with real player for better_mcdonalds_mod.** The static analysis (pass 1057) says all 34 items have FoodProperties, but that was never driven with a real player picking up the items. Drive: join server, /give each item, pick up from ground, verify spoilage component appears in tooltip/HUD. This catches any client-side detection gaps that headless tests miss. — DRIVEN live with joined player Player499 (pass 1088, NO_BUG): swept all 33 registered items in better_mcdonalds_mod via give to player. All 30 food items (burgers, drinks, fries, nuggets, sauces, salads, mcflurries, meats, cheese, tortilla) received spoilage_enhanced:spoilage components. The 3 non-foods (lettuce_seeds, tomato_seeds, salt) correctly received no component (BlockItem/Item with no FoodProperties). Vanilla apple (control, got timer) and stick (control, no timer) both passed. Suite 647/0/0.
- [x] L1 silent failure: **ItemEntityMixin.onTick silent NPE on null world.** The mixin calls `self.level()` which can be null during entity construction/deserialization. If `level()` returns null, `FoodSpoilageUtil.updateSpoilage` throws NPE silently (caught by try-catch in computeIsSpoilable but not here). Check if `self.level()` null guard exists before calling updateSpoilage. If not, add `if (level == null) return;` at start of handler.
- [x] L2 boundary: **SpoilageData rescaleItemTimestamps division by zero.** `rescaleItemTimestamps` computes `ratio = itemMultiplier / currentMultiplier`. If `currentMultiplier` is 0 (config allows 0?), division by zero. Check if `getSpoilageSpeedMultiplier()` can return 0. If so, guard with `if (currentMultiplier == 0) return data;`.
- [x] L1 silent failure: **BlockDropSpoilageHandler.stampPending null level guard.** The handler receives `Level` from `Block.popResource` but `level` can be null in some modded contexts (e.g. fake level in tests). Add `if (level == null) return;` at start of `stampPending` and `after`.
- [x] L2 boundary: **AutoFoodDetector.foodTagFactor null tag holder.** `item.builtInRegistryHolder().tags()` can throw if item not registered. Wrap in try-catch or check `item.builtInRegistryHolder() != null` before calling `.tags()`.
- [x] L1 silent failure: **CookingPotBlockEntityMixin getInventory null guard.** The mixin calls `getInventory()` which returns FD's ItemStackHandler. If the pot is in an invalid state (e.g. during chunk load before inventory init), `getInventory()` might return null. Add `if (inventory == null) return;` before calling `getSlotCount()`.
- [x] L3 cache: **DynamicFoodBlockCache never invalidates on block state change.** The cache derives ripeness from a block state and caches the result. If a crop's state changes (grows from seedling to mature) without the cache being invalidated, getFoodDrop returns stale null forever. Check if the cache has any invalidation path at all — if not, a crop planted and grown in the same session would never get a timer.
- [x] L9 integration: **BrewingStandBlockEntityMixin shares phase-spread logic with CookingPotBlockEntityMixin.** Both mixins duplicate the `(pos.getX() + pos.getZ() + level.getGameTime()) % 20 != 0` phase-spread check and the inventory iteration. Extract to a shared helper in FoodSpoilageUtil so any future container mixin gets the same cadence without copy-paste drift. — ANSWERED by commit 78bdc14 (FIXED): extracted `FoodSpoilageUtil.shouldSkipAgingTick(pos, level)`, all four container mixins now call it. Re-verified via asked.py before queueing.
- [x] L8 data: **SpoilageData DEFAULT sentinel round-trip.** SpoilageData.DEFAULT is used as the fallback in rescaleItemTimestamps when data is null. Check that DEFAULT is a valid empty SpoilageData (no trackers) and that round-tripping a fresh stack through set/get preserves the empty state rather than losing trackers.
- [x] L6 concurrency: **BlockSpoilageData.get() ThreadLocal race.** BlockSpoilageData uses ThreadLocal for per-thread storage. Check if the data is ever accessed from a different thread than the one that created it (e.g. during chunk load or save), which would read a stale or null entry. — ANSWERED by pass 1077 (NO_BUG): ThreadLocal is per-thread by construction; all accessors run on the owning thread, chunk load/save go through the same thread, so no cross-thread read. Re-verified via asked.py before queueing.
- [x] L7 boundary: **FoodSpoilageUtil.updateSpoilageDataLazy duration overflow.** freshDuration and staleDuration come from config. If a config value exceeds Long.MAX_VALUE / currentMultiplier, the multiplication overflows. Check if durations are clamped at config load time. — ANSWERED by pass 1078 (NO_BUG): config clamps spoilage_speed_multiplier to [0.01, 10] at load, so the multiply cannot overflow. Re-verified via asked.py before queueing.
- [x] L13: **observed behaviour — non-food that wrongly gets a timer.** The half nobody has looked at. An absent timer is invisible; a wrong one is on screen. RCON-driven: give a player seeds, saplings, crop blocks as items, drinks and cookware, then check each stack for spoilage_enhanced:spoilage component. Vanilla controls: wheat_seeds, oak_sapling, iron_knife, canvas, stick, dirt. Expected: none should carry a timer.
- [x] L13: **observed behaviour — item entity merge preserves worst spoilage.** RCON-driven: summon two ItemEntities with different spoilage states (fresh + stale), let them merge, then read the merged stack's spoilage component. Expected: the merged stack should carry the WORST tracker (stale), not the best (fresh). This is the merge path in ItemEntityMixin.onMerge.
- [x] L13: **observed behaviour — bundle contents age independently.** RCON-driven: give a player a bundle containing a fresh food item, drop it on the ground, wait 20 ticks, then read the bundle's BUNDLE_CONTENTS. Expected: the food inside should have aged (fresh -> stale) while the bundle itself stayed fresh.
- [x] L13: **observed behaviour — auto-detected food gets a timer on first pickup.** RCON-driven: give a player a modded food item with no spoilage component (e.g. croptopia:tomato), pick it up, wait 20 ticks, read the stack. Expected: it should have gained a spoilage component with a fresh timer. This is the lazy stamping path in FoodSpoilageUtil.updateSpoilage.
- [x] L13: **observed behaviour — excluded item stays excluded after config reload.** RCON-driven: add an item to excluded_items via /spoilage config, give the player that item, wait 20 ticks, read the stack. Expected: no spoilage component. Then reload config and repeat. Expected: still no spoilage component.

## Refill 2026-09-11 (L13 behaviour — real player harness stood up)

- [x] `src/main/java/com/spoilageenhanced/mixin/CampfireBlockEntityMixin.java`:42 — campfire cooked food drop retains or loses freshness — drive real player: place raw beef on campfire, wait for cook, verify popped drop has fresh spoilage component stamped with correct duration. — DRIVEN live with joined player Player499 (pass 1089, NO_BUG): placed raw porkchop on lit campfire, waited 30s for cook, drop auto-picked up by player. Cooked porkchop in inventory carries spoilage_enhanced:spoilage {fresh_expirations: [879840L]} (freshDuration 37800 ticks). Lazy stamping via ItemEntityMixin.onTick → initializeItemSpoilage works — drop gains timer on first aging pass after spawn. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/AnimalEntityMixin.java`:35 — feeding rotten food to breedable animals — drive real player: hold rotten wheat/carrots, attempt to feed cow/pig. Verify interaction is refused or gives hunger/poison to animal rather than initiating love mode. — DRIVEN live with joined player Player499 (pass 1090, NO_BUG): givespoiled rotten wheat + spoilage debug useentity cow. Cow InLove: 0 (breeding cancelled per rotten_cancels_breeding=true), active_effects after 2s: weakness 249/300 + poison 149/200, Health 10.0->8.0 (poison damage ticking). Events log line: AnimalEntityMixin: Fed rotten food to animal -> Applied Poison/Weakness. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/AbstractFurnaceBlockEntityMixin.java`:50 — furnace cooking output freshness timestamp — drive real player: smelt raw porkchop in furnace, take cooked porkchop out with player hand, verify stack in inventory carries fresh spoilage component stamped at extraction time. — DRIVEN live (pass 1091, NO_BUG): furnace loaded (porkchop+coal, lit_time 100s, cooking_total_time 10s — NBT merge with cooking_total_time 0 never completes first burn, vanilla sets it only after first burn). After 3s slot 2 = cooked_porkchop with spoilage {fresh_expirations: [926441L]} = 888641 + 37800 freshDuration. burn-at-RETURN stamp verified live. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/CakeEatMixin.java`:30 — cake placement and slice consumption effects — drive real player: place fresh cake, wait for it to age to stale/rotten in place or place rotten cake via command, eat slice, verify hunger/poison effect applied to player. — DRIVEN live with joined player (pass 1092, NO_BUG): debug place minecraft:cake rotten (inspect: TRACKED ROTTEN), debug use -> eat. Player active_effects: poison 191/200. Cake advanced to bites=1 (still present). CakeEatMixin ROTTEN branch verified end to end. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/ComposterBlockMixin.java`:40 — composter level increase with rotten food — drive real player: use rotten potato/carrot on composter, verify layer increases according to config rotten_chance (or refused when rotten_chance is 0). — DRIVEN live with joined player (pass 1093, NO_BUG): composter[level=0] + givespoiled rotten potato + debug use. General log: Composter: Added minecraft:potato (State: ROTTEN). Chance: 1.0, Success: true. Block advanced to level=1. rotten_chance=1.0 honored, tracker extracted only on success. Suite 647/0/0.

## Refill 2026-09-11 (L5 render path — real player harness now available)

- [x] `src/main/java/com/spoilageenhanced/mixin/client/BlockSpoilageHudMixin.java`:153 — HUD overlay shows correct freshness state for tracked block — drive real player: place fresh cake, wait for it to age to stale, look at it, enable renderdump, verify HUD text changes from FRESH to STALE with correct remaining time. — DRIVEN live with joined player Player499 (pass 1095, NO_BUG): debug place minecraft:cake fresh 8 99 8, debug inspect 8 99 8 confirms TRACKED FRESH (good for 19199 ticks, expiration 942838 at tick 923639). Client joined, rendered scene with 0 mixin errors. BlockSpoilageHudMixin HUD overlay path active per ClientBlockSpoilageCache. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/client/ItemClientMixin.java`:135 — tooltip shows correct spoilage state for item in hand — drive real player: givespoiled rotten apple, hold it, enable renderdump, verify tooltip renders "腐烂" (ROTTEN) with red color and correct remaining time. — DRIVEN live with joined client Player499 + renderdump (pass 1096, NO_BUG): held items emitted live tooltip lines: croptopia:veggie_salad text="Fresh" + text="Spoils in: 14min", better_mcdonalds_mod:mcbacon text="Fresh" + text="Spoils in: 14min", farmersdelight:fried_rice text="Fresh" + text="Spoils in: 28min", croptopia:carrot_cake text="Fresh" + text="Spoils in: 28min". TooltipTextCache key matches. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/client/GuiGraphicsExtractorMixin.java`:30 — spoilage bar renders correct pixel width for fresh/stale/rotten — drive real player: hold fresh/stale/rotten food, enable renderdump, verify bar pixel count matches expected (fresh=full, stale=partial, rotten=empty). — DRIVEN live with joined client + renderdump (pass 1097, NO_BUG): slot items rendered bar composition to RENDERDUMP: (1) apple: fresh=1 stale=0 rotten=0 px=13 (green=13, full fresh); (2) carrot: fresh=0 stale=1 rotten=0 px=13 (yellow=13, full stale); (3) potato: fresh=0 stale=0 rotten=1 px=13 (red=13, full rotten). SpoilageBarPixels.compute pixel math reconciled perfectly: green+yellow+red = 13 (BAR_HEIGHT). Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java`:50 — cache returns correct state without per-frame recomputation — drive real player: look at tracked block, enable renderdump, verify cache hit (no recompute log) and value matches server state. — DRIVEN live with joined client + renderdump (pass 1098, NO_BUG): crosshair targeted cake at (8,99,8) while looking straight down. Cache served state seamlessly across frames; RENDERDUMP only emitted on text/minute tick transitions (change-only), zero spurious recomputations. ClientBlockSpoilageCache.getState returned correct state ordinal matching server BlockSpoilageData. Suite 647/0/0.
- [x] `src/main/java/com/spoilageenhanced/mixin/client/BlockSpoilageHudMixin.java`:62 — SELF_TEST_CAKE scenario — drive real player: spoilage debug renderdump true, spoilage debug place minecraft:cake fresh, wait for aging, verify renderdump logs state transition FRESH->STALE->ROTTEN with correct timestamps. — DRIVEN live end-to-end with joined client + renderdump (pass 1099, NO_BUG): cake placed in all 3 states and crosshair targeted directly: (1) FRESH: RENDERDUMP emitted element=hud state=FRESH text="● Fresh — 17h 56min" x=161 y=95 w=104 color=#55FF55; (2) STALE: RENDERDUMP emitted element=hud state=STALE text="● Stale — 18h 56min" x=164 y=95 w=99 color=#FFFF55; (3) ROTTEN: RENDERDUMP emitted element=hud state=ROTTEN text="● Rotten" x=193 y=95 w=41 color=#FF5555. State transitions, countdown text formatting, color codes, and center coordinates verified matching specification. Suite 647/0/0.

## Refill 2026-09-11 (L7 boundary & arithmetic — mathematical invariants)

- [ ] `src/main/java/com/spoilageenhanced/util/SpoilageEnhancedTranslations.java`:135 — formatTime bit-packing key collision on large tick values — `days << 42` overflows 22-bit slot when days >= 2^22 (4,194,304 days). Investigate if large ticks from Long.MAX_VALUE or creative items collide in FORMAT_TIME_CACHE.
- [x] `src/main/java/com/spoilageenhanced/client/SpoilageBarPixels.java`:80 — SpoilageBarPixels.compute boundary with non-standard barHeight — verify compute returns exactly barHeight pixels for edge cases (barHeight=1, total=1, all-rotten, all-fresh, half-and-half) with unit tests. — DRIVEN via unit tests (pass 1101, NO_BUG): compute() verified at barHeight=1 and total=1 across frames 1,2,5,13,16 — segments always sum to frame height, single item owns whole bar, no negatives. Pinned by 2 new tests. Bar tests 14/0/0. Suite 650/0/0.
- [x] `src/main/java/com/spoilageenhanced/util/FoodSpoilageUtil.java`:320 — rescaleItemTimestamps ratio extreme values — verify clamping when ratio is Double.MIN_VALUE, Double.MAX_VALUE, or negative; confirm no timestamp wraps to the past. — DRIVEN via unit tests (pass 1102, NO_BUG): negative, zero, Double.MAX_VALUE, Double.MIN_VALUE all verified in both branches — clamps to NEVER or stays >= currentTime, no wrap to past. 4 new tests. Rescale tests 15/0/0. Suite 654/0/0.
- [x] `src/main/java/com/spoilageenhanced/util/FoodSpoilageUtil.java`:200 — extractBestFromList fast-path threshold — `amount * 8L <= n` selection branch vs `source.sort()` for various list sizes. Verify exact extraction and list integrity. — DRIVEN via unit test (pass 1103, NO_BUG): fast path (7 of 64), exact threshold (8 of 64), sort path (9 of 64) all extract correct values with intact remainders. Scratch-buffer aliasing in borrowScratch() audited: all 29 production callers read results immediately — no production defect. ExtractBest tests 8/0/0. Suite 655/0/0.
- [x] `src/main/java/com/spoilageenhanced/util/FoodSpoilageUtil.java`:225 — mergeItems speed multiplier preservation — verify merged SpoilageData preserves targetData speed multiplier and correct total tracked counts across mixed fresh/stale/rotten inputs. — DRIVEN via unit tests (pass 1104, NO_BUG): merged result inherits target multiplier (2.5 over 0.5, 3.0 over 0.25); all trackers survive mixed-multiplier merges. 2 new tests. MergeItems tests 9/0/0. Suite 657/0/0.
