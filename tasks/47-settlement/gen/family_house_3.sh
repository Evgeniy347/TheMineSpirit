source roof.sh
cat <<'H'
#!# family_house_3 — дом семьи, уровень 3 «двухэтажный дом» (tasks/47-settlement, эскиз sketches-house.md).
#!# Тот же участок и та же дверь-якорь. Низ — камень (булыжник, углы и цоколь из каменного кирпича), потолок 5;
#!# пояс из тёмного дуба, верх — фахверк (берёзовые доски в тёмно-дубовом каркасе). Двойная дверь под балконом,
#!# окна 2×2 со ставнями, камин, кухня и столовая, парадная лестница шириной 2 на второй этаж: спальня родителей,
#!# детская, кабинет. Крыша из тёмного дуба со свесом и карнизом, окна во фронтонах.
#!name: "Дом семьи, уровень 3 (двухэтажный)"
#!type: housing
#!stage: 2
#!level: 3
#!requires: family_house_2
#!size: 16x18x18   # Ш×В×Д
#!entrance: 7,1,1
#!legend:
#!  'S': stone_bricks
#!  'c': cobblestone
#!  '$': spruce_planks
#!  '#': dark_oak_planks
#!  'h': birch_planks
#!  'L': dark_oak_log[axis=y]
#!  'X': stripped_dark_oak_log[axis=x]
#!  'Z': stripped_dark_oak_log[axis=z]
#!  'G': glass_pane
#!  'D': oak_door[facing=south,hinge=left]
#!  'E': oak_door[facing=south,hinge=right]
#!  'j': oak_door[facing=south,hinge=left]
#!  'R': spruce_stairs[facing=south,half=bottom]
#!  'Q': bricks
#!  'f': campfire[lit=true]
#!  'g': spruce_stairs[facing=east,half=bottom]
#!  'a': spruce_stairs[facing=west,half=bottom]
#!  't': oak_fence
#!  'q': oak_pressure_plate
#!  '~': red_carpet
#!  'm': white_carpet
#!  'y': yellow_carpet
#!  'B': red_bed[facing=south]
#!  'b': barrel[facing=up]
#!  'F': furnace[facing=east]
#!  'k': smoker[facing=east]
#!  'u': water_cauldron[level=3]
#!  'T': crafting_table
#!  'V': chest[facing=north,type=right]
#!  'W': chest[facing=north,type=left]
#!  'Y': bookshelf
#!  'l': lectern[facing=north]
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!  'A': potted_azure_bluet
#!  'H': lantern[hanging=true]
#!  'N': wall_torch[facing=north]
#!  's': wall_torch[facing=south]
#!  'x': wall_torch[facing=east]
#!  'z': wall_torch[facing=west]
#!  'd': dark_oak_trapdoor[facing=north,half=bottom,open=true]
#!  'v': dark_oak_trapdoor[facing=west,half=bottom,open=true]
#!  'r': dark_oak_trapdoor[facing=east,half=bottom,open=true]
#!  'n': dark_oak_trapdoor[facing=south,half=bottom,open=true]
#!  'U': dark_oak_stairs[facing=east,half=top]
#!  'I': dark_oak_stairs[facing=west,half=top]
#!  'e': dark_oak_stairs[facing=east,half=bottom]
#!  'w': dark_oak_stairs[facing=west,half=bottom]
#!  '_': dark_oak_slab[type=bottom]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!layers:
H
echo "size 16 18 18"
echo "label 0 цоколь и пол"; echo "label 1 первый этаж"; echo "label 6 перекрытие"; echo "label 7 второй этаж"; echo "label 10 обвязка"
# пол и цоколь
echo "box 0 0 1 14 1 16 S"; echo "box 0 0 2 13 2 15 \$"
echo "set 0 7 0 p"; echo "set 0 8 0 p"
for x in 2 4 11 13; do echo "set 0 $x 0 P"; done; for z in 3 6 11 14; do echo "set 0 0 $z O"; echo "set 0 15 $z A"; done
for x in 2 4 6 9 11 13; do echo "set 0 $x 17 P"; done; for z in 2 8 10 15; do echo "set 0 0 $z A"; echo "set 0 15 $z O"; done
# первый этаж: стены
echo "walls 1 5 1 14 1 16 c"; for x in 1 14; do for z in 1 16; do echo "col 0 5 $x $z S"; done; done
echo "col 1 5 1 9 S"; echo "col 1 5 14 9 S"
echo "set 1 7 1 D"; echo "set 2 7 1 D"; echo "set 1 8 1 E"; echo "set 2 8 1 E"
echo "set 1 4 16 j"; echo "set 2 4 16 j"
for y in 2 3; do for x in 3 4 11 12; do echo "set $y $x 1 G"; done; for z in 4 5 11 12; do echo "set $y 1 $z G"; echo "set $y 14 $z G"; done; echo "set $y 10 16 G"; echo "set $y 11 16 G"; done
# ставни первого этажа
for y in 2 3; do for x in 2 5 10 13; do echo "set $y $x 0 d"; done; for z in 3 6 10 13; do echo "set $y 0 $z v"; echo "set $y 15 $z r"; done; done
# перегородки
echo "box 1 5 2 13 9 9 c"; echo "box 1 5 3 4 9 9 ."; echo "box 1 5 9 10 9 9 ."
echo "box 1 5 7 7 10 15 c"; echo "box 1 5 7 7 12 13 ."
# лестница шириной 2 (x12..13), подъём к тылу, под ней — забутовка
for i in 1 2 3 4 5 6; do z=$((2+i)); echo "box $i $i 12 13 $z $z R"; [ $i -gt 1 ] && echo "box 1 $((i-1)) 12 13 $z $z c"; done
# гостиная: камин, кресла, ковёр, цветы
echo "col 1 3 2 4 Q"; echo "col 1 3 2 6 Q"; echo "set 1 1 5 f"; echo "col 2 5 1 5 Q"
echo "set 1 4 4 g"; echo "set 1 4 6 g"; echo "box 1 1 5 10 3 7 ~"; echo "box 1 1 9 12 10 10 m"; echo "box 1 1 9 12 14 14 m"; echo "set 1 2 2 P"; echo "set 1 2 8 O"; echo "set 1 10 2 A"
echo "set 1 11 8 b"; echo "set 1 10 8 Y"; echo "set 2 10 8 Y"
# кухня
echo "set 1 2 11 F"; echo "set 1 2 12 k"; echo "set 1 2 13 k"; echo "set 1 2 14 u"; echo "set 1 2 15 b"; echo "set 1 3 15 T"; echo "set 1 5 15 b"; echo "set 1 6 15 b"
echo "set 1 5 11 P"; echo "box 1 1 3 6 11 14 m"
# столовая
echo "box 1 1 10 11 11 13 t"; echo "box 2 2 10 11 11 13 q"; for z in 11 12 13; do echo "set 1 9 $z g"; echo "set 1 12 $z a"; done
echo "set 1 12 15 W"; echo "set 1 13 15 V"; echo "set 1 8 15 P"; echo "set 1 13 10 O"
# свет первого этажа: фонари под перекрытием
for p in "5 4" "8 4" "5 7" "4 12" "10 12" "10 6" "7 3"; do set -- $p; echo "set 5 $1 $2 H"; done
# перекрытие (пол второго этажа), проём над лестницей, пояс снаружи
echo "box 6 6 1 14 1 16 Z"; echo "box 6 6 1 14 1 1 X"; echo "box 6 6 1 14 16 16 X"
echo "box 6 6 2 13 2 15 \$"; echo "box 6 6 12 13 3 7 ."; echo "box 6 6 12 13 8 8 R"
for x in 1 14; do for z in 1 16; do echo "set 6 $x $z L"; done; done
# балкон над дверью
echo "box 6 6 5 10 0 0 #"; echo "box 7 7 5 10 0 0 t"
# карниз под балконом и свесом: фонари у двери
echo "set 5 6 0 H"; echo "set 5 9 0 H"
# второй этаж: стены-фахверк
echo "walls 7 9 1 14 1 16 h"; for x in 1 5 10 14; do echo "col 7 9 $x 1 L"; echo "col 7 9 $x 16 L"; done; for z in 9; do echo "col 7 9 1 $z L"; echo "col 7 9 14 $z L"; done
echo "set 7 7 1 D"; echo "set 8 7 1 D"; echo "set 7 8 1 E"; echo "set 8 8 1 E"
for y in 7 8; do for x in 3 4 11 12; do echo "set $y $x 1 G"; echo "set $y $x 16 G"; done; for z in 4 5 12 13; do echo "set $y 1 $z G"; echo "set $y 14 $z G"; done; done
for y in 7 8; do for x in 2 5 10 13; do echo "set $y $x 0 d"; done; for x in 2 5 10 13; do echo "set $y $x 17 n"; done; for z in 3 6 11 14; do echo "set $y 0 $z v"; echo "set $y 15 $z r"; done; done
# второй этаж: перегородки
echo "box 7 9 11 11 2 7 h"; echo "box 7 8 11 11 2 3 ."; echo "box 7 9 2 10 9 9 h"; echo "box 7 8 5 5 9 9 ."; echo "box 7 9 7 7 10 15 h"
# спальня родителей
echo "set 7 3 7 B"; echo "set 7 3 8 B"; echo "set 7 4 7 B"; echo "set 7 4 8 B"; echo "set 7 7 8 W"; echo "set 7 8 8 V"
echo "box 7 7 3 8 3 5 y"; echo "set 7 2 2 P"; echo "set 7 10 2 O"; echo "set 7 10 7 b"; echo "set 8 2 6 x"; echo "set 8 10 4 z"
# детская
echo "set 7 3 14 B"; echo "set 7 3 15 B"; echo "set 7 5 15 W"; echo "set 7 6 15 V"; echo "box 7 7 3 5 11 12 m"; echo "set 7 6 10 A"; echo "set 8 2 10 x"
# кабинет и холл
echo "box 7 9 8 10 15 15 Y"; echo "box 7 8 8 8 13 14 Y"; echo "set 7 8 12 l"; echo "set 7 9 12 a"; echo "box 7 7 12 13 10 14 ~"; echo "set 7 13 15 P"; echo "set 8 13 10 z"; echo "set 8 9 10 s"
echo "set 8 10 2 s"
# обвязка и крыша
echo "walls 10 10 1 14 1 16 Z"; echo "box 10 10 1 14 1 1 X"; echo "box 10 10 1 14 16 16 X"; for x in 1 14; do for z in 1 16; do echo "set 10 $x $z L"; done; done
echo "box 9 9 0 0 1 16 U"; echo "box 9 9 15 15 1 16 I"
roof 1 14 1 16 10 "2 3"
