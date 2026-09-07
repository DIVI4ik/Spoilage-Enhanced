package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 148 regression test: chunk birth time cap.
 *
 * The chunkBirthTimes map grows by one entry per explored chunk and is serialized into the
 * world save on every write. Without a cap a long-running server would bloat the save file
 * linearly with distance travelled. The cap (MAX_CHUNK_BIRTH_TIMES) bounds it; eviction drops
 * the oldest chunk (smallest birth time).
 */
public class ChunkBirthTimeCapTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void setChunkBirthTimeRespectsCap() {
        BlockSpoilageData data = new BlockSpoilageData();

        // Fill to just under the cap
        int cap = 65536;
        for (int i = 0; i < cap; i++) {
            ChunkPos pos = new ChunkPos(i, 0);
            data.setChunkBirthTime(pos, 1000L + i);
        }
        assertEquals(cap, data.getChunkBirthTimes().size(),
                "Map should be at capacity before overflow");

        // Add one more — should evict the oldest (smallest birth time)
        ChunkPos newPos = new ChunkPos(cap, 0);
        data.setChunkBirthTime(newPos, 1000L + cap);

        // Map size should not exceed the cap
        assertTrue(data.getChunkBirthTimes().size() <= cap,
                "Map size should not exceed cap, was " + data.getChunkBirthTimes().size());

        // The new entry should be present
        assertTrue(data.hasChunkBirthTime(newPos),
                "New entry should be present after eviction");

        // The oldest entry (birth time 1000) should have been evicted
        ChunkPos oldestPos = new ChunkPos(0, 0);
        assertFalse(data.hasChunkBirthTime(oldestPos),
                "Oldest chunk (smallest birth time) should have been evicted");
    }

    @Test
    void setChunkBirthTimeDoesNotEvictWhenUpdatingExisting() {
        BlockSpoilageData data = new BlockSpoilageData();

        // Fill to capacity
        int cap = 65536;
        for (int i = 0; i < cap; i++) {
            ChunkPos pos = new ChunkPos(i, 0);
            data.setChunkBirthTime(pos, 1000L + i);
        }

        // Update an existing entry (same chunk, new birth time) — should NOT trigger eviction
        ChunkPos existing = new ChunkPos(100, 0);
        data.setChunkBirthTime(existing, 999999L);

        assertEquals(cap, data.getChunkBirthTimes().size(),
                "Updating an existing entry should not change map size");
        assertEquals(999999L, data.getChunkBirthTime(existing),
                "Existing entry should be updated");
    }

    @Test
    void evictionKeepsMostRecentEntries() {
        BlockSpoilageData data = new BlockSpoilageData();

        int cap = 65536;
        for (int i = 0; i < cap; i++) {
            ChunkPos pos = new ChunkPos(i, 0);
            data.setChunkBirthTime(pos, 1000L + i);
        }

        // Add a new entry with a LARGE birth time (most recent chunk)
        ChunkPos newest = new ChunkPos(cap, 0);
        data.setChunkBirthTime(newest, 1000L + cap);

        // The newest entry should be present (it has the largest birth time, won't be evicted)
        assertTrue(data.hasChunkBirthTime(newest),
                "Newest chunk should be present");

        // The oldest entry (birth time 1000) should be gone
        assertFalse(data.hasChunkBirthTime(new ChunkPos(0, 0)),
                "Oldest chunk should be evicted");
    }

    @Test
    void chunkBirthTimesSurviveSaveLoad() {
        // The cap must not break the save/load round-trip
        BlockSpoilageData data = new BlockSpoilageData();
        for (int i = 0; i < 100; i++) {
            ChunkPos pos = new ChunkPos(i, 0);
            data.setChunkBirthTime(pos, 1000L + i);
        }

        CompoundTag nbt = data.toCompound();
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        assertEquals(100, loaded.getChunkBirthTimes().size(),
                "Chunk birth times should survive save/load");
        assertTrue(loaded.hasChunkBirthTime(new ChunkPos(50, 0)));
        assertEquals(1050L, loaded.getChunkBirthTime(new ChunkPos(50, 0)));
    }
}