package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.board.ExpeditionMinePlan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.DescentKit;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.ExpeditionDispatch;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.inventory.InventoryTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExpeditionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpeditionController.class);
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
        boolean launch(UUID spirit, String script, TaskNode task, long now);
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

    public void update(List<? extends ExpeditionMember> members, StockView stock, long now, Launcher tasks) {
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

    private boolean dispatchMine(List<? extends ExpeditionMember> members, Expedition expedition, long now) {
        List<ExpeditionMember> team = team(members, expedition);
        List<String> missing = ExpeditionKit.missing(team, stock);
        if (!missing.isEmpty()) {
            String reason = "перед спуском в шахту нужен набор: " + String.join(", ", missing);
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        UUID leaderId = expedition.team().get(0);
        Map<UUID, ExpeditionMember> byId = new HashMap<>();
        for (ExpeditionMember spirit : team) {
            byId.put(spirit.id(), spirit);
        }
        ExpeditionDispatch.Plan plan =
                ExpeditionDispatch.plan(team.stream().map(ExpeditionMember::id).toList(), leaderId);
        ExpeditionMember leader = byId.get(plan.leader());
        if (leader == null) {
            leader = team.get(0);
        }
        List<String> leaderMissing = ExpeditionKit.missing(List.of(leader), stock);
        if (!leaderMissing.isEmpty()) {
            String reason = "перед спуском в шахту лидеру нужен набор: " + String.join(", ", leaderMissing);
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        if (!leader.hasPickaxe() && !ExpeditionKit.hasPickaxe(stock)) {
            String reason = "поход в шахту не начат: у лидера " + leader.displayName() + " нет кирки";
            if (!reason.equals(refusal)) {
                LOGGER.info("[graph] экспедиция отложена: {}", reason);
            }
            refusal = reason;
            return false;
        }
        List<ScriptCall> kit = DescentKit.supply(
                OutsideRules.loadDefault().mineKit(), leader.inventory(), stock.available(), InventoryTables.values());
        BlockCoord bottom = new BlockCoord(
                (int) expedition.target().x(), (int) expedition.target().y(), (int)
                        expedition.target().z());
        boolean launched = false;
        for (ExpeditionDispatch.Order order : plan.orders()) {
            ExpeditionMember spirit = byId.get(order.spirit());
            if (spirit != null) {
                TaskNode task = order.spirit().equals(plan.leader()) ? leaderTask(bottom, kit) : order.task();
                launched |= launch(spirit.id(), order.script(), task, now);
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

    private static TaskNode leaderTask(BlockCoord bottom, List<ScriptCall> kit) {
        TaskNode mine = ExpeditionMinePlan.plan(bottom);
        if (kit.isEmpty()) {
            return mine;
        }
        List<TaskNode> steps = new ArrayList<>(kit);
        steps.add(mine);
        return new SequenceNode(steps);
    }

    private void monitorMission(List<? extends ExpeditionMember> members, long now) {
        if (missionLeader == null) {
            return;
        }
        Map<UUID, ExpeditionMember> alive = new HashMap<>();
        for (ExpeditionMember spirit : members) {
            if (!spirit.removed()) {
                alive.put(spirit.id(), spirit);
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
            ExpeditionMember spirit = alive.get(id);
            if (spirit == null) {
                continue;
            }
            if (spirit.currentScript().map(RETURN_HOME::equals).orElse(false)) {
                continue;
            }
            allReturning &= launch(id, RETURN_HOME, new ScriptCall(RETURN_HOME, Map.of()), now);
        }
        if (allReturning) {
            missionTeam = List.of();
            missionLeader = null;
        }
    }

    private List<ExpeditionMember> team(List<? extends ExpeditionMember> members, Expedition expedition) {
        List<ExpeditionMember> team = new ArrayList<>();
        for (ExpeditionMember spirit : members) {
            if (!spirit.removed() && expedition.team().contains(spirit.id())) {
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

    private boolean launch(UUID spirit, String script, TaskNode task, long now) {
        return launcher != null && launcher.launch(spirit, script, task, now);
    }
}
