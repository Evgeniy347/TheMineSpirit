package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageBootTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void apply_NoPlot_DefersUntilSurvey() {
        List<String> applied = new ArrayList<>();

        boolean started = StageBoot.apply(Optional.of(SITE), Optional.empty(), applied::add);

        assertFalse(started, "Без обмеренного участка гейт этапа не поставить: старт ждёт разметки плана");
        assertTrue(applied.isEmpty(), "Отложенный старт не трогает доску и духов");
    }

    @Test
    void apply_SiteAndPlot_AppliesWithPlotWood() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(0, 0, 0), -10, 10, 8, "jungle");
        List<String> applied = new ArrayList<>();

        boolean started = StageBoot.apply(Optional.of(SITE), Optional.of(plot), applied::add);

        assertTrue(started, "Место этапа и участок на месте — гейт ставится до первого тика координатора");
        assertEquals(List.of("jungle"), applied, "Старт получает породу участка: аудит этапа меряет постройки ею");
    }
}
