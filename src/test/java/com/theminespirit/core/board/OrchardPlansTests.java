package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrchardPlansTests {
    private static final WoodRules RULES = new WoodRules(24, 5, 200);

    @Test
    void plan_HarvestOrchard_ChopsTrunkAndReplantsSameSpot() {
        BlockCoord trunk = new BlockCoord(-170, 70, -501);
        ScriptCall call = (ScriptCall) TicketPlans.plan(BoardCase.HARVEST_ORCHARD, 1, TicketTarget.at(trunk));
        assertEquals(
                "chop_target",
                call.script(),
                "Зрелое дерево сада рубится целиком по координате ствола: поиск по виду унёс бы другое дерево");
        assertEquals(
                Map.of("x", -170, "y", 70, "z", -501, "replant", true, "orchard", true),
                call.params(),
                "Рубка сада сажает саженец на то же место и идёт с пропуском сада: без orchard защита сада "
                        + "откажет, без replant сад пустеет");
    }

    @Test
    void mature_TrunkBelowThreshold_StaysStanding() {
        assertFalse(
                OrchardPlans.mature(4, RULES),
                "Дерево сада ниже порога зрелости не рубится: иначе сад снимается саженцами");
        assertTrue(OrchardPlans.mature(5, RULES), "Дерево с порогом брёвен ствола зрелое: его рубят и сажают снова");
    }

    @Test
    void id_SameSpotDifferentHeight_SameTicket() {
        assertEquals(
                OrchardPlans.id(new BlockCoord(3, 64, 7)),
                OrchardPlans.id(new BlockCoord(3, 66, 7)),
                "Задание сада — на место из плана, а не на высоту ствола: два задания на одно дерево "
                        + "дали бы двух рубщиков на один ствол");
    }

    @Test
    void parse_ShippedWoodTable_KeepsOrchardNumbersInData() {
        WoodRules shipped = WoodRules.load();
        assertTrue(
                shipped.orchardMatureLogs() >= 1 && shipped.orchardCheckTicks() >= 1,
                "Числа сада берутся из board/wood.json (горячая перезагрузка): " + shipped);
    }
}
