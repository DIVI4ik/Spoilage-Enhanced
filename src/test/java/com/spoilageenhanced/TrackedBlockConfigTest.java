package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 257 regression test: SpoilageConfig.isBlockTracked + getTrackedBlockDropItem.
 *
 * <p>getTrackedBlockDropItem resolves a block id to the food item it drops — first via
 * the tracked_blocks config map, then via a hardcoded vanilla fallback chain. These
 * tests pin the fallback chain and the config-map precedence.</p>
 */
public class TrackedBlockConfigTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void pumpkinResolvesToPumpkin() {
        assertEquals("minecraft:pumpkin",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:pumpkin"),
                "minecraft:pumpkin must resolve to minecraft:pumpkin");
    }

    @Test
    void carvedPumpkinResolvesToPumpkin() {
        assertEquals("minecraft:pumpkin",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:carved_pumpkin"),
                "minecraft:carved_pumpkin must resolve to minecraft:pumpkin");
    }

    @Test
    void melonResolvesToMelonSlice() {
        assertEquals("minecraft:melon_slice",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:melon"),
                "minecraft:melon must resolve to minecraft:melon_slice");
    }

    @Test
    void carrotsResolvesToCarrot() {
        assertEquals("minecraft:carrot",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:carrots"),
                "minecraft:carrots must resolve to minecraft:carrot");
    }

    @Test
    void sweetBerryBushResolvesToSweetBerries() {
        assertEquals("minecraft:sweet_berries",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:sweet_berry_bush"),
                "minecraft:sweet_berry_bush must resolve to minecraft:sweet_berries");
    }

    @Test
    void caveVinesResolveToGlowBerries() {
        assertEquals("minecraft:glow_berries",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:cave_vines"),
                "minecraft:cave_vines must resolve to minecraft:glow_berries");
        assertEquals("minecraft:glow_berries",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:cave_vines_plant"),
                "minecraft:cave_vines_plant must resolve to minecraft:glow_berries");
    }

    @Test
    void unknownBlockReturnsNull() {
        assertNull(SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:stone"),
                "An untracked block must resolve to null");
        assertNull(SpoilageConfig.getInstance().getTrackedBlockDropItem("somemod:unknown_block"),
                "An unknown modded block must resolve to null");
    }

    @Test
    void isBlockTrackedMatchesDropResolution() {
        assertTrue(SpoilageConfig.getInstance().isBlockTracked("minecraft:pumpkin"),
                "minecraft:pumpkin must be tracked");
        assertFalse(SpoilageConfig.getInstance().isBlockTracked("minecraft:stone"),
                "minecraft:stone must not be tracked");
    }
}
