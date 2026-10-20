source roof.sh
cat <<'H'
#!# player_house_4 — дом игрока, уровень 4 «особняк» (catalog.md, sketches-manor.md, style.md «4 нарядно»): этап 4.
#!# Цоколь из гладкого камня, низ — кальцит, верх — кирпич в каркасе из ободранного тёмного дуба, вальмовая крыша
#!# из глубинной плитки со свесом, карнизом и слуховыми окнами. Портал на четыре створки с витражом над дверьми и
#!# ступенями во всю ширину, над ним балкон; сзади вход в кухню. Зал на два этажа с галереей на колоннах, середина
#!# открыта до крыши, люстры на цепях, две парадные лестницы шириной 2. Внизу библиотека,
#!# чаровальня, столовая на 12 мест с камином и кухня; наверху спальня игрока, кабинет, гостевая и детская.
#!name: "Дом игрока, уровень 4 (особняк)"
#!type: housing
#!stage: 4
#!level: 4
#!requires: player_house_3
#!size: 24x22x23   # Ш×В×Д
#!entrance: 10,1,2
#!legend:
#!  'S': smooth_stone
#!  'K': calcite
#!  'w': bricks
#!  'M': bricks
#!  'L': stripped_dark_oak_log[axis=y]
#!  'X': stripped_dark_oak_log[axis=x]
#!  'Z': stripped_dark_oak_log[axis=z]
#!  '#': dark_oak_planks
#!  'a': polished_andesite
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
#!  'y': yellow_stained_glass_pane
#!  'Y': yellow_stained_glass
#!  'o': orange_stained_glass_pane
#!  'D': dark_oak_door[facing=south,hinge=right]
#!  'E': dark_oak_door[facing=south,hinge=left]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  '<': stone_brick_stairs[facing=east,half=bottom,shape=outer_right]
#!  '>': stone_brick_stairs[facing=west,half=bottom,shape=outer_left]
#!  'A': stone_brick_stairs[facing=north,half=bottom]
#!  'f': campfire[lit=true]
#!  'g': dark_oak_stairs[facing=east,half=bottom]
#!  'h': dark_oak_stairs[facing=west,half=bottom]
#!  'i': dark_oak_stairs[facing=north,half=bottom]
#!  'R': dark_oak_stairs[facing=south,half=bottom]
#!  't': dark_oak_fence
#!  'q': dark_oak_pressure_plate
#!  '~': red_carpet
#!  'm': white_carpet
#!  'c': light_gray_carpet
#!  'j': purple_carpet
#!  'n': brown_carpet
#!  'B': red_bed[facing=south]
#!  'C': blue_bed[facing=south]
#!  'b': barrel[facing=up]
#!  'F': furnace[facing=west]
#!  'k': smoker[facing=west]
#!  'u': water_cauldron[level=3]
#!  'T': crafting_table
#!  'V': chest[facing=north,type=right]
#!  'W': chest[facing=north,type=left]
#!  'J': chest[facing=south,type=right]
#!  'Q': chest[facing=south,type=left]
#!  'O': bookshelf
#!  'l': lectern[facing=north]
#!  'e': enchanting_table
#!  'P': potted_poppy
#!  '9': potted_azure_bluet
#!  '0': potted_red_tulip
#!  'H': lantern[hanging=true]
#!  'N': lantern[hanging=false]
#!  'z': chain[axis=y]
#!  'd': dark_oak_trapdoor[facing=north,half=bottom,open=true]
#!  's': dark_oak_trapdoor[facing=south,half=bottom,open=true]
#!  'v': dark_oak_trapdoor[facing=west,half=bottom,open=true]
#!  'r': dark_oak_trapdoor[facing=east,half=bottom,open=true]
#!layers:
H
echo "size 24 22 23"; echo "shift 0 1"
echo "label 0 цоколь, ступени, цветы и фонари у входов"; echo "label 1 первый этаж"; echo "label 7 галерея и перекрытие"
echo "label 8 второй этаж"; echo "label 11 обвязка и крыша"
# --- цоколь, пол, ступени портала, цветы в горшках вокруг дома (с проходами), фонари у ступеней ---
echo "box 0 0 1 22 1 20 S"; echo "box 0 0 2 21 2 19 #"; echo "box 0 0 8 15 2 19 a"
echo "set 0 9 0 <"; echo "box 0 0 10 13 0 0 p"; echo "set 0 14 0 >"; echo "box 0 0 19 20 21 21 A"
for x in 2 4 6 17 19 21; do echo "set 0 $x 0 P"; done; for x in 3 5 18 20; do echo "set 0 $x 0 9"; done; for x in 7 16; do echo "set 0 $x 0 N"; done
for x in 2 4 6 9 14 16; do echo "set 0 $x 21 9"; done; for x in 3 5 10 13; do echo "set 0 $x 21 0"; done; for x in 18 21; do echo "set 0 $x 21 N"; done
for z in 3 6 10 14 17; do echo "set 0 0 $z 0"; echo "set 0 23 $z P"; done; for z in 4 7 13 16; do echo "set 0 0 $z P"; echo "set 0 23 $z 9"; done
# --- первый этаж: стены из кальцита, каркас ---
echo "walls 1 6 1 22 1 20 K"
for x in 1 7 9 14 16 22; do echo "col 1 6 $x 1 L"; done; for x in 1 7 16 22; do echo "col 1 6 $x 20 L"; done; for z in 9; do echo "col 1 6 1 $z L"; echo "col 1 6 22 $z L"; done
# портал: четыре створки, перемычка, витраж над дверьми
echo "set 1 10 1 D"; echo "set 2 10 1 D"; echo "set 1 11 1 E"; echo "set 2 11 1 E"; echo "set 1 12 1 D"; echo "set 2 12 1 D"; echo "set 1 13 1 E"; echo "set 2 13 1 E"
echo "box 3 3 10 13 1 1 X"; echo "set 4 10 1 o"; echo "set 4 13 1 o"; echo "box 4 4 11 12 1 1 y"
# задняя дверь кухни
echo "set 1 19 20 D"; echo "set 2 19 20 D"; echo "set 1 20 20 E"; echo "set 2 20 20 E"; echo "box 3 3 19 20 20 20 X"
# окна 3×3 и 2×3, высокие витражи зала; ставни
for y in 2 3 4; do
  for x in 3 4 19 20; do echo "set $y $x 1 G"; done; for x in 3 4 8 9 14 15; do echo "set $y $x 20 G"; done
  for z in 12 13 16 17; do echo "set $y 1 $z G"; echo "set $y 22 $z G"; done
  for x in 2 5 18 21; do echo "set $y $x 0 d"; done; for x in 2 5 7 10 13 16; do echo "set $y $x 21 s"; done
  for z in 11 14 15 18; do echo "set $y 0 $z v"; echo "set $y 23 $z r"; done
