package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditTicketGuardTests {
    @Test
    void auditTicket_Posting_UsesGroupSnapshotWithoutRescan() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/AuditPost.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains(".withGroup(ref)")
                        && compact.contains("newScriptCall(\"audit_group\"")
                        && compact.contains("registry.snapshot(ref)")
                        && compact.contains("registry.issue(ticket.id(),snapshot.key())"),
                "Билет аудита обязан нести GroupRef, снимок и явный скрипт: иначе дух снова начнёт строить"
                        + " план из мира. При смене выдачи перепроверь AuditPost.post и TicketTarget.withGroup.");
        assertFalse(
                compact.contains("StageBuilds.of(")
                        || compact.contains("TicketPlans.plan(")
                        || compact.contains("RepairAudit.audit(")
                        || compact.contains("BuildGap.trace(")
                        || compact.contains("SettlementScan."),
                "Путь выдачи аудита не пересчитывает мир: иначе инвариант И-2 нарушен, и холодный граф снова"
                        + " зависает на полном скане. При смене выдачи перепроверь AuditPost и запрещённые вызовы.");
    }

    @Test
    void boardTargets_FillBuilding_PreservesPlanAndGroup() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardService.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("TicketTargets.fillBuilding(known,target)"),
                "Повторная постановка с подписью постройки идёт через безопасное слияние: иначе явный план"
                        + " и GroupRef теряются, а тактика возвращается к пересчёту. При смене ветки DUPLICATE"
                        + " перепроверь BoardService.submit и TicketTargets.fillBuilding.");
    }

    @Test
    void auditExecution_SingleFactCheck_HasNoWorldRescan() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/AuditGroupScript.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("MAX_ITEMS=64")
                        && compact.contains(".limit(MAX_ITEMS)")
                        && compact.contains("AuditFactCheck.of(item,actual)")
                        && compact.contains("AuditMisses.of(")
                        && compact.contains("TownAuditWatch.quarantine("),
                "Тактика аудита берёт до 64 предметов, делает одну проверку факта и сразу пишет инцидент:"
                        + " иначе повторный diff скроет промах, а огромный билет займёт весь тик. При смене"
                        + " тактики перепроверь AuditGroupScript.tick.");
        assertFalse(
                compact.contains("StageBuilds.of(")
                        || compact.contains("TicketPlans.plan(")
                        || compact.contains("RepairAudit.audit(")
                        || compact.contains("BuildGap.trace(")
                        || compact.contains("SettlementScan.")
                        || compact.contains("SchematicClearance"),
                "Тактика исполняет готовый DiffItem без пересчёта схемы: иначе инвариант И-2 нарушен, и"
                        + " дух снова читает мир целиком. При смене тактики перепроверь запрещённые вызовы.");
    }

    @Test
    void auditVerifier_Center_UsesChunkCoordinates() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/TownAuditWatch.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("anchor().x()>>4") && compact.contains("anchor().z()>>4"),
                "Центр спирали аудита обязан быть в чанках: передача блочных координат создаёт радиус"
                        + " около 10 000 и сотни миллионов элементов, иначе сервер падает OOM. При смене"
                        + " вызова перепроверь TownAuditWatch.tick и AuditVerifier.reset.");
    }

    @Test
    void stageAudit_GroupSummary_HasNoWorldRescan() throws IOException {
        String source = Files.readString(
                        Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BuildingAudit.java"))
                + Files.readString(
                        Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BuildingAuditInputs.java"))
                + Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/WorldAreaInputs.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("GroupAudits.audit(")
                        && compact.contains("GroupAudits.report(")
                        && !compact.contains("FirstStepsRunner.gap(")
                        && !compact.contains("BuildGap.trace(")
                        && !compact.contains("RepairAudit.audit(")
                        && !compact.contains("SettlementScan.scan"),
                "Сводка этапа обязана читать группы аудита: иначе /v1/world снова запускает полный пересчёт"
                        + " мира и четыре источника истины возвращаются в граф. При смене сводки перепроверь"
                        + " BuildingAudit, BuildingAuditInputs и WorldAreaInputs.");
    }

    @Test
    void buildSchematic_PlanGroup_PrecedesLegacyFallback() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/BuildingScriptPack.java"));
        String compact = source.replaceAll("\\s+", "");
        int group = compact.indexOf("AuditGroupLookup.find(");
        int fallback = compact.indexOf("newBuildSchematicScript(");

        assertTrue(
                group >= 0
                        && fallback > group
                        && compact.contains("ticket.isBlank()")
                        && !compact.contains("group!=null&&AuditGroupLookup.hasTicket(spirit)"),
                "Схема из плана сначала ищется в группе аудита: иначе старый build_schematic снова начнёт"
                        + " пересчитывать мир. При смене маршрута перепроверь BuildingScriptPack.buildSchematic.");
    }
}
