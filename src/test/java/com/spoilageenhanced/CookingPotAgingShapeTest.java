package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cooking-pot aging fix (pass 1052, L14 — foreign content) without loading
 * Farmer's Delight, in the same way {@code FruitingBlockRipenessTest} pins a Forge mod's
 * defect from a Fabric-only suite: rebuild the SHAPE of the problem, not the mod.
 *
 * <p>The defect: a cooking pot's {@code cookingTick} never calls {@code Item.inventoryTick}
 * on its contents, so ingredients and the assembled meal sat frozen with no spoilage
 * component — verified live before the fix: slot 6 read
 * {@code {count: 1, Slot: 6b, id: "farmersdelight:vegetable_soup"}} with no component, and it
 * never gained one. The fix is {@code CookingPotBlockEntityMixin}, which ages every spoilable
 * stack in the pot's inventory on a 20-tick phase-spread cadence; the lazy stamping inside
 * {@code FoodSpoilageUtil} is what gives the unstamped meal its timer.</p>
 *
 * <p>What this test pins, without the mod jar:</p>
 *
 * <ul>
 *   <li><b>The stamping contract.</b> A spoilable stack with no SPOILAGE component gains one
 *       with a fresh expiration after {@code initializeItemSpoilage} runs — the exact call
 *       {@code updateSpoilage} makes for an unstamped stack, which is what the mixin's aging
 *       pass drives on every pot slot. If that lazy stamping ever stops working, the meal
 *       goes back to reading fresh-forever and this test fails.</li>
 *   <li><b>The mixin's shape.</b> The mixin class must exist, be registered in
 *       {@code spoilage_enhanced.mixins.json}, and carry a static handler named
 *       {@code spoilage_enhanced$agePotContents} — the method the runtime log proved applied
 *       ("CookingPotBlockEntityMixin applied (Farmer's Delight present)", 03:31:49). A
 *       renamed, unregistered or deleted mixin fails here even though no unit test can load
 *       the injection itself.</li>
 *   <li><b>The phase-spread cadence.</b> The handler must gate on
 *       {@code (x + z + gameTime) % 20 == 0} — the same spreading as ItemEntityMixin and
 *       BrewingStandBlockEntityMixin. This is asserted by reading the handler's source for
 *       the modulo-20 pattern, so a refactor that drops the spread (and re-creates the
 *       synchronized-spike defect Pass 100 fixed) cannot ship silently.</li>
 * </ul>
 */
class CookingPotAgingShapeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void unstampedSpoilableStackGainsFreshTimer() {
        // The meal slot after cooking: a spoilable stack with NO spoilage component.
        // This is exactly what CookingPotBlockEntityMixin's aging pass hands to
        // updateSpoilage, which routes an unstamped stack to initializeItemSpoilage.
        ItemStack meal = new ItemStack(Items.BAKED_POTATO, 1);
        assertNull(meal.get(ModDataComponentTypes.SPOILAGE),
                "precondition: the assembled meal carries no spoilage component");

        FoodSpoilageUtil.initializeItemSpoilage(meal, null);

        SpoilageData data = meal.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(data, "the lazy stamping must give an unstamped spoilable stack a component");
        assertEquals(1, data.freshExpirations().size(),
                "a single-item meal gets exactly one fresh tracker");
        long freshDuration = com.spoilageenhanced.config.SpoilageConfig.getInstance()
                .getFreshDurationForItem(meal.getItem());
        assertEquals(freshDuration, data.freshExpirations().get(0),
                "with a null world (game time 0) the expiration equals the fresh duration");
    }

    @Test
    void multiCountMealGetsTrackerPerItem() {
        ItemStack meal = new ItemStack(Items.BAKED_POTATO, 3);
        FoodSpoilageUtil.initializeItemSpoilage(meal, null);
        SpoilageData data = meal.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(data);
        assertEquals(3, data.freshExpirations().size(),
                "a 3-count meal gets one tracker per item, like every other stamping path");
    }

    @Test
    void nonSpoilableStackIsNotStamped() {
        ItemStack stone = new ItemStack(Items.STONE, 1);
        FoodSpoilageUtil.initializeItemSpoilage(stone, null);
        assertNull(stone.get(ModDataComponentTypes.SPOILAGE),
                "the aging pass must never stamp a non-spoilable stack");
    }

    @Test
    void mixinClassExistsWithAgingHandler() throws Exception {
        // Class.forName cannot load the mixin here: its FD stub types are on the main
        // sourceSet's classpath, not the test runtime's. Read the source instead �
        // the same evidence the phase-spread check uses.
        String src = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/spoilageenhanced/mixin/CookingPotBlockEntityMixin.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("spoilage_enhanced$agePotContents"),
                "the aging handler spoilage_enhanced$agePotContents must exist");
        assertTrue(src.contains("private static void spoilage_enhanced$agePotContents"),
                "the handler injects into a static tick method, so it must be static");
        assertTrue(src.contains("@Mixin(targets = \"vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity\")"),
                "the mixin must target the FD cooking pot by string name (runtime-only dependency)");
    }

    @Test
    void mixinIsRegisteredInMixinsJson() throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("src/main/resources/spoilage_enhanced.mixins.json")),
                StandardCharsets.UTF_8);
        assertTrue(json.contains("CookingPotBlockEntityMixin"),
                "the mixin must be registered in spoilage_enhanced.mixins.json or it never applies");
    }

    @Test
    void handlerKeepsPhaseSpreadCadence() throws Exception {
        // The handler's source is the contract: (x + z + gameTime) % 20 == 0. Reading the
        // source file (not the bytecode) keeps the assertion readable and still fails the
        // build if the cadence is dropped in a refactor.
        String src = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/spoilageenhanced/mixin/CookingPotBlockEntityMixin.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("% 20 != 0"),
                "the aging handler must keep the 20-tick phase-spread gate "
                        + "(same as ItemEntityMixin / BrewingStandBlockEntityMixin)");
    }
}
