package com.theminespirit.core.board;

import com.theminespirit.core.building.BlockMatch;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class BuildGap {
    public static final String DIG = "проходка";

    public interface Probe {
        String block(BlockCoord at);

        boolean passable(BlockCoord at);
    }

    public interface Schemes {
        Optional<MineLayout> mine(ScriptCall call);

        Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call);
    }

    public record Gap(
            Map<String, Integer> required,
            Map<String, Integer> placed,
            Map<String, Integer> missingItems,
            List<String> missing) {
        public int done() {
            return placed.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int total() {
            return required.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int shortage() {
            return total() - done();
        }
    }

    private BuildGap() {}

    public static Gap trace(TaskNode plan, String wood, Probe probe, Schemes schemes) {
        Objects.requireNonNull(plan, "План постройки не может быть null: по нему считается готовность");
        Accumulator probeState = new Accumulator(wood, probe, schemes);
        probeState.walk(plan);
        return probeState.gap();
    }

    public static Map<String, Integer> need(TaskNode plan, Gap gap, MaterialSource source) {
        Set<String> crafted = new HashSet<>();
        collectCrafted(plan, crafted);
        List<TaskNode> steps = new ArrayList<>();
        gap.missingItems().forEach((item, count) -> {
            if (DIG.equals(item) || count <= 0) {
                return;
            }
            String id = item.contains(":") ? item : "minecraft:" + item;
            steps.add(new ScriptCall(crafted.contains(item) ? "craft" : "acquire", Map.of("item", id, "count", count)));
        });
        return steps.isEmpty() ? Map.of() : CaseMaterials.need(new SequenceNode(List.copyOf(steps)), source);
    }

    private static void collectCrafted(TaskNode node, Set<String> crafted) {
        switch (node) {
            case SequenceNode sequence -> sequence.steps().forEach(step -> collectCrafted(step, crafted));
            case FallbackNode fallback -> collectCrafted(fallback.steps().get(0), crafted);
            case RepeatNode repeat -> collectCrafted(repeat.step(), crafted);
            case ParallelNode parallel -> parallel.steps().forEach(step -> collectCrafted(step, crafted));
            case ConditionNode condition -> {
                collectCrafted(condition.thenStep(), crafted);
                if (condition.elseStep() != null) {
                    collectCrafted(condition.elseStep(), crafted);
                }
            }
            case ScriptCall call
            when "craft".equals(call.script()) && call.params().get("item") instanceof String item ->
                crafted.add(bare(item));
            case ScriptCall ignored -> {}
        }
    }

    private static final class Accumulator {
        private final String wood;
        private final Probe probe;
        private final Schemes schemes;
        private final Map<String, Integer> required = new LinkedHashMap<>();
        private final Map<String, Integer> placed = new LinkedHashMap<>();
        private final Map<String, Integer> missingItems = new LinkedHashMap<>();
        private final List<String> missing = new ArrayList<>();

        private Accumulator(String wood, Probe probe, Schemes schemes) {
            this.wood = wood;
            this.probe = Objects.requireNonNull(probe, "Проба мира не может быть null: по ней видно, что стоит");
            this.schemes = Objects.requireNonNull(schemes, "Схемы не могут быть null: по ним считается план");
        }

        private void walk(TaskNode node) {
            switch (node) {
                case SequenceNode sequence -> sequence.steps().forEach(this::walk);
                case FallbackNode fallback -> walk(fallback.steps().get(0));
                case RepeatNode repeat -> walk(repeat.step());
                case ParallelNode parallel -> parallel.steps().forEach(this::walk);
                case ConditionNode condition -> {
                    walk(condition.thenStep());
                    if (condition.elseStep() != null) {
                        walk(condition.elseStep());
                    }
                }
                case ScriptCall call
                when schemes.mine(call).isPresent() -> mine(schemes.mine(call).orElseThrow());
                case ScriptCall call
                when schemes.entries(call).isPresent() -> {
                    for (Schematic.PlacedEntry entry : schemes.entries(call).orElseThrow()) {
                        expect(entry.pos(), entry.block());
                    }
                }
                case ScriptCall call
                when "place_block".equals(call.script())
                        && call.params().get("block") instanceof String block
                        && call.params().get("x") instanceof Integer x
                        && call.params().get("y") instanceof Integer y
                        && call.params().get("z") instanceof Integer z -> expect(new BlockCoord(x, y, z), block);
                case ScriptCall ignored -> {}
            }
        }

        private void expect(BlockCoord at, String block) {
            String item = bare(WoodSpecies.swap(SchematicBlocks.itemOf(block), wood));
            count(at, item, () -> BlockMatch.matches(probe.block(at), item), " " + item);
        }

        private void mine(MineLayout layout) {
            String floor = bare(SchematicBlocks.itemOf(SchematicBlocks.placementBlock(SchematicBlocks.FLOOR)));
            for (BlockCoord cell : layout.floors()) {
                String standing = probe.block(cell);
                count(cell, floor, () -> SchematicBlocks.isFloorClass(standing), " " + floor);
            }
            for (BlockCoord cell : layout.air()) {
                String standing = probe.block(cell);
                count(cell, DIG, () -> probe.passable(cell), " копать: " + standing);
            }
        }

        private void count(BlockCoord at, String item, BooleanSupplier done, String suffix) {
            required.merge(item, 1, Integer::sum);
            if (done.getAsBoolean()) {
                placed.merge(item, 1, Integer::sum);
            } else {
                missingItems.merge(item, 1, Integer::sum);
                missing.add(at + suffix);
            }
        }

        private Gap gap() {
            return new Gap(Map.copyOf(required), Map.copyOf(placed), Map.copyOf(missingItems), List.copyOf(missing));
        }
    }

    private static String bare(String id) {
        int state = id.indexOf('[');
        String block = state < 0 ? id : id.substring(0, state);
        return block.startsWith("minecraft:") ? block.substring("minecraft:".length()) : block;
    }
}
