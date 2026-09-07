package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shape of vanilla cave vines, which is the reason two defects existed.
 *
 * <p>The behaviour itself cannot be tested here — deciding ripeness reads the loot table, which
 * needs a {@code ServerLevel}. What this class pins is the premise the fix rests on, so that a
 * future version changing the block's shape fails here rather than silently reviving the bugs:
 * the vine counts its LENGTH in {@code age} and carries its fruit in a separate {@code berries}
 * boolean, and the body segments have no counter at all.</p>
 *
 * <p>Both defects came from reading that counter as ripeness:</p>
 *
 * <ul>
 *   <li>a vine carrying berries below age 25 answered "not ripe", so it was never tracked and
 *       its glow berries came out with no freshness, while a bare vine at age 25 answered
 *       "ripe" and was tracked as though it were carrying fruit;</li>
 *   <li>breaking a vine shorter than half its maximum length counted as digging up an unripe
 *       crop, so the berries arrived ROTTEN.</li>
 * </ul>
 */
class CaveVineRipenessTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BooleanProperty booleanProperty(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof BooleanProperty bp && name.equals(bp.getName())) {
                return bp;
            }
        }
        return null;
    }

    @Test
    void theVineHeadCountsLengthNotFruit() {
        BlockState head = Blocks.CAVE_VINES.defaultBlockState();

        IntegerProperty age = FoodSpoilageUtil.growthProperty(head);
        assertNotNull(age, "cave_vines does expose an age - that is exactly the trap");
        assertEquals("age", age.getName());
        int max = age.getPossibleValues().stream().max(Integer::compare).orElseThrow();
        assertEquals(25, max,
                "age 0..25 is how far the vine has grown downward, not how ripe it is");

        assertNotNull(booleanProperty(head, "berries"),
                "the fruit lives in a separate boolean, which is what ripeness must be read from");
    }

    @Test
    void theVineBodyHasNoCounterAtAll() {
        BlockState body = Blocks.CAVE_VINES_PLANT.defaultBlockState();

        assertNull(FoodSpoilageUtil.growthProperty(body),
                "cave_vines_plant has no age, so a growth-stage rule answers 'never growing' - "
                        + "which reads as 'always ripe', and every segment in a lush cave counted "
                        + "as carrying berries");
        assertNotNull(booleanProperty(body, "berries"));
    }

    @Test
    void aLongVineStillLooksBarelyGrownToTheStageRule() {
        IntegerProperty age = FoodSpoilageUtil.growthProperty(Blocks.CAVE_VINES.defaultBlockState());
        BlockState grown = Blocks.CAVE_VINES.defaultBlockState().setValue(age, 10);

        assertTrue(FoodSpoilageUtil.isBarelyGrown(grown),
                "a vine 10 segments long is 'barely grown' by the stage rule (10*2 < 25) - this "
                        + "is why breaking one handed the player rotten glow berries, and why the "
                        + "drop handler must not apply that rule to a flag-ripened plant");
    }

    @Test
    void theStageRuleStillDescribesRealCrops() {
        // The escape hatch above must not have loosened anything for plants that genuinely do
        // count stages: those still answer through the growth property exactly as before.
        IntegerProperty wheatAge = FoodSpoilageUtil.growthProperty(Blocks.WHEAT.defaultBlockState());
        assertNotNull(wheatAge);
        assertTrue(FoodSpoilageUtil.isBarelyGrown(
                        Blocks.WHEAT.defaultBlockState().setValue(wheatAge, 1)),
                "wheat at 1 of 7 is barely grown and digging it up must still give rotten produce");
        assertTrue(FoodSpoilageUtil.isImmatureCrop(
                        Blocks.WHEAT.defaultBlockState().setValue(wheatAge, 6)),
                "wheat at 6 of 7 is still growing");
    }
}
