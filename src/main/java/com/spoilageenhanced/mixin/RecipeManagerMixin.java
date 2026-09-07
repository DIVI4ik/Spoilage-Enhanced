package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.RecipeScanner;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
    private void onRecipesLoaded(RecipeMap map, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        // Clear all caches on datapack/recipe reload to prevent stale entries
        DynamicFoodBlockCache.clear();
        SpoilageConfig.getInstance().clearCache(); // Clears spoilableCache and ITEM_ID_CACHE
        // Deferred to the first server tick: components and tags are not bound yet at this point.
        RecipeScanner.requestScan();
    }
}
