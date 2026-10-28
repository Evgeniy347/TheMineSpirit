package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.FloatingScan;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class SpiritAuditCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHOWN = 20;

    private SpiritAuditCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("audit").then(literal("floating").executes(context -> floating(context.getSource()))));
    }

    private static int floating(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        if (site.isEmpty()) {
            source.sendFailure(Component.literal("Участок поселения ещё не размечен: аудит висящих объектов пуст"));
            return 0;
        }
        StageSite stage = site.orElseThrow();
        List<FloatingBlocks.Finding> found = FloatingScan.find(level, stage, stage.enclosure());
        for (FloatingBlocks.Finding finding : found) {
            LOGGER.info(
                    "[audit] висящее {}: {} {} — {}",
                    finding.at(),
                    finding.kind(),
                    finding.blockId(),
                    finding.reason());
        }
        Map<FloatingBlocks.Kind, Integer> kinds = new EnumMap<>(FloatingBlocks.Kind.class);
        found.forEach(finding -> kinds.merge(finding.kind(), 1, Integer::sum));
        source.sendSuccess(() -> Component.literal("Висящие объекты на участке: " + found.size() + " " + kinds), false);
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Участок чист: блоков без опоры вне плана нет"), false);
            return 0;
        }
        for (FloatingBlocks.Finding finding : found.stream().limit(SHOWN).toList()) {
            source.sendSuccess(
                    () -> Component.literal(
                            finding.at() + " " + finding.kind() + " " + finding.blockId() + " — " + finding.reason()),
                    false);
        }
        if (found.size() > SHOWN) {
            int rest = found.size() - SHOWN;
            source.sendSuccess(() -> Component.literal("…и ещё " + rest + ": полный список в логе сервера"), false);
        }
        LOGGER.info("[audit] висящих объектов: {} по видам {}", found.size(), kinds);
        return found.size();
    }
}
