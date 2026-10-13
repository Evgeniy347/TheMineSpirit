package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FortifyPlanProperties {
    @Provide
    Arbitrary<BlockCoord> feet() {
        return Combinators.combine(Arbitraries.integers().between(-20, 20),
                Arbitraries.integers().between(0, 200), Arbitraries.integers().between(-20, 20))
                .as(BlockCoord::new);
    }

    @Provide
    Arbitrary<Optional<FortifyRoster>> rosters() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        return Combinators.combine(ids, ids.list().ofMaxSize(4),
                Arbitraries.of(FortifyMode.SHELTER, FortifyMode.BARRICADE, FortifyMode.NONE))
                .as((builder, guards, mode) -> {
                    List<UUID> cover = guards.stream().filter(id -> !id.equals(builder)).distinct().sorted()
                            .toList();
                    return Optional.of(new FortifyRoster(builder, cover, Optional.of(new Point(0, 64, 0)), mode));
                });
    }

    @Property
    void plan_AnyRoster_OrderMatchesRoster(@ForAll("rosters") Optional<FortifyRoster> roster,
            @ForAll("feet") BlockCoord feet) {
        FortifyPlan plan = FortifyPlan.plan(roster, feet, Optional.empty(), Optional.empty());

        if (roster.get().mode() == FortifyMode.NONE) {
            assertEquals(Optional.empty(), plan.order(),
                    "Режим NONE даёт пустой план: иначе духи строятся без решения");
            return;
        }
        FortifyOrder order = plan.order().orElseThrow();
        assertEquals(roster.get().builder(), order.builder(),
                "У приказа ровно один строитель из ростера: иначе строит не тот, кого назначили");
        assertEquals(roster.get().mode(), order.mode(),
                "Режим приказа обязан совпадать с ростером: иначе строитель ставит не то, что приказано");
        assertEquals(roster.get().guards(), order.guards(),
                "Защитники приказа обязаны совпадать с ростером: иначе кроет не тот, кого назначили");
        assertTrue(!order.guards().contains(order.builder()),
                "Строитель обязан не входить в защитники: один дух не строит и не прикрывает сразу");
        assertEquals(order.cells().size(), new HashSet<>(order.cells()).size(),
                "Все клетки приказа уникальны: дубль — двойной расход блоков");
        assertTrue(order.cells().size() <= FortifyTuning.FORTIFY_BLOCK_BUDGET,
                "Приказ обязан влезать в бюджет: иначе строитель берётся за то, на что нет блоков");
    }

    @Property
    void plan_AnyRoster_BatchesCoverOrder(@ForAll("rosters") Optional<FortifyRoster> roster,
            @ForAll("feet") BlockCoord feet) {
        FortifyPlan plan = FortifyPlan.plan(roster, feet, Optional.empty(), Optional.empty());

        List<BlockCoord> joined = plan.batches().stream().flatMap(List::stream).toList();
        if (plan.order().isEmpty()) {
            assertEquals(List.of(), joined,
                    "Пустой план держит пустые порции: иначе исполнитель ставит блоки из пустоты");
            return;
        }
        assertEquals(plan.order().get().cells(), joined,
                "Склейка порций обязана давать клетки приказа: потеря клетки — дыра в укреплении");
        for (List<BlockCoord> batch : plan.batches()) {
            assertTrue(batch.size() <= FortifyTuning.FORTIFY_BLOCKS_PER_TICK,
                    "Порция обязана держать не больше предела за тик: иначе стройка грузит серверный тик");
        }
    }

    @Property
    void plan_SameInput_TwiceSameResult(@ForAll("rosters") Optional<FortifyRoster> roster,
            @ForAll("feet") BlockCoord feet) {
        assertEquals(FortifyPlan.plan(roster, feet, Optional.empty(), Optional.empty()),
                FortifyPlan.plan(roster, feet, Optional.empty(), Optional.empty()),
                "Тот же вход даёт тот же план: иначе бронь клеток не сходится с постановкой");
    }

    @Property
    void plan_BarricadeBanks_SeamWithoutSpiritCell(@ForAll("feet") BlockCoord feet) {
        UUID builder = UUID.randomUUID();
        FortifyRoster crew = new FortifyRoster(builder, List.of(), Optional.of(new Point(0, 64, 0)),
                FortifyMode.BARRICADE);
        BlockCoord left = new BlockCoord(feet.x() + 5, feet.y(), feet.z());
        BlockCoord right = new BlockCoord(feet.x() + 7, feet.y(), feet.z());

        FortifyPlan plan = FortifyPlan.plan(Optional.of(crew), feet, Optional.of(left), Optional.of(right));

        FortifyOrder order = plan.order().orElseThrow();
        assertTrue(!order.cells().contains(feet),
                "Клетка строителя обязана выпадать из шва: иначе строитель замуровывает себя");
        assertEquals(FortifyMode.BARRICADE, order.mode(),
                "Шов при годных берегах обязан оставаться швом: иначе режим не различается");
    }
}
