package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
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

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void spoilage_enhanced$sweepContainerContents(BooleanSupplier haveTime, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.isClientSide()) {
                continue;
            }
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Long2ObjectLinkedOpenHashMap<ChunkHolder> chunks =
                    ((ChunkMapAccessor) chunkMap).spoilage_enhanced$getUpdatingChunkMap();
            long gameTime = level.getGameTime();

            for (ChunkHolder holder : chunks.values()) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }
                // Phase spread by chunk: 1/20 of the loaded chunks per tick. See the class
                // javadoc for why the per-position gate was wrong (residue mismatch).
                if (((long) chunk.getPos().x() + chunk.getPos().z() + gameTime) % 20 != 0) {
                    continue;
                }
                for (Map.Entry<net.minecraft.core.BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof Container container)) {
                        continue;
                    }
                    // One bad container must not kill the server tick: the sweep is the FIRST
                    // code that ever touches many of these containers (vanilla never ticks a
                    // chest), so a modded container whose getItem() throws would otherwise
                    // crash through this loop every second. Log and move on.
                    try {
                        ageContainer(container, level);
                    } catch (Throwable t) {
                        SpoilageEnhancedLogger.log("ContainerAgingSweep: skipped container at "
                                + entry.getKey() + " (" + entry.getValue().getType() + "): " + t);
                    }
                }
            }
        }
    }

    /**
     * Ages every spoilable stack in one container. Mirrors the probe-then-update shape of
     * {@code HopperAgingMixin}: the cheap {@code isSpoilable} probe runs before any
     * component access, and {@code updateSpoilage} lazily stamps unstamped spoilable food
     * exactly as the bundle and minecart branches do.
     */
    private static void ageContainer(Container container, ServerLevel level) {
        boolean anySpoilable = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
            // Pass 1189 (L13): a stack that is not itself food can still CARRY it — a
            // bundle (BUNDLE_CONTENTS) or a shulker box (CONTAINER) sitting in this
            // container. The old probe saw 'not spoilable' and skipped the whole
            // container, so a bundle with a tracked apple inside a chest never aged
            // (verified live: fresh_expirations unchanged after 25s). Probe the carried
            // templates — no ItemStack allocation, just the backing lists.
            if (stack.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)) {
                net.minecraft.world.item.component.BundleContents contents =
                        stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
                for (net.minecraft.world.item.ItemStackTemplate template : contents.items()) {
                    if (SpoilageConfig.getInstance().isSpoilable(template.item().value())) {
                        anySpoilable = true;
                        break;
                    }
                }
            } else if (stack.has(net.minecraft.core.component.DataComponents.CONTAINER)) {
                net.minecraft.world.item.component.ItemContainerContents contents =
                        stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                for (net.minecraft.world.item.ItemStackTemplate template : contents.nonEmptyItems()) {
                    if (SpoilageConfig.getInstance().isSpoilable(template.item().value())) {
                        anySpoilable = true;
                        break;
                    }
                }
            }
            if (anySpoilable) {
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            // Pass 1189: updateSpoilage itself no-ops on stacks that are neither spoilable
            // nor food-carrying (its own guards), so calling it on every non-empty stack of
            // a container the probe flagged is safe — and it is the only way a bundle or
            // nested shulker box in this container gets its contents aged. The old
            // isSpoilable filter here skipped them even after the probe was fixed.
            FoodSpoilageUtil.updateSpoilage(stack, level);
        }
    }
}
