source roof.sh
cat <<'H'
#!# well_2 — колодец, уровень 2 (catalog.md): каменный сруб 3×3 на площадке 5×5 из каменного кирпича,
#!# двускатная крыша на четырёх бревенчатых столбах со свесом, фонарь на цепи над водой, котёл-ведро и цветы у сруба.
#!name: "Колодец, уровень 2"
#!type: infrastructure
#!stage: 2
#!level: 2
#!requires: well
#!size: 7x9x7   # Ш×В×Д
#!entrance: 3,1,1
#!exemptions:
#!  - rule: closed_walls
#!    reason: "колодец открыт: крыша стоит на четырёх столбах, сплошных стен нет по замыслу"
#!legend:
#!  'S': stone_bricks
#!  'c': cobblestone_wall
#!  'W': water
#!  't': stripped_spruce_log[axis=y]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  '#': spruce_planks
#!  'e': spruce_stairs[facing=east,half=bottom]
#!  'w': spruce_stairs[facing=west,half=bottom]
#!  '_': spruce_slab[type=bottom]
#!  'C': chain
#!  'H': lantern[hanging=true]
#!  'u': water_cauldron[level=3]
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!layers:
H
echo "size 7 9 7"
echo "label 0 площадка"; echo "label 1 сруб и столбы"; echo "label 4 потолок"; echo "label 5 крыша"
echo "box 0 0 1 5 1 5 S"; echo "set 0 3 3 W"; echo "set 0 3 0 p"
echo "walls 1 1 2 4 2 4 c"
for x in 1 5; do for z in 1 5; do echo "col 1 3 $x $z t"; done; done
echo "set 1 1 3 u"; echo "set 1 5 3 P"; echo "set 1 3 5 O"
echo "box 4 4 1 5 1 5 #"; roof 1 5 1 5 5 ""; echo "box 5 5 1 5 1 5 #"; echo "box 6 6 2 4 1 5 #"
echo "set 3 3 3 C"; echo "set 2 3 3 H"
