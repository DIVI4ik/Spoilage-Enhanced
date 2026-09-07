package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.core.component.DataComponentMap spoilage_enhanced$mushroomFoodComponents;

    /**
     * Built lazily: ItemStack can be initialized before DataComponents/Consumables are,
     * so this must not run inside the class initializer.
     */
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.core.component.DataComponentMap spoilage_enhanced$mushroomFoodComponents() {
        net.minecraft.core.component.DataComponentMap cached = spoilage_enhanced$mushroomFoodComponents;
        if (cached == null) {
            cached = net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.FOOD, new net.minecraft.world.food.FoodProperties(3, 0.6f, false))
                    .set(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumables.DEFAULT_FOOD)
                    .build();
            spoilage_enhanced$mushroomFoodComponents = cached;
        }
        return cached;
    }

    @Shadow
    public abstract Item getItem();

    @Shadow
    public abstract int getCount();

    @Shadow
    public abstract boolean isEmpty();

    @Inject(method = "split", at = @At("RETURN"))
    private void onSplit(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack self = (ItemStack) (Object) this;
        ItemStack result = cir.getReturnValue();

        // Pass 87 (Lens 8/13): the work below gates on current != null, so when the split
        // source has no SPOILAGE component (every non-food split — dispensers, crafters,
        // bundle inserts) nothing happens. Check the component first via the cheaper
        // ItemStack.has() and skip both isSpoilable() CHM gets when no data is present.
        if (!self.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (SpoilageConfig.getInstance().isSpoilable(self.getItem())
                && SpoilageConfig.getInstance().isSpoilable(result.getItem())) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "ItemStackMixin: split authorized mutation. Amount extracted: " + result.getCount());

            SpoilageData current = self.get(ModDataComponentTypes.SPOILAGE);
            if (current != null) {
                SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(current, result.getCount());
                self.set(ModDataComponentTypes.SPOILAGE, split[0]);
                result.set(ModDataComponentTypes.SPOILAGE, split[1]);
            }
        }
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void onFinishUsingItem(net.minecraft.world.level.Level world, net.minecraft.world.entity.LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        // Pass 87 (Lens 8/13): the work below returns early when data == null, so when the
        // consumed stack has no SPOILAGE component (every non-food item use — potions, tools,
        // buckets) nothing happens. Check the component first via the cheaper ItemStack.has()
        // and skip the isSpoilable() CHM get when no data is present.
        if (world.isClientSide() || !stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
            SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
            if (data == null) return;

            // Extract the 1 WORST item that was just consumed (player eats oldest first)
            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
            SpoilageData consumedItemData = split[1];
            SpoilageData remainingData = split[0];

            // Update remaining stack with remaining spoilage data
            stack.set(ModDataComponentTypes.SPOILAGE, remainingData);

            ItemStack resultStack = cir.getReturnValue();
            if (resultStack != null && resultStack != stack && !resultStack.isEmpty()
                    && SpoilageConfig.getInstance().isSpoilable(resultStack.getItem())) {
                resultStack.set(ModDataComponentTypes.SPOILAGE, remainingData);
            }

            net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            SpoilageConfig.EffectsConfig fx = SpoilageConfig.getInstance().getEffectsConfig();

            if (!consumedItemData.freshExpirations().isEmpty()) {
                // Fresh food eaten - normal hunger and no debuff
            } else if (!consumedItemData.staleExpirations().isEmpty()) {
                if (entity instanceof net.minecraft.world.entity.player.Player player && food != null) {
                    int penalty = (int) (food.nutrition() * fx.stale_hunger_penalty_percent / 100.0);
                    float satPenalty = (float) (food.saturation() * fx.stale_saturation_penalty_percent / 100.0);
                    player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - penalty));
                    player.getFoodData().setSaturation(Math.max(0f, player.getFoodData().getSaturationLevel() - satPenalty));
                }
                if (world.getRandom().nextFloat() < (float) fx.stale_nausea_chance) {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, fx.stale_nausea_duration_ticks, 0));
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "Player consumed Stale food -> Nausea applied.");
                }
            } else if (consumedItemData.rottenCount() > 0) {
                if (entity instanceof net.minecraft.world.entity.player.Player player && food != null) {
                    if (fx.rotten_removes_all_hunger) {
                        player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - food.nutrition()));
                        player.getFoodData().setSaturation(Math.max(0f, player.getFoodData().getSaturationLevel() - food.saturation()));
                    }
                }
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, fx.rotten_poison_duration_ticks, 0));
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "Player consumed Rotten food -> Poison applied.");
            }
        }
    }

    @Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
    private static void onIsSameItemSameComponents(ItemStack left, ItemStack right, CallbackInfoReturnable<Boolean> cir) {
        // Pass 92 (Lens 8): this mixin fires on every stack comparison (hopper transfers,
        // inventory adds, shift-clicks, drags). When NEITHER stack carries the SPOILAGE
        // component, vanilla's Objects.equals(components) is already correct — the loop below
        // would be pure overhead. Only run the ignore-SPOILAGE comparison when at least one
        // stack actually has spoilage data to ignore.
        if (!left.hasNonDefault(ModDataComponentTypes.SPOILAGE) && !right.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (!left.isEmpty() && !right.isEmpty()
                && SpoilageConfig.getInstance().isSpoilable(left.getItem())
                && SpoilageConfig.getInstance().isSpoilable(right.getItem())) {
            if (left.getItem() == right.getItem()) {
                boolean match = true;
                for (net.minecraft.core.component.DataComponentType<?> type : left.getComponents().keySet()) {
                    if (type == ModDataComponentTypes.SPOILAGE) continue;
                    if (!java.util.Objects.equals(left.get(type), right.get(type))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    for (net.minecraft.core.component.DataComponentType<?> type : right.getComponents().keySet()) {
                        if (type == ModDataComponentTypes.SPOILAGE) continue;
                        if (!java.util.Objects.equals(left.get(type), right.get(type))) {
                            match = false;
                            break;
                        }
                    }
                }
                if (match) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "getComponents", at = @At("RETURN"), cancellable = true)
    private void onGetComponents(CallbackInfoReturnable<net.minecraft.core.component.DataComponentMap> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isEmpty()) {
            Item item = self.getItem();
            if (item == net.minecraft.world.item.Items.RED_MUSHROOM || item == net.minecraft.world.item.Items.BROWN_MUSHROOM) {
                // getComponents() is the full-map view (tooltips, serialization, creative
                // tab comparisons) — NOT the per-component get(type) hot path, which reads
                // the components field directly. The mushroom map is built once (static
                // cache); composite() here wraps the per-stack vanilla map and only runs
                // on this low-frequency path.
                cir.setReturnValue(net.minecraft.core.component.DataComponentMap.composite(
                        spoilage_enhanced$mushroomFoodComponents(),
                        cir.getReturnValue()
                ));
            }
        }
    }
}
