package com.theminespirit.core.ui;

import com.theminespirit.core.board.StageRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SettlementTrees {
    private static final int PERCENT = 100;

    private record Draft(
            String id,
            SettlementTree.Kind kind,
            SettlementTree.State state,
            String title,
            String icon,
            int level,
            int percent,
            boolean optional,
            boolean blocking,
            SettlementTree.@Nullable Position at,
            Map<String, Integer> missing,
            List<String> requirements,
            String detail) {
        SettlementTree.Node node(SettlementTree.Point place) {
            return new SettlementTree.Node(
                    id,
                    kind,
                    state,
                    title,
                    icon,
                    level,
                    percent,
                    optional,
                    blocking,
                    place.x(),
                    place.y(),
                    at,
                    missing,
                    requirements,
                    detail);
        }
    }

    private SettlementTrees() {}

    public static SettlementTree build(
            StageRules rules, SettlementTreeFacts facts, SettlementTreeIcons icons, int currentStage, int stage) {
        Objects.requireNonNull(rules, "Правила этапов не могут быть null: по ним строится древо");
        Objects.requireNonNull(facts, "Состояние мира не может быть null: по нему считаются проценты");
        Objects.requireNonNull(icons, "Таблица иконок не может быть null: без неё рамки пустые");
        if (stage < 1 || stage > rules.stages().size()) {
            throw new IllegalArgumentException("Этап " + stage + " вне 1.."
                    + rules.stages().size() + ": вкладка открывает только существующий этап");
        }
        StageRules.Stage rule = rules.stages().get(stage - 1);
        String rootId = "s" + stage + ":root";
        String gateId = "s" + stage + ":gate";
        List<Draft> drafts = new ArrayList<>();
        drafts.add(new Draft(
                rootId,
                SettlementTree.Kind.ROOT,
                SettlementTree.State.READY,
                stage == 1 ? "Создать поселение" : "Этап " + stage,
                icons.rootIcon(),
                0,
                PERCENT,
                false,
                false,
                null,
                Map.of(),
                List.of(),
                ""));
        List<SettlementTreeLayout.Branch> branches = new ArrayList<>();
        List<String> gateParents = new ArrayList<>();
        List<SettlementTreeObjects.Ref> refs = SettlementTreeObjects.of(rule, icons);
        for (SettlementTreeObjects.Ref ref : refs) {
            Draft base = baseDraft(stage, ref, facts);
            drafts.add(base);
            List<String> chain = new ArrayList<>();
            chain.add(base.id());
            if (ref.blocking()) {
                gateParents.add(base.id());
            }
            SettlementTree.State previous = base.state();
            int maxLevel = SettlementTreeObjects.maxLevel(ref);
            for (int level = ref.level() + 1; level <= maxLevel; level++) {
                Draft upgrade = upgradeDraft(stage, ref, level, facts, previous, base.title());
                drafts.add(upgrade);
                chain.add(upgrade.id());
                previous = upgrade.state();
            }
            branches.add(new SettlementTreeLayout.Branch(chain));
        }
        Map<String, SettlementTree.State> states = new HashMap<>();
        drafts.forEach(draft -> states.put(draft.id(), draft.state()));
        boolean gateReady = gateParents.stream()
                .allMatch(id -> states.getOrDefault(id, SettlementTree.State.LOCKED) == SettlementTree.State.READY);
        List<String> gateMissing = gateParents.stream()
                .filter(id -> states.getOrDefault(id, SettlementTree.State.LOCKED) != SettlementTree.State.READY)
                .map(id -> titleOf(drafts, id))
                .toList();
        drafts.add(gateDraft(stage, rules.stages().size(), icons, gateReady, gateMissing));
        SettlementTreeLayout.Result layout = SettlementTreeLayout.of(rootId, branches, gateParents, gateId);
        List<SettlementTree.Node> nodes = drafts.stream()
                .map(draft -> draft.node(place(layout.places(), draft.id())))
                .toList();
        return new SettlementTree(
                stage, rules.stages().size(), currentStage, nodes, layout.edges(), layout.width(), layout.height());
    }

    private static Draft baseDraft(int stage, SettlementTreeObjects.Ref ref, SettlementTreeFacts facts) {
        SettlementTreeFacts.Fact fact = facts.fact(key(ref, ref.level()));
        SettlementTree.State state = stateOf(fact, true);
        return new Draft(
                nodeId(stage, ref, ref.level()),
                SettlementTree.Kind.BUILDING,
                state,
                ref.title(),
                ref.icon(),
                ref.level(),
                percentOf(state, fact),
                ref.optional(),
                ref.blocking(),
                fact == null ? null : fact.at(),
                fact == null ? Map.of() : fact.missing(),
                List.of(),
                fact == null ? "" : fact.detail());
    }

    private static Draft upgradeDraft(
            int stage,
            SettlementTreeObjects.Ref ref,
            int level,
            SettlementTreeFacts facts,
            SettlementTree.State previous,
            String previousTitle) {
        SettlementTreeFacts.Fact fact = facts.fact(key(ref, level));
        SettlementTree.State state = stateOf(fact, previous == SettlementTree.State.READY);
        List<String> requirements = previous == SettlementTree.State.READY
                ? List.of()
                : List.of(previousTitle + " (уровень " + (level - 1) + ")");
        return new Draft(
                nodeId(stage, ref, level),
                SettlementTree.Kind.UPGRADE,
                state,
                ref.title(),
                ref.icon(),
                level,
                percentOf(state, fact),
                false,
                false,
                fact == null ? null : fact.at(),
                fact == null ? Map.of() : fact.missing(),
                requirements,
                fact == null ? "" : fact.detail());
    }

    private static Draft gateDraft(
            int stage, int stages, SettlementTreeIcons icons, boolean ready, List<String> missing) {
        return new Draft(
                "s" + stage + ":gate",
                SettlementTree.Kind.GATE,
                ready ? SettlementTree.State.READY : SettlementTree.State.LOCKED,
                stage < stages ? "Этап " + (stage + 1) + " открыт" : "Поселение завершено",
                icons.gateIcon(),
                0,
                ready ? PERCENT : 0,
                false,
                false,
                null,
                Map.of(),
                missing,
                "");
    }

    static SettlementTree.State stateOf(SettlementTreeFacts.@Nullable Fact fact, boolean predecessorReady) {
        if (!predecessorReady) {
            return SettlementTree.State.LOCKED;
        }
        if (fact == null) {
            return SettlementTree.State.NOT_STARTED;
        }
        if (fact.unknown()) {
            return SettlementTree.State.UNKNOWN;
        }
        if (fact.ready()) {
            return SettlementTree.State.READY;
        }
        return fact.percent() > 0 ? SettlementTree.State.IN_PROGRESS : SettlementTree.State.NOT_STARTED;
    }

    static int percentOf(SettlementTree.State state, SettlementTreeFacts.@Nullable Fact fact) {
        if (state == SettlementTree.State.READY) {
            return PERCENT;
        }
        if (state != SettlementTree.State.IN_PROGRESS && state != SettlementTree.State.NOT_STARTED) {
            return 0;
        }
        return fact == null ? 0 : fact.percent();
    }

    private static String key(SettlementTreeObjects.Ref ref, int level) {
        return ref.planId() != null
                ? SettlementTreeFacts.planKey(ref.planId(), level)
                : SettlementTreeFacts.boardKey(Objects.requireNonNull(ref.boardCase()), level);
    }

    private static String nodeId(int stage, SettlementTreeObjects.Ref ref, int level) {
        String owner = ref.planId() != null
                ? "plan:" + ref.planId()
                : "case:" + Objects.requireNonNull(ref.boardCase()).name();
        return "s" + stage + ":" + owner + ":" + level;
    }

    private static String titleOf(List<Draft> drafts, String id) {
        return drafts.stream()
                .filter(draft -> draft.id().equals(id))
                .map(Draft::title)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Рамка '" + id + "' не найдена среди объектов этапа: ворота не знают, чего ждут"));
    }

    private static SettlementTree.Point place(Map<String, SettlementTree.Point> places, String id) {
        SettlementTree.Point place = places.get(id);
        if (place == null) {
            throw new IllegalStateException("Рамка '" + id + "' не разложена: узел древа остался без места");
        }
        return place;
    }
}
