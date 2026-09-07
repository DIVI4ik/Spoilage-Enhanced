package com.spoilageenhanced;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 442 regression test: ItemEntityMixin phase-spreading math.
 *
 * <p>ItemEntityMixin gates per-item spoilage work on
 * {@code (entityId + tickCount) % 20 == 0}. The property that makes this correct is
 * subtle and worth pinning, because a wrong variant of the same idea (plain
 * {@code tickCount % 20}) shipped a synchronized load spike: every dropped item in the
 * world ticked on the same global boundary, so a field of 1,000 items produced a
 * once-per-second spike instead of 50 items per tick.</p>
 *
 * <p>This test pins the two properties the mixin relies on:</p>
 * <ol>
 *   <li><b>Spread</b> — a burst of sequential entity ids distributes evenly across the
 *       20 ticks of the window (a 10,000-item burst lands at 500 per tick, not 10,000 on
 *       one tick).</li>
 *   <li><b>Exactly once</b> — each id fires exactly once per 20-tick window, because
 *       tickCount advances by 1 per tick and the condition is a fixed residue.</li>
 * </ol>
 *
 * <p>The mixin itself needs a live ItemEntity, so the math is tested here as a pure
 * function of (id, tickCount).</p>
 */
public class PhaseSpreadingTest {

    /** Mirrors ItemEntityMixin's gate: (entityId + tickCount) % 20 == 0. */
    private static boolean fires(int entityId, int tickCount) {
        return (entityId + tickCount) % 20 == 0;
    }

    @Test
    void burstOfTenThousandSpreadsEvenly() {
        // A mass break / drop event creates thousands of item entities with sequential ids
        // in the same tick. The whole point of the offset is that they do NOT all fire at
        // once: the ids cover all 20 residues, so the work is spread across the window.
        int firstId = 1000;
        int count = 10_000;
        int[] perTick = new int[20];
        for (int id = firstId; id < firstId + count; id++) {
            for (int tick = 0; tick < 20; tick++) {
                if (fires(id, tick)) {
                    perTick[tick]++;
                }
            }
        }
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(count / 20, perTick[tick],
                    "tick " + tick + " must carry exactly 1/20th of the burst ("
                            + count + "/20 = " + (count / 20) + "), got " + perTick[tick]);
        }
    }

    @Test
    void eachItemFiresExactlyOncePerWindow() {
        // tickCount advances by 1 per tick. For a fixed id, (id + tickCount) % 20 == 0
        // holds for exactly one tickCount in any 20 consecutive ticks — so each item's
        // spoilage work runs exactly once per 20-tick window, never zero, never twice.
        for (int id = 0; id < 200; id++) {
            int fires = 0;
            for (int tick = 0; tick < 20; tick++) {
                if (fires(id, tick)) {
                    fires++;
                }
            }
            assertEquals(1, fires,
                    "id " + id + " must fire exactly once per 20-tick window, fired " + fires);
        }
    }

    @Test
    void consecutiveWindowsKeepTheSamePhase() {
        // The entity id is stable for the entity's lifetime, so an item that fires on
        // tickCount T also fires on T+20, T+40, ... — the phase never drifts, which is
        // what keeps the per-tick load flat across windows (no re-synchronisation).
        for (int id = 0; id < 50; id++) {
            int firstFire = -1;
            for (int tick = 0; tick < 20; tick++) {
                if (fires(id, tick)) {
                    firstFire = tick;
                    break;
                }
            }
            assertTrue(firstFire >= 0, "id " + id + " must fire somewhere in the window");
            for (int window = 1; window <= 5; window++) {
                int tick = firstFire + 20 * window;
                assertTrue(fires(id, tick),
                        "id " + id + " must fire at the same phase in window " + window);
                // And nowhere else in that window.
                for (int drift = 1; drift < 20; drift++) {
                    assertFalse(fires(id, tick + drift),
                            "id " + id + " must not fire off-phase (tick " + (tick + drift) + ")");
                }
            }
        }
    }

    @Test
    void plainTickCountModuloWouldSpike() {
        // The counterfactual that justifies the offset: with plain tickCount % 20, every
        // item in the world fires on the SAME tick — 10,000 items on one tick, zero on the
        // other 19. This test documents the spike the offset removes.
        int count = 10_000;
        Map<Integer, Integer> perTick = new HashMap<>();
        for (int tick = 0; tick < 20; tick++) {
            perTick.put(tick, 0);
        }
        for (int item = 0; item < count; item++) {
            for (int tick = 0; tick < 20; tick++) {
                if (tick % 20 == 0) {
                    perTick.merge(tick, 1, Integer::sum);
                }
            }
        }
        assertEquals(count, perTick.get(0),
                "the un-offset form puts the whole burst on tick 0 — the spike the fix removes");
        for (int tick = 1; tick < 20; tick++) {
            assertEquals(0, perTick.get(tick),
                    "the un-offset form leaves every other tick idle");
        }
    }
}