package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DispatchPickTests {
    @Test
    void localWork_LocalScripts_DoNotRequireGlobalPath() {
        assertTrue(
                DispatchPick.localWork(BoardCase.CRAFT_STONE_SHOVEL),
                "Инструмент тоже крафтится локально после дистанционного acquire: иначе путь к общему "
                        + "двору не даёт заменить сломанную лопату. При смене "
                        + "localWork_LocalScripts_DoNotRequireGlobalPath проверь ToolCraftPlan.");
        assertTrue(
                DispatchPick.localWork(BoardCase.ACTIVITY_SENSE),
                "Локальный scan не требует пути к дому: иначе простаивающий дух снова ждёт "
                        + "недостижимую общую точку вместо полезной работы рядом. При смене "
                        + "localWork_LocalScripts_DoNotRequireGlobalPath проверь GraphMoreFarmPost.sense.");
        assertFalse(
                DispatchPick.localWork(BoardCase.STOCK_CHESTS),
                "Сундуки строятся по месту и требуют пути: иначе их начнут ставить в любую удалённую "
                        + "точку. При смене localWork_LocalScripts_DoNotRequireGlobalPath проверь "
                        + "DispatchPick.localWork.");
    }

    @Test
    void remoteFood_SettlementDeposit_SkipsApproachButNearestDoesNot() {
        BoardTicket ticket = new BoardTicket(
                "graph:restock_food@spirit",
                BoardCase.RESTOCK_FOOD,
                Optional.empty(),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        TicketTarget remote = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withSupply("bread", 8)
                .withDeposit(new BlockCoord(11, 60, -5));

        assertTrue(
                DispatchPick.remoteFood(ticket, remote),
                "Выбор сундука поселения делает получение еды дистанционным"
                        + " Иначе тест remoteFood_SettlementDeposit_SkipsApproachButNearestDoesNot перестанет ловить эту поломку. "
                        + "При смене remoteFood_SettlementDeposit_SkipsApproachButNearestDoesNot проверь DispatchPick.remoteFood.");
        assertFalse(
                DispatchPick.remoteFood(ticket, TicketTarget.at(remote.target()).withSupply("bread", 8)),
                "Без выбранного сундука получение остаётся локальным и требует пути"
                        + " Иначе тест remoteFood_SettlementDeposit_SkipsApproachButNearestDoesNot перестанет ловить эту поломку. "
                        + "При смене remoteFood_SettlementDeposit_SkipsApproachButNearestDoesNot проверь это утверждение.");
    }

    @Test
    void needsStand_WorkWithoutLeveling_SearchesSafeApproach() {
        TicketTarget target =
                TicketTarget.at(new BlockCoord(10, 60, -5)).withWork(new StageWork(TownFixtures.flatSite(), List.of()));

        assertTrue(
                DispatchPick.needsStand(target),
                "Работа постройки без явного leveling всё равно требует безопасной стоянки: иначе путь "
                        + "проверяется к твёрдому origin и поле остаётся без исполнителя. При смене "
                        + "needsStand_WorkWithoutLeveling_SearchesSafeApproach проверь DispatchPick.standNear "
                        + "и живой waitReason wheat_field.");
    }

    @Test
    void needsStand_PlainTarget_KeepsExactPoint() {
        assertFalse(
                DispatchPick.needsStand(TicketTarget.at(new BlockCoord(10, 60, -5))),
                "Обычная активность без схемы должна идти в точную цель: иначе StandSearch уведёт духа "
                        + "от контейнера или моба. При смене needsStand_PlainTarget_KeepsExactPoint проверь "
                        + "DispatchPick.reachTarget и TicketTarget.work.");
    }

    @Test
    void reachTarget_LevelingSchematic_UsesOriginBeforeEntrance() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withScheme("wheat_field_1")
                .withLeveling(new PlotLeveling(
                        List.of(new PlotLeveling.Box(10, 60, -5, 10, 60, -5)), List.of(), PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                new BlockCoord(10, 60, -5),
                DispatchPick.reachTarget(target),
                "До выравнивания проверяется origin, а не вход схемы: первый шаг — clear_area, иначе путь "
                        + "к ещё не построенному входу в яме ложно блокирует поле; от origin доска затем ищет "
                        + "ближайшую безопасную стоянку. При смене reachTarget_LevelingSchematic_UsesOriginBeforeEntrance "
                        + "проверь StagePlans.placed, DispatchPick.standNear и живую выдачу graph:building:wheat_field");
    }

    @Test
    void reachHeight_LevelingSchematic_UsesTopOfOriginColumn() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withScheme("wheat_field_1")
                .withLeveling(new PlotLeveling(
                        List.of(
                                new PlotLeveling.Box(10, 60, -5, 10, 70, -5),
                                new PlotLeveling.Box(20, 60, -5, 20, 90, -5)),
                        List.of(),
                        PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                70,
                DispatchPick.reachHeight(target),
                "Путь к выравниваемой схеме проверяется от верха колонны origin: иначе низкая стоянка "
                        + "снова не пускает духа к верхнему слою холма. При смене "
                        + "reachHeight_LevelingSchematic_UsesTopOfOriginColumn проверь DispatchPick.standNear.");
    }

    @Test
    void reachHeight_LevelingWithoutOriginCut_KeepsOriginHeight() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withLeveling(new PlotLeveling(
                        List.of(new PlotLeveling.Box(20, 60, -5, 20, 70, -5)), List.of(), PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                60,
                DispatchPick.reachHeight(target),
                "Без срезки в колонне origin высота проверки остаётся у origin: иначе чужой холм перемещает "
                        + "цель задания. При смене reachHeight_LevelingWithoutOriginCut_KeepsOriginHeight "
                        + "проверь PlotLeveling.Box и живой wheat_field.");
    }

    @Test
    void reachTarget_SchematicBuilding_UsesEntranceInsteadOfOrigin() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5)).withScheme("wheat_field_1");

        assertEquals(
                new BlockCoord(17, 61, -3),
                DispatchPick.reachTarget(target),
                "Проверка пути ведёт к входу схемы: иначе угол постройки в рельефе ложно объявляется "
                        + "недостижимым и грядка остаётся без исполнителя. При смене "
                        + "reachTarget_SchematicBuilding_UsesEntranceInsteadOfOrigin проверь StagePlans.placed, "
                        + "Preset.entry и живой wheat_field.");
    }
}
