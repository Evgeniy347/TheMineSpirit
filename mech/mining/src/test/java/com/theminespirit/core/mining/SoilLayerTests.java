package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.SoilLayer.Cell;
import com.theminespirit.core.mining.SoilLayer.Ground;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SoilLayerTests {

    @Test
    void takeable_TurfOnSlope_Taken() {
        assertTrue(
                SoilLayer.takeable(new Cell(70, Ground.TURF, Ground.OPEN, Ground.SOIL, OptionalInt.empty())),
                "Дёрн склона под открытым верхом над землёй — верхний слой, его берут: иначе земли за оградой нет"
                        + " При смене takeable_TurfOnSlope_Taken проверь это утверждение.");
    }

    @Test
    void takeable_SoilUnderTakenTurf_Refused() {
        assertFalse(
                SoilLayer.takeable(new Cell(69, Ground.SOIL, Ground.OPEN, Ground.SOIL, OptionalInt.empty())),
                "Земля под снятым дёрном — второй слой: её взятие роет яму вниз, в которую духи падали (INC-73)"
                        + " Иначе тест takeable_SoilUnderTakenTurf_Refused перестанет ловить эту поломку. При смене takeable_SoilUnderTakenTurf_Refused проверь это утверждение.");
    }

    @Test
    void takeable_TurfUnderBlock_Refused() {
        assertFalse(
                SoilLayer.takeable(new Cell(70, Ground.TURF, Ground.SOIL, Ground.SOIL, OptionalInt.empty())),
                "Дёрн под другим блоком — не поверхность: добыча шла бы вглубь склона"
                        + " Иначе тест takeable_TurfUnderBlock_Refused перестанет ловить эту поломку. При смене takeable_TurfUnderBlock_Refused проверь это утверждение.");
    }

    @Test
    void takeable_TurfAtWaterLevel_Refused() {
        assertFalse(
                SoilLayer.takeable(new Cell(62, Ground.TURF, Ground.OPEN, Ground.SOIL, OptionalInt.of(62))),
                "Вода в радиусе " + SoilLayer.WATER_RADIUS + " на уровне клетки: снятая клетка зальётся — не ниже "
                        + "уровня воды"
                        + " Иначе тест takeable_TurfAtWaterLevel_Refused перестанет ловить эту поломку. При смене takeable_TurfAtWaterLevel_Refused проверь это утверждение.");
    }

    @Test
    void takeable_TurfAboveNearbyWater_Taken() {
        assertTrue(
                SoilLayer.takeable(new Cell(63, Ground.TURF, Ground.OPEN, Ground.SOIL, OptionalInt.of(62))),
                "Дёрн выше уровня воды рядом берётся: правило воды не запрещает весь берег"
                        + " Иначе тест takeable_TurfAboveNearbyWater_Taken перестанет ловить эту поломку. При смене takeable_TurfAboveNearbyWater_Taken проверь это утверждение.");
    }

    @Test
    void takeable_ThinTurfOnStone_Refused() {
        assertFalse(
                SoilLayer.takeable(new Cell(70, Ground.TURF, Ground.OPEN, Ground.STONE, OptionalInt.empty())),
                "Дёрн одним слоем на камне не берут: под добычей должна остаться земля, камень не оголяется"
                        + " Иначе тест takeable_ThinTurfOnStone_Refused перестанет ловить эту поломку. При смене takeable_ThinTurfOnStone_Refused проверь это утверждение.");
    }

    @Test
    void dirt_DirtIds_TurfGathered() {
        assertTrue(
                SoilLayer.dirt("minecraft:dirt") && SoilLayer.dirt("dirt") && !SoilLayer.dirt("sand"),
                "Земля добывается дёрном верхнего слоя: для dirt и minecraft:dirt, не для песка"
                        + " Иначе тест dirt_DirtIds_TurfGathered перестанет ловить эту поломку. При смене dirt_DirtIds_TurfGathered проверь это утверждение.");
    }

    @Test
    void takeableGranular_SandSurfaceAboveWater_Taken() {
        assertTrue(
                SoilLayer.takeableGranular(new Cell(70, Ground.SOIL, Ground.OPEN, Ground.SOIL, OptionalInt.empty())),
                "Песок открытого верхнего слоя берут: карьер снимает слой за слоем, как и землю"
                        + " Иначе тест takeableGranular_SandSurfaceAboveWater_Taken перестанет ловить эту поломку. При смене takeableGranular_SandSurfaceAboveWater_Taken проверь это утверждение.");
    }

    @Test
    void takeableGranular_SandSecondLayer_Refused() {
        assertFalse(
                SoilLayer.takeableGranular(new Cell(69, Ground.SOIL, Ground.SOIL, Ground.SOIL, OptionalInt.empty())),
                "Второй слой песка не берут: яма глубже соседей и вертикальная стенка — духи падают"
                        + " Иначе тест takeableGranular_SandSecondLayer_Refused перестанет ловить эту поломку. При смене takeableGranular_SandSecondLayer_Refused проверь это утверждение.");
    }

    @Test
    void takeableGranular_GravelAtWaterLevel_Refused() {
        assertFalse(
                SoilLayer.takeableGranular(new Cell(62, Ground.SOIL, Ground.OPEN, Ground.STONE, OptionalInt.of(62))),
                "Гравий на уровне воды не берут: карьер зальётся, как и земляной (SoilLayer.WATER_RADIUS)"
                        + " Иначе тест takeableGranular_GravelAtWaterLevel_Refused перестанет ловить эту поломку. При смене takeableGranular_GravelAtWaterLevel_Refused проверь это утверждение.");
    }
}
