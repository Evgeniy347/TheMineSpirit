package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.tactics.ArmorModel;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TacticalIntellect {
    private final ActivityCatalog catalog;
    private final ActivityAssigner assigner;
    private final EnemyProfiles profiles;
    private final ArmorModel armor;
    private final ExpeditionPlanner expeditions = new ExpeditionPlanner();
    private final OutsideGate outsideGate = OutsideGate.withDefaults();
    private List<EnemyFact> enemies = List.of();
    private Point vein;
    private boolean weaponStock;
    private boolean playerEscort;

    public TacticalIntellect(ActivityCatalog catalog, EnemyProfiles profiles, ArmorModel armor) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "Интеллект без каталога не распределяет: деятельности — классы каталога");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("Интеллект без профилей врагов не оценивает силы: урон — из enemies.json");
        }
        if (armor == null) {
            throw new IllegalArgumentException("Интеллект без модели брони не оценивает силы: входит в запас победы");
        }
        this.catalog = catalog;
        this.assigner = new ActivityAssigner(catalog);
        this.profiles = profiles;
        this.armor = armor;
    }

    public void trackEnemies(List<EnemyFact> sighted) {
        if (sighted == null) {
            throw new IllegalArgumentException("Наблюдение без списка врагов не учитывается: пустой список — нет угроз");
        }
        enemies = List.copyOf(sighted);
    }

    public void trackVein(Point knownVein) {
        vein = knownVein;
    }

    public void trackWeaponStock(boolean available) {
        weaponStock = available;
    }

    public void trackPlayerEscort(boolean accompanied) {
        playerEscort = accompanied;
    }

    public OutsideGate outsideGate() {
        return outsideGate;
    }

    public TacticalDecision decide(SettlementView view, ActivityLedger ledger, WorkReservations reservations,
            StrategicQueue queue, long now) {
        if (view == null) {
            throw new IllegalArgumentException("Решение без обстановки не принимается: зависит от нужд поселения");
        }
        if (ledger == null) {
            throw new IllegalArgumentException("Решение без учёта занятий дёргает задачи: нужен ledger");
        }
        if (reservations == null) {
            throw new IllegalArgumentException("Решение без броней раздаст занятые цели: нужен реестр координатора");
        }
        if (queue == null) {
            throw new IllegalArgumentException("Решение без очереди стратега теряет приказы: пустая очередь — не null");
        }
        if (now < 0) {
            throw new IllegalArgumentException("Решение в тике " + now + ": время не может быть отрицательным");
        }
        queue.prune(now);
        List<StrategicOrder> orders = new ArrayList<>();
        Set<UUID> ordered = new HashSet<>();
        for (SpiritSnapshot spirit : view.spirits()) {
            Optional<StrategicOrder> head = queue.poll(spirit.id());
            if (head.isPresent()) {
                orders.add(head.orElseThrow());
                ordered.add(spirit.id());
            }
        }
        List<SpiritSnapshot> free = view.spirits().stream().filter(spirit -> !ordered.contains(spirit.id())).toList();
        List<StrengthVerdict> strengths =
                StrengthAssessor.assess(free, enemies, profiles, armor, weaponStock);
        Set<UUID> judged = new HashSet<>();
        for (StrengthVerdict verdict : strengths) {
            judged.add(verdict.spirit());
        }
        Set<UUID> displaced = new HashSet<>(ordered);
        displaced.addAll(judged);
        for (UUID spirit : displaced) {
            ledger.clear(spirit);
        }
        SettlementView workers = new SettlementView(view.population(), view.children(), view.foodPerSoul(),
                view.foodNutritionPerSoul(), view.ore(), view.iron(), view.weapons(), view.threats(),
                view.dayFraction(), view.farmWork(), view.forage(),
                view.spirits().stream().filter(spirit -> !displaced.contains(spirit.id())).toList(),
                view.tick(), view.processing(), view.inventoryFills());
        List<String> refusals = new ArrayList<>();
        List<ActivityDemand> demands = admitOutside(workers, DemandModel.evaluate(view, catalog), refusals);
        ActivityPlan plan = assigner.assign(workers, demands, ledger, now);
        for (ActivityAssignment assignment : plan.assignments()) {
            ledger.assign(assignment.spirit(), assignment.activity(), assignment.since(), assignment.demand());
        }
        for (UUID departed : plan.dropped()) {
            ledger.clear(departed);
            reservations.releaseAll(departed);
        }
        reservations.expire(now);
        Optional<Expedition> expedition =
                vein == null ? Optional.empty() : expeditions.plan(view, workers.freeWorkers(), vein);
        return new TacticalDecision(List.copyOf(orders), strengths, plan, expedition, refusals);
    }

    private List<ActivityDemand> admitOutside(SettlementView workers, List<ActivityDemand> demands,
            List<String> refusals) {
        List<ActivityDemand> allowed = new ArrayList<>();
        boolean evening = outsideGate.isEvening(workers.dayFraction());
        for (ActivityDemand demand : demands) {
            Activity activity = catalog.byId(demand.id());
            if (!activity.external()) {
                allowed.add(demand);
                continue;
            }
            int party = 0;
            for (SpiritSnapshot spirit : workers.spirits()) {
                if (activity.eligible(spirit, workers)) {
                    party++;
                }
            }
            OutsideGate.Decision decision = outsideGate.admitExternal(party, playerEscort, evening);
            if (decision instanceof OutsideGate.Refused refused) {
                refusals.add(demand.id() + ": " + refused.reason());
            } else {
                allowed.add(demand);
            }
        }
        return allowed;
    }
}
