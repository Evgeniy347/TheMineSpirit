package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ContainerPickerProperties {
    private static final String COBBLE = "minecraft:cobblestone";
    private static final String PLANK = "minecraft:oak_planks";
    private static final int MAX_STACK = 64;
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);

    // Инвариант 1: предмет никогда не попадает в контейнер, который не может его принять.
    @Property
    void choose_AnyContainers_NeverPicksFullOne(@ForAll("containers") List<ContainerSnapshot> containers,
            @ForAll("incoming") Stack incoming) {
        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, incoming);
        if (choice instanceof ContainerChoice.Chosen chosen) {
            ContainerSnapshot picked = containers.get(chosen.index());
            assertTrue(picked.hasFreeSlot() || picked.hasPartial(incoming),
                    "выбранный контейнер обязан принять предмет: либо свободный слот, либо неполная стопка того же "
                            + "предмета. В полный контейнер предмет не влезет. Снимки: " + containers
                            + ", предмет: " + incoming);
        }
    }

    // Инвариант 2: при наличии неполного стака того же предмета выбирается контейнер с ним.
    @Property
    void choose_PartialStackExists_PicksContainerWithPartial(
            @ForAll("containersWithPartial") List<ContainerSnapshot> containers,
            @ForAll("incoming") Stack incoming, @ForAll("partialIndex") int partialIndex) {
        ContainerChoice choice = ContainerPicker.choose(containers, ORIGIN, incoming);
        assertTrue(choice instanceof ContainerChoice.Chosen,
                "при наличии неполного стака того же предмета отказ недопустим: " + containers);
        ContainerSnapshot chosen = containers.get(((ContainerChoice.Chosen) choice).index());
        assertTrue(chosen.hasPartial(incoming),
                "если в радиусе есть контейнер с неполной стопкой того же предмета, выбран обязан быть такой "
                        + "контейнер, а не пустой. Снимки: " + containers + ", предмет: " + incoming
                        + ", выбран индекс " + ((ContainerChoice.Chosen) choice).index());
        assertFalse(containers.get(partialIndex).hasPartial(incoming) && !chosen.hasPartial(incoming),
                "неполный стак был в контейнере " + partialIndex + ", но выбран контейнер без него: "
                        + "докладывание обязано идти к существующей стопке");
    }

    @Provide
    Arbitrary<List<ContainerSnapshot>> containers() {
        Arbitrary<ContainerSnapshot> one = container(Arbitraries.of(COBBLE, PLANK, "minecraft:dirt"));
        return one.list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<ContainerSnapshot>> containersWithPartial() {
        Arbitrary<Stack> partial = partialOf(COBBLE);
        // Контейнер с неполной стопкой бывает и без свободного слота: докладывать всё равно надо к стопке.
        Arbitrary<ContainerSnapshot> withPartial = partial.map(stack -> {
            List<Stack> slots = new ArrayList<>();
            slots.add(stack);
            slots.add(Arbitraries.of(true, false).sample()
                    ? new Stack(PLANK, 0, MAX_STACK, MAX_STACK)
                    : null);
            return new ContainerSnapshot(new BlockCoord(0, 64, 0), slots);
        });
        Arbitrary<ContainerSnapshot> filler = container(Arbitraries.of(COBBLE, PLANK));
        return withPartial.list().ofMinSize(1).ofMaxSize(3)
                .flatMap(partials -> filler.list().ofMaxSize(3)
                        .map(rest -> {
                            List<ContainerSnapshot> all = new ArrayList<>(partials);
                            all.addAll(rest);
                            return all;
                        }));
    }

    @Provide
    Arbitrary<Integer> partialIndex() {
        return Arbitraries.integers().between(0, 0);
    }

    @Provide
    Arbitrary<Stack> incoming() {
        return Arbitraries.integers().between(1, MAX_STACK)
                .map(count -> new Stack(COBBLE, 0, count, MAX_STACK));
    }

    private static Arbitrary<Stack> partialOf(String item) {
        return Arbitraries.integers().between(1, MAX_STACK - 1)
                .map(count -> new Stack(item, 0, count, MAX_STACK));
    }

    private static Arbitrary<ContainerSnapshot> container(Arbitrary<String> items) {
        Arbitrary<Stack> stack = items.flatMap(item -> Arbitraries.integers().between(1, MAX_STACK)
                .map(count -> new Stack(item, 0, count, MAX_STACK)));
        Arbitrary<List<Stack>> slots = stack.injectNull(0.4).list().ofMinSize(1).ofMaxSize(4);
        return slots.map(list -> new ContainerSnapshot(new BlockCoord(0, 64, list.size()), list));
    }
}