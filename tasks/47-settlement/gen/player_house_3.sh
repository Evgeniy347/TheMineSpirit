source roof.sh
cat <<'H'
#!# player_house_3 — дом игрока, уровень 3 «большой дом» (catalog.md, sketches-manor.md, style.md «3 богато»): этап 3.
#!# Цоколь и стены из каменного кирпича, каркас, пояс и наличники входа из тёмного дуба, крыша из глубинного кирпича со свесом,
#!# карнизом и окнами во фронтонах. Двойная дверь в дубовой раме, широкие ступени, над дверью балкон на
#!# площадь; сзади второй вход. Зал с потолком 5 и колоннадой, люстры на цепях, камин; кабинет-библиотека,
#!# уголок чарования, кухня-столовая; парадная лестница шириной 2; наверху спальня игрока, гостевая, кабинет
#!# и гостиная с выходом на балкон.
#!name: "Дом игрока, уровень 3"
#!type: housing
#!stage: 3
#!level: 3
#!requires: player_house_2
#!size: 22x21x19   # Ш×В×Д
#!entrance: 10,1,1
#!legend:
#!  'S': stone_bricks
#!  'Y': chiseled_stone_bricks
#!  'a': polished_andesite
#!  'L': dark_oak_log[axis=y]
#!  'X': dark_oak_log[axis=x]
#!  'Z': dark_oak_log[axis=z]
#!  '#': dark_oak_planks
#!  '$': spruce_planks
#!  'e': deepslate_brick_stairs[facing=east,half=bottom]
#!  'w': deepslate_brick_stairs[facing=west,half=bottom]
#!  'U': deepslate_brick_stairs[facing=east,half=top]
#!  'I': deepslate_brick_stairs[facing=west,half=top]
#!  '_': deepslate_brick_slab[type=bottom]
#!  'G': glass_pane
#!  'D': dark_oak_door[facing=south,hinge=left]
#!  'E': dark_oak_door[facing=south,hinge=right]
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  'o': stone_brick_stairs[facing=north,half=bottom]
#!  'M': bricks
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
#!  'B': red_bed[facing=south]
#!  'C': blue_bed[facing=south]
#!  'b': barrel[facing=up]
#!  'F': furnace[facing=north]
#!  'k': smoker[facing=north]
#!  'u': water_cauldron[level=3]
#!  'T': crafting_table
#!  'V': chest[facing=north,type=right]
#!  'W': chest[facing=north,type=left]
#!  'J': chest[facing=south,type=right]
#!  'Q': chest[facing=south,type=left]
#!  'O': bookshelf
#!  'l': lectern[facing=north]
#!  'K': enchanting_table
#!  'P': potted_poppy
#!  '9': potted_azure_bluet
#!  '0': potted_red_tulip
#!  'H': lantern[hanging=true]
#!  'N': lantern[hanging=false]
#!  'A': chain[axis=y]
#!  'd': dark_oak_trapdoor[facing=north,half=bottom,open=true]
#!  'n': dark_oak_trapdoor[facing=south,half=bottom,open=true]
#!  'v': dark_oak_trapdoor[facing=west,half=bottom,open=true]
#!  'r': dark_oak_trapdoor[facing=east,half=bottom,open=true]
#!layers:
H
echo "size 22 21 19"
echo "label 0 цоколь, пол, ступени"; echo "label 1 первый этаж"; echo "label 6 перекрытие и балкон"
echo "label 7 второй этаж"; echo "label 10 обвязка и крыша"
# --- цоколь, пол, ступени во всю ширину входа ---
echo "box 0 0 1 20 1 17 S"; echo "box 0 0 2 19 2 16 \$"; echo "box 0 0 7 14 2 16 a"; echo "box 0 0 10 11 2 12 Y"
echo "box 0 0 8 13 0 0 p"; echo "box 0 0 16 17 18 18 o"; for x in 2 4 15 18; do echo "set 0 $x 0 P"; done; for x in 3 5 14 16; do echo "set 0 $x 0 9"; done; for z in 2 7 10 16; do echo "set 0 0 $z 0"; echo "set 0 21 $z P"; done
# --- первый этаж: стены, каркас ---
echo "walls 1 5 1 20 1 17 S"
for x in 1 6 15 20; do echo "col 1 5 $x 1 L"; echo "col 1 5 $x 17 L"; done; for z in 9; do echo "col 1 5 1 $z L"; echo "col 1 5 20 $z L"; done
# вход: двойная дверь, боковые стёкла и фрамуга; задняя двойная дверь
echo "set 1 10 1 D"; echo "set 2 10 1 D"; echo "set 1 11 1 E"; echo "set 2 11 1 E"; echo "box 3 3 10 11 1 1 X"
echo "col 1 3 9 1 L"; echo "col 1 3 12 1 L"; echo "box 4 4 9 12 1 1 X"
echo "set 1 16 17 D"; echo "set 2 16 17 D"; echo "set 1 17 17 E"; echo "set 2 17 17 E"; echo "box 3 3 16 17 17 17 X"
# окна 2×3 со ставнями
for y in 2 3 4; do
  for x in 3 4 16 17; do echo "set $y $x 1 G"; done; for x in 3 4 7 8 13 14; do echo "set $y $x 17 G"; done
  for z in 4 5 12 13; do echo "set $y 1 $z G"; echo "set $y 20 $z G"; done
  for x in 2 5 15 18; do echo "set $y $x 0 d"; done; for x in 2 5 6 9 12 15; do echo "set $y $x 18 n"; done
  for z in 3 6 11 14; do echo "set $y 0 $z v"; echo "set $y 21 $z r"; done
