package com.spoilageenhanced.mixin;

import com.spoilageenhanced.network.BlockSpoilageRequestPayload;
import com.spoilageenhanced.network.SpoilagePayloads;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Serverbound counterpart of {@link ClientboundPayloadTypesMixin}.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public class ServerboundPayloadTypesMixin {

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
    private static Object spoilage_enhanced$addServerboundTypes(Object types) {
        return SpoilagePayloads.appendType(types,
                BlockSpoilageRequestPayload.TYPE, BlockSpoilageRequestPayload.STREAM_CODEC);
    }
}
