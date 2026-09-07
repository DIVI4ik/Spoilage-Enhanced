package com.spoilageenhanced;

import com.mojang.serialization.DataResult;
import com.spoilageenhanced.component.SpoilageData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 289 regression test: SpoilageData.CODEC round-trip.
 *
 * <p>CODEC is used to serialize SpoilageData to NBT (save/load). This test verifies
 * that encode/decode preserves all fields.</p>
 */
public class SpoilageDataCodecTest {

    @Test
    void emptyDataRoundTrip() {
        SpoilageData original = SpoilageData.DEFAULT;
        DataResult<net.minecraft.nbt.Tag> encoded = SpoilageData.CODEC.encodeStart(
                net.minecraft.nbt.NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "Encode must succeed");
        DataResult<SpoilageData> decoded = SpoilageData.CODEC.parse(
                net.minecraft.nbt.NbtOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "Decode must succeed");
        assertEquals(original, decoded.result().get(), "Empty data must round-trip");
    }

    @Test
    void populatedDataRoundTrip() {
        SpoilageData original = new SpoilageData(
                List.of(1000L, 2000L, 3000L),
                List.of(500L, 600L),
                3,
                2.5);
        DataResult<net.minecraft.nbt.Tag> encoded = SpoilageData.CODEC.encodeStart(
                net.minecraft.nbt.NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "Encode must succeed");
        DataResult<SpoilageData> decoded = SpoilageData.CODEC.parse(
                net.minecraft.nbt.NbtOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "Decode must succeed");
        assertEquals(original, decoded.result().get(), "Populated data must round-trip");
    }
}
