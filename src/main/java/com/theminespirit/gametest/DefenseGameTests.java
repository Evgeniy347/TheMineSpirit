package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.defense.CombatStance;
import com.theminespirit.core.intellect.defense.DefenderOrder;
import com.theminespirit.core.intellect.defense.DefensePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.policy.PolicyVerdict;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.defense.BlockerProbe;
import com.theminespirit.neoforge.defense.DefenseService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefenseGameTests {
    private static final float SWELL_CAP = 0.5F;

    private DefenseGameTests() {}

    private static BlockCoord homeOf(GameTestHelper helper, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static SpiritEntity swordsman(GameTestHelper helper, BlockPos at, BlockCoord home, boolean armored) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.setHome(home);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        if (armored) {
            TacticsScene.wearIronArmor(spirit);
        }
        return spirit;
    }

    private static SpiritCoordinator coordinatorOf(GameTestHelper helper, SpiritEntity spirit) {
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
        return CoordinatorRegistry.get(helper.getLevel(), key);
    }

    private static <T extends LivingEntity> T unkillable(T mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TacticsScene.FOREVER, 4, false, false));
        return mob;
    }

    private static Optional<DefenderOrder> orderOf(DefensePlan plan, SpiritEntity spirit) {
        return plan.orders().stream()
                .filter(order -> order.defender().equals(spirit.getUUID()))
                .findFirst();
    }

    private static String taskFacts(SpiritEntity... spirits) {
        StringBuilder facts = new StringBuilder();
        for (SpiritEntity spirit : spirits) {
            if (facts.length() > 0) {
                facts.append("; ");
            }
            facts.append(spirit.displayName())
                    .append(" задача=")
                    .append(spirit.taskStatus().map(RunStatus::name).orElse("нет"))
                    .append("/")
                    .append(spirit.currentTaskScript().orElse("?"));
        }
        return facts.toString();
    }

    private static boolean planCovers(DefensePlan plan, SpiritEntity... spirits) {
        for (SpiritEntity spirit : spirits) {
            if (orderOf(plan, spirit).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void cover_ZombieBetweenSpiritAndSkeleton_SpiritFightsFromBehindZombie(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity archer = TacticsScene.spirit(helper, new BlockPos(7, 1, 6));
        archer.setHome(home);
        archer.getInventory().addItem(new ItemStack(Items.BOW));
        archer.getInventory().addItem(new ItemStack(Items.ARROW, 64));
        SpiritEntity second = swordsman(helper, new BlockPos(3, 1, 4), home, true);
        Zombie zombie = TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 5));
        Skeleton skeleton = TacticsScene.watcher(helper, EntityType.SKELETON, new BlockPos(3, 1, 8));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        float zombieFull = zombie.getMaxHealth();
        float spiritFull = second.getMaxHealth();
        boolean[] coverSeen = {false};
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] % 15 == 0 && skeleton.isAlive() && !second.isRemoved()) {
                skeleton.performRangedAttack(second, 1.0F);
            }
        });

        helper.succeedWhen(() -> {
            DefensePlan plan = coordinatorOf(helper, second).defense().lastPlan();
            helper.assertTrue(
                    planCovers(plan, archer, second),
                    "План обороны накрывает обоих защитников: иначе решение читается по частичному снимку, "
                            + "приказов " + plan.orders().size() + ", постов "
                            + plan.posts().size());
            Optional<DefenderOrder> archerOrder = orderOf(plan, archer);
            Optional<DefenderOrder> order = orderOf(plan, second);
            helper.assertTrue(
                    archerOrder.isPresent()
                            && archerOrder.get().target().isPresent()
                            && archerOrder
                                    .get()
                                    .target()
                                    .get()
                                    .id()
                                    .equals(skeleton.getUUID().toString()),
                    "Лучник держит скелета: иначе скелет ничей и сцена не про приём «зомби между»");
            if (order.isPresent()
                    && order.get().target().isPresent()
                    && order.get().target().get().id().equals(zombie.getUUID().toString())
                    && order.get().stance() == CombatStance.COVER_ENGAGE
                    && order.get().cover().isPresent()) {
                coverSeen[0] = true;
            }
            helper.assertTrue(
                    coverSeen[0],
                    "Мечник бьёт зомби из-за укрытия: зомби между ним и скелетом, иначе приём «зомби между» не работает");
            helper.assertTrue(
                    zombie.isRemoved() || zombie.getHealth() < zombieFull,
                    "Зомби получает урон: дух бьёт его мечом, а стрелы скелета вязнут в нём, а не летят мимо");
            helper.assertTrue(
                    second.getHealth() == spiritFull,
                    "Дух не получает стрел: укрытие держит линию «дух–скелет», иначе прикрытие фиктивно");
            helper.assertTrue(
                    archer.isAlive() && second.isAlive(), "Оба защитника живы: оборона не разменивает духов на зомби");
            zombie.discard();
            skeleton.discard();
            archer.discard();
            TacticsScene.finish(helper, second);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void creeper_StrikeAndRetreat_ArmoredEngagesNakedHolds(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity armored = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        armored.setHome(home);
        armored.getInventory().addItem(new ItemStack(Items.DIAMOND_SWORD));
        TacticsScene.wearIronArmor(armored);
        SpiritEntity naked = swordsman(helper, new BlockPos(6, 1, 4), home, false);
        Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(4, 1, 5));
        creeper.setPersistenceRequired();
        creeper.targetSelector.removeAllGoals(goal -> true);
        creeper.setTarget(armored);
        TacticsScene.slowDown(creeper, 0.04);
        boolean[] swelled = {false};
        boolean[] retreated = {false};
        boolean[] approachedAgain = {false};
        boolean[] nakedHolds = {false};
        long[] firstSwell = {-1};
        long[] firstReflex = {-1};
        String[] reflexText = {null};
        long[] nakedReflex = {-1};
        String[] nakedReflexText = {null};
        long[] creeperGone = {-1};
        String[] creeperTarget = {"?"};
        float[] maxSwell = {0.0F};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (creeper.isAlive()) {
                holdCreeperOnArmored(creeper, armored, naked, maxSwell, creeperTarget);
            }
            if (firstSwell[0] < 0 && creeper.isAlive() && creeper.getSwelling(1.0F) > 0.0F) {
                firstSwell[0] = tick;
            }
            noteFirstReflex(tick, armored, firstReflex, reflexText);
            noteFirstReflex(tick, naked, nakedReflex, nakedReflexText);
            if (creeperGone[0] < 0 && !creeper.isAlive()) {
                creeperGone[0] = tick;
            }
        });

        helper.succeedWhen(() -> {
            String duel = "тик " + helper.getTick() + ", свелл@" + firstSwell[0] + " макс " + maxSwell[0]
                    + ", рефлекс брони@" + firstReflex[0] + " (" + reflexText[0] + "), рефлекс без брони@"
                    + nakedReflex[0] + " (" + nakedReflexText[0] + "), крипер целит " + creeperTarget[0]
                    + ", крипера нет@" + creeperGone[0] + ", дистанции " + armored.distanceTo(creeper) + "/"
                    + naked.distanceTo(creeper) + ", " + taskFacts(armored, naked);
            helper.assertTrue(
                    creeper.equals(armored.getTarget()),
                    "Мечник в железной броне идёт на крипера первым: иначе «ударь и беги» делает слабый и " + "гибнет; "
                            + duel);
            if (nakedOrderedOffCreeper(helper, armored, naked, creeper)) {
                nakedHolds[0] = true;
            }
            helper.assertTrue(
                    nakedHolds[0],
                    "Мечник без брони на крипера не идёт: иначе приказ подвёл слабого первым вместо "
                            + "бронированного; " + taskFacts(armored, naked));
            assertStrikeAndRetreatCycle(helper, armored, creeper, duel, swelled, retreated, approachedAgain);
            helper.assertTrue(
                    armored.isAlive() && naked.isAlive(),
                    "Оба мечника пережили крипера: иначе дух стоял вплотную и поймал взрыв");
            creeper.discard();
            armored.discard();
            TacticsScene.finish(helper, naked);
        });
    }

    private static void holdCreeperOnArmored(
            Creeper creeper, SpiritEntity armored, SpiritEntity naked, float[] maxSwell, String[] creeperTarget) {
        creeper.setTarget(armored);
        if (creeper.getSwelling(1.0F) >= SWELL_CAP) {
            creeper.setSwellDir(-1);
        }
        maxSwell[0] = Math.max(maxSwell[0], creeper.getSwelling(1.0F));
        creeperTarget[0] = armored.equals(creeper.getTarget())
                ? "броня"
                : naked.equals(creeper.getTarget()) ? "без брони" : String.valueOf(creeper.getTarget());
    }

    private static void noteFirstReflex(long tick, SpiritEntity spirit, long[] firstTick, String[] text) {
        if (firstTick[0] < 0 && spirit.policies().verdict() instanceof PolicyVerdict.Reflex reflex) {
            firstTick[0] = tick;
            text[0] = reflex.policy() + ": " + reflex.reason();
        }
    }

    private static boolean nakedOrderedOffCreeper(
            GameTestHelper helper, SpiritEntity armored, SpiritEntity naked, Creeper creeper) {
        DefensePlan duelPlan = coordinatorOf(helper, armored).defense().lastPlan();
        return orderOf(duelPlan, naked)
                .map(order -> order.target().isEmpty()
                        || !order.target().get().id().equals(creeper.getUUID().toString()))
                .orElse(false);
    }

    private static void assertStrikeAndRetreatCycle(
            GameTestHelper helper,
            SpiritEntity armored,
            Creeper creeper,
            String duel,
            boolean[] swelled,
            boolean[] retreated,
            boolean[] approachedAgain) {
        double distance = armored.distanceTo(creeper);
        if (creeper.getSwelling(1.0F) > 0.0F) {
            swelled[0] = true;
        }
        if (swelled[0] && distance >= 7.0) {
            retreated[0] = true;
        }
        if (retreated[0] && distance < 6.5) {
            approachedAgain[0] = true;
        }
        helper.assertTrue(
                swelled[0],
                "Крипер набухает рядом с духом в броне: иначе сближения не было и проверять нечего; " + duel);
        helper.assertTrue(
                retreated[0],
                "При набухании дух отходит за радиус взрыва: рефлекс ExplosionAvoidance не перебит приказом; " + duel);
        helper.assertTrue(
                approachedAgain[0],
                "Дух возвращается для нового удара: «ударь и беги» — цикл, а не бесконечный отход; " + duel);
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void redistribute_TwoSidedThreat_WeightFollowsStrength(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        Point defended = new Point(absoluteHome.getX() + 0.5, absoluteHome.getY(), absoluteHome.getZ() + 0.5);
        List<SpiritEntity> defenders = new ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(5, 1, 6))) {
            defenders.add(swordsman(helper, at, home, true));
        }
        List<LivingEntity> east = new ArrayList<>();
        for (BlockPos at :
                List.of(new BlockPos(9, 1, 3), new BlockPos(9, 1, 4), new BlockPos(9, 1, 6), new BlockPos(9, 1, 7))) {
            east.add(TacticsScene.watcher(helper, EntityType.ZOMBIE, at));
        }
        Spider north = TacticsScene.watcher(helper, EntityType.SPIDER, new BlockPos(5, 1, 1));
        boolean[] eastHeld = {false};
        boolean[] moved = {false};
        List<Point> before = new ArrayList<>();

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    CoordinatorTuning.DEFENSE_RADIUS == 16.0,
                    "Радиус обороны 16: посты взвешенных секторов лежат в нём, иначе оборона не у дома");
            DefensePlan plan = coordinatorOf(helper, defenders.get(0)).defense().lastPlan();
            List<Point> posts = plan.posts().stream().map(post -> post.post()).toList();
            helper.assertTrue(
                    posts.size() == 3, "У каждого из трёх защитников свой пост: иначе оборона не распределяется");
            helper.assertTrue(
                    posts.stream().allMatch(post -> post.distanceTo(defended) <= 16.0 + 1.0),
                    "Посты в радиусе обороны от дома: иначе защитник ушёл за радиус");
            if (!eastHeld[0]) {
                long eastPosts =
                        posts.stream().filter(post -> post.x() > defended.x()).count();
                if (eastPosts >= 2 && !plan.orders().isEmpty()) {
                    eastHeld[0] = true;
                    before.addAll(posts);
                }
            }
            helper.assertTrue(
                    eastHeld[0], "Перевес защитников на востоке: четверо зомби сильнее паука, иначе сектора без веса");
            if (!moved[0] && eastHeld[0]) {
                east.forEach(LivingEntity::discard);
                east.clear();
                for (BlockPos at : List.of(
                        new BlockPos(1, 1, 3), new BlockPos(1, 1, 4), new BlockPos(1, 1, 6), new BlockPos(1, 1, 7))) {
                    east.add(TacticsScene.watcher(helper, EntityType.ZOMBIE, at));
                }
                moved[0] = true;
            }
            if (moved[0]) {
                long westPosts =
                        posts.stream().filter(post -> post.x() < defended.x()).count();
                helper.assertTrue(
                        westPosts >= 2,
                        "После смещения угрозы перевес на западе: иначе сектора застыли и не следят за угрозой");
                helper.assertTrue(
                        posts.stream().anyMatch(post -> before.stream().noneMatch(old -> old.distanceTo(post) < 1.5)),
                        "Посты сдвинулись за угрозой: иначе при смене направления никто не двигается");
                east.forEach(LivingEntity::discard);
                north.discard();
                defenders.forEach(SpiritEntity::discard);
                TacticsScene.finish(helper, defenders.get(0));
                defenders.clear();
            }
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void specialization_ArcherAndMelee_PrioritiesHold(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity archer = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        archer.setHome(home);
        archer.getInventory().addItem(new ItemStack(Items.BOW));
        archer.getInventory().addItem(new ItemStack(Items.ARROW, 64));
        SpiritEntity melee = swordsman(helper, new BlockPos(6, 1, 4), home, true);
        Creeper creeper = unkillable(TacticsScene.watcher(helper, EntityType.CREEPER, new BlockPos(5, 1, 2)));
        Zombie zombie = unkillable(TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(5, 1, 8)));
        boolean[] archerSeen = {false};
        boolean[] meleeSeen = {false};

        helper.succeedWhen(() -> {
            DefensePlan specializationPlan =
                    coordinatorOf(helper, archer).defense().lastPlan();
            helper.assertTrue(
                    planCovers(specializationPlan, archer, melee),
                    "План обороны накрывает обоих защитников: иначе решение читается по частичному снимку, "
                            + "приказов " + specializationPlan.orders().size() + ", постов "
                            + specializationPlan.posts().size() + ", тики духов " + archer.tickCount + "/"
                            + melee.tickCount);
            if (orderOf(specializationPlan, archer)
                    .map(order -> order.target().isPresent()
                            && order.target()
                                    .get()
                                    .id()
                                    .equals(creeper.getUUID().toString()))
                    .orElse(false)) {
                archerSeen[0] = true;
            }
            if (orderOf(specializationPlan, melee)
                    .map(order -> order.target().isPresent()
                            && order.target().get().id().equals(zombie.getUUID().toString()))
                    .orElse(false)) {
                meleeSeen[0] = true;
            }
            helper.assertTrue(
                    archerSeen[0],
                    "Лучник бьёт крипера первым, а не ближнего зомби: иначе таблица приоритетов не работает");
            helper.assertTrue(
                    meleeSeen[0], "Мечник бьёт зомби, а не крипера: иначе мечник идёт под взрыв вместо лучника");
            helper.assertTrue(
                    archer.isAlive() && melee.isAlive(), "Оба защитника живы: приоритеты не разменивают духов");
            creeper.discard();
            zombie.discard();
            archer.discard();
            TacticsScene.finish(helper, melee);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void specialization_NoArcher_MeleeRushesSkeleton(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity melee = swordsman(helper, new BlockPos(5, 1, 4), home, true);
        Skeleton skeleton = unkillable(TacticsScene.watcher(helper, EntityType.SKELETON, new BlockPos(5, 1, 2)));
        Zombie zombie = unkillable(TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(5, 1, 8)));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    skeleton.equals(melee.getTarget()),
                    "Без лучника мечник сближается со скелетом, а не бьёт зомби: иначе обстрел без ответа");
            DefensePlan plan = coordinatorOf(helper, melee).defense().lastPlan();
            helper.assertTrue(
                    orderOf(plan, melee)
                            .map(order -> order.stance() == CombatStance.RUSH_RANGED)
                            .orElse(false),
                    "Стойка мечника против скелета — стремительное сближение: иначе он идёт шагом под стрелами");
            skeleton.discard();
            zombie.discard();
            TacticsScene.finish(helper, melee);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void health_WoundedWithdraws_AllyTakesTarget(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity first = swordsman(helper, new BlockPos(5, 1, 6), home, false);
        SpiritEntity second = swordsman(helper, new BlockPos(5, 1, 4), home, false);
        Zombie zombie = unkillable(TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(5, 1, 8)));
        Point defended = new Point(home.x() + 0.5, home.y(), home.z() + 0.5);
        boolean[] wounded = {false};
        boolean[] handedOff = {false};
        UUID[] hurtId = {null};

        helper.succeedWhen(() -> {
            DefenseService defense = coordinatorOf(helper, first).defense();
            DefensePlan plan = defense.lastPlan();
            helper.assertTrue(
                    planCovers(plan, first, second),
                    "План обороны накрывает обоих защитников: иначе раненый выбирается по частичному снимку, "
                            + "приказов " + plan.orders().size() + ", постов "
                            + plan.posts().size());
            if (!wounded[0]) {
                Optional<DefenderOrder> holder = plan.orders().stream()
                        .filter(order -> order.target().isPresent()
                                && order.target()
                                        .get()
                                        .id()
                                        .equals(zombie.getUUID().toString()))
                        .findFirst();
                if (holder.isPresent()) {
                    SpiritEntity holderSpirit = holder.get().defender().equals(first.getUUID()) ? first : second;
                    holderSpirit.setHealth(4.0F);
                    hurtId[0] = holderSpirit.getUUID();
                    wounded[0] = true;
                }
            }
            helper.assertTrue(wounded[0], "Дух с целью ранен до 4 HP: иначе некому отходить и передавать цель");
            Optional<DefenderOrder> hurtOrder = plan.orders().stream()
                    .filter(order -> order.target().isPresent()
                            && order.target().get().id().equals(zombie.getUUID().toString()))
                    .findFirst();
            if (hurtOrder.isPresent()
                    && hurtOrder.get().stance() == CombatStance.WITHDRAW
                    && hurtOrder.get().handOff().isPresent()) {
                UUID holderId = hurtOrder.get().defender();
                SpiritEntity holder = holderId.equals(first.getUUID()) ? first : second;
                SpiritEntity ally = first.equals(holder) ? second : first;
                if (zombie.equals(ally.getTarget())) {
                    handedOff[0] = true;
                }
                helper.assertTrue(
                        holder.tactics().combatMode() == CombatMode.PASSIVE,
                        "Раненый уходит в отход: иначе он продолжает бой и гибнет");
            }
            helper.assertTrue(
                    handedOff[0], "Цель принимает второй боец: иначе переданный зомби остаётся без присмотра");
            SpiritEntity hurt = hurtId[0] != null && hurtId[0].equals(first.getUUID()) ? first : second;
            helper.assertTrue(hurt.isAlive(), "Раненый пережил передачу: иначе отход не уводит из-под удара");
            helper.assertTrue(
                    hurt.position().distanceTo(new Vec3(defended.x(), defended.y(), defended.z())) <= 2.0,
                    "Раненый отошёл к точке обороны: иначе отход только на бумаге");
            zombie.discard();
            first.discard();
            TacticsScene.finish(helper, second);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void charged_MeleeWithdraws_ChargedCreeperSurvives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity melee = swordsman(helper, new BlockPos(5, 1, 4), home, true);
        Creeper creeper = TacticsScene.watcher(helper, EntityType.CREEPER, new BlockPos(5, 1, 7));
        ServerLevel level = helper.getLevel();
        creeper.thunderHit(level, new LightningBolt(EntityType.LIGHTNING_BOLT, level));
        helper.assertTrue(
                creeper.isPowered(), "Крипер заряжен ударом молнии: иначе запрет ближнего боя проверять не на чем");
        boolean[] withdrawn = {false};

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    planCovers(coordinatorOf(helper, melee).defense().lastPlan(), melee),
                    "План обороны накрывает защитника: иначе отход читается по частичному снимку");
            if (melee.tactics().combatMode() == CombatMode.PASSIVE) {
                withdrawn[0] = true;
            }
            helper.assertTrue(
                    withdrawn[0],
                    "Мечник отходит от заряженного крипера и не атакует: ближний бой с заряженным запрещён");
            helper.assertTrue(
                    melee.isAlive(), "Мечник пережил встречу с заряженным: иначе он подошёл под усиленный взрыв");
            helper.assertTrue(
                    creeper.isAlive(), "Заряженный крипер цел: его бьёт только лучник, а мечник держится дальше");
            creeper.discard();
            TacticsScene.finish(helper, melee);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100, coordinator = true)
    public static void probe_BlocksOnLine_AreCandidatesWithinBudget(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        helper.setBlock(6, 1, 2, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Point self = new Point(
                helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)).x,
                helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)).y,
                helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)).z);
        Vec3 threatVec = helper.absoluteVec(new Vec3(8.5, 1.0, 2.5));
        Point threat = new Point(threatVec.x, threatVec.y, threatVec.z);
        Vec3 mobVec = helper.absoluteVec(new Vec3(5.5, 1.0, 2.5));
        BlockerProbe probe = new BlockerProbe();

        List<Point> candidates = probe.probe(level, self, threat, List.of(new Point(mobVec.x, mobVec.y, mobVec.z)));

        Vec3 blockVec = helper.absoluteVec(new Vec3(6.5, 1.0, 2.5));
        helper.assertTrue(
                candidates.stream()
                        .anyMatch(point -> point.distanceTo(new Point(blockVec.x, blockVec.y, blockVec.z)) < 0.6),
                "Твёрдый блок на линии «дух–скелет» попадает в кандидаты: иначе укрытие только из мобов");
        helper.assertTrue(
                candidates.stream().anyMatch(point -> point.distanceTo(new Point(mobVec.x, mobVec.y, mobVec.z)) < 0.6),
                "Зомби между духом и скелетом попадает в кандидаты: иначе приём «зомби между» не из чего строить");
        helper.assertTrue(
                BlockerProbe.BLOCK_BUDGET == 24, "Бюджет чтений блоков 24: число в тесте, факт — из production");
        helper.assertTrue(
                probe.blockReads() == 6,
                "Чтений блоков 6 на линии длиной 6: зонд идёт по клеткам линии, а не сканирует окрестность");
        helper.assertTrue(
                probe.blockReads() <= BlockerProbe.BLOCK_BUDGET,
                "Чтений не больше бюджета: иначе зонд читает полмира каждый пересчёт");
        try {
            probe.probe(null, self, threat, List.of());
            helper.assertTrue(false, "Зонд без мира обязан отказывать: иначе молча вернёт пустые кандидаты");
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(true, "отказ без мира");
        }
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, coordinator = true)
    public static void busy_SpiritWithRunningTask_NoDefenseOrder(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        spirit.startTask(new SequenceNode(List.of(new ScriptCall("wait", Map.of("ticks", 200)))));
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                "Задача запущена: иначе занятость духа проверять не на чем");
        helper.assertTrue(
                spirit.currentTaskScript().isEmpty(),
                "У запущенной задачи из шагов первый скрипт назван только с первого тика мозга: это окно, в котором "
                        + "оборона глушила занятых; назван: "
                        + spirit.currentTaskScript().orElse("?"));
        SpiritCoordinator coordinator = coordinatorOf(helper, spirit);
        coordinator.tick(List.of(spirit), helper.getLevel().getGameTime());
        helper.assertTrue(
                coordinator.defense().lastPlan().posts().isEmpty(),
                "Занятому духу не выдан пост обороны, пока задача RUNNING без названного скрипта: иначе пост "
                        + "держит тактику владельцем тика и задача встаёт; постов "
                        + coordinator.defense().lastPlan().posts().size());
        helper.assertTrue(
                spirit.tactics().activeDirective().isEmpty(),
                "Занятому духу не выдан приказ обороны: иначе тактика глушит его задачу, а пост тянет к якорю; "
                        + "приказ "
                        + spirit.tactics()
                                .activeDirective()
                                .map(d -> d.mode().toString())
                                .orElse("нет"));
        helper.succeed();
    }
}
