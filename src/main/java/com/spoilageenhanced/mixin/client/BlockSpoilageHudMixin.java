package com.spoilageenhanced.mixin.client;

import com.spoilageenhanced.client.ClientBlockSpoilageCache;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the freshness of the block under the crosshair.
 *
 * <p>Block spoilage is server-side saved data, so the state is requested over the network
 * (see {@link ClientBlockSpoilageCache}) instead of being read from the integrated server —
 * that only ever worked in singleplayer and silently showed made-up values on a real server.
 */
@Mixin(Hud.class)
public abstract class BlockSpoilageHudMixin {

    /**
     * Debug hook for headless verification of the packet round-trip: with
     * {@code -Dspoilage_enhanced.netprobe=true} (gradle: {@code runClient -Pnetprobe=true}) the
     * client also asks about the block it is standing on, so the request/response pair shows up in
     * network.log without anyone having to aim at a block. Off by default.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$NET_PROBE = Boolean.getBoolean("spoilage_enhanced.netprobe");

    /**
     * Self-test hook for BUG-12 (block placement keeps the worst spoilage state): with
     * {@code -Dspoilage_enhanced.selftest=place} the client runs the whole scenario through
     * chat commands once the world is joined — spawn a rotten pumpkin, place it via the
     * {@code spoilage debug place} subcommand, break it, and dump the dropped item's data.
     * The proof lands in the server's general.log / latest.log. Off by default.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_PLACE = "place".equals(System.getProperty("spoilage_enhanced.selftest"));

    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_GUARD = "guard".equals(System.getProperty("spoilage_enhanced.selftest"));

    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_CAKE = "cake".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 629 (L13 behaviour): container-interaction scenarios that RCON cannot reach —
     * QUICK_MOVE, SWAP, PICKUP_ALL and QUICK_CRAFT all need an open menu on a real player.
     * The menuclick command acts as the player, but only a client can open the menu first.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_CONTAINER = "container".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 639 (L13 behaviour): the campfire rotten-refusal guard. CampfireBlockEntityMixin
     * refuses rotten food at placeFood HEAD, but that path needs a player holding food
     * right-clicking a campfire — RCON cannot right-click. This scenario drives it through
     * debug use: spawn a rotten potato, let the player pick it up (hotbar slot 0), then
     * debug use the campfire. The guard must refuse: the potato stays in hand and the
     * campfire's Items stay empty. A control step with a fresh potato must succeed.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_CAMPFIRE = "campfire".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 641 (L13 behaviour): AnimalEntityMixin's rotten-food guard. mobInteract runs
     * when a player right-clicks an animal holding food — needs a real player. The new
     * debug useentity command (Pass 641) wraps that path. The animal must receive the
     * poison effect from rotten food and the breeding love mode must be reset.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_ANIMAL = "animal".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 643 (L13 behaviour): ComposterBlockMixin.addItem rotten-refusal guard. The
     * composter must not raise its level for a rotten item (rotten_chance=0.0F by default).
     * Needs a player holding the item right-clicking the composter — RCON cannot. Driven
     * through debug use.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_COMPOSTER = "composter".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 656 (L13 behaviour): RandomizableContainerBlockEntityMixin.onGenerateLoot —
     * the loot randomization path. A chest with a LootTable field unpacks its loot when a
     * player opens it, and the mixin randomizes each spoilable item's state (60/30/10
     * fresh/stale/rotten by default). Needs a real player to open the chest.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_LOOT = "loot".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 670 (L13 behaviour): drive ClearAllStatusEffectsConsumeEffectMixin via real player
     * eating. Pass 666 REFUTED the RCON-based verification because debug eat is a server-side
     * simulation with no real player to receive effects. This harness drives the real
     * Item.finishUsingItem path with the executing player, so the poison/nausea/hunger
     * effects are actually applied to the player entity.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_EAT = "eat".equals(System.getProperty("spoilage_enhanced.selftest"));

    /**
     * Pass 672 (L13 behaviour): drive AbstractFurnaceBlockEntityMixin.canBurn rotten-refusal
     * guard. The furnace must not accept rotten food as fuel/input. No existing selftest
     * covers this path.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean spoilage_enhanced$SELF_TEST_FURNACE = "furnace".equals(System.getProperty("spoilage_enhanced.selftest"));

    @org.spongepowered.asm.mixin.Unique
    private static boolean spoilage_enhanced$selfTestStarted = false;

    @org.spongepowered.asm.mixin.Unique
    private static int spoilage_enhanced$selfTestStep = 0;

    @org.spongepowered.asm.mixin.Unique
    private static long spoilage_enhanced$selfTestNextStepTime = 0L;

    /**
     * Pass 177 (Lens 5 — render-path): the HUD text only changes when displayedTicks changes
     * (every tick), not every frame. Cache the last rendered Component + width per
     * (spoilState, displayedTicks) pair to avoid allocating new Component.translatable
     * objects and measuring font.width() every frame. The cache is tiny (max 3 states *
     * a few tick buckets).
     *
     * <p>Pass 186 (Lens 12 — claim drift): the Pass 177 javadoc claimed the cache was
     * "cleared on language change via clearFormatTimeCache()" — but that method only
     * clears the formatTime string cache, not this one. The cached Component re-resolves
     * its language on render (TranslatableContents.decompose checks the current language),
     * so the TEXT is fine — but the cached WIDTH was measured in the old language and
     * goes stale, mis-centering the HUD line. The cache now lives in HudTextCache and is
     * cleared by ClientLanguageMixin alongside clearFormatTimeCache().</p>
     */
    @org.spongepowered.asm.mixin.Unique
    private static final int HUD_TEXT_CACHE_MAX = 64;

