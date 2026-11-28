package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class BuildingAudit {
    private BuildingAudit() {}

    static BuildingAuditInputs.Audit first(ServerLevel level, FirstStepsRunner.StageScan scan, BoardCase step) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        if (registry == null) {
            return new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1);
        }
        return switch (step) {
            case BUILD_MINE -> GroupAudits.audit(registry, Group.Kind.MINE, "");
            case BUILD_FENCE -> GroupAudits.audit(registry, Group.Kind.WALL, "");
            case BUILD_ORCHARD -> GroupAudits.audit(registry, Group.Kind.BUILDING, "orchard");
            case STOCK_CHESTS -> GroupAudits.audit(registry, Group.Kind.BUILDING, "yard");
            case CRAFT_FURNACE, LIGHTING -> new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 0);
            default -> throw new IllegalStateException("Аудит этапа не использует группы для кейса " + step);
        };
    }

    static BuildingAuditInputs.Audit floor(ServerLevel level, FirstStepsRunner.StageScan scan) {
        if (!YardFloorWatch.loaded(level, scan.stage())) {
            return new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1);
        }
        TownSnapshot.Progress progress = YardFloorWatch.progress(level, scan.stage());
        int holes = progress.total() - progress.done();
        return new BuildingAuditInputs.Audit(
                progress.total(),
                progress.done(),
                holes == 0 ? Map.of() : Map.of("minecraft:cobblestone", holes),
                Map.of(),
                0,
                0);
    }

    static BuildingAuditInputs.Audit streets(ServerLevel level, StageSite site, String fallback) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        return registry == null
                ? new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1)
                : GroupAudits.audit(registry, Group.Kind.STREET, "");
    }

    static BuildingAuditInputs.Audit clearing(ServerLevel level, StageSite site, long now) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        return registry == null
                ? new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1)
                : GroupAudits.audit(registry, Group.Kind.GROUND, "");
    }

    static BuildingAuditInputs.Audit stageOrchard(ServerLevel level, GraphFacts.Position at, String wood, long now) {
        Optional<String> scheme = DataTables.INSTANCE.stages().current().scheme(BoardCase.BUILD_ORCHARD, 1);
        return scheme.isEmpty()
                ? new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 0)
                : scheme(level, scheme.orElseThrow(), at, wood, now);
    }

    static BuildingAuditInputs.Audit scheme(
            ServerLevel level, String scheme, GraphFacts.Position at, String wood, long now) {
        List<Group> groups = AuditGroupLookup.findAll(level, scheme, at.x(), at.z());
        return groups.isEmpty()
                ? new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1)
                : GroupAudits.audit(groups);
    }

    static RepairAudit.@Nullable Report schemeReport(
            ServerLevel level, String scheme, GraphFacts.Position at, String wood, long now) {
        List<Group> groups = AuditGroupLookup.findAll(level, scheme, at.x(), at.z());
        return groups.isEmpty() ? null : GroupAudits.report(groups);
    }

    static BuildingAuditInputs.Audit report(RepairAudit.Report report) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        Map<String, Integer> extra = new LinkedHashMap<>();
        int wrong = 0;
        if (!report.rebuild()) {
            for (RepairAudit.Fix fix : report.fixes()) {
                if (fix.action() == RepairAudit.Action.PLACE) {
                    missing.merge(id(fix.wanted()), 1, Integer::sum);
                    if (!air(fix.found())) {
                        wrong++;
                    }
                } else {
                    extra.merge(id(fix.found()), 1, Integer::sum);
                }
            }
        }
        return new BuildingAuditInputs.Audit(
                report.total(), report.total() - report.missing(), missing, extra, wrong, report.unknown());
    }

    private static boolean air(String block) {
        return "air".equals(SchematicBlocks.path(block));
    }

    private static String id(String block) {
        return block.contains(":") ? block : "minecraft:" + block;
    }
}
