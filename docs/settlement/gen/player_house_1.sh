source roof.sh
cat <<'H'
#!# player_house_1 — дом игрока, уровень 1 «первый каменный дом» (docs/settlement, sketches-manor.md).
#!# Общее жильё первых духов этапа 0: булыжник в дубовом каркасе, одна просторная комната под коньком,
#!# 4 кровати, верстак, печь, бочка, два двойных сундука, стол со стульями. Окна 1×2 со ставнями, свес,
#!# окно во фронтоне, ступень у двери и факелы снаружи.
#!name: "Дом игрока, уровень 1 (первый каменный дом)"
#!type: housing
#!stage: 0
#!level: 1
#!size: 13x11x15   # Ш×В×Д
#!entrance: 6,1,1
#!legend:
#!  'c': cobblestone
#!  '#': oak_planks
#!  'L': oak_log[axis=y]
#!  'X': oak_log[axis=x]
#!  'Z': oak_log[axis=z]
#!  'e': oak_stairs[facing=east,half=bottom]
#!  'w': oak_stairs[facing=west,half=bottom]
#!  '_': oak_slab[type=bottom]
#!  'G': glass_pane
#!  'D': oak_door[facing=south,hinge=left]
#!  'p': cobblestone_stairs[facing=south,half=bottom]
#!  '<': cobblestone_stairs[facing=east,half=bottom,shape=outer_right]
#!  '>': cobblestone_stairs[facing=west,half=bottom,shape=outer_left]
#!  'F': furnace[facing=south]
#!  'T': crafting_table
#!  'b': barrel[facing=up]
#!  'V': chest[facing=west,type=right]
#!  'W': chest[facing=west,type=left]
#!  'B': red_bed[facing=south]
#!  't': oak_fence
#!  'q': oak_pressure_plate
#!  'g': oak_stairs[facing=east,half=bottom]
#!  'h': oak_stairs[facing=west,half=bottom]
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!  'N': wall_torch[facing=north]
#!  's': wall_torch[facing=south]
#!  'x': wall_torch[facing=east]
#!  'z': wall_torch[facing=west]
#!  'd': oak_trapdoor[facing=north,half=bottom,open=true]
#!  'n': oak_trapdoor[facing=south,half=bottom,open=true]
#!  'v': oak_trapdoor[facing=west,half=bottom,open=true]
#!  'r': oak_trapdoor[facing=east,half=bottom,open=true]
#!layers:
H
echo "size 13 11 15"
echo "label 0 цоколь и пол"; echo "label 1 комната"; echo "label 4 обвязка и свес"
echo "box 0 0 1 11 1 13 c"; echo "box 0 0 2 10 2 12 #"; echo "set 0 5 0 <"; echo "set 0 6 0 p"; echo "set 0 7 0 >"
echo "walls 1 3 1 11 1 13 c"
for x in 1 11; do for z in 1 13; do echo "col 1 3 $x $z L"; done; done
echo "col 1 3 1 7 L"; echo "col 1 3 11 7 L"; echo "col 1 3 6 13 L"; echo "col 1 3 5 1 L"; echo "col 1 3 7 1 L"
echo "set 1 6 1 D"; echo "set 2 6 1 D"; echo "set 3 6 1 X"
for y in 2 3; do
  for x in 3 9; do echo "set $y $x 1 G"; done; for x in 4 8; do echo "set $y $x 13 G"; done
  for z in 4 10; do echo "set $y 1 $z G"; echo "set $y 11 $z G"; done
  for x in 2 4 8 10; do echo "set $y $x 0 d"; done; for x in 3 5 7 9; do echo "set $y $x 14 n"; done
  for z in 3 5 9 11; do echo "set $y 0 $z v"; echo "set $y 12 $z r"; done
done
echo "set 3 5 0 N"; echo "set 3 7 0 N"
# мебель: кухонный угол, сундуки, стол, кровати
echo "set 1 2 2 F"; echo "set 1 3 2 T"; echo "set 1 2 3 b"; echo "set 2 2 3 P"
echo "set 1 10 2 V"; echo "set 1 10 3 W"; echo "set 1 10 8 V"; echo "set 1 10 9 W"
echo "set 1 6 5 t"; echo "set 2 6 5 q"; echo "set 1 5 5 h"; echo "set 1 7 5 g"
for x in 2 4 8 10; do echo "set 1 $x 11 B"; echo "set 1 $x 12 B"; done; echo "set 1 6 12 b"; echo "set 2 6 12 O"
# свет внутри
echo "set 2 2 7 x"; echo "set 2 10 6 z"; echo "set 2 5 12 N"; echo "set 2 4 2 s"; echo "set 2 8 2 s"
# обвязка, карниз, крыша
echo "walls 4 4 1 11 1 13 Z"; echo "box 4 4 1 11 1 1 X"; echo "box 4 4 1 11 13 13 X"; for x in 1 11; do for z in 1 13; do echo "set 4 $x $z L"; done; done
roof 1 11 1 13 4 "3"
