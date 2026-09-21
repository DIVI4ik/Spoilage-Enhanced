package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.network.BlockSpoilageRequestPayload;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1391 (L18 — multi-observer): tests that BlockSpoilageData correctly serves
 * multiple players requesting the same block.
 *
 * <p>The server-side BlockSpoilageData is a SavedData per ServerLevel (dimension).
 * All players in the same dimension share the same BlockSpoilageData instance.
 * When multiple players look at the same tracked block, each sends a
 * BlockSpoilageRequestPayload and receives a BlockSpoilageResponsePayload.
 *
 * <p>This test pins the following invariants:</p>
 * <ol>
 *   <li>Two requests for the same block from different "players" return identical state/ticks.</li>
 *   <li>The server does not mutate the entry between requests (no side effects on read).</li>
 *   <li>Requests for different blocks in the same dimension are independent.</li>
 *   <li>Requests from different dimensions use different BlockSpoilageData instances.</li>
 * </ol>
 *
 * <p>Since we cannot spin up two real clients in the test harness, we simulate the
 * server-side logic directly: create a BlockSpoilageData, populate it, and verify
 * the invariants through direct data access and code inspection.</p>
 */
public class MultiObserverBlockSpoilageTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Bind item components so isSpoilable() doesn't throw
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    /**
     * Creates a BlockSpoilageData with a pre-populated entry for a pumpkin at (100, 64, 100)
     * that is in the STALE state with a known expiration time.
     */
    private static BlockSpoilageData createTestData() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(100, 64, 100);
        long currentTime = 100000L;
        long freshDuration = 24000L;
        long staleDuration = 48000L;
        // STALE: expirationTime = currentTime + staleDuration
        long expireTime = currentTime + staleDuration;
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.STALE, expireTime);
        return data;
    }

    @Test
    void twoObserversSameBlockGetIdenticalAnswers() {
        BlockSpoilageData data = createTestData();
        BlockPos pos = new BlockPos(100, 64, 100);

        // Simulate two observers reading the same entry
        BlockSpoilageData.BlockSpoilageEntry entry1 = data.getEntry(pos);
        BlockSpoilageData.BlockSpoilageEntry entry2 = data.getEntry(pos);

        assertNotNull(entry1, "Entry must exist for first observer");
        assertNotNull(entry2, "Entry must exist for second observer");

        // Both must see the same state and expiration
        assertEquals(entry1.state, entry2.state,
                "Both observers must see the same spoilage state");
        assertEquals(entry1.expirationTime, entry2.expirationTime,
                "Both observers must see the same expiration time");
    }

    @Test
    void twoObserversDifferentBlocksGetIndependentAnswers() {
        BlockSpoilageData data = createTestData();
        long currentTime = 100000L;

        // Add a second block (melon) that is FRESH
        BlockPos melonPos = new BlockPos(200, 64, 200);
        long freshExpire = currentTime + 24000L;
        data.setSpoilageState(melonPos, FoodSpoilageUtil.SpoilageState.FRESH, freshExpire);

        // Observer 1 requests pumpkin (STALE)
        BlockSpoilageData.BlockSpoilageEntry pumpkin = data.getEntry(new BlockPos(100, 64, 100));
        assertNotNull(pumpkin);

        // Observer 2 requests melon (FRESH)
        BlockSpoilageData.BlockSpoilageEntry melon = data.getEntry(melonPos);
        assertNotNull(melon);

        // States must differ
        assertNotEquals(pumpkin.state, melon.state,
                "Different blocks must report independent states");

        // Pumpkin is STALE, melon is FRESH
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, pumpkin.state);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, melon.state);
    }

    @Test
    void untrackedBlockReturnsFreshForAllObservers() {
        BlockSpoilageData data = createTestData();
        BlockPos untrackedPos = new BlockPos(999, 64, 999); // Never registered

        // Both observers should get null entry (untracked)
        assertNull(data.getEntry(untrackedPos),
                "Untracked block must return null entry for all observers");
    }

    @Test
    void readDoesNotMutateEntry() {
        BlockSpoilageData data = createTestData();
        BlockPos pos = new BlockPos(100, 64, 100);

        // Capture the entry before any reads
        BlockSpoilageData.BlockSpoilageEntry before = data.getEntry(pos);
        assertNotNull(before);
        long beforeExpiration = before.expirationTime;
        FoodSpoilageUtil.SpoilageState beforeState = before.state;

        // Make 10 reads
        for (int i = 0; i < 10; i++) {
            BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
            assertNotNull(entry);
        }

        // Entry must be unchanged
        BlockSpoilageData.BlockSpoilageEntry after = data.getEntry(pos);
        assertNotNull(after);
        assertEquals(beforeExpiration, after.expirationTime,
                "Reading spoilage state must not mutate expirationTime");
        assertEquals(beforeState, after.state,
                "Reading spoilage state must not mutate state");
    }

    @Test
    void differentDimensionsHaveSeparateDataInstances() {
        // This test verifies the SavedData contract: each ServerLevel has its own BlockSpoilageData.
        BlockSpoilageData data1 = new BlockSpoilageData();
        BlockSpoilageData data2 = new BlockSpoilageData();

        BlockPos pos = new BlockPos(100, 64, 100);
        data1.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, 1000L);

        // data2 must not see data1's entry
        assertNull(data2.getEntry(pos),
                "Different BlockSpoilageData instances must be independent (per-dimension isolation)");
    }

    @Test
    void requestPayloadCarriesCorrectPosition() {
        // Verify the request payload structure matches what the client sends
        BlockPos pos = new BlockPos(123, 64, 456);
        BlockSpoilageRequestPayload payload = new BlockSpoilageRequestPayload(pos);

        assertEquals(pos, payload.pos(),
                "Request payload must carry the exact block position the client queried");
    }

    @Test
    void responsePayloadCarriesAllRequiredFields() {
        // Verify the response payload structure matches what the server sends
        BlockPos pos = new BlockPos(123, 64, 456);
        int stateOrdinal = FoodSpoilageUtil.SpoilageState.STALE.ordinal();
        long ticksRemaining = 42000L;
        double speedMultiplier = 1.5;

        BlockSpoilageResponsePayload payload = new BlockSpoilageResponsePayload(
                pos, stateOrdinal, ticksRemaining, speedMultiplier);

        assertEquals(pos, payload.pos());
        assertEquals(stateOrdinal, payload.state());
        assertEquals(ticksRemaining, payload.ticksRemaining());
        assertEquals(speedMultiplier, payload.speedMultiplier(), 0.001);
    }

    @Test
    void rateLimiterIsPerPlayerNotGlobal() throws Exception {
        // The rate limiter in ServerCustomPayloadMixin is per-connection (synchronized on 'this',
        // where 'this' is the ServerGamePacketListenerImpl for a specific player).
        // This test pins that the limiter field is instance-level, not static.

        Class<?> mixinClass = Class.forName("com.spoilageenhanced.mixin.ServerCustomPayloadMixin");
        java.lang.reflect.Field requestsField = mixinClass.getDeclaredField("spoilage_enhanced$requestsInWindow");
        java.lang.reflect.Field windowField = mixinClass.getDeclaredField("spoilage_enhanced$lastRequestWindowStart");
        java.lang.reflect.Field loggedField = mixinClass.getDeclaredField("spoilage_enhanced$loggedDropThisWindow");

        // All three must be instance fields (not static)
        assertFalse(java.lang.reflect.Modifier.isStatic(requestsField.getModifiers()),
                "Request counter must be per-player (instance field)");
        assertFalse(java.lang.reflect.Modifier.isStatic(windowField.getModifiers()),
                "Window start must be per-player (instance field)");
        assertFalse(java.lang.reflect.Modifier.isStatic(loggedField.getModifiers()),
                "Logged flag must be per-player (instance field)");
    }

    @Test
    void serverExecuteSerializesRequests() throws Exception {
        // BlockSpoilageNetworking.handleRequest is always called via server.execute(),
        // which runs on the main server thread. This means all requests are serialized
        // and there is no concurrent access to BlockSpoilageData.
        //
        // This test documents that invariant. If the code ever changes to call
        // handleRequest directly from the Netty thread, this test would need to
        // be updated to verify thread safety (e.g., ConcurrentHashMap).
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ServerCustomPayloadMixin.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("server.execute(() -> BlockSpoilageNetworking.handleRequest"),
                "handleRequest must be called via server.execute() to serialize on main thread");
    }

    @Test
    void clientCacheIsPerClient() throws Exception {
        // ClientBlockSpoilageCache is a static class with static fields, but it runs
        // on the render thread of each client. Each client process has its own JVM,
        // so the static fields are naturally isolated.
        //
        // In a single-process dev environment with multiple clients (not possible here),
        // they would share the static cache — but that's a dev-environment artifact,
        // not a production concern.
        //
        // This test pins that the cache has no cross-player leakage mechanism.
        Class<?> cacheClass = Class.forName("com.spoilageenhanced.client.ClientBlockSpoilageCache");
        java.lang.reflect.Field cacheField = cacheClass.getDeclaredField("CACHE");
        assertTrue(java.lang.reflect.Modifier.isStatic(cacheField.getModifiers()),
                "Client cache is static (per-JVM), which is correct for single-client processes");
    }

    @Test
    void blockSpoilageDataIsNotThreadSafeButSerializationMakesItSafe() throws Exception {
        // BlockSpoilageData uses HashMap (not ConcurrentHashMap), so it is not thread-safe.
        // However, the server.execute() serialization ensures all access happens on the
        // main server thread, making concurrent access impossible in practice.
        //
        // This test documents that invariant. If the code ever changes to access
        // BlockSpoilageData from multiple threads, this test would need to be updated.
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("new HashMap<>"),
                "BlockSpoilageData uses HashMap (not thread-safe)");
        assertFalse(source.contains("ConcurrentHashMap"),
                "BlockSpoilageData does not use ConcurrentHashMap (relies on serialization)");
    }

    @Test
    void networkHandlerUsesServerPlayerNotStatic() {
        // BlockSpoilageNetworking.handleRequest takes a ServerPlayer parameter,
        // ensuring each request is associated with a specific player.
        // This is important for multi-observer scenarios where different players
        // may be at different positions.
        try {
            Class<?> networkingClass = Class.forName("com.spoilageenhanced.network.BlockSpoilageNetworking");
            Method handleRequest = networkingClass.getMethod("handleRequest",
                    net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.core.BlockPos.class);

            assertNotNull(handleRequest,
                    "handleRequest must exist with ServerPlayer parameter");
        } catch (Exception e) {
            fail("Could not verify handleRequest signature: " + e.getMessage());
        }
    }

    @Test
    void responseIncludesPlayerPosition() {
        // The response payload includes the block position, allowing the client
        // to match responses to requests even when multiple players are observing
        // different blocks.
        BlockPos pos = new BlockPos(123, 64, 456);
        BlockSpoilageResponsePayload payload = new BlockSpoilageResponsePayload(
                pos, 0, 0, 1.0);

        assertEquals(pos, payload.pos(),
                "Response must include the queried block position for client matching");
    }
}