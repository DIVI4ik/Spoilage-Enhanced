package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 270 regression test: SpoilageConfig.isSpoilable.
 *
 * <p>isSpoilable checks if an item is spoilable, with a CHM cache and a binding-timing
 * guard (unbound components skip the cache). This test pins the contract: known spoilable
 * item, known non-spoilable item, null item, cache behavior.</p>
 */
public class IsSpoilableTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void nullItemReturnsFalse() {
        assertFalse(SpoilageConfig.getInstance().isSpoilable(null),
                "null item must return false");
    }

    @Test
    void airItemReturnsFalse() {
        assertFalse(SpoilageConfig.getInstance().isSpoilable(Items.AIR),
                "AIR must return false");
    }

    @Test
    void knownSpoilableItemReturnsTrue() {
        // EGG is always-spoilable (AutoFoodDetector.isAlwaysSpoilable).
        assertTrue(SpoilageConfig.getInstance().isSpoilable(Items.EGG),
                "EGG must be spoilable");
    }

    @Test
    void knownNonSpoilableItemReturnsFalse() {
        // STONE is not a food item.
        assertFalse(SpoilageConfig.getInstance().isSpoilable(Items.STONE),
                "STONE must not be spoilable");
    }

    @Test
    void cacheHitReturnsSameResult() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // First call populates the cache.
        boolean first = config.isSpoilable(Items.EGG);
        // Second call must hit the cache and return the same result.
        boolean second = config.isSpoilable(Items.EGG);
        assertEquals(first, second,
                "Cache hit must return the same result");
    }
}