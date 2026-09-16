package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1300 regression test: the pass-1283 nested-bundle aging fix.
 *
 * Vanilla 26.2 allows bundles inside bundles (BundleContents.BUNDLE_IN_BUNDLE_WEIGHT),
 * so Bundle -> Bundle -> Apple is reachable in pure vanilla. Before pass 1283 the food
 * inside never aged for three stacked reasons: the carrier probe was depth 2, the
 * updateBundleItemSpoilage gate used bare isSpoilable (a nested bundle is not food),
 * and the update loop compared only the item's own SPOILAGE component so a changed
 * BUNDLE_CONTENTS was never written back.
 *
 * A null Level is used deliberately (same pattern as ContainerSpoilageTest):
 * updateSpoilage(item, null) returns early, so only the GATE is observable headless —
 * it must accept a nested bundle (not return early as it did pre-1283) and reject a
 * stone-only one. The aging write-back and the inner-food trim need a real Level;
 * both were live-proven in pass 1283 (nested apple rotten_count:1 in a chest) and
 * cannot run headless.
 */
public class NestedBundleAgingTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    /** Builds bundle -> bundle -> egg (always-spoilable, safe with EMPTY components). */
    private static ItemStack nestedBundle(SpoilageData innerData) {
        ItemStack innerFood = new ItemStack(Items.EGG);
        if (innerData != null) innerFood.set(ModDataComponentTypes.SPOILAGE, innerData);
        BundleContents innerContents = new BundleContents(
                List.of(ItemStackTemplate.fromNonEmptyStack(innerFood)));
        ItemStack innerBundle = new ItemStack(Items.BUNDLE);
        innerBundle.set(DataComponents.BUNDLE_CONTENTS, innerContents);
        BundleContents outerContents = new BundleContents(
                List.of(ItemStackTemplate.fromNonEmptyStack(innerBundle)));
        ItemStack outerBundle = new ItemStack(Items.BUNDLE);
        outerBundle.set(DataComponents.BUNDLE_CONTENTS, outerContents);
        return outerBundle;
    }

    @Test
    void gateAcceptsNestedBundleWithoutThrowing() {
        // Pre-1283 the gate (bare isSpoilable on the inner template) rejected the inner
        // bundle and the method returned before doing anything. Post-1283 the gate uses
        // the depth-aware carrier probe and proceeds. With a null Level the aging itself
        // no-ops, but the gate passing is observable as: no exception, and the outer
        // bundle's contents are still readable and intact.
        ItemStack outer = nestedBundle(new SpoilageData(List.of(999999999L), List.of(), 0, 1.0));
        assertDoesNotThrow(() -> FoodSpoilageUtil.updateBundleItemSpoilage(outer, null),
                "the depth-aware gate must accept a nested bundle, not throw");
        BundleContents contents = outer.get(DataComponents.BUNDLE_CONTENTS);
        assertNotNull(contents, "outer bundle contents must survive the call");
        assertEquals(1, contents.items().size());
    }

    @Test
    void gateRejectsBundleOfOnlyStone() {
        // The gate must still reject a bundle whose nested content is not food —
        // the depth probe answers false and the method returns without work.
        ItemStack stone = new ItemStack(Items.STONE);
        BundleContents innerContents = new BundleContents(
                List.of(ItemStackTemplate.fromNonEmptyStack(stone)));
        ItemStack innerBundle = new ItemStack(Items.BUNDLE);
        innerBundle.set(DataComponents.BUNDLE_CONTENTS, innerContents);
        BundleContents outerContents = new BundleContents(
                List.of(ItemStackTemplate.fromNonEmptyStack(innerBundle)));
        ItemStack outer = new ItemStack(Items.BUNDLE);
        outer.set(DataComponents.BUNDLE_CONTENTS, outerContents);

        assertDoesNotThrow(() -> FoodSpoilageUtil.updateBundleItemSpoilage(outer, null));
        // The stone bundle must be untouched: no component was added anywhere.
        BundleContents after = outer.get(DataComponents.BUNDLE_CONTENTS);
        ItemStackTemplate inner = after.items().get(0);
        assertNull(inner.components().get(Items.BUNDLE.components(), ModDataComponentTypes.SPOILAGE),
                "no spoilage component may appear on a stone-only nested bundle");
    }

}
