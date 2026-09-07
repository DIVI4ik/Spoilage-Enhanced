package com.spoilageenhanced.network;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server side of the block-spoilage HUD: answers per-block questions from clients.
 *
 * <p>Block spoilage lives in {@link BlockSpoilageData} (server-only saved data), so the client
 * cannot read it directly — on a dedicated server the HUD would otherwise have to invent values.
 */
public final class BlockSpoilageNetworking {

    /** Requests further away than this are ignored (the crosshair can never reach them). */
    private static final double MAX_REQUEST_DISTANCE_SQR = 64.0 * 64.0;

    private BlockSpoilageNetworking() {
    }

    public static void handleRequest(ServerPlayer player, BlockPos pos) {
        // Pass 139 (L1 silent-failure): every early return below used to silently drop the
        // request without sending a response. The client waited for an answer that never came,
        // HUD stuck on "checking" until its 2-tick retry timeout. Now we send a STATE_NONE
        // response immediately so the client can render "nothing here" instead of a spinner.
        if (player == null || pos == null) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Dropped block spoilage request: player or pos was null");
            return; // Cannot send response without player/pos
        }
        ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (level == null) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Dropped block spoilage request for " + pos + ": player is not on a ServerLevel");
            sendEmptyResponse(player, pos);
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REQUEST_DISTANCE_SQR) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Dropped block spoilage request for " + pos + ": too far from "
                            + player.getGameProfile().name());
            sendEmptyResponse(player, pos);
            return;
        }
        if (!level.isLoaded(pos)) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Dropped block spoilage request for " + pos + ": chunk not loaded");
            sendEmptyResponse(player, pos);
            return;
        }

        BlockState state = level.getBlockState(pos);
        Item dropItem = resolveDropItem(state, level, pos);

        int stateOrdinal = BlockSpoilageResponsePayload.STATE_NONE;
        long ticksRemaining = 0L;

        if (dropItem != null && dropItem != Items.AIR) {
            BlockSpoilageData data = BlockSpoilageData.get(level);
            // Ask the same two methods the drop path asks. No special case for an untracked
            // block: both already handle one, by deriving its age from the chunk it sits in
            // (getSpoilageState -> getChunkBirthTime, which adopts a neighbour's birth time or
            // falls back to the chunk's inhabitedTime, then registers the block).
            //
            // The branch that used to be here answered untracked blocks itself, and every
            // version of that answer was wrong in a different way. First it sent the full fresh
            // duration — a constant recomputed identically on every request, so the HUD drew a
            // countdown that never advanced. Then it sent the NO_TIMER sentinel, which stopped
            // the false countdown but showed no time at all, and a player cannot tell a wild
            // pumpkin that will keep for an hour from one about to turn.
            //
            // Both versions shared the deeper fault: the HUD was answering from different logic
            // than the drop. Break an untracked block and BlockDropSpoilageHandler calls
            // getSpoilageState, which ages it by chunk — so the item you received was already
            // aging while the HUD had just told you the block was fresh and static. Displayed
            // and actual disagreed. One code path for both is what keeps them honest.
            stateOrdinal = data.getSpoilageState(pos, level, dropItem).ordinal();
            ticksRemaining = Math.max(0L, data.getTicksUntilNextStage(pos, level, dropItem));
        }

        BlockSpoilageResponsePayload response = new BlockSpoilageResponsePayload(
                pos, stateOrdinal, ticksRemaining, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        try {
            if (player.connection instanceof com.spoilageenhanced.mixin.ServerCommonPacketListenerImplAccessor accessor
                    && accessor.spoilage_enhanced$getConnection() != null) {
                accessor.spoilage_enhanced$getConnection().send(new ClientboundCustomPayloadPacket(response));
            } else {
                player.connection.send(new ClientboundCustomPayloadPacket(response));
            }
        } catch (Throwable t) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Failed to send block spoilage response: " + t.getMessage());
        }

        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                "Answered block spoilage request from " + player.getGameProfile().name() + " for " + pos
                        + " (" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " -> "
                        + (dropItem == null ? "none" : BuiltInRegistries.ITEM.getKey(dropItem))
                        + ") -> state=" + stateOrdinal + ", ticks=" + ticksRemaining);
    }

    /**
     * Sends a STATE_NONE response so the client does not wait on a request that was refused.
     * The client treats STATE_NONE as "no spoilage info" and stops asking until the refresh
     * interval elapses, instead of showing "checking" until its retry timeout.
     */
    private static void sendEmptyResponse(ServerPlayer player, BlockPos pos) {
        BlockSpoilageResponsePayload response = new BlockSpoilageResponsePayload(
                pos, BlockSpoilageResponsePayload.STATE_NONE, 0L,
                SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        try {
            if (player.connection instanceof com.spoilageenhanced.mixin.ServerCommonPacketListenerImplAccessor accessor
                    && accessor.spoilage_enhanced$getConnection() != null) {
                accessor.spoilage_enhanced$getConnection().send(new ClientboundCustomPayloadPacket(response));
            } else {
                player.connection.send(new ClientboundCustomPayloadPacket(response));
            }
        } catch (Throwable t) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Failed to send empty block spoilage response: " + t.getMessage());
        }
    }

    /**
     * Mirrors the drop resolution used by the block mixins: dynamic cache first, then the
     * configured block→drop mapping, then the block's own item.
     */
    private static Item resolveDropItem(BlockState state, ServerLevel level, BlockPos pos) {
        // getFoodDrop is the whole answer. It already walks the same chain this method used to
        // repeat by hand — tracked_blocks, then the block's own item, then the loot table — and
        // it applies the two rules that decide whether the block should be showing anything at
        // all: the block must be bearing food right now, and it must not be excluded.
        //
        // Repeating the chain here defeated both of those rules, because the fallbacks could
        // only ever run when getFoodDrop had just said no.
        //
        // The exclusion case was the live one. excluded_blocks promises a block "NEVER gets
        // spoilage tracking, whatever auto-detection decides", and getFoodDrop honours it — but
        // the final asItem() fallback below did not, so any excluded block whose own item
        // spoils (pumpkin, melon, hay, dried kelp, mushroom blocks, cake, honey, every modded
        // food block) still resolved to an item. The HUD then asked for its spoilage state, and
        // asking is what registers an untracked block and starts its clock. Excluding a block
        // stopped nothing as soon as a player looked at it.
        //
        // The growth guard that used to stand at the top of this method is gone for the same
        // reason: it existed only to stop those fallbacks resurrecting a seedling, and it asked
        // a narrower question than getFoodDrop does. Asking getFoodDrop first also lets it
        // derive the block's ripeness rule, which needs a level and cannot be done without one.
        String dropId = DynamicFoodBlockCache.getFoodDrop(state, level, pos);
        if (dropId == null) {
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(dropId));
        // A mapping alone is not enough: the drop still has to be something that spoils,
        // otherwise the HUD would happily count down the freshness of dirt.
        if (item != null && item != Items.AIR && SpoilageConfig.getInstance().isSpoilable(item)) {
            return item;
        }
        return null;
    }
}
