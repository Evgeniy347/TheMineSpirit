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
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.TransferTicket;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.core.social.TransferSource;
import com.theminespirit.neoforge.OwnWood;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

public final class TransferWatch {
    private static final String TABLE_ITEM = "minecraft:crafting_table";
    private static final String DIRT = "dirt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHEST_RADIUS = 16;
    private static final int CHEST_PROBES = 4;
    private static final int TORCHES_PER_COAL = 4;
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
            return ensureBuildTransfers(level, service, spirits, craftTicket, crafter);
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
        if (ownWoodCovers(crafter, need)) {
            return false;
        }
        if (needsTable(level, recipes, craftItem.orElseThrow(), service.targetPoint(craftTicket.id(), spirits))
                && !placesOwnTable(craftTicket, target)) {
            ingredients.add(new Ingredient(ItemIds.normalize(TABLE_ITEM), 1));
        }
        StackMapper mapper = new StackMapper();
        BlockCoord crafterAt = at(crafter);
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
                chest ? chestStock(level, service, place, craftTicket.id()) : Map.of(),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
        if (submitOrders(service, spirits, craftTicket, orders, now)) {
            return true;
        }
        return !gather && heldByBusy(ingredients, crafter, orders, Map.of(), spirits, mapper);
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

    private static boolean ensureBuildTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            BoardTicket build,
            SpiritEntity builder) {
        Map<String, Integer> need;
        try {
            TaskNode plan = TicketPlans.plan(build.boardCase(), build.level(), service.targetOf(build.id(), spirits));
            need = BuildRemainder.left(
                    level, build.boardCase(), plan, CaseMaterials.need(plan, FirstStepsRunner.materialSource(level)));
        } catch (RuntimeException failure) {
            LOGGER.warn("Материалы стройки {} для передачи не посчитаны: {}", build.id(), failure.getMessage());
            return false;
        }
        Map<String, Integer> wood = new HashMap<>();
        need.forEach((item, count) -> {
            if (!OwnWood.species(item).isEmpty() || OwnWood.stick(item)) {
                wood.put(item, count);
            }
        });
        BlockCoord yard = SettlementPlotStore.get(level)
                .site()
                .map(site -> site.workYard())
                .orElseGet(() -> builder.home().orElseGet(() -> at(builder)));
        Map<String, Integer> pooled =
                hasSettlementChest(level, yard) ? chestStock(level, service, yard, build.id()) : Map.of();
        if (!wood.isEmpty() && (ownWoodCovers(builder, wood) || pooledWoodCovers(builder, wood, pooled))) {
            need = new HashMap<>(need);
            need.keySet().removeAll(wood.keySet());
        }
        if (need.containsKey(DIRT) || need.containsKey("minecraft:" + DIRT)) {
            need = new HashMap<>(need);
            need.remove(DIRT);
            need.remove("minecraft:" + DIRT);
        }
        if (!pooled.isEmpty()) {
            Map<String, Integer> missing = new HashMap<>(need);
            missing.entrySet().removeIf(entry -> pooledCount(pooled, bareId(entry.getKey())) >= entry.getValue());
            need = missing;
        }
        int torches = need.getOrDefault("torch", 0) + need.getOrDefault("minecraft:torch", 0);
        int coal = builder.getInventory().countItem(Items.COAL)
                + builder.getInventory().countItem(Items.CHARCOAL)
                + pooledCount(pooled, "coal")
                + pooledCount(pooled, "charcoal")
                + (pooledCount(pooled, "torch") + TORCHES_PER_COAL - 1) / TORCHES_PER_COAL;
        if (torches > 0 && coal * TORCHES_PER_COAL >= torches) {
            need = new HashMap<>(need);
            need.remove("torch");
            need.remove("minecraft:torch");
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
                                            ? at(recipient.orElseThrow())
                                            : recipient
                                                    .orElseThrow()
                                                    .home()
                                                    .orElseGet(() -> at(recipient.orElseThrow())))
                            .withTransfer(order.from().orElse(null), order.to(), order.item(), order.count()));
            submitted |= outcome == BoardOutcome.ACCEPTED || active(service, id);
        }
        return submitted || active;
    }

    private static Map<String, Integer> gatherNeed(RecipeKnowledge recipes, String item, int count) {
        int output = Math.max(recipes.outputCount(item), 1);
        int crafts = (Math.max(count, 1) + output - 1) / output;
        Map<String, Integer> need = new LinkedHashMap<>();
        recipes.ingredientsFor(item)
                .forEach(ingredient -> need.merge(ingredient.item(), ingredient.count() * crafts, Integer::sum));
        return need;
    }

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

    private static boolean placesOwnTable(BoardTicket craftTicket, TicketTarget target) {
        if (target.work() == null) {
            return false;
        }
        return placesTable(TicketPlans.plan(craftTicket.boardCase(), craftTicket.level(), target));
    }

    private static boolean placesTable(TaskNode plan) {
        return switch (plan) {
            case SequenceNode sequence -> sequence.steps().stream().anyMatch(TransferWatch::placesTable);
            case FallbackNode fallback -> placesTable(fallback.steps().get(0));
            case ScriptCall call ->
                "place_block".equals(call.script())
                        && TABLE_ITEM.equals(call.params().get("block"));
            default -> false;
        };
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

    private static List<SpiritEntity> givers(
            BoardService service, List<SpiritEntity> spirits, SpiritEntity crafter, long now) {
        return spirits.stream()
                .filter(spirit -> spirit.getUUID().equals(crafter.getUUID())
                        || service.board().availableTo(spirit.getUUID(), BoardCase.TRANSFER_ITEM, now))
                .toList();
    }

    private static void dropStuckGivers(ServerLevel level, BoardService service, String craftId, long now) {
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            String from = modifier(ticket, "from=");
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

    private static boolean ownWoodCovers(SpiritEntity crafter, Map<String, Integer> need) {
        String species = need.keySet().stream()
                .map(OwnWood::species)
                .filter(found -> !found.isEmpty())
                .findFirst()
                .orElse("");
        if (species.isEmpty()
                || !need.keySet().stream().allMatch(id -> OwnWood.stick(id) || species.equals(OwnWood.species(id)))) {
            return false;
        }
        int cost = need.entrySet().stream()
                .mapToInt(entry -> OwnWood.plankCost(entry.getKey(), entry.getValue()))
                .sum();
        return OwnWood.planksHeld(crafter.getInventory(), species) >= cost;
    }

    private static String bareId(String item) {
        return item.substring(item.indexOf(':') + 1);
    }

    private static int pooledCount(Map<String, Integer> pooled, String item) {
        return pooled.getOrDefault(item, 0) + pooled.getOrDefault("minecraft:" + item, 0);
    }

    private static boolean pooledWoodCovers(
            SpiritEntity builder, Map<String, Integer> need, Map<String, Integer> pooled) {
        String species = need.keySet().stream()
                .map(OwnWood::species)
                .filter(found -> !found.isEmpty())
                .findFirst()
                .orElse("");
        if (species.isEmpty()) {
            return false;
        }
        int cost = need.entrySet().stream()
                .mapToInt(entry -> OwnWood.plankCost(entry.getKey(), entry.getValue()))
                .sum();
        return OwnWood.planksHeld(builder.getInventory(), species) + OwnWood.planksIn(pooled, species) >= cost;
    }

    private static boolean busy(SpiritEntity spirit) {
        return spirit.taskStatus().orElse(null) == RunStatus.RUNNING && !spirit.runsBackgroundTask();
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
        return TransferCarriers.cause(craftId);
    }

    private static String ticketId(String craftId, TransferOrder order) {
        return "transfer:" + craftId + ":" + order.item() + ":"
                + order.from().map(UUID::toString).orElse("warehouse");
    }
}
