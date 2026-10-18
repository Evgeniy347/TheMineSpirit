package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CullPolicyTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);

    static Stream<Arguments> cullRows() {
        BlockCoord near = new BlockCoord(1, 64, 0);
        BlockCoord mid = new BlockCoord(3, 64, 0);
        BlockCoord far = new BlockCoord(6, 64, 0);
        BlockCoord edge = new BlockCoord(9, 64, 0);
        return Stream.of(
                Arguments.of(List.of(cow("a", near), cow("b", mid)), 4, SwordTier.IRON, List.of(),
                        "стадо меньше нормы: никого не трогают"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge)), 4,
                        SwordTier.IRON, List.of(), "ровно норма: никого не трогают"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.IRON, List.of("d"), "одна лишняя: забирают дальнюю от центра"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", far)), 3,
                        SwordTier.IRON, List.of("d", "c"), "две лишние: забирают двух дальних по порядку"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), calf("d", edge), calf("e", edge)),
                        3, SwordTier.IRON, List.of(),
                        "лишние только детёныши: молодняк не трогают никогда"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), calf("e", edge),
                        calf("f", edge)), 3, SwordTier.IRON, List.of("d"),
                        "телята при забое целы: забирают только взрослого дальнего"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.WOOD, List.of(), "деревянный меч: отказ до удара"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.STONE, List.of(), "каменный меч: отказ до удара"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.GOLD, List.of(), "золотой меч: отказ до удара"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.IRON, List.of("d"), "железный меч: забой разрешён"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.DIAMOND, List.of("d"), "алмазный меч: забой разрешён"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.NETHERITE, List.of("d"), "незеритовый меч: забой разрешён"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", near)), 4,
                        SwordTier.HAND, List.of(), "руки: всегда отказ, животное не бьют зря"),
                Arguments.of(List.of(), 4, SwordTier.IRON, List.of(), "пустое стадо: забирать некого"),
                Arguments.of(List.of(pig("p", near), pig("q", mid), pig("r", far)), 4, SwordTier.IRON, List.of(),
                        "чужого вида нет: считают только свой вид"),
                Arguments.of(List.of(cow("a", near), cow("b", mid), cow("c", far), cow("d", edge), cow("e", edge),
                        cow("f", edge)), 2, SwordTier.IRON, List.of("d", "e", "f", "c"),
                        "шесть голов при норме два: остаются двое ближних"));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("cullRows")
    void pick_HerdTable_PicksOnlyExtraAdultsOrRefuses(List<AnimalRef> herd, int keep, SwordTier sword,
            List<String> expected, String reason) {
        CullPolicy.Outcome result = CullPolicy.pick(herd, CENTER, Species.COW, keep, sword);

        if (!sword.atLeast(SwordTier.IRON)) {
            assertInstanceOf(CullPolicy.Refused.class, result,
                    "Слабый меч обязан давать отказ до удара (" + reason + "): животное не бьют зря");
            assertTrue(((CullPolicy.Refused) result).reason().contains("железным мечом и выше"),
                    "Отказ обязан называть порог (" + reason + "): иначе стратег не узнает, какой меч нести");
            return;
        }
        assertInstanceOf(CullPolicy.Picked.class, result,
                "Достаточный меч обязан давать список (" + reason + "): иначе стадо перерастёт загон");
        assertEquals(expected, ((CullPolicy.Picked) result).ids(),
                "Забирать обязаны дальних от центра сверх нормы (" + reason + "): ближние держат стадо у кормушки");
    }

    @Test
    void pick_KeepBelowTwo_IsRejected() {
        List<AnimalRef> herd = List.of(cow("a", new BlockCoord(1, 64, 0)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CullPolicy.pick(herd, CENTER, Species.COW, 1, SwordTier.IRON),
                "Норма меньше двух обязана отвергаться: пара нужна для разведения, стадо выродится до нуля");

        assertTrue(error.getMessage().contains("2"),
                "Сообщение обязано называть минимум: иначе норму поправят вслепую. Фактически: " + error.getMessage());
    }

    @Test
    void pick_Sheep_RefusedEvenWithIronSword() {
        List<AnimalRef> herd = List.of(
                new AnimalRef("a", Species.SHEEP, false, false, true, new BlockCoord(1, 64, 0), true),
                new AnimalRef("b", Species.SHEEP, false, false, true, new BlockCoord(3, 64, 0), true),
                new AnimalRef("c", Species.SHEEP, false, false, true, new BlockCoord(5, 64, 0), true));

        CullPolicy.Outcome result = CullPolicy.pick(herd, CENTER, Species.SHEEP, 2, SwordTier.IRON);

        assertInstanceOf(CullPolicy.Refused.class, result,
                "Овец загона не забивают даже железным мечом: шерсть стригут повторно, а не режут стадо");
        assertTrue(((CullPolicy.Refused) result).reason().contains("овец загона не убиваем"),
                "Отказ обязан называть правило: иначе стратег решит, что виноват меч. Фактически: "
                        + ((CullPolicy.Refused) result).reason());
    }

    @Test
    void swordTiers_ProductionTable_MatchesVanillaOrder() {
        assertEquals(SwordTier.WOOD, SwordTier.ofItem("minecraft:wooden_sword"),
                "Деревянный меч обязан читаться из таблицы: иначе забой разрешит не тот тир");
        assertEquals(SwordTier.IRON, SwordTier.ofItem("minecraft:iron_sword"),
                "Железный меч обязан читаться из таблицы: порог забоя считается по таблице, а не по урону");
        assertEquals(SwordTier.NETHERITE, SwordTier.ofItem("minecraft:netherite_sword"),
                "Незеритовый меч обязан читаться из таблицы: иначе лучшему мечу откажут");
        assertEquals(SwordTier.HAND, SwordTier.ofItem("minecraft:air"),
                "Пустая рука обязана быть ниже дерева: руками стадо не забивают");
        assertTrue(SwordTier.NETHERITE.atLeast(SwordTier.IRON) && !SwordTier.GOLD.atLeast(SwordTier.IRON),
                "Порядок тиров обязан идти от руки к незериту: иначе золотой меч сойдёт за железный");
        assertThrows(IllegalArgumentException.class, () -> SwordTier.ofItem("minecraft:diamond_axe"),
                "Алмазный топор — не меч: тир считается по таблице, а не по урону, топору обязаны отказать");
    }

    @Test
    void swordTable_FileItems_MatchProduction() {
        Map<String, SwordTier> items = SwordTier.items();

        assertTrue(items.get("minecraft:iron_sword") == SwordTier.IRON
                && items.get("minecraft:diamond_sword") == SwordTier.DIAMOND
                && items.get("minecraft:netherite_sword") == SwordTier.NETHERITE,
                "Файл sword.json обязан покрывать боевые мечи: без файла забой запрещён вовсе");
        assertTrue(items.get("minecraft:air") == SwordTier.HAND,
                "Пустая рука обязана быть в таблице ниже дерева");
    }

    @Property
    void pick_AnyHerd_PickedAreAdultsAndKeepRemains(@ForAll @IntRange(min = 0, max = 8) int adults,
            @ForAll @IntRange(min = 0, max = 8) int babies, @ForAll @IntRange(min = 2, max = 5) int keep) {
        List<AnimalRef> herd = new ArrayList<>();
        for (int i = 0; i < adults; i++) {
            herd.add(cow("cow-" + i, new BlockCoord(i, 64, 0)));
        }
        Set<String> babyIds = new HashSet<>();
        for (int i = 0; i < babies; i++) {
            herd.add(calf("calf-" + i, new BlockCoord(i, 64, 1)));
            babyIds.add("calf-" + i);
        }

        CullPolicy.Outcome result = CullPolicy.pick(herd, CENTER, Species.COW, keep, SwordTier.IRON);

        assertInstanceOf(CullPolicy.Picked.class, result, "Железный меч обязан разрешать забой");
        List<String> ids = ((CullPolicy.Picked) result).ids();
        assertTrue(ids.stream().noneMatch(babyIds::contains),
                "Выбранные обязаны быть только взрослыми: детёнышей не трогают никогда. Выбрано: " + ids);
        assertEquals(Math.max(0, adults - keep), ids.size(),
                "После забоя выбранных взрослых обязано остаться ровно keep (" + keep + "): иначе норма не держится");
    }

    @Property
    void pick_SameHerd_ResultIsDeterministic(@ForAll @IntRange(min = 3, max = 8) int adults) {
        List<AnimalRef> herd = new ArrayList<>();
        for (int i = 0; i < adults; i++) {
            herd.add(cow("cow-" + i, new BlockCoord(i * 3, 64, i)));
        }

        CullPolicy.Outcome first = CullPolicy.pick(herd, CENTER, Species.COW, 2, SwordTier.DIAMOND);
        CullPolicy.Outcome second = CullPolicy.pick(herd.reversed(), CENTER, Species.COW, 2, SwordTier.DIAMOND);

        assertEquals(first, second,
                "Забой обязан зависеть только от стада, а не от порядка снимка: иначе дух будет менять жертв");
    }

    private static AnimalRef cow(String id, BlockCoord pos) {
        return new AnimalRef(id, Species.COW, false, false, false, pos, true);
    }

    private static AnimalRef calf(String id, BlockCoord pos) {
        return new AnimalRef(id, Species.COW, true, false, false, pos, true);
    }

    private static AnimalRef pig(String id, BlockCoord pos) {
        return new AnimalRef(id, Species.PIG, false, false, false, pos, true);
    }
}
