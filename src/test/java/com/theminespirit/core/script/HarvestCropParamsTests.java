package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.CropKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HarvestCropParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        HarvestCropParams params = HarvestCropParams.from(Map.of("crop", "wheat", "radius", 8, "replant", true));

        assertEquals(new HarvestCropParams(CropKind.WHEAT, 8, true), params,
                "harvest_crop обязан сохранить вид, радиус и пересадку: по ним адаптер ищет зрелое и держит грядку");
    }

    @Test
    void from_AnyCrop_IsAccepted() {
        HarvestCropParams params = HarvestCropParams.from(Map.of("crop", "any", "radius", 8, "replant", false));

        assertEquals(new HarvestCropParams(CropKind.ANY, 8, false), params,
                "harvest_crop с crop=any собирает любой зрелый посев: фильтр «любой вид» здесь законен");
    }

    @Test
    void from_MissingCrop_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HarvestCropParams.from(Map.of("radius", 8, "replant", true)),
                "harvest_crop без crop обязан отказывать: умолчание any подставляет валидатор, а не маппинг");
    }

    @Test
    void from_MissingReplant_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HarvestCropParams.from(Map.of("crop", "wheat", "radius", 8)),
                "harvest_crop без replant обязан отказывать: умолчание true подставляет валидатор, а не маппинг");
    }

    @Test
    void from_StringReplant_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HarvestCropParams.from(Map.of("crop", "wheat", "radius", 8, "replant", "yes")),
                "Строка вместо boolean обязана отвергаться: скрипт читает значение через requireBoolean");
    }

    @Test
    void from_RadiusAboveFarmLimit_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HarvestCropParams.from(Map.of("crop", "any", "radius", 17, "replant", true)),
                "radius больше 16 обязан отвергаться: фермерский поиск шире грядки кладёт серверный тик");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> HarvestCropParams.from(null),
                "HarvestCropParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир");
    }
}
