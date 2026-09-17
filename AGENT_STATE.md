# Agent State

Pass: 1313
Goal: Hunt correctness defects, per-tick (TPS) cost, and UI/HUD problems. Fix them, prove each fix, commit.
Current task: L7 merge boundary batch - NO_BUG (max-stack merge, tracked+untracked merge, over-max and zero-count all clean)
Last lens: L7 boundary
Recent: NO_BUG FIXED NO_BUG NO_BUG NO_BUG
Barren streak: 1
Refills since L13: 4
Commits since: release = 0
Updated: 2026-09-17 04:09

## Notes

Short-lived context only: what the current task depends on, what the next pass should know. Not a history — history lives in `AGENT_LOG.md`.

**The first three unchecked lines in `TASKS.md` are player-reported defects, found by
actually playing the mod. Take them first, in order, before any lens-generated task.**
`PLAYER_REPORTS.md` in the project root carries the traced cause and the reasoning for each
— read it once when you start the first of them, not every pass. All three are runtime
behaviour (a HUD packet answer, a container click path, and a block-eating path); the unit suite
cannot see any of them, so Step 5 needs a real launch and a line from the fresh log.

Migration note: passes 1–119 were run under the previous rule set. The counter continues
from 119, so the next pass is 120. Older pass entries are in
`.claude/archive/AGENT_LOG_ARCHIVE.md`; the last 15 are in `AGENT_LOG.md`.

Uncommitted work was left in the tree from pass 120 (a mutability guard in
`FoodSpoilageUtil` plus `ExcessTrackerDrainTest`). Step 2 of the loop says unfinished work
wins — finish and commit that before starting anything new.

All three PLAYER_REPORTS defects now verified: §1 (lazy registration + chunk aging), §2 (worstSliceContainsRotten), §3 (CakeEatMixin poison on rotten cake via self-test harness). Barren streak reset to 0.

Pass 600 fixed L1 silent failure in computeIsSpoilable: unboundOut[0] now set to false when additional/duration checks match, so the answer is cached. Benchmark: cached 0.014 µs/call vs cold 74 µs/call.

Pass 601 fixed L2 lifecycle: ActiveInteractionContext now records game time at start(); isActive(t) answers false once the context is older than MAX_AGE_TICKS (2). Guards against exception-path ThreadLocal leaks.

Pass 602: L13 behaviour - 5 fresh scenarios on v1.0.89 all NO_BUG.

Pass 603: L3 cache - RenderDump.LAST_LINE unbounded growth fixed.

Pass 604: L3 cache - HudTextCache + FORMAT_TIME_CACHE caps enforced.

Pass 605: L4 hot-path - ItemMixin.inventoryTick CONTAINER check reordered.

Pass 606: L5 render-path - SpoilageBarPixels.compute int[3] allocation eliminated via Result record.

Pass 607: L7 boundary - GiveSpoiledCommand secondsRemaining=0 fixed.

Pass 608: L8 data round-trip - STREAM_CODEC negative rotten_count test added.

Pass 609: L13 behaviour - 5 fresh scenarios on v1.0.90 all NO_BUG.

Pass 610: L9 interaction - ComposterBlockMixin tracker extracted on rejected compost fixed.