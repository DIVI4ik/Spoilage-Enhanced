package com.spoilageenhanced.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GiveSpoiledCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("givespoiled")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("item", ItemArgument.item(registryAccess))
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("fresh");
                        builder.suggest("stale");
                        builder.suggest("rotten");
                        return builder.buildFuture();
                    })
                    .executes(context -> execute(
                        context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        context.getArgument("item", ItemInput.class),
                        StringArgumentType.getString(context, "stage"),
                        1,
                        -1
                    ))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> execute(
                            context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            context.getArgument("item", ItemInput.class),
                            StringArgumentType.getString(context, "stage"),
                            IntegerArgumentType.getInteger(context, "count"),
                            -1
                        ))
                        .then(Commands.argument("secondsRemaining", IntegerArgumentType.integer(0))
                            .executes(context -> execute(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                context.getArgument("item", ItemInput.class),
                                StringArgumentType.getString(context, "stage"),
                                IntegerArgumentType.getInteger(context, "count"),
                                IntegerArgumentType.getInteger(context, "secondsRemaining")
                            ))
                        )
                    )
                )))
        );
    }

    private static int execute(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            ItemInput itemArgument,
            String stage,
            int count,
            int secondsRemaining) throws CommandSyntaxException {
        try {
            Item item = itemArgument.item().value();
            if (!SpoilageConfig.getInstance().isSpoilable(item)) {
                source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_GIVESPOILED_NOT_SPOILABLE));
                return 0;
            }

            stage = stage.toLowerCase();
            if (!stage.equals("fresh") && !stage.equals("stale") && !stage.equals("rotten")) {
                source.sendFailure(Component.translatable(SpoilageEnhancedTranslations.CMD_GIVESPOILED_INVALID_STAGE));
                return 0;
            }

            long currentTime = source.getLevel().getGameTime();
            long defaultFresh = SpoilageConfig.getInstance().getFreshDurationForItem(item);
            long defaultStale = SpoilageConfig.getInstance().getStaleDurationForItem(item);

            double currentMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
            long ticksRemaining;
            if (secondsRemaining > 0) {
                // Pass 607 (L7 — boundary): secondsRemaining=0 must yield 0 ticks, not 1.
                // The old formula Math.max(1L, (0 * 20L) / multiplier) = 1 was wrong —
                // 0 seconds means "expire immediately", not "expire in 1 tick".
                ticksRemaining = (long) ((secondsRemaining * 20L) / (currentMultiplier > 0 ? currentMultiplier : 1.0));
            } else if (secondsRemaining == 0) {
                ticksRemaining = 0L;
            } else {
                ticksRemaining = stage.equals("fresh") ? defaultFresh : (stage.equals("stale") ? defaultStale : 0L);
            }
            // Pass 562 (L7 — boundary): a ROTTEN item has no expiration to set, so an explicit
            // secondsRemaining on the rotten stage was silently ignored by the data path but
            // still shown in the success message — the command answered "rotten, 30s left"
            // for an item that never expires. Zero the display value so the message and the
            // data agree.
            if (stage.equals("rotten")) {
                ticksRemaining = 0L;
            }

            for (ServerPlayer player : targets) {
                ItemStack stack = itemArgument.createItemStack(count);
                long expire = currentTime + ticksRemaining;

                SpoilageData data;
                if (stage.equals("fresh")) {
                    List<Long> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) list.add(expire);
                    data = new SpoilageData(list, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                } else if (stage.equals("stale")) {
                    List<Long> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) list.add(expire);
                    data = new SpoilageData(Collections.emptyList(), list, 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                } else {
                    data = new SpoilageData(Collections.emptyList(), Collections.emptyList(), count, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                }

                stack.set(ModDataComponentTypes.SPOILAGE, data);

                boolean added = player.getInventory().add(stack);
                if (!added || !stack.isEmpty()) {
                    player.drop(stack, false);
                }
            }

            long seconds = ticksRemaining / 20L;
            String finalStage = stage;
            Component itemName = item.getName(new ItemStack(item));
            source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_GIVESPOILED_SUCCESS,
                    count, itemName.getString(), finalStage, seconds), true);

            return targets.size();
        } catch (Throwable t) {
            SpoilageEnhancedLogger.log("Error in GiveSpoiledCommand: " + t.getMessage());
            t.printStackTrace();
            throw t;
        }
    }
}
