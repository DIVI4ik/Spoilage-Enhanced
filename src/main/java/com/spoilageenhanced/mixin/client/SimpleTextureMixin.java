package com.spoilageenhanced.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

@Environment(EnvType.CLIENT)
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin extends ReloadableTexture {

    public SimpleTextureMixin(Identifier resourceId) {
        super(resourceId);
    }

    @Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
    private void spoilage_enhanced$fallbackClasspathLoad(ResourceManager resourceManager, CallbackInfoReturnable<TextureContents> cir) {
        Identifier id = this.resourceId();
        if (id != null && "spoilage_enhanced".equals(id.getNamespace())) {
            if (resourceManager.getResource(id).isEmpty()) {
                String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
                InputStream stream = SimpleTextureMixin.class.getResourceAsStream(path);
                if (stream != null) {
                    try {
                        NativeImage image = NativeImage.read(stream);
                        cir.setReturnValue(new TextureContents(image, null));
                    } catch (IOException e) {
                        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                                "Failed to load fallback texture: " + path + ": " + e.getMessage());
                    } finally {
                        try {
                            stream.close();
                        } catch (IOException ignored) {}
                    }
                }
            }
        }
    }
}