done
# перегородки: крылья слева и справа от зала, проёмы 2×3
echo "box 1 5 6 6 2 16 S"; echo "box 1 5 15 15 2 16 S"
for x in 6 15; do echo "box 1 5 $x $x 4 5 ."; echo "box 1 5 $x $x 12 13 ."; done
echo "box 1 5 2 5 9 9 S"; echo "box 1 5 16 19 9 9 S"
# зал: колоннада, ковровая дорожка, камин у задней стены, кресла
for z in 4 7 10 13; do echo "col 1 5 8 $z L"; echo "col 1 5 13 $z L"; done
echo "box 1 1 10 11 2 12 ~"
echo "box 1 5 9 12 17 17 M"; echo "set 1 10 17 f"; echo "set 1 11 17 f"; echo "col 1 3 9 16 M"; echo "col 1 3 12 16 M"
echo "set 1 9 13 i"; echo "set 1 12 13 i"; echo "box 1 1 10 11 14 15 ~"; echo "set 1 7 16 b"; echo "set 2 7 16 P"; echo "set 1 14 16 b"; echo "set 2 14 16 9"
echo "set 1 7 2 P"; echo "set 1 14 2 0"; echo "set 1 9 2 b"; echo "set 1 12 2 b"
# люстры на цепях в зале
for z in 5 11; do for x in 10 11; do echo "set 5 $x $z A"; echo "set 4 $x $z H"; done; done
for p in "7 8" "14 8" "7 3" "14 3"; do set -- $p; echo "set 5 $1 $2 H"; done
# кабинет-библиотека (левое переднее крыло)
echo "box 1 1 2 5 3 7 c"; echo "box 1 3 2 5 8 8 O"; echo "box 1 1 3 4 4 4 t"; echo "box 2 2 3 4 4 4 q"; echo "set 1 3 5 R"; echo "set 1 4 5 R"
echo "set 1 5 2 l"; echo "set 1 2 2 b"; echo "set 2 2 2 P"; echo "set 5 4 5 H"
# уголок чарования (левое заднее крыло)
echo "box 1 3 2 5 10 10 O"; echo "col 1 3 2 16 O"; echo "col 1 3 5 16 O"; echo "box 1 1 2 5 11 15 j"; echo "set 1 3 13 K"
echo "set 1 2 15 b"; echo "set 2 2 15 0"; echo "set 5 4 13 H"
# кухня-столовая (правое переднее крыло)
echo "set 1 16 8 F"; echo "set 1 17 8 k"; echo "set 1 18 8 u"; echo "set 1 19 8 T"; echo "set 1 19 2 b"; echo "set 2 19 2 P"
echo "box 1 1 17 18 4 5 t"; echo "box 2 2 17 18 4 5 q"; echo "set 1 16 4 h"; echo "set 1 16 5 h"; echo "set 1 19 4 g"; echo "set 1 19 5 g"
echo "set 1 16 2 W"; echo "set 1 17 2 V"; echo "box 1 1 17 18 3 3 m"; echo "set 5 17 6 H"
# правое заднее крыло: парадная лестница шириной 2 (x18..19) к тылу, под ней забутовка; бочки у прохода
for i in 1 2 3 4 5 6; do z=$((10+i)); echo "box $i $i 18 19 $z $z R"; [ $i -gt 1 ] && echo "box 1 $((i-1)) 18 19 $z $z S"; done
echo "box 1 1 16 17 10 16 m"; echo "set 1 16 14 b"; echo "set 1 16 15 b"; echo "set 2 16 15 9"; echo "set 5 16 14 H"
# --- перекрытие y6, балкон над входом ---
echo "box 6 6 1 20 1 17 Z"; echo "box 6 6 1 20 1 1 X"; echo "box 6 6 1 20 17 17 X"; echo "box 6 6 2 19 2 16 \$"
for x in 1 20; do for z in 1 17; do echo "set 6 $x $z L"; done; done
echo "box 6 6 18 19 11 15 ."; echo "box 6 6 18 19 16 16 R"
echo "box 6 6 7 14 0 0 #"; echo "box 7 7 7 14 0 0 t"; echo "set 8 7 0 N"; echo "set 8 14 0 N"
for x in 8 13; do echo "set 5 $x 0 H"; done
# --- второй этаж: стены, балконная дверь, окна 2×2 со ставнями ---
echo "walls 7 9 1 20 1 17 S"
for x in 1 6 15 20; do echo "col 7 9 $x 1 L"; echo "col 7 9 $x 17 L"; done; echo "col 7 9 1 9 L"; echo "col 7 9 20 9 L"
echo "set 7 10 1 D"; echo "set 8 10 1 D"; echo "set 7 11 1 E"; echo "set 8 11 1 E"; echo "box 7 8 9 9 1 1 G"; echo "box 7 8 12 12 1 1 G"
for y in 7 8; do
  for x in 3 4 16 17; do echo "set $y $x 1 G"; echo "set $y $x 17 G"; done; for x in 8 13; do echo "set $y $x 17 G"; done
  for z in 4 5 12 13; do echo "set $y 1 $z G"; echo "set $y 20 $z G"; done
  for x in 2 5 15 18; do echo "set $y $x 0 d"; echo "set $y $x 18 n"; done
  for z in 3 6 11 14; do echo "set $y 0 $z v"; echo "set $y 21 $z r"; done
