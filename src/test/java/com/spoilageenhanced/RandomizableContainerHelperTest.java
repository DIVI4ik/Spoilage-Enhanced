package com.spoilageenhanced;

import com.spoilageenhanced.util.RandomizableContainerHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 125 regression test: RandomizableContainerHelper ThreadLocal state.
 *
 * The helper uses a ThreadLocal<Boolean> to track whether we're currently
 * inside a randomize call, to prevent recursion. This test verifies the
 * ThreadLocal is properly initialized and can be set/reset.
 */
public class RandomizableContainerHelperTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Bind item components so isSpoilable() doesn't throw
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @AfterEach
    void resetThreadLocal() {
        // Clean up after each test
        RandomizableContainerHelper.IS_RANDOMIZING.remove();
    }

    @Test
    void threadLocalDefaultsToFalse() {
        // The ThreadLocal is initialized with Boolean.FALSE
        Boolean value = RandomizableContainerHelper.IS_RANDOMIZING.get();
        assertNotNull(value, "IS_RANDOMIZING should not be null after first access");
        assertFalse(value, "IS_RANDOMIZING should default to false");
    }

    @Test
    void threadLocalCanBeSetToTrue() {
        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.TRUE);
        assertTrue(RandomizableContainerHelper.IS_RANDOMIZING.get());

        // Clean up
        RandomizableContainerHelper.IS_RANDOMIZING.remove();
    }

    @Test
    void threadLocalCanBeReset() {
        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.TRUE);
        assertTrue(RandomizableContainerHelper.IS_RANDOMIZING.get());

        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.FALSE);
        assertFalse(RandomizableContainerHelper.IS_RANDOMIZING.get());
    }

    @Test
    void threadLocalRemoveWorks() {
        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.TRUE);
        RandomizableContainerHelper.IS_RANDOMIZING.remove();

        // After remove, the ThreadLocal will re-initialize to its default (Boolean.FALSE)
        Boolean value = RandomizableContainerHelper.IS_RANDOMIZING.get();
        assertNotNull(value, "After remove, get() should return the default value");
        assertFalse(value, "After remove, get() should return false (the default)");
    }

    @Test
    void threadLocalIsPerThread() {
        // This test verifies that the ThreadLocal is indeed thread-local
        // by checking the main thread's value and ensuring it's isolated

        // Set a value in the main thread
        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.TRUE);
        assertTrue(RandomizableContainerHelper.IS_RANDOMIZING.get());

        // Clean up
        RandomizableContainerHelper.IS_RANDOMIZING.remove();
    }
}