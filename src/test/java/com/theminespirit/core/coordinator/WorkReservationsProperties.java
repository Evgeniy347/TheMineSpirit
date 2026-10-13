package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

class WorkReservationsProperties {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final List<WorkTarget> TARGETS = List.of(
            new WorkTarget(WorkKind.CELL, new BlockCoord(1, 64, 1)),
            new WorkTarget(WorkKind.TREE, new BlockCoord(2, 64, 2)),
            new WorkTarget(WorkKind.VEIN, new BlockCoord(3, 64, 3)));
    private static final List<UUID> SPIRITS = List.of(ALPHA, BETA, GAMMA);
    private static final long NOW = 100;
    private static final long LEASE = 50;

    private sealed interface Op {
    }

    private record Reserve(WorkTarget target, UUID spirit) implements Op {
    }

    private record Release(WorkTarget target, UUID spirit) implements Op {
    }

    private record ReleaseAll(UUID spirit) implements Op {
    }

    @Provide
    Arbitrary<List<Op>> opLists() {
        WorkTarget[] targetArray = TARGETS.toArray(WorkTarget[]::new);
        UUID[] spiritArray = SPIRITS.toArray(UUID[]::new);
        Arbitrary<Op> reserves = Arbitraries.of(targetArray)
                .flatMap(target -> Arbitraries.of(spiritArray).map(spirit -> (Op) new Reserve(target, spirit)));
        Arbitrary<Op> releases = Arbitraries.of(targetArray)
                .flatMap(target -> Arbitraries.of(spiritArray).map(spirit -> (Op) new Release(target, spirit)));
        Arbitrary<Op> releaseAlls = Arbitraries.of(spiritArray).map(spirit -> (Op) new ReleaseAll(spirit));
        return Arbitraries.frequencyOf(Tuple.of(3, reserves), Tuple.of(2, releases), Tuple.of(1, releaseAlls))
                .list()
                .ofMinSize(1)
                .ofMaxSize(12);
    }

    @Property
    void reserveReleaseReleaseAll_AnyOpSequence_AgreesWithModel(@ForAll("opLists") List<Op> ops) {
        WorkReservations reservations = new WorkReservations();
        Map<WorkTarget, UUID> model = new HashMap<>();
        for (Op op : ops) {
            if (op instanceof Reserve reserve) {
                WorkReservations.Outcome expected = model.containsKey(reserve.target())
                        ? (reserve.spirit().equals(model.get(reserve.target()))
                                ? WorkReservations.Outcome.TAKEN_BY_SELF
                                : WorkReservations.Outcome.TAKEN_BY_OTHER)
                        : WorkReservations.Outcome.GRANTED;
                assertEquals(expected, reservations.reserve(reserve.target(), reserve.spirit(), NOW, LEASE),
                        "Исхода брони обязан совпасть с моделью владения: иначе два духа оказались бы на одной цели "
                                + "или бронь потеряла владельца");
                if (expected == WorkReservations.Outcome.GRANTED) {
                    model.put(reserve.target(), reserve.spirit());
                }
            } else if (op instanceof Release release) {
                boolean expected = release.spirit().equals(model.get(release.target()));
                assertEquals(expected, reservations.release(release.target(), release.spirit()),
                        "Снятие брони обязано совпасть с моделью: иначе цель освободилась бы без владельца "
                                + "или осталась занята после снятия");
                if (expected) {
                    model.remove(release.target());
                }
            } else {
                ReleaseAll releaseAll = (ReleaseAll) op;
                long expected = model.values().stream().filter(releaseAll.spirit()::equals).count();
                assertEquals((int) expected, reservations.releaseAll(releaseAll.spirit()),
                        "Число снятых броней обязано совпасть с моделью: иначе у духа осталась бы бронь или снялась "
                                + "чужая");
                model.values().removeIf(releaseAll.spirit()::equals);
            }
        }
        for (WorkTarget target : TARGETS) {
            assertEquals(Optional.ofNullable(model.get(target)), reservations.owner(target),
                    "Владелец цели обязан совпасть с моделью после всей последовательности: иначе два духа владеют "
                            + "одной целью или бронь потерялась");
        }
    }

    @Property
    void releaseAll_AfterAnyReservations_SpiritOwnsNothingAndOthersKeep(@ForAll("opLists") List<Op> ops) {
        WorkReservations reservations = new WorkReservations();
        Map<WorkTarget, UUID> model = new HashMap<>();
        applyAll(reservations, model, ops);

        UUID chosen = SPIRITS.get(0);
        reservations.releaseAll(chosen);
        for (WorkTarget target : TARGETS) {
            assertEquals(Optional.ofNullable(model.get(target)).filter(owner -> !owner.equals(chosen)),
                    reservations.owner(target),
                    "После снятия все брони духа сняты, а чужие владения целы: иначе выгрузка одного духа "
                            + "освобождала бы цели других или оставляла свои");
        }
    }

    @Property
    void expire_AnyDeadline_KeepsThenRemoves(@ForAll("targets") WorkTarget target,
            @ForAll("spirits") UUID spirit, @ForAll("nows") long now, @ForAll("leases") long lease) {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(target, spirit, now, lease);

        assertEquals(0, reservations.expire(now + lease - 1),
                "Бронь не снимается раньше дедлайна: иначе цель перераспределялась бы под руками");
        assertEquals(Optional.of(spirit), reservations.owner(target), "До дедлайна цель остаётся под бронью");
        assertEquals(1, reservations.expire(now + lease), "На дедлайне бронь снимается");
        assertEquals(Optional.empty(), reservations.owner(target), "После дедлайна цель свободна");
    }

    @Provide
    Arbitrary<WorkTarget> targets() {
        return Arbitraries.of(TARGETS.toArray(WorkTarget[]::new));
    }

    @Provide
    Arbitrary<UUID> spirits() {
        return Arbitraries.of(SPIRITS.toArray(UUID[]::new));
    }

    @Provide
    Arbitrary<Long> nows() {
        return Arbitraries.longs().between(0, 10000);
    }

    @Provide
    Arbitrary<Long> leases() {
        return Arbitraries.longs().between(1, 10000);
    }

    private static void applyAll(WorkReservations reservations, Map<WorkTarget, UUID> model, List<Op> ops) {
        for (Op op : ops) {
            if (op instanceof Reserve reserve) {
                WorkReservations.Outcome outcome = reservations.reserve(reserve.target(), reserve.spirit(), NOW, LEASE);
                if (outcome == WorkReservations.Outcome.GRANTED) {
                    model.put(reserve.target(), reserve.spirit());
                }
            } else if (op instanceof Release release) {
                if (reservations.release(release.target(), release.spirit())) {
                    model.remove(release.target());
                }
            } else {
                ReleaseAll releaseAll = (ReleaseAll) op;
                reservations.releaseAll(releaseAll.spirit());
                model.values().removeIf(releaseAll.spirit()::equals);
            }
        }
    }
}
