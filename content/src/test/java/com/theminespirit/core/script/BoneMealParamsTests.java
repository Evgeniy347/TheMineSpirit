package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.api.CropKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoneMealParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        BoneMealParams params = BoneMealParams.from(Map.of("crop", "carrot", "radius", 8, "count", 16));

        assertEquals(
                new BoneMealParams(CropKind.CARROT, 8, 16),
                params,
                "bone_meal обязан сохранить вид, радиус и число: по ним адаптер тратит муку на растущие посевы"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_MissingCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("radius", 8, "count", 16)),
                "bone_meal без crop обязан отказывать: умолчание any подставляет валидатор, а не маппинг"
                        + " Иначе тест from_MissingCrop_IsRejected перестанет ловить эту поломку. При смене from_MissingCrop_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnknownCrop_IsRejectedWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "rice", "radius", 8, "count", 16)),
                "bone_meal с crop=rice обязан отказывать: риса нет в видах CropKind"
                        + " Иначе тест from_UnknownCrop_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownCrop_IsRejectedWithAllowedList проверь это утверждение.");

        assertEquals(
                true,
                error.getMessage().contains("carrot"),
                "Отказ обязан перечислять допустимые виды культур. Фактическое: " + error.getMessage()
                        + " Иначе тест from_UnknownCrop_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownCrop_IsRejectedWithAllowedList проверь это утверждение.");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "any", "radius", 8)),
                "bone_meal без count обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг"
                        + " Иначе тест from_MissingCount_IsRejected перестанет ловить эту поломку. При смене from_MissingCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BoneMealParams.from(Map.of("crop", "any", "radius", 8, "count", 257)),
                "count больше 256 обязан отвергаться: лимит ограничивает трату муки за один запуск скрипта"
                        + " Иначе тест from_CountAboveLimit_IsRejected перестанет ловить эту поломку. При смене from_CountAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> BoneMealParams.from(null),
                "BoneMealParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
