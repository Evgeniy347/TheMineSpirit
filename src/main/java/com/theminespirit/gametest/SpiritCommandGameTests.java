package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritCommandGameTests {
    // Цель, под которую нет правила в FallbackPlanner и нет скрипта в каталоге: тест отказа не должен
    // ломаться, когда очередной скрипт начнёт реализовывать «сруби дерево» или «добудь руду».
    private static final String UNPLANNABLE_GOAL = "построй дом";

    private SpiritCommandGameTests() {}

    private static String tagSelector(String tag) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,tag=" + tag + "]";
    }

    // Селектор по расстоянию видит и духов соседних тестов пачки (структуры стоят в 8 клетках друг от друга),
    // и команда ложится на чужого духа; тег уникален на запуск и выбирает только духов своего теста.
    private static String freshTag(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private static SpiritEntity spawnTagged(GameTestHelper helper, BlockPos relative, String tag) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.addTag(tag);
        return spirit;
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void commandFollow_MockPlayerSource_SpiritApproachesPlayer(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        String tag = freshTag("follow");
        SpiritEntity spirit = spawnTagged(helper, new BlockPos(0, 1, 1), tag);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(4, 1, 1), "gt_follow_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();

        helper.assertTrue(
                dispatcher.getRoot().getChild("spirit") != null,
                "Команда /spirit обязана быть зарегистрирована на сервере: без неё оператор не получит канал управления духом");

        double before = spirit.position().distanceTo(player.position());
        int accepted = dispatcher.execute("spirit goal " + tagSelector(tag) + " следуй за мной", source);
        helper.assertTrue(
                accepted == 1,
                "Выполненная игроком цель «следуй за мной» обязана ставить задачу: подстановка заглушки 'player' "
                        + "или потеря имени игрока-источника приводит к отказу, результат=" + accepted);
        helper.assertTrue(
                spirit.taskStatus()
                        .filter(status -> status == RunStatus.RUNNING)
                        .isPresent(),
                "Сразу после команды дух обязан быть в состоянии «выполняется»: команда не запустила задачу. "
                        + "Состояние=" + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.taskStatus()
                            .filter(status -> status != RunStatus.FAILED)
                            .isPresent(),
                    "Сопровождение не должно быть провалено: цель '"
                            + player.getName().getString()
                            + "' не найдена, потому что команда не передала имя игрока. Состояние="
                            + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());
            double after = spirit.position().distanceTo(player.position());
            helper.assertTrue(
                    after < before,
                    "Дух обязан приближаться к игроку-источнику: было " + before + ", стало " + after
                            + ". Иначе follow не выполняет сопровождение");
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void commandGoalThenCancel_OnSelectedSpirit_StartsAndCancelsTask(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.sweepEntities(helper);
        String tag = freshTag("cancel");
        SpiritEntity spirit = spawnTagged(helper, new BlockPos(1, 1, 1), tag);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 1), "gt_cancel_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        String selector = tagSelector(tag);

        int accepted = dispatcher.execute("spirit goal " + selector + " следуй за мной", source);
        helper.assertTrue(
                accepted == 1, "Команда goal обязана вернуть число принявших цель духов: результат=" + accepted);
        helper.assertTrue(
                spirit.taskStatus()
                        .filter(status -> status == RunStatus.RUNNING)
                        .isPresent(),
                "После /spirit goal дух обязан быть в состоянии «выполняется»: команда не запустила задачу. "
                        + "Состояние=" + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());

        int rejected = dispatcher.execute("spirit goal " + selector + " " + UNPLANNABLE_GOAL, source);
        helper.assertTrue(
                rejected == 0,
                "Цель '" + UNPLANNABLE_GOAL + "', которую не умеет планировщик и не реализует ни один скрипт, "
                        + "обязана вернуть 0 и не заменить выполняемую задачу: результат=" + rejected);
        helper.assertTrue(
                spirit.taskStatus()
                        .filter(status -> status == RunStatus.RUNNING)
                        .isPresent(),
                "Отклонённая команда goal не должна трогать уже выполняемую задачу: статус духа изменился");

        int cancelled = dispatcher.execute("spirit cancel " + selector, source);
        helper.assertTrue(
                cancelled == 1,
                "Команда cancel обязана вернуть число духов с отменённой задачей: результат=" + cancelled);
        helper.assertTrue(
                spirit.taskStatus()
                        .filter(status -> status == RunStatus.CANCELLED)
                        .isPresent(),
                "После /spirit cancel задача духа обязана считаться отменённой: команда не остановила задачу");

        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void commandGoal_OnTwoSpirits_AppliesToAllOrNone(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        String tag = freshTag("pair");
        SpiritEntity first = spawnTagged(helper, new BlockPos(0, 1, 1), tag);
        SpiritEntity second = spawnTagged(helper, new BlockPos(0, 1, 2), tag);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(4, 1, 1), "gt_pair_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        String both = tagSelector(tag);

        int rejected = dispatcher.execute("spirit goal " + both + " " + UNPLANNABLE_GOAL, source);
        helper.assertTrue(
                rejected == 0,
                "Цель '" + UNPLANNABLE_GOAL + "', которую не умеет планировщик и не реализует ни один скрипт, "
                        + "обязана вернуть 0: результат=" + rejected);
        helper.assertTrue(
                first.taskStatus().isEmpty() && second.taskStatus().isEmpty(),
                "При отказе планирования ни один дух не должен получить задачу: первый=" + first.taskStatus()
                        + ", второй=" + second.taskStatus());

        int accepted = dispatcher.execute("spirit goal " + both + " следуй за мной", source);
        helper.assertTrue(accepted == 2, "Выполнимая цель обязана стать задачей обоим духам: результат=" + accepted);
        helper.assertTrue(
                first.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent()
                        && second.taskStatus()
                                .filter(status -> status == RunStatus.RUNNING)
                                .isPresent(),
                "После /spirit goal оба духа обязаны быть в состоянии «выполняется»: первый=" + first.taskStatus()
                        + ", второй=" + second.taskStatus());

        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void commandGoal_TwoSpiritsOnArena_PlansFromEachOwnObservation(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        helper.setBlock(0, 0, 0, Blocks.STONE);
        helper.setBlock(10, 0, 10, Blocks.STONE);
        // Тег уникален на запуск: мир переживает прогоны, и духи с прошлым тегом попали бы в селектор.
        String tag = freshTag("observe_each");
        SpiritEntity first = spawnTagged(helper, new BlockPos(0, 1, 0), tag);
        SpiritEntity second = spawnTagged(helper, new BlockPos(10, 1, 10), tag);
        second.getInventory().setItem(0, new ItemStack(Items.IRON_AXE, 1));
        helper.assertTrue(
                GameTestFixtures.countInInventory(second.getInventory(), Items.IRON_AXE) == 1,
                "Подготовка обязана положить топор в инвентарь второго духа: без разного инвентаря наблюдения "
                        + "духов неразличимы и тест не доказывает по-духовые наблюдения");
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 5), "gt_observe_each");
        try {
            CommandSourceStack source = playerSource(player);
            CommandDispatcher<CommandSourceStack> dispatcher =
                    helper.getLevel().getServer().getCommands().getDispatcher();
            String both = tagSelector(tag);

            int accepted = dispatcher.execute("spirit goal " + both + " следуй за мной", source);
            helper.assertTrue(
                    accepted == 2,
                    "Одна команда на двух духов обязана ставить задачу обоим: общее наблюдение первого духа "
                            + "сторож BatchPlanner отклоняет, и задача не ставится никому. Результат=" + accepted);
            helper.assertTrue(
                    first.taskStatus()
                                    .filter(status -> status == RunStatus.RUNNING)
                                    .isPresent()
                            && second.taskStatus()
                                    .filter(status -> status == RunStatus.RUNNING)
                                    .isPresent(),
                    "После /spirit goal оба духа обязаны выполняться: первый=" + first.taskStatus() + ", второй="
                            + second.taskStatus());

            helper.succeed();
        } finally {
            first.discard();
            second.discard();
        }
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void commandFollow_ServerSource_RefusesWithoutTask(GameTestHelper helper)
            throws CommandSyntaxException {
        String tag = freshTag("server_source");
        SpiritEntity spirit = spawnTagged(helper, new BlockPos(1, 1, 1), tag);
        CommandSourceStack base = helper.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(4);
        List<String> failures = new ArrayList<>();
        CommandSourceStack source = base.withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                failures.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return base.source.acceptsSuccess();
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();

        int accepted = dispatcher.execute("spirit goal " + tagSelector(tag) + " следуй за мной", source);

        helper.assertTrue(
                accepted == 0,
                "Цель 'за мной' без игрока-источника обязана возвращать 0, а не ставиться с пустым target: "
                        + "результат=" + accepted);
        helper.assertTrue(
                spirit.taskStatus().isEmpty(),
                "Отказ без игрока-источника не должен запускать задачу: у духа появился статус " + spirit.taskStatus());
        helper.assertTrue(
                failures.size() == 1
                        && failures.get(0).contains("требует игрока-источника")
                        && failures.get(0).contains("следуй за мной"),
                "Отказ без игрока-источника обязан называть причину текстом с целью и подсказкой: молчаливый отказ "
                        + "или чужой текст не объяснит оператору, что команду надо выполнить от имени игрока. "
                        + "Получено: " + failures);
        helper.succeed();
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void commandGoal_ByFullUuid_FiveSpiritsTargetsOnlyThatSpirit(GameTestHelper helper)
            throws CommandSyntaxException {
        assertGoalTokenSelectsOne(helper, "uuid_full", spirit -> spirit.getStringUUID(), "полный UUID");
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void commandGoal_ByUuidPrefix_FiveSpiritsTargetsOnlyThatSpirit(GameTestHelper helper)
            throws CommandSyntaxException {
        assertGoalTokenSelectsOne(
                helper, "uuid_prefix", spirit -> spirit.getStringUUID().substring(0, 8), "8-значный UUID из лога");
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void commandGoal_ByDisplayName_FiveSpiritsTargetsOnlyThatSpirit(GameTestHelper helper)
            throws CommandSyntaxException {
        assertGoalTokenSelectsOne(helper, "name", SpiritEntity::displayName, "имя духа");
    }

    private static void assertGoalTokenSelectsOne(
            GameTestHelper helper, String tagPrefix, Function<SpiritEntity, String> token, String what)
            throws CommandSyntaxException {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        String tag = freshTag(tagPrefix);
        SpiritEntity[] spirits = new SpiritEntity[5];
        for (int i = 0; i < spirits.length; i++) {
            spirits[i] = spawnTagged(helper, new BlockPos(1 + i, 1, 1), tag);
        }
        SpiritEntity chosen = spirits[0];
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(4, 1, 1), "gt_goal_" + tagPrefix);
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            int accepted;
            try {
                accepted = dispatcher.execute("spirit goal " + token.apply(chosen) + " следуй за мной", source);
            } catch (CommandSyntaxException failure) {
                helper.fail("spirit goal по " + what + " '" + token.apply(chosen) + "' не выполнилась: "
                        + failure.getMessage() + ". INC-8: игрок указывает одного духа");
                return;
            }
            helper.assertTrue(
                    accepted == 1,
                    "spirit goal по " + what + " одного духа обязана выбрать только его: результат=" + accepted
                            + ", всего духов=" + spirits.length + ". INC-8");
            helper.assertTrue(
                    chosen.taskStatus().filter(s -> s == RunStatus.RUNNING).isPresent(),
                    "Выбранный дух обязан получить задачу: статус=" + chosen.taskStatus());
            for (int i = 1; i < spirits.length; i++) {
                helper.assertTrue(
                        spirits[i].taskStatus().isEmpty(),
                        "Дух " + spirits[i].displayName() + " (" + spirits[i].getStringUUID()
                                + ") не обязан получать цель: статус=" + spirits[i].taskStatus()
                                + ", ожидаемо пусто. INC-8");
            }
        } finally {
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
        }
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void commandSpawn_Twice_GivesDifferentNumberedNames(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        buildFloor(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(2, 1, 1), "gt_spawn_names");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();

        helper.assertTrue(dispatcher.execute("spirit spawn", source) == 1, "Первый /spirit spawn обязан призвать духа");
        helper.assertTrue(
                dispatcher.execute("spirit spawn", source) == 1, "Второй /spirit spawn обязан призвать второго духа");

        List<SpiritEntity> spawned = helper.getLevel()
                .getEntitiesOfClass(
                        SpiritEntity.class, player.getBoundingBox().inflate(2), spirit -> !spirit.isRemoved());
        helper.assertTrue(
                spawned.size() == 2, "После двух /spirit spawn рядом с игроком два духа, фактически " + spawned.size());
        String first = spawned.get(0).displayName();
        String second = spawned.get(1).displayName();
        helper.assertTrue(
                !first.equals(second),
                "Два заспавненных духа обязаны иметь разные имена, фактически '" + first + "' и '" + second
                        + "': иначе журнал не различит исполнителей");
        helper.assertTrue(
                !SpiritEntity.DEFAULT_NAME.equals(first) && !SpiritEntity.DEFAULT_NAME.equals(second),
                "Имена после /spirit spawn не остаются общим «spirit»: " + first + ", " + second);
        for (SpiritEntity spirit : spawned) {
            spirit.discard();
        }
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static CommandSourceStack playerSource(ServerPlayer player) {
        return player.server
                .createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}
