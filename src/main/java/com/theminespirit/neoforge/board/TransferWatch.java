package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.core.social.TransferSource;
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
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;

public final class TransferWatch {
    private static final String TABLE_ITEM = "minecraft:crafting_table";

    private TransferWatch() {}

    public static boolean ensureTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            BoardTicket craftTicket,
            SpiritEntity crafter,
            long now) {
        Optional<String> craftItem = TicketPlans.craftItem(craftTicket.boardCase());
        if (craftItem.isEmpty()) {
            return false;
        }
        RecipeKnowledge recipes = RecipeKnowledge.of(level);
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipes.ingredientsFor(craftItem.orElseThrow())) {
            ingredients.add(new Ingredient(ItemIds.normalize(ingredient.item()), ingredient.count()));
        }
        if (ingredients.isEmpty()) {
            return false;
        }
        if (needsTable(level, recipes, craftItem.orElseThrow(), service.targetPoint(craftTicket.id(), spirits))) {
            // Верстак у места крафта не стоит — он сам ингредиент: его несёт дух, у которого он в инвентаре.
            // Иначе крафтер проваливал «нужен верстак: рядом (16) нет», пока верстак лежал у соседа (INC-26).
            ingredients.add(new Ingredient(ItemIds.normalize(TABLE_ITEM), 1));
        }
        StackMapper mapper = new StackMapper();
        BlockCoord crafterAt = at(crafter);
        List<TransferSource> sources = sources(spirits, mapper, recipes, crafter.getUUID());
        List<TransferOrder> orders = TransferPlanner.planForCraft(new TransferPlanner.CraftRequest(
                craftItem.orElseThrow(),
                crafter.getUUID(),
                crafterAt,
                stacks(crafter.getInventory(), mapper),
                ingredients,
                sources,
                warehouse(stock, spirits, mapper),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
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
            List<String> modifiers = List.of(
                    "to=" + order.to(),
                    "item=" + order.item(),
                    "count=" + order.count(),
                    "from=" + order.from().map(UUID::toString).orElse("warehouse"));
            service.submit(
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
                    TicketTarget.at(at(recipient.orElseThrow()))
                            .withTransfer(order.from().orElse(null), order.to(), order.item(), order.count()));
            submitted = true;
        }
        return submitted || active;
    }

    private static boolean needsTable(ServerLevel level, RecipeKnowledge recipes, String item, BlockCoord craftAt) {
        return recipes.stationFor(item).filter(Stations.CRAFTING_TABLE::equals).isPresent()
                && !HomeBlocks.near(
                        level, craftAt, CatalogLimits.STATION_RADIUS_DEFAULT, state -> state.is(Blocks.CRAFTING_TABLE));
    }

    public static Set<String> liveCauses(BoardService service) {
        Set<String> live = new HashSet<>();
        for (BoardTicket ticket : service.board().all()) {
            if (TicketPlans.craftItem(ticket.boardCase()).isEmpty()) {
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

    private static List<TransferSource> sources(
            List<SpiritEntity> spirits, StackMapper mapper, RecipeKnowledge recipes, UUID crafter) {
        List<TransferSource> sources = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || spirit.getUUID().equals(crafter)) {
                continue;
            }
            BaseKit kit = KitFor.kitFor(spirit.currentActivity().orElse(null), spirit.isChild());
            List<String> needs = spirit.currentPlan()
                    .map(plan -> NeedsOfPlan.collect(plan, recipes))
                    .orElse(List.of());
            sources.add(new TransferSource(
                    spirit.getUUID(), at(spirit), stacks(spirit.getInventory(), mapper), kit, needs));
        }
        return sources;
    }

    private static Map<String, Integer> warehouse(StockView stock, List<SpiritEntity> spirits, StackMapper mapper) {
        Map<String, Integer> containers = new HashMap<>(stock.available());
        for (SpiritEntity spirit : spirits) {
            for (Stack stack : stacks(spirit.getInventory(), mapper)) {
                if (stack != null) {
                    containers.merge(stack.item(), -stack.count(), Integer::sum);
                }
            }
        }
        containers.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return Map.copyOf(containers);
    }

    private static List<Stack> stacks(Container inventory, StackMapper mapper) {
        List<Stack> raw = mapper.map(inventory);
        List<Stack> normalized = new ArrayList<>(raw.size());
        for (Stack stack : raw) {
            normalized.add(
                    stack == null
                            ? null
                            : new Stack(
                                    ItemIds.normalize(stack.item()), stack.variant(), stack.count(), stack.maxStack()));
        }
        return normalized;
    }

    private static boolean hasActiveTransfer(BoardService service, String craftId) {
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() == BoardCase.TRANSFER_ITEM
                    && cause(craftId).equals(ticket.cause())
                    && active(service, ticket.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean active(BoardService service, String ticketId) {
        BoardStatus status = service.board().statusOf(ticketId).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static Optional<SpiritEntity> find(List<SpiritEntity> spirits, UUID id) {
        return spirits.stream().filter(spirit -> spirit.getUUID().equals(id)).findFirst();
    }

    private static BlockCoord at(SpiritEntity spirit) {
        return new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }

    private static String cause(String craftId) {
        return "transfer:" + craftId;
    }

    private static String ticketId(String craftId, TransferOrder order) {
        return "transfer:" + craftId + ":" + order.item() + ":"
                + order.from().map(UUID::toString).orElse("warehouse");
    }
}
