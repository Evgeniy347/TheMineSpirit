package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.neoforge.demo.DemoRegistry;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /spirit demo} — креативная демонстрация: подкоманда на каждую постройку, upgrade, list и stop. */
final class SpiritDemoCommand {
    private static final List<String> ROTATIONS =
            List.of("none", "clockwise_90", "clockwise_180", "counterclockwise_90");

    private SpiritDemoCommand() {}

    @FunctionalInterface
    private interface Starter {
        int run(CommandContext<CommandSourceStack> context, BlockPos pos, BuildRotation rotation);
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("demo")
                .then(literal("list").executes(SpiritDemoCommand::list))
                .then(literal("stop").executes(SpiritDemoCommand::stop))
                .then(building("family_house"))
                .then(building("stone_house"))
                .then(building("storage_hut"))
                .then(building("storage"))
                .then(building("well"))
                .then(building("torch_post"))
                .then(building("palisade_section"))
                .then(building("pen"))
                .then(building("enchant_room"))
                .then(building("mine_entrance")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> building(String id) {
        return building(DemoBuildings.require(id));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> building(DemoBuildings.Building building) {
        LiteralArgumentBuilder<CommandSourceStack> node = literal(building.id());
        node.executes(context -> start(context, building, 0, null, null, 0, 0));

        RequiredArgumentBuilder<CommandSourceStack, Integer> level = argument("level", IntegerArgumentType.integer(0));
        level.executes(context -> start(context, building, level(context), null, null, 0, 0));
        attachPos(level, (context, pos, rotation) -> start(context, building, level(context), pos, rotation, 0, 0));
        node.then(level);

        attachPos(node, (context, pos, rotation) -> start(context, building, 0, pos, rotation, 0, 0));

        node.then(literal("upgrade")
                .then(argument("from", IntegerArgumentType.integer(1))
                        .then(argument("to", IntegerArgumentType.integer(1))
                                .executes(context -> start(
                                        context,
                                        building,
                                        0,
                                        null,
                                        null,
                                        integer(context, "from"),
                                        integer(context, "to")))
                                .then(posAndRotation((context, pos, rotation) -> start(
                                        context,
                                        building,
                                        0,
                                        pos,
                                        rotation,
                                        integer(context, "from"),
                                        integer(context, "to")))))));
        return node;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, ?>> void attachPos(T parent, Starter starter) {
        parent.then(posAndRotation(starter));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Coordinates> posAndRotation(Starter starter) {
        return argument("pos", BlockPosArgument.blockPos())
                .executes(context -> starter.run(context, BlockPosArgument.getBlockPos(context, "pos"), null))
                .then(argument("rotation", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ROTATIONS, builder))
                        .executes(context -> {
                            BuildRotation rotation;
                            try {
                                rotation = BuildRotation.parse(StringArgumentType.getString(context, "rotation"));
                            } catch (IllegalArgumentException refused) {
                                context.getSource().sendFailure(Component.literal(refused.getMessage()));
                                return 0;
                            }
                            return starter.run(context, BlockPosArgument.getBlockPos(context, "pos"), rotation);
                        }));
    }

    private static int start(
            CommandContext<CommandSourceStack> context,
            DemoBuildings.Building building,
            int level,
            BlockPos pos,
            BuildRotation rotation,
            int from,
            int to) {
        CommandSourceStack source = context.getSource();
        return DemoRegistry.get(source.getLevel()).start(source, building, level, pos, rotation, from, to);
    }

    private static int level(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "level");
    }

    private static int integer(CommandContext<CommandSourceStack> context, String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Демо-стройки (только оператор):"), false);
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            String levels = building.leveled()
                    ? "уровни "
                            + building.levels().stream()
                                    .map(entry -> entry.level() + "→" + entry.scheme())
                                    .toList()
                    : "схема " + building.levels().get(0).scheme();
            source.sendSuccess(
                    () -> Component.literal("  " + building.id() + " — " + building.name() + "; " + levels), false);
        }
        return DemoBuildings.all().size();
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int removed = DemoRegistry.get(source.getLevel()).stop();
        if (removed == 0) {
            source.sendFailure(Component.literal("Активных демо-строек нет: убирать нечего"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Демо-стройки отменены, духов убрано: " + removed), true);
        return removed;
    }
}
