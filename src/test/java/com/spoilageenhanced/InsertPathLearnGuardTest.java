package com.spoilageenhanced;

import com.spoilageenhanced.util.DynamicFoodBlockCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The insert-path guard on learning a block -> drop mapping.
 *
 * <p>InteractionInsertStackMixin fires for every spoilable item that lands in a player
 * inventory while a block interaction is in flight. Vanilla reaches that path with a
 * honey_bottle when a glass bottle is used on a full beehive (BeehiveBlock.java:175). The
 * mixin used to call registerFoodDrop unconditionally, which wrote
 * beehive -> honey_bottle into the cache — a beehive is not a food source, the honey
 * inside it is. After that, every beehive resolved to honey_bottle: the HUD showed a
 * freshness countdown on beehives, looking at one lazily registered it, and once that
 * clock ran out the next bottle of honey came out STALE or ROTTEN.</p>
 *
 * <p>The mixin now asks getFoodDropIgnoringGrowth first and only registers when that
 * answers null — the same guard learnFoodDropFromInteraction (the popResource sibling)
 * has carried since it was written. This test pins the guard's building block: a block
 * whose loot table yields no spoilable drop (beehive drops itself, which is not
 * spoilable) must answer null, so the guard holds and no mapping is learned for it.</p>
 */
public class InsertPathLearnGuardTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @AfterEach
    void clearCache() {
        DynamicFoodBlockCache.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<Block, String> cache() throws Exception {
        Field cacheField = DynamicFoodBlockCache.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        return (java.util.Map<Block, String>) cacheField.get(null);
    }

    @Test
    void beehiveIsNotAFoodSourceSoNoMappingIsLearned() throws Exception {
        DynamicFoodBlockCache.clear();
        BlockState beehive = Blocks.BEEHIVE.defaultBlockState();

        // What the guard asks: does this block yield food at any growth stage? The beehive's
        // loot table drops minecraft:beehive, which is not spoilable, and the block has no
        // tracked_blocks entry — so the answer must be null.
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(beehive, null, BlockPos.ZERO),
                "a beehive must not resolve to a food drop, or the insert-path guard cannot hold");

        // The guarded call the mixin now makes: nothing is learned for the beehive. The cache
        // may hold the NO_FOOD_DROP sentinel for it (that is the lookup's own memoisation),
        // but never a food mapping.
        String cached = cache().get(Blocks.BEEHIVE);
        assertTrue(cached == null || cached.equals("__NO_FOOD__"),
                "no beehive -> honey_bottle mapping may be cached from a honey harvest, got: " + cached);
    }

    @Test
    void aGenuineHandPickedBlockStillLearns() throws Exception {
        DynamicFoodBlockCache.clear();
        // A block with no loot-table answer and no config entry is the gap the learning
        // exists to fill. Vanilla has no such block (every vanilla food block is in a loot
        // table or tracked_blocks), so pin the mechanism with a block that is definitely
        // not food: the guard must answer null for it, and registering through the guarded
        // path must still work when the gap is real.
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(stone, null, BlockPos.ZERO));

        // The guarded registration itself:
        if (DynamicFoodBlockCache.getFoodDropIgnoringGrowth(stone, null, BlockPos.ZERO) == null) {
            DynamicFoodBlockCache.registerFoodDrop(stone, "minecraft:apple");
        }
        assertEquals("minecraft:apple", cache().get(Blocks.STONE),
                "when the guard's question answers null, the mapping must still be learnable");
    }
}
