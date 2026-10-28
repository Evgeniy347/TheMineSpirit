package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.countStored;
import static com.theminespirit.gametest.InventorySceneSupport.snapshotInventory;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryEquipGameTests {
    private InventoryEquipGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_IronChestplate_SwapsAndPinsDropChance(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
        Map<String, Object> params = Map.of("item", "minecraft:iron_chestplate");
        SpiritRun first = SpiritRun.launch(helper, spirit, new ScriptCall("equip", params), 1);
        AtomicBoolean relaunched = new AtomicBoolean();
        AtomicReference<SpiritRun> second = new AtomicReference<>();
        AtomicReference<List<String>> afterFirst = new AtomicReference<>();

        helper.succeedWhen(() -> {
            first.assertStarted();
            if (!relaunched.get()) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                        "equip железного нагрудника обязан завершиться успехом, причина: "
                                + spirit.taskFailureReason().orElse("нет"));
                helper.assertTrue(first.spiritTicks() <= 120, "сценарий обязан укладываться в 120 тиков духа");
                afterFirst.set(snapshotInventory(spirit));
                second.set(SpiritRun.launch(helper, spirit, new ScriptCall("equip", params), spirit.tickCount));
                relaunched.set(true);
            }
            helper.assertTrue(
                    second.get() != null && spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "повторный equip обязан завершиться успехом без изменений, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            second.get().assertStarted();
            helper.assertTrue(
                    spirit.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "железный нагрудник обязан остаться надетым");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER_CHESTPLATE) == 1,
                    "прежний кожаный нагрудник обязан вернуться в инвентарь, а не исчезнуть");
            helper.assertTrue(
                    snapshotInventory(spirit).equals(afterFirst.get()),
                    "повторный вызов обязан ничего не менять (идемпотентность)");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_PickaxePattern_TakesMostValuable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.STONE_PICKAXE));
        spirit.getInventory().setItem(2, new ItemStack(Items.IRON_PICKAXE));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:*_pickaxe")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "equip по шаблону обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE),
                    "по шаблону *_pickaxe берётся самая ценная (железная), а не первая попавшаяся: в руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 0,
                    "остальные кирки обязаны остаться в инвентаре");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_WornWoodenPickaxeWithIronInInventory_SwapsToIron(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:*_pickaxe")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "equip по шаблону обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE),
                    "в руке деревянная кирка, а в инвентаре железная подходит под тот же шаблон: берётся более "
                            + "ценная, иначе дух вечно работал бы худшим инструментом. В руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1,
                    "прежняя деревянная кирка обязана вернуться в инвентарь");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_MissingItem_FailsWithoutChanges(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:diamond_sword")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "отсутствующий предмет обязан проваливать equip, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("нет предмета"),
                    "причина обязана говорить, что предмета нет в инвентаре, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    spirit.getMainHandItem().isEmpty(),
                    "при провале рука обязана остаться пустой: ничего не перемещено");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_CraftEquipGatherSwap_KeepsEveryTool(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        helper.setBlock(FullCycleScene.TABLE, Blocks.CRAFTING_TABLE);
        FullCycleScene.placeCobblestoneRow(helper, 3);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 3));
        spirit.getInventory().addItem(new ItemStack(Items.STICK, 2));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        EquipRoundtrip roundtrip = new EquipRoundtrip(helper, spirit);
        helper.succeedWhen(() -> {
            roundtrip.step();
        });
    }

    static final class EquipRoundtrip {
        private final GameTestHelper helper;
        private final SpiritEntity spirit;
        private SpiritRun current;
        private int phase;

        private EquipRoundtrip(GameTestHelper helper, SpiritEntity spirit) {
            this.helper = helper;
            this.spirit = spirit;
            current = launch("craft", Map.of("item", "stone_pickaxe", "count", 1, "radius", 8));
        }

        private SpiritRun launch(String script, Map<String, Object> params) {
            return SpiritRun.launch(helper, spirit, new ScriptCall(script, params), Math.max(1, spirit.tickCount));
        }

        private void step() {
            current.assertStarted();
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status != RunStatus.RUNNING, "шаг цепочки ещё выполняется: " + status);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "шаг " + phase + " цепочки craft → equip → gather → equip другого обязан завершиться успехом, "
                            + "статус: " + status + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            switch (phase) {
                case 0 -> afterCraft();
                case 1 -> afterEquip();
                case 2 -> afterRelease();
                case 3 -> afterGather();
                case 4 -> afterSwap();
                default -> helper.assertTrue(false, "лишний шаг " + phase + ": цепочка уже закончилась");
            }
        }

        private void afterCraft() {
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "craft обязан положить каменную кирку в инвентарь, фактически: "
                            + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("equip", Map.of("item", "minecraft:stone_pickaxe"));
            phase = 1;
            helper.assertTrue(false, "ждём equip: кирка в инвентаре, рука пока пуста");
        }

        private void afterEquip() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "equip обязан надеть кирку в настоящую руку, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) == 0,
                    "кирка после equip живёт в руке, а не в инвентаре: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE));
            spirit.gear().holdNothing();
            phase = 2;
            helper.assertTrue(false, "ждём проверки кирки после освобождения руки");
        }

        private void afterRelease() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "INC-12: тактика освобождает только свою копию, а настоящая кирка после equip обязана "
                            + "остаться в руке, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "INC-12: кирка, вынутая из инвентаря для equip, не имеет права исчезнуть; в руке "
                            + spirit.getMainHandItem() + ", всего " + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8));
            phase = 3;
            helper.assertTrue(false, "ждём gather: кирка снова у духа");
        }

        private void afterGather() {
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "gather не вправе потерять кирку: в руке " + spirit.getMainHandItem() + ", всего "
                            + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("equip", Map.of("item", "minecraft:stone_axe"));
            phase = 4;
            helper.assertTrue(false, "ждём equip топора: кирка должна остаться у духа");
        }

        private void afterSwap() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_AXE),
                    "equip другого инструмента обязан надеть топор, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "equip другого инструмента обязан вернуть прежнюю кирку в инвентарь, всего "
                            + countStored(spirit, Items.STONE_PICKAXE));
            spirit.gear().holdNothing();
            helper.assertTrue(
                    countStored(spirit, Items.STONE_AXE) == 1 && countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "после освобождения руки оба инструмента обязаны остаться у духа: топор "
                            + countStored(spirit, Items.STONE_AXE) + ", кирка "
                            + countStored(spirit, Items.STONE_PICKAXE));
            TacticsScene.finish(helper, spirit);
        }
    }
}
