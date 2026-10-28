package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmTestSupport.ARENA_HEIGHT;
import static com.theminespirit.gametest.FarmTestSupport.ARENA_SIZE;
import static com.theminespirit.gametest.FarmTestSupport.name;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.CropState;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.CropRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmCropSupport {
    private FarmCropSupport() {}

    static TileCase plain(String name, Block ground, Block above, Ground expectedGround, Above expectedAbove) {
        return new TileCase(
                name, ground, above.defaultBlockState(), new FarmTile(expectedGround, expectedAbove, Optional.empty()));
    }

    static TileCase crop(CropKind kind, int age) {
        Block block = CropRegistry.block(kind);
        IntegerProperty ageProperty =
                (IntegerProperty) block.getStateDefinition().getProperty("age");
        return new TileCase(
                kind + " возраст " + age,
                Blocks.FARMLAND,
                block.defaultBlockState().setValue(ageProperty, age),
                new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, age))));
    }

    static int countBlock(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int y = 0; y < ARENA_HEIGHT; y++) {
                for (int z = 0; z < ARENA_SIZE; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    static BlockCoord coordOf(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }
}
