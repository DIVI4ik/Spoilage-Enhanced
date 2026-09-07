package com.spoilageenhanced.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modern Data Component holding spoilage state for ItemStacks in Minecraft 26.2+.
 */
public record SpoilageData(
        List<Long> freshExpirations,
        List<Long> staleExpirations,
        int rottenCount,
        double speedMultiplier
) {
    public static final SpoilageData DEFAULT = new SpoilageData(Collections.emptyList(), Collections.emptyList(), 0, 1.0);

    public static final Codec<SpoilageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.listOf().optionalFieldOf("fresh_expirations", Collections.emptyList()).forGetter(SpoilageData::freshExpirations),
                    Codec.LONG.listOf().optionalFieldOf("stale_expirations", Collections.emptyList()).forGetter(SpoilageData::staleExpirations),
                    Codec.INT.optionalFieldOf("rotten_count", 0).forGetter(SpoilageData::rottenCount),
                    Codec.DOUBLE.optionalFieldOf("speed_multiplier", 1.0).forGetter(SpoilageData::speedMultiplier)
            ).apply(instance, SpoilageData::new)
    );

    public static final StreamCodec<ByteBuf, SpoilageData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), SpoilageData::freshExpirations,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), SpoilageData::staleExpirations,
            ByteBufCodecs.VAR_INT, SpoilageData::rottenCount,
            ByteBufCodecs.DOUBLE, SpoilageData::speedMultiplier,
            SpoilageData::new
    );

    public SpoilageData {
        // Pass 88 (Lens 13): empty lists skip the defensive copy entirely — the shared
        // immutable Collections.emptyList() is safe to store directly because every reader
        // only iterates/sizes it, and every writer path (updateSpoilageData, mergeItems,
        // extract*) replaces the reference with a fresh ArrayList before mutating. This
        // avoids allocating an empty ArrayList wrapper for every component-less side of a
        // SpoilageData (e.g. all-fresh stacks, all-rotten stacks, DEFAULT).
        freshExpirations = copyIfNotEmpty(freshExpirations);
        staleExpirations = copyIfNotEmpty(staleExpirations);
        // Pass 459 (L7 — boundaries): the codec reads rotten_count as a raw int with no
        // validation, so a corrupt or hand-edited save can carry a negative value through
        // into totalTracked() — and extractWorstItems would then copy that negative count
        // into a freshly built target (rottenTake = min(sourceRotten, amount) goes
        // negative), surfacing as a negative count in the tooltip. Clamp at construction:
        // every writer path already produces non-negative counts, so this only affects
        // corrupt input.
        rottenCount = Math.max(0, rottenCount);
        // Pass 663 (L7 — boundary): the codec reads speed_multiplier as a raw double with
        // no validation, so a corrupt or hand-edited save can carry 0, negative, NaN, or
        // Infinity through. rescaleItemTimestamps (Pass 654) guards ratio <= 0, but a
        // direct read of speedMultiplier (e.g. for HUD display or per-item duration
        // calculation) would divide by zero or produce garbage. Clamp to a sane default
        // at construction: every writer path already produces positive finite values.
        if (!Double.isFinite(speedMultiplier) || speedMultiplier <= 0.0d) {
            speedMultiplier = 1.0d;
        }
    }

    private static List<Long> copyIfNotEmpty(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    public int totalTracked() {
        return freshExpirations.size() + staleExpirations.size() + rottenCount;
    }

    public boolean isEmpty() {
        return freshExpirations.isEmpty() && staleExpirations.isEmpty() && rottenCount == 0;
    }

    /**
     * Pass 91 (Lens 13): custom equals/hashCode that short-circuits on primitive fields and
     * uses primitive long comparisons instead of boxed Long.equals on the list elements.
     * Records auto-generate these via List.equals -> Long.equals, which boxes every element.
     * For a 64-item stack the auto-generated equals does 128 boxed comparisons; this version
     * does primitive field checks first and exits early on any mismatch.
     *
     * <p>Semantic contract MUST match the record's auto-generated version. The fields
     * freshExpirations and staleExpirations are List&lt;Long&gt;, so list equality is order-
     * sensitive (which is what List.equals does). We preserve that.</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SpoilageData other)) return false;
        if (rottenCount != other.rottenCount) return false;
        if (Double.compare(speedMultiplier, other.speedMultiplier) != 0) return false;
        if (!freshExpirations.equals(other.freshExpirations)) return false;
        return staleExpirations.equals(other.staleExpirations);
    }

    @Override
    public int hashCode() {
        // Same field order as the record's auto-generated Objects.hash(fresh, stale,
        // rottenCount, speedMultiplier) — expanded to avoid the varargs Object[] allocation.
        int h = 1;
        h = 31 * h + freshExpirations.hashCode();
        h = 31 * h + staleExpirations.hashCode();
        h = 31 * h + rottenCount;
        h = 31 * h + Double.hashCode(speedMultiplier);
        return h;
    }
}
