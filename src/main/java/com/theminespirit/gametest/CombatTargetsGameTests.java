package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CombatTargetsGameTests {
    private CombatTargetsGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolvePlayerByName_ReturnsThatPlayer(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 3), "Steve42");

        Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, TargetSelector.parse("player:Steve42"));
        helper.assertTrue(
                found.isPresent(),
                "игрок по имени обязан находиться, игрок: " + player.getName().getString());
        helper.assertTrue(
                player.equals(found.get()), "найден должен быть ровно тот игрок, что искали, найден: " + found.get());
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolveSpiritUuid_ReturnsThatSpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        SpiritEntity other = TacticsScene.spirit(helper, new BlockPos(5, 1, 7));

        Optional<LivingEntity> found =
                ScriptTargetResolver.resolve(spirit, TargetSelector.parse("spirit:" + other.getUUID()));
        helper.assertTrue(found.isPresent(), "дух по UUID обязан находиться, UUID: " + other.getUUID());
        helper.assertTrue(
                other.equals(found.get()), "найден должен быть ровно тот дух, что искали, найден: " + found.get());
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolveHostileKind_ReturnsNearestMonster(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk near = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(6, 1, 6));
        Husk far = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 10));

        Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(found.isPresent(), "враждебный моб обязан находиться, мобов: 2, найдено пусто");
        helper.assertTrue(
                near.equals(found.get()),
                "ближайший враждебный обязан выбираться первым, найден: " + found.get() + " вместо " + near
                        + ", дальние: " + far);
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolveUnknownPlayer_ReturnsEmpty(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, TargetSelector.parse("player:Nobody404"));
        helper.assertTrue(
                found.isEmpty(),
                "отсутствующий игрок обязан давать Optional.empty(), а не исключение, найден: " + found.orElse(null));
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolveWithoutTargets_ReturnsEmpty(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, TargetSelector.parse("nearest"));
        helper.assertTrue(
                found.isEmpty(),
                "на пустой арене ближайший обязан давать Optional.empty(), найден: " + found.orElse(null));
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void targets_ResolveTwice_CountsTwoEntityQueries(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        int before = spirit.entityQueries();

        ScriptTargetResolver.resolve(spirit, TargetSelector.parse("nearest"));
        ScriptTargetResolver.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(
                spirit.entityQueries() == before + 2,
                "каждый resolve обязан ровно один раз запросить мир, запросов: " + (spirit.entityQueries() - before)
                        + " вместо 2");
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }
}
