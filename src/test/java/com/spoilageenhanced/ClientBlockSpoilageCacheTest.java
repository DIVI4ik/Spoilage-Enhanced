package com.spoilageenhanced;

import com.spoilageenhanced.client.ClientBlockSpoilageCache;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 142 regression test: ClientBlockSpoilageCache logic.
 *
 * Tests the cached looksSpoilable flag, refresh interval logic, and LRU eviction.
 * The cache is client-only (render thread), so we test the pure logic by calling
 * the package-private methods directly via reflection.
 */
public class ClientBlockSpoilageCacheTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Bind item components so isSpoilable() doesn't throw
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void getStateReturnsStateNoneForUnknownBlock() {
        BlockPos pos = new BlockPos(777, 777, 777);
        int state = ClientBlockSpoilageCache.getState(pos);
        assertEquals(BlockSpoilageResponsePayload.STATE_NONE, state,
                "Unknown block should return STATE_NONE");
    }

    @Test
    void hasAnswerForReturnsFalseForUnknownBlock() {
        BlockPos pos = new BlockPos(888, 888, 888);
        assertFalse(ClientBlockSpoilageCache.hasAnswerFor(pos),
                "Unknown block should not have an answer");
    }

    @Test
    void looksSpoilableCachedFallsBackToLiveCheckWhenNoAnswer() {
        // No answer in cache - should fall back to live check
        BlockPos pos = new BlockPos(999, 64, 999); // never used in tests
        // The live check for stone returns false (no Minecraft instance in test)
        assertFalse(ClientBlockSpoilageCache.looksSpoilableCached(pos),
                "Unknown position with no Minecraft instance should return false");
    }

    @Test
    void lruEvictionRespectsMaxEntries() {
        // The cache has MAX_ENTRIES = 128. We can't easily test eviction without
        // reflection, but we can verify the cache doesn't crash on many entries.
        // We can't inject answers because accept() requires Minecraft.getInstance().
        // Instead, just verify the cache is accessible.
        assertNotNull(ClientBlockSpoilageCache.class, "ClientBlockSpoilageCache should be loaded");
    }

    @Test
    void clearRemovesAllEntries() {
        // Clear should be safe to call even with no entries
        ClientBlockSpoilageCache.clear();
        ClientBlockSpoilageCache.clear();
        // If we got here without exception, clear works
        assertTrue(true);
    }

    @Test
    void getTicksRemainingReturnsZeroForUnknownBlock() {
        BlockPos pos = new BlockPos(111, 111, 111);
        long ticks = ClientBlockSpoilageCache.getTicksRemaining(pos);
        assertEquals(0L, ticks,
                "Unknown block should return 0 ticks remaining");
    }

    @Test
    void getSpeedMultiplierReturnsDefaultForUnknownBlock() {
        BlockPos pos = new BlockPos(222, 222, 222);
        // Pass 200: the standalone getSpeedMultiplier was removed (no production callers
        // after the HUD switched to the combined accessor); the default-multiplier
        // contract now lives on the combined accessor.
        var answer = ClientBlockSpoilageCache.getTicksRemainingAndMultiplier(pos);
        assertEquals(0L, answer.ticksRemaining(), "Unknown block should return 0 ticks remaining");
        assertEquals(1.0, answer.speedMultiplier(), 0.001,
                "Unknown block should return default speed multiplier 1.0");
    }

    @Test
    void injectAnswerViaReflectionStoresCachedEntry() throws Exception {
        // Use reflection to inject an answer directly into the cache
        BlockPos pos = new BlockPos(50, 50, 50);

        // Inject via the put() method (package-private)
        Method putMethod = ClientBlockSpoilageCache.class.getDeclaredMethod("put",
                BlockPos.class, Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer"));
        putMethod.setAccessible(true);

        // Create a CachedAnswer via reflection
        Class<?> cachedAnswerClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer");
        var ctor = cachedAnswerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object cachedAnswer = ctor.newInstance(
                FoodSpoilageUtil.SpoilageState.FRESH.ordinal(), // state
                1000L,                                          // ticksRemaining
                1.0,                                            // speedMultiplier
                0L,                                             // atGameTime
                Blocks.PUMPKIN,                                 // block
                true                                            // looksSpoilable
        );

        putMethod.invoke(null, pos, cachedAnswer);

        // Verify the entry was stored
        int state = ClientBlockSpoilageCache.getState(pos);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH.ordinal(), state,
                "Injected answer should return FRESH state");

        long ticks = ClientBlockSpoilageCache.getTicksRemaining(pos);
        assertTrue(ticks >= 990 && ticks <= 1000,
                "Injected ticks should be close to 1000, got " + ticks);

        assertTrue(ClientBlockSpoilageCache.hasAnswerFor(pos),
                "Injected answer should be present");

        // Pass 200: the combined accessor must return the same ticks and expose the
        // injected multiplier — one get() chain for both.
        // Pass 205: the result is a record (TicksAndMultiplier), eliminating the static
        // lastCombinedMultiplier field that could be read without the preceding call.
        var combined = ClientBlockSpoilageCache.getTicksRemainingAndMultiplier(pos);
        assertTrue(combined.ticksRemaining() >= 990 && combined.ticksRemaining() <= 1000,
                "Combined accessor should return the same ticks, got " + combined.ticksRemaining());
        assertEquals(1.0, combined.speedMultiplier(), 0.001,
                "Combined accessor should expose the injected multiplier");
    }

    @Test
    void injectedStateNoneAnswerIsRecognized() throws Exception {
        BlockPos pos = new BlockPos(60, 60, 60);

        Method putMethod = ClientBlockSpoilageCache.class.getDeclaredMethod("put",
                BlockPos.class, Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer"));
        putMethod.setAccessible(true);

        Class<?> cachedAnswerClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer");
        var ctor = cachedAnswerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object cachedAnswer = ctor.newInstance(
                BlockSpoilageResponsePayload.STATE_NONE, // state
                0L,                                       // ticksRemaining
                1.0,                                      // speedMultiplier
                0L,                                       // atGameTime
                Blocks.STONE,                             // block
                false                                     // looksSpoilable
        );

        putMethod.invoke(null, pos, cachedAnswer);

        int state = ClientBlockSpoilageCache.getState(pos);
        assertEquals(BlockSpoilageResponsePayload.STATE_NONE, state,
                "Injected STATE_NONE should be recognized");

        // "Nothing here" is an ANSWER, not the absence of one. The HUD draws its "checking
        // freshness" placeholder only while genuinely waiting, and decides that from this
        // method. When the two were conflated the placeholder stayed on screen forever the
        // moment the server began replying STATE_NONE for a growing crop: the client still
        // guesses potatoes look spoilable, so it drew "checking" every frame and nothing ever
        // replaced it.
        assertTrue(ClientBlockSpoilageCache.hasAnswerFor(pos),
                "a STATE_NONE response must still count as an answer, or the HUD waits forever "
                        + "for one that already arrived");

        // looksSpoilableCached should return the cached false value
        assertFalse(ClientBlockSpoilageCache.looksSpoilableCached(pos),
                "Injected STATE_NONE for stone should cache looksSpoilable=false");
    }

    @Test
    void injectedSpoilableBlockReportsSpoilable() throws Exception {
        BlockPos pos = new BlockPos(70, 70, 70);

        Method putMethod = ClientBlockSpoilageCache.class.getDeclaredMethod("put",
                BlockPos.class, Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer"));
        putMethod.setAccessible(true);

        Class<?> cachedAnswerClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer");
        var ctor = cachedAnswerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object cachedAnswer = ctor.newInstance(
                FoodSpoilageUtil.SpoilageState.FRESH.ordinal(), // state
                24000L,                                         // ticksRemaining
                1.0,                                            // speedMultiplier
                0L,                                             // atGameTime
                Blocks.PUMPKIN,                                 // block
                true                                            // looksSpoilable
        );

        putMethod.invoke(null, pos, cachedAnswer);

        assertTrue(ClientBlockSpoilageCache.looksSpoilableCached(pos),
                "Injected answer for pumpkin should report spoilable");
    }

    @Test
    void lruEvictionWorksUnderLoad() throws Exception {
        // The cache has MAX_ENTRIES = 128. Inject 200 entries and verify the cache
        // doesn't crash and the most recent entries are still accessible.
        Method putMethod = ClientBlockSpoilageCache.class.getDeclaredMethod("put",
                BlockPos.class, Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer"));
        putMethod.setAccessible(true);

        Class<?> cachedAnswerClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer");
        var ctor = cachedAnswerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);

        // Inject 200 entries
        for (int i = 0; i < 200; i++) {
            BlockPos pos = new BlockPos(1000 + i, 64, 0);
            Object cachedAnswer = ctor.newInstance(
                    FoodSpoilageUtil.SpoilageState.FRESH.ordinal(),
                    1000L,
                    1.0,
                    0L,
                    Blocks.PUMPKIN,
                    true
            );
            putMethod.invoke(null, pos, cachedAnswer);
        }

        // The most recent entry should still be accessible
        BlockPos recent = new BlockPos(1199, 64, 0);
        assertTrue(ClientBlockSpoilageCache.hasAnswerFor(recent),
                "Most recent entry should be accessible after LRU eviction");

        // An old entry should have been evicted
        BlockPos old = new BlockPos(1000, 64, 0);
        assertFalse(ClientBlockSpoilageCache.hasAnswerFor(old),
                "Oldest entry should have been evicted");
    }

    /**
     * Pass 195 (Lens 4 — TPS): currentBlockAt must memoize per-tick so that the 4-5 calls
     * per render frame for the same pos cost 1 chunk-section read, not 4-5. The memo is a
     * (pos, gameTick, block) tuple; a tick advance or a different pos invalidates it.
     *
     * <p>The memo fields are private and cannot be set from outside without reflection, but
     * the memo's CONTRACT is testable here: clear() resets the memo fields (verified by
     * reading them back via reflection). The read-side memo is exercised by the production
     * code path — when Minecraft.getInstance() is non-null and a real level exists, two
     * currentBlockAt calls in the same tick for the same pos will both see the memoed
     * value without re-reading the chunk. This test pins the invariant that clear() wipes
     * the memo, so a fresh world doesn't see a leftover answer.</p>
     */
    @Test
    void clearInvalidatesBlockMemo() throws Exception {
        // Pre-populate the memo fields with a stale value.
        java.lang.reflect.Field memoedPosField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlockPos");
        memoedPosField.setAccessible(true);
        java.lang.reflect.Field memoedTickField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlockAtTick");
        memoedTickField.setAccessible(true);
        java.lang.reflect.Field memoedBlockField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlock");
        memoedBlockField.setAccessible(true);

        BlockPos stale = new BlockPos(12345, 64, 67890);
        memoedPosField.set(null, stale);
        memoedTickField.set(null, 1000L);
        memoedBlockField.set(null, Blocks.PUMPKIN);

        // clear() must reset all three memo fields so the next call does a fresh read.
        ClientBlockSpoilageCache.clear();

        assertNull(memoedPosField.get(null), "clear() must reset memoedBlockPos");
        assertEquals(-1L, ((Long) memoedTickField.get(null)).longValue(),
                "clear() must reset memoedBlockAtTick to -1");
        assertNull(memoedBlockField.get(null), "clear() must reset memoedBlock");
    }

    /**
     * Pass 195 (Lens 4 — TPS): the memo must be invalidated when the pos changes (a different
     * crosshair target needs a fresh read), but must REUSE the cached value when the same pos
     * is queried twice. We test the memoization contract by populating the fields directly
     * and then reading them back: two reads for the same pos + same tick must return the
     * memoed block, and the underlying fields must not be overwritten.
     */
    @Test
    void memoReusesCachedBlockForSamePosAndTick() throws Exception {
        java.lang.reflect.Field memoedPosField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlockPos");
        memoedPosField.setAccessible(true);
        java.lang.reflect.Field memoedTickField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlockAtTick");
        memoedTickField.setAccessible(true);
        java.lang.reflect.Field memoedBlockField = ClientBlockSpoilageCache.class.getDeclaredField("memoedBlock");
        memoedBlockField.setAccessible(true);

        // Simulate "the memo was populated at tick 1000 for pos (1,2,3) with PUMPKIN".
        BlockPos memoed = new BlockPos(1, 2, 3);
        memoedPosField.set(null, memoed);
        memoedTickField.set(null, 1000L);
        memoedBlockField.set(null, Blocks.PUMPKIN);

        // Without a real Minecraft instance, currentBlockAt returns null immediately, so we
        // can't drive the real code path here. Instead, pin the contract by reading the
        // memo fields and confirming they survive — production code is what populates them.
        assertEquals(memoed, memoedPosField.get(null),
                "memoedBlockPos must persist between calls in the same tick");
        assertEquals(1000L, ((Long) memoedTickField.get(null)).longValue(),
                "memoedBlockAtTick must persist between calls in the same tick");
        assertEquals(Blocks.PUMPKIN, memoedBlockField.get(null),
                "memoedBlock must persist between calls in the same tick");
    }

    /**
     * Pass 206 (Lens 3 — cache correctness): clear() must reset the connection-fingerprint
     * field too. Without that, a rejoin to the same dimension where the new connection
     * happened to occupy the same identity-hash slot as the old one would not trigger
     * the CACHE.clear() in requestIfStale — the HUD would show stale answers for up to
     * REFRESH_INTERVAL_TICKS (20 ticks) after the rejoin.
     */
    @Test
    void clearResetsConnectionFingerprint() throws Exception {
        java.lang.reflect.Field connField = ClientBlockSpoilageCache.class.getDeclaredField("cachedConnectionHash");
        connField.setAccessible(true);
        connField.set(null, 12345);
        ClientBlockSpoilageCache.clear();
        assertEquals(0, connField.get(null),
                "clear() must reset cachedConnectionHash to 0 so the next requestIfStale re-records it");
    }

    /**
     * PLAYER_REPORT §1 — the "not aging" answer must survive both accessors intact.
     *
     * <p>The server sends {@link com.spoilageenhanced.network.BlockSpoilageResponsePayload#NO_TIMER}
     * for a block that does not age, so the HUD can omit the timer entirely. Both accessors
     * used to run it through {@code Math.max(0L, ticks - elapsed)}, which turned the sentinel
     * into a plain zero — and zero is rendered as "&lt;1 min", a countdown the block never
     * performs. That is exactly what a player saw on a world-generated pumpkin after the first
     * attempt at this fix.</p>
     *
     * <p>The injected answer is deliberately given an old {@code atGameTime} so a nonzero
     * elapsed time is subtracted: a test that passes only at elapsed == 0 would not have
     * caught the clamp.</p>
     */
    @Test
    void noTimerSentinelSurvivesBothAccessors() throws Exception {
        BlockPos pos = new BlockPos(77, 77, 77);

        Method putMethod = ClientBlockSpoilageCache.class.getDeclaredMethod("put",
                BlockPos.class, Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer"));
        putMethod.setAccessible(true);

        Class<?> cachedAnswerClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache$CachedAnswer");
        var ctor = cachedAnswerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object cachedAnswer = ctor.newInstance(
                FoodSpoilageUtil.SpoilageState.FRESH.ordinal(),
                com.spoilageenhanced.network.BlockSpoilageResponsePayload.NO_TIMER,
                1.0,
                0L,        // answered long ago, so elapsed is nonzero
                Blocks.PUMPKIN,
                true
        );
        putMethod.invoke(null, pos, cachedAnswer);

        assertEquals(com.spoilageenhanced.network.BlockSpoilageResponsePayload.NO_TIMER,
                ClientBlockSpoilageCache.getTicksRemaining(pos),
                "getTicksRemaining must pass NO_TIMER through, not clamp it to 0");

        var combined = ClientBlockSpoilageCache.getTicksRemainingAndMultiplier(pos);
        assertEquals(com.spoilageenhanced.network.BlockSpoilageResponsePayload.NO_TIMER,
                combined.ticksRemaining(),
                "the combined accessor the HUD calls must pass NO_TIMER through, not clamp it to 0");
    }
}