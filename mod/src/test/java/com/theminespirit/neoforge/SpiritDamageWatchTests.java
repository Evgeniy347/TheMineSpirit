package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.board.graph.GraphFacts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpiritDamageWatchTests {
    private static final long WINDOW = 200;
    private final UUID spirit = UUID.randomUUID();

    @Test
    void recent_HitAtWindowEdge_StillFresh() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, WINDOW);

        GraphFacts.Hurt hurt = SpiritDamageWatch.recent(spirit, 300, WINDOW);

        assertEquals(6.0F, hurt.amount(), "Урон на самой границе окна ещё считается свежим");
        assertEquals(200, hurt.ticksAgo(), "Возраст урона — разница времени удара и запроса");
        assertEquals("minecraft:zombie", hurt.source(), "Источник урона сохраняется для диагноза графа");
    }

    @Test
    void recent_HitPastWindow_Forgets() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, WINDOW);

        assertNull(
                SpiritDamageWatch.recent(spirit, 301, WINDOW),
                "Урон старше окна жизни не поднимает ранение: окно приходит из graph.json");
    }

    @Test
    void recent_NarrowGraphWindow_ForgetsSooner() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, 60);

        assertNull(
                SpiritDamageWatch.recent(spirit, 161, 60),
                "Окно 60 тиков из graph.json короче встроенного 200: урон 61 тик уже не свежий");
    }

    @Test
    void note_SecondHitWithinWindow_SumsDamage() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 4.0F, 100, WINDOW);
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 150, WINDOW);

        assertEquals(
                10.0F,
                SpiritDamageWatch.recent(spirit, 150, WINDOW).amount(),
                "Два удара внутри окна складываются: ранение — сумма свежего урона");
    }

    @Test
    void note_SecondHitPastWindow_StartsNewSum() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 4.0F, 100, WINDOW);
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 301, WINDOW);

        assertEquals(
                6.0F,
                SpiritDamageWatch.recent(spirit, 301, WINDOW).amount(),
                "Удар после окна начинает новый счёт, а не тянет старый урон");
    }

    @Test
    void note_ZeroDamage_IsIgnored() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 0.0F, 100, WINDOW);

        assertNull(SpiritDamageWatch.recent(spirit, 100, WINDOW), "Нулевой урон не делает духа раненым");
    }

    @Test
    void note_NonPositiveWindow_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, 0),
                "Окно урона берётся из формулы жизни графа и обязано быть положительным");
    }

    @Test
    void recent_UnknownSpirit_NoHurt() {
        assertNull(
                SpiritDamageWatch.recent(UUID.randomUUID(), 100, WINDOW),
                "Дух без ударов не ранен: пустая история — не свежий урон");
    }
}
