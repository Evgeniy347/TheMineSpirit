package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.WorldScanner;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritGameTests {
    private SpiritGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void adapterRegistry_EveryScriptId_IsDescribedInCatalog(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        ScriptRegistry registry = SpiritScripts.registryFor(spirit);
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        for (String id : registry.ids()) {
            helper.assertTrue(catalog.find(id).isPresent(),
                    "Скрипт '" + id + "' адаптера отсутствует в каталоге скриптов: реестр выдаёт id, которого нет в "
                            + "CoreScripts.defaultCatalog()");
        }
        ScriptCatalog restricted = SpiritScripts.catalogFor(spirit);
        helper.assertTrue(!restricted.all().isEmpty(),
                "catalogFor должен возвращать непустой каталог для реализованных скриптов: иначе LlmPlanner получит пустой каталог");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spawn_SpiritEntityType_CreatesLivingSpirit(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));

        helper.assertTrue(spirit.isAlive(),
                "Дух обязан появляться живым в мире: сломанная сущность не сможет выполнять скрипты goto/follow");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void observe_WorldWithFloor_ReturnsNonEmptyBlocksAndSpectreEntity(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
        WorldScanner.Scan scan = WorldScanner.observe(helper.getLevel(), "spirit_1",
                new BlockCoord(
                        absolute.getX(), absolute.getY(), absolute.getZ()),
                ScanRadius.of(2), spirit.getInventory());
        WorldObservation observation = scan.observation();

        helper.assertTrue(scan.scannedPositions() > 0,
                "Скан обязан считать просмотренные позиции: по пустому числу GameTest не проверит счётчик "
                        + "и не заметит, что обход грузит чанки");
        helper.assertTrue(!observation.blocks().isEmpty(),
                "Скан вокруг духа обязан видеть блоки пола: при пустом наблюдении ИИ не может планировать добычу");
        helper.assertTrue(observation.hash().length() == 64,
                "hash наблюдения обязан быть SHA-256 длиной 64: по нему bridge сравнивает картины мира");
        helper.assertTrue(observation.entities().stream()
                        .anyMatch(entity -> entity.type().equals(ModIdentity.MOD_ID + ":spirit")),
                "Скан обязан видеть самого духа в списке сущностей: без этого наблюдение не видит bot-а себя");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scan_legacy_unloaded", timeoutTicks = 100)
    public static void scan_Radius32NearUnloadedChunks_DoesNotLoadChunks(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        int structureChunkX = base.getX() >> 4;
        int structureChunkZ = base.getZ() >> 4;
        int firstUnloaded = Integer.MIN_VALUE;
        for (int k = 1; k <= 256; k++) {
            if (!helper.getLevel().hasChunk(structureChunkX + k, structureChunkZ)) {
                firstUnloaded = structureChunkX + k;
                break;
            }
        }
        helper.assertTrue(firstUnloaded != Integer.MIN_VALUE,
                "В пределах 256 чанков к востоку обязана найтись незагруженная область: иначе мир загружен "
                        + "целиком и тест не может проверить пропуск незагруженных чанков");
        // Центр ставится так, чтобы крайний восточный чанк радиуса совпал с найденным незагруженным: тогда
        // в окно попадают и загруженные чанки, и гарантированно незагруженный.
        int centerX = firstUnloaded * 16 - 24;
        BlockPos center = new BlockPos(centerX, base.getY(), base.getZ());
        int before = helper.getLevel().getChunkSource().getLoadedChunksCount();
        WorldScanner.Scan scan = WorldScanner.observe(helper.getLevel(), "spirit_1",
                new BlockCoord(center.getX(), center.getY(), center.getZ()),
                ScanRadius.of(32), spirit.getInventory());
        int after = helper.getLevel().getChunkSource().getLoadedChunksCount();

        helper.assertTrue(scan.skippedChunks() > 0,
                "Скан радиусом 32 обязан встретить незагруженные чанки у края загруженной области, иначе тест "
                        + "доказывает неработу загрузки на пустом множестве: пропущено " + scan.skippedChunks());
        helper.assertTrue(after == before,
                "Скан у края загруженной области обязан не грузить чанки: было " + before
                        + " загруженных чанков, стало " + after + ". Обращение к getBlockState незагруженного "
                        + "чанка синхронно грузит его и фризит серверный тик");
        helper.assertTrue(scan.scannedPositions() > 0,
                "Скан обязан просмотреть позиции уже загруженных чанков, а не ничего: просмотрено "
                        + scan.scannedPositions());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void gotoScript_SpiritOnFloor_ReachesTargetWithinTolerance(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        Vec3 targetCenter = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        spirit.startTask(new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())));

        helper.succeedWhen(() -> {
            double distance = spirit.position().distanceTo(targetCenter);
            helper.assertTrue(distance <= 1.5,
                    "goto обязан довести духа до цели: расстояние " + distance + " больше допуска 1.5. "
                            + "Статус задачи: " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskStatus().map(status -> status != RunStatus.FAILED).orElse(true),
                    "goto обязан не падать, пока цель достижима: провал скрипта остановил бы движение духа");
        });
    }

    // [флак] дух идёт к недостижимой цели, ждём тайм-аут задачи
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void gotoScript_UnreachableTarget_KeepsFailureStatusAndReason(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));
        spirit.startTask(new ScriptCall("goto",
                Map.of("x", target.getX(), "y", target.getY() + 20, "z", target.getZ())));

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status == RunStatus.FAILED,
                    "goto к недостижимой точке обязан довести задачу до FAILED, а не потерять её: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().filter(reason -> !reason.isBlank()).isPresent(),
                    "после провала причина обязана дойти до задачи, а не теряться. Фактическое: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(spirit.taskFailureReason().filter(reason -> reason.contains("не дошёл")
                            || reason.contains("застрял")).isPresent(),
                    "причина падения goto обязана описывать неудачу перемещения, а не общий текст движка. "
                            + "Фактическое: " + spirit.taskFailureReason().orElse("нет причины"));
        });
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}