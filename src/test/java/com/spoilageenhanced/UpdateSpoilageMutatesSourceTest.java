package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1042 regression test: updateSpoilageDataImpl must not mutate the source SpoilageData.
 *
 * <p>The missing-tracker branch (actualCount > totalTracked, totalTracked > 0) used to call
 * staleList.add() / freshList.add() directly on the references returned by the accessor,
 * which are the record's stored ArrayLists. That mutated the component still attached to
 * the stack until stack.set() was called — a stack that had been updated once would carry
 * stale padding forever, and a second update would see the inflated list.</p>
 */
public class UpdateSpoilageMutatesSourceTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void missingTrackerDoesNotMutateSource() {
        // Source has 2 fresh trackers, count is 4 — 2 missing, totalTracked > 0.
        List<Long> fresh = new ArrayList<>(List.of(100L, 200L));
        SpoilageData source = new SpoilageData(fresh, Collections.emptyList(), 0, 1.0);

        SpoilageData result = FoodSpoilageUtil.updateSpoilageData(source, 4, 50_000L, 24000L, 24000L, 1.0);

        // The result must carry 4 trackers (2 original + 2 padded worst).
        assertEquals(4, result.totalTracked(),
                "updateSpoilageData must pad to the requested count");
        // The source must be untouched — the record's stored list must still hold 2 entries.
        assertEquals(2, source.freshExpirations().size(),
                "updateSpoilageData must NOT mutate the source fresh list");
    }

    @Test
    void missingTrackerDoesNotMutateSourceStale() {
        // Source has 2 stale trackers, count is 4 — 2 missing, totalTracked > 0.
        List<Long> stale = new ArrayList<>(List.of(100L, 200L));
        SpoilageData source = new SpoilageData(Collections.emptyList(), stale, 0, 1.0);

        SpoilageData result = FoodSpoilageUtil.updateSpoilageData(source, 4, 50_000L, 24000L, 24000L, 1.0);

        assertEquals(4, result.totalTracked(),
                "updateSpoilageData must pad to the requested count");
        assertEquals(2, source.staleExpirations().size(),
                "updateSpoilageData must NOT mutate the source stale list");
    }

    @Test
    void missingTrackerDoesNotMutateSourceRotten() {
        // Source has 2 rotten, count is 4 — 2 missing, totalTracked > 0.
        SpoilageData source = new SpoilageData(Collections.emptyList(), Collections.emptyList(), 2, 1.0);

        SpoilageData result = FoodSpoilageUtil.updateSpoilageData(source, 4, 50_000L, 24000L, 24000L, 1.0);

        assertEquals(4, result.totalTracked(),
                "updateSpoilageData must pad to the requested count");
        assertEquals(2, source.rottenCount(),
                "updateSpoilageData must NOT mutate the source rotten count");
    }
}
