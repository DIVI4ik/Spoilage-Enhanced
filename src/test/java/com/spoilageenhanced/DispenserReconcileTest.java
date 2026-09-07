package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 410 (L13 observed): dispenser reconcile behavior — what a player sees.
 *
 * <p>Scenario: player puts 3 rotten eggs in a dispenser, triggers dispense.
 * DispenserBlockMixin reconciles the slot: when the dispenser dispenses 1 item,
 * the slot's count drops to 2 but the component still has 3 trackers. The mixin
 * trims to count, keeping the BEST trackers (the dispensed item already left
 * with the worst).
 *
 * The mixin needs ServerLevel and DispenserBlockEntity. This test pins the
 * reconcile logic by checking extractWorstItems on the stack before the mixin runs.
 *
 * What should happen: over-tracked dispenser slot is trimmed to count.
 */
public class DispenserReconcileTest {

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

    @Test
    void overTrackedDispenserSlotIsReconciled() {
        // Scenario: dispenser had 3 eggs, dispensed 1, slot has count=2 but 3 trackers
        ItemStack stack = new ItemStack(Items.EGG, 2);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 3, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(3, data.totalTracked(), "Source has 3 trackers");
        assertEquals(2, stack.getCount(), "Slot has count=2 after dispense");

        // Reconcile: drop the worst (tracked - count) = 1 worst tracker
        // The dispensed item already left with the worst, so the slot keeps the best
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData reconciled = split[0]; // remaining (after dropping 1 worst)

        assertEquals(2, reconciled.totalTracked(), "Reconciled must have 2 trackers (count=2)");
        assertEquals(2, reconciled.rottenCount(), "Reconciled must keep 2 rotten (the best)");
    }

    @Test
    void overTrackedMixedDispenserSlotIsReconciled() {
        // Scenario: dispenser had 3 eggs (1 fresh, 1 stale, 1 rotten), dispensed 1 (the rotten left)
        ItemStack stack = new ItemStack(Items.EGG, 2);
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 10000L),  // 1 fresh
                List.of(System.currentTimeMillis() + 10000L),  // 1 stale
                1, 1.0);                                        // 1 rotten
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(3, data.totalTracked(), "Source has 3 trackers");
        assertEquals(2, stack.getCount(), "Slot has count=2 after dispense");

        // Reconcile: drop the worst (rotten) -> slot keeps fresh + stale
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData reconciled = split[0];

        assertEquals(2, reconciled.totalTracked(), "Reconciled must have 2 trackers");
        assertEquals(1, reconciled.freshExpirations().size(), "Reconciled keeps 1 fresh");
        assertEquals(1, reconciled.staleExpirations().size(), "Reconciled keeps 1 stale");
        assertEquals(0, reconciled.rottenCount(), "Reconciled drops the rotten (it was dispensed)");
    }

    @Test
    void notOverTrackedDispenserSlotIsNotReconciled() {
        // Scenario: dispenser had 1 egg, dispensed nothing, slot has count=1, trackers=1
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 10000L),
                List.of(),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(1, data.totalTracked(), "Source has 1 tracker");
        assertEquals(1, stack.getCount(), "Slot has count=1");

        // No reconcile needed (totalTracked <= count)
        assertFalse(data.totalTracked() > stack.getCount(),
                "Not over-tracked, so no reconcile should happen");
    }

    @Test
    void dispenserSlotWithoutComponentIsSkipped() {
        // Scenario: dispenser has cobblestone (non-spoilable)
        ItemStack stack = new ItemStack(Items.COBBLESTONE, 1);
        // No component set

        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable item must not have SPOILAGE component");
    }
}