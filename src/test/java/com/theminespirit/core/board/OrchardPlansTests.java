package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
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
    void plan_ExtraOrchardBuilding_BuildsOnItsPlotWithItsSpecies() {
        StageSite site = TownFixtures.site(TownFixtures.planWithOrchard("orchard_jungle"));
        OrchardPlot plot = site.extraOrchard("orchard_jungle").orElseThrow();
        BlockCoord origin = plot.origin(site.anchor().y());
        TicketTarget target = TicketTarget.at(origin)
                .withWork(new StageWork(site, List.of()))
                .withWood(plot.wood())
                .withBuilding("orchard_jungle");

        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target);
        ScriptCall build = calls(plan).stream()
                .filter(call -> "build_schematic".equals(call.script()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В задании сада нет постройки по схеме: " + plan));
        assertEquals(origin.x(), build.params().get("x"), "Схема сада ставится на место своей породы");
        assertEquals(origin.z(), build.params().get("z"), "Схема сада ставится на место своей породы");
        assertEquals(
                WoodSpecies.OAK.equals(plot.wood()) ? "oak" : plot.wood(),
                build.params().get("wood"),
                "Порода схемы — порода постройки графа, а не порода поселения");
        assertFalse(
                calls(plan).stream().anyMatch(call -> "chop_tree".equals(call.script())),
                "Дополнительный сад не блокируется рубкой ради необязательных саженцев: сначала стройка "
                        + "дорожек и факелов. План: " + calls(plan));
    }

    @Test
    void plan_OrchardTorches_CraftedNotGatheredFromWorld() {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));

        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target));

        assertFalse(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:torch".equals(call.params().get("item"))),
                "Факелы сада не добываются из мира: acquire уходил в сбор и падал «без стоянки: 118», "
                        + "а факелы надо крафтить из угля и палки");
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "craft".equals(call.script())
                                && "minecraft:torch".equals(call.params().get("item"))),
                "Сад обязан планировать крафт факелов: без этого стройка открывается без ресурсов");
    }

    private static List<ScriptCall> calls(TaskNode plan) {
        List<ScriptCall> out = new ArrayList<>();
        if (plan instanceof ScriptCall call) {
            out.add(call);
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> out.addAll(calls(step)));
        }
        return out;
    }

    @Test
    void parse_ShippedWoodTable_KeepsOrchardNumbersInData() {
        WoodRules shipped = WoodRules.load();
        assertTrue(
                shipped.orchardMatureLogs() >= 1 && shipped.orchardCheckTicks() >= 1,
                "Числа сада берутся из board/wood.json (горячая перезагрузка): " + shipped);
    }
}
