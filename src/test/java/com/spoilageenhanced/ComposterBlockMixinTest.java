package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 134 regression test: the semantics ComposterBlockMixin.onAddToComposter depends on.
 *
 * <p>The mixin replaces the old {@code containsKey() + getFloat()} pair with a single
 * {@code getFloat()} call and guards on {@code defaultChance <= 0.0F}. That guard only works
 * because {@code ComposterBlock.COMPOSTABLES} is an {@code Object2FloatOpenHashMap} whose
 * {@code defaultReturnValue} is {@code -1.0F} (ComposterBlock.java:68) — so a missing key
 * returns -1.0F, not 0.0F as an earlier comment claimed. This test pins that contract and the
 * guard's behaviour for a non-compostable item.
 *
 * <p>The {@code addItem} method the mixin hooks is private, so it cannot be exercised from a
 * unit test; what can be tested is the map semantics the guard is built on, which is what
 * actually decides whether a non-compostable item passes the guard.
 */
public class ComposterBlockMixinTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingKeyReturnsNegativeOneNotZero() {
        // Items.STONE is not registered as compostable. The old comment claimed getFloat()
        // returns 0.0f for missing keys; vanilla sets defaultReturnValue(-1.0F).
        float stoneChance = ComposterBlock.COMPOSTABLES.getFloat(Items.STONE);
        assertEquals(-1.0F, stoneChance, 0.0F,
                "COMPOSTABLES.defaultReturnValue is -1.0F (ComposterBlock.java:68), so a missing key must return -1.0F");
    }

    @Test
    void registeredCompostableReturnsItsChance() {
        float breadChance = ComposterBlock.COMPOSTABLES.getFloat(Items.BREAD);
        assertTrue(breadChance > 0.0F,
                "a registered compostable item must return a positive chance, got " + breadChance);
    }

    @Test
    void guardRejectsNonCompostableAndAcceptsCompostable() {
        // Mirror the exact predicate in ComposterBlockMixin.onAddToComposter:
        //   float defaultChance = ComposterBlock.COMPOSTABLES.getFloat(item.getItem());
        //   if (defaultChance <= 0.0F) return;
        float stoneChance = ComposterBlock.COMPOSTABLES.getFloat(Items.STONE);
        float breadChance = ComposterBlock.COMPOSTABLES.getFloat(Items.BREAD);

        assertTrue(stoneChance <= 0.0F,
                "a non-compostable item (stone, -1.0F) must be rejected by the <= 0.0F guard");
        assertFalse(breadChance <= 0.0F,
                "a compostable item (bread, " + breadChance + "F) must pass the <= 0.0F guard");
    }

    @Test
    void allCompostableItemsHavePositiveChance() {
        // Regression: if a future vanilla version changed defaultReturnValue to 0.0F, the
        // guard would stop rejecting non-compostable items and every non-food item would be
        // fed to the spoilage logic. Verify the contract holds for the whole map.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;
            float chance = ComposterBlock.COMPOSTABLES.getFloat(item);
            if (chance == -1.0F) continue; // not registered — expected
            assertTrue(chance > 0.0F,
                    BuiltInRegistries.ITEM.getKey(item) + " is registered as compostable but has a non-positive chance: " + chance);
        }
    }
}