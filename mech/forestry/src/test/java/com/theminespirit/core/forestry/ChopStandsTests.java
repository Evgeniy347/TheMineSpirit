package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ChopStandsTests {
    @Test
    void stands_GroundNextToTrunk_ReturnsEveryGroundStand() {
        Tree tree = trunk(64, 3);
        List<BlockCoord> stands = ChopStands.stands(tree, groundAt(64));

        assertEquals(
                8,
                stands.size(),
                "Вокруг ствола на земле восемь стоянок: дух встаёт на землю, а не на дерево, иначе рубка начинается "
                        + "сверху. При смене ChopStands.stands проверь строку уровня ниже в тесте");
        assertTrue(
                stands.stream().allMatch(feet -> feet.y() == 64),
                "Стоянка у ствола — на земле основания: залезать на дерево дух не должен, иначе он рубит под собой. "
                        + "При смене выбора стоянок проверь строку ствола ниже в тесте");
        assertFalse(
                stands.contains(new BlockCoord(0, 64, 0)),
                "Клетка самого ствола стоянкой не бывает, иначе дух стоит в дереве. При смене проверки стоянок "
                        + "проверь строку размера выше в тесте");
    }

    @Test
    void stands_ThickTrunk_NeverStandsOnLogs() {
        Tree tree = new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(1, 64, 0), new BlockCoord(0, 65, 0)));
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        List<BlockCoord> stands = ChopStands.stands(
                tree, new TestView(feet -> feet.y() == 64 && !logs.contains(feet), cell -> !logs.contains(cell)));

        assertFalse(
                stands.contains(new BlockCoord(1, 64, 0)),
                "Вторая клетка толстого ствола — не стоянка: дух не стоит в дереве, иначе падает при рубке. При "
                        + "смене проверки занятости проверь строку выше основания ниже в тесте");
        assertFalse(
                stands.contains(new BlockCoord(0, 65, 0)),
                "Бревно выше основания тоже не стоянка: на дерево не лезут, иначе дух рубит под собой. При смене "
                        + "ChopStands.stands проверь строку на бревне ниже в тесте");
        assertFalse(
                stands.contains(new BlockCoord(1, 65, 0)),
                "Стоять на бревне нельзя: клетка занята стволом, иначе дух застревает. При смене проверки свободной "
                        + "клетки проверь строку выше в тесте");
    }

    @Test
    void stands_CellDirectlyAboveLog_IsNotStand() {
        Tree tree = new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(0, 67, 0)));
        Set<BlockCoord> logs = Set.copyOf(tree.logs());

        List<BlockCoord> stands =
                ChopStands.stands(tree, new TestView(feet -> !logs.contains(feet), cell -> !logs.contains(cell)));

        assertFalse(
                stands.contains(new BlockCoord(0, 66, 0)),
                "Клетка прямо над бревном — не стоянка: дух срубит ветку под собой и упадёт, " + stands + ", иначе "
                        + "рубка опасна. При смене проверки опоры проверь строку без земли ниже в тесте");
    }

    @Test
    void stands_NoStandableGround_IsEmpty() {
        assertEquals(
                List.of(),
                ChopStands.stands(trunk(64, 3), new TestView(feet -> false, cell -> false)),
                "Нет свободной клетки у ствола — нет стоянки: дух не подходит к стволу и не лезет на дерево, иначе "
                        + "план пуст с ошибкой. При смене ChopStands.stands проверь строку кучи ниже в тесте");
    }

    @Test
    void stands_ForestPileByTrunk_StillFindsStandFartherWithinReach() {
        Tree tree = trunk(64, 4);
        Set<BlockCoord> blocked = new HashSet<>();
        for (int dy = -3; dy <= 8; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        blocked.add(new BlockCoord(dx, 64 + dy, dz));
                    }
                }
            }
        }
        TestView view = new TestView(feet -> !blocked.contains(feet), cell -> !blocked.contains(cell));
        assertTrue(
                ChopStands.standPoints(tree, view).stream()
                        .noneMatch(stand -> Math.abs(stand.feet().x()) <= 1
                                && Math.abs(stand.feet().z()) <= 1),
                "Условие теста: вплотную к стволу стоянки нет — ствол в куче деревьев, иначе строки ниже не "
                        + "проверяют дальний поиск. При смене статической проверки проверь строки ниже в тесте");

        List<ChopStands.Stand> stands = ChopStands.standPoints(tree, view);

        assertFalse(
                stands.isEmpty(),
                "Лес вплотную к стволу не делает дерево недоступным: стоянку ищут дальше, но в пределах рубки, "
                        + "иначе дерево в лесу недоступно. При смене FAR_RADIUS проверь строку дальше ниже в тесте");
        assertTrue(
                stands.stream()
                        .anyMatch(stand -> Math.abs(stand.feet().x()) > 1
                                || Math.abs(stand.feet().z()) > 1),
                "Дерево в куче рубится со стоянки дальше соседней клетки, а не бросается как недоступное, иначе "
                        + "куча отменяет рубку. При смене поиска стоянок проверь строку предела ниже в тесте");
        assertTrue(
                stands.stream()
                        .allMatch(stand -> Math.abs(stand.feet().x()) <= ChopStands.FAR_RADIUS
                                && Math.abs(stand.feet().z()) <= ChopStands.FAR_RADIUS),
                "Дальняя стоянка остаётся в пределах досягаемости рубки, иначе дух не достанет ствол. При смене "
                        + "FAR_RADIUS проверь строку выше в тесте");
    }

    @Test
    void stands_HighGroundStandOutsideOldHeightWindow_IsFound() {
        Tree tree = trunk(64, 6);
        Set<BlockCoord> ground = new HashSet<>();
        for (int y = 63; y <= 67; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        ground.add(new BlockCoord(dx, y, dz));
                    }
                }
            }
        }
        Set<BlockCoord> free = new HashSet<>();
        for (int y = 68; y <= 72; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        free.add(new BlockCoord(dx, y, dz));
                    }
                }
            }
        }
        TestView view = new TestView(
                feet -> free.contains(feet) && ground.contains(new BlockCoord(feet.x(), feet.y() - 1, feet.z())),
                free::contains);

        List<BlockCoord> stands = ChopStands.stands(tree, view);

        assertTrue(
                stands.contains(new BlockCoord(1, 68, 0)),
                "Стоянка на верху бугра вплотную к стволу обязана найтись: высота от основания не предел, а старый "
                        + "поиск смотрел только основание −3..+1: " + stands + ", иначе бугор отменяет рубку. При "
                        + "смене окна высот проверь строку ниже в тесте");
        assertTrue(
                stands.stream().noneMatch(feet -> feet.y() <= 67),
                "Ниже верха бугра стоянки нет: клетки заняты породой, " + stands + ", иначе дух висит в породе. При "
                        + "смене проверки земли проверь строку выше в тесте");
    }

    @Test
    void standPoints_BranchAboveGround_ClimbsFromWalkableBase() {
        List<BlockCoord> logs = new ArrayList<>();
        for (int y = 1; y <= 8; y++) {
            logs.add(new BlockCoord(0, y, 0));
        }
        Tree hanging = new Tree(new BlockCoord(0, 1, 0), logs);
        TestView view = new TestView(
                feet -> feet.y() == 1 && feet.x() == 1 && feet.z() == 0,
                cell -> cell.y() >= 1 && cell.y() <= 9 && !logs.contains(cell));

        List<ChopStands.Stand> stands = ChopStands.standPoints(hanging, view);

        ChopStands.Stand climb = stands.stream()
                .filter(stand -> stand.feet().equals(new BlockCoord(1, 8, 0)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Стоянка у верхней ветки не найдена: " + stands));
        assertTrue(
                climb.climbFrom() != null,
                "До ветки на высоте 8 не дойти пешком: стоянка обязана быть с подъёмом, иначе ветка недостижима. "
                        + "При смене ChopStands.standPoints проверь строку опоры ниже в тесте");
        assertEquals(
                new BlockCoord(1, 1, 0),
                climb.climbFrom(),
                "Опора подмостей — ходимая клетка под стоянкой, а не воздух: " + stands + ", иначе подмостки висят. "
                        + "При смене поиска подъёма проверь строку выше в тесте");
    }

    @Test
    void standPoints_BlockedColumnOverBase_NoClimbStand() {
        Tree tree = trunk(64, 8);
        TestView view = new TestView(
                feet -> feet.y() == 64 && feet.x() == 1 && feet.z() == 0,
                cell -> !cell.equals(new BlockCoord(1, 66, 0)));

        List<ChopStands.Stand> stands = ChopStands.standPoints(tree, view);

        assertTrue(
                stands.stream().noneMatch(stand -> stand.feet().equals(new BlockCoord(1, 70, 0))),
                "Блок в столбе подъёма не даёт подняться: стоянка за ним не достижима, " + stands + ", иначе дух "
                        + "лезет сквозь блок. При смене проверки столба проверь строку основания ниже в тесте");
        assertTrue(
                stands.stream().anyMatch(stand -> stand.feet().equals(new BlockCoord(1, 64, 0))),
                "Ходимая стоянка у основания остаётся: " + stands + ", иначе заблокированный подъём отменяет рубку. "
                        + "При смене standPoints проверь строку выше в тесте");
    }

    @Test
    void standPoints_HangingTopWithoutTrunk_ClimbsFromGround() {
        List<BlockCoord> logs = List.of(new BlockCoord(0, 6, 0), new BlockCoord(0, 7, 0), new BlockCoord(0, 8, 0));
        Tree hanging = new Tree(new BlockCoord(0, 6, 0), logs);
        TestView view = new TestView(feet -> feet.y() == 1, cell -> cell.y() >= 1 && cell.y() <= 9);

        List<ChopStands.Stand> stands = ChopStands.standPoints(hanging, view);

        ChopStands.Stand climb = stands.stream()
                .filter(stand -> stand.feet().equals(new BlockCoord(1, 6, 0)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Стоянка у висящей верхушки не найдена: " + stands));
        assertEquals(
                new BlockCoord(1, 1, 0),
                climb.climbFrom(),
                "Висящая верхушка рубится с подъёмом от земли под ней: " + stands + ", иначе верхушка недостижима. "
                        + "При смене поиска подъёма проверь строку запасной ниже в тесте");
        assertTrue(
                stands.stream()
                        .anyMatch(stand -> stand.feet().equals(new BlockCoord(0, 1, 0)) && stand.climbFrom() == null),
                "Земля прямо под верхушкой остаётся ходимой запасной стоянкой без столба: " + stands + ", иначе "
                        + "духу некуда встать. При смене standPoints проверь строку выше в тесте");
    }

    @Test
    void standPoints_GroundOnlyHighLog_NoClimbStands() {
        List<BlockCoord> logs = List.of(new BlockCoord(0, 6, 0), new BlockCoord(0, 7, 0), new BlockCoord(0, 8, 0));
        Tree hanging = new Tree(new BlockCoord(0, 6, 0), logs);
        TestView view = new TestView(feet -> feet.y() == 1, cell -> cell.y() >= 1 && cell.y() <= 9);

        List<ChopStands.Stand> stands = ChopStands.standPoints(hanging, view, true);

        assertFalse(
                stands.isEmpty(),
                "Висящая верхушка рубится с земли: стоянка на поверхности обязана найтись, " + stands + ", иначе "
                        + "очистка зоны бросает верхние брёвна. При смене groundOnly проверь строку ниже в тесте");
        assertTrue(
                stands.stream().allMatch(stand -> stand.climbFrom() == null),
                "В режиме земли подъёмов нет: ни одна стоянка не несёт опору подмостей, " + stands + ", иначе дух "
                        + "снова строит столбы. При смене standPoints проверь строку земли ниже в тесте");
        assertTrue(
                stands.stream().anyMatch(stand -> stand.feet().equals(new BlockCoord(0, 1, 0))),
                "Земля прямо под верхушкой — законная стоянка, " + stands + ", иначе духу некуда встать. При "
                        + "смене groundBelow проверь строку выше в тесте");
    }

    private static ChopStands.View groundAt(int groundY) {
        return new TestView(
                feet -> feet.y() == groundY && (feet.x() != 0 || feet.z() != 0), cell -> cell.y() == groundY);
    }

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }

    private record TestView(Predicate<BlockCoord> standableCheck, Predicate<BlockCoord> freeCheck)
            implements ChopStands.View {
        @Override
        public boolean standable(BlockCoord feet) {
            return standableCheck.test(feet);
        }

        @Override
        public boolean free(BlockCoord cell) {
            return freeCheck.test(cell);
        }
    }
}
