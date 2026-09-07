package com.spoilageenhanced.block;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries a broken block's spoilage onto the items it drops.
 *
 * <p>Lives outside the mixins because there is more than one {@code Block.dropResources} to hook.
 * Vanilla ends at the six-argument overload, but Forge adds a seventh {@code boolean} parameter and
 * — this is the part that matters — {@code Block.playerDestroy} on Forge calls <em>that</em> one.
 * A mixin on the six-argument method therefore never fires when a player breaks a block on Forge,
 * which is why a stale pumpkin dropped a fresh one there while the same code worked on Fabric.</p>
 *
 * <p>Both mixins funnel through here, and the depth counter makes sure only the outermost call does
 * the work: on Forge the six-argument overload delegates to the seven-argument one, so without it
 * the pair would run twice and the inner frame would consume the outer frame's state.</p>
 */
public final class BlockDropSpoilageHandler {

    private static final ThreadLocal<long[]> PENDING_SPOILAGE = new ThreadLocal<>();
    private static final ThreadLocal<String> PENDING_DROP_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    /**
     * Pass 100 (Lens 8): set when stampPending() already stamped the drop, so after() can skip
     * the getEntitiesOfClass AABB scan (the reliable popResource path already did the work —
     * the scan is a fallback for loaders like Forge that spawn entities after dropResources
     * returns). after() still runs the BlockSpoilageData entry cleanup either way.
     */
    private static final ThreadLocal<Boolean> STAMPED = new ThreadLocal<>();

    /**
     * Pass 157 (Lens 4 — hot-path TPS): REFUTED. The fallback entity scan allocates a new
     * AABB per un-stamped break, and the obvious fix — reuse a thread-local AABB mutated in
     * place — is impossible: AABB's six fields are {@code public final} and its setMinX/setMaxX
     * "setters" return a NEW AABB, so chaining them allocates six objects instead of one.
     * The allocation stands; a mixin into vanilla AABB for a 48-byte object is not worth it.
     * The scan itself only runs on loaders that spawn entities after dropResources returns
     * (Forge) — on Fabric the popResource stamp sets STAMPED and skips this path entirely.
     */
    // (no SEARCH_BOX field — see the note above)

    private BlockDropSpoilageHandler() {
    }