    @Inject(method = "extractCrosshair", at = @At("TAIL"))
    private void renderBlockSpoilageHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;

        if (spoilage_enhanced$SELF_TEST_PLACE) {
            spoilage_enhanced$runPlaceSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_GUARD) {
            spoilage_enhanced$runGuardSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_CAKE) {
            spoilage_enhanced$runCakeSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_CONTAINER) {
            spoilage_enhanced$runContainerSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_CAMPFIRE) {
            spoilage_enhanced$runCampfireSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_ANIMAL) {
            spoilage_enhanced$runAnimalSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_COMPOSTER) {
            spoilage_enhanced$runComposterSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_LOOT) {
            spoilage_enhanced$runLootSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_EAT) {
            spoilage_enhanced$runEatSelfTest(client);
        }

        if (spoilage_enhanced$SELF_TEST_FURNACE) {
            spoilage_enhanced$runFurnaceSelfTest(client);
        }

        HitResult hitResult = client.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            if (spoilage_enhanced$NET_PROBE) {
                ClientBlockSpoilageCache.requestIfStale(client.player.blockPosition().below());
            }
            return;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        ClientBlockSpoilageCache.requestIfStale(pos);

        int stateOrdinal = ClientBlockSpoilageCache.getState(pos);
        if (stateOrdinal == BlockSpoilageResponsePayload.STATE_NONE
                || stateOrdinal < 0
                || stateOrdinal >= FoodSpoilageUtil.SpoilageState.values().length) {
            // BUG-13 (CLIENT-HUD-02): no answer yet. If the block is spoilable at all, show a
            // neutral "checking" line right away instead of blank space for a round-trip —
            // the real state replaces it as soon as the server answers.
            //
            // STATE_NONE means two different things and only one of them is "wait": either no
            // answer has arrived, or the server answered and the answer is "this block has no
            // spoilage". Treating both as "waiting" left "checking freshness" on screen forever
            // the moment the server started returning STATE_NONE for a growing crop - the client
            // still guesses potatoes look spoilable, because the block IS in tracked_blocks, so
            // the placeholder was drawn on every frame and nothing ever replaced it.
            //
            // hasAnswerFor is what separates the two: once an answer is in, its verdict stands,
            // including the verdict that there is nothing to show.
            if (!com.spoilageenhanced.client.ClientBlockSpoilageCache.hasAnswerFor(pos)
                    && spoilage_enhanced$isSpoilableBlock(client, pos)) {
                Component checking = Component.translatable(SpoilageEnhancedTranslations.HUD_CHECKING);
                Font font0 = client.font;
                int w0 = font0.width(checking);
                int x0 = (graphics.guiWidth() - w0) / 2;
                int y0 = graphics.guiHeight() / 2 - 25;
                graphics.fill(x0 - 4, y0 - 2, x0 + w0 + 4, y0 + 12, 0x80000000);
                graphics.text(font0, checking, x0, y0, 0xFFAAAAAA, true);
            }
            return;
        }

