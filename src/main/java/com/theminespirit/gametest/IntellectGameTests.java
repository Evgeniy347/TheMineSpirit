package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.ActivityAssignment;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.InvitationAnswer;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StrengthAction;
import com.theminespirit.core.intellect.StrengthVerdict;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import com.theminespirit.neoforge.intellect.StockReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntellectGameTests {
    private IntellectGameTests() {
    }

    private static BlockCoord homeOf(GameTestHelper helper, SpiritEntity spirit) {
        BlockPos absolute = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        spirit.setHome(home);
        return home;
    }

    private static SettlementIntellect intellectOf(GameTestHelper helper, SpiritEntity spirit) {
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
        SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
        return coordinator.intellect();
    }

    private static void layIronVein(GameTestHelper helper) {
        for (int x = 8; x <= 9; x++) {
            for (int z = 3; z <= 6; z++) {
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(x, y, z, Blocks.IRON_ORE);
                }
            }
        }
    }

    private static ScriptCall mineVein() {
        return new ScriptCall("mine_vein", Map.of("ore", "any", "count", 8, "radius", 16));
    }

    @GameTest(template = "arena", batch = "intellect_activities", timeoutTicks = 400)
    public static void activities_ThreeSpiritsStarving_DifferentJobsAndNoFlicker(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            homeOf(helper, spirit);
            spirits.add(spirit);
        }
        spirits.get(0).startTask(new ScriptCall("wait", Map.of("ticks", 6000)));
        spirits.get(1).startTask(new ScriptCall("wait", Map.of("ticks", 6000)));
        UUID busy = spirits.get(0).getUUID();
        boolean[] seen = {false};
        List<ActivityAssignment> first = new ArrayList<>();
        long[] firstSince = {-1};

        helper.succeedWhen(() -> {
            SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
            if (!seen[0]) {
                helper.assertTrue(intellect.decideCalls() >= 1,
                        "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
                first.clear();
                first.addAll(intellect.decision().orElseThrow().activities().assignments());
                helper.assertTrue(first.size() == 3,
                        "Все три духа получили занятия: иначе интеллект слеп, а поселение простаивает");
                helper.assertTrue(new HashSet<>(first.stream().map(ActivityAssignment::spirit).toList())
                                .size() == 3,
                        "У духа не больше одного занятия: иначе два приказа рвут задачу духа");
                Set<Object> jobs = new HashSet<>(first.stream().map(ActivityAssignment::activity).toList());
                helper.assertTrue(jobs.size() >= 2,
                        "Занятия разные (двое в поле, третий отдыхает): иначе толпа стоит на одном");
                firstSince[0] = first.stream().filter(assignment -> assignment.spirit().equals(busy))
                        .mapToLong(ActivityAssignment::since).findFirst().orElse(-1);
                helper.assertTrue(firstSince[0] >= 0,
                        "Занятый дух записан в план: иначе нечего проверять на мигание");
                seen[0] = true;
            }
            helper.assertTrue(intellect.decideCalls() >= 2,
                    "Ждём второй пересчёт: гистерезис виден только между двумя решениями");
            List<ActivityAssignment> second = intellect.decision().orElseThrow().activities().assignments();
            long busySince = second.stream().filter(assignment -> assignment.spirit().equals(busy))
                    .mapToLong(ActivityAssignment::since).findFirst().orElse(-2);
            helper.assertTrue(busySince == firstSince[0],
                    "Занятие духа не меняется и срок тот же: иначе план мигает каждый пересчёт");
            helper.assertTrue(spirits.get(0).currentTaskScript().map(script -> script.equals("wait"))
                            .orElse(false),
                    "Чужую задачу wait интеллект не отбирает: иначе сцены соседних тестов ломаются");
            helper.assertTrue(IntellectTuning.DEMAND_INTERVAL_TICKS == 100,
                    "Пересчёт спроса раз в 100 тиков: чаще — лишние чтения запасов, реже — поздно видит нужду");
            spirits.forEach(SpiritEntity::discard);
            TacticsScene.finish(helper, spirits.get(0));
        });
    }

    @GameTest(template = "arena", batch = "intellect_settlement", timeoutTicks = 400)
    public static void stocks_ChestWithBreadAndIron_ViewSeesThem(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        homeOf(helper, spirit);
        BlockPos chest = new BlockPos(6, 1, 6);
        helper.setBlock(chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST);
        ChestBlockEntity box = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(chest));
        box.setItem(0, new ItemStack(Items.BREAD, 5));
        box.setItem(1, new ItemStack(Items.RAW_IRON, 4));
        box.setItem(2, new ItemStack(Items.IRON_SWORD, 1));

        helper.succeedWhen(() -> {
            SettlementIntellect intellect = intellectOf(helper, spirit);
            helper.assertTrue(intellect.decision().isPresent(),
                    "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
            SettlementView view = intellect.view().orElseThrow();
            helper.assertTrue(view.population() == 1,
                    "Вид видит одного духа: иначе учёт на душу делит мимо");
            helper.assertTrue(view.foodPerSoul() == 5.0,
                    "Сундук с 5 хлебом даёт 5 еды на душу: иначе спрос фермы не видит запас");
            helper.assertTrue(view.ore() == 4,
                    "Четыре необожжённых железных — руда 4: иначе шахта не видит добычу до плавки");
            helper.assertTrue(view.iron() == 4,
                    "Четыре необожжённых железных — железо 4: иначе переплавка не видит сырьё");
            helper.assertTrue(view.weapons() == 1,
                    "Железный меч — одно оружие: иначе оценка сил шлёт безоружных за оружием, которого нет");
            helper.assertTrue(IntellectTuning.DEMAND_INTERVAL_TICKS == 100,
                    "Пересчёт спроса раз в 100 тиков: бюджет чтений считается от этого числа");
            helper.assertTrue(StockReader.STOCK_READ_BUDGET == 4096,
                    "Бюджет чтений запасов 4096 на пересборку: шире — тик читает полмира, уже — не видит сундуки");
            helper.assertTrue(intellect.stockReads() <= StockReader.STOCK_READ_BUDGET
                            * intellect.decideCalls(),
                    "Чтений запасов не больше бюджета на число пересборок: иначе тик стоит дорого");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "intellect_strength", timeoutTicks = 400)
    public static void strength_OneHuskThreeSpirits_WoundedHealsUnarmedArms(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            homeOf(helper, spirit);
            spirits.add(spirit);
        }
        spirits.get(0).setHealth(4.0F);
        BlockPos chest = new BlockPos(6, 1, 6);
        helper.setBlock(chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST);
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chest)))
                .setItem(0, new ItemStack(Items.IRON_SWORD, 1));
        boolean[] threatened = {false};
        Husk[] husk = {null};

        helper.succeedWhen(() -> {
            SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
            helper.assertTrue(intellect.decision().isPresent(),
                    "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
            if (!threatened[0]) {
                helper.assertTrue(spirits.stream().allMatch(spirit -> spirit.currentTaskScript()
                                .map(script -> script.equals("wait")).orElse(false)),
                        "Ждём свои задачи ожидания: вердикт ARM нужен духу без оружия, а не пустому");
                husk[0] = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 5));
                husk[0].setTarget(spirits.get(2));
                threatened[0] = true;
            } else {
                List<StrengthVerdict> verdicts = intellect.decision().orElseThrow().strengths();
                helper.assertTrue(verdicts.size() == 3,
                        "Каждый из трёх духов при угрозе получил вердикт: иначе угроза не вытесняет работу");
                helper.assertTrue(IntellectTuning.MAX_FIGHTERS == 3,
                        "Предел бойцов 3: больше — поселение бросает работу ради одного зомби");
                helper.assertTrue(verdicts.stream()
                                .filter(verdict -> verdict.action() == StrengthAction.FIGHT).count() <= 2,
                        "Против одного слабого зомби в бой идут один-два духа, а не все: остальные работают");
                StrengthVerdict wounded = verdicts.stream()
                        .filter(verdict -> verdict.spirit().equals(spirits.get(0).getUUID())).findFirst()
                        .orElseThrow();
                helper.assertTrue(wounded.action() == StrengthAction.HEAL,
                        "Раненый дух с 4 HP лечится, а не рубит дальше: иначе оценка сил не видит низкого здоровья");
                Optional<StrengthVerdict> unarmed = verdicts.stream()
                        .filter(verdict -> verdict.spirit().equals(spirits.get(1).getUUID())
                                && verdict.action() == StrengthAction.ARM)
                        .findFirst();
                if (unarmed.isEmpty()) {
                    helper.assertTrue(intellect.decideCalls() < 4,
                            "Ждём вердикт ARM: безоружным дух становится, когда возьмётся за ожидание");
                } else {
                    helper.assertTrue(spirits.get(1).currentTaskScript().map(script ->
                                    script.equals("withdraw") || script.equals("equip")).orElse(false),
                            "Приказ «за оружием» превращается в задачу withdraw/equip: иначе вердикт остался на бумаге");
                    husk[0].discard();
                    spirits.forEach(SpiritEntity::discard);
                    TacticsScene.finish(helper, spirits.get(0));
                }
            }
        });
    }

    @GameTest(template = "arena", batch = "intellect_expedition", timeoutTicks = 400)
    public static void expedition_NoOreAndTwoMiners_InviteOpensTeamGoesAlone(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        List<Component> heard = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "Miner", heard);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        spirits.get(0).getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirits.get(1).getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        boolean[] settled = {false};
        boolean[] answered = {false};
        List<UUID> team = new ArrayList<>();

        helper.succeedWhen(() -> {
            if (!settled[0]) {
                helper.assertTrue(helper.getLevel().isLoaded(helper.absolutePos(new BlockPos(8, 1, 5))),
                        "Ждём загрузки чанка с жилой: поиск руды по незагруженному чанку падает сразу");
                homeOf(helper, spirits.get(0));
                homeOf(helper, spirits.get(1));
                homeOf(helper, spirits.get(2));
                spirits.get(0).startTask(mineVein());
                spirits.get(1).startTask(mineVein());
                settled[0] = true;
            } else {
                SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
                if (!answered[0]) {
                helper.assertTrue(intellect.expeditions().active().isPresent(),
                        "Без руды и оружия собирается команда в шахту: иначе поселение сидит без железа");
                int size = intellect.expeditions().active().orElseThrow().team().size();
                helper.assertTrue(size >= IntellectTuning.TEAM_MIN && size <= IntellectTuning.TEAM_MAX,
                        "Команда 2–3 духа: в одиночку в шахту не ходят, толпой — топчут друг друга");
                helper.assertTrue(IntellectTuning.TEAM_MIN == 2 && IntellectTuning.TEAM_MAX == 3,
                        "Команда строго 2–3: число проверяется буквой production-константы, а не верой");
                helper.assertTrue(heard.stream().anyMatch(message ->
                                message.getString().contains("Идите без меня")
                                        && message.getString().contains("Подождите")),
                        "Приглашение в чате с кнопками: без кнопок игрок не понимает, как ответить");
                team.clear();
                team.addAll(intellect.expeditions().active().orElseThrow().team());
                String id = intellect.expeditions().expeditionId().toString();
                helper.assertTrue(intellect.expeditions().onAnswer(id, InvitationAnswer.GO_WITHOUT,
                                helper.getLevel().getGameTime()),
                        "Ответ «идите без меня» принимает поход: иначе кнопка ведёт в никуда");
                answered[0] = true;
            }
            helper.assertTrue(intellect.expeditions().active().isEmpty(),
                    "После ответа команда уходит: иначе приглашение висит после решения игрока");
            helper.assertTrue(intellect.expeditions().lastDispatch()
                            .map(dispatch -> dispatch.script().equals("mine_vein")
                                    && new HashSet<>(dispatch.team()).equals(new HashSet<>(team)))
                            .orElse(false),
                    "Команда без игрока идёт сама добывать: диспетч mine_vein на ту же команду, иначе уход молча");
                spirits.forEach(SpiritEntity::discard);
                TacticsScene.finish(helper, spirits.get(0));
            }
        });
    }

    @GameTest(template = "arena", batch = "intellect_expedition", timeoutTicks = 400)
    public static void expedition_PlayerComesClose_TeamFollowsHim(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        ServerPlayer miner = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "Miner");
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        spirits.get(0).getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirits.get(1).getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        Vec3 near = helper.absoluteVec(new Vec3(5.5, 1.0, 5.5));
        boolean[] settled = {false};
        boolean[] moved = {false};

        helper.succeedWhen(() -> {
            if (!settled[0]) {
                helper.assertTrue(helper.getLevel().isLoaded(helper.absolutePos(new BlockPos(8, 1, 5))),
                        "Ждём загрузки чанка с жилой: поиск руды по незагруженному чанку падает сразу");
                homeOf(helper, spirits.get(0));
                homeOf(helper, spirits.get(1));
                homeOf(helper, spirits.get(2));
                spirits.get(0).startTask(mineVein());
                spirits.get(1).startTask(mineVein());
                settled[0] = true;
            } else {
                SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
                helper.assertTrue(intellect.expeditions().active().isPresent(),
                        "Интеллект ещё не созвал команду: без нужды в руде похода нет");
                if (!moved[0]) {
                    spirits.get(0).cancelTask();
                    spirits.get(1).cancelTask();
                    miner.moveTo(near.x, near.y, near.z);
                    moved[0] = true;
                }
                helper.assertTrue(spirits.stream().anyMatch(spirit -> spirit.currentTaskScript()
                                .map(script -> script.equals("follow")).orElse(false)),
                        "Подошедший игрок ведёт команду: согласился — идут за ним");
                spirits.forEach(SpiritEntity::discard);
                TacticsScene.finish(helper, spirits.get(0));
            }
        });
    }
}
