package com.spoilageenhanced;

import com.spoilageenhanced.mixin.client.SimpleTextureMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1361 (L1 — silent failure): test SimpleTextureMixin's silent failure pattern.
 *
 * <p>SimpleTextureMixin (SimpleTextureMixin.java:39, :45) catches {@code IOException} when
 * loading fallback textures. If the texture stream fails to read, it logs the failure.
 * The stream close also catches and ignores IOException.</p>
 *
 * <p>What this test pins is that these patterns remain as documented: texture loading
 * errors are caught and logged, stream close errors are ignored.</p>
 */
class SimpleTextureMixinSilentFailureTest {

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
    void textureLoadingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around texture loading
        assertTrue(source.contains("try {"),
                "texture loading must have try block");
        assertTrue(source.contains("} catch (IOException e) {"),
                "texture loading must catch IOException");
        assertTrue(source.contains("Failed to load fallback texture"),
                "texture loading must log for failed texture load");
    }

    @Test
    void textureLoadingUsesNativeImage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses NativeImage.read
        assertTrue(source.contains("NativeImage.read(stream)"),
                "texture loading must use NativeImage.read");
        assertTrue(source.contains("TextureContents"),
                "texture loading must create TextureContents");
    }

    @Test
    void streamCloseHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify stream close has try-catch
        assertTrue(source.contains("finally {"),
                "texture loading must have finally block");
        assertTrue(source.contains("try {"),
                "stream close must have try block");
        assertTrue(source.contains("} catch (IOException ignored) {"),
                "stream close must catch and ignore IOException");
        assertTrue(source.contains("stream.close()"),
                "stream close must call close");
    }
}