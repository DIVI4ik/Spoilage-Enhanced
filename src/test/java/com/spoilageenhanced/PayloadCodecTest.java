package com.spoilageenhanced;

import com.spoilageenhanced.network.BlockSpoilageRequestPayload;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 154 regression test: payload StreamCodec round-trips.
 *
 * The codecs are the wire format between client and server. A mismatch between the
 * write order and the read order (or a width change on one side only) silently
 * corrupts every HUD answer — the payload decodes as garbage or throws mid-packet,
 * which looks like "the HUD never answers" in production. These tests pin the
 * round-trip for both payloads.
 */
public class PayloadCodecTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void releaseBuffers() {
        // Each test allocates its own buf and releases it inline; nothing to do here,
        // but keeping the hook documents the lifecycle.
    }

    @Test
    void responsePayloadRoundTrips() {
        BlockPos pos = new BlockPos(-300, 64, 12345);
        int state = 2; // STALE
        long ticks = 987654321L;
        double multiplier = 3.75;

        BlockSpoilageResponsePayload original = new BlockSpoilageResponsePayload(pos, state, ticks, multiplier);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageResponsePayload.STREAM_CODEC.encode(buf, original);

        BlockSpoilageResponsePayload decoded = BlockSpoilageResponsePayload.STREAM_CODEC.decode(buf);

        assertEquals(pos, decoded.pos(), "Position must survive the round-trip");
        assertEquals(state, decoded.state(), "State ordinal must survive the round-trip");
        assertEquals(ticks, decoded.ticksRemaining(), "Ticks remaining must survive the round-trip");
        assertEquals(multiplier, decoded.speedMultiplier(), 0.0, "Speed multiplier must survive the round-trip");
        assertEquals(0, buf.readableBytes(), "The buffer must be fully consumed");
        backing.release();
    }

    @Test
    void responsePayloadStateNoneRoundTrips() {
        BlockPos pos = new BlockPos(0, 0, 0);
        BlockSpoilageResponsePayload original = new BlockSpoilageResponsePayload(
                pos, BlockSpoilageResponsePayload.STATE_NONE, 0L, 1.0);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageResponsePayload.STREAM_CODEC.encode(buf, original);
        BlockSpoilageResponsePayload decoded = BlockSpoilageResponsePayload.STREAM_CODEC.decode(buf);

        assertEquals(BlockSpoilageResponsePayload.STATE_NONE, decoded.state(),
                "STATE_NONE (-1) must survive the round-trip — it is written as a byte");
        assertEquals(0L, decoded.ticksRemaining());
        assertEquals(1.0, decoded.speedMultiplier(), 0.0);
        backing.release();
    }

    @Test
    void responsePayloadExtremeValuesRoundTrip() {
        // BlockPos packing holds 26 bits for X/Z (±33,554,431 — the world border) and
        // 12 signed bits for Y (±2048). Values beyond that are not representable on the
        // wire, so "extreme" here means the largest representable coordinates.
        BlockPos pos = new BlockPos(33554431, 2047, -33554431);
        // The state field is written as a SIGNED byte (writeByte/readByte), so the
        // representable range is [-128, 127]. The mod uses ordinals 0..2 plus
        // STATE_NONE (-1) — all well inside. 127 is the largest value that survives.
        BlockSpoilageResponsePayload original = new BlockSpoilageResponsePayload(
                pos, 127, Long.MAX_VALUE, Double.MAX_VALUE);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageResponsePayload.STREAM_CODEC.encode(buf, original);
        BlockSpoilageResponsePayload decoded = BlockSpoilageResponsePayload.STREAM_CODEC.decode(buf);

        assertEquals(pos, decoded.pos(), "Largest representable BlockPos must survive the round-trip");
        assertEquals(127, decoded.state(), "State 127 (max signed byte) must survive the round-trip");
        assertEquals(Long.MAX_VALUE, decoded.ticksRemaining(), "Max long ticks must survive the round-trip");
        assertEquals(Double.MAX_VALUE, decoded.speedMultiplier(), 0.0, "Max double must survive the round-trip");
        backing.release();
    }

    @Test
    void responsePayloadNoTimerSentinelRoundTrips() {
        // Pass 556 (L7 — boundary): NO_TIMER (-1L) is a wire value that versions 1.0.48–1.0.49
        // actually send (see the constant's javadoc). The varlong encoding spreads a negative
        // long across 10 groups, and a decode that read it as unsigned would turn it into a
        // huge countdown — the HUD would show days remaining on a block that never ages.
        // The extreme-values test above pins Long.MAX_VALUE; this pins the negative sentinel.
        BlockPos pos = new BlockPos(0, 64, 0);
        BlockSpoilageResponsePayload original = new BlockSpoilageResponsePayload(
                pos, 0, BlockSpoilageResponsePayload.NO_TIMER, 1.0);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageResponsePayload.STREAM_CODEC.encode(buf, original);
        BlockSpoilageResponsePayload decoded = BlockSpoilageResponsePayload.STREAM_CODEC.decode(buf);

        assertEquals(BlockSpoilageResponsePayload.NO_TIMER, decoded.ticksRemaining(),
                "NO_TIMER (-1L) must survive the varlong round-trip exactly — a sign loss here "
                        + "renders the sentinel as a huge countdown instead of no timer");
        backing.release();
    }

    @Test
    void requestPayloadRoundTrips() {
        BlockPos pos = new BlockPos(-1, -64, -1);
        BlockSpoilageRequestPayload original = new BlockSpoilageRequestPayload(pos);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageRequestPayload.STREAM_CODEC.encode(buf, original);
        BlockSpoilageRequestPayload decoded = BlockSpoilageRequestPayload.STREAM_CODEC.decode(buf);

        assertEquals(pos, decoded.pos(), "Request position must survive the round-trip");
        assertEquals(0, buf.readableBytes(), "The buffer must be fully consumed");
        backing.release();
    }

    @Test
    void requestPayloadExtremePositionRoundTrips() {
        // Smallest representable coordinates (26-bit X/Z, 12-bit signed Y)
        BlockPos pos = new BlockPos(-33554431, -2048, -33554431);
        BlockSpoilageRequestPayload original = new BlockSpoilageRequestPayload(pos);

        ByteBuf backing = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(backing);
        BlockSpoilageRequestPayload.STREAM_CODEC.encode(buf, original);
        BlockSpoilageRequestPayload decoded = BlockSpoilageRequestPayload.STREAM_CODEC.decode(buf);

        assertEquals(pos, decoded.pos(), "Smallest representable BlockPos must survive the round-trip");
        backing.release();
    }

    @Test
    void responsePayloadTypeIsStable() {
        // The TYPE id is part of the wire format — changing it breaks old clients.
        assertEquals("spoilage_enhanced:block_spoilage_response",
                BlockSpoilageResponsePayload.TYPE.id().toString(),
                "Response payload type id must stay stable");
        assertEquals("spoilage_enhanced:block_spoilage_request",
                BlockSpoilageRequestPayload.TYPE.id().toString(),
                "Request payload type id must stay stable");
    }
}