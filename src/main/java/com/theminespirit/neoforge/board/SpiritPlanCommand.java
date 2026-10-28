package com.theminespirit.neoforge.board;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StreetLayout;
import com.theminespirit.core.town.StreetTile;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
        streets(source);
        routes(source);
        return plan.lots().size();
    }

    private static void routes(CommandSourceStack source) {
        RouteStore.get(source.getLevel())
                .plan()
                .markers()
                .values()
                .forEach(marker -> source.sendSuccess(
                        () -> Component.literal("  маршрут " + marker.resource().family() + " ("
                                + marker.point().x() + ", "
                                + marker.point().y() + ", " + marker.point().z() + "): "
                                + (marker.routeBuilt()
                                        ? "готов"
                                        : marker.pathBuilt() ? "тропа проложена" : "точка отмечена")),
                        false));
    }

    private static void streets(CommandSourceStack source) {
        SettlementPlotStore store = SettlementPlotStore.get(source.getLevel());
        Optional<TownPlan> town = store.town();
        Optional<StageSite> site = store.site();
        if (town.isEmpty() || site.isEmpty()) {
            return;
        }
        List<StreetTile> tiles = StreetLayout.of(DataTables.INSTANCE.presets().current())
                .town(
                        town.orElseThrow().streets(),
                        town.orElseThrow().anchor().x(),
                        town.orElseThrow().anchor().z(),
                        DataTables.INSTANCE.town().current().streets(),
                        site.orElseThrow().enclosure());
        Map<String, Long> kinds =
                tiles.stream().collect(Collectors.groupingBy(StreetTile::scheme, TreeMap::new, Collectors.counting()));
        source.sendSuccess(() -> Component.literal("  улицы этапа по плиткам-схемам: " + kinds), false);
    }
}
