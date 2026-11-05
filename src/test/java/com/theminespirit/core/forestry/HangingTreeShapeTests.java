package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class HangingTreeShapeTests {
    @Test
    void detect_HangingTopWithoutTrunk_ChoppedWhenAllowed() {
        TreeGrid grid = new TreeGrid()
                .log(0, 5, 0)
                .log(0, 6, 0)
                .log(0, 7, 0)
                .leaves(1, 6, 0)
                .leaves(1, 7, 0)
                .leaves(0, 8, 0);
        BlockCoord start = new BlockCoord(0, 5, 0);

        NotATree strict = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(start, grid),
                "Обычный разбор без почвы не считает висящую верхушку деревом: так защищены случайные брёвна");
        assertEquals(
                NotATree.Reason.NO_SOIL,
                strict.reason(),
                "Без разрешения висящей верхушки причина отказа — отсутствие почвы под нижним бревном");

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(start, grid, true),
                "Рубка принимает висящую верхушку без ствола: иначе после срубленной нижней части дерево бросают "
                        + "висеть, а дух застревает на «не дотянуться»");
        assertEquals(
                List.of(new BlockCoord(0, 5, 0), new BlockCoord(0, 6, 0), new BlockCoord(0, 7, 0)),
                tree.logs(),
                "Верхушка рубится целиком сверху вниз: все оставшиеся брёвна в дереве");
    }
}
