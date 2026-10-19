source roof.sh
cat <<'H'
#!# player_house_2 — дом игрока, уровень 2 (catalog.md, sketches-manor.md): этап 2, кровати духов переехали в дома
#!# семей. Низ — булыжник, верх — еловые доски в каркасе из ободранной ели, цоколь из каменного кирпича; двойная
#!# дверь под козырьком с фонарями, окна 2×2 со ставнями. Зал с камином и креслами, прихожая у двери, стол,
#!# книжные полки, два двойных сундука; сзади — спальня игрока и гостевая. Крыша из тёмного дуба, окна во фронтонах.
#!name: "Дом игрока, уровень 2"
#!type: housing
#!stage: 2
#!level: 2
#!requires: player_house_1
#!size: 18x14x17   # Ш×В×Д
#!entrance: 8,1,1
#!legend:
#!  'S': stone_bricks
#!  'c': cobblestone
#!  '$': spruce_planks
#!  '#': dark_oak_planks
#!  'L': stripped_spruce_log[axis=y]
#!  'X': stripped_spruce_log[axis=x]
#!  'Z': stripped_spruce_log[axis=z]
#!  'e': dark_oak_stairs[facing=east,half=bottom]
#!  'w': dark_oak_stairs[facing=west,half=bottom]
#!  '_': dark_oak_slab[type=bottom]
#!  'G': glass_pane
#!  'D': spruce_door[facing=south,hinge=left]
#!  'E': spruce_door[facing=south,hinge=right]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  'Q': bricks
#!  'f': campfire[lit=true]
#!  'g': spruce_stairs[facing=east,half=bottom]
#!  'h': spruce_stairs[facing=west,half=bottom]
#!  'i': spruce_stairs[facing=north,half=bottom]
#!  'R': spruce_stairs[facing=south,half=bottom]
#!  't': spruce_fence
#!  'q': spruce_pressure_plate
#!  '~': red_carpet
#!  'm': brown_carpet
#!  'B': red_bed[facing=south]
#!  'C': blue_bed[facing=south]
#!  'b': barrel[facing=up]
#!  'F': furnace[facing=east]
#!  'T': crafting_table
#!  'V': chest[facing=north,type=right]
#!  'W': chest[facing=north,type=left]
#!  'O': bookshelf
#!  'P': potted_poppy
#!  '9': potted_azure_bluet
#!  '0': potted_red_tulip
#!  'H': lantern[hanging=true]
#!  'd': spruce_trapdoor[facing=north,half=bottom,open=true]
#!  'n': spruce_trapdoor[facing=south,half=bottom,open=true]
#!  'v': spruce_trapdoor[facing=west,half=bottom,open=true]
#!  'r': spruce_trapdoor[facing=east,half=bottom,open=true]
#!layers:
H
echo "size 18 14 17"
echo "label 0 цоколь и пол"; echo "label 1 зал и комнаты"; echo "label 5 обвязка и свес"
echo "box 0 0 1 16 1 15 S"; echo "box 0 0 2 15 2 14 \$"; echo "box 0 0 8 9 0 0 p"
# стены: низ булыжник, верх доски, каркас
echo "walls 1 2 1 16 1 15 c"; echo "walls 3 4 1 16 1 15 \$"
for x in 1 16; do for z in 1 8 15; do echo "col 1 4 $x $z L"; done; done; for x in 7 10; do echo "col 1 4 $x 1 L"; done; echo "col 1 4 8 15 L"; echo "col 1 4 9 15 L"
echo "set 1 8 1 D"; echo "set 2 8 1 D"; echo "set 1 9 1 E"; echo "set 2 9 1 E"; echo "box 3 3 8 9 1 1 X"
for y in 2 3; do
  for x in 3 4 13 14; do echo "set $y $x 1 G"; done; for x in 4 5 12 13; do echo "set $y $x 15 G"; done
  for z in 4 5 11 12; do echo "set $y 1 $z G"; echo "set $y 16 $z G"; done
  for x in 2 5 12 15; do echo "set $y $x 0 d"; done; for x in 3 6 11 14; do echo "set $y $x 16 n"; done
  for z in 3 6 10 13; do echo "set $y 0 $z v"; echo "set $y 17 $z r"; done
done
# козырёк над дверью с фонарями
echo "box 4 4 7 10 0 0 #"; echo "box 5 5 7 10 0 0 _"; echo "set 3 7 0 H"; echo "set 3 10 0 H"
# перегородки: зал спереди, сзади спальня игрока и гостевая (проёмы во всю высоту)
echo "box 1 4 2 15 9 9 \$"; echo "box 1 4 4 5 9 9 ."; echo "box 1 4 11 12 9 9 ."; echo "box 1 4 8 8 10 14 \$"
# зал: камин в западной стене, кресла, ковёр, стол, полки, сундуки
echo "box 1 1 3 7 3 7 ~"
echo "set 1 1 5 f"; echo "col 1 3 2 4 Q"; echo "col 1 3 2 6 Q"; echo "set 1 4 4 g"; echo "set 1 4 6 g"
echo "box 1 1 10 12 4 6 t"; echo "box 2 2 10 12 4 6 q"; for z in 4 5 6; do echo "set 1 9 $z h"; echo "set 1 13 $z g"; done
echo "box 1 1 15 15 3 7 O"; echo "box 2 2 15 15 6 7 O"; echo "set 1 2 2 b"; echo "set 2 2 2 P"; echo "set 1 6 2 b"; echo "set 1 11 2 b"; echo "set 2 11 2 9"
echo "set 1 3 8 W"; echo "set 1 4 8 V"; echo "set 1 12 8 W"; echo "set 1 13 8 V"; echo "set 1 15 8 T"; echo "set 1 2 8 F"
echo "box 1 1 8 9 2 8 m"
# спальня игрока
echo "box 1 1 2 7 10 14 ~"; echo "set 1 3 13 B"; echo "set 1 3 14 B"; echo "set 1 2 14 b"; echo "set 2 2 14 0"; echo "set 1 5 14 W"; echo "set 1 6 14 V"; echo "box 1 2 7 7 10 10 O"
# гостевая
echo "box 1 1 9 15 10 14 m"; echo "set 1 14 13 C"; echo "set 1 14 14 C"; echo "set 1 15 14 b"; echo "set 2 15 14 P"; echo "set 1 10 14 b"; echo "box 1 2 9 9 10 10 O"
# обвязка и крыша
echo "walls 5 5 1 16 1 15 Z"; echo "box 5 5 1 16 1 1 X"; echo "box 5 5 1 16 15 15 X"; for x in 1 16; do for z in 1 15; do echo "set 5 $x $z L"; done; done
roof 1 16 1 15 5 "3 4"
# фонари под скатами
for p in "4 5" "13 5" "4 12" "13 12"; do set -- $p; echo "set 7 $1 $2 H"; done; for z in 5 12; do echo "set 10 7 $z H"; echo "set 10 10 $z H"; done
