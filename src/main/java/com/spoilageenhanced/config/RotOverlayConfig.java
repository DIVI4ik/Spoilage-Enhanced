package com.spoilageenhanced.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RotOverlayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILENAME = "spoilage_enhanced_rot_overlays.json";

    // Pass 634 (Lens 6 — concurrency): the INSTANCE field was not volatile, unlike
    // SpoilageConfig.INSTANCE. getInstance() is called from the RENDER thread
    // (GuiGraphicsExtractorMixin:145, every frame for every fully-rotten rendered item)
    // while load() writes INSTANCE from the thread that first touches it. The lazy-init
    // check-then-act (if (INSTANCE == null) INSTANCE = load()) is a classic race:
    // two threads can both see null and both call load(), and per the JMM a non-volatile
    // publish can expose a partially-constructed object. Add volatile to match
    // SpoilageConfig.INSTANCE's pattern.
    private static volatile RotOverlayConfig INSTANCE;

    private boolean enable_rot_overlay = true;
    private String default_pattern = "mold_spots";
    private Map<String, String> item_overrides = new HashMap<>();

    // Available patterns (texture paths)
    private static final Map<String, Identifier> PATTERN_TEXTURES = new HashMap<>();

    /**
     * Pass 109 (Lens 13): per-Item resolved-pattern cache. getPatternForItem runs every frame
     * for every fully-rotten rendered item, and each call did a BuiltInRegistries.ITEM.getKey
     * registry lookup plus a String allocation (itemId.toString()) plus two HashMap gets.
     * The resolved Identifier depends only on the Item and the (immutable-after-load) config,
     * so a single CHM hit answers repeat calls. Cleared in load() in case a reload path is
     * ever added.
     */
    private static final Map<Item, Identifier> RESOLVED_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        PATTERN_TEXTURES.put("mold_spots", Identifier.fromNamespaceAndPath("spoilage_enhanced", "textures/overlay/mold_spots.png"));
        PATTERN_TEXTURES.put("mold_web", Identifier.fromNamespaceAndPath("spoilage_enhanced", "textures/overlay/mold_web.png"));
        PATTERN_TEXTURES.put("mold_crust", Identifier.fromNamespaceAndPath("spoilage_enhanced", "textures/overlay/mold_crust.png"));
    }

    public static RotOverlayConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public boolean isOverlayEnabled() {
        return enable_rot_overlay;
    }

    public Identifier getPatternForItem(Item item) {
        if (!enable_rot_overlay || item == null) return null;
        // Pass 109: single CHM hit for repeat calls (see RESOLVED_CACHE comment).
        Identifier cached = RESOLVED_CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        String itemIdStr = itemId.toString();

        String patternName = item_overrides.getOrDefault(itemIdStr, default_pattern);
        Identifier texture = PATTERN_TEXTURES.get(patternName);

        if (texture == null) {
            texture = PATTERN_TEXTURES.get(default_pattern);
        }
        if (texture == null) {
            texture = PATTERN_TEXTURES.get("mold_spots");
        }

        if (texture != null) {
            RESOLVED_CACHE.put(item, texture);
        }
        return texture;
    }

    private static RotOverlayConfig load() {
        RESOLVED_CACHE.clear(); // Pass 109: a reload must not serve stale resolved patterns
        Path configDir = SpoilageEnhancedPlatform.getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILENAME);

        if (Files.exists(configFile)) {
            try (Reader reader = new FileReader(configFile.toFile())) {
                RotOverlayConfig config = GSON.fromJson(reader, RotOverlayConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[Spoilage Enhanced] Failed to load rot overlay config: " + e.getMessage());
            }
        }

        RotOverlayConfig config = new RotOverlayConfig();
        config.item_overrides.put("minecraft:cooked_beef", "mold_web");
        config.item_overrides.put("minecraft:cooked_porkchop", "mold_web");
        config.item_overrides.put("minecraft:cooked_chicken", "mold_web");
        config.item_overrides.put("minecraft:cooked_mutton", "mold_web");
        config.item_overrides.put("minecraft:cooked_rabbit", "mold_web");
        config.item_overrides.put("minecraft:cooked_cod", "mold_web");
        config.item_overrides.put("minecraft:cooked_salmon", "mold_web");
        config.item_overrides.put("minecraft:bread", "mold_crust");
        config.item_overrides.put("minecraft:cookie", "mold_crust");
        config.item_overrides.put("minecraft:pumpkin_pie", "mold_crust");
        config.item_overrides.put("minecraft:cake", "mold_crust");

        config.save();
        return config;
    }

    private void save() {
        Path configDir = SpoilageEnhancedPlatform.getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILENAME);

        try (Writer writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            System.err.println("[Spoilage Enhanced] Failed to save rot overlay config: " + e.getMessage());
        }
    }
}
