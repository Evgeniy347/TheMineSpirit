package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TownMapEditsTests {
    @Test
    void removePlot_ExistingId_DropsOnlyIt() {
        TownMap source = TownMapFixtures.small();

        TownMap edited = TownMapEdits.removePlot(source, "hut").orElseThrow();

        assertEquals(
                1,
                edited.plots().size(),
                "Удаление по имени убирает ровно один участок: иначе с плана пропадают чужие дома; получено "
                        + edited.plots()
                        + " Иначе тест removePlot_ExistingId_DropsOnlyIt перестанет ловить эту поломку. При смене "
                        + "removePlot_ExistingId_DropsOnlyIt проверь число участков");
        assertEquals(
                "family_house_a",
                edited.plots().get(0).id(),
                "Остальные участки и их координаты не меняются: иначе удаление одного объекта двигает соседей; "
                        + "получено " + edited.plots().get(0)
                        + " Иначе тест removePlot_ExistingId_DropsOnlyIt перестанет ловить эту поломку. При смене "
                        + "removePlot_ExistingId_DropsOnlyIt проверь id");
        assertEquals(
                source.streets(),
                edited.streets(),
                "Улицы при удалении участка не трогаются: иначе вместе с домом исчезает проезд; получено "
                        + edited.streets()
                        + " Иначе тест removePlot_ExistingId_DropsOnlyIt перестанет ловить эту поломку. При смене "
                        + "removePlot_ExistingId_DropsOnlyIt проверь улицы");
    }

    @Test
    void removePlot_UnknownId_KeepsMap() {
        TownMap source = TownMapFixtures.small();

        assertTrue(
                TownMapEdits.removePlot(source, "нет-такого").isEmpty(),
                "Неизвестное имя не создаёт новую карту: иначе опечатка молча применяет старый план; получено "
                        + source.plots()
                        + " Иначе тест removePlot_UnknownId_KeepsMap перестанет ловить эту поломку. При смене "
                        + "removePlot_UnknownId_KeepsMap проверь пустой результат");
    }
}
