package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.CropKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantCropParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        PlantCropParams params = PlantCropParams.from(Map.of("crop", "wheat", "radius", 8, "count", 64));

        assertEquals(
                new PlantCropParams(CropKind.WHEAT, 8, 64),
                params,
                "plant_crop обязан сохранить вид, радиус и число: по ним адаптер берёт семена и ищет грядки");
    }

    @Test
    void from_MissingCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("radius", 8, "count", 64)),
                "plant_crop без crop обязан отказывать: вид обязателен, без него неизвестно, какие семена тратить");
    }

    @Test
    void from_AnyCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "any", "radius", 8, "count", 64)),
                "plant_crop с crop=any обязан отказывать: «посади что-нибудь» не говорит, какие семена брать");
    }

    @Test
    void from_UnknownCrop_IsRejectedWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "rice", "radius", 8, "count", 64)),
                "plant_crop с crop=rice обязан отказывать: риса нет в видах CropKind");

        assertEquals(
                true,
                error.getMessage().contains("wheat"),
                "Отказ обязан перечислять допустимые виды культур. Фактическое: " + error.getMessage());
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "carrot", "radius", 8)),
                "plant_crop без count обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг");
    }

    @Test
    void from_RadiusAboveFarmLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlantCropParams.from(Map.of("crop", "wheat", "radius", 17, "count", 64)),
                "radius больше 16 обязан отвергаться: фермерский поиск шире грядки кладёт серверный тик");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> PlantCropParams.from(null),
                "PlantCropParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир");
    }
}
