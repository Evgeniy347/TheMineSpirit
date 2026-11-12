package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TickCacheTests {
    @Test
    void get_SameKeyWithinTtl_ComputesOnce() {
        TickCache<Integer> cache = new TickCache<>(600);
        AtomicInteger scans = new AtomicInteger();
        cache.get("home", 100, scans::incrementAndGet);
        cache.get("home", 699, scans::incrementAndGet);

        assertEquals(
                1,
                scans.get(),
                "скан окрестности (65×65×17 блоков) не повторяется на каждой пересборке доски: окрестность меняется"
                        + " медленно (сервер 12.11: graph.around ≈150 мс каждые несколько секунд). Сломается: тик"
                        + " снова держит доска. Проверить при смене: GraphSurroundings.SCAN_TTL_TICKS");
    }

    @Test
    void get_KeyChangedOrExpired_Recomputes() {
        TickCache<Integer> cache = new TickCache<>(600);
        AtomicInteger scans = new AtomicInteger();
        cache.get("home", 100, scans::incrementAndGet);
        cache.get("moved", 101, scans::incrementAndGet);
        cache.get("moved", 701, scans::incrementAndGet);

        assertEquals(
                3,
                scans.get(),
                "новый дом (ключ) или истёкший срок — скан заново: иначе граф видит окрестность старого места и"
                        + " шлёт духов к несуществующим ресурсам. Проверить при смене: ключ в GraphSurroundings.of");
    }
}
