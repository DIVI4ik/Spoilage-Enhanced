package com.spoilageenhanced.client;

import com.spoilageenhanced.network.BlockSpoilageRequestPayload;
import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import com.spoilageenhanced.mixin.ClientCommonPacketListenerImplAccessor;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.spoilageenhanced.config.SpoilageConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only cache for the blocks the crosshair visits. Block spoilage is server-side state, so
 * the HUD asks for it and renders whatever the last answer was.
 *
 * <p>BUG-13 (CLIENT-HUD-01): the cache used to hold a single entry, so every slight crosshair
 * move to a previously-seen block erased it and forced a fresh request — the HUD then went blank
 * for a round-trip (0.1–0.5 s). It now keeps a small LRU of recent answers (the crosshair sweeps
 * back and forth over the same few blocks) and serves them instantly while a refresh is in
 * flight.</p>
 *
 * <p>CLIENT ONLY — never load this from common or server code.</p>
 */
public final class ClientBlockSpoilageCache {

    /** Do not ask again for the same block more often than this. */
    private static final long REFRESH_INTERVAL_TICKS = 20L;
    /** Blocks that are not spoilable at all barely need refreshing. */
    private static final long IDLE_REFRESH_INTERVAL_TICKS = 200L;
    /**
     * While the client's world and the server's answer disagree about a block, re-ask on the very
     * next tick. The disagreement only happens for the tick or two between placing a block — which
     * the client shows immediately — and the server processing that placement, so this costs one
     * or two extra packets and removes the visible pause entirely.
     */
    private static final long DISAGREEMENT_REFRESH_INTERVAL_TICKS = 1L;
    /**
     * Give up on an unanswered request after this long and allow a retry.
     *
     * <p>The server answers in 1–25 ms, so anything still unanswered after a couple of ticks is
     * lost, not slow. This used to be 40 ticks: a single dropped request then pinned the HUD on
     * "checking" for two full seconds, which is the delay the player actually saw once the refresh
     * intervals were fixed. A retry costs one tiny payload, so waiting longer buys nothing.</p>
     */
    private static final long REQUEST_TIMEOUT_TICKS = 2L;
    /**
     * How many recent blocks to remember. The crosshair only ever sweeps a small neighborhood,
     * so 128 entries cover a wide look-around without meaningful memory cost.
     */
    private static final int MAX_ENTRIES = 128;

    /**
     * An answer plus the block it was an answer about. Without the block the cache happily serves
     * a verdict for whatever used to stand at those coordinates: looking at an empty spot cached
     * "nothing here", and a crate placed there a second later stayed unrecognised for the whole
     * idle interval, because a "nothing here" entry is only refreshed every
     * {@link #IDLE_REFRESH_INTERVAL_TICKS} ticks.
     *
     * <p>Pass 140 (Lens 5): {@code looksSpoilable} is computed once when the answer arrives and
     * stored here. The old code recomputed it EVERY FRAME in {@link #requestIfStale} for any block
     * whose answer was STATE_NONE — which is every non-spoilable block the crosshair rests on
     * (stone, dirt, cobble — the most common case in the game), paying a chunk-section read plus
     * two CHM gets per frame. The flag stays valid for the entry's lifetime because {@link #peek}
     * already drops the entry the moment the block at the position changes.</p>
     */
    private record CachedAnswer(int state, long ticksRemaining, double speedMultiplier, long atGameTime,
            Block block, boolean looksSpoilable) {}

    /**
     * Pass 104 (Lens 8): access-ordered LinkedHashMap replaces the HashMap + ArrayDeque pair.
     * The old touch() did a LINEAR SCAN of the 128-entry deque on every cache hit — O(n) per
     * HUD render frame that queried a cached block. LinkedHashMap(accessOrder=true) maintains
     * LRU position inside get()/put() in O(1), and the eldest-entry removal replaces the
     * manual poll loop. Client-side only: all access happens on the render thread.
     */
    private static final Map<BlockPos, CachedAnswer> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<BlockPos, CachedAnswer> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static String cachedDimension;
    // Pass 206 (L3 — cache correctness): the dimension check alone misses server switches
    // within the same dimension (hub world, server rejoin). Track the connection identity
    // too — a new connection is always a fresh world with stale-data answers for every
    // coordinate, so the cache must clear on connection change as well.
    private static int cachedConnectionHash;
    private static BlockPos pendingPos;
    private static long pendingSinceGameTime;

