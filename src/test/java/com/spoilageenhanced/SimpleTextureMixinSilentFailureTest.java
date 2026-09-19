package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1376 (L1 — silent failure): test SimpleTextureMixin's silent failure pattern.
 *
 * <p>SimpleTextureMixin (SimpleTextureMixin.java:39, :45) has two silent-failure catch
 * blocks when loading fallback textures:</p>
 *
 * <ol>
 *   <li>NativeImage.read (SimpleTextureMixin.java:39): catches {@code IOException} when
 *       reading the texture stream. Logs the failure with the texture path.</li>
 *   <li>stream.close() (SimpleTextureMixin.java:45): catches {@code IOException} and
 *       ignores it in the finally block.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: texture loading
 * failure is logged with path, stream is closed in finally with ignored exception.</p>
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
    void loadFallbackTextureHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around texture loading
        assertTrue(source.contains("try {"),
                "loadFallbackTexture must have try block");
        assertTrue(source.contains("} catch (IOException e) {"),
                "loadFallbackTexture must catch IOException");
        assertTrue(source.contains("Failed to load fallback texture"),
                "loadFallbackTexture must log for failed load");
    }

    @Test
    void loadFallbackTextureLogsPath() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs the texture path
        assertTrue(source.contains("path"),
                "loadFallbackTexture must log the texture path");
        assertTrue(source.contains("e.getMessage()"),
                "loadFallbackTexture must log exception message");
    }

    @Test
    void loadFallbackTextureUsesNativeImage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses NativeImage.read
        assertTrue(source.contains("NativeImage.read(stream)"),
                "loadFallbackTexture must use NativeImage.read");
        assertTrue(source.contains("TextureContents(image, null)"),
                "loadFallbackTexture must create TextureContents");
    }

    @Test
    void loadFallbackTextureHasFinally() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify it has finally block
        assertTrue(source.contains("finally {"),
                "loadFallbackTexture must have finally block");
        assertTrue(source.contains("stream.close()"),
                "loadFallbackTexture must close stream in finally");
    }

    @Test
    void loadFallbackTextureIgnoresCloseException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/client/SimpleTextureMixin.java"))
                .replace("\r\n", "\n");

        // Verify it ignores close exception
        assertTrue(source.contains("} catch (IOException ignored) {}"),
                "loadFallbackTexture must ignore close exception");
    }
}