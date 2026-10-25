package com.theminespirit.neoforge.board;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.core.town.TownSnapshot;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class SpiritPlanCommand {
    private SpiritPlanCommand() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("plan").executes(context -> show(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        Optional<TownSnapshot> snapshot = TownSnapshots.current(source.getLevel());
        if (snapshot.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("Плана поселения нет: доска размечает его, когда у духов есть дом"), false);
            return 0;
        }
        TownSnapshot plan = snapshot.orElseThrow();
        source.sendSuccess(
                () -> Component.literal("План поселения: площадь " + plan.area() + ", этап " + plan.stage()
                        + ", контур " + plan.contour().size() + " столбов, калиток "
                        + plan.contourGates().size()
                        + "; ограда этапа " + plan.stageFenceProgress().percent() + "%, тропинок "
                        + plan.paths().size()),
                false);
        for (TownSnapshot.Lot lot : plan.lots()) {
            if (lot.stage() == plan.stage()) {
                source.sendSuccess(
                        () -> Component.literal("  " + lot.name() + " (" + lot.x() + ", " + lot.z() + ") "
                                + lot.width() + "×" + lot.depth() + ": "
                                + lot.progress().percent() + "%"
                                + (lot.progress().damaged() ? ", повреждена" : "")),
                        false);
            }
        }
        return plan.lots().size();
    }
}
