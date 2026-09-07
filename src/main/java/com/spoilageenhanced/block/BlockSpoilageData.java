package com.spoilageenhanced.block;

import com.mojang.serialization.Codec;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BlockSpoilageData extends SavedData {

    public static class BlockSpoilageEntry {
        public FoodSpoilageUtil.SpoilageState state = FoodSpoilageUtil.SpoilageState.FRESH;
        public long expirationTime = -1;
        public long legacyBirthTime = -1;

        public BlockSpoilageEntry() {}

        public BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState state, long expirationTime) {
            this.state = state;
            this.expirationTime = expirationTime;
        }

        public BlockSpoilageEntry(long legacyBirthTime) {
            this.legacyBirthTime = legacyBirthTime;
        }
    }

    /**
     * Maximum number of tracked blocks per world to prevent unbounded memory growth.
     * Vanilla worlds typically have far fewer spoilable blocks (farms, melons, pumpkins).
     * 10000 gives comfortable headroom for large modded farms.
     */
    private static final int MAX_TRACKED_BLOCKS = 10000;

    /**
     * Pass 148 (Lens 8 — data round-trip): chunk birth times are recorded once per chunk the
     * player visits (getChunkBirthTime(ServerLevel, ChunkPos) writes back at line 400). Without a
     * cap the map grows by one entry per explored chunk and is serialized into the world save on
     * every write — a long-running server that explores the world would bloat the save file
     * linearly with distance travelled. The cap bounds it; eviction drops the OLDEST chunk
     * (smallest birth time) because an old chunk has already fully spoiled, so its birth time is
     * the least relevant to a newly-placed block. Evicted entries are recomputed on demand
     * (getChunkBirthTime adopts from neighbours or uses inhabitedTime), so nothing is lost.
     */
    private static final int MAX_CHUNK_BIRTH_TIMES = 65536;

    private final Map<Long, BlockSpoilageEntry> entries = new HashMap<>();
    private final Map<Long, Long> chunkBirthTimes = new HashMap<>();
    private double savedMultiplier = 1.0;

    /**
     * BUG-14: when a player breaks a block, vanilla clears the block before it drops anything —
     * {@code ServerPlayerGameMode:280} calls {@code removeBlock}, and only {@code :298} calls
     * {@code playerDestroy} -> {@code dropResources}. BlockStateChangeMixin reacts to that
     * air transition and drops the tracking entry, so by the time the drop code looks the entry
     * is gone and the fallback mints a fresh one — a stale pumpkin came back out fresh.
     *
     * <p>These two maps hold such an entry for a couple of ticks so the drop can still claim it.
     * Deliberately transient: they are not written to NBT and must never outlive the break they
     * belong to.</p>
     */
    private final transient Map<Long, BlockSpoilageEntry> parkedEntries = new HashMap<>();
    private final transient Map<Long, Long> parkedAtGameTime = new HashMap<>();

    /** A break and its drops happen inside one tick; two is slack, not a lifetime. */
    private static final long PARK_TTL_TICKS = 2L;

    public static final Codec<BlockSpoilageData> CODEC = CompoundTag.CODEC.xmap(
            BlockSpoilageData::fromCompound,
            BlockSpoilageData::toCompound
    );

    public static final SavedDataType<BlockSpoilageData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("spoilage_enhanced", "block_spoilage"),
            BlockSpoilageData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    public BlockSpoilageData() {
    }

    public static BlockSpoilageData fromCompound(CompoundTag nbt) {
        BlockSpoilageData data = new BlockSpoilageData();

        if (nbt.contains("Blocks")) {
            CompoundTag blocks = nbt.getCompoundOrEmpty("Blocks");
            FoodSpoilageUtil.SpoilageState[] states = FoodSpoilageUtil.SpoilageState.values();
            for (String key : blocks.keySet()) {
                try {
                    long posLong = Long.parseLong(key);
                    CompoundTag entryNbt = blocks.getCompoundOrEmpty(key);
                    BlockSpoilageEntry entry = new BlockSpoilageEntry();
                    if (entryNbt.contains("BirthTime")) {
                        entry.legacyBirthTime = entryNbt.getLongOr("BirthTime", -1L);
                    } else {
                        // Pass 133 (Lens 1 — silent failure): the old code indexed
                        // SpoilageState.values()[stateInt] with an unchecked cast. A corrupt
                        // save (stateInt out of range, e.g. a future version that removed a
                        // state, or a hand-edited NBT) threw ArrayIndexOutOfBoundsException,
                        // which the outer catch silently swallowed — the WHOLE block entry was
                        // dropped and getSpoilageState() returned FRESH for it, resurrecting a
                        // stale pumpkin on world load with no log line at all. Bound the index
                        // and default to FRESH for anything out of range; log so the loss is
                        // visible in the data log instead of invisible.
                        int stateInt = entryNbt.getIntOr("State", 0);
                        entry.state = (stateInt >= 0 && stateInt < states.length)
                                ? states[stateInt]
                                : FoodSpoilageUtil.SpoilageState.FRESH;
                        entry.expirationTime = entryNbt.getLongOr("Expire", -1L);
                    }
                    data.entries.put(posLong, entry);
                } catch (Exception e) {
                    // Pass 133: a malformed entry must not silently vanish. Log it so a
                    // corrupted save is diagnosable instead of resurrecting blocks to FRESH.
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                            "BlockSpoilageData: skipped malformed block entry at key '" + key
                                    + "' while loading: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        if (nbt.contains("SpeedMultiplier")) {
            data.savedMultiplier = nbt.getDoubleOr("SpeedMultiplier", 1.0);
        } else {
            data.savedMultiplier = 1.0;
        }

        if (nbt.contains("ChunkBirthTimes")) {
            CompoundTag chunkTimesNbt = nbt.getCompoundOrEmpty("ChunkBirthTimes");
            for (String key : chunkTimesNbt.keySet()) {
                try {
                    long chunkPosLong = Long.parseLong(key);
                    // getLongOr returns default (0L) for non-NumericTag values — a corrupt
                    // value (e.g., a string) would silently become 0L. Check the tag type
                    // explicitly so any non-long value is logged and skipped.
                    net.minecraft.nbt.Tag tag = chunkTimesNbt.get(key);
                    if (!(tag instanceof net.minecraft.nbt.NumericTag)) {
                        throw new NumberFormatException("value is not a long: " + tag.getClass().getSimpleName());
                    }
                    long birthTime = ((net.minecraft.nbt.NumericTag) tag).longValue();
                    data.chunkBirthTimes.put(chunkPosLong, birthTime);
                } catch (NumberFormatException e) {
                    // Pass 133: same silent-failure class as the block entries — a corrupt
                    // chunk birth time used to vanish without a trace.
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                            "BlockSpoilageData: skipped malformed chunk birth time at key '" + key
                                    + "' while loading: " + e.getMessage());
                }
            }
        }
        return data;
    }

    public CompoundTag toCompound() {
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        for (Map.Entry<Long, BlockSpoilageEntry> entry : entries.entrySet()) {
            CompoundTag entryNbt = new CompoundTag();
            if (entry.getValue().legacyBirthTime >= 0) {
                entryNbt.putLong("BirthTime", entry.getValue().legacyBirthTime);
            } else {
                entryNbt.putInt("State", entry.getValue().state.ordinal());
                entryNbt.putLong("Expire", entry.getValue().expirationTime);
            }
            blocks.put(String.valueOf(entry.getKey()), entryNbt);
        }
        nbt.put("Blocks", blocks);
        nbt.putDouble("SpeedMultiplier", savedMultiplier);

        CompoundTag chunkTimesNbt = new CompoundTag();
        for (Map.Entry<Long, Long> entry : chunkBirthTimes.entrySet()) {
            chunkTimesNbt.putLong(String.valueOf(entry.getKey()), entry.getValue());
        }
        nbt.put("ChunkBirthTimes", chunkTimesNbt);

        return nbt;
    }

    public void setSpoilageState(BlockPos pos, FoodSpoilageUtil.SpoilageState state, long expirationTime) {
        // Evict if at capacity before adding new entry
        if (entries.size() >= MAX_TRACKED_BLOCKS && !entries.containsKey(pos.asLong())) {
            evictOldestRottenOrExpired();
        }
        entries.put(pos.asLong(), new BlockSpoilageEntry(state, expirationTime));
        this.savedMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockSpoilageData: Registered/Updated block at " + pos + " with state " + state + ", expires at: " + expirationTime);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos.asLong()) != null) {
            com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockSpoilageData: Removed block at " + pos);
            setDirty();
        }
    }

    /**
     * Set the entry aside before dropping it, so a drop happening later in the same tick can still
     * inherit it. See the note on {@link #parkedEntries}.
     */
    public void park(BlockPos pos, BlockSpoilageEntry entry, long gameTime) {
        if (entry == null) {
            return;
        }
        prunePark(gameTime);
        parkedEntries.put(pos.asLong(), entry);
        parkedAtGameTime.put(pos.asLong(), gameTime);
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockSpoilageData: Parked entry for " + pos
                + " (state " + entry.state + ", expires at " + entry.expirationTime + ") for the pending drop");
    }

    /**
     * Reclaim an entry parked by {@link #park}, if it is still fresh enough to belong to this
     * break. Returns {@code null} once the parking window has passed.
     */
    public BlockSpoilageEntry takeParked(BlockPos pos, long gameTime) {
        Long parkedAt = parkedAtGameTime.get(pos.asLong());
        if (parkedAt == null) {
            return null;
        }
        parkedAtGameTime.remove(pos.asLong());
        BlockSpoilageEntry entry = parkedEntries.remove(pos.asLong());
        if (gameTime - parkedAt > PARK_TTL_TICKS || gameTime < parkedAt) {
            return null;
        }
        return entry;
    }

    private void prunePark(long gameTime) {
        parkedAtGameTime.entrySet().removeIf(e -> {
            boolean stale = gameTime - e.getValue() > PARK_TTL_TICKS || gameTime < e.getValue();
            if (stale) {
                parkedEntries.remove(e.getKey());
            }
            return stale;
        });
    }

    /**
     * Evicts the best eviction candidate when the map is at capacity: prefer a ROTTEN entry
     * (worthless — nothing inherits from rotten), otherwise the entry with the smallest
     * position key (a stable, deterministic tiebreaker — see the Pass 147 note below for why
     * it is the key and not the expirationTime).
     *
     * <p>Pass 102 (Lens 8): the old version did two full map iterations and, despite the
     * "oldest" in its name, took the FIRST match in HashMap iteration order — effectively
     * arbitrary. Worse, its second pass tested {@code expirationTime > 0}, which matches any
     * non-rotten entry with a positive expiration, including a fresh crop that just started
     * spoiling — it could evict an actively-spoiling crop while truly expired ones stayed.
     * The single pass below picks the genuinely best candidate in O(n) with no second scan.</p>
     *
     * <p>Pass 147 (Lens 3 — cache correctness): the old guard {@code e.expirationTime >= 0}
     * silently skipped legacy entries (legacyBirthTime >= 0, expirationTime == -1 — the default
     * that getSpoilageState never populates, see Pass 136). A world full of legacy entries
     * would produce no eviction candidate (no ROTTEN, no positive expirationTime) and the new
     * entry from setSpoilageState would silently push the map past MAX_TRACKED_BLOCKS.
     * The fix compares the position key instead: every entry has one, so a candidate always
     * exists. (The earlier draft of this note said legacy entries were compared by
     * legacyBirthTime — they are not; the key is the posLong.)</p>
     */
    private void evictOldestRottenOrExpired() {
        Long evictKey = null;
        boolean evictingRotten = false;
        long bestKey = Long.MAX_VALUE;

        for (Map.Entry<Long, BlockSpoilageEntry> entry : entries.entrySet()) {
            BlockSpoilageEntry e = entry.getValue();
            if (e.state == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                // ROTTEN is always the best candidate — stop at the first one.
                evictKey = entry.getKey();
                evictingRotten = true;
                break;
            }
            // Prefer entries with the smallest key (posLong) when expirationTime is invalid
            // (legacy format or rotten). This ensures a map full of legacy entries still
            // produces a candidate so setSpoilageState can't push past the cap.
            long key = entry.getKey();
            if (key < bestKey) {
                bestKey = key;
                evictKey = key;
                evictingRotten = false;
            }
        }

        if (evictKey != null) {
            entries.remove(evictKey);
            SpoilageEnhancedLogger.log("BlockSpoilageData: Evicted "
                    + (evictingRotten ? "rotten" : "oldest-entry")
                    + " block at " + BlockPos.of(evictKey) + " due to capacity limit");
        }
    }

    public boolean isTracked(BlockPos pos) {
        return entries.containsKey(pos.asLong());
    }

    public BlockSpoilageEntry getEntry(BlockPos pos) {
        return entries.get(pos.asLong());
    }

    public Map<Long, BlockSpoilageEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public Map<Long, Long> getChunkBirthTimes() {
        return Collections.unmodifiableMap(chunkBirthTimes);
    }

    public void rescaleExpirations(long currentTime, double ratio) {
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockSpoilageData: Rescaling block expirations. Ratio: " + ratio + ", current world time: " + currentTime);
        // Pass 135 (Lens 7 — boundary arithmetic): the old code did (long)(elapsed * ratio)
        // and (long)(remaining * ratio) with no overflow guards. When /spoilage speed changes
        // the multiplier, ratio can be up to 10,000 (old=100, new=0.01). elapsed/remaining
        // can be up to ~2^63, so the product overflows long silently, wrapping to negative.
        // newElapsed negative → currentTime - newElapsed becomes a FUTURE birth time,
        // resurrecting stale/rotten blocks to FRESH. Mirror the guards from
        // FoodSpoilageUtil.rescaleItemTimestamps: Long.MAX_VALUE sentinel, ratio > 1e15 clamp,
        // negative remaining check.
        final long NEVER = Long.MAX_VALUE;
        for (BlockSpoilageEntry entry : entries.values()) {
            if (entry.legacyBirthTime >= 0) {
                long oldBirth = entry.legacyBirthTime;
                long elapsed = currentTime - oldBirth;
                if (elapsed <= 0) {
                    // Already expired or invalid — keep as-is to prevent resurrection
                    continue;
                }
                if (Double.isInfinite(ratio) || Double.isNaN(ratio) || ratio > 1e15d) {
                    // Ratio would overflow long arithmetic; clamp to "essentially never expires"
                    entry.legacyBirthTime = NEVER;
                } else {
                    long newElapsed = Math.max(1L, (long) (elapsed * ratio));
                    entry.legacyBirthTime = currentTime - newElapsed;
                }
            } else if (entry.state != FoodSpoilageUtil.SpoilageState.ROTTEN) {
                long remaining = entry.expirationTime - currentTime;
                if (remaining <= 0) {
                    // Already expired — keep expired to prevent resurrection
                    continue;
                }
                if (Double.isInfinite(ratio) || Double.isNaN(ratio) || ratio > 1e15d) {
                    entry.expirationTime = NEVER;
                } else {
                    long newRemaining = Math.max(1L, (long) (remaining * ratio));
                    entry.expirationTime = currentTime + newRemaining;
                }
            }
        }
        savedMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        setDirty();
    }

    public void setChunkBirthTime(ChunkPos pos, long birthTime) {
        if (chunkBirthTimes.size() >= MAX_CHUNK_BIRTH_TIMES && !chunkBirthTimes.containsKey(pos.pack())) {
            evictOldestChunkBirthTime();
        }
        chunkBirthTimes.put(pos.pack(), birthTime);
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockSpoilageData: Recorded birth time " + birthTime + " for chunk " + pos);
        setDirty();
    }

    /**
     * Pass 148 (Lens 8): evicts the chunk birth time with the smallest value (oldest chunk) when
     * the map is at capacity. See MAX_CHUNK_BIRTH_TIMES for why oldest is chosen.
     */
    private void evictOldestChunkBirthTime() {
        Long evictKey = null;
        long oldestBirth = Long.MAX_VALUE;
        for (Map.Entry<Long, Long> entry : chunkBirthTimes.entrySet()) {
            if (entry.getValue() < oldestBirth) {
                oldestBirth = entry.getValue();
                evictKey = entry.getKey();
            }
        }
        if (evictKey != null) {
            chunkBirthTimes.remove(evictKey);
            SpoilageEnhancedLogger.log("BlockSpoilageData: Evicted oldest chunk birth time (chunk "
                    + ChunkPos.unpack(evictKey) + ") due to capacity limit");
        }
    }

    public long getChunkBirthTime(ChunkPos pos) {
        return chunkBirthTimes.getOrDefault(pos.pack(), -1L);
    }

    public long getChunkBirthTime(ServerLevel world, ChunkPos chunkPos) {
        long posLong = chunkPos.pack();
        if (chunkBirthTimes.containsKey(posLong)) {
            return chunkBirthTimes.get(posLong);
        }

        long adoptedBirthTime = -1;
        int searchRadius = 4; // 9x9 chunks area (144x144 blocks) - covers any village

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (dx == 0 && dz == 0) continue;
                long neighborPosLong = ChunkPos.pack(chunkPos.x() + dx, chunkPos.z() + dz);
                if (chunkBirthTimes.containsKey(neighborPosLong)) {
                    long nBirth = chunkBirthTimes.get(neighborPosLong);
                    if (adoptedBirthTime == -1 || nBirth < adoptedBirthTime) {
                        adoptedBirthTime = nBirth; // Adopt the oldest (minimum) birth time in the region
                    }
                }
            }
        }

        long birthTime;
        if (adoptedBirthTime != -1) {
            birthTime = adoptedBirthTime;
            com.spoilageenhanced.util.SpoilageEnhancedLogger.log(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.CHUNKS,
                    "Chunk " + chunkPos + " adopted birth time " + birthTime + " from nearby regional chunk.");
        } else {
            net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
            long inhabitedTime = (chunk != null) ? chunk.getInhabitedTime() : 0L;
            birthTime = Math.max(0L, world.getGameTime() - inhabitedTime);
            com.spoilageenhanced.util.SpoilageEnhancedLogger.log(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.CHUNKS,
                    "Chunk " + chunkPos + " initialized new birth time " + birthTime + " (inhabitedTime: " + inhabitedTime + ").");
        }

        chunkBirthTimes.put(posLong, birthTime);
        setDirty();
        return birthTime;
    }

    public boolean hasChunkBirthTime(ChunkPos pos) {
        return chunkBirthTimes.containsKey(pos.pack());
    }

    public FoodSpoilageUtil.SpoilageState getSpoilageState(BlockPos pos, Level world, Item dropItem) {
        return getSpoilageState(pos, world, dropItem, null);
    }

    /**
     * Overload that accepts a pre-fetched blockState to avoid duplicate chunk-section reads.
     * Called from getTicksUntilNextStage which already has the blockState.
     */
    public FoodSpoilageUtil.SpoilageState getSpoilageState(BlockPos pos, Level world, Item dropItem,
            net.minecraft.world.level.block.state.BlockState blockState) {
        // Pass 103 (Lens 8): the block state lookup (a chunk-section read) ran on EVERY call,
        // but itemToUse is only needed when a duration lookup actually happens — the ROTTEN
        // early-return and the not-yet-expired path never touch it. Resolve the item lazily.
        Item itemToUse = null;

        BlockSpoilageEntry entry = entries.get(pos.asLong());

        // BUG-14: the block may already be gone — a player break clears it before the drop runs —
        // in which case the entry is parked rather than lost. Reclaim it before inventing a new
        // one, otherwise a stale block drops a fresh item.
        if (entry == null) {
            BlockSpoilageEntry reclaimed = takeParked(pos, world.getGameTime());
            if (reclaimed != null) {
                entries.put(pos.asLong(), reclaimed);
                setDirty();
                entry = reclaimed;
                com.spoilageenhanced.util.SpoilageEnhancedLogger.log(
                        "BlockSpoilageData: Reclaimed parked entry for " + pos + " (state " + reclaimed.state + ")");
            }
        }

        // Never start a clock on a crop that is still growing.
        //
        // This is the choke point where an untracked block BECOMES tracked, and it is reached by
        // simply asking about a block - the HUD asks every time the player looks at one. Guarding
        // the callers one at a time does not hold: each fix closed a path and the seedling kept
        // its timer through the next one. The registration itself has to refuse.
        if (entry == null && world instanceof ServerLevel registeringLevel) {
            net.minecraft.world.level.block.state.BlockState here =
                    blockState != null ? blockState : world.getBlockState(pos);
            // Asked of the ripeness rule rather than of the growth stage alone. A plant that
            // signals its fruit with a flag has no meaningful stage — a cave vine carrying
            // berries sits at whatever length it grew to, and reading that length as "still
            // growing" refused to register it here, so a vine with berries on it never got a
            // clock no matter how many other paths were fixed.
            if (!com.spoilageenhanced.util.DynamicFoodBlockCache.bearsFoodYet(here, registeringLevel, pos)) {
                return FoodSpoilageUtil.SpoilageState.FRESH;
            }
        }

        if (entry == null && world instanceof ServerLevel serverWorld) {
            // Pass 127: use the cached blockState instead of calling resolveItemToUse
            // which would do another world.getBlockState(pos).
            if (blockState != null) {
                Item blockItem = blockState.getBlock().asItem();
                if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                    itemToUse = blockItem;
                }
            }
            if (itemToUse == null) itemToUse = dropItem;
            long chunkBirthTime = getChunkBirthTime(serverWorld, ChunkPos.containing(pos));
            long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(itemToUse);
            long expireTime = chunkBirthTime + freshDuration;

            setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, expireTime);
            entry = entries.get(pos.asLong());
        }

        if (entry == null) return FoodSpoilageUtil.SpoilageState.FRESH;

        long currentTime = world.getGameTime();

        if (entry.legacyBirthTime >= 0) {
            if (itemToUse == null) {
                // Pass 127: use cached blockState if available, otherwise fall back to resolveItemToUse
                if (blockState != null) {
                    Item blockItem = blockState.getBlock().asItem();
                    if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                        itemToUse = blockItem;
                    }
                }
                if (itemToUse == null) itemToUse = resolveItemToUse(pos, world, dropItem);
            }
            long age = currentTime - entry.legacyBirthTime;
            long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(itemToUse);
            long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(itemToUse);
            if (age < freshDuration) {
                return FoodSpoilageUtil.SpoilageState.FRESH;
            } else if (age < freshDuration + staleDuration) {
                return FoodSpoilageUtil.SpoilageState.STALE;
            } else {
                return FoodSpoilageUtil.SpoilageState.ROTTEN;
            }
        }

        if (entry.state == FoodSpoilageUtil.SpoilageState.ROTTEN) {
            return FoodSpoilageUtil.SpoilageState.ROTTEN;
        }

        if (currentTime >= entry.expirationTime) {
            if (itemToUse == null) {
                // Pass 127: use cached blockState if available
                if (blockState != null) {
                    Item blockItem = blockState.getBlock().asItem();
                    if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                        itemToUse = blockItem;
                    }
                }
                if (itemToUse == null) itemToUse = resolveItemToUse(pos, world, dropItem);
            }
            long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(itemToUse);
            if (entry.state == FoodSpoilageUtil.SpoilageState.FRESH) {
                entry.state = FoodSpoilageUtil.SpoilageState.STALE;
                entry.expirationTime = currentTime + staleDuration;
                setDirty();
                SpoilageEnhancedLogger.log("BlockSpoilageData: Block at " + pos + " transitioned FRESH -> STALE");

                if (currentTime >= entry.expirationTime) {
                    entry.state = FoodSpoilageUtil.SpoilageState.ROTTEN;
                    entry.expirationTime = -1;
                    setDirty();
                    SpoilageEnhancedLogger.log("BlockSpoilageData: Block at " + pos + " transitioned STALE -> ROTTEN");
                    return FoodSpoilageUtil.SpoilageState.ROTTEN;
                }
                return FoodSpoilageUtil.SpoilageState.STALE;
            } else if (entry.state == FoodSpoilageUtil.SpoilageState.STALE) {
                entry.state = FoodSpoilageUtil.SpoilageState.ROTTEN;
                entry.expirationTime = -1;
                setDirty();
                SpoilageEnhancedLogger.log("BlockSpoilageData: Block at " + pos + " transitioned STALE -> ROTTEN");
                return FoodSpoilageUtil.SpoilageState.ROTTEN;
            }
        }

        return entry.state;
    }

    public long getTicksUntilNextStage(BlockPos pos, Level world, Item dropItem) {
        // Pass 127 (Lens 1/4): cache the block state to avoid TWO chunk-section reads.
        // The old code did world.getBlockState(pos) here, then getSpoilageState called
        // resolveItemToUse which did ANOTHER world.getBlockState(pos). On a busy server
        // with many HUD requests this doubled the chunk-section read cost.
        net.minecraft.world.level.block.state.BlockState blockState = world.getBlockState(pos);
        Item itemToUse = dropItem;
        if (blockState != null) {
            Item blockItem = blockState.getBlock().asItem();
            if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                itemToUse = blockItem;
            }
        }

        BlockSpoilageEntry entry = entries.get(pos.asLong());
        if (entry == null) {
            getSpoilageState(pos, world, itemToUse, blockState);
            entry = entries.get(pos.asLong());
        }

        if (entry == null) {
            return SpoilageConfig.getInstance().getFreshDurationForItem(itemToUse);
        }

        if (entry.state == FoodSpoilageUtil.SpoilageState.ROTTEN) {
            return 0;
        }

        // Pass 136 (Lens 3/L7): a legacy-format entry (loaded via the "BirthTime" key in
        // fromCompound) has legacyBirthTime >= 0 and expirationTime == -1 (its default, never
        // populated — the legacy branch of getSpoilageState computes state from age and never
        // writes back an expirationTime). The old code below did expirationTime - currentTime
        // = -1 - currentTime, which is deeply negative, so Math.max(0, ...) returned 0: a
        // genuinely fresh block reported "0 ticks until next stage" and the HUD rendered a
        // fully-depleted countdown for it. Compute the real remaining time from legacyBirthTime.
        if (entry.legacyBirthTime >= 0) {
            if (itemToUse == null) {
                if (blockState != null) {
                    Item blockItem = blockState.getBlock().asItem();
                    if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR
                            && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                        itemToUse = blockItem;
                    }
                }
                if (itemToUse == null) itemToUse = resolveItemToUse(pos, world, dropItem);
            }
            long age = world.getGameTime() - entry.legacyBirthTime;
            long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(itemToUse);
            long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(itemToUse);
            if (age < freshDuration) return freshDuration - age;
            if (age < freshDuration + staleDuration) return (freshDuration + staleDuration) - age;
            return 0;
        }

        long remaining = entry.expirationTime - world.getGameTime();
        return Math.max(0, remaining);
    }

    public static BlockSpoilageData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Pass 103 (Lens 8): resolves the item whose durations apply — the block's own item when it
     * is spoilable, otherwise the caller's drop item. Extracted so getSpoilageState can resolve
     * it lazily instead of paying a world.getBlockState chunk-section read on every call.
     */
    private static Item resolveItemToUse(BlockPos pos, Level world, Item dropItem) {
        net.minecraft.world.level.block.state.BlockState blockState = world.getBlockState(pos);
        if (blockState != null) {
            Item blockItem = blockState.getBlock().asItem();
            if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR && SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                return blockItem;
            }
        }
        return dropItem;
    }
}