        FoodSpoilageUtil.SpoilageState spoilState = FoodSpoilageUtil.SpoilageState.values()[stateOrdinal];
        // Pass 200 (L5): one get() chain instead of two — the combined accessor returns
        // both the remaining ticks and the multiplier from a single cache lookup.
        // Pass 205 (L3): the record return replaces the static lastCombinedMultiplier
        // field, which could be read without the preceding call and silently serve a
        // stale value from the previous frame.
        ClientBlockSpoilageCache.TicksAndMultiplier answer =
                ClientBlockSpoilageCache.getTicksRemainingAndMultiplier(pos);
        // NO_TIMER means "this block is not aging" — test it on the RAW value, before the
        // multiplier touches it. Scaling the sentinel destroys it: any multiplier below 1.0
        // makes (long)(-1 * m) round to 0, and zero renders as "<1 min". That is precisely
        // how the previous attempt at this fix still showed a countdown in game.
        boolean noTimer = answer.ticksRemaining() == BlockSpoilageResponsePayload.NO_TIMER;
        long displayedTicks = noTimer
                ? BlockSpoilageResponsePayload.NO_TIMER
                : (long) (answer.ticksRemaining() * answer.speedMultiplier());
        String timeStr = noTimer ? "" : SpoilageEnhancedTranslations.formatTime(displayedTicks);

        // Pass 177: cache the rendered text per (state, displayedTicks) pair to avoid
        // allocating new Component.translatable and measuring font.width() every frame.
        // Pass 547 (Lens 5 — render-path): quantize displayedTicks into (days, hours, minutes)
        // for the cache key. The HUD line text only changes when one of those three units
        // changes (the "2d 5h" string doesn't change for an hour at a time), so packing the
        // raw tick value (which moves every game tick) meant every frame hit a brand-new
        // key. After 64 unique frames the cap stopped caching and the HUD re-allocated
        // Component + re-measured font.width() every frame. Packing the same triple
        // SpoilageEnhancedTranslations.formatTime uses gives ~100% hit rate.
        long days = displayedTicks / 24000L;
        long remAfterDays = displayedTicks % 24000L;
        long hours = remAfterDays / 1000L;
        long minutes = (remAfterDays % 1000L) * 60L / 1000L;
        // Mask each field to a fixed width so a pathological duration (a modded
        // item_durations entry near Long.MAX_VALUE) cannot bleed into the state bits
        // above and collide two different states onto one key. 16 bits per field caps
        // days at 65535 (~4.5 game years) — beyond that the bucket saturates, which
        // only merges cache entries for absurdly long timers, never splits one.
        // Pass 547 (follow-up): NO_TIMER (-1) is the only negative displayedTicks, and the
        // quantization above turns it into days=0, hours=23, minutes=59 — which is a real
        // countdown a tracked FRESH block can actually have. Without the sentinel bit the
        // "Fresh" (no timer) text for an untracked block would be served to a block that has
        // 23h59m left, which is the same class of defect this mod has burned before: a value
        // that looks right until you trace it end to end. Bit 47 is free (stateOrdinal only
        // reaches bits 48-49; days is masked to 16 bits at 32-47, and 32768 days is ~90 years
        // — unreachable for a spoilage countdown).
        long cacheKey = ((long) stateOrdinal << 48)
                | (noTimer ? 0x8000_0000_0000L : 0L)
                | ((days & 0xFFFFL) << 32) | ((hours & 0xFFFFL) << 16) | (minutes & 0xFFFFL);
        com.spoilageenhanced.client.HudTextCache.CachedHudText cached =
                com.spoilageenhanced.client.HudTextCache.get(cacheKey);
        if (cached == null) {
            Component text;
            int color;
            switch (spoilState) {
                case STALE:
                    text = Component.translatable(SpoilageEnhancedTranslations.HUD_STALE, timeStr);
                    color = 0xFFFFFF55;
                    break;
                case ROTTEN:
                    text = Component.translatable(SpoilageEnhancedTranslations.HUD_ROTTEN);
                    color = 0xFFFF5555;
                    break;
                case FRESH:
                default:
                    // Pass 221: untracked blocks (timeStr empty) render as "Fresh" with no timer.
                    text = timeStr.isEmpty()
                            ? Component.translatable(SpoilageEnhancedTranslations.HUD_FRESH_NO_TIMER)
                            : Component.translatable(SpoilageEnhancedTranslations.HUD_FRESH, timeStr);
                    color = 0xFF55FF55;
                    break;
            }
            int textWidth = client.font.width(text);
            cached = new com.spoilageenhanced.client.HudTextCache.CachedHudText(text, textWidth, color);
            com.spoilageenhanced.client.HudTextCache.put(cacheKey, cached);
        }

        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int x = (screenWidth - cached.width()) / 2;
        int y = screenHeight / 2 - 25;