    /**
     * Per-tick memo for {@link #currentBlockAt}. The HUD reads the same block 4-5 times per
     * render frame (requestIfStale + getState + looksSpoilableCached + getTicksRemaining +
     * getSpeedMultiplier), and the value can only change between ticks, so the chunk-section
     * read is done once per tick for the most-recently-queried pos. Pass 195 (Lens 4 — TPS):
     * this replaces 4-5 chunk-section reads/frame with 1.
     */
    private static BlockPos memoedBlockPos;
    private static long memoedBlockAtTick = -1L;
    private static Block memoedBlock;

    private ClientBlockSpoilageCache() {
    }

    /** Called from the network thread hop-over in ClientCustomPayloadMixin (render thread). */
    public static void accept(BlockSpoilageResponsePayload response) {
        Block block = currentBlockAt(response.pos());
        boolean looksSpoilable = block != null && looksSpoilable(block);
        put(response.pos(), new CachedAnswer(response.state(), response.ticksRemaining(),
                response.speedMultiplier(), currentGameTime(), block, looksSpoilable));
        if (response.pos().equals(pendingPos)) {
            pendingPos = null;
        }
        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                "Received block spoilage for " + response.pos() + ": state=" + response.state()
                        + ", ticks=" + response.ticksRemaining());
    }

    /**
     * Asks the server about {@code pos} unless a fresh answer (or an in-flight request) already
     * covers it.
     */
    public static void requestIfStale(BlockPos pos) {
        if (pos == null) {
            return;
        }
        long now = currentGameTime();

        if (pos.equals(pendingPos) && now - pendingSinceGameTime < REQUEST_TIMEOUT_TICKS) {
            return;
        }
        int currentConnHash = currentConnectionHash();
        if (!java.util.Objects.equals(cachedDimension, currentDimension())
                || currentConnHash != cachedConnectionHash) {
            // Changed world/dimension OR server connection: every previous answer is
            // meaningless now. The dimension check misses a same-dimension rejoin (hub
            // world) where the new server has different block states at every coordinate.
            CACHE.clear();
            pendingPos = null;
        }
        cachedConnectionHash = currentConnHash;

        CachedAnswer entry = peek(pos);
        long refreshInterval;
        if (entry == null || entry.state() != BlockSpoilageResponsePayload.STATE_NONE) {
            refreshInterval = REFRESH_INTERVAL_TICKS;
        } else if (entry.looksSpoilable()) {
            // Our own world says this block is spoilable and the server says nothing is there.
            // That disagreement is normal for a beat after placing a block — the client shows it
            // immediately, the server has not processed the placement yet — so the "nothing here"
            // answer is about air that is already gone. Ask again next tick: anything slower is a
            // pause the player sees, and the answer that clears it is one tick away.
            refreshInterval = DISAGREEMENT_REFRESH_INTERVAL_TICKS;
        } else {
            refreshInterval = IDLE_REFRESH_INTERVAL_TICKS;
        }
        if (entry != null && now - entry.atGameTime() < refreshInterval) {
            touch(pos);
            return;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }

        pendingPos = pos.immutable();
        pendingSinceGameTime = now;
        ServerboundCustomPayloadPacket packet = new ServerboundCustomPayloadPacket(new BlockSpoilageRequestPayload(pendingPos));
        try {
            if (connection instanceof ClientCommonPacketListenerImplAccessor accessor) {
                accessor.spoilage_enhanced$getConnection().send(packet);
            } else {
                connection.send(packet);
            }
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Requested block spoilage for " + pendingPos);
        } catch (Throwable t) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK,
                    "Failed to send block spoilage request: " + t.getMessage());
        }
    }

    /** Ordinal of the cached spoilage state, or {@code STATE_NONE} if unknown for this block. */
    public static int getState(BlockPos pos) {
        CachedAnswer entry = get(pos);
        return entry != null ? entry.state() : BlockSpoilageResponsePayload.STATE_NONE;
    }

    /**
     * Whether the client's own world thinks the block at {@code pos} could spoil — the cached
     * flag from the last answer when one exists, the live check otherwise. The HUD uses this to
     * decide whether the "checking" placeholder is worth drawing while waiting for the server.
     */
    public static boolean looksSpoilableCached(BlockPos pos) {
        CachedAnswer entry = peek(pos);
        if (entry != null) {
            return entry.looksSpoilable();
        }
        return looksSpoilable(pos);
    }

    /** Ticks until the next stage, counted down since the answer arrived. */
    public static long getTicksRemaining(BlockPos pos) {
        CachedAnswer entry = get(pos);
        if (entry == null) {
            return 0L;
        }
        // A "not aging" answer must survive this method untouched. Counting elapsed time off
        // the sentinel and clamping the result at zero turns "no timer" into "zero ticks left",
        // which the HUD renders as "<1 min" — a countdown the block will never perform.
        if (entry.ticksRemaining() < 0L) {
            return BlockSpoilageResponsePayload.NO_TIMER;
        }
        long elapsed = Math.max(0L, currentGameTime() - entry.atGameTime());
        return Math.max(0L, entry.ticksRemaining() - elapsed);
    }

    /**
     * Pass 200 (L5 — render-path): the HUD needs BOTH the remaining ticks and the speed
     * multiplier for the same pos in the same frame. Calling the two getters separately
     * runs the full {@link #get} chain (dimension check + map get + game-time read +
     * block-validity check) twice per frame. This combined accessor runs it once and
     * returns both values in a record, eliminating the footgun of a separate static
     * field that could be read without the preceding call.
     *
     * @return a record with {@code ticksRemaining} (0 when no answer) and
     *         {@code speedMultiplier} (1.0 when no answer).
     */
    public static TicksAndMultiplier getTicksRemainingAndMultiplier(BlockPos pos) {
        CachedAnswer entry = get(pos);
        if (entry == null) {
            return new TicksAndMultiplier(0L, 1.0);
        }
        // Same sentinel guard as getTicksRemaining — see the comment there. This is the
        // accessor the HUD actually calls, so the clamp below is what produced the "<1 min"
        // on world-generated blocks.
        if (entry.ticksRemaining() < 0L) {
            return new TicksAndMultiplier(BlockSpoilageResponsePayload.NO_TIMER, entry.speedMultiplier());
        }
        long elapsed = Math.max(0L, currentGameTime() - entry.atGameTime());
        long ticks = Math.max(0L, entry.ticksRemaining() - elapsed);
        return new TicksAndMultiplier(ticks, entry.speedMultiplier());
    }

    /**
     * Return value for {@link #getTicksRemainingAndMultiplier} — both the remaining
     * ticks and the speed multiplier from a single cache lookup.
     */
    public record TicksAndMultiplier(long ticksRemaining, double speedMultiplier) {}

    public static boolean hasAnswerFor(BlockPos pos) {
        return get(pos) != null;
    }

    public static void clear() {
        CACHE.clear();
        pendingPos = null;
        cachedDimension = null;
        // Pass 206: invalidate the connection fingerprint so the next requestIfStale
        // re-records the current connection. Without this, a same-dimension rejoin
        // could see cachedConnectionHash match a stale value if the new connection
        // happened to occupy the same identity-hash slot as the old one.
        cachedConnectionHash = 0;
        // Pass 195: the memo captures the (pos, tick, block) of the previous world; a new
        // world joined in the same session would otherwise see a stale answer for one tick.
        memoedBlockPos = null;
        memoedBlockAtTick = -1L;
        memoedBlock = null;
    }

    /**
     * The cache is keyed by dimension as well as position, so the same coordinates in the Nether
     * (or in the next world joined) never show a leftover answer: {@link #requestIfStale} wipes
     * everything on a dimension change.
     */
    private static CachedAnswer get(BlockPos pos) {
        if (pos == null || !java.util.Objects.equals(cachedDimension, currentDimension())) {
            return null;
        }
        // Pass 104: LinkedHashMap(accessOrder=true) reorders on get(), so this single call
        // both fetches and touches — no separate O(n) deque scan.
        CachedAnswer entry = CACHE.get(pos);
        if (entry == null) {
            return null;
        }
        Block now = currentBlockAt(pos);
        if (now != null && entry.block() != now) {
            CACHE.remove(pos);
            return null;
        }
        return entry;
    }

    /**
     * The cached answer for {@code pos}, or {@code null} when there is none that still applies.
     * An answer is thrown away as soon as the block at those coordinates is no longer the block it
     * described — otherwise a verdict about air survives the crate that replaced it.
     */
    private static CachedAnswer peek(BlockPos pos) {
        // Pass 519 (L5 — render-path): the old containsKey+get did TWO hash lookups
        // per call, and peek is on the HUD's hot path (requestIfStale + looksSpoilableCached
        // both call it every frame). A single get() does the same work in one lookup.
        // The access-order reorder that get() triggers is harmless here: every caller
        // of peek also calls get() or touch() in the same frame, which re-touches the
        // entry, so the LRU position ends up where it would have been anyway.
        CachedAnswer entry = CACHE.get(pos);
        if (entry == null) {
            return null;
        }
        Block now = currentBlockAt(pos);
        if (now != null && entry.block() != now) {
            CACHE.remove(pos);
            return null;
        }
        return entry;
    }

    /**
     * Whether the client's own copy of the world thinks this block could spoil. Mirrors the check
     * the HUD uses to decide it is worth showing anything at all, and exists here so a server
     * answer of "nothing here" about a block we can see is treated as a disagreement to re-ask,
     * not as a settled fact to cache for ten seconds.
     */
    private static boolean looksSpoilable(BlockPos pos) {
        Block block = currentBlockAt(pos);
        return block != null && looksSpoilable(block);
    }

    /**
     * Overload that takes a pre-fetched {@link Block} — avoids the chunk-section read when the
     * block is already known (e.g. when storing the answer in {@link #accept}).
     */
    private static boolean looksSpoilable(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (SpoilageConfig.getInstance().isBlockTracked(blockId)) {
            return true;
        }
        Item blockItem = block.asItem();
        return blockItem != null && blockItem != Items.AIR
                && SpoilageConfig.getInstance().isSpoilable(blockItem);
    }

    /** Block currently standing at {@code pos} on the client, or {@code null} with no level. */
    private static Block currentBlockAt(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (pos == null || client == null || client.level == null) {
            return null;
        }
        // Pass 195 (Lens 4 — TPS): per-tick memo. The block at a pos can only change when a
        // setBlock call lands, which advances the game time. Within one tick, every caller
        // sees the same block. The HUD issues 4-5 calls/frame for the same pos; this drops
        // the chunk-section read to 1/tick/pos.
        long tick = client.level.getGameTime();
        if (memoedBlockAtTick == tick && pos.equals(memoedBlockPos)) {
            return memoedBlock;
        }
        Block now = client.level.getBlockState(pos).getBlock();
        memoedBlockPos = pos.immutable();
        memoedBlockAtTick = tick;
        memoedBlock = now;
        return now;
    }

    private static void put(BlockPos pos, CachedAnswer entry) {
        cachedDimension = currentDimension();
        // Pass 104: put() on an access-ordered LinkedHashMap moves the entry to the MRU end
        // and removeEldestEntry enforces the cap — no manual deque bookkeeping.
        CACHE.put(pos, entry);
    }

    /** Moves {@code pos} to the MRU end. O(1) via the access-ordered map. */
    private static void touch(BlockPos pos) {
        // containsKey does not reorder; get() does. Only touch entries that exist.
        if (CACHE.containsKey(pos)) {
            CACHE.get(pos);
        }
    }

    private static String currentDimension() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null ? client.level.dimension().toString() : null;
    }

    private static long currentGameTime() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null ? client.level.getGameTime() : 0L;
    }

    /**
     * Pass 206: a stable per-session fingerprint for the network connection. Returns
     * {@link System#identityHashCode} of the {@code ClientPacketListener} when one exists,
     * and {@code 0} when there is none. A new connection always has a different hash
     * (the old object is GC-eligible), so a connection switch forces the cache to clear
     * even when the dimension stays the same.
     */
    private static int currentConnectionHash() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return 0;
        ClientPacketListener conn = client.getConnection();
        return conn == null ? 0 : System.identityHashCode(conn);
    }
}