package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrovePlansTests {
    private static final WoodRules RULES = new WoodRules(24, 5, 200);

    @Test
    void plan_HarvestGrove_ChopsTrunkAndReplantsSameSpot() {
        BlockCoord trunk = new BlockCoord(-170, 70, -501);
        ScriptCall call = (ScriptCall) TicketPlans.plan(BoardCase.HARVEST_GROVE, 1, TicketTarget.at(trunk));
        assertEquals(
                "chop_target",
                call.script(),
                "Зрелое дерево рощи рубится целиком по координате ствола: поиск по виду унёс бы другое дерево");
        assertEquals(
                Map.of("x", -170, "y", 70, "z", -501, "replant", true, "grove", true),
                call.params(),
                "Рубка рощи сажает саженец на то же место и идёт с пропуском рощи: без grove защита рощи "
                        + "откажет, без replant роща пустеет");
    }

    @Test
    void mature_TrunkBelowThreshold_StaysStanding() {
        assertFalse(
                GrovePlans.mature(4, RULES),
                "Дерево рощи ниже порога зрелости не рубится: иначе роща снимается саженцами");
        assertTrue(GrovePlans.mature(5, RULES), "Дерево с порогом брёвен ствола зрелое: его рубят и сажают снова");
    }

    @Test
    void id_SameSpotDifferentHeight_SameTicket() {
        assertEquals(
                GrovePlans.id(new BlockCoord(3, 64, 7)),
                GrovePlans.id(new BlockCoord(3, 66, 7)),
                "Задание рощи — на место из плана, а не на высоту ствола: два задания на одно дерево "
                        + "дали бы двух рубщиков на один ствол");
    }

    @Test
    void parse_ShippedWoodTable_KeepsGroveNumbersInData() {
        WoodRules shipped = WoodRules.load();
        assertTrue(
                shipped.groveMatureLogs() >= 1 && shipped.groveCheckTicks() >= 1,
                "Числа рощи берутся из board/wood.json (горячая перезагрузка): " + shipped);
    }
}
