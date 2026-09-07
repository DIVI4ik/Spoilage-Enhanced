package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 173 regression test: randomizeSpoilage guard clauses.
 *
 * randomizeSpoilage is used for loot generation (chests, mob drops). It requires
 * a non-null Level (uses world.getGameTime() at line 566) — the only caller
 * (RandomizableContainerBlockEntityMixin:44) guards world == null at line 30,
 * so the contract is safe in production.
 *
 * These tests pin the guard clauses that run BEFORE the world is touched —
 * empty stack and non-spoilable item must return without needing a Level.
 * The full randomization path needs a real Level, which cannot be constructed
 * in a unit test (Level has an 8-argument constructor and 20+ abstract methods).
 */
public class RandomizeSpoilageTest {

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
    void emptyStackReturnsBeforeTouchingWorld() {
        // The empty-stack guard (line 561) must return before world.getGameTime()
        // (line 566) — a null world must not NPE here.
        assertDoesNotThrow(() ->
                FoodSpoilageUtil.randomizeSpoilage(ItemStack.EMPTY, null, RandomSource.create()));
    }

    @Test
    void nonSpoilableItemReturnsBeforeTouchingWorld() {
        // The isSpoilable guard (line 561) must return before world.getGameTime()
        // (line 566) — a null world must not NPE here.
        ItemStack stone = new ItemStack(Items.STONE, 5);
        assertDoesNotThrow(() ->
                FoodSpoilageUtil.randomizeSpoilage(stone, null, RandomSource.create()));
        assertNull(stone.get(ModDataComponentTypes.SPOILAGE),
                "A non-spoilable item must not get a component");
    }

    @Test
    void nullStackReturnsBeforeTouchingWorld() {
        // Defensive: the method must not NPE on a null stack either.
        assertDoesNotThrow(() ->
                FoodSpoilageUtil.randomizeSpoilage(null, null, RandomSource.create()));
    }
}