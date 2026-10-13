package com.theminespirit.gametest;

import static com.theminespirit.gametest.PerceptionTestSupport.TEST_TIMEOUT_TICKS;
import static com.theminespirit.gametest.PerceptionTestSupport.assertSucceeded;
import static com.theminespirit.gametest.PerceptionTestSupport.coordOf;
import static com.theminespirit.gametest.PerceptionTestSupport.launch;
import static com.theminespirit.gametest.PerceptionTestSupport.spawnSpirit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryStore;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.core.perception.Sighting;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MemoryGameTests {
    private MemoryGameTests() {
    }

    private static String tagSelector(String tag) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,tag=" + tag + "]";
    }

    // Селектор по расстоянию видит и духов соседних тестов пачки (структуры стоят в 8 клетках друг от друга),
    // и команда ложится на чужого духа; тег уникален на запуск и выбирает только духа своего теста.
    private static String freshTag(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private static SpiritEntity spawnTagged(GameTestHelper helper, int x, int y, int z, String tag) {
        SpiritEntity spirit = PerceptionTestSupport.spawnSpirit(helper, x, y, z);
        spirit.addTag(tag);
        return spirit;
    }

    private static ScriptCall remember(String name, BlockCoord pos, MemoryType type) {
        return new ScriptCall("remember", Map.of("name", name, "x", pos.x(), "y", pos.y(), "z", pos.z(),
                "type", type.id()));
    }

    private static String dimensionOf(GameTestHelper helper) {
        return helper.getLevel().dimension().location().toString();
    }

    @GameTest(template = "arena", batch = "memory_remember_recall", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void rememberThenRecall_Point_ReturnsPositionTypeAndDimension(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        BlockCoord iron = coordOf(helper, 7, 1, 2);
        SpiritRun run = launch(helper, spirit, new SequenceNode(List.of(
                remember("iron", iron, MemoryType.RESOURCE),
                new ScriptCall("recall", Map.of("name", "iron")))));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "remember и recall");
            Finding expected = new Finding("recall",
                    List.of(new Sighting("resource", "iron", iron, dimensionOf(helper))));
            helper.assertTrue(expected.equals(spirit.finding().orElse(null)),
                    "recall обязан вернуть позицию, тип и измерение запомненной точки " + expected + ", а вернул: "
                            + spirit.finding().orElse(null));
            helper.assertTrue(spirit.memory().recall("iron") != null,
                    "после remember точка обязана лежать в памяти духа");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "memory_recall_all", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void recallAll_MixedTypes_ReturnsOnlyPointsOfRequestedType(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        SpiritRun run = launch(helper, spirit, new SequenceNode(List.of(
                remember("iron", coordOf(helper, 1, 1, 1), MemoryType.RESOURCE),
                remember("lava_lake", coordOf(helper, 2, 1, 2), MemoryType.DANGER),
                remember("home", coordOf(helper, 3, 1, 3), MemoryType.BASE),
                remember("coal", coordOf(helper, 5, 1, 5), MemoryType.RESOURCE),
                new ScriptCall("recall_all", Map.of("type", "resource")))));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "recall_all");
            Finding finding = spirit.finding().orElse(null);
            helper.assertTrue(finding != null && finding.script().equals("recall_all"),
                    "последним ответом обязан быть ответ recall_all: " + finding);
            List<String> names = finding.sightings().stream().map(Sighting::label).sorted().toList();
            helper.assertTrue(names.equals(List.of("coal", "iron")),
                    "recall_all по типу resource обязан вернуть ровно coal и iron, а вернул " + names
                            + ": точки других типов (опасность, база) попали в ответ или ресурсы потеряны");
            helper.assertTrue(finding.sightings().stream().allMatch(sighting -> sighting.kind().equals("resource")),
                    "все точки ответа обязаны быть типа resource: " + finding.sightings());
            helper.assertTrue(spirit.memory().all().size() == 4,
                    "recall_all не должен менять память, точек в ней " + spirit.memory().all().size());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "memory_forget", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void forget_RememberedPoint_IsNotFoundByRecall(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        SpiritRun run = launch(helper, spirit, new SequenceNode(List.of(
                remember("iron", coordOf(helper, 7, 1, 2), MemoryType.RESOURCE),
                new ScriptCall("forget", Map.of("name", "iron")),
                new ScriptCall("recall", Map.of("name", "iron")))));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "remember, forget и recall");
            Finding finding = spirit.finding().orElse(null);
            helper.assertTrue(finding != null && finding.script().equals("recall") && finding.isEmpty(),
                    "после forget recall обязан отвечать «не найдено» (пустая находка), а ответил: " + finding);
            helper.assertTrue(spirit.memory().all().isEmpty(),
                    "после forget единственной точки память обязана быть пустой: " + spirit.memory().all());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "memory_nbt_roundtrip", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void memory_SavedAndReloaded_KeepsPointsAndDimension(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        BlockCoord iron = coordOf(helper, 7, 1, 2);
        BlockCoord home = coordOf(helper, 1, 1, 1);
        SpiritRun run = launch(helper, spirit, new SequenceNode(List.of(
                remember("iron", iron, MemoryType.RESOURCE), remember("home", home, MemoryType.BASE))));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "remember");
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            helper.assertTrue(tag.contains(SpiritMemoryStore.KEY, Tag.TAG_STRING),
                    "при сохранении духа память обязана попасть в NBT под ключом " + SpiritMemoryStore.KEY
                            + ": иначе точки не переживут перезапуск сервера");
            SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
            reloaded.load(tag);
            MemoryStore memory = reloaded.memory();
            helper.assertTrue(new MemoryPoint("iron", MemoryType.RESOURCE, dimensionOf(helper), iron)
                            .equals(memory.recall("iron"))
                            && new MemoryPoint("home", MemoryType.BASE, dimensionOf(helper), home)
                            .equals(memory.recall("home")),
                    "после загрузки из NBT дух обязан помнить обе точки с типом и измерением, а помнит: "
                            + memory.all());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "memory_corrupt", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void memory_CorruptNbt_LoadsEmptyAndCountsFailure(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        spirit.updateMemory(new MemoryStore(List.of(
                new MemoryPoint("iron", MemoryType.RESOURCE, dimensionOf(helper), coordOf(helper, 7, 1, 2)))));
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        tag.putString(SpiritMemoryStore.KEY, "{\"version\":1,\"points\":[{\"name\":");
        int failuresBefore = SpiritMemoryStore.corruptLoads();

        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);

        helper.assertTrue(reloaded.memory().all().isEmpty(),
                "повреждённая память обязана загрузиться пустой, а не частично: " + reloaded.memory().all());
        helper.assertTrue(SpiritMemoryStore.corruptLoads() == failuresBefore + 1,
                "повреждённая память обязана быть посчитана: счётчик был " + failuresBefore + ", стал "
                        + SpiritMemoryStore.corruptLoads() + ". Тихая потеря памяти — авария");
        helper.assertTrue(!reloaded.isRemoved(),
                "повреждённая память не должна ронять загрузку духа");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "memory_full", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void remember_SeventeenthPointOfType_FailsWithReadableReason(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        List<MemoryPoint> full = new ArrayList<>();
        for (int i = 0; i < MemoryStore.MAX_PER_TYPE; i++) {
            full.add(new MemoryPoint("ore_" + i, MemoryType.RESOURCE, dimensionOf(helper), coordOf(helper, 1, 1, 1)));
        }
        spirit.updateMemory(new MemoryStore(full));
        SpiritRun run = launch(helper, spirit, "remember", Map.of("name", "one_more", "x", 0, "y", 64, "z", 0,
                "type", "resource"));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "семнадцатая точка типа обязана провалить remember, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("resource") && reason.contains("заполнена"),
                    "причина отказа обязана назвать тип и то, что память заполнена, а она: '" + reason + "'");
            helper.assertTrue(spirit.memory().recall("one_more") == null && spirit.memory().all().size() == 16,
                    "отказ не должен ни вытеснять старую точку, ни добавлять новую: " + spirit.memory().all().size());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "memory_command", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void spiritMemoryCommand_RecallAllRecallAndForget_ReportsAndChangesMemory(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        String tag = freshTag("memory");
        SpiritEntity spirit = spawnTagged(helper, 4, 1, 4, tag);
        String selector = tagSelector(tag);
        BlockCoord pos = coordOf(helper, 2, 1, 2);
        spirit.updateMemory(new MemoryStore(List.of(
                new MemoryPoint("iron", MemoryType.RESOURCE, dimensionOf(helper), pos),
                new MemoryPoint("coal", MemoryType.RESOURCE, dimensionOf(helper), pos),
                new MemoryPoint("lava_lake", MemoryType.DANGER, dimensionOf(helper), pos))));
        List<String> messages = new ArrayList<>();
        CommandSourceStack base = helper.getLevel().getServer().createCommandSourceStack()
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)))).withPermission(4);
        CommandSourceStack source = base.withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
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
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();

        int listed = dispatcher.execute("spirit memory " + selector + " all resource", source);
        helper.assertTrue(listed == 1 && messages.size() == 1 && messages.get(0).contains("Точек типа resource: 2")
                        && messages.get(0).contains("iron") && messages.get(0).contains("coal")
                        && !messages.get(0).contains("lava_lake"),
                "recall all resource обязан перечислить только iron и coal, ответ: " + messages);
        messages.clear();
        int recalled = dispatcher.execute("spirit memory " + selector + " recall lava_lake", source);
        helper.assertTrue(recalled == 1 && messages.get(0).contains("lava_lake: danger"),
                "recall обязан назвать имя и тип точки, ответ: " + messages);
        messages.clear();
        dispatcher.execute("spirit memory " + selector + " recall gold", source);
        helper.assertTrue(messages.get(0).contains("в памяти нет"),
                "recall несуществующей точки обязан сказать, что её нет, ответ: " + messages);
        messages.clear();
        dispatcher.execute("spirit memory " + selector + " forget iron", source);
        helper.assertTrue(spirit.memory().recall("iron") == null && spirit.memory().all().size() == 2,
                "forget обязан убрать только iron, в памяти осталось " + spirit.memory().all());
        messages.clear();
        int rejected = dispatcher.execute("spirit memory " + selector + " all treasure", source);
        helper.assertTrue(rejected == 0 && messages.get(0).contains("resource"),
                "неизвестный тип обязан отклоняться с перечнем допустимых строчных значений, ответ: " + messages
                        + ", результат " + rejected);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }
}
