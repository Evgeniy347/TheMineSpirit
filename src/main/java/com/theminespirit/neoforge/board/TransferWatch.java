package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.core.social.TransferSource;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

public final class TransferWatch {
    private static final String TABLE_ITEM = "minecraft:crafting_table";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHEST_RADIUS = 16;
    private static final int CHEST_PROBES = 4;
    // Факелы освещения — тоже материал стройки: без передачи дух шёл ставить их пустым («нет материала torch»).
    private static final Set<BoardCase> BUILDS =
            Set.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING);

    private TransferWatch() {}

    public static boolean ensureTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            BoardTicket craftTicket,
            SpiritEntity crafter,
            long now) {
        if (BUILDS.contains(craftTicket.boardCase())) {
            return ensureBuildTransfers(level, service, spirits, craftTicket, crafter);
        }
        Optional<String> craftItem = TicketPlans.craftItem(craftTicket.boardCase());
        TicketTarget target = service.targetOf(craftTicket.id(), spirits);
        // Добыча крафтового (забор ×72) — тот же крафт: брёвна после расчистки лежат у расчищавшего, а добытчик
        // падал «не удалось добыть jungle_fence: в инвентаре 0» (приёмка «джунгли» 23.10).
        boolean gather = craftItem.isEmpty()
                && craftTicket.boardCase() == BoardCase.GATHER_MATERIALS
                && target.supplyItem() != null;
        if (gather) {
            craftItem = Optional.of(ItemIds.normalize(target.supplyItem()));
        }
        if (craftItem.isEmpty()) {
            return false;
        }
        // Доски — той породы, что есть в запасе поселения, а не первая из рецепта (в джунглях сундук ждал дубовые).
        BlockCoord place = crafter.home().orElseGet(() -> at(crafter));
        RecipeKnowledge recipes = RecipeKnowledge.of(level)
                .preferring(stock.available(), SettlementWood.of(level, place, stock.available()));
        List<Ingredient> ingredients = new ArrayList<>();
        Map<String, Integer> need = gather
                ? gatherNeed(recipes, craftItem.orElseThrow(), target.supplyCount())
                : craftNeed(recipes, craftTicket, target, craftItem.orElseThrow());
        need.forEach((item, count) -> ingredients.add(new Ingredient(ItemIds.normalize(item), count)));
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
        // Добыча сама рубит и крафтит («acquire»): ждать передачу от занятого стройкой духа незачем — свободный
        // дух простаивал 1845 тиков, пока строитель ограды держал доски и брёвна (приёмка «джунгли» 24.10).
        // Есть сундук поселения — передачи только со склада и от свободных духов: остатки каждый дух сдаёт в сундук
        // по окончании шага (TicketPlans.LEFTOVERS), а передача от занятого духа висела до 10000 тиков (приёмка
        // «джунгли» 24.10). Свободный дух шага не кончает и не сдаёт: взятое со склада под крафт, отданный
        // другому духу, без передачи от него застревало, крафт падал «не хватает» (settlement_EmptyTwoSpirits).
        boolean chest = hasSettlementChest(level, place);
        List<SpiritEntity> donors = chest || gather
                ? spirits.stream().filter(spirit -> !busy(spirit)).toList()
                : spirits;
        List<TransferSource> sources = sources(donors, mapper, recipes, crafter.getUUID());
        List<TransferOrder> orders = TransferPlanner.planForCraft(new TransferPlanner.CraftRequest(
                craftItem.orElseThrow(),
                crafter.getUUID(),
                crafterAt,
                stacks(crafter.getInventory(), mapper),
                ingredients,
                sources,
                // Без сундука склада нет: снимок запаса считал складом устаревшие инвентари, передача «со склада»
                // ничего не брала, а крафт падал «не хватает» (settlement_EmptyTwoSpirits 24.10).
                chest ? chestStock(level, service, place, craftTicket.id()) : Map.of(),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
        if (submitOrders(service, spirits, craftTicket, orders, now)) {
            return true;
        }
        // Ингредиент только у занятого духа: по окончании шага он сдаст его в сундук (или отдаст излишек передачей,
        // если сундука нет) — крафт ждёт, а не падает «не хватает: cobblestone» (приёмка «джунгли» 24.10) или
        // «не хватает: oak_planks 1 из 9» у сундуков склада (settlement_EmptyTwoSpirits).
        return heldByBusy(ingredients, crafter, orders, Map.of(), spirits, mapper);
    }

    private static boolean heldByBusy(
            List<Ingredient> ingredients,
            SpiritEntity crafter,
            List<TransferOrder> orders,
            Map<String, Integer> stored,
            List<SpiritEntity> spirits,
            StackMapper mapper) {
        for (Ingredient ingredient : ingredients) {
            int have = count(stacks(crafter.getInventory(), mapper), ingredient.item())
                    + stored.getOrDefault(ingredient.item(), 0);
            for (TransferOrder order : orders) {
                if (order.item().equals(ingredient.item())) {
                    have += order.count();
                }
            }
            if (have >= ingredient.count()) {
                continue;
            }
            for (SpiritEntity spirit : spirits) {
                if (!spirit.getUUID().equals(crafter.getUUID())
                        && busy(spirit)
                        && count(stacks(spirit.getInventory(), mapper), ingredient.item()) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int count(List<Stack> stacks, String item) {
        int total = 0;
        for (Stack stack : stacks) {
            if (stack != null && stack.item().equals(item)) {
                total += stack.count();
            }
        }
        return total;
    }

    // Стройка первых шагов: материалы, собранные другими духами, лежат у них в инвентаре — строитель брал из
    // сундуков и падал «не хватило материала 'jungle_fence': нужно 25, есть 0» (приёмка «джунгли»). Сундуки
    // строитель обходит сам (заходы за материалами), поэтому здесь — только передачи от духов.
    private static boolean ensureBuildTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            BoardTicket build,
            SpiritEntity builder) {
        Map<String, Integer> need;
        try {
            need = CaseMaterials.need(
                    TicketPlans.plan(build.boardCase(), build.level(), service.targetOf(build.id(), spirits)),
                    FirstStepsRunner.materialSource(level));
        } catch (RuntimeException failure) {
            LOGGER.warn("Материалы стройки {} для передачи не посчитаны: {}", build.id(), failure.getMessage());
            return false;
        }
        List<Ingredient> materials = new ArrayList<>();
        need.forEach((item, count) -> materials.add(new Ingredient(ItemIds.normalize(item), count)));
        if (materials.isEmpty()) {
            return false;
        }
        StackMapper mapper = new StackMapper();
        RecipeKnowledge recipes = RecipeKnowledge.of(level);
        BlockCoord home = builder.home().orElseGet(() -> at(builder));
        boolean chest = hasSettlementChest(level, home);
        List<TransferOrder> orders = TransferPlanner.planForCraft(new TransferPlanner.CraftRequest(
                build.boardCase().name().toLowerCase(Locale.ROOT),
                builder.getUUID(),
                at(builder),
                stacks(builder.getInventory(), mapper),
                materials,
                sources(
                        chest ? spirits.stream().filter(spirit -> !busy(spirit)).toList() : spirits,
                        mapper,
                        recipes,
                        builder.getUUID()),
                Map.of(),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
        if (submitOrders(service, spirits, build, orders, level.getGameTime())) {
            return true;
        }
        // Материал стройки ещё у занятого духа (добытчик не сдал брёвна в сундук) — стройка ждёт, а не падает
        // «не хватило материала 'jungle_log': поставлено 7 из 94, есть 0» (приёмка «джунгли» 24.10). Сундуки
        // строитель обходит сам — их содержимое считается имеющимся.
        Map<String, Integer> stored = chest ? chestStock(level, service, home, build.id()) : Map.of();
        return heldByBusy(materials, builder, orders, stored, spirits, mapper);
    }

    private static boolean submitOrders(
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
            List<String> modifiers = List.of(
                    "to=" + order.to(),
                    "item=" + order.item(),
                    "count=" + order.count(),
                    "from=" + order.from().map(UUID::toString).orElse("warehouse"));
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
                    // Со склада — к дому получателя (сундуки поселения у дома), от духа — к получателю.
                    TicketTarget.at(
                                    order.from().isPresent()
                                            ? at(recipient.orElseThrow())
                                            : recipient
                                                    .orElseThrow()
                                                    .home()
                                                    .orElseGet(() -> at(recipient.orElseThrow())))
                            .withTransfer(order.from().orElse(null), order.to(), order.item(), order.count()));
            // Проваленная передача остаётся FAILED, повторная подача — DUPLICATE: крафт «ждал передачу» без
            // открытой передачи до конца приёмки (gather:jungle_fence после «stick: нужно 16, в инвентаре 15»).
            submitted |= outcome == BoardOutcome.ACCEPTED || active(service, id);
        }
        return submitted || active;
    }

    // Ингредиенты на все крафты добычи: ×72 забора — 24 крафта по 3, а не один.
    private static Map<String, Integer> gatherNeed(RecipeKnowledge recipes, String item, int count) {
        int output = Math.max(recipes.outputCount(item), 1);
        int crafts = (Math.max(count, 1) + output - 1) / output;
        Map<String, Integer> need = new LinkedHashMap<>();
        recipes.ingredientsFor(item)
                .forEach(ingredient -> need.merge(ingredient.item(), ingredient.count() * crafts, Integer::sum));
        return need;
    }

    // Ингредиенты на все крафты задания (torch ×8 — два крафта), а не на один.
    private static Map<String, Integer> craftNeed(
            RecipeKnowledge recipes, BoardTicket craftTicket, TicketTarget target, String item) {
        MaterialSource source = new MaterialSource() {
            @Override
            public int outputCount(String product) {
                return recipes.outputCount(product);
            }

            @Override
            public List<Ingredient> ingredientsFor(String product) {
                return recipes.ingredientsFor(product);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return Map.of();
            }
        };
        try {
            TaskNode plan = TicketPlans.plan(craftTicket.boardCase(), craftTicket.level(), target);
            Map<String, Integer> need = CaseMaterials.craftNeed(plan, source, item);
            if (!need.isEmpty()) {
                return need;
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("План крафта {} не собран для передачи: {}", craftTicket.id(), failure.getMessage());
        }
        Map<String, Integer> single = new LinkedHashMap<>();
        recipes.ingredientsFor(item)
                .forEach(ingredient -> single.merge(ingredient.item(), ingredient.count(), Integer::sum));
        return single;
    }

    private static boolean needsTable(ServerLevel level, RecipeKnowledge recipes, String item, BlockCoord craftAt) {
        return recipes.stationFor(item).filter(Stations.CRAFTING_TABLE::equals).isPresent()
                && !HomeBlocks.near(
                        level, craftAt, CatalogLimits.STATION_RADIUS_DEFAULT, state -> state.is(Blocks.CRAFTING_TABLE));
    }

    public static Set<String> liveCauses(BoardService service) {
        Set<String> live = new HashSet<>();
        for (BoardTicket ticket : service.board().all()) {
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

    // Склад — то, что сейчас лежит в сундуках у дома (досягаемость withdraw), за вычетом открытых передач со склада:
    // снимок запаса устаревает между пересборками, и одни и те же палки заказывались дважды — передача падала
    // «в сундуках рядом только 0 из 3 'jungle_planks'» (приёмка «джунгли» 24.10).
    private static Map<String, Integer> chestStock(
            ServerLevel level, BoardService service, BlockCoord home, String craftId) {
        Map<String, Integer> containers = new HashMap<>();
        StackMapper mapper = new StackMapper();
        for (BlockCoord pos : SettlementChestStore.get(level).chests().nearest(home, CHEST_PROBES)) {
            int dx = pos.x() - home.x();
            int dz = pos.z() - home.z();
            if (dx * dx + dz * dz > CatalogLimits.CONTAINER_RADIUS_DEFAULT * CatalogLimits.CONTAINER_RADIUS_DEFAULT) {
                continue;
            }
            if (ContainerAccess.open(level, new BlockPos(pos.x(), pos.y(), pos.z()))
                    instanceof ContainerAccess.Opened opened) {
                for (Stack stack : stacks(opened.container(), mapper)) {
                    if (stack != null) {
                        containers.merge(stack.item(), stack.count(), Integer::sum);
                    }
                }
            }
        }
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                    || cause(craftId).equals(ticket.cause())
                    || !ticket.modifiers().contains("from=warehouse")
                    || !active(service, ticket.id())) {
                continue;
            }
            String item = modifier(ticket, "item=");
            String count = modifier(ticket, "count=");
            if (item != null && count != null) {
                containers.merge(item, -Integer.parseInt(count), Integer::sum);
            }
        }
        containers.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return Map.copyOf(containers);
    }

    private static String modifier(BoardTicket ticket, String prefix) {
        for (String modifier : ticket.modifiers()) {
            if (modifier.startsWith(prefix)) {
                return modifier.substring(prefix.length());
            }
        }
        return null;
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

    // Сундук поселения рядом с домом (реестр сундуков, блок на месте проверяется: запись могла устареть).
    static boolean hasSettlementChest(ServerLevel level, BlockCoord home) {
        for (BlockCoord pos : SettlementChestStore.get(level).chests().nearest(home, CHEST_PROBES)) {
            int dx = pos.x() - home.x();
            int dz = pos.z() - home.z();
            if (dx * dx + dz * dz > CHEST_RADIUS * CHEST_RADIUS || Math.abs(pos.y() - home.y()) > CHEST_RADIUS) {
                return false;
            }
            if (ContainerAccess.allowed(level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z())))) {
                return true;
            }
        }
        return false;
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

    private static boolean busy(SpiritEntity spirit) {
        return spirit.taskStatus().orElse(null) == RunStatus.RUNNING && !spirit.runsBackgroundTask();
    }

    // Отдающий ждёт свою передачу: в том же цикле доска отдавала ему стройку ограды, передача палок стояла
    // PENDING, а крафтер факелов простаивал 1630 тиков (приёмка «джунгли» 24.10).
    static Set<UUID> donors(BoardService service, String craftId) {
        Set<UUID> donors = new HashSet<>();
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() == BoardCase.TRANSFER_ITEM
                    && cause(craftId).equals(ticket.cause())
                    && active(service, ticket.id())) {
                ticket.assignee().ifPresent(donors::add);
            }
        }
        return donors;
    }

    private static boolean active(BoardService service, String ticketId) {
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
