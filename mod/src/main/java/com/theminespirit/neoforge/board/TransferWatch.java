package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.TransferTicket;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.core.social.TransferSource;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class TransferWatch {
    static final String TABLE_ITEM = "minecraft:crafting_table";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardCase> BUILDS =
            Set.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.REPAIR_BUILDING);

    private TransferWatch() {}

    public static boolean ensureTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> all,
            StockView stock,
            BoardTicket craftTicket,
            SpiritEntity crafter,
            long now) {
        dropStuckGivers(level, service, craftTicket.id(), now);
        List<SpiritEntity> spirits = givers(service, all, crafter, now);
        if (BUILDS.contains(craftTicket.boardCase())) {
            return TransferBuildDesk.ensureBuildTransfers(level, service, spirits, craftTicket, crafter);
        }
        Optional<String> craftItem = TicketPlans.craftItem(craftTicket.boardCase());
        TicketTarget target = service.targetOf(craftTicket.id(), spirits);
        boolean gather = craftItem.isEmpty()
                && craftTicket.boardCase() == BoardCase.GATHER_MATERIALS
                && target.supplyItem() != null;
        if (gather) {
            craftItem = Optional.of(ItemIds.normalize(target.supplyItem()));
        }
        if (craftItem.isEmpty()) {
            return false;
        }
        BlockCoord place = crafter.home().orElseGet(() -> TransferStockDesk.at(crafter));
        RecipeKnowledge recipes = RecipeKnowledge.of(level)
                .preferring(stock.available(), SettlementWood.of(level, place, stock.available()));
        List<Ingredient> ingredients = new ArrayList<>();
        Map<String, Integer> need = gather
                ? TransferCraftNeed.gatherNeed(recipes, craftItem.orElseThrow(), target.supplyCount())
                : TransferCraftNeed.craftNeed(recipes, craftTicket, target, craftItem.orElseThrow());
        need.forEach((item, count) -> ingredients.add(new Ingredient(ItemIds.normalize(item), count)));
        if (ingredients.isEmpty()) {
            return false;
        }
        if (craftTicket.boardCase() == BoardCase.CRAFT_TORCHES) {
            Map<String, Integer> sticks = new HashMap<>();
            need.forEach((item, count) -> {
                if (OwnWood.stick(item)) {
                    sticks.put(item, count);
                }
            });
            if (!sticks.isEmpty()
                    && OwnWood.planksHeld(crafter.getInventory(), SettlementWood.of(level, place, stock.available()))
                            >= sticks.values().stream()
                                    .mapToInt(count -> OwnWood.plankCost("stick", count))
                                    .sum()) {
                ingredients.removeIf(ingredient -> OwnWood.stick(ingredient.item()));
                if (ingredients.isEmpty()) {
                    return false;
                }
            }
        }
        if (TransferBuildDesk.ownWoodCovers(crafter, need)) {
            return false;
        }
        if (TransferCraftNeed.needsTable(
                        level, recipes, craftItem.orElseThrow(), service.targetPoint(craftTicket.id(), spirits))
                && !TransferCraftNeed.placesOwnTable(craftTicket, target)) {
            ingredients.add(new Ingredient(ItemIds.normalize(TABLE_ITEM), 1));
        }
        StackMapper mapper = new StackMapper();
        BlockCoord crafterAt = TransferStockDesk.at(crafter);
        boolean chest = TransferStockDesk.hasSettlementChest(level, place);
        List<SpiritEntity> donors = chest || gather
                ? spirits.stream().filter(spirit -> !busy(spirit)).toList()
                : spirits;
        List<TransferSource> sources = TransferStockDesk.sources(donors, mapper, recipes, crafter.getUUID());
        List<TransferOrder> orders = TransferPlanner.planForCraft(new TransferPlanner.CraftRequest(
                craftItem.orElseThrow(),
                crafter.getUUID(),
                crafterAt,
                TransferStockDesk.stacks(crafter.getInventory(), mapper),
                ingredients,
                sources,
                chest ? TransferStockDesk.chestStock(level, service, place, craftTicket.id()) : Map.of(),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
        if (submitOrders(service, spirits, craftTicket, orders, now)) {
            return true;
        }
        return !gather && TransferCarriers.awaitsDelivery(service.board(), craftTicket.id());
    }

    static boolean submitOrders(
            BoardService service,
            List<SpiritEntity> spirits,
            BoardTicket craftTicket,
            List<TransferOrder> orders,
            long now) {
        boolean active = hasActiveTransfer(service, craftTicket.id());
        if (orders.isEmpty()) {
            return active;
        }
        boolean submitted = false;
        for (TransferOrder order : orders) {
            String id = ticketId(craftTicket.id(), order);
            if (active(service, id)) {
                submitted = true;
                continue;
            }
            Optional<SpiritEntity> recipient = find(spirits, order.to());
            if (recipient.isEmpty()) {
                continue;
            }
            UUID assignee = order.from().orElse(order.to());
            List<String> modifiers = TransferTicket.modifiers(
                    order.to(), order.item(), order.count(), order.from().orElse(null));
            BoardOutcome outcome = service.submit(
                    new BoardTicket(
                            id,
                            BoardCase.TRANSFER_ITEM,
                            Optional.of(assignee),
                            BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                            now,
                            1,
                            cause(craftTicket.id()),
                            List.of(),
                            Map.of(),
                            modifiers),
                    TicketTarget.at(
                                    order.from().isPresent()
                                            ? TransferStockDesk.at(recipient.orElseThrow())
                                            : recipient
                                                    .orElseThrow()
                                                    .home()
                                                    .orElseGet(() -> TransferStockDesk.at(recipient.orElseThrow())))
                            .withTransfer(order.from().orElse(null), order.to(), order.item(), order.count()));
            submitted |= outcome == BoardOutcome.ACCEPTED || active(service, id);
        }
        return submitted || active;
    }

    public static Set<String> liveCauses(BoardService service) {
        Set<String> live = new HashSet<>();
        for (BoardTicket ticket : service.board().tickets()) {
            if (TicketPlans.craftItem(ticket.boardCase()).isEmpty()
                    && !BUILDS.contains(ticket.boardCase())
                    && ticket.boardCase() != BoardCase.GATHER_MATERIALS) {
                continue;
            }
            BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                live.add(cause(ticket.id()));
            }
        }
        return live;
    }

    private static List<SpiritEntity> givers(
            BoardService service, List<SpiritEntity> spirits, SpiritEntity crafter, long now) {
        return spirits.stream()
                .filter(spirit -> spirit.getUUID().equals(crafter.getUUID())
                        || service.board().availableTo(spirit.getUUID(), BoardCase.TRANSFER_ITEM, now))
                .toList();
    }

    private static void dropStuckGivers(ServerLevel level, BoardService service, String craftId, long now) {
        for (BoardTicket ticket : List.copyOf(service.board().tickets())) {
            String from = TransferStockDesk.modifier(ticket, "from=");
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                    || !cause(craftId).equals(ticket.cause())
                    || !active(service, ticket.id())
                    || from == null
                    || "warehouse".equals(from)) {
                continue;
            }
            boolean stuck = ticket.assignee()
                    .filter(giver -> !service.board().availableTo(giver, BoardCase.TRANSFER_ITEM, now))
                    .isPresent();
            if (stuck && service.cancel(level, ticket.id())) {
                LOGGER.info("Передача {} снята: отдающий {} снят с передач или на паузе", ticket.id(), from);
            }
        }
    }

    private static boolean hasActiveTransfer(BoardService service, String craftId) {
        for (BoardTicket ticket : service.board().tickets()) {
            if (ticket.boardCase() == BoardCase.TRANSFER_ITEM
                    && cause(craftId).equals(ticket.cause())
                    && active(service, ticket.id())) {
                return true;
            }
        }
        return false;
    }

    static boolean busy(SpiritEntity spirit) {
        return spirit.taskStatus().orElse(null) == RunStatus.RUNNING && !spirit.runsBackgroundTask();
    }

    static boolean active(BoardService service, String ticketId) {
        return service.board().statusOf(ticketId).filter(TransferWatch::open).isPresent();
    }

    static boolean open(BoardStatus status) {
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static Optional<SpiritEntity> find(List<SpiritEntity> spirits, UUID id) {
        return spirits.stream().filter(spirit -> spirit.getUUID().equals(id)).findFirst();
    }

    static String cause(String craftId) {
        return TransferCarriers.cause(craftId);
    }

    private static String ticketId(String craftId, TransferOrder order) {
        return "transfer:" + craftId + ":" + order.item() + ":"
                + order.from().map(UUID::toString).orElse("warehouse");
    }
}
