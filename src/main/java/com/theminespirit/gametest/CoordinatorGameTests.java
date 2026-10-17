package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.coordinator.SpiritSnapshotReader;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(coordinator = true)
public final class CoordinatorGameTests {
    private CoordinatorGameTests() {
    }

    private static WorkTarget tree(GameTestHelper helper, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        return new WorkTarget(WorkKind.TREE, new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workReserve_TwoSpiritsOneTarget_SecondRefusedUntilRelease(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity first = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity second = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(first, target),
                "Первая бронь цели встаёт: иначе распределение работ не защищает цель");
        helper.assertTrue(!WorkReserveHub.reserve(second, target),
                "Второй дух на ту же цель получает отказ: иначе двое рубят одно дерево");
        helper.assertTrue(WorkReserveHub.release(first, target), "Владелец снимает свою бронь");
        helper.assertTrue(WorkReserveHub.reserve(second, target),
                "После release цель свободна: работу забирает следующий дух");
        helper.assertTrue(WorkReserveHub.releaseAll(second) == 1,
                "releaseAll снимает бронь духа: выгрузка оставляет цель свободной");

        TacticsScene.finish(helper, first);
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workChoose_TargetUnderForeignReservation_NotTaken(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity seeker = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget taken = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, taken), "Владелец занял цель");
        helper.assertTrue(WorkReserveHub.choose(seeker, List.of(taken)).isEmpty(),
                "choose не берёт чужую бронь: занятая цель не возвращается");
        helper.assertTrue(WorkReserveHub.releaseAll(owner) == 1, "releaseAll снимает бронь владельца");
        helper.assertTrue(WorkReserveHub.choose(seeker, List.of(taken)).isPresent(),
                "После снятия брони цель снова свободна для выбора");

        TacticsScene.finish(helper, owner);
        seeker.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workCancelTask_ReservedTarget_ReleasesIt(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, target), "Дух занял цель перед задачей");
        owner.cancelTask();
        helper.assertTrue(WorkReserveHub.choose(owner, List.of(target)).isPresent(),
                "Отмена задачи освобождает цель: бронь не держится за снятым заданием");
        owner.cancelTask();
        helper.assertTrue(WorkReserveHub.releaseAll(owner) == 0,
                "Повторное снятие броней без них — не ошибка: выгрузка безопасна");

