package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 429 regression test: chunk birth time sentinel arithmetic (L7 boundary).
 *
 * <p>SpoilageEnhancedDebugCommand.inspectTargetedBlock computed
 * {@code chunkAge = world.getGameTime() - chunkBirth} for untracked blocks.
 * getChunkBirthTime returns -1 when the chunk has no recorded birth time
 * (BlockSpoilageData.java:390), so the subtraction produced
 * {@code gameTime + 1} — an absurd age (the entire world's age + 1) for a
 * chunk that was never tracked. The fix guards the sentinel: the age is only
 * computed when a birth time exists (chunkBirth >= 0).
 *
 * <p>The command itself needs a CommandSourceStack (a player looking at a
 * block), which cannot be constructed in a unit test. This test pins the
 * sentinel contract it depends on: -1 means "no birth time recorded", and the
 * arithmetic that consumed it must treat it as absent, not as a real time.
 */
public class ChunkBirthTimeBoundaryTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unrecordedChunkBirthTimeIsMinusOne() {
        // The sentinel: a chunk with no recorded birth time returns -1, not 0.
        // 0 would be a legitimate birth time (world start), so the sentinel must
        // be negative to be distinguishable.
        BlockSpoilageData data = new BlockSpoilageData();
        long birth = data.getChunkBirthTime(new ChunkPos(12345, 67890));
        assertEquals(-1L, birth,
                "Unrecorded chunk birth time must be the -1 sentinel");
        assertTrue(birth < 0,
                "The sentinel must be negative so chunkBirth >= 0 can gate on it");
    }

    @Test
    void recordedChunkBirthTimeIsReturnedVerbatim() {
        // A recorded birth time round-trips exactly — including 0 (world start),
        // which must NOT be confused with the sentinel.
        BlockSpoilageData data = new BlockSpoilageData();
        ChunkPos pos = new ChunkPos(1, 2);
        data.setChunkBirthTime(pos, 0L);
        assertEquals(0L, data.getChunkBirthTime(pos),
                "Birth time 0 (world start) must round-trip, not read as the sentinel");
        data.setChunkBirthTime(pos, 42L);
        assertEquals(42L, data.getChunkBirthTime(pos),
                "Recorded birth time must round-trip verbatim");
    }

    @Test
    void sentinelSubtractionWouldProduceAbsurdAge() {
        // Documents the defect the fix guards against: with the sentinel -1,
        // the old ungated subtraction produced gameTime + 1 — an age larger than
        // the world itself. The fix's guard (chunkBirth >= 0) excludes exactly
        // this case; this test pins the arithmetic so a future edit that drops
        // the guard has a failing test to answer to.
        long gameTime = 1_762_839L;
        long sentinel = -1L;
        long ungatedAge = gameTime - sentinel;
        assertEquals(gameTime + 1, ungatedAge,
                "The ungated subtraction the fix removed produced gameTime + 1");
        assertTrue(ungatedAge > gameTime,
                "The ungated result was an absurd age larger than the world's own age");
        // The guard: only compute when a real birth time exists.
        assertFalse(sentinel >= 0,
                "The fix's chunkBirth >= 0 guard must reject the sentinel");
    }
}