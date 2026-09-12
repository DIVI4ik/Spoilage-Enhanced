package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1164 regression test: overflow guard in initializeItemSpoilage.
 *
 * <p>initializeItemSpoilage computes {@code currentTime + freshDuration} for every new
 * stack. The identical padding computation in updateSpoilageDataImpl got an overflow
 * guard in pass 233 ("a future config with a huge base duration"), but this path did
 * not — and it runs FIRST, on the stack's very first tick. item_durations entries are
 * unclamped by the config loader (clampHandEditedValues touches only the defaults and
 * the speed multiplier; computeBaseDurations accepts any fresh &gt; 0 up to
 * Long.MAX_VALUE), so a hand-edited huge value reaches this addition. Overflowing it
 * to negative made the stack read as already expired on its first tick.</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel), matching the padding
 * branch.</p>
 */
public class InitializeOverflowGuardTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectConfig(SpoilageConfig cfg) throws Exception {
        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, cfg);
    }

    private static SpoilageConfig readInstance() throws Exception {
        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        return (SpoilageConfig) instanceField.get(null);
    }

    @Test
    void hugeFreshDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE. computeBaseDurations
        // accepts any fresh > 0, and the config loader clamps neither this entry nor
        // anything that would keep currentTime + freshDuration in range.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(Long.MAX_VALUE - 100L, 24000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            // Drop the per-item duration cache so the injected override is visible.
            SpoilageConfig.clearDurationCache();

            ItemStack stack = new ItemStack(Items.CARROT, 1);
            FoodSpoilageUtil.initializeItemSpoilage(stack, null);

            SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
            assertNotNull(data, "SPOILAGE component must be present after initialization");
            assertEquals(1, data.freshExpirations().size(), "One expiration for count=1");
            assertEquals(Long.MAX_VALUE, data.freshExpirations().get(0),
                    "currentTime + freshDuration would overflow (0 + Long.MAX_VALUE - 100 is fine, "
                            + "but any real gameTime pushes it past Long.MAX_VALUE); the guard must "
                            + "clamp to the NEVER sentinel rather than wrap negative");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void exactlyMaxDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(Long.MAX_VALUE, Long.MAX_VALUE));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            ItemStack stack = new ItemStack(Items.CARROT, 2);
            FoodSpoilageUtil.initializeItemSpoilage(stack, null);

            SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
            for (long exp : data.freshExpirations()) {
                assertEquals(Long.MAX_VALUE, exp,
                        "Long.MAX_VALUE duration must clamp to Long.MAX_VALUE, not wrap");
            }
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void normalDurationStillComputesPlainly() throws Exception {
        // Control: the guard must not change the normal path. With world == null,
        // currentTime is 0, so expirationTime == freshDuration exactly.
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            ItemStack stack = new ItemStack(Items.CARROT, 1);
            FoodSpoilageUtil.initializeItemSpoilage(stack, null);

            SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
            long exp = data.freshExpirations().get(0);
            assertTrue(exp > 0 && exp < Long.MAX_VALUE,
                    "Normal duration must produce a plain finite expiration, got " + exp);
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
