package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.AssistOrder;
import com.theminespirit.core.coordinator.CoordinatorDirectives;
import com.theminespirit.core.coordinator.CoordinatorPlan;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

final class CoordinatorOrders {
    private final ServerLevel level;
    private final Map<UUID, CombatDirective> lastDirectives = new HashMap<>();
    private final Map<UUID, String> lastForced = new HashMap<>();
    private final Set<UUID> lastUrgent = new HashSet<>();

    CoordinatorOrders(ServerLevel level) {
        this.level = level;
    }

    void applyPlan(CoordinatorPlan plan, List<HelpCall> helpCalls) {
        Map<UUID, CombatDirective> directives = new HashMap<>(CoordinatorDirectives.of(plan));
        Set<UUID> callers = new HashSet<>();
        for (HelpCall call : helpCalls) {
            callers.add(call.caller());
        }
        directives.keySet().removeAll(callers);
        Set<UUID> urgent = new HashSet<>();
        for (AssistOrder assist : plan.assists()) {
            if (assist.urgent()) {
                urgent.add(assist.helper());
            }
        }
        Map<UUID, String> forced = new HashMap<>();
        for (UUID id : new ArrayList<>(directives.keySet())) {
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit != null && !spirit.isRemoved()) {
                applyDirective(spirit, id, directives.get(id), urgent, forced);
            }
        }
        releaseDropped(directives);
    }

    private void applyDirective(
            SpiritEntity spirit, UUID id, CombatDirective d, Set<UUID> urgent, Map<UUID, String> forced) {
        if (lastUrgent.contains(id) && !urgent.contains(id)) {
            lastUrgent.remove(id);
            lastDirectives.remove(id);
            lastForced.remove(id);
            spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
            spirit.tactics().clearGuardZone();
            spirit.tactics().setWard(null);
            if (spirit.currentTaskScript().isPresent()) {
                return;
            }
        }
        if (urgent.contains(id)) {
            CombatDirective last = lastDirectives.get(id);
            if (last == null || !last.equals(d) || !Objects.equals(lastForced.get(id), forced.get(id))) {
                applyDirectiveToSpirit(spirit, d, forced.get(id));
                lastDirectives.put(id, d);
                lastUrgent.add(id);
                rememberForced(id, forced.get(id));
            }
            return;
        }
        if (spirit.currentTaskScript().isPresent()) {
            yieldToTask(spirit, id);
            return;
        }
        CombatDirective last = lastDirectives.get(id);
        if (last == null || !last.equals(d) || !Objects.equals(lastForced.get(id), forced.get(id))) {
            applyDirectiveToSpirit(spirit, d, forced.get(id));
            lastDirectives.put(id, d);
            lastUrgent.remove(id);
            rememberForced(id, forced.get(id));
        }
    }

    private void rememberForced(UUID id, String forcedTargetId) {
        if (forcedTargetId != null) {
            lastForced.put(id, forcedTargetId);
        } else {
            lastForced.remove(id);
        }
    }

    private void yieldToTask(SpiritEntity spirit, UUID id) {
        CombatDirective ours = lastDirectives.get(id);
        CombatDirective current = spirit.tactics().activeDirective().orElse(null);
        lastDirectives.remove(id);
        lastForced.remove(id);
        lastUrgent.remove(id);
        if (ours != null && ours.mode() == CombatMode.AGGRESSIVE && ours.equals(current)) {
            spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
            spirit.tactics().setWard(null);
            return;
        }
        if (current != null && current.equals(ours)) {
            spirit.tactics().holdDirective();
        }
    }

    private void releaseDropped(Map<UUID, CombatDirective> directives) {
        for (UUID id : new ArrayList<>(lastDirectives.keySet())) {
            if (directives.containsKey(id)) {
                continue;
            }
            CombatDirective last = lastDirectives.remove(id);
            lastForced.remove(id);
            lastUrgent.remove(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            CombatDirective current = spirit.tactics().activeDirective().orElse(null);
            if (busy(spirit) && current != null) {
                if (current.equals(last)) {
                    spirit.tactics().holdDirective();
                }
                continue;
            }
            if (spirit.currentTaskScript().isPresent() && current != null && !current.equals(last)) {
                continue;
            }
            spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
            spirit.tactics().clearGuardZone();
            spirit.tactics().setWard(null);
        }
    }

    private static boolean busy(SpiritEntity spirit) {
        return spirit.currentTaskScript().isPresent()
                || spirit.currentPlan().isPresent()
                || spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
    }

    private void applyDirectiveToSpirit(SpiritEntity spirit, CombatDirective d, String forcedTargetId) {
        TacticsService tactics = spirit.tactics();
        tactics.applyDirective(d, DirectiveOwner.COORDINATOR);
        if (d.safePoint().isPresent()) {
            Point safe = d.safePoint().get();
            tactics.setGuardZone(
                    new BlockCoord((int) Math.floor(safe.x()), (int) Math.floor(safe.y()), (int) Math.floor(safe.z())),
                    CoordinatorTuning.DEFENSE_POST_RADIUS);
        }
        if (d.target().isPresent()) {
            TargetSelector.Target t = d.target().get();
            if (t.kind() == TargetSelector.Kind.SPIRIT && t.name().isPresent()) {
                try {
                    tactics.setWard(UUID.fromString(t.name().get()));
                } catch (IllegalArgumentException notUuid) {
                    // lib: UUID.fromString бросает IllegalArgumentException на имени без UUID — цель без него не
                    // ставится
                }
            }
        }
        if (forcedTargetId != null) {
            LivingEntity forced = null;
            try {
                if (level.getEntity(UUID.fromString(forcedTargetId)) instanceof LivingEntity living
                        && living.isAlive()) {
                    forced = living;
                }
            } catch (IllegalArgumentException notUuid) {
                // lib: битый id цели — дух дерётся по своей оценке, а не по пустому следу
            }
            tactics.setForcedTarget(forced);
        } else {
            tactics.setForcedTarget(null);
        }
    }
}
