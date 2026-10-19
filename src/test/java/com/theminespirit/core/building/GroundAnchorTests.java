package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GroundAnchorTests {
    @Test
    void groundY_LeavesAboveGround_DescendsToGround() {
        Set<Integer> solid = Set.of(63);

        int ground = GroundAnchor.groundY(66, -64, solid::contains);

        assertEquals(
                63,
                ground,
                "Якорь, попавший на листву, обязан опуститься до земли под ней: иначе демо-стройка повиснет "
                        + "в кроне, а не встанет на твёрдый полный блок");
    }

    @Test
    void groundY_TopAlreadySolid_KeepsIt() {
        Set<Integer> solid = Set.of(66, 63);

        int ground = GroundAnchor.groundY(66, -64, solid::contains);

        assertEquals(
                66, ground, "Если верхний блок уже твёрдый, спуск не начинается: якорь не роет землю под собой зря");
    }

    @Test
    void groundY_NoSolidDownToBedrock_IsRejected() {
        Set<Integer> solid = Set.of();

        assertThrows(
                IllegalArgumentException.class,
                () -> GroundAnchor.groundY(66, 0, solid::contains),
                "Столбец без твёрдого блока до низа мира обязан отвергаться: постройке не на что встать, "
                        + "а тихий выбор «воздуха» уронил бы духа в пустоту");
    }
}
