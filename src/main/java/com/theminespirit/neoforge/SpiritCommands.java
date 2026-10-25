package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.neoforge.board.SpiritPlanCommand;
import com.theminespirit.neoforge.crater.CraterCommand;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SpiritCommands {
    private static final Predicate<CommandSourceStack> OPERATOR = source -> source.hasPermission(2);

    private SpiritCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> spirit = literal("spirit");
        spirit.then(literal("version").executes(SpiritVersionCommand::version));

        LiteralArgumentBuilder<CommandSourceStack> operatorOnly = new OperatorSubtree();
        SpiritSpawnCommand.register(operatorOnly);
        SpiritObserveCommand.register(operatorOnly);
        SpiritTaskCommands.register(operatorOnly);
        SpiritMemoryCommand.register(operatorOnly);
        SpiritZoneCommand.register(operatorOnly);
        SpiritHomeCommand.register(operatorOnly);
        SpiritCampCommand.register(operatorOnly);
        SpiritDecisionsCommand.register(operatorOnly);
        SpiritBoardCommand.register(operatorOnly);
        SpiritTablesCommand.register(operatorOnly);
        SpiritPresetsCommand.register(operatorOnly);
        CraterCommand.register(operatorOnly);
        SpiritInviteCommand.register(operatorOnly);
        SpiritUnprotectCommand.register(operatorOnly);
        SpiritDemoCommand.register(operatorOnly);
        SpiritPlanCommand.register(operatorOnly);

        dispatcher.register(spirit);
        dispatcher.register(operatorOnly);
    }

    private static final class OperatorSubtree extends LiteralArgumentBuilder<CommandSourceStack> {
        private OperatorSubtree() {
            super("spirit");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> then(ArgumentBuilder<CommandSourceStack, ?> argument) {
            argument.requires(OPERATOR);
            return super.then(argument);
        }
    }
}
