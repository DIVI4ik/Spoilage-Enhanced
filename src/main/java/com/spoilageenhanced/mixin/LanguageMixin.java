package com.spoilageenhanced.mixin;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.util.function.BiConsumer;

@Mixin(Language.class)
public class LanguageMixin {

    @Inject(method = "parseTranslations", at = @At("TAIL"))
    private static void onParseTranslations(BiConsumer<String, String> output, String path, CallbackInfo ci) {
        if ("/assets/minecraft/lang/en_us.json".equals(path)) {
            try (InputStream stream = LanguageMixin.class.getResourceAsStream("/assets/spoilage_enhanced/lang/en_us.json")) {
                if (stream != null) {
                    Language.loadFromJson(stream, output);
                }
            } catch (Exception e) {
                com.spoilageenhanced.util.SpoilageEnhancedLogger.log("LanguageMixin: failed to load spoilage_enhanced en_us lang: " + e);
            }
        }
    }
}
