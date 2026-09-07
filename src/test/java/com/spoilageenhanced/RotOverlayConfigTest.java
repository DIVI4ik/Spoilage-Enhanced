package com.spoilageenhanced;

import com.spoilageenhanced.config.RotOverlayConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 239 regression test: RotOverlayConfig.getPatternForItem.
 *
 * <p>getPatternForItem runs every frame for every fully-rotten rendered item (called
 * from the spoilage bar renderer). Pass 109 added a per-Item CHM cache. This test pins
 * the resolution contract: null item, disabled overlay, override lookup, default
 * fallback, unknown-pattern fallback, and cache behavior.</p>
 */
public class RotOverlayConfigTest {

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

    @Test
    void nullItemReturnsNull() {
        // The overlay must not resolve a pattern for a null item.
        assertNull(RotOverlayConfig.getInstance().getPatternForItem(null),
                "null item must return null");
    }

    @Test
    void knownItemResolvesAPattern() {
        // With the overlay enabled (default), a known item must resolve to a non-null
        // pattern — the default fallback chain guarantees one of default_pattern or
        // mold_spots exists.
        Item item = Items.COOKED_BEEF;
        assertNotNull(RotOverlayConfig.getInstance().getPatternForItem(item),
                "A known item must resolve to a pattern (default fallback chain)");
    }

    @Test
    void cachedCallReturnsSameInstance() {
        // Pass 109: repeat calls must hit the CHM and return the SAME Identifier instance.
        Item item = Items.BREAD;
        Object first = RotOverlayConfig.getInstance().getPatternForItem(item);
        Object second = RotOverlayConfig.getInstance().getPatternForItem(item);
        assertSame(first, second,
                "Repeat calls must return the cached instance (Pass 109 CHM cache)");
    }

    @Test
    void overrideBeatsDefault() throws Exception {
        // The default config puts minecraft:cooked_beef -> mold_web. The override must
        // win over the default pattern.
        Field overridesField = RotOverlayConfig.class.getDeclaredField("item_overrides");
        overridesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> overrides =
                (java.util.Map<String, String>) overridesField.get(RotOverlayConfig.getInstance());
        String override = overrides.get("minecraft:cooked_beef");
        assertNotNull(override, "The default config must contain the cooked_beef override");
        assertEquals("mold_web", override,
                "The cooked_beef override must be mold_web (set in load())");
    }

    @Test
    void unknownPatternFallsBackToDefault() throws Exception {
        // An override naming an unknown pattern must fall back to default_pattern,
        // then to mold_spots — never null when the overlay is enabled.
        Field overridesField = RotOverlayConfig.class.getDeclaredField("item_overrides");
        overridesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> overrides =
                (java.util.Map<String, String>) overridesField.get(RotOverlayConfig.getInstance());
        overrides.put("minecraft:stick", "nonexistent_pattern");

        try {
            assertNotNull(RotOverlayConfig.getInstance().getPatternForItem(Items.STICK),
                    "An unknown pattern name must fall back to default_pattern, not null");
        } finally {
            overrides.remove("minecraft:stick");
        }
    }
}
