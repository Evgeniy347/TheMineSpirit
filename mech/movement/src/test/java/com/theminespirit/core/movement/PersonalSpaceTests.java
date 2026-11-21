package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.PersonalSpace;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersonalSpaceTests {
    private static final PersonalSpace RULES = new PersonalSpace(1.5, 2.5, 2, 5);
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);
    private static final PersonalSpace.Position SPIRIT = new PersonalSpace.Position(10.5, 64, 10.5);

    @Test
    void table_OwnerDecision_NotCloserThanOneBlockStepOneOrTwo() {
        PersonalSpace data = PersonalSpace.load();

        assertEquals(
                RULES,
                data,
                "Личное пространство (владелец 19.10): ближе 1,5 между центрами (вплотную) — отход на 1–2 клетки, "
                        + "чтобы до игрока стало не меньше 2,5; менять — в movement/personal_space.json"
                        + " Иначе тест table_OwnerDecision_NotCloserThanOneBlockStepOneOrTwo перестанет ловить эту поломку. При смене table_OwnerDecision_NotCloserThanOneBlockStepOneOrTwo проверь это утверждение.");
    }

    @Test
    void crowded_PlayerInNeighbourCell_IsCrowded() {
        assertTrue(
                RULES.crowded(SPIRIT, List.of(new PersonalSpace.Position(11.5, 64, 10.5))),
                "Игрок в соседней клетке (1 блок между центрами) — вплотную: дух обязан отойти"
                        + " Иначе тест crowded_PlayerInNeighbourCell_IsCrowded перестанет ловить эту поломку. При смене crowded_PlayerInNeighbourCell_IsCrowded проверь это утверждение.");
    }

    @Test
    void crowded_PlayerTwoBlocksAway_IsNotCrowded() {
        assertFalse(
                RULES.crowded(SPIRIT, List.of(new PersonalSpace.Position(12.5, 64, 10.5))),
                "Игрок в двух блоках — не вплотную: дух не дёргается и работает дальше"
                        + " Иначе тест crowded_PlayerTwoBlocksAway_IsNotCrowded перестанет ловить эту поломку. При смене crowded_PlayerTwoBlocksAway_IsNotCrowded проверь это утверждение.");
    }

    @Test
    void retreat_PlayerOnEast_StepsWestAwayFromPlayer() {
        PersonalSpace.Position player = new PersonalSpace.Position(11.5, 64, 10.5);

        Optional<BlockCoord> step = RULES.retreat(FEET, List.of(player), cell -> cell.y() == 64);

        assertTrue(
                step.isPresent(),
                "На ровном полу клетка для отхода обязана найтись"
                        + " Иначе тест retreat_PlayerOnEast_StepsWestAwayFromPlayer перестанет ловить эту поломку. При смене retreat_PlayerOnEast_StepsWestAwayFromPlayer проверь это утверждение.");
        BlockCoord cell = step.get();
        double distance = Math.hypot(cell.x() + 0.5 - player.x(), cell.z() + 0.5 - player.z());
        assertTrue(
                distance >= 2.5 && Math.max(Math.abs(cell.x() - FEET.x()), Math.abs(cell.z() - FEET.z())) <= 2,
                "Отход — не дальше 2 клеток, и до игрока после него не меньше 2,5: иначе дух отходит в никуда "
                        + "или остаётся вплотную; выбрана " + cell + " в " + distance
                        + " При смене retreat_PlayerOnEast_StepsWestAwayFromPlayer проверь это утверждение.");
        assertTrue(
                cell.x() < FEET.x(),
                "Игрок с востока — дух отходит на запад, от игрока; выбрана " + cell
                        + " Иначе тест retreat_PlayerOnEast_StepsWestAwayFromPlayer перестанет ловить эту поломку. При смене retreat_PlayerOnEast_StepsWestAwayFromPlayer проверь это утверждение.");
    }

    @Test
    void retreat_OnlyOneFreeCell_ChoosesIt() {
        PersonalSpace.Position player = new PersonalSpace.Position(10.5, 64, 11.5);
        BlockCoord free = new BlockCoord(10, 64, 8);

        Optional<BlockCoord> step = RULES.retreat(FEET, List.of(player), free::equals);

        assertEquals(
                Optional.of(free),
                step,
                "Стоять можно только в одной клетке — дух обязан отойти туда"
                        + " Иначе тест retreat_OnlyOneFreeCell_ChoosesIt перестанет ловить эту поломку. При смене retreat_OnlyOneFreeCell_ChoosesIt проверь это утверждение.");
    }

    @Test
    void retreat_NoFreeCell_StaysPut() {
        Optional<BlockCoord> step =
                RULES.retreat(FEET, List.of(new PersonalSpace.Position(11.5, 64, 10.5)), cell -> false);

        assertEquals(
                Optional.empty(),
                step,
                "Без свободной клетки дух остаётся на месте: шаг в стену или в обрыв хуже соседства с игроком"
                        + " Иначе тест retreat_NoFreeCell_StaysPut перестанет ловить эту поломку. При смене retreat_NoFreeCell_StaysPut проверь это утверждение.");
    }

    @Test
    void parse_KeepCloserThanMin_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PersonalSpace.parse(
                        "{\"minDistance\":2,\"keepDistance\":1,\"maxStep\":2,\"checkIntervalTicks\":5}", "тест"),
                "Отход ближе порога обязан отвергаться: дух отходил бы и тут же снова оказывался вплотную"
                        + " Иначе тест parse_KeepCloserThanMin_IsRejected перестанет ловить эту поломку. При смене parse_KeepCloserThanMin_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("keepDistance"),
                "Ошибка называет поле: " + error.getMessage()
                        + " Иначе тест parse_KeepCloserThanMin_IsRejected перестанет ловить эту поломку. При смене parse_KeepCloserThanMin_IsRejected проверь это утверждение.");
    }
}