    /** Called at the head of every {@code dropResources} overload we hook. */
    public static void before(BlockState state, Level world, BlockPos pos, String via) {
        int depth = DEPTH.get() == null ? 0 : DEPTH.get();
        DEPTH.set(depth + 1);
        if (depth > 0) {
            // Nested overload; the outer frame already captured the state.
            return;
        }
        // A previous call that threw before its tail would have left this behind.
        PENDING_SPOILAGE.remove();
        PENDING_DROP_ID.remove();
        STAMPED.remove();

        // Pass 176 (Lens 7 — boundary): updateSpoilage and randomizeSpoilage guard world == null;
        // this public method did not, so a null world NPE'd at world.isClientSide() on the next
        // line. The only current callers (BlockDropSpoilageMixin/ForgeMixin) pass a non-null
        // world from dropResources, but the method is public — a future caller could pass null.
        if (world == null || world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
            return;
        }

        // Digging up a crop before it is ripe gives you rotten produce.
        //
        // Vanilla still drops a potato from a seedling, and without this it would arrive with no
        // spoilage data at all — which the rest of the mod reads as brand new, so tearing up a
        // field early would be a way to farm permanently fresh food. The plant never finished
        // growing; what comes out of the ground is not something anyone would eat.
        //
        // getFoodDrop answers null for an unripe crop by design, so the drop item is resolved
        // through the growth-ignoring variant here.
        // "Barely grown" only means something for a plant that counts stages. A plant whose
        // fruit is a flag has none: a cave vine carries berries or it does not, and its age is
        // how far the vine grew downward. Reading that as ripeness handed the player ROTTEN
        // glow berries for breaking any vine shorter than half its maximum length — which is
        // most of them — even though the berries on it were perfectly good.
        //
        // Order matters, and it was wrong when this guard was first written. isBarelyGrown only
        // reads the block's own state properties and answers false immediately for anything
        // without a growth stage — which is almost every block. ripensByFlag, on a cache miss,
        // derives the block's ripeness rule by PROBING THE LOOT TABLE: a roll per value of every
        // boolean property, plus a roll per growth stage. Putting that first meant every block
        // broken anywhere in the world paid for it — stone, dirt, logs, and every block an
        // explosion or a piston takes with it — to be told it is not a crop.
        //
        // Both conditions still have to hold, so swapping them changes nothing but cost: the
        // probe now runs only for a block that actually has a growth stage and is under half
        // grown, which is a handful of crop states and only once each.
        if (FoodSpoilageUtil.isBarelyGrown(state)
                && !DynamicFoodBlockCache.ripensByFlag(state, serverWorld, pos)) {
            String immatureDrop = DynamicFoodBlockCache.getFoodDropIgnoringGrowth(state, serverWorld, pos);
            if (immatureDrop != null) {
                PENDING_SPOILAGE.set(new long[] { FoodSpoilageUtil.SpoilageState.ROTTEN.ordinal(), -1 });
                PENDING_DROP_ID.set(immatureDrop);
                SpoilageEnhancedLogger.log("BlockDrop: captured ROTTEN for " + immatureDrop
                        + " at " + pos + " (unripe crop broken, via " + via + ")");
            }
            return;
        }

        String dropItemId = DynamicFoodBlockCache.getFoodDrop(state, serverWorld, pos);
        if (dropItemId == null) {
            return;
        }

        BlockSpoilageData data = BlockSpoilageData.get(serverWorld);
        Item dropItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(dropItemId));
        FoodSpoilageUtil.SpoilageState spoilState = data.getSpoilageState(pos, world, dropItem);
        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);

        long expirationTime = entry != null ? entry.expirationTime : -1;
        PENDING_SPOILAGE.set(new long[] { spoilState.ordinal(), expirationTime });
        PENDING_DROP_ID.set(dropItemId);

        SpoilageEnhancedLogger.log("BlockDrop: captured " + spoilState + " for " + dropItemId
                + " at " + pos + " (via " + via + ")");
    }

    /**
     * Stamp a stack on its way out of {@code Block.popResource}, while the enclosing
     * {@code dropResources} still holds the broken block's state.
     *
     * <p>This is the reliable half of the job. Scanning for freshly spawned {@link ItemEntity}s at
     * the end of {@code dropResources} works on vanilla, but Forge captures block drops into a
     * list and spawns them after the call returns, so at that point there is nothing in the world
     * to find — the state was captured correctly and then had nowhere to go. The stack, on the
     * other hand, passes through {@code popResource} on every loader.</p>
     */
    public static void stampPending(ItemStack stack) {
        long[] spoilageInfo = PENDING_SPOILAGE.get();
        if (spoilageInfo == null || stack == null || stack.isEmpty()) {
            return;
        }
        String expectedId = PENDING_DROP_ID.get();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (expectedId != null && !expectedId.equals(itemId)) {
            // A block can drop several different things; only the food one inherits spoilage.
            return;
        }
        if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem()) || stack.has(ModDataComponentTypes.SPOILAGE)) {
            return;
        }

        FoodSpoilageUtil.SpoilageState spoilState = FoodSpoilageUtil.SpoilageState.values()[(int) spoilageInfo[0]];
        long expirationTime = spoilageInfo[1];
        switch (spoilState) {
            case FRESH -> applyFresh(stack, expirationTime);
            case STALE -> applyStale(stack, expirationTime);
            case ROTTEN -> applyRotten(stack);
        }
        SpoilageEnhancedLogger.log("Applied " + spoilState + " to dropped item " + itemId + " (via popResource)");

        // Pass 100: the popResource stamp is the reliable path. Clear PENDING_SPOILAGE so
        // after() can skip the getEntitiesOfClass AABB scan (which walks every entity near
        // the block to find freshly spawned drops — expensive on busy servers). The STAMPED
        // flag tells after() to still run the BlockSpoilageData entry cleanup, which is
        // needed regardless of which path consumed the state.
        PENDING_SPOILAGE.remove();
        PENDING_DROP_ID.remove();
        STAMPED.set(Boolean.TRUE);
    }

    /** Called at the tail of every {@code dropResources} overload we hook. */
    public static void after(BlockState state, Level world, BlockPos pos, String via) {
        int depth = (DEPTH.get() == null ? 1 : DEPTH.get()) - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
            return;
        }

        long[] spoilageInfo = PENDING_SPOILAGE.get();
        PENDING_DROP_ID.remove();
        boolean stamped = Boolean.TRUE.equals(STAMPED.get());
        STAMPED.remove();
        if (spoilageInfo == null && !stamped) {
            return;
        }
        PENDING_SPOILAGE.remove();

        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
            return;
        }

        // The parked entry must be reclaimed regardless of which path consumed the state.
        BlockSpoilageData.get(serverWorld).remove(pos);

        if (stamped) {
            // Pass 100: stampPending() already stamped the drop via popResource — the
            // entity scan below is a fallback for loaders that spawn entities after
            // dropResources returns (Forge). Skip the AABB getEntitiesOfClass scan.
            return;
        }

        FoodSpoilageUtil.SpoilageState spoilState = FoodSpoilageUtil.SpoilageState.values()[(int) spoilageInfo[0]];
        long expirationTime = spoilageInfo[1];

        AABB searchBox = new AABB(pos).inflate(1.5);
        List<ItemEntity> itemEntities = serverWorld.getEntitiesOfClass(
                ItemEntity.class, searchBox, e -> e != null && e.isAlive() && e.tickCount <= 2);

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String dropItemId = DynamicFoodBlockCache.getFoodDrop(state, serverWorld, pos);

        for (ItemEntity itemEntity : itemEntities) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (dropItemId == null || (!itemId.equals(dropItemId) && !itemId.equals(blockId))) {
                continue;
            }
            if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                continue;
            }
            if (stack.has(ModDataComponentTypes.SPOILAGE)) {
                continue;
            }

            switch (spoilState) {
                case FRESH -> applyFresh(stack, expirationTime);
                case STALE -> applyStale(stack, expirationTime);
                case ROTTEN -> applyRotten(stack);
            }
            SpoilageEnhancedLogger.log("Applied " + spoilState + " to dropped item " + itemId
                    + " from broken block at " + pos + " (via " + via + ")");
            itemEntity.setItem(stack);
        }
    }

    private static void applyFresh(ItemStack stack, long expirationTime) {
        List<Long> freshList = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            freshList.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(freshList, Collections.emptyList(), 0,
                SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    private static void applyStale(ItemStack stack, long expirationTime) {
        List<Long> staleList = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            staleList.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), staleList, 0,
                SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    private static void applyRotten(ItemStack stack) {
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), Collections.emptyList(),
                stack.getCount(), SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }
}
