package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class BoardScenario {
    static final List<BoardCase> CASES = List.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.LIGHTING,
            BoardCase.GATHER_MATERIALS,
            BoardCase.TRANSFER_ITEM,
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_STONE_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.REPAIR_TOOL,
            BoardCase.REPAIR_FENCE,
            BoardCase.FILL_CRATER,
            BoardCase.CRAFT_TORCHES,
            BoardCase.STOCK_CHESTS,
            BoardCase.EAT_TO_FULL,
            BoardCase.RESTOCK_FOOD);
    static final List<String> ITEMS = List.of(
            "minecraft:wooden_pickaxe",
            "minecraft:stone_pickaxe",
            "minecraft:wooden_axe",
            "minecraft:oak_log",
            "minecraft:oak_planks",
            "minecraft:cobblestone",
            "minecraft:bread");
    static final List<String> STOCK = List.of("oak_log", "oak_planks", "cobblestone", "stick", "coal");
    static final EatNeed FED = new EatNeed(20, 20, 20, 20);
    static final EatNeed HUNGRY = new EatNeed(4, 20, 20, 20);

    record Spirit(UUID id, String name, List<String> items, EatNeed eat, SpiritView view) {
        DispatchRound.Candidate candidate() {
            return new DispatchRound.Candidate(id, name, null, items, eat, view);
        }

        Spirit withoutTool(String suffix) {
            List<String> left = new ArrayList<>(items);
            left.removeIf(item -> item.endsWith(suffix));
            return new Spirit(id, name, left, eat, view);
        }
    }

    record Anomaly(String kind, long seed, int round, String detail) {}

    final long seed;
    final Random random;
    final long[] clock = {0};
    final Board board = new Board(() -> clock[0]);
    final List<Spirit> spirits = new ArrayList<>();
    final Map<String, Integer> stock = new HashMap<>();
    final List<Anomaly> anomalies = new ArrayList<>();
    final Set<UUID> dead = new HashSet<>();

    BoardScenario(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        int count = 1 + random.nextInt(4);
        for (int i = 1; i <= count; i++) {
            List<String> items = new ArrayList<>();
            for (String item : ITEMS) {
                if (random.nextInt(3) == 0) {
                    items.add(item);
                }
            }
            EatNeed eat = random.nextInt(5) == 0 ? HUNGRY : FED;
            SpiritView view = new SpiritView(random.nextInt(8) == 0, false, false);
            spirits.add(new Spirit(new UUID(seed, i), "spirit-" + i, items, eat, view));
        }
        for (String item : STOCK) {
            stock.put(item, random.nextInt(3) == 0 ? 0 : random.nextInt(40));
        }
        int tickets = 2 + random.nextInt(14);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < tickets; i++) {
            BoardCase boardCase = CASES.get(random.nextInt(CASES.size()));
            String id = boardCase.name().toLowerCase(Locale.ROOT) + ":" + i;
            List<String> deps = new ArrayList<>();
            if (!ids.isEmpty() && random.nextInt(4) == 0) {
                deps.add(ids.get(random.nextInt(ids.size())));
            }
            Map<String, Integer> required = new HashMap<>();
            if (random.nextInt(3) == 0) {
                required.put(STOCK.get(random.nextInt(STOCK.size())), 1 + random.nextInt(16));
            }
            Optional<UUID> assignee = random.nextInt(5) == 0
                    ? Optional.of(spirits.get(random.nextInt(spirits.size())).id())
                    : Optional.empty();
            board.submit(new BoardTicket(
                    id,
                    boardCase,
                    assignee,
                    BoardRules.priorityOf(boardCase),
                    i,
                    1,
                    BoardTicket.ALWAYS_CAUSE,
                    deps,
                    required));
            ids.add(id);
        }
    }

    StockView stockView() {
        return new StockView(stock);
    }

    List<Spirit> live() {
        return spirits.stream().filter(spirit -> !dead.contains(spirit.id())).toList();
    }

    Optional<Spirit> spirit(UUID id) {
        return live().stream().filter(spirit -> spirit.id().equals(id)).findFirst();
    }

    void dispatch(int round) {
        board.prune(clock[0]);
        for (BoardTicket held : assigned()) {
            Optional<Spirit> holder = board.holderOf(held.id()).flatMap(this::spirit);
            if (holder.isPresent()
                    && ToolGate.keep(held.boardCase(), holder.orElseThrow().items(), false)
                            instanceof ToolGate.Release) {
                board.release(held.id());
            }
        }
        DispatchRound dispatch = new DispatchRound(board, clock[0]);
        List<DispatchRound.Candidate> candidates =
                live().stream().map(Spirit::candidate).toList();
        for (BoardTicket ticket : DispatchRound.order(board.claimable(stockView(), clock[0]))) {
            Optional<DispatchRound.Candidate> chosen = dispatch.choose(ticket, candidates);
            if (chosen.isEmpty()) {
                board.noteWait(ticket.id(), dispatch.why(ticket, candidates));
                continue;
            }
            BoardOutcome outcome = board.claim(ticket.id(), chosen.orElseThrow().id(), clock[0]);
            if (outcome != BoardOutcome.CLAIMED) {
                anomalies.add(new Anomaly("CLAIM_REFUSED", seed, round, ticket.id() + " → " + outcome));
                continue;
            }
            dispatch.take(chosen.orElseThrow().id());
        }
    }

    void events(int round) {
        for (BoardTicket held : assigned()) {
            int roll = random.nextInt(10);
            if (roll < 3) {
                board.complete(held.id());
            } else if (roll == 3) {
                board.fail(held.id(), stockView(), "сцена: провал");
            }
        }
        if (random.nextInt(4) == 0) {
            int index = random.nextInt(spirits.size());
            spirits.set(index, spirits.get(index).withoutTool("_pickaxe"));
        }
        if (random.nextInt(10) == 0 && live().size() > 1) {
            dead.add(live().get(random.nextInt(live().size())).id());
            board.releaseDeadHolders(live().stream().map(Spirit::id).collect(Collectors.toSet()));
        }
        clock[0] += BoardTuning.boardIntervalTicks();
    }

    List<BoardTicket> assigned() {
        List<BoardTicket> held = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) == BoardStatus.ASSIGNED
                    && !BoardActivities.isActivity(ticket.boardCase())) {
                held.add(ticket);
            }
        }
        return held;
    }

    BoardScenario run(int rounds, ScenarioCheck check) {
        for (int round = 0; round < rounds; round++) {
            dispatch(round);
            check.after(this, round);
            events(round);
        }
        return this;
    }

    @FunctionalInterface
    interface ScenarioCheck {
        void after(BoardScenario scenario, int round);
    }
}
