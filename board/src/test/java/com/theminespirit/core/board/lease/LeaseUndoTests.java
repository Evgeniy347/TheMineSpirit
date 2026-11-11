package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseUndoTests {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void order_TopDownAndReverseRecord_ReturnsTopFirst() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(OWNER, null, "scaffold", 10);
        BlockCoord low = new BlockCoord(0, 70, 0);
        BlockCoord middle = new BlockCoord(0, 72, 0);
        BlockCoord high = new BlockCoord(0, 74, 0);
        book.record(lease.id(), List.of(LeaseCell.placed(low), LeaseCell.placed(middle), LeaseCell.placed(high)));

        assertEquals(
                List.of(high, middle, low),
                LeaseUndo.order(book.require(lease.id())).stream()
                        .map(LeaseUndo.Step::pos)
                        .toList(),
                "Откат идёт сверху вниз: нижний блок столба держит духа, его убирают последним");
    }

    @Test
    void order_EqualHeight_KeepsReverseRecordOrder() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(OWNER, null, "bridge", 10);
        BlockCoord first = new BlockCoord(10, 64, 0);
        BlockCoord second = new BlockCoord(11, 64, 0);
        book.record(lease.id(), List.of(LeaseCell.placed(first), LeaseCell.placed(second)));

        assertEquals(
                List.of(second, first),
                LeaseUndo.order(book.require(lease.id())).stream()
                        .map(LeaseUndo.Step::pos)
                        .toList(),
                "В одной высоте порядок обратный записи: настил разбирается с дальнего конца");
    }

    @Test
    void supportsBody_PlacedCellDirectlyBelow_IsBodySupport() {
        BlockCoord feet = new BlockCoord(0, 70, 0);
        LeaseUndo.Step support = new LeaseUndo.Step(new BlockCoord(0, 69, 0), LeaseKind.PLACED, null);
        LeaseUndo.Step restored = new LeaseUndo.Step(new BlockCoord(0, 69, 0), LeaseKind.BROKEN, "minecraft:stone");
        LeaseUndo.Step sameLevel = new LeaseUndo.Step(new BlockCoord(1, 70, 0), LeaseKind.PLACED, null);

        assertTrue(LeaseUndo.supportsBody(support, feet), "Блок прямо под ногами — опора тела");
        assertFalse(LeaseUndo.supportsBody(restored, feet), "Возврат сломанного блока не опора, его возвращают");
        assertFalse(LeaseUndo.supportsBody(sameLevel, feet), "Блок в стороне от тела можно ломать сразу");
    }

    @Test
    void breaksNow_PillarTop_DropsOneCellOntoNextBlock() {
        BlockCoord feet = new BlockCoord(0, 73, 0);
        LeaseUndo.Step top = new LeaseUndo.Step(new BlockCoord(0, 72, 0), LeaseKind.PLACED, null);
        RouteWorld column = (x, y, z) -> x == 0 && z == 0 && y >= 60 && y <= 72 ? RouteCell.SOLID : RouteCell.AIR;

        assertTrue(
                LeaseUndo.breaksNow(top, feet, column, Set.of()),
                "Верх столба ломается: дух падает на одну клетку на следующий блок столба, а не в пропасть");
    }

    @Test
    void breaksNow_SingleBlockOverVoid_IsRefused() {
        BlockCoord feet = new BlockCoord(0, 73, 0);
        LeaseUndo.Step lone = new LeaseUndo.Step(new BlockCoord(0, 72, 0), LeaseKind.PLACED, null);
        RouteWorld floating = (x, y, z) -> x == 0 && z == 0 && y == 72 ? RouteCell.SOLID : RouteCell.AIR;

        assertFalse(
                LeaseUndo.breaksNow(lone, feet, floating, Set.of()),
                "Единственный блок над пропастью не ломают под собой: дух упал бы с высоты — нужен спуск");
    }

    @Test
    void rollback_CellByCell_FreesCellsAndClosesLease() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(OWNER, null, "bridge", 10);
        BlockCoord first = new BlockCoord(10, 64, 0);
        BlockCoord second = new BlockCoord(11, 64, 0);
        book.record(lease.id(), List.of(LeaseCell.placed(first), LeaseCell.placed(second)));
        book.orphan(lease.id());

        book.rollback(lease.id(), second);

        assertEquals(
                LeaseStatus.ORPHANED,
                book.require(lease.id()).status(),
                "Пока осталась хоть одна клетка, аренда ещё брошена и скан её видит");
        assertFalse(book.isOrphaned(second), "Откатанная клетка больше не ждёт уборки");

        book.rollback(lease.id(), first);

        assertEquals(LeaseStatus.DONE, book.require(lease.id()).status(), "Последняя клетка закрывает аренду DONE");
        assertTrue(book.orphanedCells().isEmpty(), "Уборке нечего делать: все клетки откатаны");
    }

    @Test
    void rollback_ActiveOrForeignCell_IsRefused() {
        LeaseBook book = new LeaseBook();
        Lease active = book.open(OWNER, null, "scaffold", 10);
        BlockCoord cell = new BlockCoord(0, 70, 0);
        book.record(active.id(), LeaseCell.placed(cell));

        assertThrows(
                IllegalStateException.class,
                () -> book.rollback(active.id(), cell),
                "Действующую правку нельзя закрыть откатом: владелец ещё работает");

        book.orphan(active.id());
        assertThrows(
                IllegalArgumentException.class,
                () -> book.rollback(active.id(), new BlockCoord(9, 70, 0)),
                "Откат не закрывает клетку, которой в аренде нет");
    }

    @Test
    void door_StateLease_DeathOrphansAndUndoClosesIt() {
        LeaseBook book = new LeaseBook();
        Lease door = book.open(OWNER, null, "door", 100);
        BlockCoord gate = new BlockCoord(5, 65, 5);
        book.record(
                door.id(), LeaseCell.state(gate, "minecraft:oak_door[open=false]", "minecraft:oak_door[open=true]"));

        book.orphanSpirit(OWNER);

        assertTrue(
                book.isOrphaned(gate),
                "Открытая духом дверь после смерти — брошенная аренда STATE, а не открытая навсегда створка");
        List<LeaseUndo.Step> steps = LeaseUndo.order(book.require(door.id()));
        assertEquals(1, steps.size(), "У отката одна клетка: сама створка");
        assertEquals(LeaseKind.STATE, steps.get(0).kind(), "Дверь откатывается возвратом состояния, а не сломом");
        assertEquals(
                "minecraft:oak_door[open=false]",
                steps.get(0).before(),
                "Откат помнит закрытое состояние: дверь закроется, а не исчезнет");

        book.rollback(door.id(), gate);

        assertEquals(LeaseStatus.DONE, book.require(door.id()).status(), "Закрытие двери завершает аренду DONE");
    }

    @Test
    void reach_HighAndLowCells_ChooseClimbOrDig() {
        assertEquals(
                new RollbackReach.Step(RollbackReach.Action.CLIMB, 9),
                RollbackReach.next(9, false),
                "Клетка выше духа добирается своими подмостями: отдельной ветки «если высоко» нет");
        assertEquals(
                RollbackReach.Action.DIG,
                RollbackReach.next(0, false).action(),
                "Клетка на уровне духа без стоянки вскрывается подходом копкой: рядом нет места для ног");
        assertEquals(
                RollbackReach.Action.STEP_DOWN,
                RollbackReach.next(-1, true).action(),
                "Клетка ниже, а под духом свои подмости — шаг вниз по ним, а не прыжок в шахту");
        assertEquals(
                RollbackReach.Action.DIG,
                RollbackReach.next(-4, false).action(),
                "Клетка ниже без своих подмостей — подход копкой; спускаться не по чему");
    }

    @Test
    void nothingToDo_SameBlockOrGone_SkipsTheCell() {
        LeaseUndo.Step placed = new LeaseUndo.Step(new BlockCoord(0, 70, 0), LeaseKind.PLACED, null);
        LeaseUndo.Step broken = new LeaseUndo.Step(new BlockCoord(1, 70, 0), LeaseKind.BROKEN, "minecraft:dirt");
        LeaseUndo.Step door = new LeaseUndo.Step(
                new BlockCoord(2, 70, 0), LeaseKind.STATE, "minecraft:oak_door[open=false,half=lower]");

        assertTrue(LeaseUndo.nothingToDo(placed, true, "minecraft:air"), "Своего блока уже нет — ломать нечего");
        assertFalse(
                LeaseUndo.nothingToDo(placed, false, "minecraft:dirt"),
                "Свой блок на месте — его ломают, а не пропускают");
        assertTrue(
                LeaseUndo.nothingToDo(broken, false, "minecraft:dirt"),
                "Сломанный блок уже вернули — второй раз ставить нечего");
        assertFalse(
                LeaseUndo.nothingToDo(broken, true, "minecraft:air"),
                "Сломанную клетку ещё не вернули: воздух не равен прежнему блоку");
        assertTrue(
                LeaseUndo.nothingToDo(door, false, "minecraft:oak_door[open=false,half=lower]"),
                "Дверь уже закрыта — состояние возвращать нечего");
        assertFalse(
                LeaseUndo.nothingToDo(door, false, "minecraft:oak_door[open=true,half=lower]"),
                "Открытую дверь закрывают: другое состояние — не повод пропускать клетку");
    }

    @Test
    void activeLease_AfterCommit_StopsCleanup() {
        LeaseBook book = new LeaseBook();
        BlockCoord cell = new BlockCoord(0, 70, 0);
        Lease scaffold = book.open(OWNER, "scaffold-1", "scaffold", 100);
        book.record(scaffold.id(), LeaseCell.placed(cell));

        book.commit(scaffold.id());

        assertFalse(book.isActive(cell), "После успешного спуска столб перестал быть действующей арендой");
        assertFalse(book.isOrphaned(cell), "Успешно убранная правка не попадает в уборку графа");
        assertTrue(book.orphanedCells().isEmpty(), "Уборке нечего делать: механика закрыла аренду сама");
    }
}
