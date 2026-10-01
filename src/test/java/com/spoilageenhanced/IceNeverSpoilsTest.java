package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.AutoFoodDetector;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test: Ice blocks and items must NEVER receive spoilage data, freshness timers,
 * or be tracked as food/storage.
 */
public class IceNeverSpoilsTest {

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
        SpoilageConfig.getInstance().clearCache();
    }

    @Test
    void iceItemsAreNeverSpoilable() {
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ICE), "ice must be never-spoilable");
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.PACKED_ICE), "packed ice must be never-spoilable");
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.BLUE_ICE), "blue ice must be never-spoilable");
    }

    @Test
    void iceItemsAreNotValidFoodCandidates() {
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.ICE), "ice must not be a food candidate");
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.PACKED_ICE), "packed ice must not be a food candidate");
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.BLUE_ICE), "blue ice must not be a food candidate");
    }

    @Test
    void iceItemsAreNotSpoilableInConfig() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertFalse(config.isSpoilable(Items.ICE), "ice must not be spoilable in config");
        assertFalse(config.isSpoilable(Items.PACKED_ICE), "packed ice must not be spoilable in config");
        assertFalse(config.isSpoilable(Items.BLUE_ICE), "blue ice must not be spoilable in config");
    }

    @Test
    void iceItemsAreExcluded() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertTrue(config.isExcluded(Items.ICE), "ice must be excluded");
        assertTrue(config.isExcluded(Items.PACKED_ICE), "packed ice must be excluded");
        assertTrue(config.isExcluded(Items.BLUE_ICE), "blue ice must be excluded");
        assertTrue(config.getExcludedSet().contains("minecraft:ice"));
        assertTrue(config.getExcludedSet().contains("minecraft:packed_ice"));
        assertTrue(config.getExcludedSet().contains("minecraft:blue_ice"));
    }

    @Test
    void iceBlocksAreExcludedFromTracking() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertTrue(config.getExcludedBlockSet().contains("minecraft:ice"));
        assertTrue(config.getExcludedBlockSet().contains("minecraft:packed_ice"));
        assertTrue(config.getExcludedBlockSet().contains("minecraft:blue_ice"));
        assertNull(config.getTrackedBlockDropItem("minecraft:ice"), "ice block must not have tracked drop");
        assertNull(config.getTrackedBlockDropItem("minecraft:packed_ice"), "packed ice block must not have tracked drop");
        assertNull(config.getTrackedBlockDropItem("minecraft:blue_ice"), "blue ice block must not have tracked drop");
    }

    @Test
    void initializeItemSpoilageDoesNotAttachFreshnessToIce() {
        ItemStack iceStack = new ItemStack(Items.ICE);
        FoodSpoilageUtil.initializeItemSpoilage(iceStack, null);
        assertFalse(iceStack.has(ModDataComponentTypes.SPOILAGE), "initializeItemSpoilage must not set SPOILAGE on ice");

        ItemStack packedIceStack = new ItemStack(Items.PACKED_ICE);
        FoodSpoilageUtil.initializeItemSpoilage(packedIceStack, null);
        assertFalse(packedIceStack.has(ModDataComponentTypes.SPOILAGE), "initializeItemSpoilage must not set SPOILAGE on packed ice");

        ItemStack blueIceStack = new ItemStack(Items.BLUE_ICE);
        FoodSpoilageUtil.initializeItemSpoilage(blueIceStack, null);
        assertFalse(blueIceStack.has(ModDataComponentTypes.SPOILAGE), "initializeItemSpoilage must not set SPOILAGE on blue ice");
    }

    @Test
    void dynamicFoodBlockCacheReturnsNullForIce() {
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.ICE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.PACKED_ICE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.BLUE_ICE.defaultBlockState(), null, null));
    }

    @Test
    void registerDynamicStorageItemRefusesIce() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.registerDynamicStorageItem("minecraft:packed_ice", "minecraft:ice");
        assertFalse(config.isSpoilable(Items.PACKED_ICE), "packed ice must not become spoilable through storage registration");
        assertFalse(config.getAdditionalSet().contains("minecraft:packed_ice"));
    }
}
