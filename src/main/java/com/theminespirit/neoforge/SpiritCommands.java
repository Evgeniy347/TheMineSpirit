package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.neoforge.crater.CraterCommand;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SpiritCommands {
    private SpiritCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> spirit =
                literal("spirit").requires(source -> source.hasPermission(2));
        SpiritSpawnCommand.register(spirit);
        SpiritObserveCommand.register(spirit);
        SpiritTaskCommands.register(spirit);
        SpiritMemoryCommand.register(spirit);
        SpiritZoneCommand.register(spirit);
        SpiritHomeCommand.register(spirit);
        SpiritCampCommand.register(spirit);
        SpiritDecisionsCommand.register(spirit);
        SpiritBoardCommand.register(spirit);
        SpiritTablesCommand.register(spirit);
        SpiritPresetsCommand.register(spirit);
        CraterCommand.register(spirit);
        SpiritInviteCommand.register(spirit);
        SpiritUnprotectCommand.register(spirit);
        dispatcher.register(spirit);
    }
}
