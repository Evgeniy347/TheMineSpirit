package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.api.CropKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantCropParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        PlantCropParams params = PlantCropParams.from(Map.of("crop", "wheat", "radius", 8, "count", 64));

        assertEquals(
                new PlantCropParams(CropKind.WHEAT, 8, 64),
                params,
                "plant_crop обязан сохранить вид, радиус и число: по ним адаптер берёт семена и ищет грядки"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_MissingCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("radius", 8, "count", 64)),
                "plant_crop без crop обязан отказывать: вид обязателен, без него неизвестно, какие семена тратить"
                        + " Иначе тест from_MissingCrop_IsRejected перестанет ловить эту поломку. При смене from_MissingCrop_IsRejected проверь это утверждение.");
    }

    @Test
    void from_AnyCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "any", "radius", 8, "count", 64)),
                "plant_crop с crop=any обязан отказывать: «посади что-нибудь» не говорит, какие семена брать"
                        + " Иначе тест from_AnyCrop_IsRejected перестанет ловить эту поломку. При смене from_AnyCrop_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnknownCrop_IsRejectedWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "rice", "radius", 8, "count", 64)),
                "plant_crop с crop=rice обязан отказывать: риса нет в видах CropKind"
                        + " Иначе тест from_UnknownCrop_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownCrop_IsRejectedWithAllowedList проверь это утверждение.");

        assertEquals(
                true,
                error.getMessage().contains("wheat"),
                "Отказ обязан перечислять допустимые виды культур. Фактическое: " + error.getMessage()
                        + " Иначе тест from_UnknownCrop_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownCrop_IsRejectedWithAllowedList проверь это утверждение.");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "carrot", "radius", 8)),
                "plant_crop без count обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг"
                        + " Иначе тест from_MissingCount_IsRejected перестанет ловить эту поломку. При смене from_MissingCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusAboveFarmLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "wheat", "radius", 17, "count", 64)),
                "radius больше 16 обязан отвергаться: фермерский поиск шире грядки кладёт серверный тик"
                        + " Иначе тест from_RadiusAboveFarmLimit_IsRejected перестанет ловить эту поломку. При смене from_RadiusAboveFarmLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> PlantCropParams.from(null),
                "PlantCropParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
