    static Stream<Arguments> brokenPosCases() {
        return Stream.of(
                Arguments.of("pos из двух чисел", "[1,2]"),
                Arguments.of("pos из четырёх чисел", "[1,2,3,4]"),
                Arguments.of("pos не массив", "\"not-array\""),
                Arguments.of("pos с нечисловым элементом", "[1,2,\"x\"]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenPosCases")
    void fromJson_BrokenPos_ThrowsIllegalArgument(String description, String brokenPos) {
        assertThrows(IllegalArgumentException.class, () -> WorldObservation.fromJson(jsonWithPos(brokenPos)),
                description + ": битый JSON с pos обязан приводить к IllegalArgumentException с понятной причиной, "
                        + "иначе bridge падает нечитаемой ошибкой на повреждённом запросе");
    }

    @Test
    void fromJson_BrokenPos_ControlCase_ReturnsValidPos() {
        WorldObservation observation = WorldObservation.fromJson(jsonWithPos("[1,2,3]"));
        assertEquals(new BlockCoord(1, 2, 3), observation.position(),
                "Контрольный случай pos из трёх чисел должен корректно разбирается: до этого проверки битого pos "
                        + "могли бы ошибочно зеленью по другой причине");
    }