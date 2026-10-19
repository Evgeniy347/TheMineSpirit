source lib.sh
W=11
cat <<'H'
# family_house_2 — дом семьи, уровень 2 «дом» (tasks/47-settlement). Тот же участок и та же дверь, что у уровня 1:
# дом шире и глубже, потолок 4, две комнаты (кухня-столовая и спальня) через проём шириной 3, окна высотой 2
# со ставнями, козырёк над дверью, карниз под свесом, фронтонное окно высотой 2.
name: "Дом семьи, уровень 2 (две комнаты)"
type: housing
stage: 1
level: 2
requires: family_house_1
size: 13x12x15   # Ш×В×Д
entrance: 6,1,1
legend:
  'c': cobblestone
  '$': spruce_planks
  '#': oak_planks
  'L': spruce_log[axis=y]
  '=': stripped_spruce_log[axis=y]
  'X': stripped_spruce_log[axis=x]
  'Z': stripped_spruce_log[axis=z]
  'G': glass_pane
  'D': oak_door[facing=south,hinge=left]
  'F': furnace[facing=south]
  'k': smoker[facing=south]
  'b': barrel[facing=up]
  'T': crafting_table
  'u': water_cauldron[level=3]
  'n': spruce_stairs[facing=north,half=bottom]
  's': spruce_stairs[facing=south,half=bottom]
  't': oak_fence
  'q': oak_pressure_plate
  '~': red_carpet
  'm': white_carpet
  'B': red_bed[facing=south]
  'C': chest[facing=west,type=right]
  'K': chest[facing=west,type=left]
  'Q': bookshelf
  'S': wall_torch[facing=south]
  'N': wall_torch[facing=north]
  'E': wall_torch[facing=east]
  'W': wall_torch[facing=west]
  'd': spruce_trapdoor[facing=north,half=bottom,open=true]
  'v': spruce_trapdoor[facing=west,half=bottom,open=true]
  'r': spruce_trapdoor[facing=east,half=bottom,open=true]
  'U': spruce_stairs[facing=east,half=top]
  'V': spruce_stairs[facing=west,half=top]
  'e': spruce_stairs[facing=east,half=bottom]
  'w': spruce_stairs[facing=west,half=bottom]
  '_': spruce_slab[type=bottom]
  'P': potted_poppy
  'O': potted_dandelion
  'p': cobblestone_stairs[facing=south,half=bottom]
layers:
H
L 0 "цоколь и пол" "    P p O" " ccccccccccc" "11* c\$\$\$\$\$\$\$\$\$c" " ccccccccccc" "      ."
L 1 "мебель" "      ." \
  " L###=D=###L" \
  " #FkbT...PO#" \
  " #ummmmm.nn#" \
  " #.mmmmm.tt#" \
  " #.mmmmm.ss#" \
  " #.........#" \
  " L###...###L" \
  " #Q.~~~~~.C#" \
  " #Q.~~~~~.K#" \
  " #P.~~~~~.O#" \
  " #B.B....B.#" \
  " #B.B.PO.B.#" \
  " L####L####L" "      ."
side2() { echo "v#$1#r"; }
L 2 "окна, ставни, свет" "  d d . d d" \
  " L#G#=D=#G#L" \
  " #S.......S#" \
  "v#.........#r" \
  " G.......qqG" \
  "v#.........#r" \
  " #.........#" \
  " L###...###L" \
  " #Q.......W#" \
  "v#Q........#r" \
  " G.........G" \
  "v#.........#r" \
  " #E.......W#" \
  " L#G##L##G#L" "      ."
L 3 "окна высотой 2, фонарики у двери" "  d dN.Nd d" \
  " L#G#XXX#G#L" \
  " #.........#" \
  "v#.........#r" \
  " G.........G" \
  "v#.........#r" \
  " #.........#" \
  " L###...###L" \
  " #.........#" \
  "v#.........#r" \
  " G.........G" \
  "v#.........#r" \
  " #.........#" \
  " L#G##L##G#L" "      ."
L 4 "верх стен, козырёк, карниз" "     ___" \
  "UL#########LV" \
  "5*U#.........#V" \
  "UL###...###LV" \
  "5*U#.........#V" \
  "UL#########LV" "      ."
L 5 "обвязка и свес" "e#         #w" "eLXXXXXXXXXLw" "5*eZ.........Zw" "eL.........Lw" "5*eZ.........Zw" "eLXXXXXXXXXLw" "e#         #w"
for k in 1 2 3 4 5; do
  win="#"; [ $k -eq 2 ] || [ $k -eq 3 ] && win="G"
  rows=("$(roof_row $W $k over)" "$(roof_row $W $k gable $win)")
  for ((z=2;z<=12;z++)); do rows+=("$(roof_row $W $k mid)"); done
  rows+=("$(roof_row $W $k gable $win)" "$(roof_row $W $k over)")
  L $((5+k)) "скат" "${rows[@]}"
done
L 11 "конёк" "15*      _"
