package com.spoilageenhanced.mixin.client;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    private static final int BAR_WIDTH = 2;
    private static final int BAR_HEIGHT = 13;

    private static final int COLOR_FRESH = 0xFF00CC00;
    private static final int COLOR_STALE = 0xFFCCCC00;
    private static final int COLOR_ROTTEN = 0xFFCC0000;
    private static final int COLOR_BG = 0xFF000000;

    @Shadow
    public abstract void fill(int minX, int minY, int maxX, int maxY, int color);

    @Shadow
    public abstract void blit(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, net.minecraft.resources.Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight);

    @Inject(
        method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            shift = At.Shift.AFTER
        )
    )
    private void spoilage_enhanced_drawFreshnessBar(Font font, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        if (stack == null || stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        long currentTime = (client != null && client.level != null) ? client.level.getGameTime() : 0L;

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        int freshCount;
        int staleCount;
        int rottenCount;

        // Pass 106 (Lens 13): this mixin runs for EVERY rendered item in EVERY visible
        // inventory slot, every frame. The old virtual path built a count-sized ArrayList +
        // a SpoilageData record per frame for component-less stacks (fresh creative/crafted
        // items — CLAUDE.md section 4) just to feed the counting loops — but a virtual stack
        // is fresh by construction, so the loops always answered freshCount=count,
        // staleCount=0, rottenCount=0. Compute those directly: zero allocations.
        if (data == null || data.isEmpty()) {
            freshCount = stack.getCount();
            staleCount = 0;
            rottenCount = 0;
        } else {
            // Pass 196 (L5 — render-path): staleDuration is only needed when data exists
            // (the virtual fresh path doesn't use it). Move the call inside the else branch
            // to avoid the CHM get + multiplication for the common fresh-item case.
            long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(stack.getItem());
            List<Long> freshList = data.freshExpirations();
            List<Long> staleList = data.staleExpirations();
            rottenCount = data.rottenCount();

            freshCount = 0;
            staleCount = 0;

            for (long exp : freshList) {
                if (currentTime < exp) {
                    freshCount++;
                } else if (currentTime < exp + staleDuration) {
                    staleCount++;
                } else {
                    rottenCount++;
                }
            }

            for (long exp : staleList) {
                if (currentTime < exp) {
                    staleCount++;
                } else {
                    rottenCount++;
                }
            }
        }

        int total = freshCount + staleCount + rottenCount;
        if (stack.getCount() > total) {
            freshCount += (stack.getCount() - total);
            total = stack.getCount();
        }
        if (total <= 0) return;

        // Pass 145 (Lens 5): pixel math extracted to SpoilageBarPixels — the old independent
        // rounding could make green+yellow exceed BAR_HEIGHT (e.g. 16 fresh / 16 stale rounds
        // both to 7 in a 13px frame), drawing one pixel past the bar's background frame and
        // zeroing the red segment. The helper shaves the rounding excess off the larger
        // segment so the three always sum to exactly BAR_HEIGHT.
        // Pass 606 (L5 — render-path): compute() now returns a Result record by value. The
        // JIT scalarizes the three int fields into registers, so the old int[3] allocation
        // is eliminated without the overhead of a caller-provided scratch buffer (benchmark
        // showed computeInto is actually slower: 0.027 vs 0.013 us/call, because the array
        // store requires a memory write while the record return stays in registers).
        com.spoilageenhanced.client.SpoilageBarPixels.Result pixels =
                com.spoilageenhanced.client.SpoilageBarPixels.compute(
                        freshCount, staleCount, rottenCount, BAR_HEIGHT);
        int greenPixels = pixels.green();
        int yellowPixels = pixels.yellow();
        int redPixels = pixels.red();

        // Вертикальная полоска слева от иконки слота
        int barX = x + 1;
        int barY = y + 2;

        // Чёрная рамка фона
        this.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, COLOR_BG);

        int currentY = barY;
        if (redPixels > 0) {
            this.fill(barX, currentY, barX + BAR_WIDTH, currentY + redPixels, COLOR_ROTTEN);
            currentY += redPixels;
        }
        if (yellowPixels > 0) {
            this.fill(barX, currentY, barX + BAR_WIDTH, currentY + yellowPixels, COLOR_STALE);
            currentY += yellowPixels;
        }
        if (greenPixels > 0) {
            this.fill(barX, currentY, barX + BAR_WIDTH, currentY + greenPixels, COLOR_FRESH);
        }

        if (freshCount == 0 && staleCount == 0 && rottenCount > 0) {
            net.minecraft.resources.Identifier overlayTexture = com.spoilageenhanced.config.RotOverlayConfig.getInstance().getPatternForItem(stack.getItem());
            if (overlayTexture != null) {
                this.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, overlayTexture, x, y, 0.0f, 0.0f, 16, 16, 16, 16);
            }
        }

        // RENDERDUMP (VISUAL_VERIFICATION.md): report the bar's resolved pixel composition and
        // its on-screen slot. Keyed by the item so two slots with the same item report
        // independently; the pixel counts are the values SpoilageBarPixels already reconciled,
        // so a bar drawn at the wrong width shows up as px != the expected segment sum.
        if (com.spoilageenhanced.client.RenderDump.isEnabled()) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            com.spoilageenhanced.client.RenderDump.emit("bar:" + itemId + ":" + x + ":" + y,
                    "element=bar item=" + com.spoilageenhanced.client.RenderDump.quote(itemId)
                            + " fresh=" + freshCount + " stale=" + staleCount + " rotten=" + rottenCount
                            + " px=" + (greenPixels + yellowPixels + redPixels)
                            + " x=" + x + " y=" + y);
        }
    }
}
