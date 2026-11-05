package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PortalPolicyTests {
    private static final String S = Pictures.SOLID_LAYER;
    private static final String O = Pictures.OPEN_LAYER;

    private static Arguments row(String name, String floor, String feet, String head, PolicyVerdict expected) {
        return Arguments.of(name, floor, feet, head, expected);
    }

    private static PolicyVerdict stepOut(int dx, int dy, int dz) {
        return new PolicyVerdict.Reflex(
                PolicyId.PORTAL,
                new ReflexAction.StepOutOfPortal(Pictures.at(dx, dy, dz)),
                "дух стоит в клетке портала");
    }

    private static PolicyVerdict stuck() {
        return new PolicyVerdict.Reflex(
                PolicyId.PORTAL, new ReflexAction.StopMoving(), "дух стоит в клетке портала, выхода рядом нет");
    }

    static Stream<Arguments> pictures() {
        return Stream.of(
                row("дух вне портала", S, O, O, new PolicyVerdict.Clear()),
                row("портал рядом, дух вне его", S, ".../..P/...", ".../..P/...", new PolicyVerdict.Clear()),
                row("дух в портале высотой в две клетки", S, ".../.P./...", ".../.P./...", stepOut(-1, 0, 0)),
                row("портал только в клетке головы", S, O, ".../.P./...", stepOut(-1, 0, 0)),
                row("портал только в клетке ног", S, ".../.P./...", O, stepOut(-1, 0, 0)),
                row(
                        "слева и сзади портал, справа стена: выход на юг",
                        S,
                        "P#P/PP#/P.#",
                        "P#P/PP#/P.#",
                        stepOut(0, 0, 1)),
                row("плита портала: выход с шагом вверх на соседний блок", S, "###/#P#/###", O, stepOut(-1, 1, 0)),
                row("со всех сторон портал: выхода нет", S, "PPP/PPP/PPP", "PPP/PPP/PPP", stuck()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pictures")
    void evaluate_Picture_GivesExpectedVerdict(
            String name, String floor, String feet, String head, PolicyVerdict expected) {
        LocalSurroundings surroundings = Pictures.of(floor, feet, head, BodyFlags.CALM);

        PolicyVerdict verdict = PortalPolicy.evaluate(surroundings, PolicyExemptions.empty());

        assertEquals(
                expected,
                verdict,
                "Картинка «" + name
                        + "»: дух должен выйти из портала в ближайшую клетку без портала либо остаться на месте, если выхода нет");
    }

    @Test
    void evaluate_InPortalWithPortalExemption_IsClear() {
        LocalSurroundings surroundings = Pictures.of(S, ".../.P./...", ".../.P./...", BodyFlags.CALM);

        PolicyVerdict verdict = PortalPolicy.evaluate(surroundings, PolicyExemptions.of(PolicyId.PORTAL));

        assertEquals(
                new PolicyVerdict.Clear(),
                verdict,
                "Исключение PORTAL (следование за игроком в портал) снимает рефлекс");
    }

    @Test
    void vetoesTarget_TargetCellHoldsPortalInFeetOrHead_IsTrue() {
        LocalSurroundings surroundings = Pictures.of(S, ".../..P/...", ".../.../P..", BodyFlags.CALM);

        assertTrue(
                PortalPolicy.vetoesTarget(surroundings, Pictures.at(1, 0, 0), PolicyExemptions.empty()),
                "Цель с порталом в клетке ног запрещена: шаг туда переносит духа в другое измерение");
        assertTrue(
                PortalPolicy.vetoesTarget(surroundings, Pictures.at(-1, 0, 1), PolicyExemptions.empty()),
                "Цель с порталом в клетке головы запрещена так же, как с порталом в клетке ног");
    }

    @Test
    void vetoesTarget_TargetWithoutPortal_IsFalse() {
        LocalSurroundings surroundings = Pictures.of(S, ".../..P/...", O, BodyFlags.CALM);

        assertFalse(
                PortalPolicy.vetoesTarget(surroundings, Pictures.at(-1, 0, 0), PolicyExemptions.empty()),
                "Соседняя клетка без портала не запрещена: портал рядом сам по себе не повод стоять на месте");
    }

    @Test
    void vetoesTarget_PortalExemption_IsFalse() {
        LocalSurroundings surroundings = Pictures.of(S, ".../..P/...", O, BodyFlags.CALM);

        assertFalse(
                PortalPolicy.vetoesTarget(surroundings, Pictures.at(1, 0, 0), PolicyExemptions.of(PolicyId.PORTAL)),
                "С исключением PORTAL цель в портале разрешена: так скрипт следует за игроком");
    }

    @Test
    void vetoesTarget_TargetOutsideSnapshot_IsFalse() {
        LocalSurroundings surroundings = Pictures.of(S, "PPP/PPP/PPP", "PPP/PPP/PPP", BodyFlags.CALM);

        assertFalse(
                PortalPolicy.vetoesTarget(
                        surroundings,
                        new BlockCoord(Pictures.FEET.x() + 5, Pictures.FEET.y(), Pictures.FEET.z()),
                        PolicyExemptions.empty()),
                "За пределами снимка портал не виден: вето опирается только на увиденное, дальше проверит следующий тик");
    }
}
