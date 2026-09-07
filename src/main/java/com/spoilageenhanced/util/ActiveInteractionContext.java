package com.spoilageenhanced.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ActiveInteractionContext {
    private static final ThreadLocal<BlockPos> activeBlockPos = new ThreadLocal<>();
    private static final ThreadLocal<BlockState> activeBlockState = new ThreadLocal<>();
    /**
     * Pass 601 (L2 — lifecycle): the RETURN inject in InteractionManagerMixin clears the
     * context on every normal return, but an exception inside vanilla useItemOn (a modded
     * block's use handler throwing is common) skips it and the ThreadLocal leaks. The next
     * useItemOn overwrites it at HEAD, but between the throw and that next interaction an
     * unrelated Inventory.add — ItemEntity.playerTouch calls it on every item pickup
     * (ItemEntity.java:346) — would read the stale pos/state and stamp the picked-up stack
     * with spoilage derived from a block the player is no longer interacting with.
     *
     * <p>A useItemOn call completes within the same tick it starts, so a context older than
     * a couple of ticks can only be a leak. isActive() treats it as inactive and the next
     * start() overwrites the value, so the guard is self-healing without a removal path of
     * its own.</p>
     */
    private static final ThreadLocal<Long> startedAtGameTime = new ThreadLocal<>();
    private static final long MAX_AGE_TICKS = 2L;

    public static void start(BlockPos pos, BlockState state, long gameTime) {
        activeBlockPos.set(pos);
        activeBlockState.set(state);
        startedAtGameTime.set(gameTime);
    }

    public static void clear() {
        activeBlockPos.remove();
        activeBlockState.remove();
        startedAtGameTime.remove();
    }

    public static BlockPos getPos() {
        return activeBlockPos.get();
    }

    public static BlockState getState() {
        return activeBlockState.get();
    }

    /**
     * True only while a useItemOn call is in flight on this thread — or, after a leak,
     * only for the couple of ticks it could legitimately have been in flight for.
     */
    public static boolean isActive(long currentGameTime) {
        BlockPos pos = activeBlockPos.get();
        if (pos == null) {
            return false;
        }
        Long startedAt = startedAtGameTime.get();
        if (startedAt == null || currentGameTime - startedAt > MAX_AGE_TICKS
                || currentGameTime < startedAt) {
            return false;
        }
        return true;
    }
}