done
# перегородки второго этажа (доски), проёмы 2×2
echo "box 7 9 6 6 2 16 #"; echo "box 7 9 15 15 2 16 #"; echo "box 7 9 2 5 9 9 #"; echo "box 7 9 16 19 9 9 #"
for x in 6 15; do echo "box 7 8 $x $x 4 5 ."; echo "box 7 8 $x $x 12 13 ."; done
# спальня игрока
echo "set 7 3 7 B"; echo "set 7 3 8 B"; echo "set 7 4 7 B"; echo "set 7 4 8 B"; echo "set 7 2 8 b"; echo "set 8 2 8 P"; echo "set 7 5 8 b"; echo "set 8 5 8 9"
echo "box 7 7 2 5 2 6 ~"; echo "set 7 2 2 J"; echo "set 7 3 2 Q"; 
# гостевая
echo "set 7 3 15 C"; echo "set 7 3 16 C"; echo "set 7 2 16 b"; echo "set 8 2 16 0"; echo "set 7 4 16 W"; echo "set 7 5 16 V"; echo "box 7 7 2 5 11 14 m"
# гостиная с выходом на балкон: кресла у полок, стол, цветы
echo "box 7 8 7 7 7 10 O"; echo "box 7 8 14 14 7 10 O"; echo "box 7 7 9 12 6 11 ~"; echo "box 7 7 10 11 8 9 t"; echo "box 8 8 10 11 8 9 q"
echo "set 7 9 8 h"; echo "set 7 9 9 h"; echo "set 7 12 8 g"; echo "set 7 12 9 g"; echo "set 7 7 2 b"; echo "set 8 7 2 P"; echo "set 7 14 2 b"; echo "set 8 14 2 0"
echo "set 7 7 16 b"; echo "set 8 7 16 9"; echo "set 7 14 16 b"; echo "set 8 14 16 P"; echo "box 7 7 9 12 13 15 c"
# кабинет (правое переднее)
echo "box 7 7 16 19 2 7 c"; echo "box 7 8 16 19 8 8 O"; echo "box 7 7 17 18 4 4 t"; echo "box 8 8 17 18 4 4 q"; echo "set 7 17 5 R"; echo "set 7 18 5 R"; echo "set 7 19 2 l"; echo "set 7 16 2 b"; echo "set 8 16 2 P"
# кладовая у лестницы
echo "set 7 16 10 J"; echo "set 7 17 10 Q"; 
# --- обвязка, карниз, крыша ---
echo "walls 10 10 1 20 1 17 Z"; echo "box 10 10 1 20 1 1 X"; echo "box 10 10 1 20 17 17 X"; for x in 1 20; do for z in 1 17; do echo "set 10 $x $z L"; done; done
echo "box 9 9 0 0 1 17 U"; echo "box 9 9 21 21 1 17 I"
roof 1 20 1 17 10 "3 4 5"
# свет под крышей: фонари под скатами и люстры на цепях над гостиной
for p in "4 5" "17 5" "4 13" "17 13"; do set -- $p; echo "set 12 $1 $2 H"; done
for z in 4 14; do for y in 14 15 16 17 18; do echo "set $y 10 $z A"; done; echo "set 13 10 $z H"; done
