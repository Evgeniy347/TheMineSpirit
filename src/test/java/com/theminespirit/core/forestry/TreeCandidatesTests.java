package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TreeCandidatesTests {
    private static List<BlockCoord> logs(int count) {
        List<BlockCoord> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(i, 1, 0));
        }
        return logs;
    }

    @Test
    void isFull_AllCandidatesLive_IsTrueAtLimit() {
        TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);

        logs(ChopTuning.SEARCH_MAX_LOGS).forEach(candidates::offer);

        assertTrue(
                candidates.isFull(),
                "Лимит живых кандидатов достигнут: поиск обязан остановиться, иначе список растёт без границ");
    }

    @Test
    void isFull_RejectedCandidatesBecomeKnown_IsFalseAgain() {
        TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);
        List<BlockCoord> building = logs(300);
        building.forEach(candidates::offer);
        assertTrue(candidates.isFull(), "Постройка из 300 брёвен заполняет лимит кандидатов");

        candidates.markKnown(building.subList(0, 129));

        assertFalse(
                candidates.isFull(),
                "Отвергнутые брёвна не занимают лимит: лимит считает только живых кандидатов. Иначе постройка "
                        + "из 256+ брёвен, все отвергнуты, поиск не идёт дальше и скрипт висит RUNNING без конца, "
                        + "не дойдя до дерева за постройкой");
        assertEquals(
                300 - 129,
                candidates.liveCount(),
                "Живых кандидатов остаётся ровно столько, сколько брёвен ещё не разобрано");
    }

    @Test
    void offer_AlreadyKnownLog_IsIgnored() {
        TreeCandidates candidates = new TreeCandidates(10);
        BlockCoord log = new BlockCoord(1, 1, 1);
        candidates.markKnown(List.of(log));

        candidates.offer(log);

        assertEquals(
                0,
                candidates.liveCount(),
                "Разобранное бревно, повторно найденное поиском, не становится кандидатом: иначе срубленное "
                        + "дерево разбиралось бы заново на каждом проходе поиска");
        assertTrue(candidates.isKnown(log), "Бревно остаётся известным");
    }

    @Test
    void nearest_SkipsKnown_ReturnsNearestLive() {
        TreeCandidates candidates = new TreeCandidates(10);
        candidates.offer(new BlockCoord(1, 1, 0));
        candidates.offer(new BlockCoord(5, 1, 0));
        candidates.markKnown(List.of(new BlockCoord(1, 1, 0)));

        assertEquals(
                Optional.of(new BlockCoord(5, 1, 0)),
                candidates.nearest(0, 1, 0),
                "Ближайшее бревно среди неизвестных: известное исключено, иначе дерево разбирается повторно");
    }

    @Test
    void nearest_NoLiveCandidates_IsEmpty() {
        TreeCandidates candidates = new TreeCandidates(10);
        candidates.offer(new BlockCoord(1, 1, 0));
        candidates.markKnown(List.of(new BlockCoord(1, 1, 0)));

        assertTrue(
                candidates.nearest(0, 1, 0).isEmpty(),
                "Живых кандидатов нет: выбирать нечего, скрипт ждёт поиска или заканчивает");
    }

    @Test
    void nearestNear_LogsBeyondSettlementLimit_AreNotChosen() {
        TreeCandidates candidates = new TreeCandidates(10);
        candidates.offer(new BlockCoord(70, 64, 0));
        candidates.offer(new BlockCoord(73, 56, -9));

        assertTrue(
                candidates.nearestNear(new BlockCoord(0, 64, 0), 32, 0, 64, 0).isEmpty(),
                "Дерево дальше 32 блоков от поселения не выбирается: дух не доходит до леса в 65–73 блоках за "
                        + "3600 тиков, задание проваливается без прогресса. Как надо: ближнее дерево, иначе честная "
                        + "причина");
    }

    @Test
    void nearestNear_ChoosesLogClosestToSettlement_NotToSpirit() {
        TreeCandidates candidates = new TreeCandidates(10);
        candidates.offer(new BlockCoord(31, 64, 0));
        candidates.offer(new BlockCoord(10, 64, 0));

        assertEquals(
                Optional.of(new BlockCoord(10, 64, 0)),
                candidates.nearestNear(new BlockCoord(0, 64, 0), 32, 30, 64, 0),
                "Выбор идёт по расстоянию до поселения, а не до духа: иначе дух уходит в лес к юго-западу от сада "
                        + "и не возвращается");
    }

    @Test
    void nearestNear_LogAtLimit_IsChosen() {
        TreeCandidates candidates = new TreeCandidates(10);
        candidates.offer(new BlockCoord(32, 64, 0));

        assertEquals(
                Optional.of(new BlockCoord(32, 64, 0)),
                candidates.nearestNear(new BlockCoord(0, 64, 0), 32, 0, 64, 0),
                "Предел дальности включительный: дерево ровно в 32 блоках ещё в работе");
    }

    @Test
    void constructor_LimitBelowOne_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TreeCandidates(0),
                "Лимит меньше единицы не даёт принять ни одного бревна: ошибка конфигурации, а не пустой результат");
    }
}
