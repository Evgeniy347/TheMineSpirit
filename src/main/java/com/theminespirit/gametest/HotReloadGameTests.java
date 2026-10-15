package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.DataReload;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HotReloadGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK = "theminespirit_hotreload";

    // Датапак меняет порог «почти сломанной» брони: при worn_fraction 0.99 слегка потёртая железная кираса
    // считается сломанной и уступает целой кожаной. Без перезагрузки дух надел бы железную (по защите).
    private static final String OVERRIDE = """
            {
              "worn_fraction": 0.99,
              "check_interval_ticks": 1,
              "material_order": [
                { "match": ["leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"], "rank": 0 },
                { "match": ["iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"], "rank": 3 }
              ]
            }
            """;

    private HotReloadGameTests() {
    }

    @GameTest(template = "arena", batch = "hotreload_apply", timeoutTicks = 300)
    public static void tables_DatapackOverridesArmor_SpiritWearsByNewTable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ItemStack wornIron = new ItemStack(Items.IRON_CHESTPLATE);
        wornIron.setDamageValue(wornIron.getMaxDamage() / 2);
        spirit.setItemSlot(EquipmentSlot.CHEST, wornIron);
        spirit.getInventory().setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });
        try {
            reloadFromDatapack(helper, OVERRIDE);
            helper.succeedWhen(() -> {
                scene.assertStarted();
                helper.assertTrue(DataTables.INSTANCE.armor().current().checkIntervalTicks() == 1,
                        "после перезагрузки таблица брони обязана прийти из датапака мира (check_interval_ticks = 1), "
                                + "а не из мода (40); фактически "
                                + DataTables.INSTANCE.armor().current().checkIntervalTicks());
                helper.assertTrue(spirit.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE),
                        "по новой таблице потёртая железная кираса считается сломанной и дух обязан надеть кожаную; "
                                + "в слоте груди " + spirit.getItemBySlot(EquipmentSlot.CHEST));
                GameTestFixtures.finish(helper);
            });
        } finally {
            cleanupDatapack(helper);
        }
    }

    @GameTest(template = "arena", batch = "hotreload_broken", timeoutTicks = 300)
    public static void tables_BrokenJson_KeepsOldTableAndReportsError(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        int before = DataTables.INSTANCE.armor().current().checkIntervalTicks();
        try {
            reloadFromDatapack(helper, "{ это не json ");
            boolean reported = DataReload.lastReload().stream()
                    .anyMatch(report -> report.id().equals("armor") && report.failed());
            helper.assertTrue(reported,
                    "битый armor_table.json обязан попасть в отчёт перезагрузки ошибкой: иначе ошибка не видна");
            helper.assertTrue(DataTables.INSTANCE.armor().status().failed(),
                    "статус таблицы брони обязан нести ошибку последней перезагрузки");
            helper.assertTrue(DataTables.INSTANCE.armor().current().checkIntervalTicks() == before,
                    "битый json не подменяет таблицу: работает прежняя (check_interval_ticks = " + before + "); "
                            + "фактически " + DataTables.INSTANCE.armor().current().checkIntervalTicks());
            GameTestFixtures.finish(helper);
            helper.succeed();
        } finally {
            cleanupDatapack(helper);
        }
    }

    // Перезагрузка через настоящий серверный ресурс-менеджер: берём текущие выбранные паки сервера и добавляем
    // поверх датапак теста. Так тот же слушатель AddReloadListenerEvent читает датапак, что и при /reload.
    private static void reloadFromDatapack(GameTestHelper helper, String armorJson) {
        MinecraftServer server = helper.getLevel().getServer();
        writeDatapack(helper, armorJson);
        List<PackResources> packs = new ArrayList<>(server.getPackRepository().openAllSelected());
        packs.add(new PathPackResources(
                new PackLocationInfo("file/" + PACK, Component.literal(PACK), PackSource.WORLD, Optional.empty()),
                packDir(helper)));
        MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
        DataReload.reloadFrom(manager);
        manager.close();
    }

    private static void writeDatapack(GameTestHelper helper, String armorJson) {
        Path pack = packDir(helper);
        Path dir = pack.resolve("data/theminespirit/armor");
        try {
            Files.createDirectories(dir);
            Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\": {\"pack_format\": 48, \"description\": \"hot reload gametest\"}}\n",
                    StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("armor_table.json"), armorJson, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("датапак теста не записан: " + error.getMessage(), error);
        }
    }

    private static void cleanupDatapack(GameTestHelper helper) {
        Path pack = packDir(helper);
        try {
            if (Files.exists(pack)) {
                try (Stream<Path> walk = Files.walk(pack)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException error) {
                            LOGGER.warn("датапак теста не удалён ({}): {}", path, error.getMessage());
                        }
                    });
                }
            }
        } catch (IOException error) {
            LOGGER.warn("датапак теста не убран полностью: {}", error.getMessage());
        }
    }

    private static Path packDir(GameTestHelper helper) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(PACK);
    }
}
