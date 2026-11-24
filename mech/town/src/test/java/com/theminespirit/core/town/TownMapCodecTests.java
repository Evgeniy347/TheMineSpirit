package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TownMapCodecTests {
    private static final ColumnMask REGION = ColumnMask.rect(0, 0, 199, 199);

    @Test
    void codec_FullMap_RoundTrips() {
        TownMap map = TownMapFixtures.small();

        TownMap back = TownMapCodec.decode(TownMapCodec.encode(map, 7, REGION));

        assertEquals(
                map,
                back,
                "JSON карты читается обратно один в один: участки, улицы и стена — единственный источник плана; "
                        + "потеря поля на круге выгрузка→загрузка теряет участок или улицу, иначе стратег правит "
                        + "несуществующее"
                        + " При смене codec_FullMap_RoundTrips проверь это утверждение.");
    }

    @Test
    void codec_RingStreet_StaysWithoutCoordinates() {
        TownMap map = TownMapFixtures.small();
        String json = TownMapCodec.encode(map, 3, REGION);

        assertTrue(
                json.contains("\"kind\" : \"ring\""),
                "Кольцевая улица уходит в JSON только видом: её полоса считается от стены и контура, "
                        + "а координаты прямоугольника для кривого берега бессмысленны; без этого поля "
                        + "при загрузке кольцо станет пустым"
                        + " Иначе тест codec_RingStreet_StaysWithoutCoordinates перестанет ловить эту поломку. При смене codec_RingStreet_StaysWithoutCoordinates проверь это утверждение.");
        assertTrue(
                json.contains("\"revision\" : 3"),
                "Версия плана обязана попадать в JSON: по ней клиент не затрёт чужие правки; "
                        + "без ревизии две правки подряд затирают друг друга молча"
                        + " Иначе тест codec_RingStreet_StaysWithoutCoordinates перестанет ловить эту поломку. При смене codec_RingStreet_StaysWithoutCoordinates проверь это утверждение.");
    }

    @Test
    void codec_BrokenJson_NamesTheError() {
        IllegalArgumentException broken = assertThrows(
                IllegalArgumentException.class,
                () -> TownMapCodec.decode("{\"plots\": 5}"),
                "Битый JSON обязан отклоняться русской ошибкой: иначе стратег получает пустой план вместо "
                        + "диагноза и публикует его в мир; сообщение обязано сказать, что именно не читается"
                        + " Иначе тест codec_BrokenJson_NamesTheError перестанет ловить эту поломку. При смене codec_BrokenJson_NamesTheError проверь это утверждение.");
        assertTrue(
                broken.getMessage().startsWith("Карта поселения не читается"),
                "Текст ошибки начинается с «Карта поселения не читается»: по началу видно, что сломан JSON, "
                        + "а не валидация; получено: " + broken.getMessage()
                        + " Иначе тест codec_BrokenJson_NamesTheError перестанет ловить эту поломку. При смене codec_BrokenJson_NamesTheError проверь это утверждение.");
    }

    @Test
    void codec_ExtraFields_Ignored() {
        String json = "{\"anchor\":{\"x\":0,\"z\":0},\"plots\":[],\"streets\":[],\"wall\":[],\"note\":\"проба\"}";

        TownMap map = TownMapCodec.decode(json);

        assertEquals(
                List.of(),
                map.plots(),
                "Лишние поля в JSON не ломают загрузку: стратег может приложить комментарий, "
                        + "и карта всё равно принимается; иначе любой заметкой в файле ломается применение"
                        + " При смене codec_ExtraFields_Ignored проверь это утверждение.");
    }
}
