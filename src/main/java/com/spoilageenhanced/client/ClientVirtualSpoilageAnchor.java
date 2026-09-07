package com.spoilageenhanced.client;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable time origin for stacks the server has not stamped yet.
 *
 * <p>A stack taken from a creative tab, crafted, or pulled out of a container carries no
 * {@code spoilage_enhanced:spoilage} component until the server's first tick over it. The tooltip
 * still has to show a countdown for those, and the naive way to get one — {@code now +
 * freshDuration}, recomputed every frame — produces a frozen number, because the origin moves
 * forward exactly as fast as the clock (CLAUDE.md section 4).</p>
 *
 * <p>So the origin is remembered once, on the first frame the stack is seen, and reused after that.
 * From then on {@code anchor + freshDuration - now} shrinks with every game tick, which is what a
 * countdown is supposed to do.</p>
 *
 * <p>Keyed by identity hash: the client keeps the same {@link ItemStack} instance for a slot
 * between inventory syncs, which comfortably outlives the short window before the real component
 * arrives. The item is stored alongside the origin so a recycled hash cannot hand an apple the
 * origin of a steak, and entries expire on their own so a long session cannot accumulate them.</p>
 */
public final class ClientVirtualSpoilageAnchor {

    /**
     * How many un-stamped stacks to remember at once. The player can see a full inventory plus a
     * container at the same time, and every slot may be waiting for its first server tick, so this
     * is sized for that rather than for the handful actually hovered.
     */
    private static final int MAX_ENTRIES = 256;

    /**
     * Forget an origin nobody asked about for this long. The real component normally lands within
     * a tick or two; anything still virtual after half a minute is a stack that was put away, and
     * re-anchoring it costs nothing.
     */
    private static final long ENTRY_TTL_TICKS = 600L;

    private record Anchor(int itemHash, long firstSeenGameTime, long lastTouchedGameTime) {}

    /**
     * Pass 107 (Lens 8): access-ordered LinkedHashMap replaces the HashMap + ArrayDeque pair.
     * The old put() did LRU.remove((Integer) key) — a LINEAR SCAN of the 256-entry deque — on
     * every re-put, and firstSeen() re-puts on every call (every tooltip frame for a virtual
     * stack). LinkedHashMap(accessOrder=true) maintains LRU position inside get()/put() in
     * O(1), and removeEldestEntry enforces the cap. Client-side only (render thread).
     */
    private static final Map<Integer, Anchor> ANCHORS = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Anchor> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private ClientVirtualSpoilageAnchor() {
    }

    /**
     * Game time to count the stack's freshness from. The first call for a stack records
     * {@code currentGameTime}; later calls return that same origin.
     *
     * <p>Pass 152 (Lens 12 — claim drift): every call also refreshes the entry's
     * {@code lastTouchedGameTime}, so the {@link #ENTRY_TTL_TICKS} window is measured from the
     * LAST call, not from the first sighting. A stack that is being rendered every frame never
     * expires — the TTL only fires for a stack nobody asked about for 600 ticks (put away in a
     * chest, moved off-screen). The old javadoc said "later calls return that same origin"
     * without mentioning the touch refresh, which read as "the origin expires 600 ticks after
     * the first sighting even while in view" — it does not.</p>
     */
    public static long firstSeen(ItemStack stack, long currentGameTime) {
        int key = System.identityHashCode(stack);
        int itemHash = stack.getItem().hashCode();

        Anchor existing = ANCHORS.get(key);
        if (existing != null
                && existing.itemHash() == itemHash
                && currentGameTime >= existing.firstSeenGameTime()
                && currentGameTime - existing.lastTouchedGameTime() <= ENTRY_TTL_TICKS) {
            // Pass 198 (L5 — render-path): the put only refreshes lastTouchedGameTime (the
            // LRU position is already maintained by the get() above on this access-ordered
            // LinkedHashMap). When the tooltip re-renders within the same tick — up to 20
            // frames in a single second — we can skip the put, saving one map write per
            // frame per virtual stack. The TTL still fires on the first frame of the next
            // tick.
            if (currentGameTime != existing.lastTouchedGameTime()) {
                ANCHORS.put(key, new Anchor(itemHash, existing.firstSeenGameTime(), currentGameTime));
            }
            return existing.firstSeenGameTime();
        }

        // No usable origin: either brand new, or the world clock jumped backwards (world switch,
        // /time set), or the hash got recycled onto a different item. Start again from now.
        ANCHORS.put(key, new Anchor(itemHash, currentGameTime, currentGameTime));
        return currentGameTime;
    }

    /** Drop everything. Called on disconnect so a new world does not inherit stale origins. */
    public static void clear() {
        ANCHORS.clear();
    }
}
