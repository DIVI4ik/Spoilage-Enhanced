package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1199 (L13 observed): a bundle inside a shulker box was never aged because
 * the food-carrying probe was one level deep. This test pins the depth-2 probe:
 * shulker -> bundle -> apple must be flagged.
 */
public class NestedCarrierProbeTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void bundleInShulkerIsFlaggedByProbe() {
        ItemStack apple = new ItemStack(Items.APPLE);
        ItemStackTemplate appleTemplate = ItemStackTemplate.fromNonEmptyStack(apple);

        BundleContents bundleContents = new BundleContents(List.of(appleTemplate));
        DataComponentPatch bundlePatch = DataComponentPatch.builder()
                .set(DataComponents.BUNDLE_CONTENTS, bundleContents)
                .build();
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, bundleContents);

        ItemContainerContents shulkerContents = ItemContainerContents.fromItems(List.of(bundle));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, shulkerContents);

        assertTrue(FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(shulker),
                "shulker holding a bundle containing an apple must be flagged by the probe");
    }

    @Test
    void plainShulkerWithStoneIsNotFlagged() {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemContainerContents shulkerContents = ItemContainerContents.fromItems(List.of(stone));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, shulkerContents);

        assertFalse(FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(shulker),
                "shulker holding only stone must not be flagged");
    }

    @Test
    void shulkerInShulkerIsFlaggedByProbe() {
        ItemStack apple = new ItemStack(Items.APPLE);
        ItemStackTemplate appleTemplate = ItemStackTemplate.fromNonEmptyStack(apple);
        ItemContainerContents innerContents = ItemContainerContents.fromItems(List.of(apple));
        ItemStack innerShulker = new ItemStack(Items.SHULKER_BOX);
        innerShulker.set(DataComponents.CONTAINER, innerContents);

        ItemStack outerShulker = new ItemStack(Items.SHULKER_BOX);
        outerShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(innerShulker)));

        assertTrue(FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(outerShulker),
                "shulker holding a shulker containing an apple must be flagged by the probe");
    }
}
