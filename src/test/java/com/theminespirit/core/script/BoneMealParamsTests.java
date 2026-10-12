package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.CropKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoneMealParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        BoneMealParams params = BoneMealParams.from(Map.of("crop", "carrot", "radius", 8, "count", 16));

        assertEquals(new BoneMealParams(CropKind.CARROT, 8, 16), params,
                "bone_meal обязан сохранить вид, радиус и число: по ним адаптер тратит муку на растущие посевы");
    }

    @Test
    void from_MissingCrop_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("radius", 8, "count", 16)),
                "bone_meal без crop обязан отказывать: умолчание any подставляет валидатор, а не маппинг");
    }

    @Test
    void from_UnknownCrop_IsRejectedWithAllowedList() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "melon", "radius", 8, "count", 16)),
                "bone_meal с crop=melon обязан отказывать: арбуз не входит в фермерство чеклиста");

        assertEquals(true, error.getMessage().contains("carrot"),
                "Отказ обязан перечислять допустимые виды культур. Фактическое: " + error.getMessage());
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "any", "radius", 8)),
                "bone_meal без count обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг");
    }

    @Test
    void from_CountAboveLimit_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "any", "radius", 8, "count", 257)),
                "count больше 256 обязан отвергаться: лимит ограничивает трату муки за один запуск скрипта");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> BoneMealParams.from(null),
                "BoneMealParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир");
    }
}
