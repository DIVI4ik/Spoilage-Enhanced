package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 224 regression test: FoodSpoilageUtil.isEatenInPlace.
 *
 * <p>PLAYER_REPORT §3: blocks eaten in place (cake, and any modded equivalent) bypass
 * the ItemStackMixin rotten-effects path. A rotten item placed as one of these blocks
 * would give clean food when eaten. The fix refuses placement of a rotten block that
 * is eaten in place.</p>
 *
 * <p>The shared property is: the block's item form is NOT edible as a food (no FOOD
 * component), but the block IS spoilable. Cake is the canonical example.</p>
 */
public class IsEatenInPlaceTest {

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
    void nullBlockReturnsFalse() {
        assertFalse(FoodSpoilageUtil.isEatenInPlace(null), "null block must return false");
    }

    @Test
    void airBlockReturnsFalse() {
        assertFalse(FoodSpoilageUtil.isEatenInPlace(Blocks.AIR), "AIR must return false");
    }

    @Test
    void cakeBlockReturnsTrue() {
        // Cake is the canonical example: spoilable as an item, eaten in place as a block.
        assertTrue(FoodSpoilageUtil.isEatenInPlace(Blocks.CAKE),
                "Cake must be detected as eaten-in-place");
    }

    @Test
    void pumpkinBlockReturnsFalse() {
        // Pumpkin is spoilable but eaten as an item (pumpkin pie), not in place.
        assertFalse(FoodSpoilageUtil.isEatenInPlace(Blocks.PUMPKIN),
                "Pumpkin must NOT be detected as eaten-in-place (it's eaten as an item)");
    }

    @Test
    void stoneBlockReturnsFalse() {
        // Stone is not spoilable at all.
        assertFalse(FoodSpoilageUtil.isEatenInPlace(Blocks.STONE),
                "Stone must NOT be detected as eaten-in-place (not spoilable)");
    }
}
