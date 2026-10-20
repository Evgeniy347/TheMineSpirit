source roof.sh
cat <<'H'
#!# family_house_4 — дом семьи, уровень 4 «усадьба» (tasks/47-settlement, catalog.md, style.md «4 нарядно»).
#!# Цоколь из гладкого камня, низ — кирпич, верх — кальцит в каркасе из ободранного тёмного дуба,
#!# вальмовая крыша из глубинной плитки со свесом, карнизом и слуховыми окнами. Вход-арка шириной 4 с двойной
#!# дверью, ступени во всю ширину фасада, над входом — балкон с перилами; потолок 6,
#!# парадная лестница шириной 2; гостиная с камином, столовая, кабинет-библиотека, кухня; наверху спальни
#!# родителей и детей, кладовая, светёлка. Сзади — второй вход, цветы вдоль стены.
#!name: "Дом семьи, уровень 4 (усадьба)"
#!type: housing
#!stage: 4
#!level: 4
#!requires: family_house_3
#!size: 22x21x21   # Ш×В×Д
#!entrance: 10,1,2
#!legend:
#!  'S': smooth_stone
#!  'w': bricks
#!  'K': calcite
#!  'L': stripped_dark_oak_log[axis=y]
#!  'X': stripped_dark_oak_log[axis=x]
#!  'Z': stripped_dark_oak_log[axis=z]
#!  '$': spruce_planks
#!  '#': dark_oak_planks
#!  '1': deepslate_tile_stairs[facing=east,half=bottom]
#!  '2': deepslate_tile_stairs[facing=west,half=bottom]
#!  '3': deepslate_tile_stairs[facing=south,half=bottom]
#!  '4': deepslate_tile_stairs[facing=north,half=bottom]
#!  '5': deepslate_tile_stairs[facing=east,half=top]
#!  '6': deepslate_tile_stairs[facing=west,half=top]
#!  '7': deepslate_tile_stairs[facing=south,half=top]
#!  '8': deepslate_tile_stairs[facing=north,half=top]
#!  '_': deepslate_tile_slab[type=bottom]
#!  'G': glass_pane
#!  'D': dark_oak_door[facing=south,hinge=right]
#!  'E': dark_oak_door[facing=south,hinge=left]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  '<': stone_brick_stairs[facing=east,half=bottom,shape=outer_right]
#!  '>': stone_brick_stairs[facing=west,half=bottom,shape=outer_left]
#!  'A': stone_brick_stairs[facing=north,half=bottom]
#!  'R': spruce_stairs[facing=south,half=bottom]
#!  'g': spruce_stairs[facing=east,half=bottom]
#!  'h': spruce_stairs[facing=west,half=bottom]
#!  'i': spruce_stairs[facing=north,half=bottom]
#!  'U': brick_stairs[facing=west,half=top]
#!  'I': brick_stairs[facing=east,half=top]
#!  'f': campfire[lit=true]
#!  't': dark_oak_fence
#!  'q': dark_oak_pressure_plate
#!  '~': red_carpet
#!  'm': white_carpet
#!  'c': light_gray_carpet
#!  'B': red_bed[facing=south]
#!  'C': blue_bed[facing=south]
#!  'b': barrel[facing=up]
#!  'F': furnace[facing=east]
#!  'k': smoker[facing=east]
#!  'u': water_cauldron[level=3]
#!  'T': crafting_table
#!  'V': chest[facing=north,type=right]
#!  'W': chest[facing=north,type=left]
#!  'j': chest[facing=south,type=right]
#!  'Q': chest[facing=south,type=left]
#!  'O': bookshelf
#!  'l': lectern[facing=north]
#!  'P': potted_poppy
#!  '9': potted_azure_bluet
#!  '0': potted_red_tulip
#!  'H': lantern[hanging=true]
#!  'N': lantern[hanging=false]
#!  'x': wall_torch[facing=east]
#!  'z': wall_torch[facing=west]
#!  's': wall_torch[facing=south]
#!  'n': wall_torch[facing=north]
#!  'd': dark_oak_trapdoor[facing=north,half=bottom,open=true]
#!  'v': dark_oak_trapdoor[facing=west,half=bottom,open=true]
#!  'r': dark_oak_trapdoor[facing=east,half=bottom,open=true]
#!  'e': dark_oak_trapdoor[facing=south,half=bottom,open=true]
#!layers:
H
echo "size 22 21 21"; echo "shift 0 -1"
echo "label 0 цоколь и ступени"; echo "label 1 первый этаж"; echo "label 7 перекрытие и балкон"
echo "label 8 второй этаж"; echo "label 11 обвязка и крыша"
# --- цоколь, пол, ступени во всю ширину ---
echo "box 0 0 1 20 3 20 S"; echo "box 0 0 2 19 4 19 \$"
echo "set 0 9 2 <"; echo "box 0 0 10 11 2 2 p"; echo "set 0 12 2 >"; for x in 2 4 6 15 17 19; do echo "set 0 $x 2 P"; done; echo "box 0 0 10 11 21 21 A"
# --- первый этаж: стены, каркас ---
echo "walls 1 6 1 20 3 20 w"
for x in 1 7 14 20; do echo "col 1 6 $x 3 L"; echo "col 1 6 $x 20 L"; done
for z in 9 14; do echo "col 1 6 1 $z L"; echo "col 1 6 20 $z L"; done
# вход-арка 4: двойная дверь, боковые и верхние стёкла, арка из перевёрнутых ступенек
echo "box 1 2 9 12 3 3 G"; echo "set 1 10 3 D"; echo "set 2 10 3 D"; echo "set 1 11 3 E"; echo "set 2 11 3 E"; echo "box 3 3 9 12 3 3 X"; echo "box 4 4 10 11 3 3 G"
echo "set 4 9 3 U"; echo "set 4 12 3 I"
# задняя двойная дверь
echo "set 1 10 20 D"; echo "set 2 10 20 D"; echo "set 1 11 20 E"; echo "set 2 11 20 E"
# окна 2×3 со ставнями
for y in 2 3 4; do
  for x in 3 4 16 17; do echo "set $y $x 3 G"; done; for x in 4 5 15 16; do echo "set $y $x 20 G"; done
  for z in 15 16; do echo "set $y 1 $z G"; done; for z in 5 6 15 16; do echo "set $y 20 $z G"; done
  for x in 2 5 15 18; do echo "set $y $x 2 d"; done; for x in 3 6 14 17; do echo "set $y $x 21 e"; done
  for z in 14 17; do echo "set $y 0 $z v"; done; for z in 4 7 14 17; do echo "set $y 21 $z r"; done
