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
                        && compact.contains("groups.quarantine(ref.id())"),
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
}
