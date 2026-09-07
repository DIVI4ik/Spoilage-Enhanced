package com.spoilageenhanced.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class SpoilageSpeedCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        registerRoot("spoilage", dispatcher);
        registerRoot("spoilage_enhanced", dispatcher);
    }

    private static void registerRoot(String commandName, CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("speed")
                    .executes(context -> getSpeed(context.getSource()))
                    .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.01, 100.0))
                        .executes(context -> setSpeed(
                            context.getSource(),
                            DoubleArgumentType.getDouble(context, "multiplier")
                        ))
                    )
                )
        );
    }

    private static int getSpeed(CommandSourceStack source) {
        double current = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        String desc = formatMultiplierDescription(current);
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_SPEED_CURRENT,
                String.format("%.2f", current), desc), false);
        return 1;
    }

    private static int setSpeed(CommandSourceStack source, double newMultiplier) {
        double oldMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        if (Math.abs(oldMultiplier - newMultiplier) < 0.0001) {
            return getSpeed(source);
        }

        double ratio = oldMultiplier / newMultiplier;
        SpoilageConfig.getInstance().setSpoilageSpeedMultiplier(newMultiplier);
        SpoilageConfig.getInstance().save();

        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                long currentTime = level.getGameTime();
                BlockSpoilageData blockData = BlockSpoilageData.get(level);
                blockData.rescaleExpirations(currentTime, ratio);
                // Pass 499 (L13 behaviour — BUG FIX): also rescale item entities in this level.
                // The ItemEntityMixin.tick path calls updateSpoilage which rescales on the next
                // 20-tick boundary, but a speed change should take effect immediately for
                // already-dropped items. Iterate all item entities and rescale their spoilage data.
                level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class), e -> true)
                        .forEach(entity -> {
                            net.minecraft.world.item.ItemStack stack = entity.getItem();
                            if (!stack.isEmpty() && stack.has(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE)) {
                                com.spoilageenhanced.util.FoodSpoilageUtil.rescaleItemTimestamps(stack, currentTime, ratio);
                                entity.setItem(stack);
                            }
                        });
            }
        } else {
            ServerLevel world = source.getLevel();
            long currentTime = world.getGameTime();
            BlockSpoilageData blockData = BlockSpoilageData.get(world);
            blockData.rescaleExpirations(currentTime, ratio);
            // Pass 499 (L13 behaviour — BUG FIX): also rescale item entities in this world.
            world.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class), e -> true)
                    .forEach(entity -> {
                        net.minecraft.world.item.ItemStack stack = entity.getItem();
                        if (!stack.isEmpty() && stack.has(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE)) {
                            com.spoilageenhanced.util.FoodSpoilageUtil.rescaleItemTimestamps(stack, currentTime, ratio);
                            entity.setItem(stack);
                        }
                    });
        }

        SpoilageEnhancedLogger.log("Spoilage speed changed: " + oldMultiplier + "x -> " + newMultiplier + "x (ratio: " + ratio + ")");

        String desc = formatMultiplierDescription(newMultiplier);
        source.sendSuccess(() -> Component.translatable(SpoilageEnhancedTranslations.CMD_SPEED_CHANGED,
                String.format("%.2f", oldMultiplier), String.format("%.2f", newMultiplier), desc), true);

        return 1;
    }

    private static String formatMultiplierDescription(double multiplier) {
        if (Math.abs(multiplier - 1.0) < 0.001) {
            return Component.translatable(SpoilageEnhancedTranslations.CMD_SPEED_NORMAL).getString();
        } else if (multiplier > 1.0) {
            return Component.translatable(SpoilageEnhancedTranslations.CMD_SPEED_FASTER, String.format("%.1f", multiplier)).getString();
        } else {
            return Component.translatable(SpoilageEnhancedTranslations.CMD_SPEED_SLOWER, String.format("%.1f", 1.0 / multiplier)).getString();
        }
    }
}
