package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins two properties of the built-in defaults that are easy to break by accident and
 * invisible until someone plays.
 *
 * <p>Every standard food must have its OWN duration. Sharing tiers made distinct foods
 * indistinguishable in play — chicken and steak spoiled on the same schedule, so the timer told
 * the player nothing they could act on. Adding one more item by copying the line above it is
 * how that comes back.</p>
 *
 * <p>Seeds must never spoil. They are planting stock, not food; a rotting seed packet is the
 * one thing standing between a player and a field.</p>
 */
class DefaultDurationsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(SpoilageConfig cfg, String name) throws Exception {
        Field f = SpoilageConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(cfg);
    }

    private static SpoilageConfig defaults() {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        return cfg;
    }

    @Test
    void everyDefaultFoodHasItsOwnFreshDuration() throws Exception {
        Map<String, ?> durations = field(defaults(), "item_durations");
        assertFalse(durations.isEmpty(), "the defaults must define durations at all");

        Map<Long, List<String>> byFresh = new HashMap<>();
        for (Map.Entry<String, ?> e : durations.entrySet()) {
            SpoilageConfig.ItemDuration d = (SpoilageConfig.ItemDuration) e.getValue();
            byFresh.computeIfAbsent(d.fresh, k -> new ArrayList<>()).add(e.getKey());
        }

        List<String> shared = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : byFresh.entrySet()) {
            if (e.getValue().size() > 1) {
                shared.add(e.getKey() + " ticks: " + e.getValue());
            }
        }

        assertTrue(shared.isEmpty(),
                "these foods share a spoilage time, which makes them indistinguishable to a "
                        + "player watching the timer - give each its own value: " + shared);
    }

    @Test
    void seedsNeverSpoil() throws Exception {
        SpoilageConfig cfg = defaults();
        List<String> excluded = field(cfg, "excluded_items");
        for (String seed : new String[]{
                "minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds",
                "minecraft:pumpkin_seeds", "minecraft:torchflower_seeds", "minecraft:pitcher_pod"}) {
            assertTrue(excluded.contains(seed),
                    seed + " must be excluded - seeds are planting stock, not food");
        }
    }

    @Test
    void noSeedHasADuration() throws Exception {
        Map<String, ?> durations = field(defaults(), "item_durations");
        for (String id : durations.keySet()) {
            assertFalse(id.endsWith("_seeds") || id.equals("minecraft:pitcher_pod"),
                    id + " has a spoilage duration but seeds must never spoil");
        }
    }
}
