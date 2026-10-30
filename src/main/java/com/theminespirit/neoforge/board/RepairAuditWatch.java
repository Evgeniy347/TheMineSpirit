package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class RepairAuditWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<BlockCoord, Long> FIRST_SEEN = new HashMap<>();

    private RepairAuditWatch() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        RepairData rules = RepairData.loadDefault();
        long step = FIRST_SEEN.isEmpty() ? rules.auditIntervalTicks() : Math.max(1, rules.warnRepeatTicks());
        if (now <= 0 || now % step != 0) {
            return;
        }
        if (!service.board().ledger().has(BoardCase.BUILD_HOUSE, 1)) {
            return;
        }
        Optional<String> scheme = DataTables.INSTANCE.stages().current().scheme(BoardCase.BUILD_HOUSE, 1);
        if (scheme.isEmpty()) {
            return;
        }
        RepairAudit.Report report = RepairAudit.audit(
                SchematicResources.load(scheme.orElseThrow()),
                site.houseOrigin(),
                BuildRotation.NONE,
                new LevelWorld(level),
                rules,
                FIRST_SEEN,
                now);
        if (report.damaged()) {
            repairWarnings(level, report, now);
        }
        if (report.rebuild()) {
            FIRST_SEEN.clear();
            LOGGER.info(
                    "[repair] обход дома: повреждено больше {}% — нужна перестройка уровня", rules.rebuildPercent());
        }
    }

    private static void repairWarnings(ServerLevel level, RepairAudit.Report report, long now) {
        List<RepairAudit.Fix> warns = report.fixes().stream()
                .filter(fix -> fix.action() == RepairAudit.Action.WARN || fix.action() == RepairAudit.Action.REMOVE)
                .toList();
        FIRST_SEEN.keySet().retainAll(warns.stream().map(RepairAudit.Fix::cell).toList());
        for (RepairAudit.Fix fix : warns) {
            FIRST_SEEN.putIfAbsent(fix.cell(), now);
        }
        List<RepairAudit.Fix> player = warns.stream()
                .filter(fix -> fix.action() == RepairAudit.Action.WARN)
                .toList();
        if (player.isEmpty()) {
            return;
        }
        RepairAudit.Fix first = player.get(0);
        LOGGER.info(
                "[repair] обход дома: {} клеток игрока вернуть к схеме, первая {} — до {}",
                player.size(),
                first.cell(),
                first.removeAt());
        level.getServer()
                .getPlayerList()
                .broadcastSystemMessage(
                        Component.literal("Духи просят убрать постройки игрока у дома: клеток " + player.size()
                                + ", затем вернут схему"),
                        false);
    }

    private record LevelWorld(ServerLevel level) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .getBlock())
                    .toString();
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return PlacedBlocksData.get(level).isMarked(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
    }
}
