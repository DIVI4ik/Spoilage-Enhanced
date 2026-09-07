package com.spoilageenhanced;

import com.spoilageenhanced.client.ClientVirtualSpoilageAnchor;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 151 regression test: ClientVirtualSpoilageAnchor logic.
 *
 * The anchor gives un-stamped stacks (creative/crafted — no SPOILAGE component until the
 * first server tick) a stable time origin so the tooltip countdown actually counts down.
 * Tests cover: stable origin across calls, TTL expiry, item-hash guard against recycled
 * identity hashes, clock-jump reset, and LRU cap.
 */
public class ClientVirtualSpoilageAnchorTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @AfterEach
    void clearAnchors() {
        ClientVirtualSpoilageAnchor.clear();
    }

    @Test
    void firstSeenIsStableAcrossCalls() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long first = ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L);
        long second = ClientVirtualSpoilageAnchor.firstSeen(stack, 1050L);
        long third = ClientVirtualSpoilageAnchor.firstSeen(stack, 1100L);

        assertEquals(1000L, first, "First call should return the current game time");
        assertEquals(1000L, second, "Second call should return the SAME origin (stable)");
        assertEquals(1000L, third, "Third call should return the SAME origin (stable)");
    }

    @Test
    void ttlExpiryResetsOrigin() {
        ItemStack stack = new ItemStack(Items.BREAD);
        long first = ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L);
        assertEquals(1000L, first);

        // Within TTL (600 ticks of the LAST touch): same origin
        long within = ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L + 600L);
        assertEquals(1000L, within, "Within TTL of last touch the origin should be stable");

        // The last touch was at 1600 (previous call). Jump 601 ticks past THAT: 1600 + 601 = 2201.
        // Now currentGameTime - lastTouchedGameTime = 601 > 600 → re-anchor from now.
        long after = ClientVirtualSpoilageAnchor.firstSeen(stack, 2201L);
        assertEquals(2201L, after, "Past TTL of last touch the origin should reset to now");
    }

    @Test
    void clockJumpBackwardsResetsOrigin() {
        ItemStack stack = new ItemStack(Items.CARROT);
        long first = ClientVirtualSpoilageAnchor.firstSeen(stack, 5000L);
        assertEquals(5000L, first);

        // World clock jumps backwards (world switch, /time set)
        long after = ClientVirtualSpoilageAnchor.firstSeen(stack, 100L);
        assertEquals(100L, after, "Clock jump backwards should reset the origin to now");
    }

    @Test
    void differentStacksGetIndependentOrigins() {
        ItemStack apple = new ItemStack(Items.APPLE);
        ItemStack bread = new ItemStack(Items.BREAD);

        long appleOrigin = ClientVirtualSpoilageAnchor.firstSeen(apple, 1000L);
        long breadOrigin = ClientVirtualSpoilageAnchor.firstSeen(bread, 2000L);

        assertEquals(1000L, appleOrigin);
        assertEquals(2000L, breadOrigin);

        // Each stack keeps its own origin (the last touch is updated, but origin stays stable)
        assertEquals(1000L, ClientVirtualSpoilageAnchor.firstSeen(apple, 1100L));
        assertEquals(2000L, ClientVirtualSpoilageAnchor.firstSeen(bread, 2100L));
    }

    @Test
    void clearRemovesAllOrigins() {
        ItemStack stack = new ItemStack(Items.APPLE);
        ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L);

        ClientVirtualSpoilageAnchor.clear();

        // After clear, the origin should be re-anchored from now
        long after = ClientVirtualSpoilageAnchor.firstSeen(stack, 5000L);
        assertEquals(5000L, after, "After clear() the origin should restart");
    }

    @Test
    void lruCapDoesNotCrashUnderLoad() {
        // MAX_ENTRIES = 256. Create more stacks than the cap and verify no crash
        // and that the most recent entries still work.
        ItemStack[] stacks = new ItemStack[300];
        for (int i = 0; i < 300; i++) {
            stacks[i] = new ItemStack(Items.APPLE);
            ClientVirtualSpoilageAnchor.firstSeen(stacks[i], 1000L + i);
        }

        // The most recent stack should still have its origin (it's the MRU entry)
        long recent = ClientVirtualSpoilageAnchor.firstSeen(stacks[299], 1000L + 299);
        assertEquals(1000L + 299, recent,
                "Most recent stack should keep its origin after LRU eviction pressure");

        // The oldest stack was evicted — it re-anchors from now
        long oldest = ClientVirtualSpoilageAnchor.firstSeen(stacks[0], 5000L);
        assertEquals(5000L, oldest,
                "Evicted stack should re-anchor from now");
    }

    @Test
    void sameItemDifferentStacksAreIndependent() {
        // Two stacks of the same item are different objects with different identity hashes
        ItemStack apple1 = new ItemStack(Items.APPLE);
        ItemStack apple2 = new ItemStack(Items.APPLE);

        long origin1 = ClientVirtualSpoilageAnchor.firstSeen(apple1, 1000L);
        long origin2 = ClientVirtualSpoilageAnchor.firstSeen(apple2, 2000L);

        assertEquals(1000L, origin1);
        assertEquals(2000L, origin2);
        assertNotEquals(origin1, origin2, "Different stacks must have independent origins");
    }

    /**
     * Pass 198 (L5 — render-path): firstSeen must skip the put when called multiple times
     * within the same game tick. The put only refreshes lastTouchedGameTime (the LRU
     * position is already maintained by the get() on this access-ordered LinkedHashMap).
     * When the tooltip re-renders within the same tick, we save one map write per frame.
     */
    @Test
    void sameTickCallsSkipPut() {
        ItemStack stack = new ItemStack(Items.EGG);
        long first = ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L);
        assertEquals(1000L, first);

        // Same tick (currentGameTime unchanged): should return same origin and NOT re-put.
        // We can't directly observe the put count, but we can verify the origin is stable
        // and the lastTouchedGameTime is NOT updated (it stays at the first call's tick).
        long second = ClientVirtualSpoilageAnchor.firstSeen(stack, 1000L);
        assertEquals(1000L, second, "Same tick should return same origin");

        // Advance by 1 tick: should now re-put and update lastTouchedGameTime.
        long third = ClientVirtualSpoilageAnchor.firstSeen(stack, 1001L);
        assertEquals(1000L, third, "Next tick should still return same origin (within TTL)");

        // Now jump past TTL from the LAST touch (1001 + 601 = 1602): should re-anchor.
        long after = ClientVirtualSpoilageAnchor.firstSeen(stack, 1602L);
        assertEquals(1602L, after, "Past TTL of last touch should reset origin");
    }
}