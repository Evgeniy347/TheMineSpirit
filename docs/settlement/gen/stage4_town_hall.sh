#!/usr/bin/env bash
# town_hall_<level> — ратуша (catalog-2.md): зал совета, архив, звонница; уровни 1–4.
# Запуск: bash stage4_town_hall.sh <level> | awk -f grid.awk > ../.../town_hall_<level>.txt
set -euo pipefail
source "$(dirname "$0")/stage4lib.sh"
level=${1:?уровень 1..4}
case $level in
1) W=11; D=13; H=5 ;;
2) W=13; D=15; H=6 ;;
3) W=15; D=17; H=7 ;;
4) W=19; D=21; H=8 ;;
*) echo "уровень 1..4" >&2; exit 2 ;;
esac
X=$((W + 2)); Z=$((D + 2))
minwd=$W; [ "$D" -lt "$minwd" ] && minwd=$D
rooflayers=$(((minwd - 1) / 2 + 1))
roof_used=$rooflayers; [ $roof_used -gt 4 ] && roof_used=4; Y=$((H + 1 + roof_used))
ex=$((1 + W / 2))
if [ "$level" -le 2 ]; then
  wall=stone_bricks; plinth=polished_andesite; stonestair=polished_andesite_stairs; stair=deepslate_brick_stairs
  stage=3
else
  wall=bricks; plinth=smooth_stone; stonestair=smooth_stone_stairs; stair=deepslate_tile_stairs
  stage=4
fi
roofslab=${stair%_stairs}_slab
name_extra=( "" "зал совета" "зал с архивом" "двухзальный дом совета" "ратуша с колонным залом" )
cat <<H
#!# town_hall_$level — ратуша, уровень $level (catalog-2.md, style.md): ${name_extra[$level]}.
#!# Цоколь и пол, каркас тёмного дуба, крыша из глубинного сланца со свесом и слуховыми окнами;
#!# внутри — зал совета, длинный стол, кресла, кафедра-архив, колокол, свет без тёмных клеток.
#!name: "Ратуша, уровень $level (${name_extra[$level]})"
#!type: social
#!stage: $stage
#!level: $level
H
if [ "$level" -gt 1 ]; then echo "#!requires: town_hall_$((level - 1))"; fi
cat <<H
#!size: ${X}x${Y}x${Z}   # Ш×В×Д
#!entrance: $ex,1,1
#!legend:
#!  'c': $plinth
#!  'S': $wall
#!  'a': polished_andesite
#!  'L': dark_oak_log[axis=y]
#!  '#': dark_oak_planks
#!  'G': glass
#!  'D': dark_oak_door[facing=south,hinge=left]
#!  'p': ${stonestair}[facing=south,half=bottom]
#!  '1': ${stair}[facing=east,half=bottom]
#!  '2': ${stair}[facing=west,half=bottom]
#!  '3': ${stair}[facing=south,half=bottom]
#!  '4': ${stair}[facing=north,half=bottom]
#!  '_': ${roofslab}[type=bottom]
#!  't': dark_oak_fence
#!  'q': dark_oak_pressure_plate
#!  'e': dark_oak_stairs[facing=east,half=bottom]
#!  'w': dark_oak_stairs[facing=west,half=bottom]
#!  'n': dark_oak_stairs[facing=north,half=bottom]
#!  's': dark_oak_stairs[facing=south,half=bottom]
#!  '~': red_carpet
#!  'm': white_carpet
#!  'B': bell[attachment=floor,facing=south]
#!  'M': cartography_table
#!  'Q': bookshelf
#!  'l': lectern[facing=south]
#!  'C': chest[facing=south]
#!  'b': barrel[facing=up]
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!  'A': potted_azure_bluet
#!  'v': oak_leaves
#!  'z': chain[axis=y]
#!  'H': lantern[hanging=true]
#!layers:
H
echo "size $X $Y $Z"
echo "label 0 цоколь и пол"; echo "label 1 зал совета"
# пол, стены, потолок, крыша
echo "box 0 0 0 $((X - 1)) 0 $((Z - 1)) a"
echo "walls 1 $((H - 1)) 1 $W 1 $D S"
echo "box $H $H 1 $W 1 $D #"
eave_ring 1 $W 1 $D $H "#"
attic_roof 1 $W 1 $D $((H + 1)) "#" 1 2 3 4 4
eave_decor 1 $W 1 $D $((H + 1)) t v
frame_columns 1 $W 1 $D $((H - 1)) L
echo "set 1 $ex 1 D"; echo "set 2 $ex 1 D"; echo "set 0 $ex 0 p"
# окна по уровню
win() { local y1=$1 y2=$2; shift 2; local x z
  case $1 in
    front) shift; for x in "$@"; do echo "box $y1 $y2 $x $x 1 1 G"; done ;;
    back) shift; for x in "$@"; do echo "box $y1 $y2 $x $x $D $D G"; done ;;
    side) shift; for z in "$@"; do echo "box $y1 $y2 1 1 $z $z G"; echo "box $y1 $y2 $W $W $z $z G"; done ;;
  esac; }
case $level in
1) win 2 2 front 3 8; win 2 2 back 3 8; win 2 2 side 3 11 ;;
2) win 2 3 front 4 10; win 2 3 back 4 10; win 2 3 side 4 12 ;;
3) win 2 3 front 3 4 11 12; win 2 3 back 3 4 11 12; win 2 3 side 3 4 13 14 ;;
4) win 2 3 front 6 7 8 14 15 16; win 2 3 back 6 7 8 14 15 16
   win 2 3 side 6 7 8 14 15 16 ;;
