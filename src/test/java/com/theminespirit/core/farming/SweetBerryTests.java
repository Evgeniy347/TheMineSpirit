package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SweetBerryTests {
    @Test
    void harvestMethod_SweetBerry_IsPick() {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        assertEquals(HarvestMethod.PICK, kind.harvestMethod(),
                "Сладкая ягода обязана собираться PICK (правый клик без ломки): иначе дух ломает куст и теряет урожай");
    }

    @ParameterizedTest(name = "age {0} -> mature {1}")
    @CsvSource({"0,false", "1,false", "2,true", "3,true"})
    void isMature_SweetBerry_AgeTable(int age, boolean mature) {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        CropState state = new CropState(kind, age);
        assertEquals(mature, state.isMature(),
                "Куст ягоды зреет с 2: при age=" + age + " mature=" + mature
                        + ", иначе harvest соберёт недозрелое или пропустит зрелое");
    }

    @Test
    void eligible_SweetBerryMature_IsHarvestable() {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        FarmTile mature = new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, 3)));
        FarmTile young = new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, 1)));
        assertTrue(FarmJob.HARVEST.eligible(mature, kind, true),
                "Зрелый куст (age 3) обязан быть целью HARVEST: иначе дух не собирает ягоды");
        assertFalse(FarmJob.HARVEST.eligible(young, kind, true),
                "Недозрелый куст (age 1) не цель HARVEST: иначе дух собирает пустой куст");
    }

    @Test
    void harvestMethod_Wheat_IsBreak() {
        assertEquals(HarvestMethod.BREAK, CropKind.WHEAT.harvestMethod(),
                "Пшеница обязана собираться BREAK: иначе таблица культур врёт о способе сбора");
    }
}
