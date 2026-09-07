package com.spoilageenhanced;

import com.spoilageenhanced.util.ActiveInteractionContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 144 regression test: ActiveInteractionContext thread-local lifecycle.
 *
 * Tests start/clear/getPos/getState/isActive. This is the context that the block
 * mixins use to remember which block the player is interacting with, so a broken
 * lifecycle (leftover context, missing clear) would make a stale block's spoilage
 * leak onto the wrong drop.
 */
public class ActiveInteractionContextTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearContext() {
        ActiveInteractionContext.clear();
    }

    @Test
    void inactiveByDefault() {
        assertFalse(ActiveInteractionContext.isActive(0L),
                "Context should be inactive before any interaction");
        assertNull(ActiveInteractionContext.getPos(),
                "Position should be null before any interaction");
        assertNull(ActiveInteractionContext.getState(),
                "State should be null before any interaction");
    }

    @Test
    void startSetsPosAndState() {
        long posLong = 12345L;
        BlockState state = Blocks.PUMPKIN.defaultBlockState();
        ActiveInteractionContext.start(BlockPos.of(posLong), state, 1000L);

        assertTrue(ActiveInteractionContext.isActive(1000L),
                "Context should be active after start()");
        assertEquals(BlockPos.of(posLong), ActiveInteractionContext.getPos(),
                "Position should match what was set");
        assertEquals(state, ActiveInteractionContext.getState(),
                "State should match what was set");
    }

    @Test
    void clearRemovesContext() {
        ActiveInteractionContext.start(BlockPos.of(100L), Blocks.STONE.defaultBlockState(), 1000L);
        assertTrue(ActiveInteractionContext.isActive(1000L));

        ActiveInteractionContext.clear();
        assertFalse(ActiveInteractionContext.isActive(1000L),
                "Context should be inactive after clear()");
        assertNull(ActiveInteractionContext.getPos(),
                "Position should be null after clear()");
        assertNull(ActiveInteractionContext.getState(),
                "State should be null after clear()");
    }

    @Test
    void startOverwritesPreviousContext() {
        ActiveInteractionContext.start(BlockPos.of(100L), Blocks.STONE.defaultBlockState(), 1000L);
        ActiveInteractionContext.start(BlockPos.of(200L), Blocks.PUMPKIN.defaultBlockState(), 1001L);

        assertEquals(BlockPos.of(200L), ActiveInteractionContext.getPos(),
                "Position should be the most recent start()");
        assertEquals(Blocks.PUMPKIN.defaultBlockState(), ActiveInteractionContext.getState(),
                "State should be the most recent start()");
    }

    @Test
    void isActiveReflectsPosPresence() {
        // isActive checks pos != null, not state
        ActiveInteractionContext.start(BlockPos.of(50L), null, 1000L);

        assertTrue(ActiveInteractionContext.isActive(1000L),
                "isActive should be true when pos is set, even with null state");
        assertEquals(BlockPos.of(50L), ActiveInteractionContext.getPos());
        assertNull(ActiveInteractionContext.getState(),
                "State should be null when explicitly set to null");
    }

    @Test
    void clearIsIdempotent() {
        // Clearing when nothing was started should not throw
        ActiveInteractionContext.clear();
        ActiveInteractionContext.clear();
        ActiveInteractionContext.clear();

        assertFalse(ActiveInteractionContext.isActive(0L));
        assertNull(ActiveInteractionContext.getPos());
        assertNull(ActiveInteractionContext.getState());
    }
}