package com.spoilageenhanced.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.spoilageenhanced.client.ClientVirtualSpoilageAnchor;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CLIENT-ONLY mixin for ItemStack.getTooltipLines in Minecraft 26.2.
 */
@Mixin(ItemStack.class)
public abstract class ItemClientMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void onGetTooltip(Item.TooltipContext context, @Nullable Player player, TooltipFlag type,
            CallbackInfoReturnable<List<Component>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return;

        // When the tooltip is hidden vanilla returns an immutable list (List.of() or the
        // static op-warning list), so inserting into it would throw. Mirror the same check.
        net.minecraft.world.item.component.TooltipDisplay display =
                stack.getOrDefault(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                        net.minecraft.world.item.component.TooltipDisplay.DEFAULT);
        if (!type.isCreative() && display.hideTooltip())
            return;

        List<Component> tooltip = cir.getReturnValue();
        if (tooltip == null)
            return;

        Level world = player != null ? player.level()
                : (Minecraft.getInstance() != null ? Minecraft.getInstance().level : null);

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        long currentTime = world != null ? world.getGameTime() : 0;
        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(stack.getItem());
        long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(stack.getItem());

        // BUG-15 (ITEM-TIMER-01): a stack without the server component is brand new — the server
        // attaches the real expiration on its first tick. Synthesizing expirationTime = now +
        // freshDuration made the "Spoils in" countdown a frozen constant, because the origin moved
        // forward with the clock and now - now cancels out every frame. Fix is a fixed origin, not
        // a hidden countdown: remember when this stack was first seen and count from there
        // (CLAUDE.md section 4 — the fallback must be anchored to a stable handle).
        //
        // Pass 105 (Lens 13): the old virtual path built a count-sized ArrayList + a SpoilageData
        // record EVERY FRAME just to feed the counting loops below — but a virtual stack is fresh
        // by construction (every entry is firstSeen + freshDuration, all in the future), so the
        // loops would always answer f=count, s=0, r=0 and minTime=firstSeen+freshDuration. Compute
        // those directly and skip the synthesis entirely.
        boolean virtualData = data == null || data.isEmpty();
        int f = 0;
        int s = 0;
        int r;
        long virtualMinTime = Long.MAX_VALUE;
        long minFreshTime = Long.MAX_VALUE;
        long minStaleTime = Long.MAX_VALUE;

        if (virtualData) {
            long firstSeen = ClientVirtualSpoilageAnchor.firstSeen(stack, currentTime);
            virtualMinTime = firstSeen + freshDuration;
            f = stack.getCount();
            s = 0;
            r = 0;
        } else {
            r = data.rottenCount();

            // Pass 121 (Lens 3/UI): the old code allocated activeFresh/activeStale ArrayLists
            // on every tooltip frame just to count and find the minimum expiration. A virtual
            // stack is fresh by construction (Pass 105), so this path only runs for items that
            // already have the server component. Compute counts and min times directly in a
            // single pass over each list — zero allocations per frame.
            for (long exp : data.freshExpirations()) {
                if (exp >= Long.MAX_VALUE) {
                    f++;
                } else if (currentTime >= exp + staleDuration) {
                    r++;
                } else if (currentTime >= exp) {
                    s++;
                    long staleExp = exp + staleDuration;
                    if (staleExp < minStaleTime) minStaleTime = staleExp;
                } else {
                    f++;
                    if (exp < minFreshTime) minFreshTime = exp;
                }
            }
            for (long exp : data.staleExpirations()) {
                if (exp >= Long.MAX_VALUE) {
                    s++;
                } else if (currentTime >= exp) {
                    r++;
                } else {
                    s++;
                    if (exp < minStaleTime) minStaleTime = exp;
                }
            }
        }

        // If stack count is larger than tracked items, assume untracked are fresh
        if (stack.getCount() > f + s + r) {
            f = stack.getCount() - s - r;
        }

        boolean showShiftDetails = false;
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null) {
                showShiftDetails = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
        } catch (Exception e) {
            // Pass 628 (Lens 1 — silent failure): the old catch swallowed every exception,
            // leaving showShiftDetails = false with no signal. The only realistic failure here
            // is a NullPointerException from Minecraft.getInstance() racing during teardown,
            // but the broad catch hid anything else too. Log at WARNING so a real bug in
            // the input layer is visible instead of silently suppressing the shift-detail UI.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "ItemClientMixin: failed to read shift-key state for tooltip: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        long displayedDiff = 0L;
        boolean hasSpoilsInLine = false;
        if (world != null && (f > 0 || s > 0)) {
            long minTime = virtualMinTime;

            if (minFreshTime != Long.MAX_VALUE && minFreshTime < minTime) {
                minTime = minFreshTime;
            }
            if (minStaleTime != Long.MAX_VALUE && minStaleTime < minTime) {
                minTime = minStaleTime;
            }

            if (minTime == Long.MAX_VALUE && f > 0) {
                minTime = currentTime + freshDuration;
            }

            if (minTime != Long.MAX_VALUE) {
                long diff = Math.max(0, minTime - currentTime);
                // Pass 105: for virtual data the old synthesized record carried the config
                // multiplier; read it directly instead of building the record.
                double speedMultiplier = virtualData
                        ? SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()
                        : data.speedMultiplier();
                displayedDiff = (long) (diff * speedMultiplier);
                hasSpoilsInLine = true;
            }
        }

        // Pass 187 (Lens 5 — render-path): cache the tooltip lines per (stack identity,
        // displayedDiff, shiftHeld, stackCount) to avoid allocating new Component.translatable
        // objects every frame while the tooltip is visible. The text only changes when
        // displayedDiff changes (every tick) or shift state changes.
        // Pass 188 (Lens 7 — boundary): the cache key MUST include stackCount — the rendered
        // lines contain count-specific text ("3 fresh", "5 stale"), so two stacks with the same
        // identity hash + displayedDiff + shift but different counts would otherwise share the
        // wrong lines. The original key omitted count (the javadoc claimed it was included).
        // Pass 449 (L5 — render-path): the cache check now runs BEFORE the lines are built.
        // The old order built every Component.translatable first and then discarded them on a
        // cache hit, so the cache saved nothing of what it was built to save — the tooltip
        // runs every frame while visible, and the Component allocations were the cost.
        long cacheKey = com.spoilageenhanced.client.TooltipTextCache.key(
                System.identityHashCode(stack), displayedDiff, stack.getCount(), showShiftDetails);
        com.spoilageenhanced.client.TooltipTextCache.CachedTooltipLines cachedLines =
                com.spoilageenhanced.client.TooltipTextCache.get(cacheKey);
        if (cachedLines != null) {
            List<Component> cached = cachedLines.lines();
            int insertIndex = Math.min(1, tooltip.size());
            if (com.spoilageenhanced.client.RenderDump.isEnabled()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                for (int i = 0; i < cached.size(); i++) {
                    com.spoilageenhanced.client.RenderDump.emit("tooltip:" + itemId + ":" + i,
                            "element=tooltip item=" + com.spoilageenhanced.client.RenderDump.quote(itemId)
                                    + " line=" + i + " text=" + com.spoilageenhanced.client.RenderDump.quote(cached.get(i).getString()));
                }
            }
            tooltip.addAll(insertIndex, cached);
            return;
        }

        List<Component> spoilageLines = new ArrayList<>();
        if (showShiftDetails) {
            spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_STACK_DETAILS).withStyle(ChatFormatting.GOLD));
            if (f > 0)
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_FRESH_COUNT, f).withStyle(ChatFormatting.GREEN));
            if (s > 0)
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_STALE_COUNT, s).withStyle(ChatFormatting.YELLOW));
            if (r > 0)
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_ROTTEN_COUNT, r).withStyle(ChatFormatting.RED));
        } else {
            if (f > 0 && s == 0 && r == 0) {
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_FRESH).withStyle(ChatFormatting.GREEN));
            } else if (f == 0 && s > 0 && r == 0) {
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_STALE_WITH_COUNT, s).withStyle(ChatFormatting.YELLOW));
            } else if (f == 0 && s == 0 && r > 0) {
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_ROTTEN_WITH_COUNT, r).withStyle(ChatFormatting.RED));
            } else {
                // Mixed stack: ONE summary line, naming only the worst state.
                //
                // This branch used to print the whole breakdown - fresh count, stale count,
                // rotten count - which is character for character what the Shift branch above
                // prints. So the composition was visible without holding Shift, and the
                // "[hold Shift for details]" line added right after it promised details that
                // were already on screen.
                //
                // The worst state is the right summary because it is what the rest of the mod
                // acts on: the worst item is the one taken first by slots, hoppers and crafting,
                // and the worst state is what a placed block inherits. No count, or the summary
                // would answer the very question Shift exists to answer.
                if (r > 0) {
                    spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_ROTTEN).withStyle(ChatFormatting.RED));
                } else if (s > 0) {
                    spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_STALE).withStyle(ChatFormatting.YELLOW));
                } else if (f > 0) {
                    spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_FRESH).withStyle(ChatFormatting.GREEN));
                }
            }
            if (stack.getCount() > 1 && ((f > 0 && s > 0) || (f > 0 && r > 0) || (s > 0 && r > 0))) {
                spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_SHIFT_DETAILS).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (hasSpoilsInLine) {
            String timeStr = SpoilageEnhancedTranslations.formatTime(displayedDiff);
            spoilageLines.add(Component.translatable(SpoilageEnhancedTranslations.TOOLTIP_SPOILS_IN, timeStr).withStyle(ChatFormatting.GRAY));
        }

        com.spoilageenhanced.client.TooltipTextCache.put(cacheKey,
                new com.spoilageenhanced.client.TooltipTextCache.CachedTooltipLines(spoilageLines));

        int insertIndex = Math.min(1, tooltip.size());

        // RENDERDUMP (VISUAL_VERIFICATION.md): report each tooltip line the player reads, one
        // line per element, only when the line changes since the last frame. Keyed by the item
        // and the line index so a missing translation shows up as the raw key in text= and a
        // wrong count shows up as the wrong number.
        if (com.spoilageenhanced.client.RenderDump.isEnabled()) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (int i = 0; i < spoilageLines.size(); i++) {
                com.spoilageenhanced.client.RenderDump.emit("tooltip:" + itemId + ":" + i,
                        "element=tooltip item=" + com.spoilageenhanced.client.RenderDump.quote(itemId)
                                + " line=" + i + " text=" + com.spoilageenhanced.client.RenderDump.quote(spoilageLines.get(i).getString()));
            }
        }

        tooltip.addAll(insertIndex, spoilageLines);
    }
}
