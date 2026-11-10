package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HomeCacheTests {
    private static final BlockCoord CENTER = new BlockCoord(-171, 71, -489);
    private static final BlockCoord STAND = new BlockCoord(-169, 70, -490);
    private static final HomeCache.Key SETTLEMENT = new HomeCache.Key(CENTER, null);

    @Test
    void resolve_SameKeySameTick_ComputesOnce() {
        HomeCache cache = new HomeCache();
        AtomicInteger calls = new AtomicInteger();

        cache.resolve(100, SETTLEMENT, () -> {
            calls.incrementAndGet();
            return Optional.of(STAND);
        });
        Optional<BlockCoord> second = cache.resolve(100, SETTLEMENT, () -> {
            calls.incrementAndGet();
            return Optional.of(new BlockCoord(0, 0, 0));
        });

        assertEquals(
                1,
                calls.get(),
                "За один тик дом считается один раз: иначе BFS проходимости (9 мс) "
                        + "повторяется на каждом запросе цели внутри одного пересбора");
        assertEquals(Optional.of(STAND), second, "Повторный запрос в том же тике получает ту же стоянку");
    }

    @Test
    void resolve_NextTick_Recomputes() {
        HomeCache cache = new HomeCache();
        AtomicInteger calls = new AtomicInteger();

        cache.resolve(100, SETTLEMENT, () -> {
            calls.incrementAndGet();
            return Optional.of(STAND);
        });
        Optional<BlockCoord> next = cache.resolve(101, SETTLEMENT, () -> {
            calls.incrementAndGet();
            return Optional.of(new BlockCoord(-170, 70, -494));
        });

        assertEquals(2, calls.get(), "Новый тик пересчитывает дом: духи и мир могли измениться");
        assertEquals(Optional.of(new BlockCoord(-170, 70, -494)), next, "Новый тик возвращает свежую стоянку");
    }

    @Test
    void resolve_NoStand_RememberedWithinTick() {
        HomeCache cache = new HomeCache();
        AtomicInteger calls = new AtomicInteger();

        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(
                    cache.resolve(100, SETTLEMENT, () -> {
                                calls.incrementAndGet();
                                return Optional.empty();
                            })
                            .isEmpty(),
                    "Нет стоянки — нет дома: выдуманную точку не подставляем");
        }

        assertEquals(
                1,
                calls.get(),
                "Пустой ответ тоже кэшируется на тик: иначе пересбор без дома снова и снова "
                        + "ходит по миру в поисках стоянки");
    }

    @Test
    void resolve_DifferentSpirits_KeepSeparateHomes() {
        HomeCache cache = new HomeCache();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Optional<BlockCoord> one = cache.resolve(100, new HomeCache.Key(CENTER, first), () -> Optional.of(STAND));
        Optional<BlockCoord> two = cache.resolve(
                100, new HomeCache.Key(CENTER, second), () -> Optional.of(new BlockCoord(-173, 70, -489)));

        assertEquals(Optional.of(STAND), one, "Дом первого духа не подменяется домом второго");
        assertEquals(
                Optional.of(new BlockCoord(-173, 70, -489)),
                two,
                "У каждого духа своя стоянка: общий кэш по центру смешал бы дома разных духов");
    }
}
