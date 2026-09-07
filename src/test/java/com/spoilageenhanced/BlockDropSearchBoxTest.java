package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 157 regression test: AABB immutability — the reason the search-box reuse was refuted.
 *
 * The fallback entity scan in BlockDropSpoilageHandler.after() allocates a new AABB per
 * un-stamped break. The obvious fix — reuse a thread-local AABB mutated in place — is
 * impossible: AABB's six fields are public final, and the setMinX/setMaxX "setters"
 * return a NEW AABB, so chaining them allocates six objects instead of one. These tests
 * pin that API contract so a future pass does not re-attempt the same "optimization".
 */
public class BlockDropSearchBoxTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aabbSettersReturnNewObjectsNotThis() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        AABB returned = box.setMinX(-1.5);

        assertNotSame(box, returned,
                "setMinX must return a NEW AABB — AABB is immutable, fields are public final");
        assertEquals(0.0, box.minX, 0.0, "The original box must be unchanged");
        assertEquals(-1.5, returned.minX, 0.0, "The returned box must carry the new value");
    }

    @Test
    void chainedSettersAllocateSixObjects() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        // Each setter in the chain allocates a new AABB. This documents WHY the
        // thread-local reuse idea was refuted: the chain is 6 allocations, worse
        // than the single new AABB(pos).inflate(1.5) it was meant to replace.
        AABB chained = box.setMinX(-1.5).setMinY(-1.5).setMinZ(-1.5)
                .setMaxX(2.5).setMaxY(2.5).setMaxZ(2.5);

        assertNotSame(box, chained);
        assertEquals(-1.5, chained.minX, 0.0);
        assertEquals(2.5, chained.maxX, 0.0);
    }

    @Test
    void inflateBoundsMathIsPinned() {
        // Pin the search volume: new AABB(pos).inflate(1.5) covers
        // min = pos - 1.5, max = pos + 1 + 1.5 (the block plus 1.5 blocks around it).
        BlockPos pos = new BlockPos(100, 64, -200);
        AABB box = new AABB(pos).inflate(1.5);

        assertEquals(pos.getX() - 1.5, box.minX, 0.0);
        assertEquals(pos.getY() - 1.5, box.minY, 0.0);
        assertEquals(pos.getZ() - 1.5, box.minZ, 0.0);
        assertEquals(pos.getX() + 2.5, box.maxX, 0.0);
        assertEquals(pos.getY() + 2.5, box.maxY, 0.0);
        assertEquals(pos.getZ() + 2.5, box.maxZ, 0.0);
    }
}