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

        assertEquals(
                6.0F,
                hurt.amount(),
                "Урон на самой границе окна ещё считается свежим"
                        + " Иначе тест recent_HitAtWindowEdge_StillFresh перестанет ловить эту поломку. При смене recent_HitAtWindowEdge_StillFresh проверь это утверждение.");
        assertEquals(
                200,
                hurt.ticksAgo(),
                "Возраст урона — разница времени удара и запроса"
                        + " Иначе тест recent_HitAtWindowEdge_StillFresh перестанет ловить эту поломку. При смене recent_HitAtWindowEdge_StillFresh проверь это утверждение.");
        assertEquals(
                "minecraft:zombie",
                hurt.source(),
                "Источник урона сохраняется для диагноза графа"
                        + " Иначе тест recent_HitAtWindowEdge_StillFresh перестанет ловить эту поломку. При смене recent_HitAtWindowEdge_StillFresh проверь это утверждение.");
    }

    @Test
    void recent_HitPastWindow_Forgets() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, WINDOW);

        assertNull(
                SpiritDamageWatch.recent(spirit, 301, WINDOW),
                "Урон старше окна жизни не поднимает ранение: окно приходит из graph.json"
                        + " Иначе тест recent_HitPastWindow_Forgets перестанет ловить эту поломку. При смене recent_HitPastWindow_Forgets проверь это утверждение.");
    }

    @Test
    void recent_NarrowGraphWindow_ForgetsSooner() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, 60);

        assertNull(
                SpiritDamageWatch.recent(spirit, 161, 60),
                "Окно 60 тиков из graph.json короче встроенного 200: урон 61 тик уже не свежий"
                        + " Иначе тест recent_NarrowGraphWindow_ForgetsSooner перестанет ловить эту поломку. При смене recent_NarrowGraphWindow_ForgetsSooner проверь это утверждение.");
    }

    @Test
    void note_SecondHitWithinWindow_SumsDamage() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 4.0F, 100, WINDOW);
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 150, WINDOW);

        assertEquals(
                10.0F,
                SpiritDamageWatch.recent(spirit, 150, WINDOW).amount(),
                "Два удара внутри окна складываются: ранение — сумма свежего урона"
                        + " Иначе тест note_SecondHitWithinWindow_SumsDamage перестанет ловить эту поломку. При смене note_SecondHitWithinWindow_SumsDamage проверь это утверждение.");
    }

    @Test
    void note_SecondHitPastWindow_StartsNewSum() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 4.0F, 100, WINDOW);
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 301, WINDOW);

        assertEquals(
                6.0F,
                SpiritDamageWatch.recent(spirit, 301, WINDOW).amount(),
                "Удар после окна начинает новый счёт, а не тянет старый урон"
                        + " Иначе тест note_SecondHitPastWindow_StartsNewSum перестанет ловить эту поломку. При смене note_SecondHitPastWindow_StartsNewSum проверь это утверждение.");
    }

    @Test
    void note_ZeroDamage_IsIgnored() {
        SpiritDamageWatch.note(spirit, "minecraft:zombie", 0.0F, 100, WINDOW);

        assertNull(
                SpiritDamageWatch.recent(spirit, 100, WINDOW),
                "Нулевой урон не делает духа раненым"
                        + " Иначе тест note_ZeroDamage_IsIgnored перестанет ловить эту поломку. При смене note_ZeroDamage_IsIgnored проверь это утверждение.");
    }

    @Test
    void note_NonPositiveWindow_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritDamageWatch.note(spirit, "minecraft:zombie", 6.0F, 100, 0),
                "Окно урона берётся из формулы жизни графа и обязано быть положительным"
                        + " Иначе тест note_NonPositiveWindow_Rejected перестанет ловить эту поломку. При смене note_NonPositiveWindow_Rejected проверь это утверждение.");
    }

    @Test
    void recent_UnknownSpirit_NoHurt() {
        assertNull(
                SpiritDamageWatch.recent(UUID.randomUUID(), 100, WINDOW),
                "Дух без ударов не ранен: пустая история — не свежий урон"
                        + " Иначе тест recent_UnknownSpirit_NoHurt перестанет ловить эту поломку. При смене recent_UnknownSpirit_NoHurt проверь это утверждение.");
    }
}
