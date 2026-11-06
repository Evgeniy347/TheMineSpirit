package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.perception.MemoryCall;
import com.theminespirit.core.perception.MemoryOutcome;
import com.theminespirit.core.perception.MemoryType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

final class SpiritMemoryCommand {
    private static final String SPIRITS = "spirits";
    private static final String NAME = "name";
    private static final String TYPE = "type";

    private SpiritMemoryCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("memory")
                .then(argument(SPIRITS, EntityArgument.entities())
                        .then(literal("recall")
                                .then(argument(NAME, StringArgumentType.string())
                                        .executes(SpiritMemoryCommand::recall)))
                        .then(literal("all")
                                .then(argument(TYPE, StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(MemoryType.ids(), builder))
                                        .executes(SpiritMemoryCommand::recallAll)))
                        .then(literal("forget")
                                .then(argument(NAME, StringArgumentType.string())
                                        .executes(SpiritMemoryCommand::forget)))));
    }

    private static int recall(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, new MemoryCall.Recall(StringArgumentType.getString(context, NAME)));
    }

    private static int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, new MemoryCall.Forget(StringArgumentType.getString(context, NAME)));
    }

    private static int recallAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MemoryType type;
        try {
            type = MemoryType.parse(StringArgumentType.getString(context, TYPE));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        return run(context, new MemoryCall.RecallAll(type));
    }

    private static int run(CommandContext<CommandSourceStack> context, MemoryCall call) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(context, SPIRITS)) {
            if (entity instanceof SpiritEntity spirit) {
                spirits.add(spirit);
            }
        }
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal("Среди выбранных сущностей нет духа: память спрашивать не у кого"));
            return 0;
        }
        StringBuilder text = new StringBuilder();
        for (SpiritEntity spirit : spirits) {
            MemoryOutcome outcome = call.apply(
                    spirit.memory(), spirit.level().dimension().location().toString());
            spirit.updateMemory(outcome.store());
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("Дух '").append(spirit.getStringUUID()).append("': ").append(call.describe(outcome.finding()));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return spirits.size();
    }
}
