package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.neoforge.SpiritDamageWatch;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.LifeInputs;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeScenesGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(2, 1, 2);
    private static final BlockPos ZOMBIE_START = new BlockPos(6, 1, 6);

    private LifeScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_life_arena", timeoutTicks = 121)
    public static void lifeScene_ZombieTargetsSpirit_ThreatAndProtectionSeen(GameTestHelper helper) {
        GameTestFilter.setMobsTargetSpirits(false);
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, SPIRIT_START);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_START);
        zombie.setPersistenceRequired();
        zombie.setTarget(spirit);
        float healthBefore = spirit.getHealth();
        helper.assertTrue(
                spirit.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4.0F),
                "Зомби обязан ранить духа: иначе свежий урон не проверить в мире");
        int distance = (int) Math.round(Math.sqrt(zombie.distanceToSqr(spirit)));

        helper.succeedWhen(() -> {
            LifeInputs.Input input = LifeInputs.of(helper.getLevel(), List.of(spirit), Set.of());
            Optional<GraphFacts.Threat> found = input.threats().stream()
                    .filter(threat -> threat.id().equals(zombie.getUUID().toString()))
                    .findFirst();
            helper.assertTrue(
                    found.isPresent(), "Живой зомби рядом с духом обязан попасть на вход угроз: " + input.threats());
            GraphFacts.Threat threat = found.orElseThrow();
            helper.assertTrue(
                    "minecraft:zombie".equals(threat.type()),
                    "Тип угрозы — id сущности, иначе граф не выберет защиту: " + threat.type());
            helper.assertTrue(
                    threat.targetKind() == GraphFacts.TargetKind.SPIRIT && threat.targetingSelf(),
                    "Цель зомби — дух: связь «угроза духу → защитить», вид " + threat.targetKind());
            helper.assertTrue(
                    spirit.getUUID().toString().equals(threat.target()),
                    "Угроза называет духа, на которого агрена: " + threat.target());
            helper.assertTrue(
                    input.events().contains("life_threat") && input.events().contains("life_threat_spirit"),
                    "Угроза духу поднимает события жизни и защиты духа: " + input.events());
            helper.assertTrue(
                    Math.abs(threat.distance() - distance) <= 1,
                    "Расстояние моба считается до духа-цели: " + threat.distance() + " вместо " + distance);
            helper.assertTrue(
                    spirit.getHealth() < healthBefore,
                    "Урон в мире уменьшает здоровье духа: " + spirit.getHealth() + "/" + healthBefore);
            GraphFacts.Hurt hurt =
                    SpiritDamageWatch.recent(spirit.getUUID(), helper.getLevel().getGameTime());
            helper.assertTrue(
                    hurt != null && hurt.amount() > 0 && "minecraft:zombie".equals(hurt.source()),
                    "Свежий урон духу известен графу: " + hurt);
            zombie.discard();
            TacticsScene.finish(helper, spirit);
        });
    }
}
