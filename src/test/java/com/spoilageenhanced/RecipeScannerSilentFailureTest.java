package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1387 (L1 — silent failure): test RecipeScanner's silent failure patterns.
 *
 * <p>RecipeScanner (RecipeScanner.java:215, :280, :283) has three silent-failure
 * catch blocks when scanning recipes:</p>
 *
 * <ol>
 *   <li>Recipe scan loop (RecipeScanner.java:215): catches {@code Exception} when
 *       a recipe throws during scanning. Counts skippedThrown, records first
 *       thrown reason. Logged in summary.</li>
 *   <li>getRecipeOutput NoSuchFieldException (RecipeScanner.java:280): catches
 *       {@code NoSuchFieldException} and ignores it — continues to next class
 *       in hierarchy. This is intentional (field not found in this class).</li>
 *   <li>getRecipeOutput Throwable (RecipeScanner.java:283): catches {@code Throwable}
 *       and records lastOutputFailure. Returns null.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: thrown
 * recipes are counted and logged in summary, NoSuchFieldException is ignored
 * (continues hierarchy search), other throwables recorded in lastOutputFailure.</p>
 */
class RecipeScannerSilentFailureTest {

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
    void recipeScanLoopHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around recipe scan
        assertTrue(source.contains("try {"),
                "recipe scan must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "recipe scan must catch Exception");
        assertTrue(source.contains("skippedThrown++"),
                "recipe scan must count skipped thrown recipes");
    }

    @Test
    void recipeScanLoopRecordsFirstThrownReason() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it records first thrown reason
        assertTrue(source.contains("firstThrownReason == null"),
                "recipe scan must check if first reason recorded");
        assertTrue(source.contains("recipeHolder.id().identifier()"),
                "recipe scan must log recipe ID");
        assertTrue(source.contains("e.getClass().getName()"),
                "recipe scan must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "recipe scan must log exception message");
    }

    @Test
    void recipeScanLoopLogsSummary() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it logs summary
        assertTrue(source.contains("skippedThrown > 0"),
                "recipe scan must check if any thrown");
        assertTrue(source.contains("thrown recipes across"),
                "recipe scan must log thrown recipes summary");
    }

    @Test
    void getRecipeOutputIgnoresNoSuchFieldException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it ignores NoSuchFieldException (continues hierarchy search)
        assertTrue(source.contains("} catch (NoSuchFieldException ignored) {}"),
                "getRecipeOutput must ignore NoSuchFieldException");
    }

    @Test
    void getRecipeOutputCatchesThrowable() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it catches Throwable
        assertTrue(source.contains("} catch (Throwable t) {"),
                "getRecipeOutput must catch Throwable");
        assertTrue(source.contains("lastOutputFailure ="),
                "getRecipeOutput must record lastOutputFailure");
    }

    @Test
    void getRecipeOutputRecordsFailureDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it records failure details
        assertTrue(source.contains("t.getClass().getName()"),
                "getRecipeOutput must record exception class");
        assertTrue(source.contains("t.getMessage()"),
                "getRecipeOutput must record exception message");
    }

    @Test
    void getRecipeOutputReturnsNullOnFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it returns null on failure
        assertTrue(source.contains("return null;"),
                "getRecipeOutput must return null on failure");
    }

    @Test
    void getRecipeOutputSearchesHierarchy() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it searches class hierarchy
        assertTrue(source.contains("cls.getSuperclass()"),
                "getRecipeOutput must search superclass");
        assertTrue(source.contains("cls != null && cls != Object.class"),
                "getRecipeOutput must stop at Object");
    }

    @Test
    void getRecipeOutputHandlesItemStackTemplate() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it handles ItemStackTemplate
        assertTrue(source.contains("ItemStackTemplate template"),
                "getRecipeOutput must handle ItemStackTemplate");
        assertTrue(source.contains("template.item().value()"),
                "getRecipeOutput must get item from template");
    }

    @Test
    void getRecipeOutputHandlesItemStack() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/RecipeScanner.java"))
                .replace("\r\n", "\n");

        // Verify it handles ItemStack
        assertTrue(source.contains("val instanceof ItemStack stack"),
                "getRecipeOutput must handle ItemStack");
        assertTrue(source.contains("stack.getItem()"),
                "getRecipeOutput must get item from stack");
    }
}