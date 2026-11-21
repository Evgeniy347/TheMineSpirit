package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageWorldGatePart7Tests {
    private static void built(ModelWorld world, StageRules.Stage stage) {
        stage.buildings().forEach(world::add);
        stage.requires().forEach(world::addPlan);
    }

    private static void notePlan(BoardLedger ledger, StageRules rules, String id, int level) {
        BoardCase boardCase = rules.planCases().get(id);
        if (boardCase == null) {
            ledger.notePlan(id, level);
        } else {
            ledger.note(boardCase, level);
        }
    }

    private static final class ModelWorld implements StageWorld {
        private final Set<String> built = new HashSet<>();

        private void add(BoardCase boardCase, int level) {
            built.add(boardCase.name() + ":" + level);
        }

        private void addPlan(String id, int level) {
            built.add(id + ":" + level);
        }

        private void removePlan(String id, int level) {
            built.remove(id + ":" + level);
        }

        @Override
        public boolean built(BoardCase boardCase, int level) {
            return built.contains(boardCase.name() + ":" + level);
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return built.contains(id + ":" + level);
        }
    }

    @Test
    void stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld() {
        StageRules rules = StageRules.load();
        ModelWorld world = new ModelWorld();
        StageConfirmation confirmation = new StageConfirmation(1, 1);
        for (int number = 1; number <= rules.stages().size(); number++) {
            assertEquals(
                    number,
                    SettlementStage.of(world),
                    "Этап " + number
                            + " открыт только после аудита всех предыдущих этапов Иначе тест stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld перестанет ловить эту поломку. При смене stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld проверь это утверждение.");
            assertEquals(
                    number,
                    confirmation.gate(world).stage(),
                    "Гейт стоит на подтверждённом аудите этапа  Иначе тест stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld перестанет ловить эту поломку. При смене stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld проверь это утверждение."
                            + number);
            built(world, rules.stages().get(number - 1));
            assertEquals(
                    number + 1,
                    SettlementStage.of(world),
                    "Обязательные постройки этапа " + number + " из аудита мира закрывают его целиком; "
                            + "желательные (optional) переход не держат Иначе тест stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld перестанет ловить эту поломку. При смене stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld проверь это утверждение.");
        }
        assertEquals(
                5,
                SettlementStage.of(world),
                "После четвёртого этапа все этапы закрыты Иначе тест stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld перестанет ловить эту поломку. При смене stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld проверь это утверждение.");
    }

    @Test
    void journal_AllMarksWithoutWorld_StaysFirstStage() {
        StageRules rules = StageRules.load();
        BoardLedger journal = new BoardLedger();
        rules.stages().forEach(stage -> {
            stage.buildings().forEach((boardCase, level) -> journal.note(boardCase, level));
            stage.requires().forEach((id, level) -> notePlan(journal, rules, id, level));
            stage.optional().forEach((id, level) -> notePlan(journal, rules, id, level));
        });
        assertEquals(
                5,
                SettlementStage.of(journal),
                "Журнал «построено» считает все этапы закрытыми; остаток  Иначе тест journal_AllMarksWithoutWorld_StaysFirstStage перестанет ловить эту поломку. При смене journal_AllMarksWithoutWorld_StaysFirstStage проверь это утверждение."
                        + SettlementStage.missing(journal));
        assertEquals(
                1,
                new StageConfirmation(1, 1).gate(new ModelWorld()).stage(),
                "Пустой мир при полном журнале оставляет первый этап: отметки журнала этап не закрывают Иначе тест journal_AllMarksWithoutWorld_StaysFirstStage перестанет ловить эту поломку. При смене journal_AllMarksWithoutWorld_StaysFirstStage проверь это утверждение.");
    }

    @Test
    void damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack() {
        StageRules rules = StageRules.load();
        ModelWorld world = new ModelWorld();
        rules.stages().forEach(stage -> built(world, stage));
        StageConfirmation confirmation = new StageConfirmation(5);
        world.removePlan("forge", 1);
        assertEquals(
                3,
                SettlementStage.of(world),
                "Повреждённая кузница не даёт закрыть третий этап Иначе тест damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack перестанет ловить эту поломку. При смене damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack проверь это утверждение.");
        assertEquals(
                5,
                confirmation.observe(3),
                "Один плохой аудит этап не откатывает Иначе тест damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack перестанет ловить эту поломку. При смене damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack проверь это утверждение.");
        assertEquals(
                5,
                confirmation.observe(3),
                "Второй плохой аудит этап ещё не откатывает Иначе тест damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack перестанет ловить эту поломку. При смене damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack проверь это утверждение.");
        assertEquals(
                3,
                confirmation.observe(3),
                "Третий плохой аудит подряд откатывает этап до мира Иначе тест damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack перестанет ловить эту поломку. При смене damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack проверь это утверждение.");
        assertEquals(
                3,
                confirmation.observe(3),
                "Подтверждённый откат держится: этап 3 не закрыт, пока кузница не восстановлена Иначе тест damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack перестанет ловить эту поломку. При смене damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack проверь это утверждение.");
    }
}
