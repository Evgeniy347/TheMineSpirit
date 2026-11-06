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
                "Все режимы боя описаны в modes.json: режим без параметров не знал бы своих радиусов");
    }

    @Test
    void passive_Params_NeverInitiatesAndHasNoRadii() {
        assertEquals(
                List.of(false, 0.0, 0.0, AnchorKind.SELF, false, false),
                summary(CombatMode.PASSIVE, 0),
                "Пассивный режим не инициирует бой и не имеет радиусов: он только уходит от врагов");
    }

    @Test
    void defensive_Params_ReactsWithinSixAndPursuesToEight() {
        assertEquals(
                List.of(false, 6.0, 8.0, AnchorKind.SELF, true, false),
                summary(CombatMode.DEFENSIVE, 0),
                "Оборонительный режим: реакция 6, преследование 8 от точки начала боя, нападает только в ответ");
    }

    @Test
    void protect_Params_AnchorsToWard() {
        assertEquals(
                List.of(true, 16.0, 12.0, AnchorKind.PROTECTED, true, true),
                summary(CombatMode.PROTECT, 0),
                "Защита: реакция 16 вокруг подопечного, преследование 12 от него, возврат к подопечному");
    }

    @Test
    void guardArea_Params_RadiiGrowWithZone() {
        assertEquals(
                List.of(true, 10.0, 14.0, AnchorKind.ZONE, true, true),
                summary(CombatMode.GUARD_AREA, 10),
                "Охрана зоны радиусом 10: реакция на всю зону, преследование на 4 блока дальше, возврат к центру");
        assertEquals(
                List.of(true, 20.0, 24.0, AnchorKind.ZONE, true, true),
                summary(CombatMode.GUARD_AREA, 20),
                "Радиусы охраны следуют за радиусом зоны из приказа, а не за числом в коде");
    }

    @Test
    void aggressive_Params_ReactsWithinTwentyFour() {
        assertEquals(
                List.of(true, 24.0, 32.0, AnchorKind.SELF, true, false),
                summary(CombatMode.AGGRESSIVE, 0),
                "Агрессивный режим: реакция 24, преследование 32");
    }
}
