package com.spoilageenhanced.mixin.client;

import com.spoilageenhanced.client.ClientBlockSpoilageCache;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the server's answer about a block's spoilage state at the packet level.
 * Injecting at HEAD of {@link ClientCommonPacketListenerImpl#handleCustomPayload(ClientboundCustomPayloadPacket)}
 * allows us to handle the payload and cancel it before loader-specific network registries
 * (such as NeoForge's ClientNetworkRegistry) can reject unnegotiated channels and disconnect the client.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCustomPayloadMixin {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Inject(
            method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spoilage_enhanced$onCustomPayloadPacket(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof BlockSpoilageResponsePayload response) {
            PacketUtils.ensureRunningOnSameThread(packet, (ClientCommonPacketListenerImpl) (Object) this, this.minecraft.packetProcessor());
            ClientBlockSpoilageCache.accept(response);
            ci.cancel();
        }
    }
}
