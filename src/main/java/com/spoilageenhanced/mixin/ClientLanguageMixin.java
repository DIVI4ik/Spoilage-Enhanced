package com.spoilageenhanced.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {

    @Shadow
    private Map<String, String> storage;

    @Invoker("<init>")
    private static ClientLanguage spoilage_enhanced$create(Map<String, String> storage, boolean defaultRightToLeft) {
        throw new AssertionError();
    }

    @Inject(method = "loadFrom", at = @At("RETURN"), cancellable = true)
    private static void onLoadFrom(ResourceManager resourceManager, List<String> languages, boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        // Pass 110: a language (re)load invalidates memoized formatTime strings.
        com.spoilageenhanced.util.SpoilageEnhancedTranslations.clearFormatTimeCache();
        // Pass 186 (Lens 12): also invalidate the HUD text cache — the cached width was
        // measured in the old language and goes stale, mis-centering the HUD line.
        com.spoilageenhanced.client.HudTextCache.clear();
        // Pass 187 (Lens 5): also invalidate the tooltip text cache.
        com.spoilageenhanced.client.TooltipTextCache.clear();
        ClientLanguage original = cir.getReturnValue();
        if (original != null) {
            Map<String, String> combined = new HashMap<>(((ClientLanguageMixin) (Object) original).storage);

            // 1. Always load en_us as fallback
            try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream("/assets/spoilage_enhanced/lang/en_us.json")) {
                if (stream != null) {
                    Language.loadFromJson(stream, combined::put);
                }
            } catch (Exception e) {
                // Pass 627 (Lens 1 — silent failure): the old catch swallowed parse errors
                // for the fallback language. If en_us.json is malformed or missing, the mod
                // silently falls back to an empty translation map with no signal. Log WARNING
                // so pack authors see the issue.
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "ClientLanguageMixin: failed to load en_us.json fallback language: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            // 2. Load active languages (e.g. ru_ru, etc.)
            for (String code : languages) {
                if ("en_us".equals(code)) continue;
                try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream("/assets/spoilage_enhanced/lang/" + code + ".json")) {
                    if (stream != null) {
                        Language.loadFromJson(stream, combined::put);
                    }
                } catch (Exception e) {
                    // Pass 627 (Lens 1 — silent failure): the old catch swallowed parse errors
                    // for active languages. If a language JSON is malformed, the mod silently
                    // skips it and falls back to en_us with no signal. Log WARNING with the
                    // language code so pack authors can fix the file.
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                            "ClientLanguageMixin: failed to load language '" + code
                            + "': " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            cir.setReturnValue(spoilage_enhanced$create(combined, defaultRightToLeft));
        }
    }
}
