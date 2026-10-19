package com.theminespirit.core.intellect;

import static com.theminespirit.core.Lookup.present;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.CraftActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.ForageActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.intellect.activity.SmeltActivity;
import com.theminespirit.core.intellect.activity.UnloadActivity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class ActivityAssigner {
    private final ActivityCatalog catalog;

    public ActivityAssigner() {
        this(ActivityCatalog.adultsOnly(List.of(
                new ChopActivity(),
                new FarmActivity(),
                new ForageActivity(),
                new MineActivity(),
                new RestActivity(),
                new DefendActivity(),
                new UnloadActivity(),
                new SmeltActivity(),
                new CraftActivity())));
    }

    public ActivityAssigner(ActivityCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "Распределитель без каталога не проверяет пригодность: деятельности — классы каталога");
        }
        this.catalog = catalog;
    }

    public ActivityPlan assign(SettlementView view, List<ActivityDemand> demands, ActivityLedger ledger, long now) {
        if (view == null) {
            throw new IllegalArgumentException("Распределение без обстановки не считается: зависит от нужд поселения");
        }
        if (demands == null) {
            throw new IllegalArgumentException("Распределение без спроса не считается: спрос даёт DemandModel");
        }
        if (ledger == null) {
            throw new IllegalArgumentException("Распределение без учёта занятий дёргает задачи: нужен ledger");
        }
        if (now < 0) {
            throw new IllegalArgumentException("Распределение в тике " + now + ": время не может быть отрицательным");
        }
        Map<ActivityId, Double> demandById = demandById(demands);
        Set<UUID> present = new HashSet<>();
        for (SpiritSnapshot spirit : view.spirits()) {
            present.add(spirit.id());
        }
        List<UUID> dropped = ledger.all().stream()
                .map(ActivityAssignment::spirit)
                .filter(id -> !present.contains(id))
                .sorted()
                .toList();
        List<ActivityAssignment> assignments = new ArrayList<>();
        Map<ActivityId, Integer> taken = new EnumMap<>(ActivityId.class);
        int defenders = 0;
        int defendersNeeded =
                Math.min(view.threats(), IntellectTuning.loadDefault().maxFighters());
        for (SpiritSnapshot spirit : view.spirits()) {
            Optional<ActivityAssignment> current = ledger.of(spirit.id());
            if (current.isEmpty() && spirit.workTarget().isPresent()) {
                continue;
            }
            ChoiceContext context = new ChoiceContext(demands, demandById, taken, current, now, defendersNeeded);
            ActivityId chosen = choose(spirit, view, context, defenders);
            if (chosen == null) {
                continue;
            }
            long since = current.isPresent() && current.get().activity() == chosen
                    ? current.get().since()
                    : now;
            assignments.add(new ActivityAssignment(
                    spirit.id(), chosen, since, present(demandById, chosen, "Спрос деятельностей")));
            taken.merge(chosen, 1, Integer::sum);
            if (chosen == ActivityId.DEFEND) {
                defenders++;
            }
        }
        return new ActivityPlan(assignments, dropped);
    }

    private @Nullable ActivityId choose(
            SpiritSnapshot spirit, SettlementView view, ChoiceContext context, int defenders) {
        List<ActivityDemand> demands = context.demands();
        Map<ActivityId, Double> demandById = context.demandById();
        Map<ActivityId, Integer> taken = context.taken();
        Optional<ActivityAssignment> current = context.current();
        long now = context.now();
        int defendersNeeded = context.defendersNeeded();
        IntellectTuning tuning = IntellectTuning.loadDefault();
        boolean threatened = spirit.threatsNear() > 0 || spirit.attacked();
        boolean defendOffered = demandById.containsKey(ActivityId.DEFEND);
        if (view.threats() > 0 && threatened && defendOffered && eligible(ActivityId.DEFEND, spirit, view)) {
            return ActivityId.DEFEND;
        }
        if (view.threats() > 0
                && defendOffered
                && defenders < defendersNeeded
                && eligible(ActivityId.DEFEND, spirit, view)) {
            return ActivityId.DEFEND;
        }
        ActivityId best = bestScored(spirit, view, demands, taken, null);
        if (best == null) {
            return null;
        }
        if (best == ActivityId.DEFEND && defenders >= defendersNeeded && view.threats() > 0) {
            best = bestScored(spirit, view, demands, taken, ActivityId.DEFEND);
            if (best == null) {
                return null;
            }
        }
        if (current.isPresent()
                && current.get().activity() != ActivityId.DEFEND
                && demandById.containsKey(current.get().activity())
                && eligible(current.get().activity(), spirit, view)
                && now - current.get().since() < tuning.minSwitchTicks()
                && scored(best, demandById, taken)
                        < demandById.get(current.get().activity()) + tuning.hysteresisMargin()) {
            return current.get().activity();
        }
        return best;
    }

    private @Nullable ActivityId bestScored(
            SpiritSnapshot spirit,
            SettlementView view,
            List<ActivityDemand> demands,
            Map<ActivityId, Integer> taken,
            @Nullable ActivityId excluded) {
        IntellectTuning tuning = IntellectTuning.loadDefault();
        ActivityId best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ActivityDemand demand : demands) {
            if (demand.id() == excluded || !eligible(demand.id(), spirit, view)) {
                continue;
            }
            double score = demand.demand() - tuning.crowdPenalty() * taken.getOrDefault(demand.id(), 0);
            if (best == null
                    || score > bestScore
                    || (score == bestScore && demand.id().compareTo(best) < 0)) {
                best = demand.id();
                bestScore = score;
            }
        }
        return best;
    }

    private double scored(ActivityId id, Map<ActivityId, Double> demandById, Map<ActivityId, Integer> taken) {
        return present(demandById, id, "Спрос деятельностей")
                - IntellectTuning.loadDefault().crowdPenalty() * taken.getOrDefault(id, 0);
    }

    private boolean eligible(ActivityId id, SpiritSnapshot spirit, SettlementView view) {
        return catalog.byId(id).eligible(spirit, view);
    }

    private static Map<ActivityId, Double> demandById(List<ActivityDemand> demands) {
        Map<ActivityId, Double> demandById = new EnumMap<>(ActivityId.class);
        for (ActivityDemand demand : demands) {
            if (demand == null) {
                throw new IllegalArgumentException(
                        "Спрос с null-записью не распределяется: каждая запись — ActivityDemand");
            }
            if (demandById.put(demand.id(), demand.demand()) != null) {
                throw new IllegalArgumentException(
                        "Спрос " + demand.id() + " дважды в списке: распределитель не решит, какое число верное");
            }
        }
        return demandById;
    }

    private record ChoiceContext(
            List<ActivityDemand> demands,
            Map<ActivityId, Double> demandById,
            Map<ActivityId, Integer> taken,
            Optional<ActivityAssignment> current,
            long now,
            int defendersNeeded) {}
}
