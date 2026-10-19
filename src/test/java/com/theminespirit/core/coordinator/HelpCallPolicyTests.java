package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HelpCallPolicyTests {
    private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEAR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MIDDLE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FAR = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final double HELP_RADIUS = 32;

    private static SpiritSnapshot spirit(UUID id, double x, boolean attacked) {
        return new SpiritSnapshot(
                id,
                new Point(x, 64, 0),
                1,
                SpiritRole.IDLE,
                CombatMode.DEFENSIVE,
                attacked ? 1 : 0,
                attacked,
                Optional.empty(),
                100,
                1.0);
    }

    private static HelpCall call(UUID caller, double x) {
        return new HelpCall(caller, new Point(x, 64, 0), 100);
    }

    @Test
    void answer_FreeSpiritInsideRadius_GoesUrgently() {
        List<AssistOrder> orders = new HelpCallPolicy()
                .answer(
                        List.of(call(CALLER, 0)),
                        List.of(spirit(CALLER, 0, true), spirit(NEAR, 5, false), spirit(FAR, 40, false)),
                        HELP_RADIUS,
                        2);

        assertEquals(
                List.of(new AssistOrder(NEAR, CALLER, true)),
                orders,
                "На зов идёт ближайший свободный дух в радиусе R3; приказ помечен срочным, чтобы прервать работу "
                        + "и вернуться к ней после боя");
    }

    @Test
    void answer_AttackedSpirit_StaysInItsFight() {
        List<AssistOrder> orders = new HelpCallPolicy()
                .answer(
                        List.of(call(CALLER, 0)),
                        List.of(spirit(CALLER, 0, true), spirit(NEAR, 5, true), spirit(MIDDLE, 8, false)),
                        HELP_RADIUS,
                        2);

        assertEquals(
                List.of(new AssistOrder(MIDDLE, CALLER, true)),
                orders,
                "Дух, которого уже бьют, не бросает свой бой: он сам в бою и не может идти на помощь");
    }

    @Test
    void answer_CallingSpirit_NotChosenAsHelper() {
        List<AssistOrder> orders = new HelpCallPolicy()
                .answer(
                        List.of(call(CALLER, 0), call(NEAR, 4)),
                        List.of(spirit(CALLER, 0, true), spirit(NEAR, 4, false), spirit(MIDDLE, 8, false)),
                        HELP_RADIUS,
                        2);

        assertEquals(
                List.of(new AssistOrder(MIDDLE, CALLER, true)),
                orders,
                "Дух, который сам зовёт на помощь, не идёт помогать другому: он занят своим боем, "
                        + "иначе два зова уводят одного помощника с его собственной нужды");
    }

    @Test
    void answer_SpiritOutsideRadius_NotCalled() {
        assertTrue(
                new HelpCallPolicy()
                        .answer(
                                List.of(call(CALLER, 0)),
                                List.of(spirit(CALLER, 0, true), spirit(FAR, HELP_RADIUS + 1, false)),
                                HELP_RADIUS,
                                2)
                        .isEmpty(),
                "Дух за радиусом R3 не слышит зов: иначе помощь идёт через полкарты");
    }

    @Test
    void answer_TwoCalls_EachHelperGoesOnlyOnce() {
        List<AssistOrder> orders = new HelpCallPolicy()
                .answer(
                        List.of(call(CALLER, 0), call(MIDDLE, 10)),
                        List.of(
                                spirit(CALLER, 0, true),
                                spirit(MIDDLE, 10, true),
                                spirit(NEAR, 5, false),
                                spirit(FAR, 40, false)),
                        HELP_RADIUS,
                        2);

        assertEquals(
                List.of(new AssistOrder(NEAR, CALLER, true), new AssistOrder(FAR, MIDDLE, true)),
                orders,
                "Один дух отвечает на один зов: иначе два зова уводят одного помощника и оставляют второго без помощи");
    }

    @Test
    void answer_NoCalls_NoOrders() {
        assertTrue(
                new HelpCallPolicy()
                        .answer(List.of(), List.of(spirit(NEAR, 5, false)), HELP_RADIUS, 2)
                        .isEmpty(),
                "Без зова никто никуда не идёт: помощь не выдумывается координатором");
    }

    @Test
    void answer_MaxHelpers_Respected() {
        List<AssistOrder> orders = new HelpCallPolicy()
                .answer(
                        List.of(call(CALLER, 0)),
                        List.of(
                                spirit(CALLER, 0, true),
                                spirit(NEAR, 4, false),
                                spirit(MIDDLE, 6, false),
                                spirit(FAR, 8, false)),
                        HELP_RADIUS,
                        2);

        assertEquals(
                2,
                orders.size(),
                "На один зов откликаются не больше maxHelpers духов: иначе поселение остаётся без остальных дел");
    }
}