done
# перегородки первого этажа с проёмами 2
echo "box 1 6 7 7 4 19 w"; echo "box 1 6 14 14 4 19 w"; echo "box 1 6 7 7 5 6 ."; echo "box 1 6 14 14 5 6 ."; echo "box 1 6 14 14 16 17 ."
echo "box 1 6 2 6 12 12 w"; echo "box 1 6 15 19 12 12 w"; echo "box 1 6 4 5 12 12 ."; echo "box 1 6 16 17 12 12 ."
# парадная лестница шириной 2 (x8..9) к тылу, под ней забутовка
for i in 1 2 3 4 5 6 7; do z=$((11+i)); echo "box $i $i 8 9 $z $z R"; [ $i -gt 1 ] && echo "box 1 $((i-1)) 8 9 $z $z w"; done
# гостиная: камин в перегородке, кресла, ковёр, книги, цветы
echo "box 1 1 2 6 10 11 ~"; echo "set 1 3 10 i"; echo "set 1 5 10 i"; echo "box 1 1 2 6 5 9 ~"; echo "set 1 2 8 f"; echo "col 1 3 2 7 w"; echo "col 1 3 2 9 w"; echo "set 1 6 4 O"; echo "set 2 6 4 O"; echo "set 1 2 4 P"; echo "set 1 6 11 b"; echo "set 2 6 11 0"
# столовая: стол на 6, буфет
echo "box 1 1 2 6 13 19 m"
echo "box 1 1 3 5 15 16 t"; echo "box 2 2 3 5 15 16 q"; for x in 3 4 5; do echo "set 1 $x 14 i"; echo "set 1 $x 17 R"; done
echo "set 1 2 19 b"; echo "set 1 3 19 b"; echo "set 2 2 19 P"; echo "set 1 6 19 O"; echo "set 2 6 19 O"; echo "box 1 1 2 6 13 13 m"
# кабинет-библиотека
echo "box 1 3 15 19 11 11 O"; echo "box 1 3 18 19 4 4 O"; echo "set 1 17 8 l"; echo "set 1 16 8 h"; echo "box 1 1 15 18 5 10 c"; echo "set 1 15 4 9"
# кухня
echo "box 1 1 15 18 13 18 m"
echo "set 1 19 13 F"; echo "set 1 19 14 k"; echo "set 1 19 15 k"; echo "set 1 19 16 u"; echo "set 1 19 17 T"; echo "set 1 19 18 b"; echo "set 1 19 19 b"
echo "set 1 15 19 W"; echo "set 1 16 19 V"; echo "set 1 17 19 b"; echo "box 1 1 16 17 14 15 t"; echo "box 2 2 16 17 14 15 q"; echo "set 2 19 19 P"
echo "box 1 1 8 9 4 11 c"; echo "box 1 1 12 13 4 19 c"
# холл: ковровая дорожка, цветы, банкетка
echo "box 1 1 10 11 4 19 ~"; echo "set 1 13 4 P"; echo "set 1 8 4 0"; echo "set 1 13 8 g"; echo "set 1 13 9 g"; echo "set 1 13 10 b"
# свет первого этажа: фонари под перекрытием
for p in "4 7" "4 15" "10 6" "10 13" "17 7" "17 16" "12 18" "3 17" "16 9" "12 6" "8 6" "4 10"; do set -- $p; echo "set 6 $1 $2 H"; done
# --- перекрытие y7 и балкон над входом ---
echo "box 7 7 1 20 3 20 Z"; echo "box 7 7 1 20 3 3 X"; echo "box 7 7 1 20 20 20 X"; echo "box 7 7 2 19 4 19 \$"
for x in 1 20; do for z in 3 20; do echo "set 7 $x $z L"; done; done
echo "box 7 7 8 9 12 17 ."; echo "box 7 7 8 9 18 18 R"
echo "box 7 7 8 13 1 2 #"; echo "box 8 8 8 13 1 1 t"; echo "set 8 8 2 t"; echo "set 8 13 2 t"; echo "set 9 8 2 N"; echo "set 9 13 2 N"
# фонари под балконом
for x in 9 12; do echo "set 6 $x 2 H"; done
# --- второй этаж: стены-фахверк, окна, балконная дверь ---
echo "walls 8 10 1 20 3 20 K"
for x in 1 7 14 20; do echo "col 8 10 $x 3 L"; echo "col 8 10 $x 20 L"; done; for z in 9 14; do echo "col 8 10 1 $z L"; echo "col 8 10 20 $z L"; done
echo "set 8 10 3 D"; echo "set 9 10 3 D"; echo "set 8 11 3 E"; echo "set 9 11 3 E"
for y in 8 9; do
  for x in 3 4 16 17; do echo "set $y $x 3 G"; done; for x in 9 12; do echo "set $y $x 3 G"; done
  for x in 4 5 10 11 15 16; do echo "set $y $x 20 G"; done
  for z in 5 6 15 16; do echo "set $y 1 $z G"; echo "set $y 20 $z G"; done
  for x in 3 6 14 17; do echo "set $y $x 21 e"; done; for x in 2 5 15 18; do echo "set $y $x 2 d"; done
  for z in 4 7 14 17; do echo "set $y 0 $z v"; echo "set $y 21 $z r"; done
