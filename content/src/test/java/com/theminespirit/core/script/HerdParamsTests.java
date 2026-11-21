package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HerdParamsTests {
    @Test
    void herdLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(
                8,
                CatalogLimits.HERD_RADIUS_DEFAULT,
                "Радиус скриптов стада по умолчанию обязан быть 8: шире загон не удержать Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.HERD_RADIUS_MAX,
                "Максимум радиуса скриптов стада обязан быть 16: шире поиск кладёт тик Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.HERD_PAIRS_MIN,
                "Минимум пар разведения обязан быть 1: иначе breed без пар не вызвать При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                8,
                CatalogLimits.HERD_PAIRS_MAX,
                "Максимум пар разведения обязан быть 8: приплод за раз ограничен Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.HERD_PAIRS_DEFAULT,
                "Пар по умолчанию обязана быть 1: стадо растёт постепенно Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.HERD_COUNT_MIN,
                "Минимум голов обязан быть 1: иначе скрипт без цели не вызвать При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                64,
                CatalogLimits.HERD_COUNT_MAX,
                "Максимум голов обязан быть 64: работа за запуск ограничена Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                8,
                CatalogLimits.HERD_FEED_COUNT_DEFAULT,
                "Кормление по умолчанию обязано быть 8 голов: отара кормится постепенно Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                2,
                CatalogLimits.HERD_KEEP_MIN,
                "Минимум нормы обязан быть 2: пара нужна для разведения Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                64,
                CatalogLimits.HERD_KEEP_MAX,
                "Максимум нормы обязан быть 64: больше загон не прокормит Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                4,
                CatalogLimits.HERD_KEEP_DEFAULT_CATTLE,
                "Норма коров по умолчанию обязана быть 4: стадо держится малым Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                8,
                CatalogLimits.HERD_KEEP_DEFAULT_CHICKEN,
                "Норма кур по умолчанию обязана быть 8: птица плодится быстрее Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                20,
                CatalogLimits.HERD_REFRESH_TICKS,
                "Снимок стада обязан обновляться раз в 20 тиков: чаще — лишние запросы сущностей Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                48,
                CatalogLimits.HERD_HUNT_RADIUS_MAX,
                "Максимум радиуса охоты обязан быть 48: чеклист 39 ищет диких овец в радиусе 48 от дома Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.HERD_HUNT_RADIUS_DEFAULT,
                "Радиус охоты по умолчанию обязан быть 16: дичь ищут шире загона Иначе тест herdLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене herdLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
    }

    @Test
    void breedParams_RadiusAndPairsOutsideLimits_AreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 0, "pairs", 1)),
                "breed с радиусом 0 обязан отвергаться: искать пару негде Иначе тест breedParams_RadiusAndPairsOutsideLimits_AreRejected перестанет ловить эту поломку. При смене breedParams_RadiusAndPairsOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 17, "pairs", 1)),
                "breed с радиусом 17 обязан отвергаться: шире загона пару не собрать Иначе тест breedParams_RadiusAndPairsOutsideLimits_AreRejected перестанет ловить эту поломку. При смене breedParams_RadiusAndPairsOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 8, "pairs", 0)),
                "breed с нулём пар обязан отвергаться: скрипт без цели бессмысленен Иначе тест breedParams_RadiusAndPairsOutsideLimits_AreRejected перестанет ловить эту поломку. При смене breedParams_RadiusAndPairsOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "cow", "radius", 8, "pairs", 9)),
                "breed с 9 парами обязан отвергаться: приплод за раз ограничен Иначе тест breedParams_RadiusAndPairsOutsideLimits_AreRejected перестанет ловить эту поломку. При смене breedParams_RadiusAndPairsOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedParams.from(Map.of("species", "dragon", "radius", 8, "pairs", 1)),
                "breed с неизвестным видом обязан отвергаться: такого стада нет Иначе тест breedParams_RadiusAndPairsOutsideLimits_AreRejected перестанет ловить эту поломку. При смене breedParams_RadiusAndPairsOutsideLimits_AreRejected проверь это утверждение.");
    }

    @Test
    void feedParams_CountOutsideLimits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 0, "babiesFirst", true)),
                "feed с нулём голов обязан отвергаться Иначе тест feedParams_CountOutsideLimits_IsRejected перестанет ловить эту поломку. При смене feedParams_CountOutsideLimits_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 65, "babiesFirst", true)),
                "feed с 65 головами обязан отвергаться: за раз кормят ограниченное стадо Иначе тест feedParams_CountOutsideLimits_IsRejected перестанет ловить эту поломку. При смене feedParams_CountOutsideLimits_IsRejected проверь это утверждение.");
        assertEquals(
                new FeedParams(Species.PIG, 8, 8, true),
                FeedParams.from(Map.of("species", "pig", "radius", 8, "count", 8, "babiesFirst", true)),
                "feed обязан сохранять вид, радиус, число и порядок детёнышей: по ним адаптер ищет стадо Иначе тест feedParams_CountOutsideLimits_IsRejected перестанет ловить эту поломку. При смене feedParams_CountOutsideLimits_IsRejected проверь это утверждение.");
    }

    @Test
    void shearAndMilkParams_CountOutsideLimits_AreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShearParams.from(Map.of("radius", 8, "count", 0)),
                "shear с нулём овец обязан отвергаться Иначе тест shearAndMilkParams_CountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене shearAndMilkParams_CountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> ShearParams.from(Map.of("radius", 8, "count", 65)),
                "shear с 65 овцами обязан отвергаться Иначе тест shearAndMilkParams_CountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене shearAndMilkParams_CountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> MilkParams.from(Map.of("radius", 0, "count", 4)),
                "milk с радиусом 0 обязан отвергаться Иначе тест shearAndMilkParams_CountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене shearAndMilkParams_CountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> MilkParams.from(Map.of("radius", 8, "count", 65)),
                "milk с 65 коровами обязан отвергаться Иначе тест shearAndMilkParams_CountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене shearAndMilkParams_CountOutsideLimits_AreRejected проверь это утверждение.");
    }

    @Test
    void collectEggsParams_RadiusOutsideLimits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CollectEggsParams.from(Map.of("radius", 0)),
                "collect_eggs с радиусом 0 обязан отвергаться Иначе тест collectEggsParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене collectEggsParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> CollectEggsParams.from(Map.of("radius", 17)),
                "collect_eggs с радиусом 17 обязан отвергаться: яйца собирают только в своём загоне Иначе тест collectEggsParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене collectEggsParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
    }

    @Test
    void harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected() {
        assertEquals(
                new HarvestHiveParams(8, 2),
                HarvestHiveParams.from(Map.of("radius", 8, "count", 2)),
                "harvest_hive обязан сохранять радиус и число ульев: по ним адаптер ищет цель Иначе тест harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HarvestHiveParams.from(Map.of("radius", 0, "count", 1)),
                "harvest_hive с радиусом 0 обязан отвергаться: искать улей негде Иначе тест harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HarvestHiveParams.from(Map.of("radius", 17, "count", 1)),
                "harvest_hive с радиусом 17 обязан отвергаться: дальше загона ульев не ищут Иначе тест harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HarvestHiveParams.from(Map.of("radius", 8, "count", 0)),
                "harvest_hive с нулём ульев обязан отвергаться: скрипт без цели бессмысленен Иначе тест harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HarvestHiveParams.from(Map.of("radius", 8, "count", 65)),
                "harvest_hive с 65 ульями обязан отвергаться: за запуск обходят ограниченное число Иначе тест harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене harvestHiveParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
    }

    @Test
    void cullParams_KeepOutsideLimits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CullParams.from(Map.of("species", "cow", "keep", 1, "radius", 8)),
                "cull с нормой 1 обязан отвергаться: пара нужна для разведения Иначе тест cullParams_KeepOutsideLimits_IsRejected перестанет ловить эту поломку. При смене cullParams_KeepOutsideLimits_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> CullParams.from(Map.of("species", "cow", "keep", 65, "radius", 8)),
                "cull с нормой 65 обязан отвергаться: больше загона не прокормить Иначе тест cullParams_KeepOutsideLimits_IsRejected перестанет ловить эту поломку. При смене cullParams_KeepOutsideLimits_IsRejected проверь это утверждение.");
        assertEquals(
                CatalogLimits.HERD_KEEP_DEFAULT_CATTLE,
                CullParams.from(Map.of("species", "cow", "radius", 8)).keep(),
                "Норма коров по умолчанию обязана браться из лимитов: иначе умолчание каталога и маппинг разойдутся При смене cullParams_KeepOutsideLimits_IsRejected проверь это утверждение.");
        assertEquals(
                CatalogLimits.HERD_KEEP_DEFAULT_CHICKEN,
                CullParams.from(Map.of("species", "chicken", "radius", 8)).keep(),
                "Норма кур по умолчанию обязана быть выше коровьей: птица плодится быстрее Иначе тест cullParams_KeepOutsideLimits_IsRejected перестанет ловить эту поломку. При смене cullParams_KeepOutsideLimits_IsRejected проверь это утверждение.");
    }

    @Test
    void herdCheckParams_AllSpeciesAccepted_RadiusChecked() {
        assertEquals(
                Species.all(),
                HerdCheckParams.from(Map.of("species", "all", "radius", 8)).species(),
                "herd_check со species=all обязан проверять все виды: сводка покрывает всё стадо Иначе тест herdCheckParams_AllSpeciesAccepted_RadiusChecked перестанет ловить эту поломку. При смене herdCheckParams_AllSpeciesAccepted_RadiusChecked проверь это утверждение.");
        assertEquals(
                List.of(Species.SHEEP),
                HerdCheckParams.from(Map.of("species", "sheep", "radius", 8)).species(),
                "herd_check обязан сохранять выбранный вид: считают только его Иначе тест herdCheckParams_AllSpeciesAccepted_RadiusChecked перестанет ловить эту поломку. При смене herdCheckParams_AllSpeciesAccepted_RadiusChecked проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCheckParams.from(Map.of("species", "all", "radius", 17)),
                "herd_check с радиусом 17 обязан отвергаться Иначе тест herdCheckParams_AllSpeciesAccepted_RadiusChecked перестанет ловить эту поломку. При смене herdCheckParams_AllSpeciesAccepted_RadiusChecked проверь это утверждение.");
    }

    @Test
    void huntParams_RadiusAndCountOutsideLimits_AreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 0, "count", 4)),
                "hunt с радиусом 0 обязан отвергаться Иначе тест huntParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене huntParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 49, "count", 4)),
                "hunt с радиусом 49 обязан отвергаться: дальше дичь не выследить Иначе тест huntParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене huntParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HuntParams.from(Map.of("species", "pig", "radius", 16, "count", 65)),
                "hunt с 65 головами обязан отвергаться Иначе тест huntParams_RadiusAndCountOutsideLimits_AreRejected перестанет ловить эту поломку. При смене huntParams_RadiusAndCountOutsideLimits_AreRejected проверь это утверждение.");
    }

    @Test
    void coreScripts_HerdSpecs_ExposeAgreedParams() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();

        for (String id : List.of(
                "breed", "feed", "shear", "milk", "collect_eggs", "harvest_hive", "cull", "herd_check", "hunt")) {
            assertTrue(
                    catalog.find(id).isPresent(),
                    "Каталог обязан содержать скрипт " + id
                            + ": иначе стратег его не вызовет При смене coreScripts_HerdSpecs_ExposeAgreedParams проверь это утверждение.");
        }
        ScriptSpec breed = catalog.find("breed").orElseThrow();
        assertEquals(
                List.of("species", "radius", "pairs"),
                breed.params().stream().map(ParamSpec::name).toList(),
                "breed обязан объявлять species, radius и pairs: другие имена стратег не передаст Иначе тест coreScripts_HerdSpecs_ExposeAgreedParams перестанет ловить эту поломку. При смене coreScripts_HerdSpecs_ExposeAgreedParams проверь это утверждение.");
        ScriptSpec hive = catalog.find("harvest_hive").orElseThrow();
        assertEquals(
                List.of("radius", "count"),
                hive.params().stream().map(ParamSpec::name).toList(),
                "harvest_hive обязан объявлять radius и count: другие имена стратег не передаст Иначе тест coreScripts_HerdSpecs_ExposeAgreedParams перестанет ловить эту поломку. При смене coreScripts_HerdSpecs_ExposeAgreedParams проверь это утверждение.");
        ScriptSpec cull = catalog.find("cull").orElseThrow();
        assertEquals(
                List.of("species", "keep", "radius"),
                cull.params().stream().map(ParamSpec::name).toList(),
                "cull обязан объявлять species, keep и radius Иначе тест coreScripts_HerdSpecs_ExposeAgreedParams перестанет ловить эту поломку. При смене coreScripts_HerdSpecs_ExposeAgreedParams проверь это утверждение.");
    }
}
