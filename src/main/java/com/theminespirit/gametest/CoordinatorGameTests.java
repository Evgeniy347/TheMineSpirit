package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.SpiritSnapshotReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
public final class CoordinatorGameTests {
    private CoordinatorGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true)
    public static void defense_FourSpiritsOneMob_PostsAroundHomeAndOneEngages(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        Point defended = new Point(home.x() + 0.5, home.y(), home.z() + 0.5);
        List<SpiritEntity> defenders = spawnArmedDefenders(helper, home);
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
            if (!engaged[0]
                    && (husk.isDeadOrDying()
                            || husk.isRemoved()
                            || defenders.stream().anyMatch(defender -> husk.equals(defender.getTarget())))) {
                engaged[0] = true;
            }
            if (!attacked[0]) {
                assertSharedPostThenSendHusk(helper, defenders, husk, defended, victim, postsBefore);
                attacked[0] = true;
            }
            if (!victimAttacked[0]
                    && SpiritSnapshotReader.read(victim[0], helper.getLevel().getGameTime())
                            .attacked()) {
                victimAttacked[0] = true;
            }
            helper.assertTrue(
                    victimAttacked[0],
                    "Жертва помечена атакованной в снимке: иначе координатор не видит нападение и не шлёт помощь");
            helper.assertTrue(
                    engaged[0],
                    "Хотя бы один защитник вступает в бой: иначе помощь не пришла, а поселение не защищается");
            if (!removed[0]) {
                victim[0].discard();
                removed[0] = true;
            }
            List<SpiritEntity> rest = defenders.stream()
                    .filter(defender -> !defender.equals(victim[0]))
                    .toList();
            assertThreePostsRemain(helper, rest, defended);
            if (!secondFront[0]) {
                west[0] = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(1, 1, 5));
                west[0].addEffect(
                        new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TacticsScene.FOREVER, 4, false, false));
                secondFront[0] = true;
            }
            helper.assertTrue(
                    secondFront[0] && west[0].isAlive(),
                    "Второй рубеж открыт на западе: ждём перераспределение за новой угрозой");
            assertPostsShiftedToSecondFront(helper, rest, defended, postsBefore);
            husk.discard();
            west[0].discard();
            rest.forEach(SpiritEntity::discard);
            TacticsScene.finish(helper, victim[0]);
        });
    }

    private static List<SpiritEntity> spawnArmedDefenders(GameTestHelper helper, BlockCoord home) {
        List<SpiritEntity> defenders = new ArrayList<>();
        for (BlockPos at :
                List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(4, 1, 6), new BlockPos(6, 1, 6))) {
            SpiritEntity spirit = TacticsScene.spirit(helper, at);
            spirit.setHome(home);
            spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
            defenders.add(spirit);
        }
        return defenders;
    }

    private static void assertSharedPostThenSendHusk(
            GameTestHelper helper,
            List<SpiritEntity> defenders,
            Husk husk,
            Point defended,
            SpiritEntity[] victim,
            Set<Point> postsBefore) {
        List<Point> posts = postsOf(defenders);
        helper.assertTrue(
                posts.size() == 4,
                "У каждого из четырёх защитников свой пост: иначе оборона не распределяется по секторам");
        helper.assertTrue(
                new HashSet<>(posts).size() == 1,
                "Одна угроза — один рубеж: взвешенная оборона (33) ставит всех на угрожаемый сектор, "
                        + "а не размазывает поровну");
        helper.assertTrue(
                posts.stream().allMatch(post -> post.distanceTo(defended) <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                "Посты в радиусе обороны от дома (+1.0 на округление блока): иначе защитник ушёл за радиус");
        victim[0] = defenders.get(0);
        Point post = posts.get(0);
        husk.setPos(new Vec3(post.x(), post.y(), post.z()));
        husk.setTarget(victim[0]);
        postsBefore.addAll(posts);
    }

    private static void assertThreePostsRemain(GameTestHelper helper, List<SpiritEntity> rest, Point defended) {
        List<Point> posts = postsOf(rest);
        helper.assertTrue(
                posts.size() == 3,
                "После снятия защитника у каждого из трёх оставшихся свой пост: иначе оборона не переживает выбытие");
        helper.assertTrue(
                posts.stream().allMatch(post -> post.distanceTo(defended) <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                "Оставшиеся держатся в радиусе обороны: иначе перераспределение разбросало посты");
    }

    private static void assertPostsShiftedToSecondFront(
            GameTestHelper helper, List<SpiritEntity> rest, Point defended, Set<Point> postsBefore) {
        List<Point> shifted = postsOf(rest);
        helper.assertTrue(
                shifted.size() == 3, "Оборона держит три поста на два направления: иначе второй фронт оголяет первый");
        helper.assertTrue(
                shifted.stream().allMatch(post -> post.distanceTo(defended) <= CoordinatorTuning.DEFENSE_RADIUS + 1.0),
                "Посты двух фронтов в радиусе обороны: иначе защитники разбрелись");
        helper.assertTrue(
                shifted.stream().anyMatch(post -> postsBefore.stream().noneMatch(old -> old.distanceTo(post) < 1.5)),
                "Посты перераспределились за новой угрозой: иначе координатор держит мёртвые сектора");
    }

    private static List<Point> postsOf(List<SpiritEntity> spirits) {
        List<Point> posts = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            Optional<OrdersBuilder.Zone> zone = spirit.tactics().snapshot().zone();
            zone.map(OrdersBuilder.Zone::center).ifPresent(posts::add);
        }
        return posts;
    }
}
