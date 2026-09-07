package com.spoilageenhanced.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: spoilage state of a single block.
 *
 * @param pos            the block that was asked about
 * @param state          ordinal of {@code FoodSpoilageUtil.SpoilageState}, or {@link #STATE_NONE}
 *                       when the block is not spoilable at all
 * @param ticksRemaining real ticks until the next stage, as the server counts them
 * @param speedMultiplier the server's spoilage speed multiplier, so the HUD can show the same
 *                        "unscaled" duration the item tooltips use even when the client's own
 *                        config differs
 */
public record BlockSpoilageResponsePayload(BlockPos pos, int state, long ticksRemaining, double speedMultiplier) implements CustomPacketPayload {

    public static final int STATE_NONE = -1;

    /**
     * {@code ticksRemaining} sentinel: this block is not aging, so the HUD must show no timer
     * at all — not a zero, which renders as "less than a minute" and reads as an imminent
     * change that never comes.
     *
     * <p>It lives here because both ends must agree on it. Pass 221 wrote the literal
     * {@code -1L} on the server and checked for it in the HUD, but the cache in between
     * clamped it away with {@code Math.max(0L, ...)} — a shared named constant is what makes
     * that layer visible to whoever adds the next one.</p>
     *
     * <p><b>No current server version sends this, and the client handling must stay anyway.</b>
     * Untracked blocks are now aged from their chunk and get a real countdown, so this server
     * never emits the sentinel. But it is a wire value, and versions 1.0.48–1.0.49 do send it:
     * a newer client joining one of those servers receives it, and a client that no longer
     * recognises it renders the sentinel as "less than a minute" — the original defect, back
     * again across a version gap. Do not delete the client-side handling as unused.</p>
     */
    public static final long NO_TIMER = -1L;

    public static final CustomPacketPayload.Type<BlockSpoilageResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("spoilage_enhanced", "block_spoilage_response"));

    public static final StreamCodec<FriendlyByteBuf, BlockSpoilageResponsePayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> {
                        buf.writeBlockPos(payload.pos());
                        buf.writeByte(payload.state());
                        buf.writeVarLong(payload.ticksRemaining());
                        buf.writeDouble(payload.speedMultiplier());
                    },
                    buf -> new BlockSpoilageResponsePayload(buf.readBlockPos(), buf.readByte(), buf.readVarLong(), buf.readDouble())
            );

    @Override
    public CustomPacketPayload.Type<BlockSpoilageResponsePayload> type() {
        return TYPE;
    }
}
