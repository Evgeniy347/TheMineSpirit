package com.theminespirit.neoforge.crater;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.core.building.Crater;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /spirit craters} — список запомненных воронок: id, центр, радиус и уровень края.
 *
 * <p>По id воронки человек ставит скрипт fill_crater. Пустой список — взрывов рядом с поселением не было.
 */
public final class CraterCommand {
    private CraterCommand() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("craters").executes(context -> list(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<Crater> craters = CraterStore.get(level).all();
        if (craters.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("Запомненных воронок нет: взрывов криперов рядом с поселением "
                            + "не было или они уже заделаны"),
                    false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Запомненные воронки (" + craters.size() + "):"), false);
        for (Crater crater : craters) {
            source.sendSuccess(
                    () -> Component.literal(crater.id() + ": центр "
                            + crater.center().x() + ","
                            + crater.center().y() + "," + crater.center().z() + ", радиус " + crater.radius()
                            + ", уровень края " + crater.groundLevel()),
                    false);
        }
        return craters.size();
    }
}
