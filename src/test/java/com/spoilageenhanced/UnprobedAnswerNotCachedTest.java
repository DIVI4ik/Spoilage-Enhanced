package com.spoilageenhanced;

import com.spoilageenhanced.util.DynamicFoodBlockCache;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pass 1177 (L1 — silent failure): an exception during a loot-table probe is the absence of an
 * answer, not a negative one. Both caches in {@link DynamicFoodBlockCache} used to store the
 * fallback answer anyway:
 *
 * <ul>
 *   <li>{@code getFoodDropIgnoringGrowth} cached {@code NO_FOOD_DROP} for a block whose probe
 *       threw — condemning it until the 2048-entry cap cleared the whole map, which for a pack
 *       with fewer queried blocks is forever.</li>
 *   <li>{@code deriveRipeness} fell through to the age-based path after a failed boolean-flag
 *       probe, deriving a WRONG rule ({@code Ripeness.always()} for a block with no counter),
 *       which {@code ripenessOf} then cached permanently.</li>
 * </ul>
 *
 * <p>Both are pinned here through the {@code probed} flag on the {@code Ripeness} record: an
 * unprobed answer must never enter a cache. The tests drive the record's own contract plus the
 * cache-skip branch, because a unit test cannot stand up the {@code ServerLevel} a real probe
 * needs — the same constraint {@link FruitingBlockRipenessTest} works under.</p>
 */
class UnprobedAnswerNotCachedTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The record must carry the probe outcome, or the cache-skip branch has nothing to read. */
    @Test
    void ripenessRecordCarriesAProbedFlag() throws Exception {
        Class<?> ripeness = Class.forName(
                "com.spoilageenhanced.util.DynamicFoodBlockCache$Ripeness");
        RecordComponent[] components = ripeness.getRecordComponents();
        boolean hasProbed = false;
        for (RecordComponent c : components) {
            if (c.getName().equals("probed") && c.getType() == boolean.class) {
                hasProbed = true;
            }
        }
        assertTrue(hasProbed,
                "Ripeness must record whether its loot-table probe actually ran; without the "
                        + "flag, ripenessOf cannot tell a rule from a failure and caches both");
    }

    /**
     * The heart of the fix: {@code ripenessOf} must return an unprobed answer WITHOUT caching
     * it, so the next call retries the probe instead of replaying the failure.
     *
     * <p>Drives the real method through reflection with a null world and a synthetic state
     * carrying a boolean property (like MegaCookery's {@code apple_age} but boolean). The
     * boolean-flag probe runs {@code lootFoodDrop}, which calls {@code state.getDrops(builder)}
     * — this throws NPE at {@code level.getServer()} in {@code LootContext.Builder.create}
     * because the world is null. That NPE is caught and the method returns
     * {@code Ripeness.unprobed()}. The test verifies the answer is marked unprobed AND that
     * {@code ripenessOf} does not cache it.</p>
     */
    @Test
    void anUnprobedRipenessIsNotCached() throws Exception {
        DynamicFoodBlockCache.clear();

        // Build a synthetic state with a boolean property (like a fruiting plant's "has_fruit")
        // so the boolean-flag probe actually runs and throws on the null world.
        net.minecraft.world.level.block.state.StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> def =
                new net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState>(Blocks.OAK_LEAVES)
                        .add(net.minecraft.world.level.block.state.properties.BooleanProperty.create("has_fruit"))
                        .create(Block::defaultBlockState, net.minecraft.world.level.block.state.BlockState::new);
        net.minecraft.world.level.block.state.properties.BooleanProperty hasFruit =
                (net.minecraft.world.level.block.state.properties.BooleanProperty) def.getProperty("has_fruit");
        net.minecraft.world.level.block.state.BlockState state = def.any().setValue(hasFruit, true);

        Method ripenessOf = DynamicFoodBlockCache.class.getDeclaredMethod(
                "ripenessOf",
                net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.server.level.ServerLevel.class,
                net.minecraft.core.BlockPos.class);
        ripenessOf.setAccessible(true);

        Object answer = ripenessOf.invoke(null,
                state, null, new net.minecraft.core.BlockPos(0, 64, 0));

        Field probedField = answer.getClass().getDeclaredField("probed");
        probedField.setAccessible(true);
        assertFalse(probedField.getBoolean(answer),
                "a probe that threw must come back marked unprobed");

        Field ripenessCacheField = DynamicFoodBlockCache.class.getDeclaredField("RIPENESS");
        ripenessCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Block, Object> ripenessCache = (Map<Block, Object>) ripenessCacheField.get(null);
        // The synthetic state's owner is OAK_LEAVES, so the cache key is OAK_LEAVES
        assertFalse(ripenessCache.containsKey(Blocks.OAK_LEAVES),
                "an unprobed answer is the probe failing, not a ripeness rule - caching it "
                        + "freezes the wrong rule for this block until the cache clears");
    }

    /**
     * The CACHE (block -> drop id) side of the same defect. {@code getFoodDropIgnoringGrowth}
     * must not store {@code NO_FOOD_DROP} when the loot probe threw. The probe path needs a
     * ServerLevel, so this drives the branch the fix actually changed: a block with no growth
     * property and no CropBlock parent caches the negative ONLY when the probe completed.
     *
     * <p>Verified by reading the code path is not enough on its own — so this also asserts the
     * guard exists at all, by checking that a probe exception leaves the block uncached. The
     * method is driven with a null world: the config and self-item lookups answer null for
     * stone, and the loot probe throws before any caching decision, so the pre-fix code would
     * have cached NO_FOOD_DROP and the post-fix code must not.</p>
     */
    @Test
    void aThrownProbeLeavesTheDropCacheUncached() throws Exception {
        DynamicFoodBlockCache.clear();

        Method ignoring = DynamicFoodBlockCache.class.getDeclaredMethod(
                "getFoodDropIgnoringGrowth",
                net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.server.level.ServerLevel.class,
                net.minecraft.core.BlockPos.class);
        ignoring.setAccessible(true);

        // Stone: not excluded, no configured drop, self-item not spoilable (stone is not food),
        // no growth property, not a CropBlock. The loot probe throws on the null world.
        Object result = ignoring.invoke(null,
                Blocks.STONE.defaultBlockState(), null, new net.minecraft.core.BlockPos(0, 64, 0));

        assertTrue(result == null, "a failed probe must answer null, not invent a drop");

        Field cacheField = DynamicFoodBlockCache.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Block, String> cache = (Map<Block, String>) cacheField.get(null);
        assertFalse(cache.containsKey(Blocks.STONE),
                "NO_FOOD_DROP must not be cached for a block whose probe threw - the block "
                        + "would be condemned until the 2048 cap cleared the whole map");
    }
}
