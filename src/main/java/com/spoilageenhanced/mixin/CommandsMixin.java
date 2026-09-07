package com.spoilageenhanced.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.spoilageenhanced.command.GiveSpoiledCommand;
import com.spoilageenhanced.command.SpoilageEnhancedDebugCommand;
import com.spoilageenhanced.command.SpoilageSpeedCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void registerSpoilageCommands(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        GiveSpoiledCommand.register(this.dispatcher, context);
        SpoilageSpeedCommand.register(this.dispatcher, context);
        SpoilageEnhancedDebugCommand.register(this.dispatcher, context);
    }
}
