package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.audit.BlockEquivalence;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.building.SchematicBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class GroupAudits {
    private GroupAudits() {}

    static BuildingAuditInputs.Audit audit(GroupRegistry registry, Group.Kind kind, String subjectPrefix) {
        Objects.requireNonNull(registry, "Реестр групп не может быть null: аудит собирается из него");
        BuildingAuditInputs.Audit total = new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 0);
        boolean found = false;
        for (Group group : registry.groups()) {
            if (group.kind() == kind && group.subject().startsWith(subjectPrefix)) {
                total = total.plus(audit(group));
                found = true;
            }
        }
        return found ? total : new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1);
    }

    static BuildingAuditInputs.Audit audit(List<Group> groups) {
        BuildingAuditInputs.Audit total = new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 0);
        for (Group group : groups) {
            total = total.plus(audit(group));
        }
        return groups.isEmpty() ? new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1) : total;
    }

    static BuildingAuditInputs.Audit audit(Group group) {
        int pending = 0;
        int wrong = 0;
        int holes = 0;
        int mounds = 0;
        Map<String, Integer> missing = new LinkedHashMap<>();
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (DiffItem item : group.items()) {
            pending++;
            if (item.action() == DiffItem.Action.BREAK) {
                extra.merge(item.found(), 1, Integer::sum);
                if ("stray".equals(group.subject()) || "floating".equals(group.subject())) {
                    mounds++;
                }
            } else {
                missing.merge(block(item.wanted()), 1, Integer::sum);
                if (BlockEquivalence.check(Wanted.air(), item.found()) != BlockEquivalence.Verdict.DONE) {
                    wrong++;
                }
                if ("pit".equals(group.subject())) {
                    holes++;
                }
            }
        }
        return new BuildingAuditInputs.Audit(
                group.total(),
                Math.max(0, group.total() - pending - group.unknown()),
                Map.copyOf(missing),
                Map.copyOf(extra),
                wrong,
                group.unknown(),
                new BuildingAuditInputs.Defects(holes, mounds, 0));
    }

    static RepairAudit.Report report(Group group) {
        return report(List.of(group));
    }

    static RepairAudit.Report report(List<Group> groups) {
        List<RepairAudit.Fix> fixes = new ArrayList<>();
        int total = 0;
        int missing = 0;
        int unknown = 0;
        for (Group group : groups) {
            total += group.total();
            unknown += group.unknown();
            for (DiffItem item : group.items()) {
                if (item.action() == DiffItem.Action.BREAK) {
                    fixes.add(
                            new RepairAudit.Fix(item.cell(), RepairAudit.Action.CLEAR, "minecraft:air", item.found()));
                } else {
                    missing++;
                    fixes.add(new RepairAudit.Fix(
                            item.cell(), RepairAudit.Action.PLACE, item.wanted().text(), item.found()));
                }
            }
        }
        return new RepairAudit.Report(total, missing, false, fixes, unknown);
    }

    private static String block(Wanted wanted) {
        String id = SchematicBlocks.placementBlock(wanted.id());
        String path = SchematicBlocks.path(id);
        return path.startsWith("minecraft:") ? path : "minecraft:" + path;
    }
}
