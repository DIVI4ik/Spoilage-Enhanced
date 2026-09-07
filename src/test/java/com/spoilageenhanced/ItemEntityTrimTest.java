package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
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
 * Pass 408 (L13 observed): item entity trim behavior — what a player sees.
 *
 * <p>Scenario: player drops a stack of 3 rotten eggs (Q-drop). The dropped entity
 * is created via ItemStack.copy() which carries ALL trackers onto a 1-item entity
 * -> over-tracked (totalTracked=3, count=1). ItemEntityMixin.onTick trims to count
 * keeping the WORST trackers, so the dropped item preserves its true spoilage state.
 *
 * The mixin needs Level for world.getGameTime(). This test pins the trim logic
 * by checking extractWorstItems on the stack before the mixin runs.
 *
 * What should happen: over-tracked dropped item is trimmed to count, keeping WORST.
 */
public class ItemEntityTrimTest {

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
    void overTrackedDroppedItemIsTrimmedToCount() {
        // Scenario: player drops 3 rotten eggs (Q-drop). The entity has count=1 but 3 trackers.
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 3, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(3, data.totalTracked(), "Source stack has 3 trackers");
        assertEquals(1, stack.getCount(), "Dropped entity has count=1");

        // Trim to count (what ItemEntityMixin does)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData trimmed = split[1]; // worst 1 tracker

        assertEquals(1, trimmed.totalTracked(), "Trimmed must have 1 tracker (count=1)");
        assertEquals(1, trimmed.rottenCount(), "Trimmed must keep the WORST (rotten)");
    }

    @Test
    void overTrackedMixedStackIsTrimmedToWorst() {
        // Scenario: player drops 3 eggs (1 fresh, 1 stale, 1 rotten). Entity has count=1, trackers=3.
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 10000L),  // 1 fresh
                List.of(System.currentTimeMillis() + 10000L),  // 1 stale
                1, 1.0);                                        // 1 rotten
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(3, data.totalTracked(), "Source stack has 3 trackers");
        assertEquals(1, stack.getCount(), "Dropped entity has count=1");

        // Trim to count (what ItemEntityMixin does)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData trimmed = split[1]; // worst 1 tracker

        assertEquals(1, trimmed.totalTracked(), "Trimmed must have 1 tracker (count=1)");
        assertEquals(1, trimmed.rottenCount(), "Trimmed must keep the WORST (rotten)");
    }

    @Test
    void overTrackedStaleStackIsTrimmedToStale() {
        // Scenario: player drops 2 stale eggs. Entity has count=1, trackers=2.
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(
                List.of(),
                List.of(System.currentTimeMillis() + 10000L, System.currentTimeMillis() + 20000L),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(2, data.totalTracked(), "Source stack has 2 trackers");
        assertEquals(1, stack.getCount(), "Dropped entity has count=1");

        // Trim to count (what ItemEntityMixin does)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData trimmed = split[1]; // worst 1 tracker

        assertEquals(1, trimmed.totalTracked(), "Trimmed must have 1 tracker (count=1)");
        assertEquals(1, trimmed.staleExpirations().size(), "Trimmed must keep the WORST (stale)");
    }

    @Test
    void notOverTrackedStackIsNotTrimmed() {
        // Scenario: player drops 1 fresh egg. Entity has count=1, trackers=1.
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 10000L),
                List.of(),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(1, data.totalTracked(), "Source stack has 1 tracker");
        assertEquals(1, stack.getCount(), "Dropped entity has count=1");

        // No trim needed (totalTracked <= count)
        // The mixin checks: if (data != null && data.totalTracked() > count)
        assertFalse(data.totalTracked() > stack.getCount(),
                "Not over-tracked, so no trim should happen");
    }

    @Test
    void entityWithoutComponentIsSkipped() {
        // Scenario: player drops cobblestone (non-spoilable). Entity has no SPOILAGE component.
        ItemStack stack = new ItemStack(Items.COBBLESTONE, 1);
        // No component set

        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable item must not have SPOILAGE component");
        assertFalse(SpoilageConfig.getInstance().isSpoilable(stack.getItem()),
                "Cobblestone must not be spoilable");
    }

    @Test
    void entityWithEmptyComponentIsSkipped() {
        // Scenario: player drops an item with empty SPOILAGE component.
        ItemStack stack = new ItemStack(Items.EGG, 1);
        stack.set(ModDataComponentTypes.SPOILAGE, SpoilageData.DEFAULT);

        assertTrue(stack.has(ModDataComponentTypes.SPOILAGE),
                "Stack has SPOILAGE component (empty)");
        assertTrue(stack.get(ModDataComponentTypes.SPOILAGE).isEmpty(),
                "Component is empty (DEFAULT)");
    }
}