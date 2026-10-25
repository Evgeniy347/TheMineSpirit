package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoSpiritGameTests {
    private static final int OBSERVED_TICKS = 100;

    private DemoSpiritGameTests() {}

    private static CommandSourceStack source(ServerPlayer player) {
        return player.server
                .createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static List<SpiritEntity> spiritsNear(GameTestHelper helper, BlockPos absolute, int radius) {
        AABB box = new AABB(absolute).inflate(radius);
        return helper.getLevel().getEntitiesOfClass(SpiritEntity.class, box, spirit -> !spirit.isRemoved());
    }

    @SceneStep(template = "demo_arena", timeoutTicks = 400)
    public static void demoSpirit_ZombieThreeBlocksAway_IsNotTargetedAndNeverRetreats(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos anchorRelative = new BlockPos(15, 1, 15);
        BlockPos anchor = helper.absolutePos(anchorRelative);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_demo_immune");

        int accepted = dispatcher(helper)
                .execute(
                        "spirit demo storage_hut " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ()
                                + " none",
                        source(player));
        helper.assertTrue(accepted == 1, "Демо-стройка обязана начаться по команде оператора: результат=" + accepted);
        List<SpiritEntity> spawned = spiritsNear(helper, anchor, 4);
        helper.assertTrue(!spawned.isEmpty(), "Дух-строитель обязан появиться у площадки: без него защищать некого");
        SpiritEntity spirit = spawned.get(0);
        helper.assertTrue(
                spirit.isDemo(),
                "Дух демо-стройки обязан нести метку демо: без неё мобы берут его целью и тактика пускает отход");
        helper.assertTrue(
                spirit.isInvulnerable(),
                "Демо-дух обязан быть неуязвим: иначе случайный удар прерывает показ постройки");

        player.setGameMode(GameType.SPECTATOR);
        AtomicReference<Zombie> zombie = new AtomicReference<>();
        helper.runAfterDelay(
                2,
                () -> zombie.set(helper.spawn(
                        EntityType.ZOMBIE,
                        helper.relativePos(spirit.blockPosition().offset(3, 0, 0)))));

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            Zombie hunter = zombie.get();
            helper.assertTrue(
                    hunter != null && !spirit.equals(hunter.getTarget()),
                    "Зомби в трёх блоках не имеет права брать демо-духа целью: иначе демонстрацию прерывает бой, "
                            + "а цель зомби " + (hunter == null ? "зомби не появился" : hunter.getTarget()));
            helper.assertTrue(
                    spirit.isAlive(), "Демо-дух обязан пережить соседство с зомби: показ стройки не для гибели духа");
            helper.assertTrue(
                    !spirit.tactics().wantsControl(),
                    "Тактика демо-духа обязана молчать: любой её захват означает отход или бой вместо стройки");
            helper.assertTrue(
                    spirit.tactics().lastOutcome().isEmpty(),
                    "У демо-духа нет решений тактики: он только строит, а найдено "
                            + spirit.tactics().lastOutcome());
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }

    @SceneStep(template = "demo_arena", timeoutTicks = 400)
    public static void demoSpirit_SecondRunSamePlayer_StopsPreviousSession(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos anchor = helper.absolutePos(new BlockPos(15, 1, 15));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_demo_repeat");
        String command =
                "spirit demo storage_hut " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ() + " none";

        helper.assertTrue(
                dispatcher(helper).execute(command, source(player)) == 1,
                "Первая демо-стройка обязана начаться: иначе повторный запуск проверять не на чем");
        List<SpiritEntity> first = spiritsNear(helper, anchor, 5);
        helper.assertTrue(!first.isEmpty(), "Первый дух обязан появиться на площадке");
        SpiritEntity previous = first.get(0);

        helper.assertTrue(
                dispatcher(helper).execute(command, source(player)) == 1,
                "Повторная демо-стройка того же игрока обязана начаться");
        helper.assertTrue(
                previous.isRemoved(),
                "Повторный запуск у того же игрока обязан остановить прежнюю сессию: иначе два духа строят "
                        + "одно место и мешают друг другу");
        List<SpiritEntity> alive = spiritsNear(helper, anchor, 8);
        helper.assertTrue(
                alive.size() == 1,
                "После повторного запуска на площадке обязан остаться ровно один демо-дух, а их " + alive.size());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "demo_arena", timeoutTicks = 400)
    public static void demoSpirit_RemovedSpirit_StripsArmor(GameTestHelper helper) throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos anchor = helper.absolutePos(new BlockPos(15, 1, 15));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_demo_death");
        helper.assertTrue(
                dispatcher(helper)
                                .execute(
                                        "spirit demo storage_hut " + anchor.getX() + " " + anchor.getY() + " "
                                                + anchor.getZ() + " none",
                                        source(player))
                        == 1,
                "Демо-стройка обязана начаться: иначе нечего убирать при исчезновении духа");
        List<SpiritEntity> spawned = spiritsNear(helper, anchor, 5);
        helper.assertTrue(!spawned.isEmpty(), "Демо-дух обязан появиться на площадке");
        SpiritEntity spirit = spawned.get(0);
        spirit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        spirit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        spirit.discard();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    helper.getLevel()
                            .getEntitiesOfClass(
                                    SpiritEntity.class,
                                    new AABB(anchor).inflate(8),
                                    candidate -> !candidate.isRemoved())
                            .isEmpty(),
                    "Исчезнувший демо-дух обязан уйти со сцены: сессия осталась активной");
            helper.assertTrue(
                    spirit.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && spirit.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    "Исчезновение демо-духа обязано снять с него броню: иначе экипировка утекает из сессии");
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }
}
