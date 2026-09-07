package com.spoilageenhanced;

import com.spoilageenhanced.util.ActiveInteractionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 601 (L2 — lifecycle): the RETURN inject in InteractionManagerMixin clears the
 * context on every normal return, but an exception inside vanilla useItemOn skips it and
 * the ThreadLocal leaks. The next useItemOn overwrites it at HEAD, but between the throw
 * and that next interaction an unrelated Inventory.add — ItemEntity.playerTouch calls it
 * on every item pickup — would read the stale pos/state and stamp the picked-up stack
 * with spoilage derived from a block the player is no longer interacting with.
 *
 * <p>The fix records the game time at start(); isActive(t) answers false once the context
 * is older than MAX_AGE_TICKS (2). This test pins that contract without needing a live
 * server: start the context, verify it is active within the window, verify it reads as
 * inactive past it, and verify clear() resets everything.</p>
 */
public class ActiveInteractionContextTtlTest {

    @AfterEach
    void cleanup() {
        ActiveInteractionContext.clear();
    }

    @Test
    void contextIsActiveWithinWindow() {
        BlockPos pos = new BlockPos(1, 2, 3);
        ActiveInteractionContext.start(pos, Blocks.STONE.defaultBlockState(), 1000L);
        assertTrue(ActiveInteractionContext.isActive(1000L),
                "Context must be active in the same tick it started");
        assertTrue(ActiveInteractionContext.isActive(1001L),
                "Context must be active one tick later (useItemOn completes within a tick)");
        assertTrue(ActiveInteractionContext.isActive(1002L),
                "Context must be active at the TTL boundary (age == MAX_AGE_TICKS)");
        assertSame(pos, ActiveInteractionContext.getPos(), "Pos must be readable while active");
        assertNotNull(ActiveInteractionContext.getState(), "State must be readable while active");
    }

    @Test
    void leakedContextReadsInactivePastTtl() {
        ActiveInteractionContext.start(new BlockPos(1, 2, 3), Blocks.STONE.defaultBlockState(), 1000L);
        // A useItemOn call completes within the tick it starts. A context still set three
        // ticks later can only be a leak from an exception path — it must read as inactive.
        assertFalse(ActiveInteractionContext.isActive(1003L),
                "Context older than MAX_AGE_TICKS must read as inactive (leak guard)");
        assertFalse(ActiveInteractionContext.isActive(2000L),
                "Context from a much earlier tick must read as inactive");
    }

    @Test
    void timeGoingBackwardsReadsInactive() {
        ActiveInteractionContext.start(new BlockPos(1, 2, 3), Blocks.STONE.defaultBlockState(), 1000L);
        // A dimension change or server restart can move game time backwards; the guard
        // must not treat a future-dated context as fresh forever.
        assertFalse(ActiveInteractionContext.isActive(999L),
                "Context with a future start time must read as inactive");
    }

    @Test
    void clearResetsEverything() {
        ActiveInteractionContext.start(new BlockPos(1, 2, 3), Blocks.STONE.defaultBlockState(), 1000L);
        ActiveInteractionContext.clear();
        assertFalse(ActiveInteractionContext.isActive(1000L),
                "Context must be inactive after clear()");
        assertNull(ActiveInteractionContext.getPos(), "Pos must be null after clear()");
        assertNull(ActiveInteractionContext.getState(), "State must be null after clear()");
    }

    @Test
    void restartOverwritesStaleLeak() {
        ActiveInteractionContext.start(new BlockPos(1, 2, 3), Blocks.STONE.defaultBlockState(), 1000L);
        // Simulate the leak: no clear() happened, time moved on.
        assertFalse(ActiveInteractionContext.isActive(1005L));
        // The next useItemOn overwrites at HEAD — the new context must be active.
        BlockPos newPos = new BlockPos(9, 9, 9);
        ActiveInteractionContext.start(newPos, Blocks.DIRT.defaultBlockState(), 1005L);
        assertTrue(ActiveInteractionContext.isActive(1005L),
                "A fresh start must read as active regardless of any prior leak");
        assertSame(newPos, ActiveInteractionContext.getPos(),
                "The new pos must replace the leaked one");
    }
}
