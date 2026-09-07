package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.KnownHandPickedBlocks;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The declared mappings for blocks whose food is picked by hand.
 *
 * <p>The property that matters most here is the one about *other* players: this table names
 * blocks from third-party mods, and a player who does not have those mods must end up with
 * nothing extra in their config. Only vanilla is registered in this environment, so that is
 * exactly what these tests exercise.</p>
 */
class KnownHandPickedBlocksTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anAbsentModAddsNothing() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        int before = config.getTrackedBlocks().size();

        int added = KnownHandPickedBlocks.registerAll();

        assertEquals(0, added,
                "none of the declared mods are loaded here, so nothing may be registered - "
                        + "otherwise every player without those mods collects config entries for "
                        + "blocks that do not exist in their game");
        assertEquals(before, config.getTrackedBlocks().size(),
                "tracked_blocks must be untouched when no declared block exists");
    }

    @Test
    void runningItTwiceChangesNothing() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        KnownHandPickedBlocks.registerAll();
        int after = config.getTrackedBlocks().size();
        KnownHandPickedBlocks.registerAll();
        assertEquals(after, config.getTrackedBlocks().size(),
                "the scan runs on every server start and must stay idempotent");
    }

    @Test
    void everyDeclaredIdIsWellFormedAndNotVanilla() {
        Map<String, String> entries = KnownHandPickedBlocks.entries();
        assertFalse(entries.isEmpty(), "the table is pointless if it is empty");

        for (Map.Entry<String, String> e : entries.entrySet()) {
            String blockId = e.getKey();
            String itemId = e.getValue();

            assertDoesNotThrow(() -> Identifier.parse(blockId), blockId + " must be a valid id");
            assertDoesNotThrow(() -> Identifier.parse(itemId), itemId + " must be a valid id");

            assertFalse(blockId.startsWith("minecraft:"),
                    blockId + " is vanilla: its loot table can be read, so declaring it here "
                            + "hides a derivation that already works");

            // The drop is normally a vanilla item (the apple tree hands over a plain apple), and
            // when it is, it has to actually exist - a typo would register a mapping to nothing.
            if (itemId.startsWith("minecraft:")) {
                assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemId)),
                        itemId + " is declared as a drop but no such vanilla item exists");
            }
        }
    }
}
