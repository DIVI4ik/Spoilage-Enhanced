package com.spoilageenhanced;

import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player report: freshness works on a vanilla sweet berry bush but does nothing on the apple
 * tree from MegaCookery.
 *
 * <p>Two independent causes, both of which this class pins:</p>
 *
 * <ol>
 *   <li>The tree counts its ripeness in a property called {@code apple_age}, not {@code age}.
 *       Growth detection matched the exact name only, so the tree looked like a block that does
 *       not grow — and a block that does not grow is ripe by definition, so a bare branch was
 *       treated the same as one carrying an apple.</li>
 *   <li>The apple is not in the block's loot table at any stage. Breaking the leaves yields a
 *       sapling or sticks; the apple exists only in the right-click handler. Every discovery
 *       path asked the loot table, so nothing ever identified the tree as a food source, and
 *       the apple it handed over got no freshness at all.</li>
 * </ol>
 */
class FruitingBlockRipenessTest {

    /** Exactly MegaCookery's shape: 0 bare, 1 flowering, 2 fruiting. */
    private static final IntegerProperty APPLE_AGE = IntegerProperty.create("apple_age", 0, 2);
    private static final IntegerProperty PLAIN_AGE = IntegerProperty.create("age", 0, 7);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Builds states carrying arbitrary properties without registering a block.
     *
     * <p>A {@code new Block(...)} outside registration fails — the block registry refuses to
     * create intrusive holders once bootstrap is done — so the state definition is built
     * directly with an existing block as its nominal owner. Everything under test here reads
     * only the state's properties, so the owner never matters.</p>
     */
    private static StateDefinition<Block, BlockState> definition(IntegerProperty... properties) {
        return new StateDefinition.Builder<Block, BlockState>(Blocks.OAK_LEAVES)
                .add(properties)
                .create(Block::defaultBlockState, BlockState::new);
    }

    @Test
    void aQualifiedAgePropertyCountsAsGrowth() {
        BlockState bare = definition(APPLE_AGE).any().setValue(APPLE_AGE, 0);
        IntegerProperty found = FoodSpoilageUtil.growthProperty(bare);
        assertNotNull(found,
                "apple_age is a growth stage. Matching only the exact name 'age' made this block "
                        + "answer 'does not grow', which counts as ripe at every stage - a branch "
                        + "with no apple on it was treated like one carrying fruit");
        assertEquals("apple_age", found.getName());
    }

    @Test
    void aBareBranchIsNotRipeAndAFruitingOneIs() {
        StateDefinition<Block, BlockState> leaves = definition(APPLE_AGE);
        assertTrue(FoodSpoilageUtil.isImmatureCrop(leaves.any().setValue(APPLE_AGE, 0)),
                "apple_age 0 is a bare branch - there is no apple on it to go off");
        assertTrue(FoodSpoilageUtil.isImmatureCrop(leaves.any().setValue(APPLE_AGE, 1)),
                "apple_age 1 is flowering - still no apple");
        assertFalse(FoodSpoilageUtil.isImmatureCrop(leaves.any().setValue(APPLE_AGE, 2)),
                "apple_age 2 carries the apple, so freshness starts here");
    }

    @Test
    void exactAgeWinsWhenABlockHasBoth() {
        BlockState both = definition(APPLE_AGE, PLAIN_AGE).any();
        IntegerProperty found = FoodSpoilageUtil.growthProperty(both);
        assertNotNull(found);
        assertEquals("age", found.getName(),
                "a plain 'age' is the block's own growth stage and must keep precedence, so "
                        + "widening the match cannot change what vanilla crops answer");
    }

    @Test
    void vanillaIsUnaffected() {
        assertEquals("age", FoodSpoilageUtil.growthProperty(
                Blocks.WHEAT.defaultBlockState()).getName());
        assertEquals("age", FoodSpoilageUtil.growthProperty(
                Blocks.SWEET_BERRY_BUSH.defaultBlockState()).getName());
        assertNull(FoodSpoilageUtil.growthProperty(Blocks.STONE.defaultBlockState()),
                "stone has no age of any kind and must stay a non-crop");
    }

    /**
     * The guard that stops the learning path from mistaking a store of food for a source of it.
     *
     * <p>Taking a cooked porkchop back off a campfire is an item leaving a block during an
     * interaction — the same shape as picking an apple. The difference is that a container hands
     * back what someone put into it, and a fruiting plant does not have a block entity at all.
     * Checked before the world is touched, so a null level here is deliberate: reaching for the
     * world would mean the guard ran too late.</p>
     */
    @Test
    void aBlockEntityIsNeverLearnedAsAFoodSource() {
        BlockPos pos = new BlockPos(0, 64, 0);
        assertFalse(DynamicFoodBlockCache.learnFoodDropFromInteraction(
                        Blocks.CHEST.defaultBlockState(), pos, pos, null, "minecraft:apple"),
                "a chest gives back what was put in it and must never be recorded as growing it");
        assertFalse(DynamicFoodBlockCache.learnFoodDropFromInteraction(
                        Blocks.CAMPFIRE.defaultBlockState(), pos, pos, null, "minecraft:cooked_porkchop"),
                "food coming off a campfire was placed there by a player, not produced by it");
    }

    @Test
    void anItemLandingSomewhereElseIsNotEvidence() {
        assertFalse(DynamicFoodBlockCache.learnFoodDropFromInteraction(
                        Blocks.STONE.defaultBlockState(), new BlockPos(0, 64, 0),
                        new BlockPos(9, 64, 9), null, "minecraft:apple"),
                "popResource spawns at the block being used - an item appearing elsewhere is a "
                        + "different event that merely overlaps in time");
    }
}
