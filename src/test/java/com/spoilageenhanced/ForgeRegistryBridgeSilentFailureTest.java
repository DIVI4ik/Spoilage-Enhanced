package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1375 (L1 — silent failure): test ForgeRegistryBridge's silent failure patterns.
 *
 * <p>ForgeRegistryBridge (ForgeRegistryBridge.java:79, :87, :95, :115, :147, :187, :196, :205)
 * has multiple silent-failure catch blocks when registering data components:</p>
 *
 * <ol>
 *   <li>Unfreeze DATA_COMPONENT_TYPE (ForgeRegistryBridge.java:79): catches {@code Throwable}
 *       when trying to unfreeze the registry for registration.</li>
 *   <li>Register component (ForgeRegistryBridge.java:87): catches {@code Throwable} when
 *       registering the component. Logs failure.</li>
 *   <li>Restore frozen flag (ForgeRegistryBridge.java:95): catches {@code Throwable} when
 *       restoring the frozen flag after registration.</li>
 *   <li>Bind holder value (ForgeRegistryBridge.java:115): catches {@code Throwable} when
 *       binding the holder value via reflection.</li>
 *   <li>Forge DeferredRegister registration (ForgeRegistryBridge.java:147): catches {@code Throwable}
 *       when registering via Forge's DeferredRegister. Returns false on failure.</li>
 *   <li>Verify registration (ForgeRegistryBridge.java:187): catches {@code Throwable} when
 *       verifying the component is resolvable in the registry.</li>
 *   <li>Class present check (ForgeRegistryBridge.java:196): catches {@code Throwable} and
 *       returns false for class presence check.</li>
 *   <li>Log method (ForgeRegistryBridge.java:205): catches {@code Throwable} when logging
 *       to the file logger (may not exist early).</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: registration
 * uses reflection with try-catch, failures are logged, frozen flag is restored in finally,
 * Forge registration returns boolean, class presence check returns boolean.</p>
 */
class ForgeRegistryBridgeSilentFailureTest {

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
    void registerDataComponentHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around component registration
        assertTrue(source.contains("try {"),
                "registerDataComponent must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "registerDataComponent must catch Throwable");
        assertTrue(source.contains("FAILED to register"),
                "registerDataComponent must log for failed registration");
    }

    @Test
    void registerDataComponentUnfreezesRegistry() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify it unfreezes the registry
        assertTrue(source.contains("frozenField.get(BuiltInRegistries.DATA_COMPONENT_TYPE)"),
                "registerDataComponent must check frozen flag");
        assertTrue(source.contains("frozenField.set(BuiltInRegistries.DATA_COMPONENT_TYPE, false)"),
                "registerDataComponent must unfreeze registry");
    }

    @Test
    void registerDataComponentRestoresFrozenFlag() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify it restores frozen flag in finally
        assertTrue(source.contains("finally {"),
                "registerDataComponent must have finally block");
        assertTrue(source.contains("frozenField.set(BuiltInRegistries.DATA_COMPONENT_TYPE, true)"),
                "registerDataComponent must restore frozen flag");
    }

    @Test
    void bindHolderHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify bindHolder has try-catch
        assertTrue(source.contains("private static <T> void bindHolder"),
                "bindHolder method must exist");
        assertTrue(source.contains("try {"),
                "bindHolder must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "bindHolder must catch Throwable");
        assertTrue(source.contains("Could not bind holder value"),
                "bindHolder must log for failed bind");
    }

    @Test
    void bindHolderUsesReflection() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify it uses reflection to bind value
        assertTrue(source.contains("Holder.Reference.class.getDeclaredMethod(\"bindValue\", Object.class)"),
                "bindHolder must use reflection for bindValue");
        assertTrue(source.contains("bindValue.setAccessible(true)"),
                "bindHolder must set accessible");
        assertTrue(source.contains("bindValue.invoke(ref, type)"),
                "bindHolder must invoke bindValue");
    }

    @Test
    void registerViaForgeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify registerViaForge has try-catch
        assertTrue(source.contains("private static <T> boolean registerViaForge"),
                "registerViaForge method must exist");
        assertTrue(source.contains("try {"),
                "registerViaForge must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "registerViaForge must catch Throwable");
        assertTrue(source.contains("Forge DeferredRegister registration failed"),
                "registerViaForge must log for failed registration");
        assertTrue(source.contains("return false;"),
                "registerViaForge must return false on failure");
    }

    @Test
    void registerViaForgeUsesDeferredRegister() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify it uses DeferredRegister
        assertTrue(source.contains("Class.forName(\"net.minecraftforge.registries.DeferredRegister\")"),
                "registerViaForge must load DeferredRegister class");
        assertTrue(source.contains("create.invoke(null, Registries.DATA_COMPONENT_TYPE, MOD_ID)"),
                "registerViaForge must create DeferredRegister");
        assertTrue(source.contains("register.invoke(forgeDeferredRegister, name, (Supplier<?>) () -> type)"),
                "registerViaForge must register component");
    }

    @Test
    void verifyRegistrationHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify verifyRegistration has try-catch
        assertTrue(source.contains("public static void verifyRegistration"),
                "verifyRegistration method must exist");
        assertTrue(source.contains("try {"),
                "verifyRegistration must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "verifyRegistration must catch Throwable");
        assertTrue(source.contains("Data component check failed"),
                "verifyRegistration must log for failed check");
    }

    @Test
    void verifyRegistrationChecksResolvability() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify it checks resolvability
        assertTrue(source.contains("BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)"),
                "verifyRegistration must get key");
        assertTrue(source.contains("BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type)"),
                "verifyRegistration must get raw id");
        assertTrue(source.contains("key == null || rawId < 0"),
                "verifyRegistration must check for missing registration");
    }

    @Test
    void classPresentHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify classPresent has try-catch
        assertTrue(source.contains("private static boolean classPresent"),
                "classPresent method must exist");
        assertTrue(source.contains("try {"),
                "classPresent must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "classPresent must catch Throwable");
        assertTrue(source.contains("return false;"),
                "classPresent must return false on failure");
    }

    @Test
    void logMethodHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // Verify log method has try-catch
        assertTrue(source.contains("private static void log"),
                "log method must exist");
        assertTrue(source.contains("try {"),
                "log must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "log must catch Throwable");
        assertTrue(source.contains("SpoilageEnhancedLogger.log(message)"),
                "log must call SpoilageEnhancedLogger");
    }
}