        TacticsScene.finish(helper, owner);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workSpiritRemoved_ReservedTarget_ReleasedForOther(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity other = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, target), "Дух занял цель");
        owner.discard();
        helper.assertTrue(WorkReserveHub.reserve(other, target),
                "Выгрузка духа освобождает его брони: цель не остаётся занятой навсегда");

        TacticsScene.finish(helper, other);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_lifecycle", timeoutTicks = 200)
    public static void coordinator_RegistryQuery_SameInstancePerSettlement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementKey home = SettlementKey.homed(new BlockCoord(100, 64, -40));
        SpiritCoordinator first = CoordinatorRegistry.get(level, home);

        helper.assertTrue(first == CoordinatorRegistry.get(level, home),
                "Два запроса на поселение отдают один координатор: иначе у поселения два плана обороны");
        helper.assertTrue(CoordinatorRegistry.get(level, SettlementKey.homed(new BlockCoord(-7, 63, 12))) != first,
                "Чужой дом — другой координатор: иначе приказы одного поселения командуют чужими духами");
        CoordinatorRegistry.remove(level, home);
        helper.assertTrue(first != CoordinatorRegistry.get(level, home),
                "После remove реестр выдаёт новый координатор: исчезнувшее поселение не держит старое состояние");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_snapshot", timeoutTicks = 100)
    public static void snapshotRead_AttackerJustBeyondThreatRadius_StillCountsAsThreat(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 22));
        husk.setPos(helper.absoluteVec(new Vec3(5.5, 1.0, 21.8)));
        husk.setTarget(spirit);

        SpiritSnapshot snapshot = SpiritSnapshotReader.read(spirit, helper.getLevel().getGameTime());

        helper.assertTrue(snapshot.attacked(),
                "Моб с целью на духа считается напавшим, даже стоя за радиусом угроз: иначе координатор не зовёт помощь");
        helper.assertTrue(snapshot.threatsNear() >= 1,
                "Атакованный дух видит хотя бы одну угрозу: иначе снимок противоречит сам себе и роняет тик");

        husk.discard();
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_defense", timeoutTicks = 400)
    public static void defense_FourSpiritsOneMob_PostsAroundHomeAndOneEngages(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        Point defended = new Point(home.x() + 0.5, home.y(), home.z() + 0.5);
        List<SpiritEntity> defenders = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(4, 1, 6),
                new BlockPos(6, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirit.setHome(home);
            spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
            defenders.add(spirit);
        }
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 5));
        husk.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TacticsScene.FOREVER, 4, false, false));

        boolean[] engaged = {false};
        boolean[] attacked = {false};
        boolean[] victimAttacked = {false};
        boolean[] removed = {false};
        boolean[] secondFront = {false};
        Set<Point> postsBefore = new HashSet<>();
        SpiritEntity[] victim = {null};
        Husk[] west = {null};
        helper.succeedWhen(() -> {
            if (!engaged[0] && (husk.isDeadOrDying() || husk.isRemoved() || defenders.stream()
                    .anyMatch(defender -> defender.getTarget() == husk))) {
                engaged[0] = true;
            }
            if (!attacked[0]) {
                List<Point> posts = postsOf(defenders);
                helper.assertTrue(CoordinatorTuning.DEFENSE_RADIUS == 16.0,
                        "Радиус обороны 16: посты секторов лежат на этом круге, шире — не сектора");
                helper.assertTrue(posts.size() == 4,
                        "У каждого из четырёх защитников свой пост: иначе оборона не распределяется по секторам");
                helper.assertTrue(new HashSet<>(posts).size() == 1,
                        "Одна угроза — один рубеж: взвешенная оборона (33) ставит всех на угрожаемый сектор, "
                                + "а не размазывает поровну");
                helper.assertTrue(posts.stream().allMatch(post -> post.distanceTo(defended)
                                <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                        "Посты в радиусе обороны от дома (+1.0 на округление блока): иначе защитник ушёл за радиус");
                victim[0] = defenders.get(0);
                Point post = posts.get(0);
                husk.setPos(new Vec3(post.x(), post.y(), post.z()));
                husk.setTarget(victim[0]);
                postsBefore.addAll(posts);
                attacked[0] = true;
            }
            if (!victimAttacked[0] && SpiritSnapshotReader
                    .read(victim[0], helper.getLevel().getGameTime()).attacked()) {
                victimAttacked[0] = true;
            }
            helper.assertTrue(victimAttacked[0],
                    "Жертва помечена атакованной в снимке: иначе координатор не видит нападение и не шлёт помощь");
            helper.assertTrue(engaged[0],
                    "Хотя бы один защитник вступает в бой: иначе помощь не пришла, а поселение не защищается");
            if (!removed[0]) {
                victim[0].discard();
                removed[0] = true;
            }
            List<SpiritEntity> rest = defenders.stream().filter(defender -> defender != victim[0]).toList();
            List<Point> posts = postsOf(rest);
            helper.assertTrue(posts.size() == 3,
                    "После снятия защитника у каждого из трёх оставшихся свой пост: иначе оборона не переживает выбытие");
            helper.assertTrue(posts.stream().allMatch(post -> post.distanceTo(defended)
                            <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                    "Оставшиеся держатся в радиусе обороны: иначе перераспределение разбросало посты");
            if (!secondFront[0]) {
                west[0] = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(1, 1, 5));
                west[0].addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TacticsScene.FOREVER, 4, false, false));
                secondFront[0] = true;
            }
            helper.assertTrue(secondFront[0] && west[0].isAlive(),
                    "Второй рубеж открыт на западе: ждём перераспределение за новой угрозой");
            List<Point> shifted = postsOf(rest);
            helper.assertTrue(shifted.size() == 3,
                    "Оборона держит три поста на два направления: иначе второй фронт оголяет первый");
            helper.assertTrue(shifted.stream().allMatch(post -> post.distanceTo(defended)
                            <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                    "Посты двух фронтов в радиусе обороны: иначе защитники разбрелись");
            helper.assertTrue(shifted.stream()
                            .anyMatch(post -> postsBefore.stream().noneMatch(old -> old.distanceTo(post) < 1.5)),
                    "Посты перераспределились за новой угрозой: иначе координатор держит мёртвые сектора");
            husk.discard();
            west[0].discard();
            rest.forEach(SpiritEntity::discard);
            TacticsScene.finish(helper, victim[0]);
        });
    }

    @GameTest(template = "arena", batch = "coordinator_assist", timeoutTicks = 400)
    public static void assist_OneAttacked_NearestFreeDefenderProtectsVictim(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        Point defended = new Point(home.x() + 0.5, home.y(), home.z() + 0.5);
        List<SpiritEntity> defenders = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirit.setHome(home);
            defenders.add(spirit);
        }
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 5));

        boolean[] attacked = {false};
        SpiritEntity[] victim = {null};
        Map<SpiritEntity, Point> postsBefore = new HashMap<>();
        helper.succeedWhen(() -> {
            if (!attacked[0]) {
                List<Point> posts = postsOf(defenders);
                helper.assertTrue(CoordinatorTuning.DEFENSE_RADIUS == 16.0,
                        "Радиус обороны 16: посты секторов лежат на этом круге, шире — не сектора");
                helper.assertTrue(posts.size() == 3,
                        "У каждого из трёх защитников свой пост: иначе некому идти на помощь");
                helper.assertTrue(posts.stream().allMatch(post -> post.distanceTo(defended)
                                <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                        "Посты в радиусе обороны от дома (+1.0 на округление блока): иначе защитник ушёл за радиус");
                helper.assertTrue(new HashSet<>(posts).size() == 1,
                        "Одна угроза — один рубеж: взвешенная оборона (33) ставит всех на угрожаемый сектор, "
                                + "а не размазывает поровну");
                victim[0] = defenders.get(0);
                Point post = posts.get(0);
                husk.setPos(new Vec3(post.x(), post.y(), post.z()));
                husk.setTarget(victim[0]);
                for (int i = 0; i < defenders.size(); i++) {
                    postsBefore.put(defenders.get(i), posts.get(i));
                }
                attacked[0] = true;
            }
            List<SpiritEntity> helpers = new ArrayList<>();
            for (SpiritEntity defender : defenders) {
                if (defender != victim[0]
                        && defender.tactics().combatMode() == CombatMode.PROTECT) {
                    helpers.add(defender);
                }
            }
            helper.assertTrue(!helpers.isEmpty(),
                    "Ближайший свободный защитник получает приказ на помощь: иначе атакованный остаётся один");
            helper.assertTrue(CoordinatorTuning.MAX_ASSIST == 2,
                    "Предел помощи 2: больше — сектора пустеют, меньше — защитники гибнут");
            helper.assertTrue(helpers.size() <= CoordinatorTuning.MAX_ASSIST,
                    "Помощников не больше предела: иначе сектора пустеют, а поселение остаётся без обороны");
            for (SpiritEntity helperSpirit : helpers) {
                helper.assertTrue(helperSpirit.tactics().snapshot().ward()
                                .map(ward -> ward.equals(victim[0].getUUID().toString())).orElse(false),
                        "Помощник защищает атакованного: иначе приказ помощи ведёт не к тому духу");
            }
            for (SpiritEntity defender : defenders) {
                if (defender == victim[0] || !helpers.contains(defender)) {
                    helper.assertTrue(postsOf(List.of(defender)).stream()
                                    .anyMatch(post -> post.equals(postsBefore.get(defender))),
                            "Посты остальных сохранены: помощь не срывает чужие сектора");
                }
            }
            husk.discard();
            defenders.forEach(SpiritEntity::discard);
            TacticsScene.finish(helper, victim[0]);
        });
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workPull_ThreeSpiritsThreeTrees_EachReservesOwnTree(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(2, 1, 4), new BlockPos(3, 1, 4), new BlockPos(2, 1, 6))) {
            spirits.add(TacticsScene.spirit(helper, at));
        }
        List<WorkTarget> trees = List.of(tree(helper, new BlockPos(2, 1, 5)), tree(helper, new BlockPos(5, 1, 8)),
                tree(helper, new BlockPos(8, 1, 5)));

        List<WorkTarget> taken = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            Optional<WorkTarget> pick = WorkReserveHub.choose(spirit, trees);
            helper.assertTrue(pick.isPresent(),
                    "Свободный дух находит дерево: иначе pull не даёт работу, хотя цели свободны");
            helper.assertTrue(WorkReserveHub.reserve(spirit, pick.get()),
                    "Выбор закрепляется бронью с первого раза: распределитель не предлагает занятое");
            taken.add(pick.get());
        }
        helper.assertTrue(new HashSet<>(taken).size() == 3,
                "Двух духов на одном дереве нет: иначе брони не защищают цели друг от друга");
        helper.assertTrue(!WorkReserveHub.reserve(spirits.get(1), taken.get(0)),
                "Чужая бронь не перезанимается: иначе двое рубят одно дерево");

        helper.assertTrue(WorkReserveHub.releaseAll(spirits.get(0)) == 1, "Снятие брони освобождает дерево");
        SpiritEntity other = TacticsScene.spirit(helper, new BlockPos(5, 1, 2));
        helper.assertTrue(WorkReserveHub.reserve(other, taken.get(0)),
                "После release дерево забирает другой дух: цель не остаётся занятой навсегда");
        other.discard();

        for (SpiritEntity spirit : spirits) {
            WorkReserveHub.releaseAll(spirit);
        }
        WorkTarget single = tree(helper, new BlockPos(5, 1, 5));
        for (SpiritEntity spirit : spirits) {
            helper.assertTrue(WorkReserveHub.choose(spirit, List.of(single)).map(pick -> pick.equals(single))
                            .orElse(false),
                    "При одном дереве каждый дух получает работу: иначе двое пустуют, хотя толпа не запрещена");
        }
        helper.assertTrue(WorkReserveHub.reserve(spirits.get(0), single),
                "Первый дух закрепляет единственное дерево бронью");
        helper.assertTrue(!WorkReserveHub.reserve(spirits.get(1), single),
                "Второму то же дерево бронью не отдаётся: мягкое распределение — не двойная бронь");

        spirits.forEach(SpiritEntity::discard);
        TacticsScene.finish(helper, other);
        helper.succeed();
    }

    private static List<Point> postsOf(List<SpiritEntity> spirits) {
        List<Point> posts = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            Optional<OrdersBuilder.Zone> zone = spirit.tactics().snapshot().zone();
            zone.map(OrdersBuilder.Zone::center).ifPresent(posts::add);
        }
        return posts;
    }

    @GameTest(template = "arena", batch = "coordinator_directive_owner", timeoutTicks = 100)
    public static void directive_TestDirective_CoordinatorClearsOnlyItsOwn(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 5));
        husk.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TacticsScene.FOREVER, 4, false, false));
        SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(),
                SettlementGroups.keyOf(spirit.home(), spirit.getUUID()));
        long now = helper.getLevel().getGameTime();
        coordinator.tick(List.of(spirit), now);
        helper.assertTrue(spirit.tactics().activeDirective().isPresent(),
                "Координатор активен и выдал духу приказ обороны: иначе проверять снятие нечего; "
                        + "постов " + coordinator.defense().lastPlan().posts().size());
        spirit.tactics().applyDirective(new CombatDirective(CombatMode.PASSIVE, Optional.empty(),
                Optional.empty(), Optional.empty()));
        coordinator.tick(List.of(), now + 1);
        husk.discard();

        helper.assertTrue(spirit.tactics().activeDirective()
                        .map(directive -> directive.mode() == CombatMode.PASSIVE).orElse(false),
                "Директива теста остаётся после уборки приказа координатора: иначе повторяется флак 17.10, "
                        + "где терялись PASSIVE и forced HOME; сейчас "
                        + spirit.tactics().activeDirective().map(Object::toString).orElse("нет"));

        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_lifecycle", timeoutTicks = 200)
    public static void coordinator_ServerTicks_BuildsSnapshotWithoutManualTick(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        helper.succeedWhen(() -> {
            SettlementKey key = SettlementGroups.keyOf(Optional.empty(), spirit.getUUID());
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel(), key).snapshot().tick() > 0,
                    "Слушатель координатора зарегистрирован: снимок поселения строится сам, без ручного tick()");
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel(), key).snapshot().spirits().stream()
                            .anyMatch(snapshot -> snapshot.id().equals(spirit.getUUID())),
                    "Снимок поселения видит своего духа: иначе координатор слеп");
            TacticsScene.finish(helper, spirit);
        });
    }
}
