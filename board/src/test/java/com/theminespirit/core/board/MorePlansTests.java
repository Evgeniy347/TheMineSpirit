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
        assertEquals("return_home", call.script(), "Возврат домой — отдельный скрипт, а не ходьба по координатам");
        assertTrue(call.params().isEmpty(), "return_home не требует параметров: дом задан духу заранее");
    }

    @Test
    void meet_TargetGiven_GoesAndMeets() {
        SequenceNode plan = sequence(MorePlans.meet(AT, "player:Steve"));
        assertEquals("goto", script(plan.steps().get(0), 0).script(), "Сначала дух идёт к игроку");
        assertEquals(
                Map.of("target", "player:Steve"),
                script(plan.steps().get(1), 0).params(),
                "meet получает имя игрока: по нему цель находится в мире");
    }

    @Test
    void keepDistance_TargetGiven_KeepsConfiguredGap() {
        SequenceNode plan = sequence(MorePlans.keepDistance(AT, "player:Steve"));
        Map<String, Object> params = script(plan.steps().get(1), 0).params();
        assertEquals("player:Steve", params.get("target"), "Дистанцию держат от конкретного игрока");
        assertEquals(MorePlans.KEEP_DISTANCE, params.get("distance"), "Дистанция берётся из константы, а не с потолка");
    }

    @Test
    void patrol_PointGiven_WalksOneLoop() {
        SequenceNode plan = sequence(MorePlans.patrol(AT));
        ScriptCall patrol = script(plan.steps().get(1), 0);
        assertEquals("patrol", patrol.script(), "Охрана жителя — патруль у его точки");
        assertEquals(List.of("10,64,-3"), patrol.params().get("points"), "Точка патруля задаётся строкой x,y,z");
        assertEquals(1, patrol.params().get("loops"), "Один круг: охрана не зацикливается навсегда");
    }

    @Test
    void safeMine_EntranceGiven_StartsWithGoto() {
        SequenceNode plan = sequence(MorePlans.safeMine(AT));
        assertEquals("goto", script(plan.steps().get(0), 0).script(), "К шахте сначала идут ходьбой");
        ScriptCall mine = script(plan.steps().get(1), 0);
        assertEquals("safe_mine", mine.script(), "Истощённую шахту продолжают безопасной проходкой");
        assertEquals(MorePlans.SAFE_MINE_MODE, mine.params().get("mode"), "Форма проходки задана данными");
        assertEquals(
                MorePlans.SAFE_MINE_DIRECTION, mine.params().get("direction"), "Направление проходки задано данными");
    }

    @Test
    void plantCrop_FieldGiven_SowsWheat() {
        SequenceNode plan = sequence(MorePlans.plantCrop(AT, "wheat"));
        ScriptCall plant = script(plan.steps().get(1), 0);
        assertEquals("plant_crop", plant.script(), "Еда на поле — посадка культуры");
        assertEquals("wheat", plant.params().get("crop"), "Культура задаётся id");
        assertEquals(MorePlans.FARM_RADIUS, plant.params().get("radius"), "Радиус поиска поля ограничен");
        assertEquals(
                MorePlans.FARM_COUNT, plant.params().get("count"), "За один подход сажают ограниченное число клеток");
    }

    @Test
    void closeDoor_DoorGiven_TargetsDoorCell() {
        SequenceNode plan = sequence(MorePlans.closeDoor(AT));
        ScriptCall close = script(plan.steps().get(1), 0);
        assertEquals("close_door", close.script(), "Открытую дверь закрывают своим скриптом");
        assertEquals(10, close.params().get("x"), "Дверь задаётся координатой x");
        assertEquals(64, close.params().get("y"), "Дверь задаётся координатой y");
        assertEquals(-3, close.params().get("z"), "Дверь задаётся координатой z");
    }

    @Test
    void cover_SpotGiven_CoversWithBlock() {
        SequenceNode plan = sequence(MorePlans.cover(AT, "minecraft:cobblestone"));
        ScriptCall place = script(plan.steps().get(1), 0);
        assertEquals("place_block", place.script(), "Закрыть воронку — поставить блок");
        assertEquals("minecraft:cobblestone", place.params().get("block"), "Блок покрытия задаётся id");
    }

    @Test
    void clearCells_TwoCells_RemovesEach() {
        SequenceNode plan = sequence(MorePlans.clearCells(List.of(AT, new BlockCoord(1, 2, 3))));
        assertEquals(2, plan.steps().size(), "Каждая опасная клетка убирается отдельным шагом");
        FallbackNode retry = fallback(plan.steps().get(0));
        ScriptCall clear = script(retry.steps().get(0), 0);
        assertEquals("clear_area", clear.script(), "Огонь убирают очисткой клетки");
        assertEquals(10, clear.params().get("x1"), "Клетка очистки начинается в своей координате");
        assertEquals(10, clear.params().get("x2"), "Клетка очистки кончается в своей координате");
    }

    @Test
    void fillCells_CellGiven_SealsWithBlock() {
        SequenceNode plan = sequence(MorePlans.fillCells(List.of(AT), "minecraft:cobblestone"));
        ScriptCall fill = script(fallback(plan.steps().get(0)).steps().get(0), 0);
        assertEquals("fill", fill.script(), "Лаву и поток заливают блоком");
        assertEquals("minecraft:cobblestone", fill.params().get("block"), "Блок заливки задаётся id");
        assertEquals(64, fill.params().get("y1"), "Заливка идёт по координате клетки");
    }

    @Test
    void unload_ForceTrue_GoesToWarehouse() {
        ScriptCall unload = script(MorePlans.unload(true), 0);
        assertEquals("unload", unload.script(), "Полные руки освобождают выгрузкой");
        assertEquals(
                "warehouse",
                unload.params().get("container"),
                "Сундук берётся из списка склада поселения, а не ищется в радиусе 8 от дома: "
                        + "живой отказ «рядом нет допустимого контейнера» (10.11)");
        assertTrue((Boolean) unload.params().get("force"), "Выгрузка идёт принудительно: место кончается");
    }

    @Test
    void withdraw_ItemGiven_TakesFromChest() {
        ScriptCall withdraw = script(MorePlans.withdraw("oak_log", 4), 0);
        assertEquals("withdraw", withdraw.script(), "Пополнить запас — взять из сундука");
        assertEquals("oak_log", withdraw.params().get("item"), "Предмет задаётся id");
        assertEquals(4, withdraw.params().get("count"), "Количество задаётся числом");
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
                        + "у постройки без действия");
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
