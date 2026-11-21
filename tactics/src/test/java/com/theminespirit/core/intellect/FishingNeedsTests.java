package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.fishing.FishingData;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingNeedsTests {
    private static final FishingData DATA = new FishingData(2, 1);

    @Test
    void cookableFish_CodSalmonAndJunk_CountsOnlyCookableFish() {
        assertEquals(
                5,
                FishingNeeds.cookableFish(Map.of(
                        "minecraft:cod",
                        3,
                        "minecraft:salmon",
                        2,
                        "minecraft:pufferfish",
                        4,
                        "minecraft:tropical_fish",
                        7,
                        "minecraft:stick",
                        9)),
                "В счёт коптильни идут только треска и лосось: иглобрюх и тропическая — на склад, " + "а не на жарку"
                        + " Иначе тест cookableFish_CodSalmonAndJunk_CountsOnlyCookableFish перестанет ловить эту поломку. При смене cookableFish_CodSalmonAndJunk_CountsOnlyCookableFish проверь это утверждение.");
    }

    @Test
    void cookableFish_ShortIds_AreCountedToo() {
        assertEquals(
                4,
                FishingNeeds.cookableFish(Map.of("cod", 2, "salmon", 2)),
                "Короткие ключи склада (cod, salmon) считаются наравне с полными: иначе гейт "
                        + "недооценит улов и коптильня не появится"
                        + " При смене cookableFish_ShortIds_AreCountedToo проверь это утверждение.");
    }

    @Test
    void wantsSmoker_BelowThreshold_False() {
        assertFalse(
                FishingNeeds.wantsSmoker(Map.of("minecraft:cod", 15), DATA),
                "Пока рыбы меньше порога из данных, коптильню не крафтят: иначе лишняя постройка"
                        + " При смене wantsSmoker_BelowThreshold_False проверь это утверждение.");
    }

    @Test
    void wantsSmoker_AtThreshold_True() {
        assertTrue(
                FishingNeeds.wantsSmoker(Map.of("minecraft:cod", 16), DATA),
                "На пороге из данных коптильня заказывается: улов накопился и требует переработки"
                        + " Иначе тест wantsSmoker_AtThreshold_True перестанет ловить эту поломку. При смене wantsSmoker_AtThreshold_True проверь это утверждение.");
    }

    @Test
    void wantsSmoker_PufferfishOnly_False() {
        assertFalse(
                FishingNeeds.wantsSmoker(Map.of("minecraft:pufferfish", 64), DATA),
                "Иглобрюх коптильню не заказывает: он не жарится, порог считается по годной рыбе"
                        + " Иначе тест wantsSmoker_PufferfishOnly_False перестанет ловить эту поломку. При смене wantsSmoker_PufferfishOnly_False проверь это утверждение.");
    }

    @Test
    void foodShare_EmptyStock_FullDeficit() {
        assertEquals(
                1.0,
                FishingNeeds.foodShare(Map.of(), 1),
                1e-9,
                "Пустой склад при одной душе — полная нехватка еды: рыбалка становится постоянным занятием"
                        + " Иначе тест foodShare_EmptyStock_FullDeficit перестанет ловить эту поломку. При смене foodShare_EmptyStock_FullDeficit проверь это утверждение.");
    }

    @Test
    void foodShare_PlentyOfFood_NoDeficit() {
        assertEquals(
                0.0,
                FishingNeeds.foodShare(Map.of("minecraft:cooked_beef", 4), 1),
                1e-9,
                "Сытый склад не гонит духа на рабочую точку: рыбалка идёт только по нужде"
                        + " Иначе тест foodShare_PlentyOfFood_NoDeficit перестанет ловить эту поломку. При смене foodShare_PlentyOfFood_NoDeficit проверь это утверждение.");
    }

    @Test
    void wantsFishing_FoodShare_SplitsByZero() {
        assertFalse(
                FishingNeeds.wantsFishing(0.0),
                "При достатке еды рыбалка как постоянное занятие не идёт: дух не ловит впрок"
                        + " Иначе тест wantsFishing_FoodShare_SplitsByZero перестанет ловить эту поломку. При смене wantsFishing_FoodShare_SplitsByZero проверь это утверждение.");
        assertTrue(
                FishingNeeds.wantsFishing(0.3),
                "При нехватке еды рабочая точка у воды активна: улов закрывает дефицит"
                        + " Иначе тест wantsFishing_FoodShare_SplitsByZero перестанет ловить эту поломку. При смене wantsFishing_FoodShare_SplitsByZero проверь это утверждение.");
    }
}
