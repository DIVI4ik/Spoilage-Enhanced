package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.AutoFoodDetector;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal protection test: Any non-food block or material (from vanilla or ANY mod)
 * must NEVER be falsely identified as food, food candidate, or food storage.
 */
public class UniversalNonFoodBlockTest {

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
    void nonFoodMaterialsAreIdentifiedUniversally() {
        // Minerals, metals, ores
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:iron_ingot"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:gold_nugget"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_iron"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_copper"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_gold"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:diamond"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:amethyst_shard"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:iron_block"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:gold_block"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:copper_block"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("mod:tin_ingot"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("mod:silver_ore"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("mod:copper_dust"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("mod:ruby_gem"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("mod:lead_crystal"));

        // Construction / Building blocks
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:stone_slab"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:oak_stairs"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:cobblestone_wall"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:oak_fence"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:oak_door"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:glass_pane"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:bricks"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:oak_planks"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:iron_bars"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:chain"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:lantern"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:torch"));

        // Geology & terrain
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:stone"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:cobblestone"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:deepslate"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:granite"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:sand"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:gravel"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:dirt"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:clay"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:terracotta"));

        // Mechanisms & Redstone
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:piston"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:dispenser"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:hopper"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:rail"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:comparator"));

        // Ice and snow
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:ice"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:packed_ice"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:blue_ice"));
        assertTrue(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:snow_block"));
    }

    @Test
    void realFoodsAreNotClassifiedAsNonFood() {
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:apple"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:bread"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:cooked_beef"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:carrot"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:potato"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:sweet_berries"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:cake"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:pumpkin_pie"));

        // Raw meats must NOT be caught by raw_ ore checks
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_beef"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_porkchop"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_chicken"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_mutton"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_salmon"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("minecraft:raw_cod"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:raw_venison"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:raw_bacon"));

        // Food nuggets (chicken nugget, etc.) must NOT be caught by mineral nugget check
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:chicken_nugget"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:fish_nugget"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:tofu_nugget"));

        // Cold desserts must NOT be caught by ice check
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:ice_cream"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:iced_tea"));
        assertFalse(AutoFoodDetector.isKnownNonFoodMaterial("mod:ice_pop"));
    }

    @Test
    void nonFoodItemsAreNotValidFoodCandidates() {
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.STONE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.COBBLESTONE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.IRON_INGOT));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.GOLD_INGOT));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.OAK_PLANKS));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.GLASS));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.REDSTONE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.PISTON));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.ICE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.PACKED_ICE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.BLUE_ICE));

        // Real food items are valid food candidates
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.APPLE));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.BREAD));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.CARROT));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.BEEF));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.COOKED_BEEF));
    }

    @Test
    void nonFoodCompressionIsRefusedAsFoodStorage() {
        // 9 iron ingots -> 1 iron block (NOT food storage)
        assertFalse(AutoFoodDetector.isPlausibleFoodStorage(Items.IRON_BLOCK, Items.IRON_INGOT));

        // 9 packed ice -> 1 blue ice (NOT food storage)
        assertFalse(AutoFoodDetector.isPlausibleFoodStorage(Items.BLUE_ICE, Items.PACKED_ICE));

        // 9 ice -> 1 packed ice (NOT food storage)
        assertFalse(AutoFoodDetector.isPlausibleFoodStorage(Items.PACKED_ICE, Items.ICE));

        // 4 stone -> 1 stone bricks (NOT food storage)
        assertFalse(AutoFoodDetector.isPlausibleFoodStorage(Items.STONE_BRICKS, Items.STONE));

        // 4 clay ball -> 1 clay block (clay ball is not food, clay is geology)
        assertFalse(AutoFoodDetector.isPlausibleFoodStorage(Items.CLAY, Items.CLAY_BALL));
    }

    @Test
    void genuineFoodCompressionIsAcceptedAsFoodStorage() {
        // 9 wheat -> 1 hay block (food crop -> food storage block)
        assertTrue(AutoFoodDetector.isPlausibleFoodStorage(Items.HAY_BLOCK, Items.WHEAT));

        // 1 egg in 1 bowl (container item)
        assertTrue(AutoFoodDetector.isPlausibleFoodStorage(Items.BOWL, Items.EGG));
    }

    @Test
    void dynamicFoodBlockCacheRejectsNonFoodBlocks() {
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.STONE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.COBBLESTONE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.IRON_BLOCK.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.GLASS.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.ICE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.PACKED_ICE.defaultBlockState(), null, null));
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(Blocks.BLUE_ICE.defaultBlockState(), null, null));
    }

    @Test
    void registerDynamicStorageItemRefusesNonFoodStorage() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.registerDynamicStorageItem("minecraft:iron_block", "minecraft:iron_ingot");
        assertFalse(config.isSpoilable(Items.IRON_BLOCK));
        assertFalse(config.getAdditionalSet().contains("minecraft:iron_block"));

        config.registerDynamicStorageItem("minecraft:stone_bricks", "minecraft:stone");
        assertFalse(config.isSpoilable(Items.STONE_BRICKS));
        assertFalse(config.getAdditionalSet().contains("minecraft:stone_bricks"));
    }
}