done
# перегородки второго этажа, перила у проёма лестницы
echo "box 8 10 7 7 4 19 K"; echo "box 8 10 14 14 4 19 K"; echo "box 8 9 7 7 5 6 ."; echo "box 8 9 7 7 18 19 ."; echo "box 8 9 14 14 5 6 ."; echo "box 8 9 14 14 17 18 ."
echo "box 8 10 2 6 12 12 K"; echo "box 8 10 15 19 12 12 K"
echo "box 8 8 10 10 12 17 t"; echo "box 8 8 8 9 11 11 t"; for z in 12 14 16; do echo "set 9 10 $z P"; done; for z in 13 15 17; do echo "set 9 10 $z 9"; done
echo "box 8 8 8 13 4 10 c"; echo "box 8 8 11 13 12 19 c"
# спальня родителей
echo "set 8 3 10 B"; echo "set 8 3 11 B"; echo "set 8 4 10 B"; echo "set 8 4 11 B"; echo "set 8 2 11 b"; echo "set 9 2 11 P"; echo "set 8 5 11 b"; echo "set 9 5 11 9"
echo "box 8 8 2 6 5 9 ~"; echo "set 8 5 4 j"; echo "set 8 6 4 Q"
# детская
echo "set 8 2 18 C"; echo "set 8 2 19 C"; echo "set 8 4 19 W"; echo "set 8 5 19 V"; echo "box 8 8 2 6 13 17 m"; echo "set 8 2 13 b"; echo "set 9 2 13 0"
# кладовая
echo "set 8 15 4 j"; echo "set 8 16 4 Q"; echo "set 8 18 4 j"; echo "set 8 19 4 Q"; echo "box 8 9 19 19 6 11 b"; echo "box 8 8 15 18 6 10 c"
# светёлка
echo "box 8 9 19 19 13 19 O"; echo "set 8 17 16 l"; echo "set 8 17 15 i"; echo "box 8 8 15 18 13 18 ~"; echo "set 8 15 13 P"
# свет второго этажа
for p in "2 7" "2 17" "15 9" "15 15" "8 9"; do set -- $p; echo "set 9 $1 $2 x"; done
echo "set 9 13 9 z"; echo "set 9 12 19 n"
# --- обвязка, карниз, крыша ---
echo "walls 11 11 1 20 3 20 Z"; echo "box 11 11 1 20 3 3 X"; echo "box 11 11 1 20 20 20 X"; for x in 1 20; do for z in 3 20; do echo "set 11 $x $z L"; done; done
echo "box 10 10 1 20 2 2 7"; echo "box 10 10 1 20 21 21 8"; echo "box 10 10 0 0 3 20 5"; echo "box 10 10 21 21 3 20 6"
hiproof 1 20 3 18 11 1 2 3 4 "#" _
for p in "0 2" "21 2" "0 21" "21 21"; do set -- $p; echo "set 11 $1 $2 ."; done
# слуховые окна на переднем и заднем скатах
for p in "4 5" "19 18"; do set -- $p
  for x in 9 12; do echo "set 13 $x $1 #"; done; for x in 10 11; do echo "set 13 $x $1 G"; echo "set 13 $x $2 ."; done
  echo "box 14 14 9 12 $1 $1 #"; done
# фонари под коньком
for p in "7 12" "14 12" "10 9" "11 14" "8 9" "13 9" "8 14" "13 14" "7 10" "14 10" "7 13" "14 13"; do set -- $p; echo "set 16 $1 $2 H"; done
# --- цветы в горшках вдоль задней стены, ступень у задней двери ---
for x in 2 4 6 8 13 15 17 19; do echo "set 0 $x 21 P"; done; for x in 3 7 14 18; do echo "set 0 $x 21 9"; done
