package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptSpec;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritScriptsGameTests {
    private SpiritScriptsGameTests() {
    }

    @GameTest(batch = "rest", template = "arena", timeoutTicks = 100)
    public static void catalog_Static_ListsExactlyRegistryScriptsInDefaultCatalogOrder(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));

        List<String> catalogIds = SpiritScripts.catalog().all().stream().map(ScriptSpec::id).toList();
        Set<String> registryIds = SpiritScripts.registryFor(spirit).ids();
        List<String> defaultOrder = CoreScripts.defaultCatalog().all().stream().map(ScriptSpec::id)
                .filter(registryIds::contains).toList();

        helper.assertTrue(Set.copyOf(catalogIds).equals(registryIds),
                "Каталог реализованных скриптов и реестр исполнения берут перечень из одной таблицы и не расходятся: "
                        + "каталог " + catalogIds + ", реестр " + registryIds);
        helper.assertTrue(catalogIds.equals(defaultOrder),
                "capabilities обязан перечислять скрипты в порядке полного каталога, а не в случайном порядке "
                        + "хэш-карты: ожидалось " + defaultOrder + ", получено " + catalogIds);
        helper.assertTrue(SpiritScripts.catalogFor(spirit).all().equals(SpiritScripts.catalog().all()),
                "catalogFor(spirit) обязан совпадать со статическим catalog(): планировщик и REST видят один каталог");
        helper.succeed();
    }

    @GameTest(batch = "rest", template = "arena", timeoutTicks = 100)
    public static void acquire_InCatalogAndRegistry_ListedAsImplemented(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));

        List<String> catalogIds = SpiritScripts.catalog().all().stream().map(ScriptSpec::id).toList();
        Set<String> registryIds = SpiritScripts.registryFor(spirit).ids();

        helper.assertTrue(catalogIds.contains("acquire"),
                "acquire обязан появиться в каталоге реализованных скриптов после регистрации, каталог: "
                        + catalogIds);
        helper.assertTrue(registryIds.contains("acquire"),
                "acquire обязан попасть в реестр исполнения, реестр: " + registryIds);
        helper.succeed();
    }
}
