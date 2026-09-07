package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.RecipeScanner;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Runs the recipe/food scan on the first server tick after recipes load.
 *
 * <p>The scan used to run straight from {@code RecipeManager.apply}, where item components and
 * item tags are still unbound — every recipe result threw "Components not bound yet" and every
 * tag-based ingredient threw "Trying to access unbound tag", so the scanner silently discovered
 * nothing at all. By the first tick both are bound.</p>
 */
@Mixin(MinecraftServer.class)
public class ServerTickScanMixin {

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void spoilage_enhanced_runPendingScan(BooleanSupplier haveTime, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        RecipeScanner.runPendingScan(server.getRecipeManager());
    }
}