esac
# ковры
echo "box 1 1 2 $((W - 1)) 2 $((D - 1)) ~"
if [ "$level" -ge 2 ]; then
  echo "box 1 1 4 $((W - 3)) 3 $((D - 2)) m"
fi
# длинный стол совета и кресла
t0=4; t1=$((D - 5))
if [ "$level" -eq 1 ]; then t1=$((D - 5)); fi
for z in $(seq $t0 $t1); do echo "set 1 4 $z t"; echo "set 2 4 $z q"; done
for z in $(seq $((t0 + 1)) $((t1 - 1))); do echo "set 1 3 $z e"; echo "set 1 5 $z w"; done
# кафедра, архив, колокол, сундук, бочки, цветы
echo "set 1 2 2 M"; echo "set 1 2 3 b"; echo "set 1 2 4 b"
echo "set 1 $((W - 1)) 3 C"; echo "set 1 $((W - 2)) 2 Q"; echo "set 1 $((W - 1)) 4 Q"
echo "set 1 $((W - 1)) $((D - 1)) B"
echo "set 1 2 $((D - 1)) P"; echo "set 1 $((W - 1)) $((D - 2)) O"
if [ "$level" -ge 2 ]; then
  # перегородка с проёмом шириной 2: передний зал и архив
  pz=$((D - 6)); gap=$((ex - 1))
  echo "box 1 $((H - 1)) 3 $((gap - 1)) $pz $pz S"
  echo "box 1 $((H - 1)) $((gap + 2)) $((W - 2)) $pz $pz S"
  echo "box 1 1 $((W - 3)) $((W - 2)) $((pz - 1)) $((pz - 1)) t"; echo "set 2 $((W - 3)) $((pz - 1)) q"; echo "set 2 $((W - 2)) $((pz - 1)) q"
  echo "box 1 3 3 $((W - 2)) $((D - 1)) $((D - 1)) Q"
  echo "set 1 $((gap - 2)) $((D - 2)) l"; echo "set 1 $((gap + 3)) $((D - 2)) l"
  echo "set 1 2 $((D - 2)) C"; echo "set 1 $((W - 1)) $((D - 3)) b"; echo "set 1 3 $((D - 2)) P"
fi
if [ "$level" -ge 3 ]; then
  # второй стол у стены
  echo "box 1 1 $((W - 4)) $((W - 3)) 4 7 t"; echo "box 2 2 $((W - 4)) $((W - 3)) 4 7 q"
  echo "set 1 $((W - 5)) 4 n"; echo "set 1 $((W - 2)) 4 n"
  echo "set 1 3 $((D - 2)) B"
fi
if [ "$level" -ge 4 ]; then
  # парадная арка: створки по краям, простенки из кальцита
  echo "set 3 $((ex - 2)) 1 S"; echo "set 3 $((ex + 2)) 1 S"
  echo "set 3 $((ex - 1)) 1 G"; echo "set 3 $((ex + 1)) 1 G"
  echo "set 4 $((ex - 1)) 1 G"; echo "set 4 $((ex + 1)) 1 G"
  # люстры в зале и галерея-балкон вдоль стен
  for z in 5 $((D / 2)) $((D - 5)); do echo "set $((H - 1)) 4 $z H"; echo "set $((H - 1)) $((W - 3)) $z H"; done
  echo "box 1 4 5 5 9 12 Q"; echo "box 1 4 5 5 16 $((D - 2)) Q"; echo "box 1 4 $((W - 4)) $((W - 4)) 9 12 Q"; echo "box 1 4 $((W - 4)) $((W - 4)) 16 $((D - 2)) Q"
  echo "box 1 2 2 $((ex - 2)) 2 2 Q"; echo "box 1 2 $((ex + 2)) $((W - 1)) 2 2 Q"
  echo "box 1 2 2 $((W - 1)) $((D - 1)) $((D - 1)) Q"
  echo "box 3 3 2 $((ex - 2)) 2 2 q"; echo "box 3 3 $((ex + 2)) $((W - 1)) 2 2 q"
  echo "box 3 3 2 $((W - 1)) $((D - 1)) $((D - 1)) q"
  echo "box 4 4 3 $((W - 2)) $((D - 1)) $((D - 1)) q"
  for z in 6 10 12; do echo "set 1 2 $z b"; echo "set 2 2 $z A"; echo "set 1 $((W - 1)) $z b"; echo "set 2 $((W - 1)) $z P"; done
fi
# свет: подвесные фонари равномерно, без тёмных клеток
lx=(4 $((W / 2)) $((W - 3)))
case $level in
1) lz=(4 9) ;;
2) lz=(3 7 $((D - 4))) ;;
3) lz=(3 7 11 $((D - 4))) ;;
4) lz=(3 5 7 11 15 $((D - 4))) ;;
esac
for x in "${lx[@]}"; do for z in "${lz[@]}"; do echo "set $((H - 1)) $x $z z"; echo "set $((H - 2)) $x $z H"; done; done
# фонари под потолком по краям зала
if [ "$level" -ge 4 ]; then
  for x in $(seq 3 3 $((W - 2))); do echo "set $((H - 1)) $x 2 H"; echo "set $((H - 1)) $x $((D - 1)) H"; done
fi
# полки на антресоли: плиты на торцах
if [ "$level" -ge 4 ]; then
  echo "box 5 5 5 5 9 12 q"; echo "box 5 5 5 5 16 $((D - 2)) q"
  echo "box 5 5 $((W - 4)) $((W - 4)) 9 12 q"; echo "box 5 5 $((W - 4)) $((W - 4)) 16 $((D - 2)) q"
  for x in 3 5 7 9 11 13 15 17; do echo "set 4 $x $((D - 1)) O"; done
fi
