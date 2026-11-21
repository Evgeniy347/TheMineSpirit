package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModesTests {
    private static final Modes MODES = TacticsConfig.loadDefault().modes();

    private static List<Object> summary(CombatMode mode, double zoneRadius) {
        ModeParams params = MODES.of(mode);
        return List.of(
                params.initiates(),
                params.engage(zoneRadius),
                params.pursuit(zoneRadius),
                params.anchor(),
                params.leash(),
                params.returnToAnchor());
    }

    @Test
    void modes_ShippedFile_DescribesEveryCombatMode() {
        assertEquals(
                CombatMode.values().length,
                MODES.params().size(),
                "Все режимы боя описаны в modes.json: режим без параметров не знал бы своих радиусов"
                        + " Иначе тест modes_ShippedFile_DescribesEveryCombatMode перестанет ловить эту поломку. При смене modes_ShippedFile_DescribesEveryCombatMode проверь это утверждение.");
    }

    @Test
    void passive_Params_NeverInitiatesAndHasNoRadii() {
        assertEquals(
                List.of(false, 0.0, 0.0, AnchorKind.SELF, false, false),
                summary(CombatMode.PASSIVE, 0),
                "Пассивный режим не инициирует бой и не имеет радиусов: он только уходит от врагов"
                        + " Иначе тест passive_Params_NeverInitiatesAndHasNoRadii перестанет ловить эту поломку. При смене passive_Params_NeverInitiatesAndHasNoRadii проверь это утверждение.");
    }

    @Test
    void defensive_Params_ReactsWithinSixAndPursuesToEight() {
        assertEquals(
                List.of(false, 6.0, 8.0, AnchorKind.SELF, true, false),
                summary(CombatMode.DEFENSIVE, 0),
                "Оборонительный режим: реакция 6, преследование 8 от точки начала боя, нападает только в ответ"
                        + " Иначе тест defensive_Params_ReactsWithinSixAndPursuesToEight перестанет ловить эту поломку. При смене defensive_Params_ReactsWithinSixAndPursuesToEight проверь это утверждение.");
    }

    @Test
    void protect_Params_AnchorsToWard() {
        assertEquals(
                List.of(true, 16.0, 12.0, AnchorKind.PROTECTED, true, true),
                summary(CombatMode.PROTECT, 0),
                "Защита: реакция 16 вокруг подопечного, преследование 12 от него, возврат к подопечному"
                        + " Иначе тест protect_Params_AnchorsToWard перестанет ловить эту поломку. При смене protect_Params_AnchorsToWard проверь это утверждение.");
    }

    @Test
    void guardArea_Params_RadiiGrowWithZone() {
        assertEquals(
                List.of(true, 10.0, 14.0, AnchorKind.ZONE, true, true),
                summary(CombatMode.GUARD_AREA, 10),
                "Охрана зоны радиусом 10: реакция на всю зону, преследование на 4 блока дальше, возврат к центру"
                        + " Иначе тест guardArea_Params_RadiiGrowWithZone перестанет ловить эту поломку. При смене guardArea_Params_RadiiGrowWithZone проверь это утверждение.");
        assertEquals(
                List.of(true, 20.0, 24.0, AnchorKind.ZONE, true, true),
                summary(CombatMode.GUARD_AREA, 20),
                "Радиусы охраны следуют за радиусом зоны из приказа, а не за числом в коде"
                        + " Иначе тест guardArea_Params_RadiiGrowWithZone перестанет ловить эту поломку. При смене guardArea_Params_RadiiGrowWithZone проверь это утверждение.");
    }

    @Test
    void aggressive_Params_ReactsWithinTwentyFour() {
        assertEquals(
                List.of(true, 24.0, 32.0, AnchorKind.SELF, true, false),
                summary(CombatMode.AGGRESSIVE, 0),
                "Агрессивный режим: реакция 24, преследование 32"
                        + " Иначе тест aggressive_Params_ReactsWithinTwentyFour перестанет ловить эту поломку. При смене aggressive_Params_ReactsWithinTwentyFour проверь это утверждение.");
    }
}
