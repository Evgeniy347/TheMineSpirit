package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SettlementViewProperties {
    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    @Property
    void adults_AnyView_EqualsPopulationMinusChildren(@ForAll("views") SettlementView view) {
        assertEquals(view.population() - view.children(), view.adults(),
                "Взрослые — это население минус дети: иначе каталог деятельностей выберется неверно");
    }

    @Property
    void idle_AnyView_SubsetOfSpirits(@ForAll("views") SettlementView view) {
        assertTrue(view.spirits().containsAll(view.idle()),
                "Простаивающие — подмножество духов снимка: иначе работа уйдёт несуществующему духу");
        assertTrue(view.idle().containsAll(view.freeWorkers()),
                "Свободные работники — подмножество простаивающих: иначе занятый дух получит вторую работу");
    }

    @Property
    void derived_RepeatedCalls_Deterministic(@ForAll("views") SettlementView view) {
        assertEquals(view.idle(), view.idle(),
                "Список простаивающих детерминирован: иначе назначения мигают между пересборками");
        assertEquals(view.freeWorkers(), view.freeWorkers(),
                "Список свободных детерминирован: иначе назначения мигают между пересборками");
        assertEquals(view.needsFood(1, 1.5), view.needsFood(1, 1.5),
                "Нужда в еде детерминирована: иначе спрос фермы мигает между пересборками");
        assertEquals(view.needsOre(64, 32, 2), view.needsOre(64, 32, 2),
                "Нужда в руде детерминирована: иначе спрос добычи мигает между пересборками");
    }
}
