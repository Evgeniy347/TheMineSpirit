package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Coordinator {
    private final SectorAssigner sectorAssigner = new SectorAssigner();
    private final AssistPolicy assistPolicy = new AssistPolicy();
    private final WorkAllocator workAllocator = new WorkAllocator();

    public CoordinatorPlan plan(CoordinatorState state, WorkReservations reservations, long now) {
        if (state == null) {
            throw new IllegalArgumentException("state не может быть null: план строится из состояния поселения");
        }
        if (reservations == null) {
            throw new IllegalArgumentException("reservations не может быть null: брони без реестра не проверить");
        }
        if (now < 0) {
            throw new IllegalArgumentException("now = " + now + ": время пересборки не может быть отрицательным");
        }
        Optional<Point> point = state.defendedPoint();
        double radius = state.defendedRadius();
        List<SpiritSnapshot> defenders = state.defenders();
        List<DefenseAssignment> defense = point.isPresent() && !defenders.isEmpty()
                ? sectorAssigner.assign(point.get(), radius,
                        defenders.stream().map(SpiritSnapshot::id).toList())
                : List.of();
        List<AssistOrder> assists = point.isPresent() ? assist(defense, state) : List.of();
        List<WorkTarget> candidates = state.spirits().stream()
                .flatMap(spirit -> spirit.workTarget().stream())
                .distinct()
                .toList();
        List<SpiritSnapshot> free = state.spirits().stream()
                .filter(spirit -> spirit.role() != SpiritRole.DEFENDER)
                .toList();
        List<WorkAssignment> work = workAllocator.allocate(free, candidates, reservations, now);
        return new CoordinatorPlan(point, radius, defense, assists, work);
    }

    private List<AssistOrder> assist(List<DefenseAssignment> defense, CoordinatorState state) {
        List<AssistOrder> orders = new ArrayList<>();
        Set<UUID> wards = new HashSet<>();
        for (SpiritSnapshot spirit : state.spirits()) {
            if (spirit.attacked()) {
                wards.add(spirit.id());
            }
        }
        for (UUID ward : wards.stream().sorted().toList()) {
            for (UUID helper : assistPolicy.assist(ward, state.spirits(), defense, CoordinatorTuning.MAX_ASSIST)) {
                orders.add(new AssistOrder(helper, ward));
            }
        }
        return List.copyOf(orders);
    }
}
