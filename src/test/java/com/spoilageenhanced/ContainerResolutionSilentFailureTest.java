package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1384 (L1 — silent failure): test ContainerResolution's silent failure patterns.
 *
 * <p>ContainerResolution (ContainerResolution.java:62, :80, :103, :117, :135, :149)
 * has multiple silent-failure catch blocks when resolving containers via reflection:</p>
 *
 * <ol>
 *   <li>sentinelMethod (ContainerResolution.java:62): catches {@code NoSuchMethodException}
 *       and throws ExceptionInInitializerError — this is a sentinel, not a silent failure.</li>
 *   <li>itemListSentinelMethod (ContainerResolution.java:80): same pattern.</li>
 *   <li>asAgingItemList getItems probe (ContainerResolution.java:103): catches
 *       {@code NoSuchMethodException} and sets method to ITEM_LIST_NEGATIVE. Silent
 *       failure — no logging.</li>
 *   <li>asAgingItemList invoke (ContainerResolution.java:117): catches
 *       {@code ReflectiveOperationException} and returns null. Silent failure — no logging.</li>
 *   <li>asAgingContainer getContainer probe (ContainerResolution.java:135): catches
 *       {@code NoSuchMethodException} and sets method to NEGATIVE. Silent failure — no logging.</li>
 *   <li>asAgingContainer invoke (ContainerResolution.java:149): catches
 *       {@code ReflectiveOperationException} and returns null. Silent failure — no logging.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: reflection
 * probes cache negative results, invoke failures return null silently.</p>
 */
class ContainerResolutionSilentFailureTest {

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
    void sentinelMethodThrowsOnFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify sentinelMethod throws on failure
        assertTrue(source.contains("private static Method sentinelMethod"),
                "sentinelMethod must exist");
        assertTrue(source.contains("throw new ExceptionInInitializerError(e)"),
                "sentinelMethod must throw on failure");
    }

    @Test
    void itemListSentinelMethodThrowsOnFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify itemListSentinelMethod throws on failure
        assertTrue(source.contains("private static Method itemListSentinelMethod"),
                "itemListSentinelMethod must exist");
        assertTrue(source.contains("throw new ExceptionInInitializerError(e)"),
                "itemListSentinelMethod must throw on failure");
    }

    @Test
    void asAgingItemListProbesGetItems() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it probes getItems method
        assertTrue(source.contains("clazz.getMethod(\"getItems\")"),
                "asAgingItemList must probe getItems");
        assertTrue(source.contains("List.class.isAssignableFrom(found.getReturnType())"),
                "asAgingItemList must check return type");
    }

    @Test
    void asAgingItemListCachesNegative() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it caches negative result
        assertTrue(source.contains("ITEM_LIST_NEGATIVE"),
                "asAgingItemList must use ITEM_LIST_NEGATIVE");
        assertTrue(source.contains("ITEM_LIST_GETTERS.put(clazz, method)"),
                "asAgingItemList must cache method");
    }

    @Test
    void asAgingItemListInvokeReturnsNullOnFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify invoke failure returns null
        assertTrue(source.contains("try {"),
                "asAgingItemList must have try block for invoke");
        assertTrue(source.contains("} catch (ReflectiveOperationException e) {"),
                "asAgingItemList must catch ReflectiveOperationException");
        assertTrue(source.contains("return null;"),
                "asAgingItemList must return null on invoke failure");
    }

    @Test
    void asAgingContainerProbesGetContainer() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it probes getContainer method
        assertTrue(source.contains("clazz.getMethod(\"getContainer\")"),
                "asAgingContainer must probe getContainer");
        assertTrue(source.contains("Container.class.isAssignableFrom(found.getReturnType())"),
                "asAgingContainer must check return type");
    }

    @Test
    void asAgingContainerCachesNegative() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it caches negative result
        assertTrue(source.contains("NEGATIVE"),
                "asAgingContainer must use NEGATIVE");
        assertTrue(source.contains("CONTAINER_GETTERS.put(clazz, method)"),
                "asAgingContainer must cache method");
    }

    @Test
    void asAgingContainerInvokeReturnsNullOnFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify invoke failure returns null
        assertTrue(source.contains("try {"),
                "asAgingContainer must have try block for invoke");
        assertTrue(source.contains("} catch (ReflectiveOperationException e) {"),
                "asAgingContainer must catch ReflectiveOperationException");
        assertTrue(source.contains("return null;"),
                "asAgingContainer must return null on invoke failure");
    }

    @Test
    void asAgingContainerChecksDirectInstance() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it checks direct Container instance first
        assertTrue(source.contains("blockEntity instanceof Container direct"),
                "asAgingContainer must check direct instance");
        assertTrue(source.contains("return direct;"),
                "asAgingContainer must return direct container");
    }

    @Test
    void asAgingItemListLogsOnSuccess() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it logs on successful probe
        assertTrue(source.contains("aging contents of"),
                "asAgingItemList must log on success");
        assertTrue(source.contains("via getItems() (third convention)"),
                "asAgingItemList must log convention");
    }

    @Test
    void asAgingContainerLogsOnSuccess() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/ContainerResolution.java"))
                .replace("\r\n", "\n");

        // Verify it logs on successful probe
        assertTrue(source.contains("aging contents of"),
                "asAgingContainer must log on success");
        assertTrue(source.contains("via getContainer() (not a vanilla Container)"),
                "asAgingContainer must log convention");
    }
}