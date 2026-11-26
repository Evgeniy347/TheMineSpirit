package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MorePlansTests {
    private static final BlockCoord AT = new BlockCoord(10, 64, -3);

    @Test
    void returnHome_NoParams_IsSingleScript() {
        ScriptCall call = script(MorePlans.returnHome(), 0);
        assertEquals(
                "return_home",
                call.script(),
                "Возврат домой — отдельный скрипт, а не ходьба по координатам"
                        + " Иначе тест returnHome_NoParams_IsSingleScript перестанет ловить эту поломку. При смене returnHome_NoParams_IsSingleScript проверь это утверждение.");
        assertTrue(
                call.params().isEmpty(),
                "return_home не требует параметров: дом задан духу заранее"
                        + " Иначе тест returnHome_NoParams_IsSingleScript перестанет ловить эту поломку. При смене returnHome_NoParams_IsSingleScript проверь это утверждение.");
    }

    @Test
    void meet_TargetGiven_GoesAndMeets() {
        SequenceNode plan = sequence(MorePlans.meet(AT, "player:Steve"));
        assertEquals(
                "goto",
                script(plan.steps().get(0), 0).script(),
                "Сначала дух идёт к игроку"
                        + " Иначе тест meet_TargetGiven_GoesAndMeets перестанет ловить эту поломку. При смене meet_TargetGiven_GoesAndMeets проверь это утверждение.");
        assertEquals(
                Map.of("target", "player:Steve"),
                script(plan.steps().get(1), 0).params(),
                "meet получает имя игрока: по нему цель находится в мире"
                        + " Иначе тест meet_TargetGiven_GoesAndMeets перестанет ловить эту поломку. При смене meet_TargetGiven_GoesAndMeets проверь это утверждение.");
    }

    @Test
    void keepDistance_TargetGiven_KeepsConfiguredGap() {
        SequenceNode plan = sequence(MorePlans.keepDistance(AT, "player:Steve"));
        Map<String, Object> params = script(plan.steps().get(1), 0).params();
        assertEquals(
                "player:Steve",
                params.get("target"),
                "Дистанцию держат от конкретного игрока"
                        + " Иначе тест keepDistance_TargetGiven_KeepsConfiguredGap перестанет ловить эту поломку. При смене keepDistance_TargetGiven_KeepsConfiguredGap проверь это утверждение.");
        assertEquals(
                MorePlans.KEEP_DISTANCE,
                params.get("distance"),
                "Дистанция берётся из константы, а не с потолка"
                        + " Иначе тест keepDistance_TargetGiven_KeepsConfiguredGap перестанет ловить эту поломку. При смене keepDistance_TargetGiven_KeepsConfiguredGap проверь это утверждение.");
    }

    @Test
    void patrol_PointGiven_WalksOneLoop() {
        SequenceNode plan = sequence(MorePlans.patrol(AT));
        ScriptCall patrol = script(plan.steps().get(1), 0);
        assertEquals(
                "patrol",
                patrol.script(),
                "Охрана жителя — патруль у его точки"
                        + " Иначе тест patrol_PointGiven_WalksOneLoop перестанет ловить эту поломку. При смене patrol_PointGiven_WalksOneLoop проверь это утверждение.");
        assertEquals(
                List.of("10,64,-3"),
                patrol.params().get("points"),
                "Точка патруля задаётся строкой x,y,z"
                        + " Иначе тест patrol_PointGiven_WalksOneLoop перестанет ловить эту поломку. При смене patrol_PointGiven_WalksOneLoop проверь это утверждение.");
        assertEquals(
                1,
                patrol.params().get("loops"),
                "Один круг: охрана не зацикливается навсегда"
                        + " Иначе тест patrol_PointGiven_WalksOneLoop перестанет ловить эту поломку. При смене patrol_PointGiven_WalksOneLoop проверь это утверждение.");
    }

    @Test
    void safeMine_EntranceGiven_StartsWithGoto() {
        SequenceNode plan = sequence(MorePlans.safeMine(AT));
        assertEquals(
                "goto",
                script(plan.steps().get(0), 0).script(),
                "К шахте сначала идут ходьбой"
                        + " Иначе тест safeMine_EntranceGiven_StartsWithGoto перестанет ловить эту поломку. При смене safeMine_EntranceGiven_StartsWithGoto проверь это утверждение.");
        ScriptCall mine = script(plan.steps().get(1), 0);
        assertEquals(
                "safe_mine",
                mine.script(),
                "Истощённую шахту продолжают безопасной проходкой"
                        + " Иначе тест safeMine_EntranceGiven_StartsWithGoto перестанет ловить эту поломку. При смене safeMine_EntranceGiven_StartsWithGoto проверь это утверждение.");
        assertEquals(
                MorePlans.SAFE_MINE_MODE,
                mine.params().get("mode"),
                "Форма проходки задана данными"
                        + " Иначе тест safeMine_EntranceGiven_StartsWithGoto перестанет ловить эту поломку. При смене safeMine_EntranceGiven_StartsWithGoto проверь это утверждение.");
        assertEquals(
                MorePlans.SAFE_MINE_DIRECTION,
                mine.params().get("direction"),
                "Направление проходки задано данными"
                        + " Иначе тест safeMine_EntranceGiven_StartsWithGoto перестанет ловить эту поломку. При смене safeMine_EntranceGiven_StartsWithGoto проверь это утверждение.");
    }

    @Test
    void closeDoor_DoorGiven_TargetsDoorCell() {
        SequenceNode plan = sequence(MorePlans.closeDoor(AT));
        ScriptCall close = script(plan.steps().get(1), 0);
        assertEquals(
                "close_door",
                close.script(),
                "Открытую дверь закрывают своим скриптом"
                        + " Иначе тест closeDoor_DoorGiven_TargetsDoorCell перестанет ловить эту поломку. При смене closeDoor_DoorGiven_TargetsDoorCell проверь это утверждение.");
        assertEquals(
                10,
                close.params().get("x"),
                "Дверь задаётся координатой x"
                        + " Иначе тест closeDoor_DoorGiven_TargetsDoorCell перестанет ловить эту поломку. При смене closeDoor_DoorGiven_TargetsDoorCell проверь это утверждение.");
        assertEquals(
                64,
                close.params().get("y"),
                "Дверь задаётся координатой y"
                        + " Иначе тест closeDoor_DoorGiven_TargetsDoorCell перестанет ловить эту поломку. При смене closeDoor_DoorGiven_TargetsDoorCell проверь это утверждение.");
        assertEquals(
                -3,
                close.params().get("z"),
                "Дверь задаётся координатой z"
                        + " Иначе тест closeDoor_DoorGiven_TargetsDoorCell перестанет ловить эту поломку. При смене closeDoor_DoorGiven_TargetsDoorCell проверь это утверждение.");
    }

    @Test
    void cover_SpotGiven_CoversWithBlock() {
        SequenceNode plan = sequence(MorePlans.cover(AT, "minecraft:cobblestone"));
        ScriptCall place = script(plan.steps().get(1), 0);
        assertEquals(
                "place_block",
                place.script(),
                "Закрыть воронку — поставить блок"
                        + " Иначе тест cover_SpotGiven_CoversWithBlock перестанет ловить эту поломку. При смене cover_SpotGiven_CoversWithBlock проверь это утверждение.");
        assertEquals(
                "minecraft:cobblestone",
                place.params().get("block"),
                "Блок покрытия задаётся id"
                        + " Иначе тест cover_SpotGiven_CoversWithBlock перестанет ловить эту поломку. При смене cover_SpotGiven_CoversWithBlock проверь это утверждение.");
    }

    @Test
    void clearCells_TwoCells_RemovesEach() {
        SequenceNode plan = sequence(MorePlans.clearCells(List.of(AT, new BlockCoord(1, 2, 3))));
        assertEquals(
                2,
                plan.steps().size(),
                "Каждая опасная клетка убирается отдельным шагом"
                        + " Иначе тест clearCells_TwoCells_RemovesEach перестанет ловить эту поломку. При смене clearCells_TwoCells_RemovesEach проверь это утверждение.");
        FallbackNode retry = fallback(plan.steps().get(0));
        ScriptCall clear = script(retry.steps().get(0), 0);
        assertEquals(
                "clear_area",
                clear.script(),
                "Огонь убирают очисткой клетки"
                        + " Иначе тест clearCells_TwoCells_RemovesEach перестанет ловить эту поломку. При смене clearCells_TwoCells_RemovesEach проверь это утверждение.");
        assertEquals(
                10,
                clear.params().get("x1"),
                "Клетка очистки начинается в своей координате"
                        + " Иначе тест clearCells_TwoCells_RemovesEach перестанет ловить эту поломку. При смене clearCells_TwoCells_RemovesEach проверь это утверждение.");
        assertEquals(
                10,
                clear.params().get("x2"),
                "Клетка очистки кончается в своей координате"
                        + " Иначе тест clearCells_TwoCells_RemovesEach перестанет ловить эту поломку. При смене clearCells_TwoCells_RemovesEach проверь это утверждение.");
    }

    @Test
    void fillCells_CellGiven_SealsWithBlock() {
        SequenceNode plan = sequence(MorePlans.fillCells(List.of(AT), "minecraft:cobblestone"));
        ScriptCall fill = script(fallback(plan.steps().get(0)).steps().get(0), 0);
        assertEquals(
                "fill",
                fill.script(),
                "Лаву и поток заливают блоком"
                        + " Иначе тест fillCells_CellGiven_SealsWithBlock перестанет ловить эту поломку. При смене fillCells_CellGiven_SealsWithBlock проверь это утверждение.");
        assertEquals(
                "minecraft:cobblestone",
                fill.params().get("block"),
                "Блок заливки задаётся id"
                        + " Иначе тест fillCells_CellGiven_SealsWithBlock перестанет ловить эту поломку. При смене fillCells_CellGiven_SealsWithBlock проверь это утверждение.");
        assertEquals(
                64,
                fill.params().get("y1"),
                "Заливка идёт по координате клетки"
                        + " Иначе тест fillCells_CellGiven_SealsWithBlock перестанет ловить эту поломку. При смене fillCells_CellGiven_SealsWithBlock проверь это утверждение.");
    }

    @Test
    void unload_ForceTrue_GoesToWarehouse() {
        ScriptCall unload = script(MorePlans.unload(true), 0);
        assertEquals(
                "unload",
                unload.script(),
                "Полные руки освобождают выгрузкой"
                        + " Иначе тест unload_ForceTrue_GoesToWarehouse перестанет ловить эту поломку. При смене unload_ForceTrue_GoesToWarehouse проверь это утверждение.");
        assertEquals(
                "warehouse",
                unload.params().get("container"),
                "Сундук берётся из списка склада поселения, а не ищется в радиусе 8 от дома: "
                        + "живой отказ «рядом нет допустимого контейнера» (10.11)"
                        + " Иначе тест unload_ForceTrue_GoesToWarehouse перестанет ловить эту поломку. При смене unload_ForceTrue_GoesToWarehouse проверь это утверждение.");
        assertTrue(
                (Boolean) unload.params().get("force"),
                "Выгрузка идёт принудительно: место кончается"
                        + " Иначе тест unload_ForceTrue_GoesToWarehouse перестанет ловить эту поломку. При смене unload_ForceTrue_GoesToWarehouse проверь это утверждение.");
    }

    @Test
    void withdraw_ItemGiven_TakesFromChest() {
        ScriptCall withdraw = script(MorePlans.withdraw("oak_log", 4), 0);
        assertEquals(
                "withdraw",
                withdraw.script(),
                "Пополнить запас — взять из сундука"
                        + " Иначе тест withdraw_ItemGiven_TakesFromChest перестанет ловить эту поломку. При смене withdraw_ItemGiven_TakesFromChest проверь это утверждение.");
        assertEquals(
                "oak_log",
                withdraw.params().get("item"),
                "Предмет задаётся id"
                        + " Иначе тест withdraw_ItemGiven_TakesFromChest перестанет ловить эту поломку. При смене withdraw_ItemGiven_TakesFromChest проверь это утверждение.");
        assertEquals(
                4,
                withdraw.params().get("count"),
                "Количество задаётся числом"
                        + " Иначе тест withdraw_ItemGiven_TakesFromChest перестанет ловить эту поломку. При смене withdraw_ItemGiven_TakesFromChest проверь это утверждение.");
    }

    @Test
    void hazardCenters_AllAreasGiven_CoversEachOnce() {
        BlockCoord home = new BlockCoord(1, 2, 3);
        BlockCoord house = new BlockCoord(4, 5, 6);

        List<BlockCoord> centers = MorePlans.hazardCenters(home, house, home);

        assertEquals(
                List.of(home, house),
                centers,
                "Очаг ищется у дома и у каждой площадки этапа ровно один раз: пропуск площадки оставит пожар "
                        + "у постройки без действия"
                        + " Иначе тест hazardCenters_AllAreasGiven_CoversEachOnce перестанет ловить эту поломку. При смене hazardCenters_AllAreasGiven_CoversEachOnce проверь это утверждение.");
    }

    private static SequenceNode sequence(TaskNode node) {
        return (SequenceNode) node;
    }

    private static FallbackNode fallback(TaskNode node) {
        return (FallbackNode) node;
    }

    private static ScriptCall script(TaskNode node, int index) {
        if (node instanceof SequenceNode sequence) {
            return (ScriptCall) sequence.steps().get(index);
        }
        return (ScriptCall) node;
    }
}
