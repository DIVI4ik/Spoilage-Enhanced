package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player report: a potato has freshness the moment it is planted, and should only start ageing
 * once it has finished growing.
 *
 * <p>Freshness used to start at planting, so the clock ran through the entire growth period and
 * the crop could be stale before it was ever harvestable. Maturity is detected by the block's
 * {@code age} property rather than by its class, so modded crops are covered too — these tests
 * pin that shape-based detection, since a switch to instanceof checks would quietly work for
 * vanilla and fail for every mod in a pack.</p>
 */
class CropMaturityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wheatIsImmatureUntilItsFinalStage() {
        IntegerProperty age = CropBlock.AGE;
        int max = age.getPossibleValues().stream().max(Integer::compare).orElseThrow();

        for (int i = 0; i < max; i++) {
            BlockState growing = Blocks.WHEAT.defaultBlockState().setValue(age, i);
            assertTrue(FoodSpoilageUtil.isImmatureCrop(growing),
                    "wheat at age " + i + " of " + max + " is still growing - starting its "
                            + "freshness here is what let a crop rot in the ground before harvest");
        }

        BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(age, max);
        assertFalse(FoodSpoilageUtil.isImmatureCrop(ripe),
                "wheat at its final stage is done growing and freshness must start");
    }

    @Test
    void detectsGrowthByPropertyNotByBlockClass() {
        BlockState ripeWheat = Blocks.WHEAT.defaultBlockState()
                .setValue(CropBlock.AGE, CropBlock.AGE.getPossibleValues().stream().max(Integer::compare).orElseThrow());
        assertNotNull(FoodSpoilageUtil.growthProperty(ripeWheat),
                "a growing block must be recognised through its age property, which is what "
                        + "makes modded crops work without naming them");
        assertEquals("age", FoodSpoilageUtil.growthProperty(ripeWheat).getName());
    }

    @Test
    void aBlockThatDoesNotGrowIsNeverImmature() {
        assertNull(FoodSpoilageUtil.growthProperty(Blocks.STONE.defaultBlockState()));
        assertFalse(FoodSpoilageUtil.isImmatureCrop(Blocks.STONE.defaultBlockState()),
                "a block with no growth stage is finished by definition");
        assertFalse(FoodSpoilageUtil.isImmatureCrop(Blocks.PUMPKIN.defaultBlockState()),
                "a placed pumpkin is not a growing crop and must age from the moment it exists");
    }

    @Test
    void otherGrowablesAreCoveredToo() {
        for (var block : new net.minecraft.world.level.block.Block[]{
                Blocks.POTATOES, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.NETHER_WART,
                Blocks.SWEET_BERRY_BUSH, Blocks.COCOA}) {
            BlockState fresh = block.defaultBlockState();
            assertNotNull(FoodSpoilageUtil.growthProperty(fresh),
                    block + " should expose a growth stage");
            assertTrue(FoodSpoilageUtil.isImmatureCrop(fresh),
                    block + " at its default (just planted) stage must count as still growing");
        }
    }
}
