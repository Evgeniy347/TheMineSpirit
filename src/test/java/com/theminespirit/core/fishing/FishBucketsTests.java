package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class FishBucketsTests {

    @Test
    void bucketItem_FourVanillaFish_MapsToBucket() {
        assertEquals(
                Optional.of("minecraft:cod_bucket"),
                FishBuckets.bucketItem("minecraft:cod"),
                "Треска садится в ведро трески: по паре вид → ведро строится задача ловли вёдрами");
        assertEquals(
                Optional.of("minecraft:tropical_fish_bucket"),
                FishBuckets.bucketItem("minecraft:tropical_fish"),
                "Тропическая рыба садится в своё ведро: вид не путается с треской");
    }

    @Test
    void isBucketable_Squid_False() {
        assertFalse(
                FishBuckets.isBucketable("minecraft:squid"),
                "Кальмара ведром не берут: список видов закрыт ванилью и не расширяется наугад");
        assertTrue(
                FishBuckets.isBucketable("minecraft:pufferfish"),
                "Иглобрюх берётся ведром: это один из четырёх видов ведёрной рыбы");
    }

    @Test
    void bucketItem_UnknownEntity_Empty() {
        assertEquals(
                Optional.empty(),
                FishBuckets.bucketItem("minecraft:cow"),
                "Неизвестный вид ведра не даёт: задача отказывает, а не подставляет предмет молча");
    }
}
