package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.PathReuse;
import org.junit.jupiter.api.Test;

class PathReuseTests {
    @Test
    void keep_SameTargetWithActivePath_IsTrue() {
        assertTrue(
                PathReuse.keep(true, -156, 71, -489, -156, 71, -489, true),
                "Повторный запрос пути к той же цели при живом пути обязан переиспользовать путь: иначе дух "
                        + "разворачивается к началу нового пути и дёргается на месте"
                        + " При смене keep_SameTargetWithActivePath_IsTrue проверь это утверждение.");
    }

    @Test
    void keep_SameTargetWithoutActivePath_IsFalse() {
        assertFalse(
                PathReuse.keep(true, -156, 71, -489, -156, 71, -489, false),
                "Закончившийся путь не переиспользуют: к той же цели нужен новый путь"
                        + " Иначе тест keep_SameTargetWithoutActivePath_IsFalse перестанет ловить эту поломку. При смене keep_SameTargetWithoutActivePath_IsFalse проверь это утверждение.");
    }

    @Test
    void keep_OtherTarget_IsFalse() {
        assertFalse(
                PathReuse.keep(true, -156, 71, -489, -178, 73, -518, true),
                "Для другой цели путь строится заново: иначе дух пойдёт к старой точке"
                        + " При смене keep_OtherTarget_IsFalse проверь это утверждение.");
    }

    @Test
    void keep_NoPreviousTarget_IsFalse() {
        assertFalse(
                PathReuse.keep(false, 0, 0, 0, -156, 71, -489, true),
                "Без прошлой цели переиспользовать нечего"
                        + " Иначе тест keep_NoPreviousTarget_IsFalse перестанет ловить эту поломку. При смене keep_NoPreviousTarget_IsFalse проверь это утверждение.");
    }

    @Test
    void keep_ApproachSameCellEveryTick_KeepsPath() {
        boolean first = PathReuse.keep(false, 0, 0, 0, -163, 75, -488, false);
        boolean repeated = PathReuse.keep(true, -163, 75, -488, -163, 75, -488, true);

        assertFalse(
                first,
                "Первый запрос к новой цели строит свежий путь: иначе кеш прошлой цели увёл бы духа к "
                        + "чужой точке, и подход к новой цели пропал бы. При смене keep проверь "
                        + "PathReuseTests.keep_OtherTarget_IsFalse и живой goto к меняющимся точкам доски");
        assertTrue(
                repeated,
                "Approach зовёт путь к той же клетке каждый тик, в том числе перегрузкой с floorLevel: без "
                        + "переиспользования путь пересоздаётся, next и yaw мигают через тик (окно 7, spirit-1), "
                        + "иначе дух дрожит на месте вместо хода. При смене moveTo в SpiritNavigation проверь "
                        + "обе перегрузки: четырёхаргументную и пятиаргументную с floorLevel");
    }

    @Test
    void keep_ApproachRestartSameCell_KeepsPath() {
        assertTrue(
                PathReuse.keep(true, -155, 68, -490, -155, 68, -490, true),
                "Повторный begin подхода к той же точке не рвёт путь: иначе stop() в start() обнуляет путь, "
                        + "и навигация строит его 5 раз в секунду (1592 пути за 5 минут, окно 12), а дух "
                        + "дрожит у начала маршрута. При смене Approach.start или SpiritNavigation.stop "
                        + "проверь живой goto к неподвижной точке: путь строится один раз");
    }

    @Test
    void futile_OneNodeNotReached_IsTrue() {
        assertTrue(
                PathReuse.futile(1, false),
                "Путь из одной клетки без достижения цели бесполезен: дух уже стоит в ней, навигация считает шаг "
                        + "выполненным и запрашивает путь заново — иначе получается 4–5 расчётов в секунду "
                        + "(1352 за 5 минут, окно 13). При смене SpiritNavigation.reuseOrRebuild проверь "
                        + "кэш недостижимой цели и живой дух у стены");
    }

    @Test
    void futile_FullPath_IsFalse() {
        assertFalse(
                PathReuse.futile(24, false),
                "Длинный частичный путь остаётся рабочим: иначе дух бросит обход у края недостижимой цели "
                        + "и застрянет навсегда. При смене правила ничтожного пути проверь обход ямы: "
                        + "24 узла должны вести духа, а не откладывать поиск");
    }

    @Test
    void futile_OneNodeReached_IsFalse() {
        assertFalse(
                PathReuse.futile(1, true),
                "Путь из одной клетки с достигнутой целью нормален: дух уже на месте, иначе отказ сломает приход "
                        + "и удержание точки. При смене canReach в пути проверь Approach к цели вплотную "
                        + "и живой goto в центр клетки");
    }

    @Test
    void waiting_BeforeUntil_IsTrue() {
        assertTrue(
                PathReuse.waiting(100, 140),
                "До истечения паузы поиск не повторяют: иначе откат не гасит нагрузку и дух снова считает "
                        + "путь каждый тик. При смене шага паузы проверь, что за 40 тиков дух один раз ждёт, "
                        + "а не сто сорок");
    }

    @Test
    void waiting_AfterUntil_IsFalse() {
        assertFalse(
                PathReuse.waiting(140, 140),
                "После паузы поиск пробуют снова: иначе цель, ставшая достижимой, останется заблокированной "
                        + "и дух не пойдёт. При смене waiting проверь повторный расчёт к освободившейся цели");
    }
}
