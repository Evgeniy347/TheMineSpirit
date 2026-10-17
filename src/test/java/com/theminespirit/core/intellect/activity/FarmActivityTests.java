package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import org.junit.jupiter.api.Test;

class FarmActivityTests {
    private static final FarmActivity FARM = new FarmActivity();

    @Test
    void demand_HungryWithoutRipeCrops_Zero() {
        SettlementView hungry = SettlementViews.settlementWithFarm(0.5, 0.5, 64, 32, 2, 0, 0.2, FarmWork.NONE);

        assertEquals(0.0, FARM.demand(hungry),
                "Ферма без зрелых посевов не востребована: иначе голодный дух идёт на harvest_crop, где делать "
                        + "нечего, и падает «зрелых посевов в радиусе 8 нет». Перепроверь FarmWork.hasHarvest");
    }

    @Test
    void demand_HungryWithFarmlandAndSeedsOnly_Zero() {
        SettlementView hungry = SettlementViews.settlementWithFarm(0.5, 0.5, 64, 32, 2, 0, 0.2,
                new FarmWork(0, 20, 30));

        assertEquals(0.0, FARM.demand(hungry),
                "Пашня и семена без зрелых посевов — не работа текущего FARM (он запускает только harvest_crop): "
                        + "спрос остаётся нулевым, иначе повторяется провал уборки. Посадка — отдельная деятельность");
    }

    @Test
    void demand_HungryWithRipeCrops_AboveChopAndRest() {
        SettlementView hungry = SettlementViews.settlementWithFarm(0.5, 0.5, 64, 32, 2, 0, 0.2,
                new FarmWork(4, 0, 0));
        double farm = FARM.demand(hungry);

        assertTrue(farm > new ChopActivity().demand(hungry),
                "При голоде и зрелых посевах ферма выше рубки: иначе духи рубят лес, пока урожай гниёт");
        assertTrue(farm > new RestActivity().demand(hungry),
                "При голоде и зрелых посевах ферма выше отдыха: еда важнее простоя");
    }

    @Test
    void demand_SatedWithRipeCrops_Zero() {
        SettlementView sated = SettlementViews.settlementWithFarm(2, 2, 64, 32, 2, 0, 0.2, new FarmWork(4, 0, 0));

        assertEquals(0.0, FARM.demand(sated),
                "При запасе выше нормы ферма не востребована: иначе духи собирают урожай ради лишней еды");
    }

    @Test
    void demand_MoreDeficit_Higher() {
        SettlementView low = SettlementViews.settlementWithFarm(1, 1, 64, 32, 2, 0, 0.2, new FarmWork(4, 0, 0));
        SettlementView high = SettlementViews.settlementWithFarm(1, 0.5, 64, 32, 2, 0, 0.2, new FarmWork(4, 0, 0));

        assertTrue(FARM.demand(high) > FARM.demand(low),
                "Чем больше дефицит еды, тем выше ферма: иначе спрос не отличает лёгкий голод от сильного");
    }

    @Test
    void demand_SmallDeficit_AboveChop() {
        SettlementView hungry = SettlementViews.settlementWithFarm(1, 1, 64, 32, 2, 0, 0.2, new FarmWork(4, 0, 0));

        assertTrue(FARM.demand(hungry) > new ChopActivity().demand(hungry),
                "Даже небольшой дефицит еды важнее рубки: иначе поселение с одной буханкой рубит лес вместо урожая");
    }

    @Test
    void demand_EmptyStock_MaxDemand() {
        SettlementView empty = SettlementViews.settlementWithFarm(0, 0, 64, 32, 2, 0, 0.2, new FarmWork(4, 0, 0));

        assertEquals(9.0, FARM.demand(empty),
                "Пустой склад при населении 3 даёт максимальный пищевой спрос 9: число — в таблице IntellectTuning");
    }

    @Test
    void demand_NullView_Throws() {
        assertThrows(IllegalArgumentException.class, () -> FARM.demand(null),
                "Спрос фермы без обстановки не считается: зависит от еды на душу и работы на грядках");
    }

    @Test
    void farmWork_NegativeCount_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new FarmWork(-1, 0, 0),
                "Отрицательная работа фермы не описывается: снимок поселения исказит востребованность");
    }
}
