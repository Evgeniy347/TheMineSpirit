package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolFallback;
import com.theminespirit.core.board.ToolFate;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.ToolsConfig;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class ToolStockWatch {
    public static final String PREFIX = "stock:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockCoord CRAFT_ORIGIN = new BlockCoord(0, 0, 0);
    private static final Set<BoardCase> FIRST_STEPS = Set.copyOf(FirstStepsPlan.chain());

    private ToolStockWatch() {}

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        ToolsConfig config = DataTables.INSTANCE.board().current().tools();
        MaterialSource source = FirstStepsRunner.materialSource(level);
        Set<String> craftable = craftable(source, config, stock);
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isEmpty() || spirit.isRemoved()) {
                continue;
            }
            Map<String, Integer> own = ownTools(spirit, config, percent);
            Set<String> craftableNow = ToolFallback.allowed(
                    craftable(source, config, ownStock(spirit)),
                    openTickets(service, spirit.getUUID()),
                    now,
                    BoardTuning.toolFallbackTicks());
            for (String item : ToolSet.missing(config, own, craftable, craftableNow)) {
                BoardCase caseValue = ToolSet.caseFor(item)
                        .orElseThrow(() -> new IllegalStateException(
                                "Инструмент " + item + " без кейса крафта: набор духа не собрать"));
                post(service, spirit, home.orElseThrow(), caseValue, source, stock, now);
            }
        }
    }

    private static void post(
            BoardService service,
            SpiritEntity spirit,
            BlockCoord home,
            BoardCase caseValue,
            MaterialSource source,
            StockView stock,
            long now) {
        String id = ticketId(caseValue, spirit.getUUID());
        if (FIRST_STEPS.contains(caseValue) && !service.board().ledger().has(caseValue, 1)) {
            return;
        }
        if (active(service, id)) {
            return;
        }
        TicketTarget target = TicketTarget.at(home).withToolCount(1);
        Map<String, Integer> missing =
                ResourceGate.missing(new StockNeed(FirstStepsRunner.materials(source, caseValue, target)), stock);
        if (!missing.isEmpty()) {
            LOGGER.debug("Запас инструмента {} духа {} ждёт материалов: {}", caseValue, spirit.displayName(), missing);
            return;
        }
        LOGGER.info(
                "Поставлено: сделать {} духу {} (в его наборе не хватает инструмента)",
                caseValue,
                spirit.displayName());
        service.submit(
                new BoardTicket(
                        id,
                        caseValue,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(caseValue),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target);
    }

    private static Set<String> craftable(MaterialSource source, ToolsConfig config, StockView stock) {
        Set<String> craftable = new HashSet<>();
        for (String item : ToolSet.candidates(config)) {
            BoardCase caseValue = ToolSet.caseFor(item).orElseThrow();
            Map<String, Integer> need = FirstStepsRunner.materials(source, caseValue, TicketTarget.at(CRAFT_ORIGIN));
            if (ResourceGate.missing(new StockNeed(need), stock).isEmpty()) {
                craftable.add(item);
            }
        }
        return craftable;
    }

    private static StockView ownStock(SpiritEntity spirit) {
        Map<String, Integer> items = new HashMap<>();
        SpiritInventory inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
            }
        }
        return new StockView(items);
    }

    private static Map<String, Integer> ownTools(SpiritEntity spirit, ToolsConfig config, int percent) {
        Map<String, Integer> own = new HashMap<>();
        SpiritInventory inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            addTool(own, inventory.getItem(slot), config, percent);
        }
        if (spirit.mainHandOwned()) {
            addTool(own, spirit.getMainHandItem(), config, percent);
        }
        return own;
    }

    private static void addTool(Map<String, Integer> own, ItemStack stack, ToolsConfig config, int percent) {
        if (stack.isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (wornForReplacement(stack, itemId, config, percent)) {
            return;
        }
        own.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
    }

    private static boolean wornForReplacement(ItemStack stack, String itemId, ToolsConfig config, int percent) {
        if (stack.getMaxDamage() <= 0 || stack.getDamageValue() <= 0) {
            return false;
        }
        int durability = stack.getMaxDamage() - stack.getDamageValue();
        boolean damaged = RepairCalculator.damagedEnoughToRepair(durability, stack.getMaxDamage(), percent);
        return config.fate(itemId, damaged) == ToolFate.REPLACE;
    }

    public static String ticketId(BoardCase caseValue, UUID spirit) {
        return PREFIX + caseValue.name().toLowerCase(Locale.ROOT) + ":" + spirit;
    }

    private static List<BoardTicket> openTickets(BoardService service, UUID spirit) {
        List<BoardTicket> open = new ArrayList<>();
        for (BoardTicket ticket : service.board().all()) {
            if (ToolSet.isTool(ticket.boardCase())
                    && ticket.id().equals(ticketId(ticket.boardCase(), spirit))
                    && active(service, ticket.id())) {
                open.add(ticket);
            }
        }
        return open;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }
}
