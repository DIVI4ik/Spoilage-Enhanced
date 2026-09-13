package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
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
 * vanilla debug dump walks on the main thread (ChunkMap.java:353).</p>
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
                    ageContainer(container, level);
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
            if (!stack.isEmpty() && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                continue;
            }
            FoodSpoilageUtil.updateSpoilage(stack, level);
        }
    }
}
