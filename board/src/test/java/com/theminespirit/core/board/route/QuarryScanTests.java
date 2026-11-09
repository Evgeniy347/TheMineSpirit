package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuarryScanTests {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);
    private static final BlockCoord SITE = new BlockCoord(40, 64, 20);

    @Test
    void status_PercentBelowTen_LowAndEmpty() {
        QuarrySite site = new QuarrySite(RouteResource.DIRT, SITE, 4, "minecraft:plains", 100);

        assertEquals(
                QuarryStatus.State.ACTIVE,
                QuarryStatus.of(site, 10, true, true).state(),
                "Остаток ровно 10% ещё не «мало»: порог оповещения строго меньше 10%");
        QuarryStatus low = QuarryStatus.of(site, 9, true, false);
        assertEquals(QuarryStatus.State.LOW, low.state(), "Остаток 9% — «мало», стратег должен узнать");
        assertEquals(91, low.dug(), "Выкопано = начальный запас минус остаток: по нему видно, сколько уже взяли");
        assertEquals(
                QuarryStatus.State.EMPTY,
                QuarryStatus.of(site, 0, true, true).state(),
                "Нулевой остаток — «иссякло», а не «мало»: место больше не кормит граф");
        assertEquals(
                QuarryStatus.State.UNSET,
                QuarryStatus.unset(RouteResource.SAND).state(),
                "Место не задано — отдельное состояние, карта пишет «не найдено»");
    }

    @Test
    void alerts_SameResource_NotMoreOftenThanFiveMinutes() {
        QuarryStatus low =
                QuarryStatus.of(new QuarrySite(RouteResource.DIRT, SITE, 4, "minecraft:plains", 100), 8, false, false);

        assertTrue(QuarryAlerts.due(0, 0), "Первый раз о малом запасе сообщают сразу");
        assertFalse(
                QuarryAlerts.due(6099, 100),
                "Через 6099 тиков от прошлого сообщения ещё рано: чаще раза в 5 минут нельзя");
        assertTrue(QuarryAlerts.due(6100, 100), "Ровно через 5 минут сообщение снова разрешено");
        assertEquals(
                "Мало земли в месте добычи: остаток 8% — стратег ищет новое место",
                QuarryAlerts.message(low).orElseThrow(),
                "Оповещение называет ресурс и остаток: игрок понимает, что кончается и кто это исправит");
        assertEquals(
                Optional.empty(),
                QuarryAlerts.message(QuarryStatus.of(
                        new QuarrySite(RouteResource.DIRT, SITE, 4, "minecraft:plains", 100), 50, false, false)),
                "При нормальном запасе чат молчит");
    }

    @Test
    void ranking_ValuePerPath_BestFirst() {
        List<QuarryCandidate> candidates = List.of(
                new QuarryCandidate(RouteResource.DIRT, new BlockCoord(0, 64, 0), 4, 100, 100, "plains"),
                new QuarryCandidate(RouteResource.DIRT, new BlockCoord(200, 64, 0), 4, 50, 10, "plains"),
                new QuarryCandidate(RouteResource.DIRT, new BlockCoord(300, 64, 0), 4, 0, 10, "plains"));

        List<QuarryCandidate> best = QuarryRanking.best(candidates, 10);

        assertEquals(
                new BlockCoord(200, 64, 0),
                best.get(0).point(),
                "Лучшая выгодность — запас на длину пути: близкие 50 блоков песка выгоднее далёких 100");
        assertEquals(2, best.size(), "Пустой кандидат (запас 0) в список лучших не попадает");
        assertEquals(5000, best.get(0).valuePermille(), "Выгодность считается в тысячных без плавающей точки");
    }

    @Test
    void grid_Points_StayInsideRange() {
        List<BlockCoord> points = QuarryGrid.points(HOME, 128, 512);

        assertFalse(points.isEmpty(), "Кольца поиска 128..512 не могут быть пустыми: иначе место не найти");
        for (BlockCoord point : points) {
            int distance = Math.abs(point.x() - HOME.x()) + Math.abs(point.z() - HOME.z());
            assertTrue(
                    distance >= 128 && distance <= 512,
                    "Точка " + point + " на расстоянии " + distance + " вне диапазона 128..512: поиск обязан "
                            + "уважать min и max");
        }
    }

    @Test
    void sites_NoMarker_NoEventAndNoWorldTag() {
        QuarrySites.Feed feed = QuarrySites.feed(List.of(), HOME);

        assertTrue(feed.events().isEmpty(), "Без установленного места граф не получает события: мод не выбирает сам");
        assertTrue(feed.world().isEmpty(), "Без места нет и тега мира: добыча не начинается сама");
        assertTrue(feed.nearest().isEmpty(), "Без места нет точки для добычи");
    }

    @Test
    void sites_Marker_GivesEventWorldAndPath() {
        RouteMarker marker = new RouteMarker(RouteResource.DIRT, SITE, true, false, 4, "minecraft:plains", 164, 0);

        QuarrySites.Feed feed = QuarrySites.feed(List.of(marker), HOME);

        assertEquals(
                Set.of("biome_found:plains"),
                feed.events(),
                "Установленное место даёт графу событие «место есть»: маршрут строит граф, а не мод");
        assertEquals(
                Set.of("biome:plains"),
                feed.world(),
                "Тег мира появляется только у установленного места: без него граф не добывает");
        assertEquals(
                new BlockCoord(40, 64, 20),
                feed.nearest().get(GraphNames.SOIL),
                "Точка земли для добычи берётся из места, а не из ближайшего дёрна");
        assertEquals(60, feed.distances().get(GraphNames.SOIL), "Путь до места — 60 блоков по тропе углом");
    }
}