done
for y in 2 3 4 5; do echo "set $y 8 1 y"; echo "set $y 15 1 y"; done
# перегородки крыльев с проёмами во всю высоту
echo "box 1 6 7 7 2 19 K"; echo "box 1 6 16 16 2 19 K"; echo "box 1 6 2 6 9 9 K"; echo "box 1 6 17 21 9 9 K"
for x in 7 16; do echo "box 1 6 $x $x 4 5 ."; echo "box 1 6 $x $x 10 11 ."; done
# зал: ковры, две парадные лестницы шириной 2, кирпичная грудь в задней стене
echo "box 1 1 8 15 2 19 c"; echo "box 1 1 11 12 2 16 ~"
for i in 1 2 3 4 5 6 7; do z=$((19-i)); for x0 in 8 14; do echo "box $i $i $x0 $((x0+1)) $z $z i"; [ $i -gt 1 ] && echo "box 1 $((i-1)) $x0 $((x0+1)) $z $z #"; done; done
echo "box 1 6 10 13 20 20 M"
echo "box 1 1 11 12 17 19 ~"
echo "set 1 8 2 b"; echo "set 2 8 2 P"; echo "set 1 15 2 b"; echo "set 2 15 2 9"; echo "set 1 9 2 0"; echo "set 1 14 2 P"
echo "set 1 8 11 b"; echo "set 2 8 11 9"; echo "set 1 15 11 b"; echo "set 2 15 11 0"
# библиотека (левое переднее крыло)
echo "box 1 1 3 6 2 7 n"; echo "box 1 4 2 2 2 8 O"; echo "box 1 4 3 6 8 8 O"
echo "box 1 1 4 5 4 4 t"; echo "box 2 2 4 5 4 4 q"; echo "set 1 4 5 R"; echo "set 1 5 5 R"; echo "set 1 3 2 l"; echo "set 1 6 2 b"; echo "set 2 6 2 P"
# чаровальня (правое переднее крыло)
echo "box 1 1 17 20 2 7 j"; echo "box 1 2 21 21 2 8 O"; echo "box 1 2 17 20 8 8 O"; echo "box 1 2 17 17 2 3 O"; echo "box 1 2 17 17 6 7 O"
echo "set 1 19 5 e"; echo "set 1 20 2 b"; echo "set 2 20 2 0"
# столовая на 12 мест (левое заднее крыло)
echo "box 1 1 2 6 10 19 m"; echo "box 1 1 4 4 12 17 t"; echo "box 2 2 4 4 12 17 q"
for z in 12 13 14 15 16 17; do echo "set 1 3 $z h"; echo "set 1 5 $z g"; done
echo "set 1 2 19 b"; echo "set 2 2 19 P"; echo "set 1 6 19 b"; echo "set 2 6 19 9"; echo "set 1 2 10 b"; echo "set 2 2 10 0"
# камин столовой: костёр внутри, кирпичная грудь в задней стене
echo "box 1 6 3 5 20 20 M"; echo "col 1 3 3 19 M"; echo "col 1 3 5 19 M"; echo "set 1 4 19 f"
# кухня (правое заднее крыло)
echo "box 1 1 17 21 10 19 n"
echo "set 1 21 10 F"; echo "set 1 21 11 k"; echo "set 1 21 12 k"; echo "set 1 21 13 u"; echo "set 1 21 14 T"; echo "set 1 21 15 b"; echo "set 1 21 18 b"; echo "set 1 21 19 b"; echo "set 2 21 19 P"
echo "set 1 17 19 W"; echo "set 1 18 19 V"; echo "box 1 1 18 19 14 15 t"; echo "box 2 2 18 19 14 15 q"; echo "set 1 18 13 i"; echo "set 1 19 16 R"
# свет первого этажа: фонари под перекрытием и под балконом
for p in "4 4" "4 14" "19 4" "19 14" "4 17" "19 17" "9 6" "14 6" "9 10" "14 10"; do set -- $p; echo "set 6 $1 $2 H"; done
for x in 9 14; do echo "set 6 $x 0 H"; done
# --- перекрытие y7: галерея вдоль зала на колоннах, мостик к балкону, балкон ---
echo "box 7 7 1 22 1 20 Z"; echo "box 7 7 1 22 1 1 X"; echo "box 7 7 1 22 20 20 X"; echo "box 7 7 2 21 2 19 #"
for x in 1 22; do for z in 1 20; do echo "set 7 $x $z L"; done; done
echo "box 7 7 11 12 5 19 ."; echo "box 7 7 8 10 12 19 ."; echo "box 7 7 13 15 12 19 ."; echo "box 7 7 8 9 12 12 i"; echo "box 7 7 14 15 12 12 i"
echo "box 8 8 10 10 4 11 t"; echo "box 8 8 13 13 4 11 t"; echo "box 8 8 11 12 4 4 t"
echo "box 7 7 8 15 -1 0 #"; echo "box 8 8 8 15 -1 -1 t"; echo "set 8 8 0 t"; echo "set 8 15 0 t"; echo "set 9 8 0 N"; echo "set 9 15 0 N"
# --- второй этаж: кирпичные стены в каркасе, балконная дверь, окна ---
echo "walls 8 10 1 22 1 20 w"
for x in 1 7 16 22; do echo "col 8 10 $x 1 L"; echo "col 8 10 $x 20 L"; done; echo "col 8 10 1 9 L"; echo "col 8 10 22 9 L"
echo "set 8 11 1 D"; echo "set 9 11 1 D"; echo "set 8 12 1 E"; echo "set 9 12 1 E"; echo "box 8 9 10 10 1 1 Y"; echo "box 8 9 13 13 1 1 Y"
for y in 8 9; do
  for x in 3 4 19 20; do echo "set $y $x 1 G"; echo "set $y $x 20 G"; done; for x in 8 9 14 15; do echo "set $y $x 20 G"; done
  for z in 4 5 12 13 16 17; do echo "set $y 1 $z G"; echo "set $y 22 $z G"; done
  for x in 2 5 18 21; do echo "set $y $x 0 d"; echo "set $y $x 21 s"; done; for x in 7 10 13 16; do echo "set $y $x 21 s"; done
  for z in 3 6 11 14 15 18; do echo "set $y 0 $z v"; echo "set $y 23 $z r"; done
