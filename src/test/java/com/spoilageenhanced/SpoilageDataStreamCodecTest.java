package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 288 regression test: SpoilageData.STREAM_CODEC round-trip.
 *
 * <p>STREAM_CODEC is used to serialize SpoilageData over the network (block spoilage
 * response payload). This test verifies that encode/decode preserves all fields.</p>
 */
public class SpoilageDataStreamCodecTest {

    @Test
    void emptyDataRoundTrip() {
        SpoilageData original = SpoilageData.DEFAULT;
        ByteBuf buf = Unpooled.buffer();
        SpoilageData.STREAM_CODEC.encode(buf, original);
        SpoilageData decoded = SpoilageData.STREAM_CODEC.decode(buf);
        assertEquals(original, decoded, "Empty data must round-trip");
    }

    @Test
    void populatedDataRoundTrip() {
        SpoilageData original = new SpoilageData(
                List.of(1000L, 2000L, 3000L),
                List.of(500L, 600L),
                3,
                2.5);
        ByteBuf buf = Unpooled.buffer();
        SpoilageData.STREAM_CODEC.encode(buf, original);
        SpoilageData decoded = SpoilageData.STREAM_CODEC.decode(buf);
        assertEquals(original, decoded, "Populated data must round-trip");
        assertEquals(3, decoded.freshExpirations().size(), "Fresh count must be preserved");
        assertEquals(2, decoded.staleExpirations().size(), "Stale count must be preserved");
        assertEquals(3, decoded.rottenCount(), "Rotten count must be preserved");
        assertEquals(2.5, decoded.speedMultiplier(), 0.0, "Speed multiplier must be preserved");
    }

    @Test
    void rottenOnlyDataRoundTrip() {
        SpoilageData original = new SpoilageData(List.of(), List.of(), 10, 1.0);
        ByteBuf buf = Unpooled.buffer();
        SpoilageData.STREAM_CODEC.encode(buf, original);
        SpoilageData decoded = SpoilageData.STREAM_CODEC.decode(buf);
        assertEquals(original, decoded, "Rotten-only data must round-trip");
        assertEquals(10, decoded.rottenCount(), "Rotten count must be preserved");
    }

    @Test
    void neverSentinelRoundTrips() {
        // Pass 556 (L7 — boundary): Long.MAX_VALUE is the NEVER sentinel that
        // extractWorstItems/extractBestItems padding writes for untracked stack slots
        // (BUG-13) and that rescaleItemTimestamps clamps to. It travels as a 10-byte
        // varlong; a decode that read it as unsigned would produce a negative expiration,
        // which every consumer would read as "already expired" — the item would look
        // rotten the moment it crossed the wire.
        SpoilageData original = new SpoilageData(
                List.of(Long.MAX_VALUE, 1000L),
                List.of(Long.MAX_VALUE),
                0,
                1.0);
        ByteBuf buf = Unpooled.buffer();
        SpoilageData.STREAM_CODEC.encode(buf, original);
        SpoilageData decoded = SpoilageData.STREAM_CODEC.decode(buf);
        assertEquals(Long.MAX_VALUE, decoded.freshExpirations().get(0),
                "NEVER (Long.MAX_VALUE) must survive the varlong round-trip exactly — a sign "
                        + "loss here makes an untracked slot read as already expired");
        assertEquals(Long.MAX_VALUE, decoded.staleExpirations().get(0),
                "The stale side carries the same sentinel and must survive too");
        assertEquals(1000L, decoded.freshExpirations().get(1),
                "A real expiration next to the sentinel must not be corrupted by it");
        backing_release(buf);
    }

    @Test
    void negativeRottenCountClampedAtDecode() {
        // Pass 608 (L8 — data round-trip): the STREAM_CODEC reads rotten_count as a raw
        // varint with no validation, so a corrupt or hand-edited packet can carry a
        // negative value through. The compact constructor clamps at construction; this
        // test pins the codec path.
        ByteBuf buf = Unpooled.buffer();
        // Manually encode a SpoilageData with negative rotten_count: the codec writes
        // fresh_expirations list (size + elements), stale_expirations list (size + elements),
        // rotten_count (varint), speed_multiplier (double).
        // Write empty fresh list (size 0)
        buf.writeInt(0);
        // Write empty stale list (size 0)
        buf.writeInt(0);
        // Write negative rotten_count as varint (zigzag: -5 -> 9)
        buf.writeInt(9);
        // Write speed_multiplier
        buf.writeDouble(1.0);

        SpoilageData decoded = SpoilageData.STREAM_CODEC.decode(buf);
        assertEquals(0, decoded.rottenCount(),
                "Negative rotten_count must be clamped to 0 at construction");
        assertEquals(0, decoded.totalTracked(),
                "totalTracked must count only real entries after clamping");
        backing_release(buf);
    }

    private static void backing_release(ByteBuf buf) {
        buf.release();
    }
}
