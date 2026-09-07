package com.spoilageenhanced.mixin;

import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.network.SpoilagePayloads;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Teaches the vanilla clientbound custom-payload codec about the mod's response payload.
 *
 * <p>See {@link SpoilagePayloads} for why the injection sits on {@code Util.make} rather than on
 * {@code CustomPacketPayload.codec} or the {@code types -> {}} lambda. {@code require = 0} keeps a
 * future patch that drops the call from killing the game: the HUD then simply gets no data, and
 * the server logs "Undecodable payload".
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public class ClientboundPayloadTypesMixin {

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "make(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;",
                    remap = false
            ),
            index = 0,
            require = 0,
            remap = false
    )
    private static Object spoilage_enhanced$addClientboundTypes(Object types) {
        return SpoilagePayloads.appendType(types,
                BlockSpoilageResponsePayload.TYPE, BlockSpoilageResponsePayload.STREAM_CODEC);
    }
}
