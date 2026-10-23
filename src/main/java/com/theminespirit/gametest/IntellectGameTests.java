package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.ActivityAssignment;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.InvitationAnswer;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StrengthAction;
import com.theminespirit.core.intellect.StrengthVerdict;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
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
@SettlementSystems(coordinator = true)
public final class IntellectGameTests {
    private IntellectGameTests() {}

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

    private static void giveMineKit(SpiritEntity spirit) {
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));
        spirit.getInventory().setItem(2, new ItemStack(Items.BREAD, 3));
    }

    private static SpiritSnapshot snapshotOf(SpiritEntity spirit) {
        Vec3 pos = spirit.position();
        return new SpiritSnapshot(
                spirit.getUUID(),
                new Point(pos.x, pos.y, pos.z),
                1.0,
                SpiritRole.WORKER,
                CombatMode.DEFENSIVE,
                0,
                false,
                Optional.empty(),
                0,
                1.0);
    }

    @GameTest(template = "arena", batch = "outside_party", timeoutTicks = 200)
    public static void outside_OneMinerAtHomeWithMineNeed_NoMineTask(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        BlockCoord home = homeOf(helper, spirit);
        SettlementIntellect intellect = new SettlementIntellect();
        Point homePoint = new Point(home.x(), home.y(), home.z());
        intellect.tick(
                helper.getLevel(),
                List.of(spirit),
                List.of(snapshotOf(spirit)),
                Optional.of(homePoint),
                new WorkReservations(),
                100);

        helper.assertTrue(
                intellect.decision().orElseThrow().activities().assignments().stream()
                        .noneMatch(assignment -> assignment.activity() == ActivityId.MINE),
                "Один дух за границу не выходит: внешняя работа не назначается");
        helper.assertTrue(
                intellect.decision().orElseThrow().refusals().stream().anyMatch(reason -> reason.contains("MINE")),
                "Причина отказа видна в журнале решений, фактически: "
                        + intellect.decision().orElseThrow().refusals());
        helper.assertTrue(
                spirit.currentTaskScript()
                        .map(script -> !"mine_vein".equals(script))
                        .orElse(true),
                "Задача mine_vein одинокому шахтёру не выдаётся");
        spirit.discard();
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "outside_party", timeoutTicks = 200)
    public static void outside_TwoMinersAtHomeWithMineNeed_MineTaskStarted(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        SpiritEntity first = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        SpiritEntity second = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        BlockCoord home = homeOf(helper, first);
        homeOf(helper, second);
        SettlementIntellect intellect = new SettlementIntellect();
        Point homePoint = new Point(home.x(), home.y(), home.z());
        intellect.tick(
                helper.getLevel(),
                List.of(first, second),
                List.of(snapshotOf(first), snapshotOf(second)),
                Optional.of(homePoint),
                new WorkReservations(),
                100);

        helper.assertTrue(
                intellect.decision().orElseThrow().activities().assignments().stream()
                        .anyMatch(assignment -> assignment.activity() == ActivityId.MINE),
                "Двое духов внешнюю работу получают: группа от порога — организация");
        helper.assertTrue(
                List.of(first, second).stream()
                        .anyMatch(spirit -> spirit.currentPlan()
                                .map(plan -> plan instanceof ScriptCall call && "mine_vein".equals(call.script()))
                                .orElse(false)),
                "Хотя бы один шахтёр получает задачу mine_vein");
        first.discard();
        second.discard();
        TacticsScene.finish(helper, first);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "outside_party", timeoutTicks = 200)
    public static void outside_MineExpeditionWithoutKit_NotDispatched(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "NoKit");
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        homeOf(helper, spirits.get(0));
        homeOf(helper, spirits.get(1));
        SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
        BlockPos vein = helper.absolutePos(new BlockPos(8, 1, 5));
        Expedition planned = new Expedition(
                List.of(spirits.get(0).getUUID(), spirits.get(1).getUUID()),
                new Point(vein.getX(), vein.getY(), vein.getZ()),
                helper.getLevel().getGameTime());
        long openedAt = helper.getLevel().getGameTime();
        intellect.expeditions().update(helper.getLevel(), Optional.of(planned), spirits, openedAt);
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        openedAt + IntellectTuning.loadDefault().inviteTtlTicks() + 100);

        helper.assertTrue(
                intellect.expeditions().lastDispatch().isEmpty(),
                "Без набора команда в шахту не идёт: спуск начинается с проверки кирки, факелов и еды");
        helper.assertTrue(
                intellect
                        .expeditions()
                        .refusal()
                        .map(reason -> reason.contains("набор"))
                        .orElse(false),
                "Причина отказа — нехватка набора, фактически: "
                        + intellect.expeditions().refusal().orElse("нет"));
        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, spirits.get(0));
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "outside_party", timeoutTicks = 200)
    public static void outside_MineExpeditionWithKit_LeaderMinesPartnerFollows(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "KitMiner");
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        homeOf(helper, spirits.get(0));
        homeOf(helper, spirits.get(1));
        giveMineKit(spirits.get(0));
        giveMineKit(spirits.get(1));
        SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
        BlockPos vein = helper.absolutePos(new BlockPos(8, 1, 5));
        List<UUID> team = List.of(spirits.get(0).getUUID(), spirits.get(1).getUUID());
        Expedition planned = new Expedition(
                team,
                new Point(vein.getX(), vein.getY(), vein.getZ()),
                helper.getLevel().getGameTime());
        long openedAt = helper.getLevel().getGameTime();
        intellect.expeditions().update(helper.getLevel(), Optional.of(planned), spirits, openedAt);
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        openedAt + IntellectTuning.loadDefault().inviteTtlTicks() + 100);

        helper.assertTrue(
                intellect
                        .expeditions()
                        .lastDispatch()
                        .map(dispatch -> "mine_vein".equals(dispatch.script())
                                && new HashSet<>(dispatch.team()).equals(new HashSet<>(team)))
                        .orElse(false),
                "С набором команда идёт в шахту: диспетч mine_vein на ту же команду");
        SpiritEntity leader = spirits.stream()
                .filter(spirit -> spirit.getUUID().equals(team.get(0)))
                .findFirst()
                .orElseThrow();
        SpiritEntity partner = spirits.stream()
                .filter(spirit -> spirit.getUUID().equals(team.get(1)))
                .findFirst()
                .orElseThrow();
        helper.assertTrue(
                leader.currentPlan()
                        .map(plan -> plan instanceof SequenceNode sequence
                                && sequence.steps().stream()
                                        .anyMatch(step ->
                                                step instanceof ScriptCall call && "mine_vein".equals(call.script())))
                        .orElse(false),
                "Ведущий копает: у него в плане mine_vein, фактически "
                        + leader.currentPlan().map(Object::toString).orElse("нет"));
        helper.assertTrue(
                partner.currentPlan()
                        .map(plan -> plan instanceof ScriptCall call && "follow".equals(call.script()))
                        .orElse(false),
                "Напарник идёт рядом: он следует за ведущим, фактически "
                        + partner.currentPlan().map(Object::toString).orElse("нет"));
        helper.assertTrue(
                leader.currentPlan()
                        .map(plan -> plan instanceof SequenceNode sequence
                                && sequence.steps().stream()
                                        .anyMatch(step ->
                                                step instanceof ScriptCall call && "return_home".equals(call.script())))
                        .orElse(false),
                "После шахты ведущий возвращается на базу: план — шахта, затем возврат домой");
        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, spirits.get(0));
        helper.succeed();
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
                helper.assertTrue(
                        intellect.decideCalls() >= 1, "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
                first.clear();
                first.addAll(intellect.decision().orElseThrow().activities().assignments());
                helper.assertTrue(
                        first.size() == 3,
                        "Все три духа получили занятия: иначе интеллект слеп, а поселение простаивает");
                helper.assertTrue(
                        new HashSet<>(first.stream()
                                                .map(ActivityAssignment::spirit)
                                                .toList())
                                        .size()
                                == 3,
                        "У духа не больше одного занятия: иначе два приказа рвут задачу духа");
                Set<Object> jobs = new HashSet<>(
                        first.stream().map(ActivityAssignment::activity).toList());
                helper.assertTrue(
                        !jobs.contains(ActivityId.REST),
                        "Голодное поселение без угроз: все трое добывают еду, никто не стоит в отдыхе (свободный дух "
                                + "без задачи годен для работы): " + jobs);
                firstSince[0] = first.stream()
                        .filter(assignment -> assignment.spirit().equals(busy))
                        .mapToLong(ActivityAssignment::since)
                        .findFirst()
                        .orElse(-1);
                helper.assertTrue(firstSince[0] >= 0, "Занятый дух записан в план: иначе нечего проверять на мигание");
                seen[0] = true;
            }
            helper.assertTrue(
                    intellect.decideCalls() >= 2,
                    "Ждём второй пересчёт: гистерезис виден только между двумя решениями");
            List<ActivityAssignment> second =
                    intellect.decision().orElseThrow().activities().assignments();
            long busySince = second.stream()
                    .filter(assignment -> assignment.spirit().equals(busy))
                    .mapToLong(ActivityAssignment::since)
                    .findFirst()
                    .orElse(-2);
            helper.assertTrue(
                    busySince == firstSince[0],
                    "Занятие духа не меняется и срок тот же: иначе план мигает каждый пересчёт");
            helper.assertTrue(
                    spirits.get(0)
                            .currentTaskScript()
                            .map(script -> "wait".equals(script))
                            .orElse(false),
                    "Чужую задачу wait интеллект не отбирает: иначе сцены соседних тестов ломаются");
            helper.assertTrue(
                    IntellectTuning.loadDefault().demandIntervalTicks() == 100,
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
        ChestBlockEntity box = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chest));
        box.setItem(0, new ItemStack(Items.BREAD, 5));
        box.setItem(1, new ItemStack(Items.RAW_IRON, 4));
        box.setItem(2, new ItemStack(Items.IRON_SWORD, 1));

        helper.succeedWhen(() -> {
            SettlementIntellect intellect = intellectOf(helper, spirit);
            helper.assertTrue(
                    intellect.decision().isPresent(), "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
            SettlementView view = intellect.view().orElseThrow();
            helper.assertTrue(view.population() == 1, "Вид видит одного духа: иначе учёт на душу делит мимо");
            helper.assertTrue(
                    view.foodPerSoul() == 5.0,
                    "Сундук с 5 хлебом даёт 5 еды на душу: иначе спрос фермы не видит запас");
            helper.assertTrue(
                    view.ore() == 4, "Четыре необожжённых железных — руда 4: иначе шахта не видит добычу до плавки");
            helper.assertTrue(
                    view.iron() == 4, "Четыре необожжённых железных — железо 4: иначе переплавка не видит сырьё");
            helper.assertTrue(
                    view.weapons() == 1,
                    "Железный меч — одно оружие: иначе оценка сил шлёт безоружных за оружием, которого нет");
            helper.assertTrue(
                    IntellectTuning.loadDefault().demandIntervalTicks() == 100,
                    "Пересчёт спроса раз в 100 тиков: бюджет чтений считается от этого числа");
            helper.assertTrue(
                    StockReader.STOCK_READ_BUDGET == 4096,
                    "Бюджет чтений запасов 4096 на пересборку: шире — тик читает полмира, уже — не видит сундуки");
            helper.assertTrue(
                    intellect.stockReads() <= StockReader.STOCK_READ_BUDGET * intellect.decideCalls(),
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
            helper.assertTrue(
                    intellect.decision().isPresent(), "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
            if (!threatened[0]) {
                helper.assertTrue(
                        spirits.stream()
                                .allMatch(spirit -> spirit.currentTaskScript()
                                        .map(script -> !script.isBlank())
                                        .orElse(false)),
                        "Ждём свои задачи интеллекта: вердикт ARM нужен духу без оружия, занятому работой, а не пустому");
                husk[0] = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 5));
                husk[0].setTarget(spirits.get(2));
                threatened[0] = true;
            } else {
                List<StrengthVerdict> verdicts =
                        intellect.decision().orElseThrow().strengths();
                helper.assertTrue(
                        verdicts.size() == 3,
                        "Каждый из трёх духов при угрозе получил вердикт: иначе угроза не вытесняет работу");
                helper.assertTrue(
                        IntellectTuning.loadDefault().maxFighters() == 3,
                        "Предел бойцов 3: больше — поселение бросает работу ради одного зомби");
                helper.assertTrue(
                        verdicts.stream()
                                        .filter(verdict -> verdict.action() == StrengthAction.FIGHT)
                                        .count()
                                <= 2,
                        "Против одного слабого зомби в бой идут один-два духа, а не все: остальные работают");
                StrengthVerdict wounded = verdicts.stream()
                        .filter(verdict ->
                                verdict.spirit().equals(spirits.get(0).getUUID()))
                        .findFirst()
                        .orElseThrow();
                helper.assertTrue(
                        wounded.action() == StrengthAction.HEAL,
                        "Раненый дух с 4 HP лечится, а не рубит дальше: иначе оценка сил не видит низкого здоровья");
                Optional<StrengthVerdict> unarmed = verdicts.stream()
                        .filter(verdict ->
                                verdict.spirit().equals(spirits.get(1).getUUID())
                                        && verdict.action() == StrengthAction.ARM)
                        .findFirst();
                if (unarmed.isEmpty()) {
                    helper.assertTrue(
                            intellect.decideCalls() < 4,
                            "Ждём вердикт ARM: безоружным дух становится, когда возьмётся за ожидание");
                } else {
                    helper.assertTrue(
                            spirits.get(1)
                                    .currentTaskScript()
                                    .map(script -> "withdraw".equals(script) || "equip".equals(script))
                                    .orElse(false),
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
        homeOf(helper, spirits.get(0));
        homeOf(helper, spirits.get(1));
        homeOf(helper, spirits.get(2));
        giveMineKit(spirits.get(0));
        giveMineKit(spirits.get(1));
        SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
        BlockPos vein = helper.absolutePos(new BlockPos(8, 1, 5));
        Expedition planned = new Expedition(
                List.of(spirits.get(0).getUUID(), spirits.get(1).getUUID()),
                new Point(vein.getX(), vein.getY(), vein.getZ()),
                helper.getLevel().getGameTime());
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.of(planned),
                        spirits,
                        helper.getLevel().getGameTime());
        helper.assertTrue(
                intellect.expeditions().active().isPresent(), "Приглашение создано: иначе отвечать не на что");
        helper.assertTrue(
                heard.stream()
                        .anyMatch(message -> message.getString().contains("Идите без меня")
                                && message.getString().contains("Подождите")),
                "Приглашение в чате с кнопками: без кнопок игрок не понимает, как ответить");
        String id = intellect.expeditions().expeditionId().toString();
        helper.assertTrue(
                intellect
                        .expeditions()
                        .onAnswer(
                                id,
                                InvitationAnswer.GO_WITHOUT,
                                helper.getLevel().getGameTime()),
                "Ответ «идите без меня» принимает поход: иначе кнопка ведёт в никуда");
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        helper.getLevel().getGameTime());
        helper.assertTrue(
                intellect.expeditions().active().isEmpty(),
                "После ответа команда уходит: иначе приглашение висит после решения игрока");
        helper.assertTrue(
                intellect
                        .expeditions()
                        .lastDispatch()
                        .map(dispatch -> "mine_vein".equals(dispatch.script())
                                && new HashSet<>(dispatch.team()).equals(new HashSet<>(planned.team())))
                        .orElse(false),
                "Команда без игрока идёт сама добывать: диспетч mine_vein на ту же команду, иначе уход молча");
        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, spirits.get(0));
        helper.succeed();
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
                helper.assertTrue(
                        helper.getLevel().isLoaded(helper.absolutePos(new BlockPos(8, 1, 5))),
                        "Ждём загрузки чанка с жилой: поиск руды по незагруженному чанку падает сразу");
                homeOf(helper, spirits.get(0));
                homeOf(helper, spirits.get(1));
                homeOf(helper, spirits.get(2));
                spirits.get(0).startTask(mineVein());
                spirits.get(1).startTask(mineVein());
                settled[0] = true;
            } else {
                SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
                helper.assertTrue(
                        intellect.expeditions().active().isPresent(),
                        "Интеллект ещё не созвал команду: без нужды в руде похода нет");
                if (!moved[0]) {
                    spirits.get(0).cancelTask();
                    spirits.get(1).cancelTask();
                    miner.moveTo(near.x, near.y, near.z);
                    moved[0] = true;
                }
                helper.assertTrue(
                        spirits.stream()
                                .anyMatch(spirit -> spirit.currentTaskScript()
                                        .map(script -> "follow".equals(script))
                                        .orElse(false)),
                        "Подошедший игрок ведёт команду: согласился — идут за ним");
                spirits.forEach(SpiritEntity::discard);
                TacticsScene.finish(helper, spirits.get(0));
            }
        });
    }

    @GameTest(template = "arena", batch = "intellect_expedition", timeoutTicks = 400)
    public static void expedition_AnswerWait_TeamWaitsThenGoesAlone(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "Waiter");
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        homeOf(helper, spirits.get(0));
        homeOf(helper, spirits.get(1));
        homeOf(helper, spirits.get(2));
        giveMineKit(spirits.get(0));
        giveMineKit(spirits.get(1));
        SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
        BlockPos vein = helper.absolutePos(new BlockPos(8, 1, 5));
        Expedition planned = new Expedition(
                List.of(spirits.get(0).getUUID(), spirits.get(1).getUUID()),
                new Point(vein.getX(), vein.getY(), vein.getZ()),
                helper.getLevel().getGameTime());
        long openedAt = helper.getLevel().getGameTime();
        intellect.expeditions().update(helper.getLevel(), Optional.of(planned), spirits, openedAt);
        helper.assertTrue(
                intellect.expeditions().active().isPresent(), "Приглашение создано: иначе отвечать не на что");
        String id = intellect.expeditions().expeditionId().toString();
        helper.assertTrue(
                intellect.expeditions().onAnswer(id, InvitationAnswer.WAIT_MORE, openedAt),
                "Ответ «подождите» принимает поход: иначе кнопка ведёт в никуда");
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        openedAt + IntellectTuning.loadDefault().inviteTtlTicks() + 100);
        helper.assertTrue(
                intellect.expeditions().active().isPresent(),
                "После «подождите» поход ждёт сверх старого срока: иначе продление ничего не даёт");
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        openedAt
                                + IntellectTuning.loadDefault().inviteTtlTicks()
                                + IntellectTuning.loadDefault().inviteExtendTicks()
                                + 100);
        helper.assertTrue(
                intellect.expeditions().active().isEmpty(),
                "После продления команда уходит сама: иначе вопрос висит после срока");
        helper.assertTrue(
                intellect
                        .expeditions()
                        .lastDispatch()
                        .map(dispatch -> "mine_vein".equals(dispatch.script())
                                && new HashSet<>(dispatch.team()).equals(new HashSet<>(planned.team())))
                        .orElse(false),
                "После ожидания команда идёт добывать: диспетч mine_vein на ту же команду");
        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, spirits.get(0));
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "intellect_expedition", timeoutTicks = 400)
    public static void expedition_Silence_TeamGoesAlone(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        layIronVein(helper);
        GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "Silent");
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirits.add(spirit);
        }
        homeOf(helper, spirits.get(0));
        homeOf(helper, spirits.get(1));
        homeOf(helper, spirits.get(2));
        giveMineKit(spirits.get(0));
        giveMineKit(spirits.get(1));
        SettlementIntellect intellect = intellectOf(helper, spirits.get(0));
        BlockPos vein = helper.absolutePos(new BlockPos(8, 1, 5));
        Expedition planned = new Expedition(
                List.of(spirits.get(0).getUUID(), spirits.get(1).getUUID()),
                new Point(vein.getX(), vein.getY(), vein.getZ()),
                helper.getLevel().getGameTime());
        long openedAt = helper.getLevel().getGameTime();
        intellect.expeditions().update(helper.getLevel(), Optional.of(planned), spirits, openedAt);
        helper.assertTrue(
                intellect.expeditions().active().isPresent(), "Приглашение создано: иначе молчанию нечего истекать");
        intellect
                .expeditions()
                .update(
                        helper.getLevel(),
                        Optional.empty(),
                        spirits,
                        openedAt + IntellectTuning.loadDefault().inviteTtlTicks() + 100);
        helper.assertTrue(
                intellect.expeditions().active().isEmpty(),
                "Молчание три минуты — духи идут сами: иначе экспедиция встанет без ответа");
        helper.assertTrue(
                intellect
                        .expeditions()
                        .lastDispatch()
                        .map(dispatch -> "mine_vein".equals(dispatch.script())
                                && new HashSet<>(dispatch.team()).equals(new HashSet<>(planned.team())))
                        .orElse(false),
                "Молчащая команда идёт добывать: диспетч mine_vein на ту же команду");
        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, spirits.get(0));
        helper.succeed();
    }
}
