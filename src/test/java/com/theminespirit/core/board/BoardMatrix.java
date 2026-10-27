package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class BoardMatrix {
    static final UUID SPIRIT = new UUID(7, 1);
    static final UUID OTHER = new UUID(7, 2);

    enum Materials {
        NONE,
        MISSING,
        ENOUGH
    }

    enum Tool {
        BARE(List.of()),
        WOODEN_AXE(List.of("minecraft:wooden_axe")),
        WOODEN_PICKAXE(List.of("minecraft:wooden_pickaxe")),
        STONE_PICKAXE(List.of("minecraft:stone_pickaxe"));

        final List<String> items;

        Tool(List<String> items) {
            this.items = items;
        }
    }

    enum State {
        FREE(BoardScenario.FED, SpiritView.free()),
        HUNGRY(BoardScenario.HUNGRY, SpiritView.free()),
        COMBAT(BoardScenario.FED, new SpiritView(true, false, false)),
        HOLDS_OTHER(BoardScenario.FED, SpiritView.free()),
        PAUSED(BoardScenario.FED, SpiritView.free());

        final EatNeed eat;
        final SpiritView view;

        State(EatNeed eat, SpiritView view) {
            this.eat = eat;
            this.view = view;
        }
    }

    enum Dependency {
        NONE,
        PENDING,
        DONE
    }

    enum Owner {
        OPEN,
        OWN,
        FOREIGN
    }

    record Situation(
            BoardCase boardCase, Materials materials, Tool tool, State state, Dependency dependency, Owner owner) {
        @Override
        public String toString() {
            return boardCase + "/" + materials + "/" + tool + "/" + state + "/" + dependency + "/" + owner;
        }
    }

    enum Outcome {
        ISSUED,
        WAITS,
        REFUSED_BY_BOARD
    }

    record Result(Outcome outcome, String why) {}

    private BoardMatrix() {}

    static List<Situation> situations(BoardCase boardCase) {
        List<Situation> all = new ArrayList<>();
        for (Materials materials : Materials.values()) {
            for (Tool tool : Tool.values()) {
                for (State state : State.values()) {
                    for (Dependency dependency : Dependency.values()) {
                        for (Owner owner : Owner.values()) {
                            all.add(new Situation(boardCase, materials, tool, state, dependency, owner));
                        }
                    }
                }
            }
        }
        return all;
    }

    static Result run(Situation situation) {
        long[] clock = {0};
        Board board = new Board(() -> clock[0]);
        BoardCase boardCase = situation.boardCase();
        int priority = BoardRules.priorityOf(boardCase);
        List<String> deps = new ArrayList<>();
        if (situation.dependency() != Dependency.NONE) {
            board.submit(ticket("before", BoardCase.GATHER_MATERIALS, Optional.empty(), List.of(), Map.of()));
            if (situation.dependency() == Dependency.DONE) {
                board.claim("before", OTHER, 0);
                board.complete("before");
            }
            deps.add("before");
        }
        if (situation.state() == State.HOLDS_OTHER) {
            board.submit(ticket("held", BoardCase.GATHER_MATERIALS, Optional.empty(), List.of(), Map.of()));
            board.claim("held", SPIRIT, 0);
        }
        if (situation.state() == State.PAUSED) {
            board.submit(ticket("failed", boardCase, Optional.empty(), List.of(), Map.of()));
            board.claim("failed", SPIRIT, 0);
            board.fail("failed");
            clock[0] = 1;
        }
        Map<String, Integer> required = situation.materials() == Materials.NONE ? Map.of() : Map.of("cobblestone", 8);
        Optional<UUID> assignee = switch (situation.owner()) {
            case OPEN -> Optional.empty();
            case OWN -> Optional.of(SPIRIT);
            case FOREIGN -> Optional.of(OTHER);
        };
        BoardOutcome submitted = board.submit(new BoardTicket(
                "task", boardCase, assignee, priority, clock[0], 1, BoardTicket.ALWAYS_CAUSE, deps, required));
        if (submitted != BoardOutcome.ACCEPTED) {
            return new Result(Outcome.REFUSED_BY_BOARD, submitted.name());
        }
        StockView stock = new StockView(
                situation.materials() == Materials.ENOUGH ? Map.of("cobblestone", 64) : Map.of("cobblestone", 0));
        DispatchRound round = new DispatchRound(board, clock[0]);
        DispatchRound.Candidate spirit = new DispatchRound.Candidate(
                SPIRIT,
                "spirit-1",
                situation.state() == State.COMBAT ? "тактика держит (бой)" : null,
                situation.tool().items,
                situation.state().eat,
                situation.state().view);
        for (BoardTicket ticket : DispatchRound.order(board.claimable(stock, clock[0]))) {
            if (!ticket.id().equals("task")) {
                continue;
            }
            Optional<String> refusal = round.refusal(ticket, spirit);
            if (refusal.isPresent()) {
                return new Result(Outcome.WAITS, refusal.orElseThrow());
            }
            BoardOutcome claim = board.claim("task", SPIRIT, clock[0]);
            return claim == BoardOutcome.CLAIMED
                    ? new Result(Outcome.ISSUED, "")
                    : new Result(Outcome.WAITS, claim.name());
        }
        return new Result(
                Outcome.WAITS, "не в очереди: " + board.statusOf("task").orElseThrow());
    }

    private static BoardTicket ticket(
            String id, BoardCase boardCase, Optional<UUID> assignee, List<String> deps, Map<String, Integer> items) {
        return new BoardTicket(
                id, boardCase, assignee, BoardRules.priorityOf(boardCase), 0, 1, BoardTicket.ALWAYS_CAUSE, deps, items);
    }
}
