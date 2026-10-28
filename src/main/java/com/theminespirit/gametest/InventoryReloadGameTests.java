package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.chestCount;
import static com.theminespirit.gametest.InventorySceneSupport.exactMatches;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryReloadGameTests {
    private InventoryReloadGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_ReloadedMidway_CompletesCountNotMore(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        for (int slot = 0; slot < 20; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 1));
        }
        Map<String, Object> params =
                Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8);
        AtomicReference<SpiritEntity> current = new AtomicReference<>(spirit);
        AtomicBoolean launched = new AtomicBoolean();
        AtomicBoolean reloaded = new AtomicBoolean();
        helper.onEachTick(() -> {
            SpiritEntity live = current.get();
            if (!launched.get() && live.tickCount >= 1) {
                launched.set(true);
                live.startTask(new ScriptCall("deposit", params));
            } else if (launched.get() && !reloaded.get() && chestCount(helper, Items.COBBLESTONE) >= 4) {
                reloaded.set(true);
                CompoundTag tag = new CompoundTag();
                live.saveWithoutId(tag);
                live.discard();
                SpiritEntity next = ModEntities.SPIRIT.get().create(helper.getLevel());
                next.load(tag);
                helper.getLevel().addFreshEntity(next);
                current.set(next);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    reloaded.get(),
                    "дух обязан быть перезагружен посередине переноса: иначе возобновление не проверено");
            SpiritEntity live = current.get();
            helper.assertTrue(
                    live.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный deposit обязан завершиться успехом, причина: "
                            + live.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 10,
                    "итого в сундуке обязано оказаться 10, а не 14: после восстановления переносят "
                            + "count − moved, фактически " + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(live.getInventory(), Items.COBBLESTONE) == 10,
                    "у духа обязано остаться 10 булыжника из 20: перенесены только недостающие 6, фактически "
                            + GameTestFixtures.countInInventory(live.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void registryGuard_ExactIdsInTables_ExistInGameRegistry(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        List<String> matches = exactMatches();
        List<String> missing = new ArrayList<>();
        for (String match : matches) {
            String id = match.contains(":") ? match : "minecraft:" + match;
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))) {
                missing.add(id);
            }
        }

        helper.assertTrue(
                !matches.isEmpty(), "сторож бессмыслен без точных id: в таблицах обязаны быть не только шаблоны");
        helper.assertTrue(
                missing.isEmpty(),
                "все точные id из item_values.json и base_kit.json обязаны существовать в реестре предметов: "
                        + "иначе ценность и набор ссылаются на несуществующие предметы. Отсутствуют: " + missing);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
