package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Ages food inside placed containers that have no server-side ticker: chests, barrels,
 * shulker boxes, decorated pots, dispensers, droppers, and any modded container in the
 * same position (pass 1184, L13 — observed behaviour).
 *
 * <p>Verified live before the fix: an apple with {@code fresh_expirations:[100L]} placed
 * in a chest and in a shulker box via data modify read back UNCHANGED after 35+ seconds
 * of a running server — both were perfect freezers. The hopper (b233dce), brewing stand,
 * fridge, cooking pot, minecart, item frame and thrown-item paths each got their own
 * mixin because those classes have a tick method to hook; chests have none — a chest's
 * only ticker is the client-side lid animation (ChestBlock.java:328 returns null
 * server-side), so no per-block hook can ever reach them.</p>
 *
 * <p>This sweep runs from {@code MinecraftServer.tickServer} (the same entry point as
 * {@code ServerTickScanMixin}), walks the loaded chunks of every level via
 * {@code ChunkMapAccessor} -> {@code ChunkHolder.getTickingChunk()} ->
 * {@code LevelChunk.getBlockEntities()}, and ages every {@link Container} block entity
 * that holds spoilable food.</p>
 *
 * <p><b>Phase spreading is by CHUNK, not by block position.</b> The first revision gated
 * the whole sweep on {@code server.getTickCount() % 20 == 0} and each container on
 * {@code shouldSkipAgingTick(pos, gameTime)} — and never aged a single item. The reason
 * is a residue mismatch: {@code tickCount} starts at 0 on boot while {@code gameTime}
 * continues from the world save (1.68M on the test world, residue 13), so the sweep only
 * ever ran at gameTime ≡ 13 (mod 20) while a container at x+z ≡ 0 needed gameTime ≡ 0.
 * The two gates can never align for most positions. The fix spreads the CHUNK ITERATION
 * itself across ticks — {@code (chunkX + chunkZ + gameTime) % 20 == 0} — and ages every
 * container in the visited chunks, so each container is aged exactly once per second
 * (20 ticks) regardless of its position residue, and a storage room of hundreds of
 * chests is spread across 20 different tick boundaries by its chunk coordinates.</p>
 *
 * <p>Cost bounds: each tick visits 1/20 of the loaded chunks (a 729-chunk world visits
 * ~36), and each container in them pays one pass over its slots with the cheap
 * {@code isSpoilable} probe before any component work — a chest of cobblestone costs
 * one empty-probe pass and nothing else. The chunk map iteration is the same map the
 * vanilla debug dump walks on the main thread (ChunkMap.java:353, inside
 * {@code debugFuturesAndCreateReportedException}). The iteration is safe from
 * concurrent modification because this inject runs at the HEAD of
 * {@code tickServer}, before {@code tickChildren} -> {@code chunkSource.tick} performs
 * any chunk load/unload mutation on the same thread — during the sweep, no vanilla
 * code is mutating the map.</p>
 *
 * <p><b>Double-aging cannot happen.</b> Containers that ALSO have their own aging
 * mixin (hopper, brewing stand, fridge, cooking pot, minecart) are visited by this
 * sweep too, but {@code FoodSpoilageUtil.updateSpoilage} is a pure function of the
 * absolute {@code gameTime}: the expiration lists hold absolute timestamps and the
 * update moves entries whose expiration has passed. Calling it twice with the same
 * clock is idempotent, so the per-block mixin and this sweep agree exactly.</p>
 *
 * <p><b>Uninhabited chunks age too, by design.</b> The sweep ages any loaded chunk's
 * containers whether or not a player has ever been there — matching the mod's premise
 * that food ages everywhere it exists, and the same clock
 * {@code BlockSpoilageData} uses for block spoilage (chunk birth time).</p>
 *
 * <p><b>Empty-server pause (known non-issue).</b> This inject runs at the HEAD of
 * {@code tickServer}, before vanilla's empty-pause early-return (MinecraftServer.java:976),
 * so on a server with {@code pause-when-empty-seconds > 0} the sweep keeps walking the
 * chunk map while the server is paused. Nothing ages (gameTime is frozen while
 * {@code tickChildren} is skipped, and the update is a pure function of gameTime), so
 * the cost is one idle map walk per tick on an otherwise-idle server. The vanilla
 * default is {@code pauseWhenEmptySeconds() == 0} (MinecraftServer.java:2227), which
 * disables the pause entirely, so this only arises on servers that opted in — and
 * detecting the pause from a HEAD inject would need a remembered-gameTime heuristic
 * that costs more than the walk it saves. Left alone deliberately.</p>
 */
@Mixin(MinecraftServer.class)
public abstract class ContainerAgingSweepMixin {

