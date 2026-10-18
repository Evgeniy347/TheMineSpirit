package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingNeedsTests {
    private static final FishingData DATA = new FishingData(2, 1);

    @Test
    void cookableFish_CodSalmonAndJunk_CountsOnlyCookableFish() {
        assertEquals(5, FishingNeeds.cookableFish(Map.of(
                "minecraft:cod", 3, "minecraft:salmon", 2, "minecraft:pufferfish", 4,
                "minecraft:tropical_fish", 7, "minecraft:stick", 9)),
                "В счёт коптильни идут только треска и лосось: иглобрюх и тропическая — на склад, "
                        + "а не на жарку");
    }

    @Test
    void cookableFish_ShortIds_AreCountedToo() {
        assertEquals(4, FishingNeeds.cookableFish(Map.of("cod", 2, "salmon", 2)),
                "Короткие ключи склада (cod, salmon) считаются наравне с полными: иначе гейт "
                        + "недооценит улов и коптильня не появится");
    }

    @Test
    void wantsSmoker_BelowThreshold_False() {
        assertFalse(FishingNeeds.wantsSmoker(Map.of("minecraft:cod", 15), DATA),
                "Пока рыбы меньше порога из данных, коптильню не крафтят: иначе лишняя постройка");
    }

    @Test
    void wantsSmoker_AtThreshold_True() {
        assertTrue(FishingNeeds.wantsSmoker(Map.of("minecraft:cod", 16), DATA),
                "На пороге из данных коптильня заказывается: улов накопился и требует переработки");
    }

    @Test
    void wantsSmoker_PufferfishOnly_False() {
        assertFalse(FishingNeeds.wantsSmoker(Map.of("minecraft:pufferfish", 64), DATA),
                "Иглобрюх коптильню не заказывает: он не жарится, порог считается по годной рыбе");
    }

    @Test
    void wantsFishing_FoodShare_SplitsByZero() {
        assertFalse(FishingNeeds.wantsFishing(0.0),
                "При достатке еды рыбалка как постоянное занятие не идёт: дух не ловит впрок");
        assertTrue(FishingNeeds.wantsFishing(0.3),
                "При нехватке еды рабочая точка у воды активна: улов закрывает дефицит");
    }
}
