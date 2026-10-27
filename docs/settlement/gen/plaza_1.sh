cat <<'H'
#!# plaza_1 — площадь, уровень 1 (docs/settlement, catalog-2.md, sketches-wall-plaza.md): центр поселения
#!# этапа 0. Гравий в булыжном бордюре, колодец посередине, четыре факельных столба по углам, доска заданий
#!# (кафедра) у колодца, бочки и цветы.
#!name: "Площадь, уровень 1"
#!type: social
#!stage: 0
#!level: 1
#!size: 15x4x16   # Ш×В×Д
#!entrance: 7,1,2
#!exemptions:
#!  - rule: closed_walls
#!    reason: "площадь открыта: стен нет по замыслу, это мощёный двор с колодцем"
#!  - rule: roof
#!    reason: "площадь под открытым небом по замыслу"
#!legend:
#!  'c': cobblestone
#!  'p': cobblestone_stairs[facing=south,half=bottom]
#!  'g': gravel
#!  'W': water
#!  'L': oak_log[axis=y]
#!  'T': torch
#!  'l': lectern[facing=south]
#!  'b': barrel[facing=up]
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!  'A': potted_azure_bluet
#!layers:
H
echo "size 15 4 16"
echo "label 0 мощение"; echo "label 1 колодец и столбы"
echo "box 0 0 0 14 1 15 c"; echo "box 0 0 1 13 2 14 g"; echo "box 0 0 6 8 0 0 p"
echo "box 0 0 6 8 7 9 c"; echo "set 0 7 8 W"
for p in "6 7" "8 7" "6 9" "8 9"; do set -- $p; echo "set 1 $1 $2 c"; echo "set 2 $1 $2 T"; done
for p in "1 2" "13 2" "1 14" "13 14"; do set -- $p; echo "col 1 2 $1 $2 L"; echo "set 3 $1 $2 T"; done
echo "set 1 7 5 l"; echo "set 1 5 5 b"; echo "set 1 9 5 b"
for p in "5 7 P" "9 7 O" "5 9 A" "9 9 P" "7 11 O" "4 8 A" "10 8 P"; do set -- $p; echo "set 1 $1 $2 $3"; done
