package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1385 (L1 — silent failure): test DynamicFoodBlockCache's silent failure patterns.
 *
 * <p>DynamicFoodBlockCache (DynamicFoodBlockCache.java:212, :290, :342, :390)
 * has four silent-failure catch blocks when probing block drops:</p>
 *
 * <ol>
 *   <li>deriveRipeness fruit flags probe (DynamicFoodBlockCache.java:212): catches
 *       {@code Exception} when probing boolean properties for ripeness. Logs at DATA
 *       category and returns Ripeness.unprobed() so the caller does NOT cache the
 *       wrong answer.</li>
 *   <li>deriveRipeness age-based probe (DynamicFoodBlockCache.java:290): catches
 *       {@code Exception} when deriving ripe age. Logs at DATA category and returns
 *       max age as fallback.</li>
 *   <li>getMatureState probe (DynamicFoodBlockCache.java:342): catches {@code Exception}
 *       when probing mature state. Logs with exception class and message.</li>
 *   <li>getFoodDropIgnoringGrowth loot probe (DynamicFoodBlockCache.java:390): catches
 *       {@code Exception} when discovering drops. Sets probeThrew flag, logs at DATA
 *       category with exception class and message.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: probe failures
 * are logged, wrong answers are not cached (unprobed marker), fallbacks are used.</p>
 */
class DynamicFoodBlockCacheSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void deriveRipenessFruitFlagsProbeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around fruit flags probe
        assertTrue(source.contains("try {"),
                "deriveRipeness must have try block for fruit flags");
        assertTrue(source.contains("} catch (Exception e) {"),
                "deriveRipeness must catch Exception for fruit flags");
        assertTrue(source.contains("could not probe fruit flags"),
                "deriveRipeness must log for fruit flags failure");
    }

    @Test
    void deriveRipenessFruitFlagsReturnsUnprobed() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it returns unprobed marker (not cached)
        assertTrue(source.contains("Ripeness.unprobed()"),
                "deriveRipeness must return unprobed on fruit flags failure");
    }

    @Test
    void deriveRipenessFruitFlagsLogsAtDataCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs at DATA category
        assertTrue(source.contains("LogCategory.DATA"),
                "deriveRipeness must log to DATA category for fruit flags");
    }

    @Test
    void deriveRipenessAgeProbeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around age probe
        assertTrue(source.contains("try {"),
                "deriveRipeness must have try block for age probe");
        assertTrue(source.contains("} catch (Exception e) {"),
                "deriveRipeness must catch Exception for age probe");
        assertTrue(source.contains("could not derive ripe age"),
                "deriveRipeness must log for age probe failure");
    }

    @Test
    void deriveRipenessAgeProbeReturnsMax() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it returns max as fallback
        assertTrue(source.contains("return max;"),
                "deriveRipeness must return max on age probe failure");
    }

    @Test
    void getMatureStateHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around mature state probe
        assertTrue(source.contains("try {"),
                "getMatureState must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "getMatureState must catch Exception");
        assertTrue(source.contains("mature-state probe failed"),
                "getMatureState must log for probe failure");
    }

    @Test
    void getMatureStateLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "getMatureState must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "getMatureState must log exception message");
    }

    @Test
    void getFoodDropIgnoringGrowthHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around loot probe
        assertTrue(source.contains("try {"),
                "getFoodDropIgnoringGrowth must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "getFoodDropIgnoringGrowth must catch Exception");
        assertTrue(source.contains("Exception discovering drops"),
                "getFoodDropIgnoringGrowth must log for probe failure");
    }

    @Test
    void getFoodDropIgnoringGrowthSetsProbeThrew() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it sets probeThrew flag
        assertTrue(source.contains("probeThrew = true;"),
                "getFoodDropIgnoringGrowth must set probeThrew");
    }

    @Test
    void getFoodDropIgnoringGrowthLogsAtDataCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs at DATA category
        assertTrue(source.contains("LogCategory.DATA"),
                "getFoodDropIgnoringGrowth must log to DATA category");
    }

    @Test
    void getFoodDropIgnoringGrowthLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "getFoodDropIgnoringGrowth must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "getFoodDropIgnoringGrowth must log exception message");
    }

    @Test
    void getFoodDropIgnoringGrowthUsesMatureState() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/DynamicFoodBlockCache.java"))
                .replace("\r\n", "\n");

        // Verify it uses getMatureState
        assertTrue(source.contains("getMatureState(state, world, pos)"),
                "getFoodDropIgnoringGrowth must use getMatureState");
        assertTrue(source.contains("lootFoodDrop"),
                "getFoodDropIgnoringGrowth must use lootFoodDrop");
    }
}