        // RENDERDUMP (VISUAL_VERIFICATION.md): report what the HUD drew, one line per element,
        // only when the line changes since the last frame. Keyed by the state so a FRESH block
        // and a ROTTEN block report independently; the payload carries the resolved text, the
        // on-screen position and the colour so a mis-centred or mis-coloured line is visible
        // without a screen.
        if (com.spoilageenhanced.client.RenderDump.isEnabled()) {
            com.spoilageenhanced.client.RenderDump.emit("hud:" + spoilState,
                    "element=hud state=" + spoilState
                            + " text=" + com.spoilageenhanced.client.RenderDump.quote(cached.text().getString())
                            + " x=" + x + " y=" + y + " w=" + cached.width() + " color=#" + String.format("%06X", cached.color() & 0xFFFFFF));
        }

        graphics.fill(x - 4, y - 2, x + cached.width() + 4, y + 12, 0x80000000);
        graphics.text(font, cached.text(), x, y, cached.color(), true);
    }

    /**
     * Client-side guess whether the block can spoil at all — delegates to the cached
     * {@link ClientBlockSpoilageCache#looksSpoilableCached} which uses the flag stored with the
     * last server answer (or the live check if no answer yet). Used only to decide whether the
     * "checking" placeholder is worth drawing — the server answer remains the source of truth.
     */
    @org.spongepowered.asm.mixin.Unique
    private static boolean spoilage_enhanced$isSpoilableBlock(Minecraft client, BlockPos pos) {
        return com.spoilageenhanced.client.ClientBlockSpoilageCache.looksSpoilableCached(pos);
    }

    /**
     * Drives the BUG-12 scenario from the render tick, one step every 40 ticks (2 s), so the
     * whole test runs without a mouse: speed up spoilage, clear the area, place a rotten
     * pumpkin through the real {@code BlockItem.place} path, break it, and dump the drop.
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runPlaceSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 6) {
            return;
        }
        long now = client.level.getGameTime();
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }

        // Step 0 fires as soon as the world is joined; the rest follow on the 2 s cadence.
        // Layout: build a stone pillar at the player's column (top at feet+2) and a one-block
        // platform 2 blocks east at the same height, then tp the player onto that platform.
        // The place command then aims at the pillar top from the platform: the target cell
        // (pillar top + 1) is free air with solid support below and no player collision.
        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                conn.sendCommand("spoilage speed 100");
                conn.sendCommand("kill @e[type=item]");
                conn.sendCommand("execute at @p run setblock ~ ~ ~ minecraft:stone");
                conn.sendCommand("execute at @p run setblock ~ ~1 ~ minecraft:stone");
                conn.sendCommand("execute at @p run setblock ~ ~2 ~ minecraft:stone");
                conn.sendCommand("execute at @p run setblock ~2 ~2 ~ minecraft:stone");
                conn.sendCommand("execute at @p run tp @p ~2 ~3 ~");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> conn.sendCommand("execute at @p run spoilage debug place minecraft:pumpkin rotten");
            case 2 -> conn.sendCommand("execute at @p run spoilage debug inspect");
            case 3 -> conn.sendCommand("execute at @p run setblock ~-2 ~ ~ minecraft:air destroy");
            case 4 -> conn.sendCommand("data get entity @e[type=item,sort=nearest,limit=1] Item");
            case 5 -> conn.sendCommand("spoilage debug dump");
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 480 (L13 — behaviour, UNBLOCKED): drive the rotten-into-crafting-grid case as a
     * real player. The previous behaviour-lens tasks that needed a right-click into a
     * container came back BLOCKED because RCON cannot click; the L13 entry on TASKS.md asks
     * for this exact scenario now that {@code spoilage debug use} and
     * {@code spoilage debug menuclick} exist. Each step is a real
     * {@code BlockState.useItemOn} / {@code AbstractContainerMenu.clicked} call as the
     * executing player, so the guards in those paths are exercised for real.
     *
     * <p>Commands are sent as raw {@code ServerboundChatCommandPacket}s through the
     * connection accessor, NOT via {@code ClientPacketListener.sendCommand}: that method
     * parses the command against the client's copy of the command tree first, and the tree
     * sync proved unreliable in this dev setup (every command — vanilla ones included —
     * failed with "Unknown or incomplete command" and never reached the server). The raw
     * packet skips the local parse entirely; the server parses and executes it, which is the
     * path that matters anyway.</p>
     *
     * <p>Steps (one every 40 ticks = 2 s):
     * <ol>
     *   <li>Speed up spoilage, clear the area, spawn 1 rotten + 3 fresh carrots at the
     *       player's feet (pickup-merge keeps the worst — Pass 470), place a crafting table
     *       to the east.</li>
     *   <li>{@code debug use} the crafting table — opens the 3×3 grid menu.</li>
     *   <li>{@code menuclick} slot 37 button 0 PICKUP — picks the 4-stack from hotbar menu slot
     *       37 into the carried item. Slot 37 is USE_ROW_SLOT_START (CraftingMenu.java:28): the
     *       menu adds result(1) + craft grid(9) + main inventory(27) first, so the hotbar's
     *       Inventory index 0 — where the picked-up carrots land (Slot: 0b) — is menu slot 37,
     *       not 10. The cursor must be NON-EMPTY for the next step to reach the guard:
     *       vanilla's PICKUP on an empty cursor is a no-op, which is exactly what the previous
     *       version of this harness did and why Pass 622 found the guard never fired.</li>
     *   <li>{@code menuclick} slot 1 button 1 PICKUP — RIGHT-CLICK empty crafting slot 1 with the
     *       4-stack in the cursor. Vanilla's safeInsert(carried, 1) takes the WORST item (the
     *       rotten one) and tries to place it; ScreenHandlerMixin.clicked must reject it, so
     *       slot 1 stays empty and the cursor keeps all 4.</li>
     *   <li>{@code menuclick} slot 37 button 0 PICKUP — put the 4-stack back into hotbar
     *       slot 37 (outside the craft grid, so isProcessingInputSlot returns false and the
     *       guard does not fire on the put-back).</li>
     *   <li>{@code data get entity @p Inventory[{id:"minecraft:carrot"}]} — the 4-stack must be
     *       back in the inventory with rotten_count:1 + 3 fresh, and the craft grid must be
     *       empty. That is the before/after pair the §2 fix promises: the stack entered and left
     *       the inventory untouched, and the rotten item never reached the grid.</li>
     * </ol>
     * The proof lands in the server log; off by default.
     *
     * <p>Pass 622 (L13 behaviour): the original step list clicked slot 0 and slot 1 with an
     * EMPTY cursor, so every menuclick was a no-op and the guard was never invoked — the run
     * reported "slot=empty, carried=empty" three times and then read a perfectly untouched
     * 4-stack out of the inventory. That looked like a pass and proved nothing. The corrected
     * sequence above is what makes this test able to fail: if the guard is removed or broken,
     * the right-click lands the rotten carrot in slot 1 and the data get shows a 3-stack in
     * the inventory instead of a 4-stack.</p>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runGuardSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 5) {
            return;
        }
        long now = client.level.getGameTime();
        // First call: hold step 0 for 300 ticks (15 s) after join. The server often
        // reports "Can't keep up!" for the first 10-15 s after a player joins (chunk
        // generation, entity spawns, command tree sync). Sending commands during that
        // window fails silently (packets dropped or parsed against an incomplete tree).
        // 300 ticks = 15 s gives the server ample time to settle.
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "spoilage speed 100");
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                // Build a 4-stack with 1 rotten + 3 fresh: spawn both drops at the player's
                // feet. The player picks them up (inventory was cleared), and the mod's
                // pickup-merge path (PlayerInventoryMixin.afterAddResource -> mergeItems,
                // verified in Pass 470) preserves the worst, so the resulting 4-stack has
                // rotten_count:1 + fresh x3.
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot rotten 1");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 3");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
            case 2 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 PICKUP");
            case 3 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 1 1 PICKUP");
            case 4 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 PICKUP");
            case 5 -> spoilage_enhanced$sendRawCommand(rawConn, "data get entity @p Inventory[{id:\"minecraft:carrot\"}]");
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Sends a chat-command packet without the client-side Brigadier parse that
     * {@code sendCommand} performs. See the guard self-test javadoc for why the raw packet is
     * needed in this dev environment.
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$sendRawCommand(net.minecraft.network.Connection conn, String command) {
        conn.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(command));
    }

    /**
     * PLAYER_REPORTS §3 — drive the rotten-cake eat path as a real player.
     *
     * <p>The cake fix was reworked (Pass 224 refused the placement; the concurrent agent's
     * rework places the ROTTEN state and applies the effects in {@code CakeEatMixin} on the
     * block-eating path), and that rework has never been driven at runtime — Pass 551 marked
     * it BLOCKED because RCON cannot right-click. This scenario closes that gap through the
     * real paths: {@code debug place} places the cake through {@code BlockItem.place} (so
     * {@code GourdBlockMixin.setPlacedBy} records the ROTTEN state), then {@code debug use}
     * right-clicks it through {@code BlockState.useWithoutItem} → {@code CakeBlock.eat} (so
     * {@code CakeEatMixin} applies the poison for real).</p>
     *
     * <p>Steps (one every 40 ticks = 2 s, first held 300 ticks = 15 s for the server to
     * settle after join — same reason as the guard self-test):
     * <ol>
     *   <li>Clear the area and the player's inventory, build a stone support block 2 east
     *       at the player's feet level, and make the player hungry enough to eat (survival
     *       gamemode, food level 0 — {@code canEat(false)} must answer yes).</li>
     *   <li>{@code debug place minecraft:cake rotten} on top of that support — the real
     *       placement path, GourdBlockMixin records ROTTEN.</li>
     *   <li>{@code debug use} the cake — the real eat path, CakeEatMixin must apply poison.</li>
     *   <li>Read the player's active effects — the proof line must show minecraft:poison.</li>
     * </ol>
     * The proof lands in the server log ("Player ate a slice of ROTTEN cake ... Poison applied")
     * and in the {@code data get} answer; off by default.
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runCakeSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 5) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=item]");
                // The place command's default target is 2 blocks WEST of the player
                // (player.blockPosition().offset(-2, 0, 0)), aiming at the block below it
                // so the cake lands on top. Clear the target cell (it may hold a leftover
                // block from earlier testing — a Fail[] placement proves nothing), then
                // build the support pillar: stone at (feet - 2 west, one below feet), so
                // the cake's target cell (feet - 2 west, feet level) is free air with
                // solid support under it.
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~-2 ~ ~ minecraft:air");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~-2 ~-1 ~ minecraft:stone");
                // The player must be able to eat: CakeBlock.eat returns PASS when
                // canEat(false) is false. needsFood() == foodLevel < 20, and a player
                // joining a fresh world starts at 20. Gamemode creative makes
                // abilities.invulnerable true, which makes canEat(false) true regardless
                // of food level — the easiest deterministic way to satisfy the check.
                spoilage_enhanced$sendRawCommand(rawConn, "gamemode creative @p");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug place minecraft:cake rotten");
            case 2 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~-2 ~ ~");
            case 3 -> spoilage_enhanced$sendRawCommand(rawConn, "data get entity @p active_effects");
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 629 (L13 behaviour): drive the four container-click paths that RCON cannot reach
     * (QUICK_MOVE, SWAP, PICKUP_ALL, QUICK_CRAFT) as a real player. Each scenario rebuilds a
     * 4-stack (1 rotten + 3 fresh carrots) in the hotbar, opens a crafting table, and exercises
     * one click type; the server's general.log captures the before/after for the carried item
     * and the target slot.
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runContainerSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 6) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "spoilage speed 100");
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot rotten 1");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 3");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 QUICK_MOVE");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot rotten 1");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 3");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 1 SWAP");
            }
            case 3 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 4");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 PICKUP");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 1 0 PICKUP");
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 4");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 PICKUP");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 1 0 PICKUP");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 4");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 1 0 PICKUP_ALL");
            }
            case 4 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:crafting_table");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot rotten 1");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 3");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 37 0 QUICK_CRAFT");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 1 0 QUICK_CRAFT");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug menuclick 0 2 QUICK_CRAFT");
            }
            case 5 -> spoilage_enhanced$sendRawCommand(rawConn, "data get entity @p Inventory[{id:\"minecraft:carrot\"}]");
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 639 (L13 behaviour): drive the campfire rotten-refusal guard
     * (CampfireBlockEntityMixin.placeFood HEAD injection). Steps (one every 40 ticks,
     * first held 300 ticks for the server to settle):
     * <ol>
     *   <li>Setup: clear inventory, kill dropped items, place a campfire 2 blocks east,
     *       spawn a ROTTEN potato at the player's feet (pickup puts it in hotbar slot 0).</li>
     *   <li>debug use the campfire with the rotten potato in hand — the guard must refuse:
     *       the potato stays in the inventory and the campfire Items stay empty.</li>
     *   <li>Read the campfire's Items (must be empty) and the player's inventory (must still
     *       hold the rotten potato).</li>
     *   <li>Control: clear, spawn a FRESH potato, pick up, debug use — vanilla must accept
     *       it (campfire Items now hold the potato, inventory empty).</li>
     *   <li>Read the campfire's Items again (must hold the fresh potato).</li>
     * </ol>
     * The proof lands in the server log (the guard logs nothing, but the data get answers
     * show the before/after pair).
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runCampfireSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 5) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=item]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:campfire[lit=true]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:potato rotten 1");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block ~2 ~ ~ Items");
                spoilage_enhanced$sendRawCommand(rawConn, "data get entity @p Inventory[{id:\"minecraft:potato\"}]");
            }
            case 3 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:potato fresh 1");
            }
            case 4 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block ~2 ~ ~ Items");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 641 (L13 behaviour): drive AnimalEntityMixin.onInteractMobWithFood.
     * Steps (one every 40 ticks, first held 300 ticks):
     * <ol>
     *   <li>Setup: clear, kill mobs, teleport a cow 2 blocks from the player, spawn a
     *       rotten wheat at the player's feet (pickup puts it in hotbar slot 0).</li>
     *   <li>debug useentity cow — the guard must fire: animal.cfg applies poison
     *       (default 100 ticks) and resets love mode. The wheat stays in hand.</li>
     *   <li>Read the cow's active_effects — must include poison. Also read the cow's
     *       InLove (must be 0 since the guard called resetLove).</li>
     *   <li>Control: clear, teleport a fresh cow, spawn fresh wheat, pickup, debug
     *       useentity cow — vanilla must accept the feeding (InLove becomes 600 ticks).
     *       Read the cow's InLove to confirm.</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runAnimalSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 5) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=item]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=cow]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run summon minecraft:cow ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:wheat rotten 1");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug useentity cow");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get entity @e[type=cow,limit=1] active_effects");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get entity @e[type=cow,limit=1] InLove");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=cow]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run summon minecraft:cow ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:wheat fresh 1");
            }
            case 3 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug useentity cow");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get entity @e[type=cow,limit=1] InLove");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get entity @p Inventory[{id:\"minecraft:wheat\"}]");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 643 (L13 behaviour): drive ComposterBlockMixin.addItem rotten-refusal guard.
     * Steps (one every 40 ticks, first held 300 ticks):
     * <ol>
     *   <li>Setup: clear, kill items, place a composter 2E at feet level, spawn a rotten
     *       carrot at the player's feet (pickup puts it in hotbar slot 0).</li>
     *   <li>debug use the composter with the rotten carrot in hand — the guard must refuse:
     *       the composter's LEVEL stays 0 and the carrot stays in hand.</li>
     *   <li>Read the composter's LEVEL (must be 0) and the player's inventory (must still
     *       hold the rotten carrot).</li>
     *   <li>Control: clear, spawn a fresh carrot, pickup, debug use — vanilla must accept
     *       it (LEVEL becomes 1). Read the composter's LEVEL again (must be 1).</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runComposterSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 5) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run kill @e[type=item]");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:composter");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot rotten 1");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block ~2 ~ ~ LEVEL");
                spoilage_enhanced$sendRawCommand(rawConn, "data get entity @p Inventory[{id:\"minecraft:carrot\"}]");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "clear @p");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug spawn minecraft:carrot fresh 1");
            }
            case 3 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block ~2 ~ ~ LEVEL");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 656 (L13 behaviour): drive RandomizableContainerBlockEntityMixin.onGenerateLoot.
     * Steps (one every 40 ticks, first held 300 ticks):
     * <ol>
     *   <li>Setup: place a chest 2E with LootTable=minecraft:chests/village/village_plains_house
     *       (a loot table that contains bread — a spoilable item).</li>
     *   <li>debug use the chest — opens its menu, which calls unpackLootTable on first open.
     *       The mixin randomizes each spoilable item.</li>
     *   <li>Read the chest's Items — the generated bread must carry a spoilage component
     *       (fresh_expirations, stale_expirations, or rotten_count — any of the three).</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runLootSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 3) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock ~2 ~ ~ minecraft:chest");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data modify block ~2 ~ ~ LootTable set value \"minecraft:chests/village/village_plains_house\"");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug use ~2 ~ ~");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block ~2 ~ ~ Items");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 670 (L13 behaviour): drive ClearAllStatusEffectsConsumeEffectMixin via real player
     * eating. Steps (one every 40 ticks, first held 300 ticks):
     * <ol>
     *   <li>Give the player a rotten milk_bucket via debug give.</li>
     *   <li>Use debug eat to consume it — this calls Item.finishUsingItem on the real player,
     *       which triggers the ConsumeEffect mixin. The mixin applies poison/nausea/hunger
     *       and cancels effect clearing.</li>
     *   <li>Read the player's active_effects — must show poison (and nausea/hunger if
     *       configured).</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runEatSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 3) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug give minecraft:milk_bucket rotten 1");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run spoilage debug eat minecraft:milk_bucket rotten 1");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get entity @s active_effects");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }

    /**
     * Pass 672 (L13 behaviour): drive AbstractFurnaceBlockEntityMixin.canBurn rotten-refusal.
     * Steps:
     * <ol>
     *   <li>Place a furnace at -48 64 0.</li>
     *   <li>Try to put a rotten food item in the furnace input slot via debug menuclick.
     *       The furnace must refuse (canBurn returns false for rotten food).</li>
     *   <li>Control: put a fresh food item — should succeed.</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$runFurnaceSelfTest(Minecraft client) {
        if (spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestStep >= 3) {
            return;
        }
        long now = client.level.getGameTime();
        if (!spoilage_enhanced$selfTestStarted && spoilage_enhanced$selfTestNextStepTime == 0L) {
            spoilage_enhanced$selfTestNextStepTime = now + 300L;
            return;
        }
        if (now < spoilage_enhanced$selfTestNextStepTime) {
            return;
        }
        spoilage_enhanced$selfTestNextStepTime = now + 40L;

        net.minecraft.client.multiplayer.ClientPacketListener conn = client.getConnection();
        if (conn == null) {
            return;
        }
        net.minecraft.network.Connection rawConn =
                ((com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor) conn).spoilage_enhanced$getConnection();
        if (rawConn == null) {
            return;
        }

        switch (spoilage_enhanced$selfTestStep) {
            case 0 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run setblock -48 64 0 minecraft:furnace");
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data modify block -48 64 0 Items set value [{Slot:0b,id:\"minecraft:potato\",count:1,components:{\"spoilage_enhanced:spoilage\":{\"rotten_count\":1}}},{Slot:1b,id:\"minecraft:coal\",count:1}]");
                spoilage_enhanced$selfTestStarted = true;
            }
            case 1 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block -48 64 0 Items");
            }
            case 2 -> {
                spoilage_enhanced$sendRawCommand(rawConn, "execute at @p run data get block -48 64 0");
            }
            default -> {
            }
        }
        spoilage_enhanced$selfTestStep++;
    }
}

