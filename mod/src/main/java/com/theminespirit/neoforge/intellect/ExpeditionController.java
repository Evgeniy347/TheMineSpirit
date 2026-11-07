package com.theminespirit.neoforge.intellect;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ExpeditionMinePlan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.DescentKit;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.ExpeditionDispatch;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

public final class ExpeditionController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RETURN_HOME = "return_home";

    private StockView stock = new StockView(Map.of());
    private Launcher launcher;
    private Dispatch lastDispatch;
    private Expedition active;
    private long closedAt = Long.MIN_VALUE;
    private boolean dispatched;
    private List<UUID> missionTeam = List.of();
    private UUID missionLeader;
    private String refusal;

    @FunctionalInterface
    public interface Launcher {
        boolean launch(SpiritEntity spirit, String script, TaskNode task, long now);
    }

    public void offer(Expedition planned) {
        if (planned == null) {
            throw new IllegalArgumentException("Поход без плана не предлагается: команда и цель обязательны");
        }
        if (active != null) {
            return;
        }
        active = planned;
        dispatched = false;
        refusal = null;
    }

    public boolean dispatchedRecently(long now) {
        return dispatched && now - closedAt < IntellectTuning.loadDefault().expeditionCooldownTicks();
    }

    public Optional<Expedition> active() {
        return Optional.ofNullable(active);
    }

    public record Dispatch(String script, List<UUID> team, long tick) {}

    public Optional<Dispatch> lastDispatch() {
        return Optional.ofNullable(lastDispatch);
    }

    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    public void update(List<SpiritEntity> members, StockView stock, long now, Launcher tasks) {
        if (members == null || stock == null || tasks == null) {
            throw new IllegalArgumentException(
                    "Поход без духов, запасов или исполнителя не ведётся: команда идёт конкретным составом");
        }
        launcher = tasks;
        this.stock = stock;
        monitorMission(members, now);
        if (active == null) {
            return;
        }
        if (dispatchMine(members, active, now)) {
            close(now);
        }
    }

    private boolean dispatchMine(List<SpiritEntity> members, Expedition expedition, long now) {
        List<SpiritEntity> team = team(members, expedition);
        List<String> missing = missingKit(team, stock);
        if (!missing.isEmpty()) {
            String reason = "перед спуском в шахту нужен набор: " + String.join(", ", missing);
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        UUID leaderId = expedition.team().get(0);
        Map<UUID, SpiritEntity> byId = new HashMap<>();
        for (SpiritEntity spirit : team) {
            byId.put(spirit.getUUID(), spirit);
        }
        ExpeditionDispatch.Plan plan =
                ExpeditionDispatch.plan(team.stream().map(SpiritEntity::getUUID).toList(), leaderId);
        SpiritEntity leader = byId.get(plan.leader());
        if (leader == null) {
            leader = team.get(0);
        }
        if (!PickaxeCheck.has(leader)) {
            String reason = "поход в шахту не начат: у лидера " + leader.displayName() + " нет кирки";
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        BlockCoord bottom = new BlockCoord(
                (int) expedition.target().x(), (int) expedition.target().y(), (int)
                        expedition.target().z());
        boolean launched = false;
        for (ExpeditionDispatch.Order order : plan.orders()) {
            SpiritEntity spirit = byId.get(order.spirit());
            if (spirit != null) {
                TaskNode task = order.spirit().equals(plan.leader()) ? ExpeditionMinePlan.plan(bottom) : order.task();
                launched |= launch(spirit, order.script(), task, now);
            }
        }
        if (launched) {
            refusal = null;
            lastDispatch = new Dispatch(ExpeditionDispatch.MINE, List.copyOf(expedition.team()), now);
            missionTeam = List.copyOf(expedition.team());
            missionLeader = plan.leader();
        }
        return launched;
    }

    public static List<String> missingKit(List<SpiritEntity> team, StockView stock) {
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = new ArrayList<>();
        for (SpiritEntity spirit : team) {
            inventory.addAll(mapper.map(spirit.getInventory()));
        }
        stock.available().forEach((item, count) -> {
            for (int left = count; left > 0; left -= 64) {
                inventory.add(new Stack(item, 0, Math.min(left, 64), 64));
            }
        });
        return DescentKit.missing(OutsideRules.loadDefault().mineKit(), inventory, InventoryTables.values());
    }

    private void monitorMission(List<SpiritEntity> members, long now) {
        if (missionLeader == null) {
            return;
        }
        Map<UUID, SpiritEntity> alive = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                alive.put(spirit.getUUID(), spirit);
            }
        }
        int present = 0;
        for (UUID id : missionTeam) {
            if (alive.containsKey(id)) {
                present++;
            }
        }
        if (present == missionTeam.size()) {
            return;
        }
        boolean allReturning = present > 0;
        for (UUID id : missionTeam) {
            SpiritEntity spirit = alive.get(id);
            if (spirit == null) {
                continue;
            }
            if (spirit.currentTaskScript().map(RETURN_HOME::equals).orElse(false)) {
                continue;
            }
            allReturning &= launch(spirit, RETURN_HOME, new ScriptCall(RETURN_HOME, Map.of()), now);
        }
        if (allReturning) {
            missionTeam = List.of();
            missionLeader = null;
        }
    }

    private List<SpiritEntity> team(List<SpiritEntity> members, Expedition expedition) {
        List<SpiritEntity> team = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved() && expedition.team().contains(spirit.getUUID())) {
                team.add(spirit);
            }
        }
        return team;
    }

    private void close(long now) {
        active = null;
        dispatched = true;
        closedAt = now;
    }

    private boolean launch(SpiritEntity spirit, String script, TaskNode task, long now) {
        return launcher != null && launcher.launch(spirit, script, task, now);
    }
}
