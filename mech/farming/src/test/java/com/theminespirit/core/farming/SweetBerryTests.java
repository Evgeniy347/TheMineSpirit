package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.farming.api.HarvestMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SweetBerryTests {
    @Test
    void harvestMethod_SweetBerry_IsPick() {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        assertEquals(
                HarvestMethod.PICK,
                kind.harvestMethod(),
                "Сладкая ягода обязана собираться PICK (правый клик без ломки): иначе дух ломает куст и теряет "
                        + "урожай, а куст растёт заново. При смене таблицы культур проверь harvestMethod_Wheat_IsBreak "
                        + "и живой сбор ягод правым кликом");
    }

    @ParameterizedTest(name = "age {0} -> mature {1}")
    @CsvSource({"0,false", "1,false", "2,true", "3,true"})
    void isMature_SweetBerry_AgeTable(int age, boolean mature) {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        CropState state = new CropState(kind, age);
        assertEquals(
                mature,
                state.isMature(),
                "Куст ягоды зреет с 2: при age=" + age + " mature=" + mature
                        + ", иначе harvest соберёт недозрелое или пропустит зрелое и цикл фермы встанет. При смене "
                        + "age-таблицы CropState.isMature проверь все четыре возраста этой строки и живой куст");
    }

    @Test
    void eligible_SweetBerryMature_IsHarvestable() {
        CropKind kind = CropKind.parse("sweet_berry_bush");
        FarmTile mature = new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, 3)));
        FarmTile young = new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, 1)));
        assertTrue(
                FarmJob.HARVEST.eligible(mature, kind, true),
                "Зрелый куст (age 3) обязан быть целью HARVEST: иначе дух не собирает ягоды и урожай гниёт на "
                        + "кусте. При смене FarmJob.HARVEST.eligible проверь соседний young в этом тесте");
        assertFalse(
                FarmJob.HARVEST.eligible(young, kind, true),
                "Недозрелый куст (age 1) не цель HARVEST: иначе дух собирает пустой куст и тратит цикл фермы "
                        + "впустую. При смене FarmJob.HARVEST.eligible проверь зрелый куст рядом в этом тесте");
    }

    @Test
    void harvestMethod_Wheat_IsBreak() {
        assertEquals(
                HarvestMethod.BREAK,
                CropKind.WHEAT.harvestMethod(),
                "Пшеница обязана собираться BREAK: иначе таблица культур врёт о способе сбора и дух жмёт правый "
                        + "клик по колосьям без урожая. При смене CropKind.WHEAT.harvestMethod проверь строку "
                        + "sweet_berry выше и живой сбор пшеницы");
    }
}
