package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import com.theminespirit.neoforge.policy.UnprotectService;
import java.time.Instant;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class SpiritUnprotectCommand {
    private static final String DEFAULT_REASON = "по просьбе игрока";

    private SpiritUnprotectCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("unprotect")
                .then(literal("list").executes(SpiritUnprotectCommand::list))
                .then(literal("remove")
                        .then(argument("id", StringArgumentType.word()).executes(SpiritUnprotectCommand::remove)))
                .then(coordinates()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> coordinates() {
        return argument("x1", IntegerArgumentType.integer())
                .then(argument("y1", IntegerArgumentType.integer())
                        .then(argument("z1", IntegerArgumentType.integer())
                                .then(argument("x2", IntegerArgumentType.integer())
                                        .then(argument("y2", IntegerArgumentType.integer())
                                                .then(argument("z2", IntegerArgumentType.integer())
                                                        .executes(context -> grant(context, DEFAULT_REASON))
                                                        .then(argument("reason", StringArgumentType.greedyString())
                                                                .executes(context -> grant(
                                                                        context,
                                                                        StringArgumentType.getString(
                                                                                context, "reason")))))))));
    }

    private static int grant(CommandContext<CommandSourceStack> context, String reason) {
        CommandSourceStack source = context.getSource();
        BlockCoord first = new BlockCoord(
                IntegerArgumentType.getInteger(context, "x1"),
                IntegerArgumentType.getInteger(context, "y1"),
                IntegerArgumentType.getInteger(context, "z1"));
        BlockCoord second = new BlockCoord(
                IntegerArgumentType.getInteger(context, "x2"),
                IntegerArgumentType.getInteger(context, "y2"),
                IntegerArgumentType.getInteger(context, "z2"));
        String dimension = source.getLevel().dimension().location().toString();
        try {
            UnprotectArea area = UnprotectService.of(source.getServer())
                    .grant(dimension, first, second, source.getTextName(), reason, 0, System.currentTimeMillis());
            source.sendSuccess(
                    () -> Component.literal("Защита снята с области " + area.id() + ": " + format(area)
                            + ". Вернуть защиту — /spirit unprotect remove " + area.id()),
                    true);
            return 1;
        } catch (IllegalArgumentException refused) {
            source.sendFailure(Component.literal("Защита не снята: " + refused.getMessage()));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<UnprotectArea> areas = UnprotectService.of(source.getServer()).list();
        if (areas.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Разрешённых областей нет: постройки игрока защищены"), false);
            return 0;
        }
        for (UnprotectArea area : areas) {
            source.sendSuccess(
                    () -> Component.literal(area.id() + " | " + format(area) + " | " + area.grantedBy() + " | "
                            + area.reason() + " | " + until(area)),
                    false);
        }
        source.sendSuccess(() -> Component.literal("Разрешённых областей: " + areas.size()), false);
        return areas.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        if (!UnprotectService.of(source.getServer()).remove(id)) {
            source.sendFailure(
                    Component.literal("Разрешение '" + id + "' не найдено: список — /spirit unprotect list"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Разрешение '" + id + "' снято: постройки в области снова защищены"), true);
        return 1;
    }

    private static String format(UnprotectArea area) {
        return area.dimension() + " [" + area.box().min().x() + " "
                + area.box().min().y() + " " + area.box().min().z() + " — "
                + area.box().max().x() + " " + area.box().max().y() + " "
                + area.box().max().z() + "]";
    }

    private static String until(UnprotectArea area) {
        return area.expiresAtMillis() == 0 ? "бессрочно" : "до " + Instant.ofEpochMilli(area.expiresAtMillis());
    }
}
