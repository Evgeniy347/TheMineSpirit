package com.theminespirit.neoforge.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TickEntityCacheTests {
    private static final UUID ID = new UUID(0, 1);

    @Test
    void get_RepeatedWithinTick_LoadsOnce() {
        TickEntityCache<String> cache = new TickEntityCache<>();
        AtomicInteger loads = new AtomicInteger();

        cache.get(ID, 100, id -> {
            loads.incrementAndGet();
            return "spirit";
        });
        cache.get(ID, 100, id -> {
            loads.incrementAndGet();
            return "spirit";
        });

        assertEquals(
                1,
                loads.get(),
                "Повторное чтение REST внутри тика не сканирует уровни: сверка сущности "
                        + "с уровнем кэшируется на тик");
    }

    @Test
    void get_NextTick_ReloadsEntity() {
        TickEntityCache<String> cache = new TickEntityCache<>();
        AtomicInteger loads = new AtomicInteger();

        cache.get(ID, 100, id -> "old");
        String next = cache.get(ID, 101, id -> {
                    loads.incrementAndGet();
                    return "new";
                })
                .orElseThrow();

        assertEquals("new", next, "Новый тик обязан перечитать сущность: иначе REST отдаёт позицию прошлого тика");
        assertEquals(1, loads.get(), "Кэш живёт ровно один тик");
    }

    @Test
    void get_MissingEntity_CachesEmptyWithinTick() {
        TickEntityCache<String> cache = new TickEntityCache<>();
        AtomicInteger loads = new AtomicInteger();

        assertTrue(
                cache.get(ID, 5, id -> {
                            loads.incrementAndGet();
                            return null;
                        })
                        .isEmpty(),
                "Нет сущности — пусто");
        assertTrue(
                cache.get(ID, 5, id -> {
                            loads.incrementAndGet();
                            return "late";
                        })
                        .isEmpty(),
                "Отсутствие тоже кэшируется на тик: повторный REST не ищет сущность заново");
        assertEquals(1, loads.get(), "Промах не перечитывается внутри тика");
    }
}
