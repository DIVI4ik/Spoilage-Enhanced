package com.spoilageenhanced;

import com.mojang.serialization.DataResult;
import com.spoilageenhanced.component.SpoilageData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 215 regression test: SpoilageData NBT codec round-trip.
 *
 * <p>SpoilageData.CODEC is the NBT serialization used by ModDataComponentTypes
 * (line 10) — it crosses the wire on the item component sync and survives save/load.
 * All four fields are optional with documented defaults: missing fresh_expirations
 * → empty list, missing stale_expirations → empty list, missing rotten_count → 0,
 * missing speed_multiplier → 1.0. Pin the defaults so a future refactor cannot
 * silently change the missing-field behavior.</p>
 */
public class SpoilageDataNbtCodecTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SpoilageData roundTrip(SpoilageData original) {
        DataResult<net.minecraft.nbt.Tag> encoded = SpoilageData.CODEC.encodeStart(NbtOps.INSTANCE, original);
        net.minecraft.nbt.Tag tag = encoded.result()
                .orElseThrow(() -> new AssertionError("encode failed: " + encoded.error().get().message()));
        assertTrue(tag instanceof CompoundTag, "Encoded NBT must be a CompoundTag, got " + tag.getClass());
        DataResult<SpoilageData> decoded = SpoilageData.CODEC.parse(NbtOps.INSTANCE, tag);
        return decoded.result()
                .orElseThrow(() -> new AssertionError("decode failed: " + decoded.error().get().message()));
    }

    @Test
    void populatedDataRoundTrips() {
        SpoilageData original = new SpoilageData(
                List.of(100L, 200L, 300L),
                List.of(50L, 60L),
                3,
                2.5);
        SpoilageData decoded = roundTrip(original);
        assertEquals(original, decoded, "A populated SpoilageData must survive the NBT round-trip");
    }

    @Test
    void defaultDataRoundTrips() {
        SpoilageData decoded = roundTrip(SpoilageData.DEFAULT);
        assertEquals(SpoilageData.DEFAULT, decoded, "DEFAULT must survive the NBT round-trip");
    }

    @Test
    void emptyCompoundDecodesToDefault() {
        // The codec defines every field as optional with defaults. An empty CompoundTag
        // (e.g. a stripped component) must decode to DEFAULT, not throw.
        DataResult<SpoilageData> result = SpoilageData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag());
        SpoilageData decoded = result.result().orElseThrow(() ->
                new AssertionError("empty CompoundTag should decode via defaults, got: " + result.error().get().message()));
        assertEquals(SpoilageData.DEFAULT, decoded,
                "An empty CompoundTag must decode to SpoilageData.DEFAULT");
    }

    @Test
    void partialFieldsUseDefaults() {
        // Only fresh_expirations present; the other three fields must default.
        net.minecraft.nbt.ListTag longList = new net.minecraft.nbt.ListTag();
        for (long v : List.of(100L, 200L)) {
            longList.add(net.minecraft.nbt.LongTag.valueOf(v));
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("fresh_expirations", longList);

        DataResult<SpoilageData> result = SpoilageData.CODEC.parse(NbtOps.INSTANCE, nbt);
        SpoilageData decoded = result.result().orElseThrow(() ->
                new AssertionError("partial NBT should decode via defaults, got: " + result.error().get().message()));
        assertEquals(List.of(100L, 200L), decoded.freshExpirations(),
                "Present fresh_expirations must be used");
        assertTrue(decoded.staleExpirations().isEmpty(),
                "Missing stale_expirations must default to empty list");
        assertEquals(0, decoded.rottenCount(),
                "Missing rotten_count must default to 0");
        assertEquals(1.0, decoded.speedMultiplier(), 0.0,
                "Missing speed_multiplier must default to 1.0");
    }

    @Test
    void largeValuesRoundTrip() {
        SpoilageData original = new SpoilageData(
                List.of(Long.MAX_VALUE, Long.MIN_VALUE),
                List.of(0L),
                Integer.MAX_VALUE,
                100.0);
        SpoilageData decoded = roundTrip(original);
        assertEquals(original, decoded,
                "Extreme values (sentinels, Integer.MAX_VALUE rotten) must survive the NBT round-trip");
    }

    @Test
    void negativeRottenCountClampedAtDecode() {
        // Pass 459 (L7 — boundaries): the codec reads rotten_count as a raw int with no
        // validation, so a corrupt or hand-edited save can carry a negative value through.
        // The compact constructor clamps at construction; this test pins the codec path.
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("rotten_count", -5);
        DataResult<SpoilageData> result = SpoilageData.CODEC.parse(NbtOps.INSTANCE, nbt);
        SpoilageData decoded = result.result().orElseThrow(() ->
                new AssertionError("negative rotten_count should decode via clamp, got: " + result.error().get().message()));
        assertEquals(0, decoded.rottenCount(),
                "Negative rotten_count must be clamped to 0 at construction");
    }
}