done
for y in 8 9; do echo "set $y 8 1 o"; echo "set $y 15 1 o"; done
# перегородки второго этажа с проёмами с галереи
echo "box 8 10 7 7 2 19 #"; echo "box 8 10 16 16 2 19 #"; echo "box 8 10 2 6 9 9 #"; echo "box 8 10 17 21 9 9 #"
for x in 7 16; do echo "box 8 9 $x $x 4 5 ."; echo "box 8 9 $x $x 10 11 ."; done
# галерея: ковёр, цветы, кресла у перил
echo "box 8 8 8 9 2 11 ~"; echo "box 8 8 14 15 2 11 ~"; echo "box 8 8 10 13 2 3 ~"; echo "set 8 8 2 b"; echo "set 9 8 2 P"; echo "set 8 15 2 b"; echo "set 9 15 2 9"
# спальня игрока
echo "box 8 8 2 6 2 7 ~"; echo "set 8 3 7 B"; echo "set 8 3 8 B"; echo "set 8 4 7 B"; echo "set 8 4 8 B"; echo "set 8 2 8 b"; echo "set 9 2 8 P"; echo "set 8 5 8 b"; echo "set 9 5 8 0"
echo "set 8 2 2 J"; echo "set 8 3 2 Q"; echo "set 8 6 2 b"; echo "set 9 6 2 9"
# гостевая
echo "box 8 8 2 6 10 17 m"; echo "set 8 3 18 C"; echo "set 8 3 19 C"; echo "set 8 2 19 b"; echo "set 9 2 19 P"; echo "set 8 5 19 W"; echo "set 8 6 19 V"
echo "box 8 9 2 2 10 11 O"; echo "box 8 8 4 5 13 13 t"; echo "box 9 9 4 5 13 13 q"; echo "set 8 4 14 R"; echo "set 8 5 14 R"
# кабинет
echo "box 8 8 17 21 2 7 n"; echo "box 8 9 21 21 2 3 O"; echo "box 8 9 21 21 7 8 O"; echo "box 8 9 17 20 8 8 O"
echo "box 8 8 18 19 4 4 t"; echo "box 9 9 18 19 4 4 q"; echo "set 8 18 5 R"; echo "set 8 19 5 R"; echo "set 8 20 2 l"; echo "set 8 17 2 b"; echo "set 9 17 2 0"
# детская
echo "box 8 8 17 21 10 17 c"; echo "set 8 18 18 C"; echo "set 8 18 19 C"; echo "set 8 20 18 C"; echo "set 8 20 19 C"; echo "set 8 19 19 b"; echo "set 9 19 19 9"
echo "set 8 20 10 J"; echo "set 8 21 10 Q"; echo "set 8 21 13 b"; echo "set 9 21 13 P"
# --- обвязка, карниз, вальмовая крыша, слуховые окна ---
echo "walls 11 11 1 22 1 20 Z"; echo "box 11 11 1 22 1 1 X"; echo "box 11 11 1 22 20 20 X"; for x in 1 22; do for z in 1 20; do echo "set 11 $x $z L"; done; done
echo "box 10 10 1 22 0 0 7"; echo "box 10 10 1 22 21 21 8"; echo "box 10 10 0 0 1 20 5"; echo "box 10 10 23 23 1 20 6"
hiproof 1 22 1 20 11 1 2 3 4 "#" _
for p in "0 0" "23 0" "0 21" "23 21"; do set -- $p; echo "set 11 $1 $2 ."; done
for p in "2 3" "19 18"; do set -- $p
  for x in 9 14; do echo "set 13 $x $1 #"; done; for x in 10 11 12 13; do echo "set 13 $x $1 G"; echo "set 13 $x $2 ."; done
  echo "box 14 14 9 14 $1 $1 #"; done
# фонари на слуховых окнах и у задней двери
for z in 1 20; do echo "box 14 14 9 14 $z $z #"; echo "set 13 9 $z H"; echo "set 13 14 $z H"; done; 
# свет под крышей: фонари под подкладкой, люстры на цепях над серединой зала
for p in "4 6" "4 14" "19 6" "19 14"; do set -- $p; echo "set 13 $1 $2 H"; done
for x in 11 12; do for y in 14 15 16 17 18 19; do echo "set $y $x 10 z"; done; echo "set 13 $x 10 H"; done
for x in 11 12; do for y in 14 15; do echo "set $y $x 6 z"; done; echo "set 13 $x 6 H"; done
