package com.spoilageenhanced.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code ChunkMap.updatingChunkMap} for the container-aging sweep.
 *
 * <p>Placed chests, barrels, shulker boxes and decorated pots have no server-side ticker
 * (a chest's only ticker is the client lid animation, ChestBlock.java:328), so they never
 * appear in {@code Level.blockEntityTickers} and no per-block hook can reach them. The
 * sweep in {@code ContainerAgingSweepMixin} therefore iterates the loaded chunks directly:
 * {@code updatingChunkMap.values()} -> {@code ChunkHolder.getTickingChunk()} ->
 * {@code LevelChunk.getBlockEntities()}.</p>
 *
 * <p>{@code updatingChunkMap} is chosen over the volatile {@code visibleChunkMap} because
 * the vanilla debug dump itself iterates it on the main thread (ChunkMap.java:353) — same
 * thread the sweep runs on (MinecraftServer.tickServer), so the iteration is safe.</p>
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> spoilage_enhanced$getUpdatingChunkMap();
}
