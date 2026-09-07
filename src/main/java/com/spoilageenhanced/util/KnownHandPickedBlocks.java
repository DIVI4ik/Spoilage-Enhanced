package com.spoilageenhanced.util;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Blocks from other mods whose food is taken by hand, declared because nothing readable
 * declares it for us.
 *
 * <h2>Why this file exists at all</h2>
 *
 * <p>Everything else in this mod derives what a block yields rather than naming it — loot
 * tables, recipes, tags, the FOOD component. That principle holds for every plant that is
 * harvested by breaking it, which is nearly all of them.</p>
 *
 * <p>It breaks for a plant that is picked with a right-click. MegaCookery's apple tree is the
 * worked example: its loot table lists the leaves, a sapling and sticks, and the apple exists
 * only inside {@code useWithoutItem}, which pops it and winds the branch back from fruiting to
 * flowering. There is no data file, no tag and no recipe anywhere that connects that block to
 * an apple. The information is in a method body, and a method body cannot be read without
 * running it.</p>
 *
 * <p>{@link DynamicFoodBlockCache#learnFoodDropFromInteraction} solves this by watching a
 * harvest happen, and it stays the general answer — it needs no mod to be named and covers
 * everything absent from the table below. But it cannot answer before the first harvest, and
 * that answer is stored per installation: a player on another modpack, or anyone else who
 * installs this mod, starts over. One apple gives no freshness for them too.</p>
 *
 * <p>So the table below buys exactly that: the first harvest. It is a deliberate, narrow
 * exception, not a new way of working.</p>
 *
 * <h2>What keeps it from doing harm</h2>
 *
 * <ul>
 *   <li>An entry is applied only when both the block and the item actually exist in the
 *       registry, so a player without the mod gets nothing added to their config.</li>
 *   <li>{@link SpoilageConfig#registerTrackedBlock} inserts with putIfAbsent, so a mapping
 *       written by hand always wins, and it refuses anything named in {@code excluded_blocks}
 *       or whose drop is in {@code excluded_items}.</li>
 *   <li>Running it again registers nothing new.</li>
 * </ul>
 *
 * <h2>Adding an entry</h2>
 *
 * <p>Only for a block that is genuinely unreachable by derivation — the food is not in its loot
 * table at any growth stage. If breaking the block yields the food, auto-detection already has
 * it and an entry here is dead weight. Confirm against the mod's own jar rather than assuming:
 * for the apple tree, {@code assets/.../blockstates} named the growth property and
 * {@code data/.../loot_table/blocks} proved the apple was absent from it.</p>
 */
public final class KnownHandPickedBlocks {

    private KnownHandPickedBlocks() {
    }

    /** Block id → the food it hands over on a right-click. */
    private static final Map<String, String> HAND_PICKED = Map.of(
            // MegaCookery apple tree. apple_age 0 bare / 1 flowering / 2 fruiting; picking at 2
            // pops minecraft:apple and returns the branch to 1. Loot table yields only leaves,
            // a sapling or sticks.
            "megacookery:fruiting_apple_leaves", "minecraft:apple"
    );

    /**
     * Registers every entry whose block and item are both present.
     *
     * @return how many mappings were added, so the caller can decide whether to save
     */
    public static int registerAll() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        int added = 0;

        for (Map.Entry<String, String> entry : HAND_PICKED.entrySet()) {
            String blockId = entry.getKey();
            String itemId = entry.getValue();
            try {
                // Both halves have to exist. Without this check the config of every player who
                // does not have the mod would collect entries for blocks that are not there.
                if (!BuiltInRegistries.BLOCK.containsKey(Identifier.parse(blockId))) continue;
                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemId))) continue;
                if (config.isBlockTracked(blockId)) continue;

                if (config.registerTrackedBlock(blockId, itemId, false)) {
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                            "KnownHandPickedBlocks: " + blockId + " -> " + itemId
                                    + " (declared; its food is in no loot table)");
                    added++;
                }
            } catch (Throwable t) {
                // A malformed entry must never abort world loading.
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                        "KnownHandPickedBlocks: skipped " + blockId + ": " + t);
            }
        }
        return added;
    }

    /** The declared mappings, for tests. */
    public static Map<String, String> entries() {
        return HAND_PICKED;
    }
}
