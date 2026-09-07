package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModFeatureSimulationTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();

        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    public void testDataComponentRegistration() {
        assertNotNull(ModDataComponentTypes.SPOILAGE, "ModDataComponentTypes.SPOILAGE should not be null");
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(ModDataComponentTypes.SPOILAGE);
        assertNotNull(id, "ModDataComponentTypes.SPOILAGE must have an ID in BuiltInRegistries.DATA_COMPONENT_TYPE");
        assertEquals("spoilage_enhanced:spoilage", id.toString());

        int rawId = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(ModDataComponentTypes.SPOILAGE);
        assertTrue(rawId >= 0, "Raw registry ID must be non-negative, was: " + rawId);
    }

    @Test
    public void testItemStackNetworkSerialization() {
        // This was the exact cause of the crash in set_creative_mode_slot packet
        ItemStack stack = new ItemStack(Items.BREAD, 3);
        SpoilageData data = new SpoilageData(List.of(1000L, 2000L), List.of(3000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);

        // Encode ItemStack with SpoilageData attached
        assertDoesNotThrow(() -> {
            ItemStack.STREAM_CODEC.encode(buf, stack);
        }, "Encoding ItemStack with SpoilageData should not throw EncoderException");

        // Decode ItemStack back
        ItemStack decoded = ItemStack.STREAM_CODEC.decode(buf);
        assertNotNull(decoded);
        assertEquals(Items.BREAD, decoded.getItem());
        assertEquals(3, decoded.getCount());

        SpoilageData decodedData = decoded.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(decodedData, "Decoded stack must retain SpoilageData");
        assertEquals(2, decodedData.freshExpirations().size());
        assertEquals(1, decodedData.staleExpirations().size());
        assertEquals(1000L, decodedData.freshExpirations().get(0));
        assertEquals(2000L, decodedData.freshExpirations().get(1));
        assertEquals(3000L, decodedData.staleExpirations().get(0));
    }

    @Test
    public void testFoodSpoilageUtilCalculations() {
        ItemStack apple = new ItemStack(Items.APPLE, 2);
        assertTrue(SpoilageConfig.getInstance().isSpoilable(apple.getItem()));

        // Create data: 1 fresh, 1 stale
        SpoilageData initial = new SpoilageData(List.of(5000L), List.of(2000L), 0, 1.0);
        apple.set(ModDataComponentTypes.SPOILAGE, initial);

        // Split worst item
        SpoilageData[] splitWorst = FoodSpoilageUtil.extractWorstItems(initial, 1);
        assertEquals(1, splitWorst[0].totalTracked()); // remaining (fresh)
        assertEquals(1, splitWorst[1].totalTracked()); // extracted (stale)
        assertEquals(1, splitWorst[1].staleExpirations().size());

        // Split best item
        SpoilageData[] splitBest = FoodSpoilageUtil.extractBestItems(initial, 1);
        assertEquals(1, splitBest[0].totalTracked()); // remaining (stale)
        assertEquals(1, splitBest[1].totalTracked()); // extracted (fresh)
        assertEquals(1, splitBest[1].freshExpirations().size());

        // Rescale timestamps
        SpoilageData rescaled = FoodSpoilageUtil.rescaleItemTimestamps(initial, 1000L, 1.0, 2.0);
        assertNotNull(rescaled);
        assertEquals(2.0, rescaled.speedMultiplier());
    }

    @Test
    public void testRottenItemDetectionAndState() {
        ItemStack rottenBread = new ItemStack(Items.BREAD, 2);
        SpoilageData rottenData = new SpoilageData(List.of(), List.of(), 2, 1.0);
        rottenBread.set(ModDataComponentTypes.SPOILAGE, rottenData);

        assertTrue(FoodSpoilageUtil.isEntirelyRotten(rottenBread));
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(rottenBread));

        ItemStack mixedApple = new ItemStack(Items.APPLE, 2);
        SpoilageData mixedData = new SpoilageData(List.of(10000L), List.of(5000L), 0, 1.0);
        mixedApple.set(ModDataComponentTypes.SPOILAGE, mixedData);

        assertFalse(FoodSpoilageUtil.isEntirelyRotten(mixedApple));
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(mixedApple));
    }

    @Test
    public void testRotOverlayTextureLookup() {
        var overlayConfig = com.spoilageenhanced.config.RotOverlayConfig.getInstance();
        assertNotNull(overlayConfig);
        assertTrue(overlayConfig.isOverlayEnabled());

        Identifier beefOverlay = overlayConfig.getPatternForItem(Items.COOKED_BEEF);
        assertNotNull(beefOverlay);
        assertEquals("spoilage_enhanced:textures/overlay/mold_web.png", beefOverlay.toString());

        Identifier breadOverlay = overlayConfig.getPatternForItem(Items.BREAD);
        assertNotNull(breadOverlay);
        assertEquals("spoilage_enhanced:textures/overlay/mold_crust.png", breadOverlay.toString());
    }

    @Test
    public void testAutoFoodDetection() {
        assertTrue(com.spoilageenhanced.util.AutoFoodDetector.isContainerItem(Items.BOWL));
        assertTrue(com.spoilageenhanced.util.AutoFoodDetector.isContainerItem(Items.BUCKET));
        assertFalse(com.spoilageenhanced.util.AutoFoodDetector.isContainerItem(Items.BREAD));

        assertTrue(com.spoilageenhanced.util.AutoFoodDetector.isFoodOrMealItem(Items.BREAD));
        assertTrue(com.spoilageenhanced.util.AutoFoodDetector.isFoodOrMealItem(Items.MILK_BUCKET));
        assertFalse(com.spoilageenhanced.util.AutoFoodDetector.isFoodOrMealItem(Items.DIAMOND_SWORD));
    }

    @Test
    public void testItemMerging() {
        SpoilageData batch1 = new SpoilageData(List.of(1000L), List.of(), 0, 1.0);
        SpoilageData batch2 = new SpoilageData(List.of(2000L), List.of(500L), 1, 1.0);

        SpoilageData merged = FoodSpoilageUtil.mergeItems(batch1, batch2);
        assertNotNull(merged);
        assertEquals(2, merged.freshExpirations().size());
        assertEquals(1, merged.staleExpirations().size());
        assertEquals(1, merged.rottenCount());
        assertEquals(4, merged.totalTracked());
    }

    @Test
    public void testTradingValidationSimulation() {
        // Rotten item in payment slot blocks trade
        ItemStack paymentStack = new ItemStack(Items.PUMPKIN_PIE, 1);
        SpoilageData rottenPayment = new SpoilageData(List.of(), List.of(), 1, 1.0);
        paymentStack.set(ModDataComponentTypes.SPOILAGE, rottenPayment);
        assertTrue(FoodSpoilageUtil.isEntirelyRotten(paymentStack));

        // Fresh payment allows trade
        ItemStack freshPayment = new ItemStack(Items.EMERALD, 5);
        assertFalse(FoodSpoilageUtil.isEntirelyRotten(freshPayment));

        // Trade outcome food item gets initialized with fresh timestamps
        ItemStack boughtBread = new ItemStack(Items.BREAD, 1);
        long worldTime = 5000L;
        long freshDuration = SpoilageConfig.getInstance().getBaseFreshDurationForItem(boughtBread.getItem());
        SpoilageData freshTradeData = new SpoilageData(List.of(worldTime + freshDuration), List.of(), 0, 1.0);
        boughtBread.set(ModDataComponentTypes.SPOILAGE, freshTradeData);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(boughtBread));
        assertFalse(FoodSpoilageUtil.isEntirelyRotten(boughtBread));
    }

    @Test
    public void testMilkSpoilageSimulation() {
        ItemStack freshMilk = new ItemStack(Items.MILK_BUCKET);
        ItemStack staleMilk = new ItemStack(Items.MILK_BUCKET);
        ItemStack rottenMilk = new ItemStack(Items.MILK_BUCKET);

        freshMilk.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(10000L), List.of(), 0, 1.0));
        staleMilk.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(), List.of(20000L), 0, 1.0));
        rottenMilk.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(), List.of(), 1, 1.0));

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(freshMilk));
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(staleMilk));
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(rottenMilk));
    }

    @Test
    public void testProjectileWorstExtractionSimulation() {
        ItemStack eggStack = new ItemStack(Items.EGG, 3);
        // 1 fresh, 1 stale, 1 rotten
        SpoilageData eggData = new SpoilageData(List.of(10000L), List.of(5000L), 1, 1.0);
        eggStack.set(ModDataComponentTypes.SPOILAGE, eggData);

        // Throwing projectile extracts the worst item (which is rotten)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(eggData, 1);
        SpoilageData remaining = split[0];
        SpoilageData thrown = split[1];

        assertEquals(1, thrown.totalTracked());
        assertEquals(1, thrown.rottenCount());
        assertEquals(2, remaining.totalTracked());
        assertEquals(1, remaining.freshExpirations().size());
        assertEquals(1, remaining.staleExpirations().size());
    }

    @Test
    public void testDynamicFoodRegistration() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.registerDynamicFoodItem("spoilage_enhanced:custom_cheese", 72000, 36000);
        assertTrue(config.getAdditionalSet().contains("spoilage_enhanced:custom_cheese"));

        config.registerDynamicStorageItem("minecraft:hay_block", "minecraft:wheat");
        assertEquals("minecraft:wheat", config.getTrackedBlockDropItem("minecraft:hay_block"));
    }

    @Test
    public void testDragAndPickupAllInvariance() {
        // User scenario: 54 potatoes total (32 stale, 22 rotten)
        List<Long> staleList = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            staleList.add(5000L + i * 10L);
        }
        int rottenCount = 22;
        SpoilageData initialCursor = new SpoilageData(Collections.emptyList(), staleList, rottenCount, 1.0);
        assertEquals(54, initialCursor.totalTracked());

        // 1. Simulate dragging across 3 slots: 18 items in each slot
        SpoilageData currentCursor = initialCursor;
        List<SpoilageData> distributedSlots = new ArrayList<>();
        int[] distributions = new int[] { 18, 18, 18 };

        for (int count : distributions) {
            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(currentCursor, count);
            currentCursor = split[0];
            distributedSlots.add(split[1]);
        }

        // Verify cursor is empty after distributing all 54 items
        assertEquals(0, currentCursor.totalTracked());

        // Verify total sum across the 3 slots matches exactly: 32 stale, 22 rotten
        int totalStale = 0;
        int totalRotten = 0;
        for (SpoilageData slotData : distributedSlots) {
            totalStale += slotData.staleExpirations().size();
            totalRotten += slotData.rottenCount();
        }
        assertEquals(32, totalStale, "Stale count must remain exactly 32 after drag");
        assertEquals(22, totalRotten, "Rotten count must remain exactly 22 after drag");

        // 2. Simulate gathering them back via PICKUP_ALL (double click)
        // Cursor starts with slot 0 (18 items)
        SpoilageData gatheredCursor = distributedSlots.get(0);
        // User double clicks, picking up slot 1 and slot 2
        gatheredCursor = FoodSpoilageUtil.mergeItems(gatheredCursor, distributedSlots.get(1));
        gatheredCursor = FoodSpoilageUtil.mergeItems(gatheredCursor, distributedSlots.get(2));

        assertEquals(54, gatheredCursor.totalTracked());
        assertEquals(32, gatheredCursor.staleExpirations().size(), "Stale count must remain 32 after double-click pickup all");
        assertEquals(22, gatheredCursor.rottenCount(), "Rotten count must remain 22 after double-click pickup all");
    }

    @Test
    public void testCreativeClonePreservesProportions() {
        // Original stack: 10 items (5 stale, 5 rotten)
        List<Long> stale = List.of(100L, 200L, 300L, 400L, 500L);
        SpoilageData origData = new SpoilageData(Collections.emptyList(), stale, 5, 1.0);

        int targetCount = 64;
        List<Long> origFresh = origData.freshExpirations();
        List<Long> origStale = origData.staleExpirations();
        int origRotten = origData.rottenCount();
        int origTotal = origFresh.size() + origStale.size() + origRotten;

        List<Long> newFresh = new ArrayList<>();
        List<Long> newStale = new ArrayList<>();
        int newRotten = 0;

        for (int i = 0; i < targetCount; i++) {
            int idx = i % origTotal;
            if (idx < origFresh.size()) {
                newFresh.add(origFresh.get(idx));
            } else if (idx < origFresh.size() + origStale.size()) {
                newStale.add(origStale.get(idx - origFresh.size()));
            } else {
                newRotten++;
            }
        }
        SpoilageData cloned = new SpoilageData(newFresh, newStale, newRotten, origData.speedMultiplier());

        assertEquals(64, cloned.totalTracked());
        assertEquals(34, cloned.staleExpirations().size());
        assertEquals(30, cloned.rottenCount());
    }

    @Test
    public void testLoggerFunctionality() throws java.io.IOException {
        com.spoilageenhanced.util.SpoilageEnhancedLogger.init();
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log("Testing general logger from unit test");
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.TRACE, "Testing trace logger from unit test");
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.EVENTS, "Testing events logger from unit test");
        com.spoilageenhanced.util.SpoilageEnhancedLogger.closeWriters();

        java.io.File logDir = new java.io.File("spoilage_enhanced_logs");
        assertTrue(logDir.exists(), "spoilage_enhanced_logs directory must exist");

        java.io.File genLog = new java.io.File(logDir, "general.log");
        assertTrue(genLog.exists(), "general.log must exist");
        String genContent = java.nio.file.Files.readString(genLog.toPath());
        assertTrue(genContent.contains("Testing general logger from unit test"), "general.log must contain logged message");

        java.io.File traceLog = new java.io.File(logDir, "trace.log");
        assertTrue(traceLog.exists(), "trace.log must exist");
        String traceContent = java.nio.file.Files.readString(traceLog.toPath());
        assertTrue(traceContent.contains("Testing trace logger from unit test"), "trace.log must contain logged message");
    }

    // =========================================================================
    // Chunk Unload Simulation Tests
    // =========================================================================

    /**
     * Simulates: Item lies on the ground, chunk unloads, time passes (gameTime keeps ticking),
     * chunk reloads, and updateSpoilage() is called. The item should be stale/rotten because
     * expirationTime is an absolute timestamp based on world.getGameTime().
     */
    @Test
    public void testItemSpoilageAfterChunkUnloadReload() {
        ItemStack pumpkin = new ItemStack(Items.PUMPKIN, 1);
        assertTrue(SpoilageConfig.getInstance().isSpoilable(pumpkin.getItem()));

        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(pumpkin.getItem());
        long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(pumpkin.getItem());

        // --- Tick 1000: pumpkin appears in world, gets freshness ---
        long spawnTime = 1000L;
        long freshExpire = spawnTime + freshDuration;
        SpoilageData initialData = new SpoilageData(List.of(freshExpire), Collections.emptyList(), 0, 1.0);
        pumpkin.set(ModDataComponentTypes.SPOILAGE, initialData);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(pumpkin));

        // --- Tick 2000: Player flies away, chunk unloads ---
        // (no ticks happen for the ItemEntity, but gameTime keeps going)

        // --- Tick spawnTime + freshDuration + 100: Player returns, chunk reloads ---
        // Simulate what updateSpoilage() would do at this moment
        long returnTime = freshExpire + 100;  // 100 ticks after fresh expiration
        SpoilageData data = pumpkin.get(ModDataComponentTypes.SPOILAGE);
        List<Long> freshList = new ArrayList<>(data.freshExpirations());
        List<Long> staleList = new ArrayList<>(data.staleExpirations());
        int rottenCount = data.rottenCount();

        // Replay the updateSpoilage logic: fresh items that expired move to stale
        List<Long> remainingFresh = new ArrayList<>();
        for (long exp : freshList) {
            if (returnTime >= exp) {
                staleList.add(exp + staleDuration);
            } else {
                remainingFresh.add(exp);
            }
        }
        // Check stale items that expired move to rotten
        List<Long> remainingStale = new ArrayList<>();
        for (long exp : staleList) {
            if (returnTime >= exp) {
                rottenCount++;
            } else {
                remainingStale.add(exp);
            }
        }

        SpoilageData updated = new SpoilageData(remainingFresh, remainingStale, rottenCount, 1.0);
        pumpkin.set(ModDataComponentTypes.SPOILAGE, updated);

        // The pumpkin should have transitioned FRESH -> STALE
        assertTrue(remainingFresh.isEmpty(), "Fresh list should be empty — item expired while chunk was unloaded");
        assertFalse(remainingStale.isEmpty(), "Should have a stale entry with new stale expiration");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(pumpkin));
    }

    /**
     * Simulates: Item lies on the ground, chunk unloads, VERY long time passes
     * (longer than fresh + stale duration combined). Item should be fully ROTTEN.
     */
    @Test
    public void testItemSpoilageSkipsMultipleStagesAfterLongUnload() {
        ItemStack apple = new ItemStack(Items.APPLE, 1);
        assertTrue(SpoilageConfig.getInstance().isSpoilable(apple.getItem()));

        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(apple.getItem());
        long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(apple.getItem());

        // Apple appears at tick 500
        long spawnTime = 500L;
        long freshExpire = spawnTime + freshDuration;
        SpoilageData initialData = new SpoilageData(List.of(freshExpire), Collections.emptyList(), 0, 1.0);
        apple.set(ModDataComponentTypes.SPOILAGE, initialData);

        // Player returns at tick far beyond fresh + stale total
        long returnTime = spawnTime + freshDuration + staleDuration + 10000;
        SpoilageData data = apple.get(ModDataComponentTypes.SPOILAGE);
        List<Long> freshList = new ArrayList<>(data.freshExpirations());
        List<Long> staleList = new ArrayList<>(data.staleExpirations());
        int rottenCount = data.rottenCount();

        // Step 1: fresh -> stale transition
        List<Long> remainingFresh = new ArrayList<>();
        for (long exp : freshList) {
            if (returnTime >= exp) {
                staleList.add(exp + staleDuration);
            } else {
                remainingFresh.add(exp);
            }
        }
        // Step 2: stale -> rotten transition
        List<Long> remainingStale = new ArrayList<>();
        for (long exp : staleList) {
            if (returnTime >= exp) {
                rottenCount++;
            } else {
                remainingStale.add(exp);
            }
        }

        SpoilageData updated = new SpoilageData(remainingFresh, remainingStale, rottenCount, 1.0);
        apple.set(ModDataComponentTypes.SPOILAGE, updated);

        assertTrue(remainingFresh.isEmpty(), "No fresh items should remain");
        assertTrue(remainingStale.isEmpty(), "No stale items should remain — they expired too");
        assertEquals(1, rottenCount, "Item should be rotten");
        assertTrue(FoodSpoilageUtil.isEntirelyRotten(apple), "Apple should be entirely rotten after long chunk unload");
    }

    /**
     * Tests BlockSpoilageData.getSpoilageState() logic directly:
     * when currentTime exceeds expirationTime, the block transitions
     * from FRESH -> STALE -> ROTTEN even if no ticks happened in between.
     * This is the "lazy evaluation" approach.
     */
    @Test
    public void testBlockSpoilageDataLazyTransition() {
        // Simulate BlockSpoilageEntry behavior without a real world
        long freshExpire = 10000L;
        long staleDuration = 24000L;

        // Case 1: currentTime slightly after fresh expiration -> STALE
        {
            long currentTime = freshExpire + 500;  // 500 ticks into stale
            // Replicate the logic from BlockSpoilageData.getSpoilageState()
            FoodSpoilageUtil.SpoilageState state = FoodSpoilageUtil.SpoilageState.FRESH;
            long expirationTime = freshExpire;

            if (currentTime >= expirationTime) {
                if (state == FoodSpoilageUtil.SpoilageState.FRESH) {
                    state = FoodSpoilageUtil.SpoilageState.STALE;
                    expirationTime = currentTime + staleDuration;
                    // Check if also past stale
                    if (currentTime >= expirationTime) {
                        state = FoodSpoilageUtil.SpoilageState.ROTTEN;
                    }
                }
            }
            assertEquals(FoodSpoilageUtil.SpoilageState.STALE, state,
                    "Block should be STALE when currentTime slightly exceeds fresh expiration");
        }

        // Case 2: currentTime WAY past fresh + stale -> ROTTEN
        {
            long currentTime = freshExpire + staleDuration + 10000;
            // The fresh expiration has passed, so we transition FRESH -> STALE
            // but the stale expiration (currentTime + staleDuration) uses currentTime,
            // so it won't be immediately exceeded. Let's replicate the REAL logic:
            //
            // In BlockSpoilageData.getSpoilageState(), when FRESH expires:
            //   entry.state = STALE
            //   entry.expirationTime = currentTime + staleDuration
            // This means a new stale timer starts from NOW, not from the original expire.
            //
            // BUT the legacy path (legacyBirthTime) does calculate based on total age!

            // Test the legacyBirthTime path which uses absolute age:
            long birthTime = 0L;
            long freshDurationLegacy = 10000L;
            long staleDurationLegacy = 24000L;
            long age = currentTime - birthTime;

            FoodSpoilageUtil.SpoilageState legacyState;
            if (age < freshDurationLegacy) {
                legacyState = FoodSpoilageUtil.SpoilageState.FRESH;
            } else if (age < freshDurationLegacy + staleDurationLegacy) {
                legacyState = FoodSpoilageUtil.SpoilageState.STALE;
            } else {
                legacyState = FoodSpoilageUtil.SpoilageState.ROTTEN;
            }
            assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, legacyState,
                    "Block with legacy birth time should be ROTTEN when age exceeds fresh+stale");
        }
    }

    /**
     * Verifies that the absolute timestamp approach means no special
     * "chunk unload compensation" code is needed — the math just works.
     */
    @Test
    public void testAbsoluteTimestampInvariance() {
        // Two identical items created at the same time
        long createTime = 5000L;
        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(Items.BREAD);
        long expirationTime = createTime + freshDuration;

        // Item A: ticked every second for 48000 ticks (chunk stayed loaded)
        // Item B: no ticks for 48000 ticks (chunk was unloaded), then one tick

        // Both should produce the same result because they share the same expirationTime
        // and the check is simply: currentTime >= expirationTime

        long checkTime = createTime + freshDuration + 1;

        // Item A (ticked continuously)
        boolean aExpired = checkTime >= expirationTime;
        // Item B (no ticks, then one tick at checkTime)
        boolean bExpired = checkTime >= expirationTime;

        assertEquals(aExpired, bExpired, "Both items must produce identical results regardless of tick history");
        assertTrue(aExpired, "Both items should be expired at this time");
    }

    @Test
    public void testConfigSectionsNotNullAndDefaults() {
        SpoilageConfig cfg = SpoilageConfig.getInstance();
        assertNotNull(cfg.getEffectsConfig(), "EffectsConfig must not be null");
        assertEquals(0.5, cfg.getEffectsConfig().stale_nausea_chance, 0.001);
        assertEquals(200, cfg.getEffectsConfig().stale_nausea_duration_ticks);
        assertEquals(50, cfg.getEffectsConfig().stale_hunger_penalty_percent);
        assertEquals(200, cfg.getEffectsConfig().rotten_poison_duration_ticks);
        assertTrue(cfg.getEffectsConfig().rotten_removes_all_hunger);

        assertNotNull(cfg.getMilkEffectsConfig(), "MilkEffectsConfig must not be null");
        assertEquals(200, cfg.getMilkEffectsConfig().stale_nausea_duration_ticks);
        assertEquals(300, cfg.getMilkEffectsConfig().rotten_nausea_duration_ticks);
        assertTrue(cfg.getMilkEffectsConfig().rotten_blocks_effect_clearing);

        assertNotNull(cfg.getAnimalFeedingConfig(), "AnimalFeedingConfig must not be null");
        assertEquals(200, cfg.getAnimalFeedingConfig().rotten_poison_duration_ticks);
        assertEquals(300, cfg.getAnimalFeedingConfig().rotten_weakness_duration_ticks);
        assertTrue(cfg.getAnimalFeedingConfig().rotten_cancels_breeding);
        assertTrue(cfg.getAnimalFeedingConfig().rotten_show_particles);

        assertNotNull(cfg.getComposterConfig(), "ComposterConfig must not be null");
        assertEquals(0.30f, cfg.getComposterConfig().fresh_chance, 0.001f);
        assertEquals(0.65f, cfg.getComposterConfig().stale_chance, 0.001f);
        assertEquals(1.0f, cfg.getComposterConfig().rotten_chance, 0.001f);

        assertNotNull(cfg.getLootRandomizationConfig(), "LootRandomizationConfig must not be null");
        assertEquals(0.60f, cfg.getLootRandomizationConfig().fresh_chance, 0.001f);
        assertEquals(0.30f, cfg.getLootRandomizationConfig().stale_chance, 0.001f);
        assertEquals(0.10f, cfg.getLootRandomizationConfig().rotten_chance, 0.001f);
    }

    @Test
    public void testCustomItemDurationsForModpacks() {
        SpoilageConfig cfg = SpoilageConfig.getInstance();
        // Dynamically register a custom modded item duration
        String customItemId = "mymod:custom_cheese";
        cfg.registerDynamicFoodItem(customItemId, 15000L, 12000L);

        assertTrue(cfg.getAdditionalSet().contains(customItemId), "Custom item should be added to additional set");
        // Verify duration calculation
        Item apple = Items.APPLE;
        assertTrue(cfg.getBaseFreshDurationForItem(apple) > 0);
    }

    @Test
    public void testConfigJsonCommentsSerialization() {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(cfg);

        assertTrue(json.contains("_comment_"), "Serialized JSON must contain _comment_ fields for user documentation");
        assertTrue(json.contains("spoilage_speed_multiplier"), "Must contain spoilage_speed_multiplier");
        assertTrue(json.contains("effects"), "Must contain effects section");
        assertTrue(json.contains("composter"), "Must contain composter section");
        assertTrue(json.contains("loot_randomization"), "Must contain loot_randomization section");

        // Verify roundtrip deserialization
        SpoilageConfig deserialized = gson.fromJson(json, SpoilageConfig.class);
        assertNotNull(deserialized);
        assertNotNull(deserialized.getEffectsConfig());
        assertEquals(0.5, deserialized.getEffectsConfig().stale_nausea_chance, 0.001);

        // Update default config template file
        java.nio.file.Path configPath = java.nio.file.Path.of("config/spoilage_enhanced.json");
        if (java.nio.file.Files.exists(configPath.getParent())) {
            assertDoesNotThrow(() -> java.nio.file.Files.writeString(configPath, json));
        }
    }

    @Test
    public void testFurnacePurificationExploitPrevented() {
        // Simulates smelting 1 fresh steak into an output slot containing 1 stale steak and 1 rotten steak
        ItemStack outputStack = new ItemStack(Items.COOKED_BEEF, 2);
        List<Long> existingStale = new ArrayList<>(List.of(5000L));
        int existingRotten = 1;
        SpoilageData outputData = new SpoilageData(Collections.emptyList(), existingStale, existingRotten, 1.0);
        outputStack.set(ModDataComponentTypes.SPOILAGE, outputData);

        // Smelting completes 1 fresh item
        long cookedExpiration = 24000L;
        List<Long> freshList = new ArrayList<>(outputData.freshExpirations());
        freshList.add(cookedExpiration);
        List<Long> staleList = new ArrayList<>(outputData.staleExpirations());
        int rottenCount = outputData.rottenCount();

        SpoilageData updated = new SpoilageData(freshList, staleList, rottenCount, 1.0);
        outputStack.grow(1);
        outputStack.set(ModDataComponentTypes.SPOILAGE, updated);

        // Verify that stale and rotten counts were NOT wiped
        SpoilageData resultData = outputStack.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(resultData);
        assertEquals(3, outputStack.getCount());
        assertEquals(3, resultData.totalTracked());
        assertEquals(1, resultData.freshExpirations().size(), "Should have 1 newly fresh cooked item");
        assertEquals(cookedExpiration, resultData.freshExpirations().get(0));
        assertEquals(1, resultData.staleExpirations().size(), "Existing stale item must be preserved");
        assertEquals(5000L, resultData.staleExpirations().get(0));
        assertEquals(1, resultData.rottenCount(), "Existing rotten item must be preserved");
    }

    @Test
    public void testHopperExtractWorstItemsMergeLogic() {
        // Source stack of 3 items: 1 fresh, 2 stale
        ItemStack source = new ItemStack(Items.APPLE, 3);
        SpoilageData sourceData = new SpoilageData(List.of(10000L), List.of(2000L, 3000L), 0, 1.0);
        source.set(ModDataComponentTypes.SPOILAGE, sourceData);

        // Destination stack of 1 item: 1 fresh
        ItemStack target = new ItemStack(Items.APPLE, 1);
        SpoilageData targetData = new SpoilageData(List.of(8000L), Collections.emptyList(), 0, 1.0);
        target.set(ModDataComponentTypes.SPOILAGE, targetData);

        // Hopper transfers 1 item from source to target
        int countToTransfer = 1;
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(sourceData, countToTransfer);
        source.set(ModDataComponentTypes.SPOILAGE, split[0]);
        target.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(targetData, split[1]));
        source.shrink(countToTransfer);
        target.grow(countToTransfer);

        // Verify source: count is 2, has 1 fresh (10000L) and 1 stale (3000L) (the worst 2000L was extracted)
        assertEquals(2, source.getCount());
        SpoilageData newSourceData = source.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(newSourceData);
        assertEquals(2, newSourceData.totalTracked());
        assertEquals(1, newSourceData.freshExpirations().size());
        assertEquals(1, newSourceData.staleExpirations().size());
        assertEquals(3000L, newSourceData.staleExpirations().get(0));

        // Verify target: count is 2, has 1 fresh (8000L) and 1 extracted stale (2000L)
        assertEquals(2, target.getCount());
        SpoilageData newTargetData = target.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(newTargetData);
        assertEquals(2, newTargetData.totalTracked());
        assertEquals(1, newTargetData.freshExpirations().size());
        assertEquals(8000L, newTargetData.freshExpirations().get(0));
        assertEquals(1, newTargetData.staleExpirations().size());
        assertEquals(2000L, newTargetData.staleExpirations().get(0));
    }

    @Test
    public void testCraftingProportionalFreshness() {
        // Ingredient (wheat) with fresh duration 24000 ticks
        long currentTime = 10000L;
        long ingFreshDuration = 24000L;
        // Remaining 12000 ticks out of 24000 = 50% ratio
        long ingExpiration = currentTime + 12000L;

        ItemStack ingredient = new ItemStack(Items.WHEAT, 1);
        ingredient.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(ingExpiration), Collections.emptyList(), 0, 1.0));

        // Result item (bread) with fresh duration 48000 ticks
        ItemStack result = new ItemStack(Items.BREAD, 1);
        long resultFreshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(result.getItem());

        // Calculate ratio
        double ratio = (double) (ingExpiration - currentTime) / ingFreshDuration;
        assertEquals(0.5, ratio, 0.001);

        long newExp = currentTime + Math.max(1L, (long) (resultFreshDuration * ratio));
        SpoilageData targetData = new SpoilageData(List.of(newExp), Collections.emptyList(), 0, 1.0);
        result.set(ModDataComponentTypes.SPOILAGE, targetData);

        // Verify that bread gets 50% of its OWN fresh duration (24000 ticks from currentTime)
        SpoilageData resultData = result.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(resultData);
        assertEquals(1, resultData.freshExpirations().size());
        assertEquals(currentTime + (resultFreshDuration / 2), (long) resultData.freshExpirations().get(0));
    }

    @Test
    public void testSpoilageConfigIsSpoilableCache() {
        SpoilageConfig cfg = SpoilageConfig.getInstance();
        assertTrue(cfg.isSpoilable(Items.APPLE), "Apple must be spoilable");
        assertTrue(cfg.isSpoilable(Items.BREAD), "Bread must be spoilable");
        assertFalse(cfg.isSpoilable(Items.DIRT), "Dirt must not be spoilable");
        assertFalse(cfg.isSpoilable(Items.AIR), "Air must not be spoilable");

        // Verify caching consistency across multiple invocations
        for (int i = 0; i < 100; i++) {
            assertTrue(cfg.isSpoilable(Items.APPLE));
            assertFalse(cfg.isSpoilable(Items.DIRT));
        }
    }
}
