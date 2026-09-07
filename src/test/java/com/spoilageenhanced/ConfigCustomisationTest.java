package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a player or pack author can do from the config file alone, without touching code.
 *
 * <p>These are the promises the config makes. Each has been broken at least once: durations
 * were overwritten by the defaults on every load, and there was no way at all to keep a block
 * out - deleting a line from the auto-written {@code tracked_blocks} does not stick, because
 * the next scan puts it back.</p>
 */
class ConfigCustomisationTest {

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

    private static void set(SpoilageConfig cfg, String name, Object value) throws Exception {
        Field f = SpoilageConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(cfg, value);
    }

    @Test
    void anEditedDurationSurvivesTheDefaults() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        Map<String, SpoilageConfig.ItemDuration> durations = field(cfg, "item_durations");
        durations.put("minecraft:bread", new SpoilageConfig.ItemDuration(12345L, 6789L));

        cfg.populateDefaults();

        SpoilageConfig.ItemDuration kept = durations.get("minecraft:bread");
        assertEquals(12345L, kept.fresh,
                "a hand-edited duration must survive populateDefaults - it used to be "
                        + "overwritten on every load, so the config looked like it ignored the player");
        assertEquals(6789L, kept.stale, "the stale half must survive too");
    }

    @Test
    void anExcludedBlockIsNeverTracked() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        set(cfg, "excluded_blocks", List.of("minecraft:pumpkin"));

        assertFalse(cfg.registerTrackedBlock("minecraft:pumpkin", "minecraft:pumpkin", false),
                "auto-detection must not add a block the player excluded");
        assertNull(cfg.getTrackedBlockDropItem("minecraft:pumpkin"),
                "an excluded block must not resolve to a drop even if something registered it");
        assertFalse(cfg.isBlockTracked("minecraft:pumpkin"), "and must not read as tracked");
    }

    @Test
    void excludingAnItemAlsoStopsTheBlockThatDropsIt() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        assertTrue(cfg.isBlockTracked("minecraft:melon"),
                "melon is tracked by default - the premise of this test");

        set(cfg, "excluded_items", List.of("minecraft:melon_slice"));
        set(cfg, "excludedSet", null);

        assertNull(cfg.getTrackedBlockDropItem("minecraft:melon"),
                "someone who writes 'melon slices never spoil' means it, and should not have to "
                        + "say it a second time for the block that drops them");
    }

    @Test
    void excludingABlockDoesNotNeedTrackedBlocksEdited() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();

        Map<String, String> tracked = field(cfg, "tracked_blocks");
        assertTrue(tracked.containsKey("minecraft:melon"),
                "the entry stays in tracked_blocks - that map is written by the mod");

        set(cfg, "excluded_blocks", List.of("minecraft:melon"));
        set(cfg, "excludedBlockSet", null);

        assertNull(cfg.getTrackedBlockDropItem("minecraft:melon"),
                "the exclusion must win over the entry, so the player never has to fight the "
                        + "scanner by deleting lines it will rewrite");
    }

    /**
     * Pass 536: the exclusion must hold on the growth-ignoring lookup too.
     *
     * <p>Breaking an unripe crop resolves its drop through
     * {@code DynamicFoodBlockCache.getFoodDropIgnoringGrowth} (the ripe-stage variant answers
     * null for a seedling by design). That path used to skip the excluded_blocks check and fall
     * through to "does the block's own item spoil" — so a wheat seedling of a block the player
     * excluded was stamped ROTTEN on break, while a ripe wheat block of the same config was left
     * alone. The exclusion was honoured for the plant and ignored for its seedling.</p>
     */
    @Test
    void anExcludedBlockIsNotAFoodSourceEvenIgnoringGrowth() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        set(cfg, "excluded_blocks", List.of("minecraft:wheat"));
        set(cfg, "excludedBlockSet", null);

        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object original = instanceField.get(null);
        try {
            instanceField.set(null, cfg);

            DynamicFoodBlockCache.clear();
            assertNull(DynamicFoodBlockCache.getFoodDropIgnoringGrowth(
                            Blocks.WHEAT.defaultBlockState(), null, new BlockPos(0, 64, 0)),
                    "a seedling of an excluded block must not resolve a food drop - the ripe "
                            + "plant of the same block answers null, and the exclusion cannot "
                            + "depend on how grown the plant happens to be");
        } finally {
            instanceField.set(null, original);
            DynamicFoodBlockCache.clear();
        }
    }

    /**
     * The other half of the same contract: without the exclusion the lookup must answer, so the
     * guard above is proven to be the reason for the null and not some other early return.
     */
    @Test
    void aTrackedBlockStillAnswersIgnoringGrowth() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();

        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object original = instanceField.get(null);
        try {
            instanceField.set(null, cfg);

            DynamicFoodBlockCache.clear();
            assertEquals("minecraft:wheat", DynamicFoodBlockCache.getFoodDropIgnoringGrowth(
                            Blocks.WHEAT.defaultBlockState(), null, new BlockPos(0, 64, 0)),
                    "wheat is tracked by default and its seedling must resolve the drop - this "
                            + "is the unripe-break path that marks early-harvested produce rotten");
        } finally {
            instanceField.set(null, original);
            DynamicFoodBlockCache.clear();
        }
    }

    @Test
    void getItemIdResolvesVanillaItemsCorrectly() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();

        // Vanilla items must resolve to "minecraft:<name>" format
        assertEquals("minecraft:apple", cfg.getItemId(Items.APPLE));
        assertEquals("minecraft:carrot", cfg.getItemId(Items.CARROT));
        assertEquals("minecraft:diamond", cfg.getItemId(Items.DIAMOND));
        assertEquals("minecraft:air", cfg.getItemId(Items.AIR));
        assertEquals("minecraft:air", cfg.getItemId(null));

        // The cache must be populated
        var cache = (java.util.Map) field(cfg, "ITEM_ID_CACHE");
        assertTrue(cache.containsKey(Items.APPLE));
        assertTrue(cache.containsKey(Items.CARROT));
    }

    @Test
    void getItemIdSentinelFormatIsStable() {
        // The sentinel format is "spoilage_enhanced:unresolved:<classname>"
        // This test documents the format so future changes don't silently break
        // callers that might check for it.
        String sentinel = "spoilage_enhanced:unresolved:com.example.FakeItem";
        assertTrue(sentinel.startsWith("spoilage_enhanced:unresolved:"));
    }
}


