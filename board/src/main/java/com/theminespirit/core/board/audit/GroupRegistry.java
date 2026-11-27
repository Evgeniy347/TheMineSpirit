package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotCells;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class GroupRegistry {
    private ExpectIndex.Index index = new ExpectIndex.Index(Map.of(), 0, 0);
    private long revision = -1;
    private long fillVersion = -1;
    private @Nullable AuditRegistry facts;
    private Map<String, List<Expectation>> expectations = Map.of();
    private final Map<String, List<Expectation>> groupCells = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<BlockCoord, String> owners = new HashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final Map<String, String> tickets = new HashMap<>();

    public void rebuild(ExpectIndex.Index index, long revision) {
        if (facts != null) {
            facts.releaseAllIssued();
        }
        this.index = Objects.requireNonNull(index, "Индекс ожиданий не может быть null: группы собирать нечего");
        this.revision = revision;
        Map<String, List<Expectation>> next = new LinkedHashMap<>();
        Map<BlockCoord, String> nextOwners = new HashMap<>();
        for (Expectation expectation : index.cells().values()) {
            String key = GroupKeys.key(expectation);
            next.computeIfAbsent(key, ignored -> new ArrayList<>()).add(expectation);
            nextOwners.put(expectation.cell(), key);
        }
        expectations = Map.copyOf(next);
        groupCells.clear();
        next.forEach((key, cells) -> groupCells.put(key, List.copyOf(cells)));
        owners.clear();
        owners.putAll(nextOwners);
        groups.clear();
        dirty.clear();
        dirty.addAll(expectations.keySet());
        tickets.clear();
        fillVersion = -1;
    }

    public int sync(AuditRegistry facts, long revision, int budget) {
        Objects.requireNonNull(facts, "Реестр фактов не может быть null: группы не с чем сравнить");
        if (revision != this.revision) {
            rebuild(index, revision);
        }
        this.facts = facts;
        long currentFill = facts.fillVersion();
        if (currentFill != fillVersion && facts.waiting() == 0) {
            dirty.addAll(groups.keySet());
        }
        fillVersion = currentFill;
        int rebuilt = 0;
        Iterator<String> iterator = dirty.iterator();
        while (iterator.hasNext() && rebuilt < budget) {
            String key = iterator.next();
            iterator.remove();
            if (key.startsWith("group:ground@")) {
                rebuilt += ground(key, facts, revision);
            } else {
                groups.put(key, build(key, facts, revision, firstSubject(key)));
                rebuilt++;
            }
        }
        return rebuilt;
    }

    public List<Group> groups() {
        return groups.values().stream().sorted(Comparator.comparing(Group::key)).toList();
    }

    public @Nullable Group group(String key) {
        return groups.get(key);
    }

    public @Nullable Group snapshot(GroupRef ref) {
        Objects.requireNonNull(ref, "Ссылка на группу не может быть null: снимок ищется по ней");
        Group group = groups.get(ref.id());
        return group != null && group.revision() == ref.revision() ? group : null;
    }

    public @Nullable String owner(BlockCoord cell) {
        return owners.get(cell);
    }

    public void issue(String ticket, String group) {
        Group current = groups.get(group);
        if (current == null || current.status() != Group.Status.PENDING) {
            throw new IllegalStateException("Группа " + group + " не готова к выдаче: статус "
                    + (current == null ? "отсутствует" : current.status()));
        }
        groups.put(group, current.issued());
        tickets.put(ticket, group);
        facts().issued(ticket, group);
    }

    public void release(String ticket) {
        String group = tickets.remove(ticket);
        facts().released(ticket);
        if (group != null && groups.containsKey(group)) {
            groups.put(group, build(group, facts(), revision, firstSubject(group)));
        }
    }

    public void block(String group, String reason) {
        Group current = groups.get(group);
        if (current == null) {
            return;
        }
        tickets.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(group)) {
                return false;
            }
            facts().released(entry.getKey());
            return true;
        });
        groups.put(group, current.withStatus(Group.Status.BLOCKED, reason));
    }

    public void quarantine(String group) {
        Group current = groups.get(group);
        if (current == null) {
            return;
        }
        groups.put(group, current.quarantined());
        tickets.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(group)) {
                return false;
            }
            facts().released(entry.getKey());
            return true;
        });
    }

    public int dirty() {
        return dirty.size();
    }

    public int unknown() {
        int total = 0;
        for (Group group : groups.values()) {
            total += group.unknown();
        }
        return total;
    }

    private String firstSubject(String key) {
        if (key.startsWith("group:ground:")) {
            int start = "group:ground:".length();
            int at = key.indexOf('@');
            return key.substring(start, at < 0 ? key.length() : at);
        }
        List<Expectation> cells = expectations.get(key);
        return cells == null || cells.isEmpty() ? "" : cells.get(0).source().subject();
    }

    private int ground(String key, AuditRegistry facts, long planRevision) {
        List<Expectation> cells = expectations.get(key);
        if (cells == null || cells.isEmpty()) {
            return 0;
        }
        Expectation first = cells.get(0);
        ChunkCell chunk = GroupKeys.chunk(first.source(), first.cell());
        int levelY = cells.stream()
                .filter(expectation -> expectation.wanted().kind() == Wanted.Kind.GROUND)
                .mapToInt(expectation -> expectation.wanted().level() + 1)
                .findFirst()
                .orElse(first.cell().y());
        Map<String, List<Expectation>> parts = new LinkedHashMap<>();
        for (Expectation expectation : cells) {
            String subject = expectation.wanted().kind() == Wanted.Kind.GROUND
                    ? "pit"
                    : expectation.cell().y() < levelY + PlotCells.FREE_HEIGHT ? "stray" : "floating";
            parts.computeIfAbsent(subject, ignored -> new ArrayList<>()).add(expectation);
        }
        groups.remove(key);
        int rebuilt = 0;
        for (Map.Entry<String, List<Expectation>> part : parts.entrySet()) {
            String partKey = Group.pending(Group.Kind.GROUND, part.getKey(), chunk, List.of(), 0)
                    .key();
            groupCells.put(partKey, List.copyOf(part.getValue()));
            groups.put(partKey, build(partKey, facts, planRevision, part.getKey()));
            for (Expectation expectation : part.getValue()) {
                owners.put(expectation.cell(), partKey);
            }
            rebuilt++;
        }
        return rebuilt;
    }

    private Group build(String key, AuditRegistry facts, long planRevision, String subject) {
        List<Expectation> cells = groupCells.get(key);
        if (cells == null || cells.isEmpty()) {
            throw new IllegalStateException("Группа " + key + " без ожиданий: пересобирать нечего");
        }
        Expectation first = cells.get(0);
        List<DiffItem> items = new ArrayList<>();
        int unknown = 0;
        for (Expectation expectation : cells) {
            String found = facts.blockAt(expectation.cell());
            if (found == null) {
                unknown++;
                continue;
            }
            BlockEquivalence.Verdict verdict = BlockEquivalence.check(expectation.wanted(), found);
            if (verdict == BlockEquivalence.Verdict.DONE) {
                continue;
            }
            if (verdict == BlockEquivalence.Verdict.STATE) {
                items.add(DiffItem.fixState(
                        expectation.cell(), found, expectation.wanted(), expectation.source(), expectation.access()));
            } else if (expectation.wanted().kind() == Wanted.Kind.AIR) {
                items.add(DiffItem.breakAt(expectation.cell(), found, expectation.source(), expectation.access()));
            } else {
                items.add(DiffItem.place(
                        expectation.cell(), found, expectation.wanted(), expectation.source(), expectation.access()));
            }
        }
        items.sort(DiffItem.WORK_ORDER);
        Group previous = groups.get(key);
        int groupRevision = previous == null ? (int) Math.min(Integer.MAX_VALUE, planRevision) : previous.revision();
        Group.Status status =
                unknown > 0 ? Group.Status.BLOCKED : items.isEmpty() ? Group.Status.DONE : Group.Status.PENDING;
        String reason = unknown > 0 ? "чанк не заполнен" : "";
        return new Group(
                GroupKeys.kind(first.source()),
                subject,
                GroupKeys.chunk(first.source(), first.cell()),
                items,
                cells.size(),
                status,
                groupRevision,
                unknown,
                reason);
    }

    private AuditRegistry facts() {
        return Objects.requireNonNull(facts, "Группа ещё не синхронизирована с фактами: выдавать нечего");
    }
}
