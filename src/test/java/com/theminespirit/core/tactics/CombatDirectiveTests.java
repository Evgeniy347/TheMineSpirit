package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.TargetSelector.Target;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CombatDirectiveTests {
    private static final Point SAFE_POINT = new Point(10, 64, 10);
    private static final Target HOSTILE = TargetSelector.parse("hostile");
    private static final Target PLAYER = TargetSelector.parse("player:Steve");

    @Test
    void attack_TargetPresent_GivesAggressiveModeWithTarget() {
        CombatDirective directive = CombatDirective.attack(HOSTILE);

        assertEquals(CombatMode.AGGRESSIVE, directive.mode(),
                "attack обязан включать режим AGGRESSIVE: иначе тактика не возьмёт приказ боевого скрипта");
        assertEquals(Optional.of(HOSTILE), directive.target(),
                "attack обязан нести цель: без цели дух не знает, кого бить");
        assertTrue(directive.retreat().isEmpty(),
                "attack не обязан навязывать способ отхода: об отходе решает тактика, а не атака");
        assertTrue(directive.safePoint().isEmpty(),
                "attack не несёт точки отхода: атакующий никуда не отступает по приказу");
    }

    @Test
    void defend_TargetPresent_GivesProtectModeWithTarget() {
        CombatDirective directive = CombatDirective.defend(PLAYER);

        assertEquals(CombatMode.PROTECT, directive.mode(),
                "defend обязан включать режим PROTECT: иначе тактика не встанет на защиту подопечного");
        assertEquals(Optional.of(PLAYER), directive.target(),
                "defend обязан нести подопечного: без цели дух защищать не кого");
        assertTrue(directive.retreat().isEmpty(),
                "defend не обязан навязывать способ отхода: отступление решает тактика");
        assertTrue(directive.safePoint().isEmpty(),
                "defend не несёт точки отхода: защитник держится, а не бежит");
    }

    @Test
    void flee_Factory_GivesPassiveRetreatWithoutPoint() {
        CombatDirective directive = CombatDirective.flee();

        assertEquals(CombatMode.PASSIVE, directive.mode(),
                "flee обязан включать PASSIVE: бегущий дух не воюет, а спасается");
        assertEquals(Optional.of(RetreatMethod.NONE), directive.retreat(),
                "flee обязан навязывать способ NONE: у бегства нет назначенной точки, "
                        + "и только явный NONE отличает его от организованного отхода");
        assertTrue(directive.target().isEmpty(),
                "flee не несёт цель: убегающий выбирает цель сам по ситуации, а не по приказу");
        assertTrue(directive.safePoint().isEmpty(),
                "flee обязан идти без точки: бегство выбирает свободный путь, а не маршрут к дому");
    }

    @Test
    void retreat_PointPresent_GivesHomeMethodWithPoint() {
        CombatDirective directive = CombatDirective.retreat(SAFE_POINT);

        assertEquals(CombatMode.PASSIVE, directive.mode(),
                "retreat обязан включать PASSIVE: отходящий дух не воюет");
        assertEquals(Optional.of(RetreatMethod.HOME), directive.retreat(),
                "retreat обязан идти способом HOME: организованный отход ведёт к безопасной точке, "
                        + "а не прочь от врага вслепую");
        assertEquals(Optional.of(SAFE_POINT), directive.safePoint(),
                "retreat обязан нести точку: без неё отход не знает, куда идти");
        assertTrue(directive.target().isEmpty(),
                "retreat не несёт цель: отходящий бежит от угрозы, а не к ней");
    }

    @Test
    void directive_CombatModeWithoutTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CombatDirective(CombatMode.AGGRESSIVE, Optional.empty(), Optional.empty(),
                        Optional.empty()),
                "Боевой режим без цели обязан отказывать: дух встал бы и ждал неизвестно чего, "
                        + "провисев весь тайм-аут задачи");

        assertTrue(error.getMessage().contains("AGGRESSIVE"),
                "Отказ обязан называть режим без цели. Фактическое: " + error.getMessage());
    }

    @Test
    void directive_ProtectModeWithoutTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CombatDirective(CombatMode.PROTECT, Optional.empty(), Optional.empty(), Optional.empty()),
                "PROTECT без подопечного обязан отказывать: защищать не кого, "
                        + "и дух молча простоял бы весь тайм-аут");

        assertTrue(error.getMessage().contains("PROTECT"),
                "Отказ обязан называть режим. Фактическое: " + error.getMessage());
    }

    @Test
    void directive_TargetOnPassiveMode_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CombatDirective(CombatMode.PASSIVE, Optional.of(HOSTILE), Optional.empty(),
                        Optional.empty()),
                "Цель в пассивном режиме обязана отказывать: приказ нацелил бы духа бить в обход режима, "
                        + "и тактика получила бы противоречие");

        assertTrue(error.getMessage().contains("PASSIVE"),
                "Отказ обязан называть режим с целью. Фактическое: " + error.getMessage());
    }

    @Test
    void directive_SafePointWithoutRetreatMethod_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CombatDirective(CombatMode.PASSIVE, Optional.empty(), Optional.empty(),
                        Optional.of(SAFE_POINT)),
                "Точка без способа отхода обязана отказывать: дух не знал бы, зачем ему точка, "
                        + "и отход мог бы пойти прочь от неё");

        assertTrue(error.getMessage().contains("точк"),
                "Отказ обязан называть проблему с точкой. Фактическое: " + error.getMessage());
    }

    @Test
    void directive_SafePointWithNoneRetreatMethod_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CombatDirective(CombatMode.PASSIVE, Optional.empty(),
                        Optional.of(RetreatMethod.NONE), Optional.of(SAFE_POINT)),
                "Точка при способе NONE обязана отказывать: NONE означает «способ не навязан», "
                        + "и точка при нём ввела бы отход в заблуждение");

        assertTrue(error.getMessage().contains("без способа"),
                "Отказ обязан говорить, что способ отхода не задан. Фактическое: " + error.getMessage());
    }

    @Test
    void directive_NullMode_IsRejected() {
        assertThrows(NullPointerException.class,
                () -> new CombatDirective(null, Optional.empty(), Optional.empty(), Optional.empty()),
                "Null-режим обязан отказывать: режим — обязательная часть приказа, "
                        + "и null скрыл бы ошибку конструктора");
    }

    @Test
    void attack_NullTarget_IsRejected() {
        assertThrows(NullPointerException.class, () -> CombatDirective.attack(null),
                "attack(null) обязана отказывать: атака без цели — ошибка вызывающего кода, "
                        + "и она обязана всплыть сразу, а не в тике боя");
    }
}