    @org.spongepowered.asm.mixin.Unique
    private static int spoilage_enhanced$consecutiveSkips = 0;
    @org.spongepowered.asm.mixin.Unique
    private static final int MAX_CONSECUTIVE_SKIPS = 40; // 2 seconds at 20 TPS

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void spoilage_enhanced$sweepContainerContents(BooleanSupplier haveTime, CallbackInfo ci) {
        // Starvation and budget control:
        // haveTime signals whether the server has spare budget in this tick.
        // If the server is overloaded (e.g. heavy worldgen, dungeon generation), yield immediately.
        // Food spoilage is an idempotent function of absolute gameTime, so food will safely catch up.
        // If the server remains continuously saturated for >40 ticks (2 seconds), allow a time-capped
        // sweep (max 1.5ms) to guarantee progress without causing lag spikes.
        boolean budgetExhausted = haveTime != null && !haveTime.getAsBoolean();
        boolean forceStarvationSweep = false;

        if (budgetExhausted) {
            spoilage_enhanced$consecutiveSkips++;
            if (spoilage_enhanced$consecutiveSkips < MAX_CONSECUTIVE_SKIPS) {
                return;
            }
            forceStarvationSweep = true;
            spoilage_enhanced$consecutiveSkips = 0;
        } else {
            spoilage_enhanced$consecutiveSkips = 0;
        }

        long deadlineNanos = forceStarvationSweep ? System.nanoTime() + 1_500_000L : Long.MAX_VALUE;

        MinecraftServer server = (MinecraftServer) (Object) this;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.isClientSide()) {
                continue;
            }
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Long2ObjectLinkedOpenHashMap<ChunkHolder> chunks =
                    ((ChunkMapAccessor) chunkMap).spoilage_enhanced$getUpdatingChunkMap();
            if (chunks.isEmpty()) {
                continue;
            }

            long gameTime = level.getGameTime();

            for (ChunkHolder holder : chunks.values()) {
                net.minecraft.world.level.ChunkPos chunkPos = holder.getPos();
                // Phase spread by chunk: 1/20 of the loaded chunks per tick.
                // Filter by holder.getPos() FIRST before calling getTickingChunk()
                // to eliminate 95% of C2ME/chunk-system lookups.
                if (((long) chunkPos.x() + chunkPos.z() + gameTime) % 20 != 0) {
                    continue;
                }

                // Check time budget during chunk iteration
                if (haveTime != null && !haveTime.getAsBoolean()) {
                    if (!forceStarvationSweep || System.nanoTime() > deadlineNanos) {
                        return;
                    }
                } else if (forceStarvationSweep && System.nanoTime() > deadlineNanos) {
                    return;
                }

                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }

                if (chunk.getBlockEntities().isEmpty()) {
                    continue;
                }

                for (Map.Entry<net.minecraft.core.BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity blockEntity = entry.getValue();
                    if (blockEntity == null) {
                        continue;
                    }

                    // Fast-skip unopened loot tables in generated structures (dungeons, villages, etc.)
                    // Calling getItem() on an unopened RandomizableContainer prematurely unpacks the loot
                    // table and runs randomizeSpoilage across all slots, causing massive worldgen spikes.
                    if (blockEntity instanceof net.minecraft.world.RandomizableContainer rc && rc.getLootTable() != null) {
                        continue;
                    }

                    // Pass 1320: three conventions — Container, getContainer(), and the
                    // getItems() list shape (Ecologics pot). Try the Container shapes first
                    // (the common case), then the list shape.
                    Container container = asAgingContainer(blockEntity);
                    if (container != null) {
                        // One bad container must not kill the server tick: the sweep is the FIRST
                        // code that ever touches many of these containers (vanilla never ticks a
                        // chest), so a modded container whose getItem() throws would otherwise
                        // crash through this loop every second. Log and move on.
                        try {
                            ageContainer(container, level);
                        } catch (Throwable t) {
                            SpoilageEnhancedLogger.log("ContainerAgingSweep: skipped container at "
                                    + entry.getKey() + " (" + blockEntity.getType() + "): " + t);
                        }
                        continue;
                    }
                    java.util.List<ItemStack> itemList =
                            com.spoilageenhanced.util.ContainerResolution.asAgingItemList(blockEntity);
                    if (itemList != null) {
                        try {
                            ageItemList(itemList, level);
                        } catch (Throwable t) {
                            SpoilageEnhancedLogger.log("ContainerAgingSweep: skipped list-backed container at "
                                    + entry.getKey() + " (" + blockEntity.getType() + "): " + t);
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves a block entity to the {@link Container} whose contents this sweep ages.
     * Delegates to {@link com.spoilageenhanced.util.ContainerResolution} — the logic
     * lives outside the mixin because a mixin class cannot carry non-private static
     * members, and so the contract is unit-testable headless (pass 1275).
     */
    private static Container asAgingContainer(BlockEntity blockEntity) {
        return com.spoilageenhanced.util.ContainerResolution.asAgingContainer(blockEntity);
    }

    /**
     * Pass 1320: ages a list-backed container (the getItems() convention). The list is
     * the entity's own backing list — updateSpoilage mutates the ItemStack objects in
     * place, so the entity serialises the aged values on save. Single-pass traversal.
     */
    private static void ageItemList(java.util.List<ItemStack> items, ServerLevel level) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int size = items.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                FoodSpoilageUtil.updateSpoilage(stack, level);
            }
        }
    }

    /**
     * Ages every spoilable stack in one container in a single pass.
     * Empty containers are skipped instantly; non-food items are skipped with
     * the fast probe without touching updateSpoilage components.
     */
    private static void ageContainer(Container container, ServerLevel level) {
        if (container == null || container.isEmpty()) {
            return;
        }
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                FoodSpoilageUtil.updateSpoilage(stack, level);
            }
        }
    }
}
