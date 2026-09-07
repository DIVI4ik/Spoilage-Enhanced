package com.spoilageenhanced.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: "what is the spoilage state of the block I am looking at?".
 */
public record BlockSpoilageRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlockSpoilageRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("spoilage_enhanced", "block_spoilage_request"));

    public static final StreamCodec<FriendlyByteBuf, BlockSpoilageRequestPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> buf.writeBlockPos(payload.pos()),
                    buf -> new BlockSpoilageRequestPayload(buf.readBlockPos())
            );

    @Override
    public CustomPacketPayload.Type<BlockSpoilageRequestPayload> type() {
        return TYPE;
    }
}
