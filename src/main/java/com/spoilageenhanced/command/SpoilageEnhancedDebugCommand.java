package com.spoilageenhanced.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Map;

public class SpoilageEnhancedDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        registerRoot("spoilage", dispatcher);
        registerRoot("spoilage_enhanced", dispatcher);
    }

    private static void registerRoot(String commandName, CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("logging")
                    .executes(context -> showLoggingStatus(context.getSource()))
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setLoggingState(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("log")
                    .executes(context -> showLoggingStatus(context.getSource()))
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setLoggingState(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("config")
                    .then(Commands.literal("reload")
                        .executes(context -> reloadConfig(context.getSource()))))
                .then(Commands.literal("debug")
                    .executes(context -> showHelp(context.getSource()))
                    .then(Commands.literal("logging")
                        .executes(context -> showLoggingStatus(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> setLoggingState(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                    .then(Commands.literal("renderdump")
                        .executes(context -> showRenderDumpStatus(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> setRenderDumpState(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                    .then(Commands.literal("inspect")
                        .executes(context -> inspectTargetedBlock(context.getSource(), null))
                        // Pass 488 (L13 behaviour): same hardcoded-ray limitation as debug place —
                        // inspect only reported the block under the player's crosshair, so
                        // checking a specific tracked block meant teleporting the player around
                        // and aiming. Optional <x y z> names the block directly.
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(context -> inspectTargetedBlock(context.getSource(),
                                            new BlockPos(
                                                    IntegerArgumentType.getInteger(context, "x"),
                                                    IntegerArgumentType.getInteger(context, "y"),
                                                    IntegerArgumentType.getInteger(context, "z")))))))
                    )
                    // Interaction primitives. The behaviour lens needs a player doing player
                    // things, and RCON cannot click: it can place and summon, so every task
                    // about containers, right-clicking or menus came back BLOCKED. These drive
                    // the REAL vanilla paths with the executing player, which is the whole
                    // point — the guard that let a rotten carrot into a crafting grid lives in
                    // AbstractContainerMenu.clicked, so a helper that reimplemented the click
                    // would have passed while the real path stayed broken.
                    .then(Commands.literal("use")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                            .executes(context -> useBlock(context.getSource(),
                                    net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                    )
                    .then(Commands.literal("useentity")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes(context -> useNearestEntity(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "type")))))
                    .then(Commands.literal("stress")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                            .executes(context -> spawnStressItems(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "count")))))
                    .then(Commands.literal("menuclick")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(-1, 200))
                            .then(Commands.argument("button", IntegerArgumentType.integer(0, 9))
                                .then(Commands.argument("type", StringArgumentType.word())
                                    .executes(context -> menuClick(context.getSource(),
                                            IntegerArgumentType.getInteger(context, "slot"),
                                            IntegerArgumentType.getInteger(context, "button"),
                                            StringArgumentType.getString(context, "type"))))))
                    )
                    .then(Commands.literal("chunk")
                        .executes(context -> inspectCurrentChunk(context.getSource()))
                    )
                    .then(Commands.literal("dump")
                        .executes(context -> dumpAllData(context.getSource()))
                    )
                    .then(Commands.literal("eat")
                        .then(Commands.argument("item", IdentifierArgument.id())
                            .then(Commands.argument("state", StringArgumentType.word())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes(context -> simulateEat(
                                            context.getSource(),
                                            IdentifierArgument.getId(context, "item"),
                                            StringArgumentType.getString(context, "state"),
                                            IntegerArgumentType.getInteger(context, "count"))))))
                    )
                    .then(Commands.literal("spawn")
                        .then(Commands.argument("item", IdentifierArgument.id())
                            .then(Commands.argument("state", StringArgumentType.word())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes(context -> spawnItemEntity(
                                            context.getSource(),
                                            IdentifierArgument.getId(context, "item"),
                                            StringArgumentType.getString(context, "state"),
                                            IntegerArgumentType.getInteger(context, "count"))))))
                    )
                    .then(Commands.literal("place")
                        .then(Commands.argument("item", IdentifierArgument.id())
                            .then(Commands.argument("state", StringArgumentType.word())
                                .executes(context -> placeBlockAsPlayer(
                                        context.getSource(),
                                        IdentifierArgument.getId(context, "item"),
                                        StringArgumentType.getString(context, "state"),
                                        null))
                                // Pass 487 (L13 behaviour — observed): the old overload hardcoded
                                // the target to player.blockPosition().offset(-2, 0, 0) — the
                                // self-test pillar layout. In free use that spot is often
                                // occupied, the placement fails silently with a confusing
                                // "Fail[]" line, and a whole session was spent hunting it. Add
                                // an optional <x y z> override so a target can be named.
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                    .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                            .executes(context -> placeBlockAsPlayer(
                                                    context.getSource(),
                                                    IdentifierArgument.getId(context, "item"),
                                                    StringArgumentType.getString(context, "state"),
                                                    new BlockPos(
                                                            IntegerArgumentType.getInteger(context, "x"),
                                                            IntegerArgumentType.getInteger(context, "y"),
                                                            IntegerArgumentType.getInteger(context, "z"))))))))
                        )
                    )
                )
        );
    }

    private static int reloadConfig(CommandSourceStack source) {
        boolean ok = SpoilageConfig.reload();
        if (ok) {
            // Pass 94: a hand-edited config can flip enable_logging / enableTraceLogging —
            // refresh the logger's static flag cache so isTraceEnabled() reflects the new values.
            SpoilageEnhancedLogger.refreshConfigCache();
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_CONFIG_RELOADED), true);
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL, "Configuration reloaded via command by " + source.getTextName());
            return 1;
        } else {
            source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_CONFIG_RELOAD_FAILED));
            return 0;
        }
    }

    private static int showLoggingStatus(CommandSourceStack source) {
        boolean enabled = SpoilageConfig.getInstance().enable_logging;
        String statusKey = enabled ? SpoilageEnhancedTranslations.CMD_LOGGING_ENABLED : SpoilageEnhancedTranslations.CMD_LOGGING_DISABLED;
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_LOGGING_STATUS, Component.translatable(statusKey)), false);
        return 1;
    }

    private static int setLoggingState(CommandSourceStack source, boolean enabled) {
        SpoilageEnhancedLogger.setLoggingEnabled(enabled);
        String statusKey = enabled ? SpoilageEnhancedTranslations.CMD_LOGGING_ENABLED : SpoilageEnhancedTranslations.CMD_LOGGING_DISABLED;
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_LOGGING_SET, Component.translatable(statusKey)), true);
        return 1;
    }

    /**
     * RENDERDUMP toggle (VISUAL_VERIFICATION.md): makes each renderer report what it drew, as
     * text, one line per element, only when that line changes since the last frame. Off by
     * default and guarded exactly like {@code isTraceEnabled()} — one volatile field read per
     * element when off. The flag is {@code volatile}: written by the command thread, read
     * every frame on the render thread, so a toggle mid-render cannot corrupt anything.
     */
    private static int showRenderDumpStatus(CommandSourceStack source) {
        boolean enabled = com.spoilageenhanced.client.RenderDump.isEnabled();
        String statusKey = enabled ? SpoilageEnhancedTranslations.CMD_LOGGING_ENABLED : SpoilageEnhancedTranslations.CMD_LOGGING_DISABLED;
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_RENDERDUMP_STATUS, Component.translatable(statusKey)), false);
        return 1;
    }

    private static int setRenderDumpState(CommandSourceStack source, boolean enabled) {
        com.spoilageenhanced.client.RenderDump.setEnabled(enabled);
        String statusKey = enabled ? SpoilageEnhancedTranslations.CMD_LOGGING_ENABLED : SpoilageEnhancedTranslations.CMD_LOGGING_DISABLED;
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_RENDERDUMP_SET, Component.translatable(statusKey)), true);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_HELP), false);
        return 1;
    }

    /**
     * Right-click a block as the executing player, through vanilla's own use path.
     *
     * <p>Exists because the behaviour lens had no way to click anything: RCON can place and
     * summon, so every task about cauldrons, item frames or opening a container came back
     * BLOCKED. This calls {@code BlockState.useItemOn} / {@code useWithoutItem} exactly as a
     * real right-click does, so whatever the mod hooks into that path is exercised for real.
     * Opening a container this way is also what makes {@code menuclick} usable.</p>
     */
    private static int useBlock(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "This command acts AS a player and there is none here. Run it from a client "
                            + "(the self-test harness does), not from the server console or RCON."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);

        net.minecraft.world.InteractionResult withItem = state.useItemOn(
                player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        // InteractionResult is a sealed interface of records here, not an enum: no name(),
        // and the "nothing happened with the held item" answers are PASS and
        // TRY_WITH_EMPTY_HAND. Both mean vanilla would fall through to the empty-hand
        // interaction, which is the branch a right-click on a cauldron or a berry bush takes.
        String outcome = String.valueOf(withItem);
        if (withItem instanceof net.minecraft.world.InteractionResult.Pass
                || withItem instanceof net.minecraft.world.InteractionResult.TryEmptyHandInteraction) {
            outcome = String.valueOf(state.useWithoutItem(level, player, hit));
        }

        final String result = outcome;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "used " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + pos + " -> " + result), false);
        SpoilageEnhancedLogger.log("DebugUse: " + pos + " " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + " with " + BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()) + " -> " + result);
        return 1;
    }

    /**
     * Pass 641 (L13 behaviour): drive {@code Animal.mobInteract} as the player, so
     * AnimalEntityMixin's rotten-food guard fires. RCON cannot right-click an entity —
     * this command finds the nearest entity of the given type within 5 blocks of the
     * player, then calls {@code mobInteract} directly with the player's main-hand item.
     * Used by the {@code animal} self-test to drive the cow-wheat feed path.
     */
    private static int useNearestEntity(CommandSourceStack source, String entityTypeKey) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "This command acts AS a player and there is none here."));
            return 0;
        }
        ServerLevel level = source.getLevel();

        // Resolve the entity type from a short key (e.g. "cow", "chicken", "pig").
        net.minecraft.world.entity.EntityType<?> entityType = switch (entityTypeKey.toLowerCase(java.util.Locale.ROOT)) {
            case "cow" -> net.minecraft.world.entity.EntityTypes.COW;
            case "chicken" -> net.minecraft.world.entity.EntityTypes.CHICKEN;
            case "pig" -> net.minecraft.world.entity.EntityTypes.PIG;
            case "sheep" -> net.minecraft.world.entity.EntityTypes.SHEEP;
            case "wolf" -> net.minecraft.world.entity.EntityTypes.WOLF;
            default -> null;
        };
        if (entityType == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "Unknown entity type: " + entityTypeKey + " (try cow/chicken/pig/sheep/wolf)"));
            return 0;
        }

        net.minecraft.world.entity.animal.Animal nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (net.minecraft.world.entity.animal.Animal e : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class,
                new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(5.0))) {
            if (e.getType() != entityType) continue;
            double d = e.distanceToSqr(player);
            if (d < bestDist) { bestDist = d; nearest = e; }
        }
        if (nearest == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "No " + entityTypeKey + " within 5 blocks of player"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        net.minecraft.world.InteractionResult result = nearest.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "useentity " + entityTypeKey + " -> " + result + " (held=" + held.getItem() + ")"), false);
        SpoilageEnhancedLogger.log("DebugUseEntity: " + entityTypeKey + " held=" + held.getItem() + " -> " + result);
        return 1;
    }

    /**
     * Pass 644 (L11 — load behaviour): spawn N single-item entities around the source
     * position in one server tick, to stress ItemEntityMixin's phase-spreading and the
     * per-entity spoilage tick under volume. Each entity is a 1-item stack with a fresh
     * tracker, spread over a 20x20 area (i % 40 rows of 0.5-block spacing) so they do
     * not merge instantly.
     */
    private static int spawnStressItems(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        net.minecraft.world.phys.Vec3 base = source.getPosition();
        long now = level.getGameTime();
        SpoilageConfig config = SpoilageConfig.getInstance();
        long freshDur = config.getFreshDurationForItem(net.minecraft.world.item.Items.CARROT);

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double x = base.x + (i % 40) * 0.5 - 10.0;
            double z = base.z + (i / 40) * 0.5 - 10.0;
            ItemStack stack = new ItemStack(net.minecraft.world.item.Items.CARROT, 1);
            stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE,
                    new com.spoilageenhanced.component.SpoilageData(
                            java.util.List.of(now + freshDur / 2),
                            java.util.List.of(), 0, 1.0));
            net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                    level, x, base.y, z, stack);
            if (level.addFreshEntity(entity)) spawned++;
        }
        final int finalSpawned = spawned;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "stress: spawned " + finalSpawned + " item entities"), false);
        SpoilageEnhancedLogger.log("DebugStress: spawned " + finalSpawned + " item entities at "
                + base.x + "," + base.y + "," + base.z);
        return finalSpawned;
    }

    /**
     * Perform a real click in the player's open container menu.
     *
     * <p>This is the path the mod actually guards: the check that keeps rotten food out of a
     * crafting grid lives in {@code AbstractContainerMenu.clicked}, and the defect where a
     * right-click slipped a rotten carrot past it was invisible to anything that did not go
     * through that method. A helper that moved the item itself would have reported success
     * while the real click stayed broken, so this calls {@code clicked} and nothing else.</p>
     *
     * <p>{@code type} is a {@code ContainerInput} name, e.g. PICKUP
     * (button 0 = left, 1 = right), QUICK_MOVE, SWAP, THROW.</p>
     */
    private static int menuClick(CommandSourceStack source, int slot, int button, String type) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "This command acts AS a player and there is none here. Run it from a client "
                            + "(the self-test harness does), not from the server console or RCON."));
            return 0;
        }
        net.minecraft.world.inventory.ContainerInput clickType;
        try {
            clickType = net.minecraft.world.inventory.ContainerInput.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "unknown click type '" + type + "'. Try PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL."));
            return 0;
        }

        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "no open menu — open one first, e.g. `spoilage debug use <x> <y> <z>` on a container."));
            return 0;
        }
        if (slot >= menu.slots.size()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "slot " + slot + " is out of range; this menu has " + menu.slots.size() + " slots."));
            return 0;
        }

        menu.clicked(slot, button, clickType, player);
        menu.broadcastChanges();

        String carried = menu.getCarried().isEmpty() ? "empty"
                : BuiltInRegistries.ITEM.getKey(menu.getCarried().getItem()) + " x" + menu.getCarried().getCount();
        String inSlot = "n/a";
        if (slot >= 0 && slot < menu.slots.size()) {
            net.minecraft.world.item.ItemStack s = menu.slots.get(slot).getItem();
            inSlot = s.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(s.getItem()) + " x" + s.getCount();
        }
        final String c = carried;
        final String v = inSlot;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "clicked slot " + slot + " (" + clickType + ", button " + button + ") -> slot=" + v + ", carried=" + c), false);
        SpoilageEnhancedLogger.log("DebugMenuClick: " + menu.getClass().getSimpleName() + " slot " + slot
                + " " + clickType + " button " + button + " -> slot=" + v + ", carried=" + c);
        return 1;
    }

    private static int inspectTargetedBlock(CommandSourceStack source, BlockPos targetOverride) {
        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_PLAYER_ONLY));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        BlockPos pos;
        if (targetOverride != null) {
            // Pass 488 (L13 behaviour): explicit <x y z> — skip the crosshair pick entirely.
            pos = targetOverride;
        } else {
            HitResult hit = player.pick(5.0D, 0.0F, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_AIM_AT_BLOCK));
                return 0;
            }
            BlockHitResult blockHit = (BlockHitResult) hit;
            pos = blockHit.getBlockPos();
        }
        ServerLevel world = source.getLevel();
        BlockState state = world.getBlockState(pos);

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String dropItemId = DynamicFoodBlockCache.getFoodDrop(state, world, pos);

        BlockSpoilageData data = BlockSpoilageData.get(world);
        boolean isTracked = data.isTracked(pos);

        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_INSPECT_HEADER, pos.getX(), pos.getY(), pos.getZ()), false);
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_BLOCK, blockId, (dropItemId != null ? dropItemId : "none")), false);
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_TRACKING_STATUS,
                (isTracked ? Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_TRACKING_TRACKED).getString()
                           : Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_TRACKING_UNTRACKED).getString())), false);

        if (dropItemId != null) {
            Item dropItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(dropItemId));
            FoodSpoilageUtil.SpoilageState spoilState = data.getSpoilageState(pos, world, dropItem);
            long ticksRemaining = data.getTicksUntilNextStage(pos, world, dropItem);

            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_STATE_REMAINING, spoilState.name(), ticksRemaining), false);

            if (isTracked) {
                BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
                if (entry != null) {
                    source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_EXPIRATION_TICK, entry.expirationTime, world.getGameTime()), false);
                }
            } else {
                ChunkPos chunkPos = ChunkPos.containing(pos);
                long chunkBirth = data.getChunkBirthTime(chunkPos);
                // Pass 429 (L7 — boundary): getChunkBirthTime returns -1 when the chunk has
                // no recorded birth time (BlockSpoilageData.java:390). The old subtraction
                // gameTime - (-1) = gameTime + 1 reported an absurd age (the entire world's
                // age + 1) for a chunk that was never tracked. Guard the sentinel: only
                // compute the age when a birth time exists.
                if (chunkBirth >= 0) {
                    long chunkAge = world.getGameTime() - chunkBirth;
                    source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_ESTIMATED_FRESHNESS, chunkAge), false);
                    source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_BIRTH, chunkBirth, world.getGameTime()), false);
                } else {
                    source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_BIRTH, -1L, world.getGameTime()), false);
                }
            }
        }

        return 1;
    }

    private static int inspectCurrentChunk(CommandSourceStack source) {
        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_PLAYER_ONLY));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.blockPosition();
        ChunkPos chunkPos = ChunkPos.containing(pos);
        ServerLevel world = source.getLevel();
        BlockSpoilageData data = BlockSpoilageData.get(world);

        long chunkBirth = data.getChunkBirthTime(chunkPos);
        long currentTick = world.getGameTime();
        // Pass 429 (L7 — boundary): same sentinel guard as inspectTargetedBlock above —
        // getChunkBirthTime returns -1 for a chunk with no recorded birth time, and the
        // ungated subtraction produced currentTick + 1 (an absurd age) plus a nonsense
        // day count from the /1200 division. Only compute the age when a birth time exists.
        if (chunkBirth >= 0) {
            long chunkAge = currentTick - chunkBirth;
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_HEADER, chunkPos.x(), chunkPos.z()), false);
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_BIRTH_ABS, chunkBirth, currentTick), false);
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_AGE, chunkAge, (chunkAge / 1200L)), false);
        } else {
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_HEADER, chunkPos.x(), chunkPos.z()), false);
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_CHUNK_BIRTH_ABS, -1L, currentTick), false);
        }

        return 1;
    }

    private static int dumpAllData(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        BlockSpoilageData data = BlockSpoilageData.get(world);

        try {
            File logDir = new File(SpoilageEnhancedPlatform.getGameDir().toFile(), "spoilage_enhanced_logs");
            if (!logDir.exists()) logDir.mkdirs();
            File dumpFile = new File(logDir, "dump.txt");

            try (PrintWriter writer = new PrintWriter(new FileWriter(dumpFile, false))) {
                writer.println("=== Spoilage Enhanced Data Dump ===");
                writer.println("Timestamp: " + LocalDateTime.now());
                writer.println("World Game Time: " + world.getGameTime());
                writer.println("Dimension: " + world.dimension().identifier().toString());
                writer.println("Speed Multiplier: " + SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                writer.println();

                Map<Long, BlockSpoilageData.BlockSpoilageEntry> entries = data.getEntries();
                writer.println("--- Tracked Blocks (" + entries.size() + ") ---");
                // Pass 486 (L13 — observed behaviour): the stored state is a LAZY snapshot —
                // it only advances when something queries the block (HUD aim, drop, inspect).
                // Dumping the raw stored value showed a FRESH entry long after its expiration
                // had passed, which reads as "blocks never age" and sent this session chasing
                // a state machine that was working correctly. Show the computed state too:
                // getSpoilageState advances the entry in place, so the dump now reports what
                // a player looking at the block would actually see.
                for (Map.Entry<Long, BlockSpoilageData.BlockSpoilageEntry> e : entries.entrySet()) {
                    BlockPos p = BlockPos.of(e.getKey());
                    BlockSpoilageData.BlockSpoilageEntry entry = e.getValue();
                    // Pass 491 (L13 behaviour): a null dropItem reaches
                    // getFreshDurationForItem(null) -> ConcurrentHashMap.put(null, ...) -> NPE
                    // ("Cannot invoke Object.hashCode() because key is null"). Resolve the
                    // block's own item first — every tracked block is spoilable, so the
                    // block's asItem() is always the right dropItem here. But if the block
                    // is AIR (stale entry for a broken block), asItem() returns Items.AIR.
                    // We must never pass null to getSpoilageState (it would reach
                    // getFreshDurationForItem(null) -> ConcurrentHashMap.put(null, ...) -> NPE).
                    // Use the block's item if it's not AIR, otherwise fall back to the entry's
                    // own state to infer a valid item (ROTTEN entries don't need durations).
                    Item dropItem = world.getBlockState(p).getBlock().asItem();
                    if (dropItem == net.minecraft.world.item.Items.AIR) {
                        // Stale entry for a broken block — use a dummy spoilable item to avoid
                        // null. The computed state will just confirm the stored state.
                        dropItem = net.minecraft.world.item.Items.CARROT;
                    }
                    FoodSpoilageUtil.SpoilageState computed = data.getSpoilageState(p, world, dropItem);
                    String stateNote = computed == entry.state ? "" : " (computed: " + computed + ")";
                    writer.println("  Pos: " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                            + " | State: " + entry.state + stateNote
                            + " | Expiration: " + entry.expirationTime
                            + " | LegacyBirth: " + entry.legacyBirthTime);
                }
                writer.println();

                Map<Long, Long> chunks = data.getChunkBirthTimes();
                writer.println("--- Chunk Birth Times (" + chunks.size() + ") ---");
                for (Map.Entry<Long, Long> c : chunks.entrySet()) {
                    ChunkPos cp = ChunkPos.unpack(c.getKey());
                    writer.println("  Chunk: [" + cp.x() + ", " + cp.z() + "] | BirthTick: " + c.getValue());
                }
            }

            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_DUMP_SUCCESS), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_DEBUG_DUMP_ERROR, e.getMessage()));
            return 0;
        }
    }

    /**
     * Simulates eating 1 item from a stack with the given spoilage state.
     * Used to verify BUG-12 (finishUsingItem must extract WORST items first).
     */
    private static int simulateEat(CommandSourceStack source, Identifier itemId, String state, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }

        long now = source.getLevel().getGameTime();
        SpoilageConfig config = SpoilageConfig.getInstance();

        com.spoilageenhanced.component.SpoilageData ingredientData = switch (state.toLowerCase(java.util.Locale.ROOT)) {
            case "fresh" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(now + config.getFreshDurationForItem(item) / 2),
                    java.util.List.of(), 0, 1.0);
            case "stale" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(),
                    java.util.List.of(now + config.getStaleDurationForItem(item) / 2), 0, 1.0);
            case "rotten" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(), java.util.List.of(), 1, 1.0);
            case "mixed" -> {
                long freshDur = config.getFreshDurationForItem(item);
                long staleDur = config.getStaleDurationForItem(item);
                java.util.List<Long> fresh = new java.util.ArrayList<>();
                java.util.List<Long> stale = new java.util.ArrayList<>();
                fresh.add(now + freshDur / 2);
                stale.add(now + staleDur / 2);
                yield new com.spoilageenhanced.component.SpoilageData(fresh, stale, 1, 1.0);
            }
            default -> {
                source.sendFailure(Component.literal("state must be one of: fresh, stale, rotten, mixed"));
                yield com.spoilageenhanced.component.SpoilageData.DEFAULT;
            }
        };
        if (ingredientData == com.spoilageenhanced.component.SpoilageData.DEFAULT) return 0;

        ItemStack stack = new ItemStack(item, count);
        if (ingredientData != null) {
            stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, ingredientData);
        }

        com.spoilageenhanced.component.SpoilageData data = stack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No spoilage data to eat from"), false);
            return 0;
        }

        // Use extractWorstItems (the FIXED logic from finishUsingItem after BUG-12)
        com.spoilageenhanced.component.SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        com.spoilageenhanced.component.SpoilageData consumedItemData = split[1];
        com.spoilageenhanced.component.SpoilageData remainingData = split[0];

        stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, remainingData);

        String consumedState = "FRESH";
        if (!consumedItemData.freshExpirations().isEmpty()) consumedState = "FRESH";
        else if (!consumedItemData.staleExpirations().isEmpty()) consumedState = "STALE";
        else if (consumedItemData.rottenCount() > 0) consumedState = "ROTTEN";

        String remainingState = "FRESH";
        if (!remainingData.freshExpirations().isEmpty()) remainingState = "FRESH";
        else if (!remainingData.staleExpirations().isEmpty()) remainingState = "STALE";
        else if (remainingData.rottenCount() > 0) remainingState = "ROTTEN";

        String message = "eat " + itemId + "(" + state + ") x" + count + " -> consumed: " + consumedState
                + " | remaining: " + remainingState
                + " fresh=" + remainingData.freshExpirations().size()
                + " stale=" + remainingData.staleExpirations().size()
                + " rotten=" + remainingData.rottenCount();

        // Pass 489 (L13 behaviour): the old simulateEat only extracted the worst item and
        // reported the split, so the ROTTEN consume effects (poison, hunger loss) were never
        // exercised headlessly — a real eating path (ItemStackMixin.onFinishUsingItem:118-126)
        // applies them, and nothing here ever confirmed they fired. Apply them now, the same
        // way the mixin does, so the full behaviour chain is verifiable without a mouse.
        net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        SpoilageConfig.EffectsConfig fx = SpoilageConfig.getInstance().getEffectsConfig();
        String effectsApplied = "none";
        net.minecraft.world.entity.LivingEntity entity =
                source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                        ? living : null;
        if (consumedState.equals("STALE")) {
            if (entity instanceof net.minecraft.world.entity.player.Player player && food != null) {
                int penalty = (int) (food.nutrition() * fx.stale_hunger_penalty_percent / 100.0);
                float satPenalty = (float) (food.saturation() * fx.stale_saturation_penalty_percent / 100.0);
                player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - penalty));
                player.getFoodData().setSaturation(Math.max(0f, player.getFoodData().getSaturationLevel() - satPenalty));
            }
            if (source.getLevel().getRandom().nextFloat() < (float) fx.stale_nausea_chance) {
                if (entity != null) {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, fx.stale_nausea_duration_ticks, 0));
                }
            }
            effectsApplied = "stale_hunger_penalty=" + fx.stale_hunger_penalty_percent
                    + "% nausea=" + fx.stale_nausea_chance;
        } else if (consumedState.equals("ROTTEN")) {
            if (entity instanceof net.minecraft.world.entity.player.Player player && food != null) {
                if (fx.rotten_removes_all_hunger) {
                    player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - food.nutrition()));
                    player.getFoodData().setSaturation(Math.max(0f, player.getFoodData().getSaturationLevel() - food.saturation()));
                }
            }
            if (entity != null) {
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, fx.rotten_poison_duration_ticks, 0));
            }
            effectsApplied = "removes_all_hunger=" + fx.rotten_removes_all_hunger
                    + " poison=" + fx.rotten_poison_duration_ticks + " ticks";
        }

        String finalMessage = message + " | effects=" + effectsApplied;
        source.sendSuccess(() -> Component.literal(finalMessage), false);
        SpoilageEnhancedLogger.log(finalMessage);
        return 1;
    }

    /**
     * Places a block as if a player had placed it, carrying the given spoilage state.
     *
     * <p>Headless verification hook for BUG-12: {@code /setblock} bypasses
     * {@code BlockItem.place} (and therefore {@code setPlacedBy}), so the GourdBlockMixin
     * path can only be exercised by a real placement. This subcommand builds the same
     * {@link net.minecraft.world.item.context.BlockPlaceContext} a player click would and
     * calls {@code BlockItem.place} directly, no mouse required.</p>
     */
    private static int placeBlockAsPlayer(CommandSourceStack source, Identifier itemId, String state, BlockPos targetOverride) {
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) {
            source.sendFailure(Component.literal("Not a placeable block item: " + itemId));
            return 0;
        }

        long now = source.getLevel().getGameTime();
        SpoilageConfig config = SpoilageConfig.getInstance();

        com.spoilageenhanced.component.SpoilageData data = switch (state.toLowerCase(java.util.Locale.ROOT)) {
            case "fresh" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(now + config.getFreshDurationForItem(item) / 2),
                    java.util.List.of(), 0, 1.0);
            case "stale" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(),
                    java.util.List.of(now + config.getStaleDurationForItem(item) / 2), 0, 1.0);
            case "rotten" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(), java.util.List.of(), 1, 1.0);
            case "mixed" -> {
                // Pass 493 (L13 behaviour): debug place now accepts "mixed" so the
                // worst-first inheritance (BUG-12) can be verified through the REAL
                // BlockItem.place path. Build a 1-block mixed stack: 1 fresh, 1 stale,
                // 1 rotten. GourdBlockMixin's getWorstState(ROTTEN) will pull the rotten
                // item and the placed block is tracked ROTTEN.
                long freshDur = config.getFreshDurationForItem(item);
                long staleDur = config.getStaleDurationForItem(item);
                yield new com.spoilageenhanced.component.SpoilageData(
                        java.util.List.of(now + freshDur / 2),
                        java.util.List.of(now + staleDur / 2),
                        1, 1.0);
            }
            case "new" -> null;
            default -> {
                source.sendFailure(Component.literal("state must be one of: fresh, stale, rotten, mixed, new"));
                yield com.spoilageenhanced.component.SpoilageData.DEFAULT;
            }
        };
        if (data == com.spoilageenhanced.component.SpoilageData.DEFAULT) return 0;

        ItemStack stack = new ItemStack(item, 1);
        if (data != null) {
            stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, data);
        }

        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run via /execute at @p — a player context is required for placement"));
            return 0;
        }
        // The self-test script builds a stone pillar 2 blocks west of the player and moves
        // the player onto a platform beside it. Aim at the pillar's top block (2 west, one
        // below the player's feet): it is solid and non-replaceable, so the context uses
        // relativePos = pillarTop.relative(UP) — free air above the pillar, clear of the player.
        // Pass 487 (L13 behaviour — observed): if an explicit target was given via the
        // optional <x y z> arguments, use that instead of the hardcoded pillar offset.
        BlockPos targetPos = (targetOverride != null) ? targetOverride : player.blockPosition().offset(-2, 0, 0);
        BlockPos support = (targetOverride != null) ? targetOverride.below() : player.blockPosition().offset(-2, -1, 0);
        BlockHitResult hit = new BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(support), net.minecraft.core.Direction.UP, support, false);
        net.minecraft.world.item.context.BlockPlaceContext ctx =
                new net.minecraft.world.item.context.BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);

        net.minecraft.world.InteractionResult result = blockItem.place(ctx);
        String message = "Placed " + itemId + "(" + state + ") at " + targetPos + " -> " + result
                + " (stack remaining: " + stack.getCount() + ")";
        source.sendSuccess(() -> Component.literal(message), false);
        SpoilageEnhancedLogger.log(message);
        return result.consumesAction() ? 1 : 0;
    }

    /**
     * Spawns an item entity with the given spoilage state at the command source position.
     * Used to test ItemEntity merge behavior.
     */
    private static int spawnItemEntity(CommandSourceStack source, Identifier itemId, String state, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }

        long now = source.getLevel().getGameTime();
        SpoilageConfig config = SpoilageConfig.getInstance();

        com.spoilageenhanced.component.SpoilageData ingredientData = switch (state.toLowerCase(java.util.Locale.ROOT)) {
            case "fresh" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.Collections.nCopies(count, now + config.getFreshDurationForItem(item) / 2),
                    java.util.List.of(), 0, 1.0);
            case "stale" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(),
                    java.util.Collections.nCopies(count, now + config.getStaleDurationForItem(item) / 2), 0, 1.0);
            case "rotten" -> new com.spoilageenhanced.component.SpoilageData(
                    java.util.List.of(), java.util.List.of(), count, 1.0);
            case "mixed" -> {
                // Pass 573 (L13 behaviour): debug spawn must accept the same states as debug
                // place, which gained "mixed" in Pass 493 so the worst-first inheritance
                // (BUG-12) could be verified through the real BlockItem.place path. A 1-block
                // mixed stack here is 1 fresh + 1 stale + 1 rotten, so a count of N yields N
                // of each class — the same shape BlockDropSpoilageHandler.mergeItems has to
                // reconcile when that stack is later broken and re-dropped.
                long freshDur = config.getFreshDurationForItem(item);
                long staleDur = config.getStaleDurationForItem(item);
                yield new com.spoilageenhanced.component.SpoilageData(
                        java.util.Collections.nCopies(count, now + freshDur / 2),
                        java.util.Collections.nCopies(count, now + staleDur / 2),
                        count, 1.0);
            }
            case "new" -> null;
            default -> {
                source.sendFailure(Component.literal("state must be one of: fresh, stale, rotten, mixed, new"));
                yield com.spoilageenhanced.component.SpoilageData.DEFAULT;
            }
        };
        if (ingredientData == com.spoilageenhanced.component.SpoilageData.DEFAULT) return 0;

        ItemStack stack = new ItemStack(item, count);
        if (ingredientData != null) {
            stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, ingredientData);
        }

        // Pass 468 (L1 — silent failure): the entity used to be constructed at a hardcoded
        // (0.5, 70.0, 0.5) while the log line below claimed it spawned at source.getPosition().
        // Every verification run that relied on this command found nothing at the reported
        // position — the items were 60+ blocks away, or already scattered by physics. Spawn
        // at the command source so the log and the world agree.
        net.minecraft.world.phys.Vec3 spawnPos = source.getPosition();
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                source.getLevel(), spawnPos.x, spawnPos.y, spawnPos.z, stack);
        source.getLevel().addFreshEntity(entity);

        String message = "Spawned " + itemId + "(" + state + ") x" + count + " at " + spawnPos;
        source.sendSuccess(() -> Component.literal(message), false);
        SpoilageEnhancedLogger.log(message);
        return 1;
    }
}
