package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HerdParamsTests {
    @Test
    void herdLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(8, CatalogLimits.HERD_RADIUS_DEFAULT,
                "Радиус скриптов стада по умолчанию обязан быть 8: шире загон не удержать");
        assertEquals(16, CatalogLimits.HERD_RADIUS_MAX,
                "Максимум радиуса скриптов стада обязан быть 16: шире поиск кладёт тик");
        assertEquals(1, CatalogLimits.HERD_PAIRS_MIN,
                "Минимум пар разведения обязан быть 1: иначе breed без пар не вызвать");
        assertEquals(8, CatalogLimits.HERD_PAIRS_MAX,
                "Максимум пар разведения обязан быть 8: приплод за раз ограничен");
        assertEquals(1, CatalogLimits.HERD_PAIRS_DEFAULT,
                "Пар по умолчанию обязана быть 1: стадо растёт постепенно");
        assertEquals(1, CatalogLimits.HERD_COUNT_MIN,
                "Минимум голов обязан быть 1: иначе скрипт без цели не вызвать");
        assertEquals(64, CatalogLimits.HERD_COUNT_MAX,
                "Максимум голов обязан быть 64: работа за запуск ограничена");
        assertEquals(8, CatalogLimits.HERD_FEED_COUNT_DEFAULT,
                "Кормление по умолчанию обязано быть 8 голов: отара кормится постепенно");
        assertEquals(2, CatalogLimits.HERD_KEEP_MIN,
                "Минимум нормы обязан быть 2: пара нужна для разведения");
        assertEquals(64, CatalogLimits.HERD_KEEP_MAX,
                "Максимум нормы обязан быть 64: больше загон не прокормит");
        assertEquals(4, CatalogLimits.HERD_KEEP_DEFAULT_CATTLE,
                "Норма коров по умолчанию обязана быть 4: стадо держится малым");
        assertEquals(8, CatalogLimits.HERD_KEEP_DEFAULT_CHICKEN,
                "Норма кур по умолчанию обязана быть 8: птица плодится быстрее");
        assertEquals(20, CatalogLimits.HERD_REFRESH_TICKS,
                "Снимок стада обязан обновляться раз в 20 тиков: чаще — лишние запросы сущностей");
        assertEquals(48, CatalogLimits.HERD_HUNT_RADIUS_MAX,
                "Максимум радиуса охоты обязан быть 48: чеклист 39 ищет диких овец в радиусе 48 от дома");
        assertEquals(16, CatalogLimits.HERD_HUNT_RADIUS_DEFAULT,
                "Радиус охоты по умолчанию обязан быть 16: дичь ищут шире загона");
    }

    @Test
    void breedParams_RadiusAndPairsOutsideLimits_AreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 0, "pairs", 1)),
                "breed с радиусом 0 обязан отвергаться: искать пару негде");
        assertThrows(IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 17, "pairs", 1)),
                "breed с радиусом 17 обязан отвергаться: шире загона пару не собрать");
        assertThrows(IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 8, "pairs", 0)),
                "breed с нулём пар обязан отвергаться: скрипт без цели бессмысленен");
        assertThrows(IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 8, "pairs", 9)),
                "breed с 9 парами обязан отвергаться: приплод за раз ограничен");
        assertThrows(IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "dragon", "radius", 8, "pairs", 1)),
                "breed с неизвестным видом обязан отвергаться: такого стада нет");
    }

    @Test
    void feedParams_CountOutsideLimits_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 0, "babiesFirst", true)),
                "feed с нулём голов обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 65, "babiesFirst", true)),
                "feed с 65 головами обязан отвергаться: за раз кормят ограниченное стадо");
        assertEquals(new FeedParams(Species.PIG, 8, 8, true),
                FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 8, "babiesFirst", true)),
                "feed обязан сохранять вид, радиус, число и порядок детёнышей: по ним адаптер ищет стадо");
    }

    @Test
    void shearAndMilkParams_CountOutsideLimits_AreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ShearParams.from(Map.of("radius", 8, "count", 0)),
                "shear с нулём овец обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> ShearParams.from(Map.of("radius", 8, "count", 65)),
                "shear с 65 овцами обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> MilkParams.from(Map.of("radius", 0, "count", 4)),
                "milk с радиусом 0 обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> MilkParams.from(Map.of("radius", 8, "count", 65)),
                "milk с 65 коровами обязан отвергаться");
    }

    @Test
    void collectEggsParams_RadiusOutsideLimits_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CollectEggsParams.from(Map.of("radius", 0)),
                "collect_eggs с радиусом 0 обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> CollectEggsParams.from(Map.of("radius", 17)),
                "collect_eggs с радиусом 17 обязан отвергаться: яйца собирают только в своём загоне");
    }

    @Test
    void cullParams_KeepOutsideLimits_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CullParams.from(Map.of("species", "cow", "keep", 1, "radius", 8)),
                "cull с нормой 1 обязан отвергаться: пара нужна для разведения");
        assertThrows(IllegalArgumentException.class,
                () -> CullParams.from(Map.of("species", "cow", "keep", 65, "radius", 8)),
                "cull с нормой 65 обязан отвергаться: больше загона не прокормить");
        assertEquals(CatalogLimits.HERD_KEEP_DEFAULT_CATTLE,
                CullParams.from(Map.of("species", "cow", "radius", 8)).keep(),
                "Норма коров по умолчанию обязана браться из лимитов: иначе умолчание каталога и маппинг разойдутся");
        assertEquals(CatalogLimits.HERD_KEEP_DEFAULT_CHICKEN,
                CullParams.from(Map.of("species", "chicken", "radius", 8)).keep(),
                "Норма кур по умолчанию обязана быть выше коровьей: птица плодится быстрее");
    }

    @Test
    void herdCheckParams_AllSpeciesAccepted_RadiusChecked() {
        assertEquals(Species.all(), HerdCheckParams.from(Map.of("species", "all", "radius", 8)).species(),
                "herd_check со species=all обязан проверять все виды: сводка покрывает всё стадо");
        assertEquals(List.of(Species.SHEEP), HerdCheckParams.from(Map.of("species", "sheep", "radius", 8)).species(),
                "herd_check обязан сохранять выбранный вид: считают только его");
        assertThrows(IllegalArgumentException.class,
                () -> HerdCheckParams.from(Map.of("species", "all", "radius", 17)),
                "herd_check с радиусом 17 обязан отвергаться");
    }

    @Test
    void huntParams_RadiusAndCountOutsideLimits_AreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 0, "count", 4)),
                "hunt с радиусом 0 обязан отвергаться");
        assertThrows(IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 49, "count", 4)),
                "hunt с радиусом 49 обязан отвергаться: дальше дичь не выследить");
        assertThrows(IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 16, "count", 65)),
                "hunt с 65 головами обязан отвергаться");
    }

    @Test
    void coreScripts_HerdSpecs_ExposeAgreedParams() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();

        for (String id : List.of("breed", "feed", "shear", "milk", "collect_eggs", "cull", "herd_check", "hunt")) {
            assertTrue(catalog.find(id).isPresent(),
                    "Каталог обязан содержать скрипт " + id + ": иначе стратег его не вызовет");
        }
        ScriptSpec breed = catalog.find("breed").orElseThrow();
        assertEquals(List.of("species", "radius", "pairs"), breed.params().stream().map(ParamSpec::name).toList(),
                "breed обязан объявлять species, radius и pairs: другие имена стратег не передаст");
        ScriptSpec cull = catalog.find("cull").orElseThrow();
        assertEquals(List.of("species", "keep", "radius"), cull.params().stream().map(ParamSpec::name).toList(),
                "cull обязан объявлять species, keep и radius");
    }
}
