package com.spoilageenhanced.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper shared by the two payload-registration mixins.
 *
 * <p>The registration point is the list handed to {@code Util.make(list, types -> {})} in the
 * static initializer of the custom-payload packet classes. That call is the only shape common to
 * all three loaders: NeoForge patches {@code CustomPacketPayload.codec} into a four-argument
 * (protocol + flow aware) method, so injecting on the codec call itself only works on Fabric and
 * Forge and hard-crashes NeoForge with "Scanned 0 target(s)".
 */
public final class SpoilagePayloads {

    private SpoilagePayloads() {
    }

    /**
     * Returns a copy of the payload type list with one more entry appended. Anything that is not
     * a list of {@link CustomPacketPayload.TypeAndCodec} is passed through untouched, because
     * {@code Util.make} is a generic helper and a patched class may use it for something else.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends CustomPacketPayload> Object appendType(
            Object listArgument, CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
        if (!(listArgument instanceof List<?> list)) {
            return listArgument;
        }
        if (!list.isEmpty() && !(list.get(0) instanceof CustomPacketPayload.TypeAndCodec)) {
            return listArgument;
        }

        List extended = new ArrayList(list);
        extended.add(new CustomPacketPayload.TypeAndCodec<>(type, codec));
        // Class-init time: the mod logger does not exist yet, so this goes to stdout. It is the
        // only proof that the payload actually reached the codec table on this loader.
        System.out.println("[SpoilageEnhanced] Registered payload " + type.id() + " into a custom-payload codec table");
        return extended;
    }
}
