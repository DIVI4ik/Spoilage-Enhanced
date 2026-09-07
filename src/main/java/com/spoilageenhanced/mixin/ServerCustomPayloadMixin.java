package com.spoilageenhanced.mixin;

import com.spoilageenhanced.network.BlockSpoilageNetworking;
import com.spoilageenhanced.network.BlockSpoilageRequestPayload;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Receives {@link BlockSpoilageRequestPayload} from clients.
 *
 * <p>Targets ServerGamePacketListenerImpl rather than ServerCommonPacketListenerImpl: the game
 * listener overrides handleCustomPayload with an empty body, so an injection into the parent would
 * never run in the play phase. The method runs on the netty thread, so the actual work is pushed
 * onto the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerCustomPayloadMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private long spoilage_enhanced$lastRequestWindowStart = 0L;
    @Unique
    private int spoilage_enhanced$requestsInWindow = 0;
    /**
     * The limiter exists to stop a hostile client hammering the server, not to ration a HUD.
     *
     * <p>It used to be 4/s, which the normal game blows through instantly: every block the
     * crosshair crosses is one request, and sweeping a look across a room is easily twenty. Half
     * of all requests were being discarded, and because the discard was silent the HUD simply sat
     * on "checking" for up to a full limiter window — the one-second delay players reported. The
     * payload is a BlockPos and the answer takes about a millisecond, so the ceiling can be high
     * enough to never touch honest play and still bound abuse.</p>
     */
    @Unique
    private static final int MAX_REQUESTS_PER_SECOND = 60;

    @Unique
    private boolean spoilage_enhanced$loggedDropThisWindow = false;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void spoilage_enhanced$onCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (!(packet.payload() instanceof BlockSpoilageRequestPayload request)) {
            if ("spoilage_enhanced".equals(packet.payload().type().id().getNamespace())) {
                // The id made it through but the codec did not: the payload came back as
                // DiscardedPayload, which means ServerboundPayloadTypesMixin never applied.
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                        "Undecodable payload " + packet.payload().type().id() + " ("
                                + packet.payload().getClass().getSimpleName() + ") - serverbound codec not registered");
            }
            return;
        }

        long now = System.currentTimeMillis();
        boolean rateLimited = false;
        boolean shouldLogDrop = false;
        synchronized (this) {
            if (now - this.spoilage_enhanced$lastRequestWindowStart > 1000L) {
                this.spoilage_enhanced$lastRequestWindowStart = now;
                this.spoilage_enhanced$requestsInWindow = 1;
                this.spoilage_enhanced$loggedDropThisWindow = false;
            } else {
                this.spoilage_enhanced$requestsInWindow++;
                if (this.spoilage_enhanced$requestsInWindow > MAX_REQUESTS_PER_SECOND) {
                    rateLimited = true;
                    // Say it once per window: a discard nobody can see is indistinguishable from a
                    // lost packet, and that is exactly what hid this limiter for three iterations.
                    if (!this.spoilage_enhanced$loggedDropThisWindow) {
                        this.spoilage_enhanced$loggedDropThisWindow = true;
                        shouldLogDrop = true;
                    }
                }
            }
        }
        if (rateLimited) {
            if (shouldLogDrop) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                        "Rate limit hit: discarding block spoilage requests beyond "
                                + MAX_REQUESTS_PER_SECOND + "/s (first drop this window was " + request.pos() + ")");
            }
            // Discard immediately on the Netty thread without queuing to the server.
            ci.cancel();
            return;
        }

        ServerPlayer serverPlayer = this.player;
        if (serverPlayer == null) {
            return;
        }
        if (!(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return;
        }

        server.execute(() -> BlockSpoilageNetworking.handleRequest(serverPlayer, request.pos()));
        ci.cancel();
    }
}
