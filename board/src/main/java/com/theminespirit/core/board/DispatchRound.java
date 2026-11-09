package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class DispatchRound {
    public record Candidate(
            UUID id,
            String name,
            @Nullable String busyWith,
            List<String> items,
            EatNeed eat,
            SpiritView view,
            boolean wet,
            List<String> stockTools,
            Map<String, String> needs,
            List<String> wornTools) {
        public Candidate(
                UUID id, String name, @Nullable String busyWith, List<String> items, EatNeed eat, SpiritView view) {
            this(id, name, busyWith, items, eat, view, false, List.of(), Map.of(), List.of());
        }

        public Candidate(
                UUID id,
                String name,
                @Nullable String busyWith,
                List<String> items,
                EatNeed eat,
                SpiritView view,
                boolean wet) {
            this(id, name, busyWith, items, eat, view, wet, List.of(), Map.of(), List.of());
        }

        public Candidate(
                UUID id,
                String name,
                @Nullable String busyWith,
                List<String> items,
                EatNeed eat,
                SpiritView view,
                boolean wet,
                List<String> stockTools) {
            this(id, name, busyWith, items, eat, view, wet, stockTools, Map.of(), List.of());
        }

        public Candidate {
            Objects.requireNonNull(id, "Id духа выдачи не может быть null: задание выдаётся конкретному духу");
            Objects.requireNonNull(name, "Имя духа выдачи не может быть null: оно пишется в причину ожидания");
            items = List.copyOf(items);
            Objects.requireNonNull(eat, "Сытость духа выдачи не может быть null: по ней решают еду");
            Objects.requireNonNull(view, "Состояние духа выдачи не может быть null: по нему решают применимость");
            stockTools = List.copyOf(Objects.requireNonNull(
                    stockTools, "Инструменты склада не могут быть null: пустой склад задают пустым списком"));
            needs = Map.copyOf(Objects.requireNonNull(
                    needs, "Живые нужды духа не могут быть null: у сытого и здорового — пустая карта"));
            wornTools = List.copyOf(Objects.requireNonNull(
                    wornTools, "Изношенные инструменты не могут быть null: целый инструмент — пустой список"));
        }
    }

    private final Board board;
    private final long now;
    private final Map<UUID, Integer> held;
    private final Map<UUID, String> awaiting = new HashMap<>();
    private final LifePreemption preemption = new LifePreemption();

    public DispatchRound(Board board, long now) {
        this.board = Objects.requireNonNull(board, "Доска раунда выдачи не может быть null: задания берутся с неё");
        this.now = now;
        this.held = new HashMap<>(board.assignedCounts());
    }

    public static boolean tooLowForCleanup(BoardCase boardCase, int spiritY, int targetY) {
        return boardCase == BoardCase.DISMANTLE_SHELTER && spiritY < targetY - 4;
    }

    public static int stageCasesOpen(Board board) {
        Objects.requireNonNull(board, "Доска не может быть null: открытые шаги этапа считают по ней");
        Set<BoardCase> cases = EnumSet.noneOf(BoardCase.class);
        for (BoardTicket ticket : board.all()) {
            if (!StageTicket.stage(ticket.id())) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                cases.add(ticket.boardCase());
            }
        }
        return cases.size();
    }

    public static List<BoardTicket> order(List<BoardTicket> claimable) {
        List<BoardTicket> own = new ArrayList<>();
        List<BoardTicket> open = new ArrayList<>();
        for (BoardTicket ticket : claimable) {
            (ticket.assignee().isPresent() ? own : open).add(ticket);
        }
        own.addAll(open);
        return List.copyOf(own);
    }

    public Board board() {
        return board;
    }

    public Optional<String> refusal(BoardTicket ticket, Candidate spirit) {
        return refusal(ticket, spirit, null);
    }

    public Optional<String> refusal(BoardTicket ticket, Candidate spirit, @Nullable String supplyItem) {
        if (ticket.boardCase() == BoardCase.DISMANTLE_SHELTER && spirit.wet()) {
            return Optional.of("в воде или под крышей: до стоянки уборки не дойдёт");
        }
        boolean ownRescue = ticket.boardCase() == BoardCase.RESCUE_SPIRIT
                && ticket.assignee().filter(spirit.id()::equals).isPresent();
        if (!ownRescue) {
            Optional<StuckSpirits.Stuck> stuck = board.stuck().of(spirit.id(), now);
            if (stuck.isPresent()) {
                StuckSpirits.Stuck mark = stuck.orElseThrow();
                return Optional.of((mark.noPath() ? "пути нет: " : "")
                        + "не двигается с тика " + mark.since()
                        + (mark.at() == null ? "" : " в " + mark.at())
                        + ": ждёт выход из ловушки (граф ставит rescue)");
            }
        }
        Optional<String> vital =
                VitalGate.refusal(ticket, VitalChain.withPath(spirit.needs(), board, spirit.id()), VitalNeeds.needs());
        if (vital.isPresent()) {
            return vital;
        }
        if (spirit.busyWith() != null && !preemption.freed(spirit.id())) {
            return Optional.of("занят: " + spirit.busyWith());
        }
        String waits = awaiting.get(spirit.id());
        if (waits != null) {
            return Optional.of(waits);
        }
        if (held.getOrDefault(spirit.id(), 0) >= BoardTuning.maxActivePerSpirit()) {
            return Optional.of("держит " + heldIds(spirit.id()));
        }
        if (ticket.assignee().filter(owner -> !owner.equals(spirit.id())).isPresent() && !reassignable(ticket, now)) {
            return Optional.of("задание другого духа");
        }
        if (!board.availableTo(spirit.id(), ticket, now)) {
            return Optional.of("на паузе кейса " + ticket.boardCase() + " после "
                    + board.consecutiveFailures(spirit.id(), ticket.boardCase()) + " провалов");
        }
        if (!ToolGate.carries(ticket, spirit.items())) {
            return Optional.of("нет запаса " + ToolGate.carry(ticket).orElse("?")
                    + " в инвентаре: сначала взять со склада или скрафтить");
        }
        if (ToolGate.worn(ticket, spirit.items(), spirit.wornTools())) {
            return Optional.of("инструмент изношен: сначала починка или замена по графу, "
                    + ToolGate.required(ticket).orElse("?"));
        }
        if (!EvergreenRules.issuedTo(ticket, spirit.id(), spirit.eat(), spirit.view())) {
            return Optional.of("кейс " + ticket.boardCase() + " ему сейчас не к месту");
        }
        return Optional.empty();
    }

    public boolean fits(BoardTicket ticket, Candidate spirit) {
        return fits(ticket, spirit, null);
    }

    public boolean fits(BoardTicket ticket, Candidate spirit, @Nullable String supplyItem) {
        return refusal(ticket, spirit, supplyItem).isEmpty();
    }

    private boolean reassignable(BoardTicket ticket, long now) {
        return ticket.boardCase() == BoardCase.TRANSFER_ITEM
                && ticket.assignee()
                        .filter(owner -> board.stuck().isStuck(owner, now))
                        .isPresent();
    }

    public Optional<Candidate> choose(BoardTicket ticket, List<Candidate> preference) {
        return choose(ticket, preference, null);
    }

    public Optional<Candidate> choose(BoardTicket ticket, List<Candidate> preference, @Nullable String supplyItem) {
        for (Candidate spirit : preference) {
            if (tooled(ticket, spirit) && accepts(ticket, spirit, supplyItem)) {
                return Optional.of(spirit);
            }
        }
        for (Candidate spirit : preference) {
            if (!tooled(ticket, spirit) && accepts(ticket, spirit, supplyItem)) {
                return Optional.of(spirit);
            }
        }
        return Optional.empty();
    }

    private boolean accepts(BoardTicket ticket, Candidate spirit, @Nullable String supplyItem) {
        Optional<String> refusal = refusal(ticket, spirit, supplyItem);
        if (refusal.isEmpty()) {
            return true;
        }
        String reason = refusal.orElseThrow();
        if (!reason.startsWith("занят: ") && !reason.startsWith("держит ")) {
            return false;
        }
        Optional<BoardTicket> released =
                preemption.displace(ticket, spirit, board, VitalGate.satisfies(ticket.id(), spirit.needs()));
        if (released.isEmpty()) {
            return false;
        }
        held.computeIfPresent(spirit.id(), (ignored, count) -> Math.max(0, count - 1));
        return refusal(ticket, spirit, supplyItem).isEmpty();
    }

    public List<BoardTicket> drainDisplaced(UUID spirit) {
        return preemption.drain(spirit);
    }

    private static boolean tooled(BoardTicket ticket, Candidate spirit) {
        if (ToolGate.allowsFromStock(ticket, spirit.stockTools())) {
            return true;
        }
        return ToolGate.allows(ticket, spirit.items()) && !ToolGate.worn(ticket, spirit.items(), spirit.wornTools());
    }

    public void take(UUID spirit) {
        held.merge(spirit, 1, Integer::sum);
    }

    public void await(UUID spirit, String why) {
        awaiting.putIfAbsent(spirit, why);
    }

    public void release(UUID spirit) {
        awaiting.remove(spirit);
    }

    public boolean awaited(UUID spirit) {
        return awaiting.containsKey(spirit);
    }

    public String why(BoardTicket ticket, List<Candidate> spirits) {
        return why(ticket, spirits, null);
    }

    public String why(BoardTicket ticket, List<Candidate> spirits, @Nullable String supplyItem) {
        return DispatchWhy.text(this, ticket, spirits, supplyItem);
    }

    private String heldIds(UUID spirit) {
        List<String> ids = new ArrayList<>();
        for (BoardTicket ticket : board.assignedTo(spirit)) {
            ids.add(ticket.id());
        }
        return ids.isEmpty() ? "задание этого раунда" : String.join(", ", ids);
    }
}
