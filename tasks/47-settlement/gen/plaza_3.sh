cat <<'H'
#!# plaza_3 — площадь, уровень 3 (catalog-2.md, sketches-wall-plaza.md «уровень 3»): этап 4. Мощение каменным
#!# кирпичом с кольцом из шлифованного андезита, фонтан в центре (бортик, вода, столб с фонарём), четыре скамьи
#!# лицом к фонтану, клумбы по краям, цветущие азалии по углам, фонарные столбы, колокол и доска заданий;
#!# ступени с главной улицы и широкие ступени к дому игрока сзади.
#!name: "Площадь, уровень 3"
#!type: social
#!stage: 4
#!level: 3
#!requires: plaza_2
#!size: 25x5x26   # Ш×В×Д
#!entrance: 12,1,1
#!exemptions:
#!  - rule: closed_walls
#!    reason: "площадь открыта: стен нет по замыслу, это мощёный двор с фонтаном"
#!  - rule: roof
#!    reason: "площадь под открытым небом по замыслу"
#!legend:
#!  'S': stone_bricks
#!  'C': chiseled_stone_bricks
#!  'a': polished_andesite
#!  'p': stone_brick_stairs[facing=south,half=bottom]
#!  'e': stone_brick_stairs[facing=south,half=bottom]
#!  'M': grass_block
#!  'W': water
#!  't': dark_oak_fence
#!  'N': lantern[hanging=false]
#!  'l': lectern[facing=south]
#!  'b': barrel[facing=up]
#!  'B': bell[attachment=floor,facing=south]
#!  'g': dark_oak_stairs[facing=east,half=bottom]
#!  'h': dark_oak_stairs[facing=west,half=bottom]
#!  'i': dark_oak_stairs[facing=north,half=bottom]
#!  'R': dark_oak_stairs[facing=south,half=bottom]
#!  'z': flowering_azalea
#!  'o': poppy
#!  'A': azure_bluet
#!  'J': cornflower
#!  'O': oxeye_daisy
#!  'U': allium
#!  'Y': red_tulip
#!  'P': potted_poppy
#!  '9': potted_azure_bluet
#!layers:
H
echo "size 25 5 26"
echo "label 0 мощение, клумбы, бортик ступеней"; echo "label 1 фонтан, скамьи, цветы, столбы"
# мощение и ступени: с улицы (z0, ширина 5) и к дому игрока (z25, ширина 7)
echo "box 0 0 0 24 1 24 S"; echo "box 0 0 10 14 0 0 p"; echo "box 0 0 9 15 25 25 e"
# клумбы по краям (кроме проходов улиц), азалии по углам
echo "box 0 0 0 24 1 1 M"; echo "box 0 0 0 24 24 24 M"; echo "box 0 0 0 0 1 24 M"; echo "box 0 0 24 24 1 24 M"
echo "box 0 0 10 14 1 1 S"; echo "box 0 0 9 15 24 24 S"; echo "box 0 0 0 0 11 15 S"; echo "box 0 0 24 24 11 15 S"
flowers=(o A J O U Y); pick() { echo "${flowers[$(( ($1 + $2) % 6 ))]}"; }
for x in $(seq 1 23); do
  [ $x -ge 10 ] && [ $x -le 14 ] || echo "set 1 $x 1 $(pick $x 1)"
  [ $x -ge 9 ] && [ $x -le 15 ] || echo "set 1 $x 24 $(pick $x 24)"
done
for z in $(seq 2 23); do
  [ $z -ge 11 ] && [ $z -le 15 ] && continue; echo "set 1 0 $z $(pick 0 $z)"; echo "set 1 24 $z $(pick 24 $z)"
done
for p in "0 1" "24 1" "0 24" "24 24"; do set -- $p; echo "set 1 $1 $2 z"; done
# кольцо из шлифованного андезита и лучи к проходам
echo "walls 0 0 7 17 8 18 a"; echo "box 0 0 12 12 2 7 a"; echo "box 0 0 12 12 19 23 a"; echo "box 0 0 1 6 13 13 a"; echo "box 0 0 18 23 13 13 a"
# фонтан: бортик, вода, столб с фонарём
echo "walls 1 1 10 14 11 15 a"; echo "box 1 1 11 13 12 14 W"; echo "col 1 2 12 13 S"; echo "set 3 12 13 C"; echo "set 4 12 13 N"
for p in "10 11" "14 11" "10 15" "14 15"; do set -- $p; echo "set 2 $1 $2 P"; done
# скамьи лицом к фонтану (спинкой наружу)
echo "box 1 1 11 13 9 9 i"; echo "box 1 1 11 13 17 17 R"; echo "box 1 1 8 8 12 14 h"; echo "box 1 1 16 16 12 14 g"
# фонарные столбы: по углам кольца и вдоль краёв
for p in "7 8" "17 8" "7 18" "17 18" "8 2" "16 2" "1 9" "23 9" "1 17" "23 17" "9 23" "15 23"; do set -- $p
  echo "set 1 $1 $2 C"; echo "set 2 $1 $2 t"; echo "set 3 $1 $2 N"; done
# доска заданий у входа с улицы, бочки, колокол на столбе у ступеней к дому
echo "set 1 12 4 l"; echo "set 1 10 4 b"; echo "set 1 14 4 b"; echo "set 2 10 4 9"; echo "set 2 14 4 P"
echo "set 1 12 21 C"; echo "set 2 12 21 B"
# цветы в горшках у скамей
for p in "4 4" "20 4" "4 21" "20 21"; do set -- $p; echo "set 1 $1 $2 b"; echo "set 2 $1 $2 P"; done
