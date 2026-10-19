cat <<'H'
#!# plaza_2 — площадь, уровень 2 (catalog-2.md, sketches-wall-plaza.md): мощение булыжником с узором из каменного
#!# кирпича, колодец в центре, четыре скамьи лицом к колодцу, клумбы, фонари на столбах по углам, колокол на столбе
#!# и доска заданий.
#!name: "Площадь, уровень 2"
#!type: social
#!stage: 2
#!level: 2
#!requires: plaza_1
#!size: 19x4x20   # Ш×В×Д
#!entrance: 9,1,2
#!exemptions:
#!  - rule: closed_walls
#!    reason: "площадь открыта: стен нет по замыслу, это мощёный двор с колодцем"
#!  - rule: roof
#!    reason: "площадь под открытым небом по замыслу"
#!legend:
#!  'c': cobblestone
#!  'S': stone_bricks
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  'M': grass_block
#!  'W': water
#!  'L': stripped_spruce_log[axis=y]
#!  't': spruce_fence
#!  'N': lantern[hanging=false]
#!  'T': torch
#!  'l': lectern[facing=south]
#!  'b': barrel[facing=up]
#!  'B': bell[attachment=floor,facing=south]
#!  'g': spruce_stairs[facing=east,half=bottom]
#!  'h': spruce_stairs[facing=west,half=bottom]
#!  'i': spruce_stairs[facing=north,half=bottom]
#!  'R': spruce_stairs[facing=south,half=bottom]
#!  'o': poppy
#!  'A': azure_bluet
#!  'J': cornflower
#!  'O': oxeye_daisy
#!  'P': potted_poppy
#!layers:
H
echo "size 19 4 20"
echo "label 0 мощение"; echo "label 1 колодец, скамьи и столбы"
echo "box 0 0 0 18 1 19 S"; echo "box 0 0 1 17 2 18 c"; echo "box 0 0 7 11 0 0 p"
# узор: кольцо и диагональные дорожки из каменного кирпича
echo "walls 0 0 4 14 5 15 S"; for i in 0 1 2; do echo "set 0 $((2+i)) $((3+i)) S"; echo "set 0 $((16-i)) $((3+i)) S"; echo "set 0 $((2+i)) $((17-i)) S"; echo "set 0 $((16-i)) $((17-i)) S"; done
# колодец
echo "box 0 0 8 10 9 11 S"; echo "set 0 9 10 W"
for p in "8 9" "10 9" "8 11" "10 11"; do set -- $p; echo "set 1 $1 $2 S"; echo "set 2 $1 $2 T"; done
# клумбы у колодца
for p in "2 6" "15 6" "2 13" "15 13"; do set -- $p; echo "box 0 0 $1 $(( $1 + 1 )) $2 $(( $2 + 1 )) M"; echo "set 1 $1 $2 o"; echo "set 1 $(( $1 + 1 )) $2 A"; echo "set 1 $1 $(( $2 + 1 )) J"; echo "set 1 $(( $1 + 1 )) $(( $2 + 1 )) O"; done
# скамьи лицом к колодцу, по торцам — люки
echo "box 1 1 8 10 6 6 i"
echo "box 1 1 8 10 14 14 R"
echo "box 1 1 5 5 9 11 h"
echo "box 1 1 13 13 9 11 g"
# фонари на столбах по углам, колокол и доска заданий
for p in "1 2" "17 2" "1 18" "17 18"; do set -- $p; echo "set 1 $1 $2 L"; echo "set 2 $1 $2 t"; echo "set 3 $1 $2 N"; done
echo "set 1 4 4 L"; echo "set 2 4 4 B"
echo "set 1 9 4 l"; echo "set 1 7 4 b"; echo "set 1 11 4 b"
# цветы в горшках вдоль краёв площади
for x in 3 6 12 15; do echo "set 1 $x 17 P"; done; for z in 6 14; do echo "set 1 2 $z P"; echo "set 1 16 $z P"; done; echo "set 1 14 4 b"; echo "set 2 14 4 P"
