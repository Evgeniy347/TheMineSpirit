package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.coordOf;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.SPIRIT_START;
import static com.theminespirit.gametest.ChopViewSupport.blockClassificationTable;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelTreeView;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopViewGameTests {
    private ChopViewGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 40)
    public static void treeView_BlockTable_ClassifiesBuiltAndNaturalBlocks(GameTestHelper helper) {
        prepareField(helper);
        Map<BlockState, Boolean> table = blockClassificationTable();

        LevelTreeView view = new LevelTreeView(helper.getLevel(), Set.of(Blocks.OAK_LOG));
        int index = 0;
        for (Map.Entry<BlockState, Boolean> entry : table.entrySet()) {
            BlockPos cell = new BlockPos(index % PLATFORM, 1, index / PLATFORM);
            helper.getLevel().setBlock(helper.absolutePos(cell), entry.getKey(), Block.UPDATE_CLIENTS);
            BlockPos absolute = helper.absolutePos(cell);
            BlockCoord coord = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
            helper.assertTrue(
                    view.isArtificial(coord) == entry.getValue(),
                    "блок " + entry.getKey() + (entry.getValue() ? " — постройка" : " — естественный")
                            + ": LevelTreeView.isArtificial вернул " + view.isArtificial(coord)
                            + ". Таблица решает, что дух не тронет; ошибка в сторону «естественный» ломает стены игрока, "
                            + "в сторону «постройка» оставляет настоящие деревья стоять");
            int answers = (view.isLog(coord) ? 1 : 0)
                    + (view.isNaturalLeaves(coord) ? 1 : 0)
                    + (view.isSoil(coord) ? 1 : 0)
                    + (view.isArtificial(coord) ? 1 : 0);
            helper.assertTrue(
                    answers <= 1, "блок " + entry.getKey() + " не может быть двумя видами сразу, ответов " + answers);
            index++;
        }
        helper.assertTrue(
                view.isLog(coordOf(helper, table, Blocks.OAK_LOG)),
                "дубовое бревно при запрошенной породе oak — бревно");
        helper.assertTrue(
                !view.isLog(coordOf(helper, table, Blocks.BIRCH_LOG)),
                "берёзовое бревно при запрошенной породе oak не бревно рубки, но и не постройка");
        clearField(helper);
        helper.succeed();
    }

    @SceneStep(template = FIELD, timeoutTicks = 40)
    public static void treeView_UnloadedCell_AllAnswersFalseAndChunkNotLoaded(GameTestHelper helper) {
        prepareField(helper);
        Level level = helper.getLevel();
        BlockPos base = helper.absolutePos(SPIRIT_START);
        BlockPos unloaded = GameTestFixtures.firstUnloadedChunkEast(helper, base);
        helper.assertTrue(
                unloaded != null, "рядом должна найтись незагруженная область для проверки неизвестной клетки");
        int chunkX = unloaded.getX() >> 4;
        int chunkZ = unloaded.getZ() >> 4;
        helper.assertTrue(
                level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "клетка для проверки обязана быть в незагруженном чанке");
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        LevelTreeView view = new LevelTreeView(level, Set.of(Blocks.OAK_LOG));
        BlockCoord coord = new BlockCoord(unloaded.getX(), unloaded.getY(), unloaded.getZ());
        helper.assertTrue(
                !view.isLog(coord) && !view.isNaturalLeaves(coord) && !view.isSoil(coord) && !view.isArtificial(coord),
                "клетка в незагруженном чанке неизвестна: все четыре ответа обязаны быть ложью, иначе дух примет "
                        + "чужой блок за своё бревно, почву или постройку");
        helper.assertTrue(
                level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "чтение неизвестной клетки не должно грузить чанк");
        helper.assertTrue(
                level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                "чтение неизвестной клетки не должно грузить чанки: было " + loadedBefore + ", стало "
                        + level.getChunkSource().getLoadedChunksCount());
        clearField(helper);
        helper.succeed();
    }
}
