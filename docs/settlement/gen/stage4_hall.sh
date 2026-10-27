#!/usr/bin/env bash
# stage4_hall_<family>_<level> — таверна, лазарет, библиотека, торговый пост, алхимия, чаровальня.
# Запуск: bash stage4_hall.sh <family> <level> | awk -f grid.awk > <scheme>.txt
set -euo pipefail
source "$(dirname "$0")/stage4lib.sh"
family=${1:?семья: tavern|infirmary|library|alchemy|enchant_room}
level=${2:?уровень 1..4}
case $family in
tavern)
  case $level in 1) W=11; D=13;; 2) W=15; D=15;; 3) W=17; D=19;; 4) W=19; D=21;; esac
  type=social; band=b; scheme=tavern_$level
  case $level in 1|2) stage=3;; *) stage=4;; esac
  name="Таверна, уровень $level"
  ;;
infirmary)
  case $level in 1) W=9; D=11;; 2) W=13; D=13;; 3) W=13; D=13;; 4) W=15; D=15;; esac
  type=social; band=Q; scheme=infirmary_$level
  case $level in 1) stage=3;; *) stage=4;; esac
  name="Лазарет, уровень $level"
  ;;
library)
  case $level in 1) W=9; D=11;; 2) W=13; D=15;; 3) W=17; D=17;; 4) W=19; D=19;; esac
  type=social; band=Q; scheme=library_$level; stage=4
  name="Библиотека, уровень $level"
  ;;
alchemy)
  case $level in 1) W=7; D=9;; 2) W=11; D=11;; 3) W=11; D=11;; 4) W=13; D=13;; esac
  type=craft; band=Q; scheme=alchemy_$level
  case $level in 1) stage=3;; *) stage=4;; esac
  name="Алхимическая лаборатория, уровень $level"
  ;;
enchant_room)
  case $level in 4) W=19; D=17;; *) echo "чаровальня 1–3 уже есть" >&2; exit 2;; esac
  type=craft; band=Q; scheme=enchant_room_v4; stage=2
  name="Чаровальня, уровень 4"
  ;;
*) echo "неизвестная семья $family" >&2; exit 2 ;;
esac
H=0
case $level in 1) H=5;; 2) H=6;; 3) H=7;; 4) H=8;; esac
X=$((W + 2)); Z=$((D + 2))
minwd=$W; [ "$D" -lt "$minwd" ] && minwd=$D
rooflayers=$(((minwd - 1) / 2 + 1)); [ $rooflayers -gt 4 ] && rooflayers=4
Y=$((H + 1 + rooflayers))
ex=$((1 + W / 2))
if [ "$level" -le 2 ]; then
  wall=stone_bricks; plinth=polished_andesite; stonestair=polished_andesite_stairs; stair=deepslate_brick_stairs
else
  wall=bricks; plinth=smooth_stone; stonestair=smooth_stone_stairs; stair=deepslate_tile_stairs
fi
roofslab=${stair%_stairs}_slab
cat <<H
#!# $scheme — $name (catalog-2.md, style.md): цоколь, каркас тёмного дуба, крыша из глубинного сланца
#!# со свесом; внутри — назначение семьи, мебель у стен, свет без тёмных клеток.
#!name: "$name"
#!type: $type
#!stage: $stage
#!level: $level
H
if [ "$family" = enchant_room ]; then echo "#!requires: enchant_room_v3"
elif [ "$level" -gt 1 ]; then echo "#!requires: ${family}_$((level - 1))"; fi
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
#!  'g': light_gray_carpet
#!  'C': chest[facing=south]
#!  'b': barrel[facing=up]
#!  'Q': bookshelf
#!  'l': lectern[facing=south]
#!  'T': crafting_table
#!  'F': furnace[facing=south]
#!  'k': smoker[facing=south]
#!  'u': water_cauldron[level=3]
#!  'V': cauldron
#!  'v': brewing_stand
#!  'A': anvil
#!  'W': grindstone
#!  'M': cartography_table
#!  'j': enchanting_table
#!  'B': white_bed[facing=north]
#!  'f': campfire[lit=true]
#!  'h': hay_block
#!  'P': potted_poppy
#!  'O': potted_dandelion
#!  'X': potted_azure_bluet
#!  'H': lantern[hanging=true]
#!  'N': lantern[hanging=false]
#!  'z': chain[axis=y]
#!  'x': oak_leaves
#!layers:
H
echo "size $X $Y $Z"
echo "label 0 цоколь и пол"; echo "label 1 зал"
echo "box 0 0 0 $((X - 1)) 0 $((Z - 1)) a"
echo "walls 1 $((H - 1)) 1 $W 1 $D S"
echo "box $H $H 1 $W 1 $D #"
# нижний пояс стен из декоративного полного блока у богатых общественных построек (декор без лишних клеток)
if [ "$type" = social ] && [ "$level" -ge 4 ]; then echo "walls 1 2 1 $W 1 $D $band"; fi
# окна
win() { local y1=$1 y2=$2 kind=$3; shift 3; local p z
  case $kind in
    front) for p in "$@"; do echo "box $y1 $y2 $p $p 1 1 G"; done ;;
    back) for p in "$@"; do echo "box $y1 $y2 $p $p $D $D G"; done ;;
    side) for z in "$@"; do echo "box $y1 $y2 1 1 $z $z G"; echo "box $y1 $y2 $W $W $z $z G"; done ;;
  esac; }
case $level in
1) win 2 2 front 3 $((W - 2)); win 2 2 back 3 $((W - 2)); win 2 2 side 3 $((D - 2)) ;;
2) win 2 3 front 3 $((W - 2)); win 2 3 back 3 $((W - 2)); win 2 3 side 3 $((D - 2)) ;;
3) win 2 3 front 3 4 $((W - 3)) $((W - 2)); win 2 3 back 3 4 $((W - 3)) $((W - 2))
   win 2 3 side 3 4 $((D - 3)) $((D - 2)) ;;
4) win 2 3 front 3 4 5 $((W - 4)) $((W - 3)) $((W - 2)); win 2 3 back 3 4 5 $((W - 4)) $((W - 3)) $((W - 2))
   win 2 3 side 3 4 5 $((D - 4)) $((D - 3)) $((D - 2)) ;;
esac
echo "set 1 $ex 1 D"; echo "set 2 $ex 1 D"; echo "set 0 $ex 0 p"
corner_frame 1 $W 1 $D $((H - 1)) L $ex
eave_ring 1 $W 1 $D $H "#"
attic_roof 1 $W 1 $D $((H + 1)) "#" 1 2 3 4 $rooflayers
eave_decor 1 $W 1 $D $((H + 1)) t x
# ковры: у общественных — по всему полу, у ремесленных — дорожка
if [ "$type" = social ]; then
  echo "box 1 1 2 $((W - 1)) 2 $((D - 1)) ~"
  [ "$level" -ge 2 ] && echo "box 1 1 4 $((W - 3)) 3 $((D - 2)) m"
else
  echo "box 1 1 $ex $ex 2 $((D - 1)) ~"
fi
# свет: цепи с фонарями под потолком
lx=(3 $((W / 2)) $((W - 2)))
case $level in
1) lz=(3 $((D - 2))) ;;
2) lz=(3 $((D / 2)) $((D - 2))) ;;
3|4) lz=(3 7 $((D - 3))) ;;
esac
for x in "${lx[@]}"; do for z in "${lz[@]}"; do echo "set $((H - 1)) $x $z z"; echo "set $((H - 2)) $x $z H"; done; done
if [ "$type" = social ] && [ "$level" -ge 4 ]; then
  for x in $(seq 3 3 $((W - 2))); do echo "set $((H - 1)) $x 2 H"; echo "set $((H - 1)) $x $((D - 1)) H"; done
  for z in $(seq 4 3 $((D - 4))); do echo "set $((H - 1)) 2 $z H"; echo "set $((H - 1)) $((W - 1)) $z H"; done
  for x in $(seq 4 2 $((W - 3))); do echo "set $((H - 1)) $x $((D / 2)) H"; done
  for z in $((D / 3)) $((2 * D / 3)) $((D / 2 - 2)); do for x in $(seq 4 3 $((W - 3))); do echo "set $((H - 1)) $x $z H"; done; done
fi
# --- назначение семьи ---
case $family in
tavern)
  for x in $(seq 3 $((W - 2))); do echo "set 1 $x $((D - 1)) b"; done
  rowA=4; rowB=$((D - 4)); rowC=$((D / 2))
  rows=( "$rowA" "$rowB" ); [ "$level" -ge 2 ] && rows=( "$rowA" "$rowC" "$rowB" )
  for x in 3 $((W - 3)); do for z in "${rows[@]}"; do
    echo "box 1 1 $x $((x + 1)) $z $((z + 1)) t"; echo "box 2 2 $x $((x + 1)) $z $((z + 1)) q"
    echo "set 1 $((x - 1)) $z e"; echo "set 1 $((x + 2)) $z w"
  done; done
  echo "set 1 2 $((D - 2)) F"; echo "set 1 3 $((D - 2)) k"; echo "set 1 4 $((D - 2)) u"; echo "set 1 2 $((D - 3)) T"
  echo "set 1 $((W - 1)) 3 C"; echo "set 1 $((W - 1)) $((D - 3)) C"
  echo "set 1 $((W - 2)) $((D - 2)) f"
  echo "set 2 3 $((D - 1)) P"; echo "set 2 $((W - 2)) $((D - 1)) O"
  ;;
infirmary)
  beds=(4 7 10)
  [ "$level" -eq 1 ] && beds=(4 7)
  [ "$level" -eq 4 ] && beds=(4 7 10 13)
  for x in 2 $((W - 1)); do for z in "${beds[@]}"; do
    [ "$z" -lt "$D" ] || continue
    echo "set 1 $x $z B"; echo "set 1 $x $((z - 1)) B"
  done; done
  echo "set 1 3 2 v"; echo "set 1 4 2 V"; echo "set 1 $((W - 2)) 2 C"
  echo "set 1 3 $((D - 2)) Q"; echo "set 1 4 $((D - 2)) Q"; echo "set 1 3 3 u"
  [ "$level" -ge 2 ] && { echo "set 1 $((W - 4)) $((D - 3)) v"; echo "set 1 $((W - 5)) $((D - 3)) V"; }
  if [ "$level" -ge 4 ]; then
    echo "box 1 2 3 $((W - 2)) $((D - 1)) $((D - 1)) Q"
    for x in 3 5 7 9 11 13; do echo "set 3 $x $((D - 1)) O"; done
  fi
  ;;
library)
  if [ "$level" -lt 4 ]; then
    echo "box 1 2 2 $((W - 1)) $((D - 1)) $((D - 1)) Q"
    if [ "$level" -ge 3 ]; then
      echo "box 1 2 2 2 3 $((D - 2)) Q"; echo "box 1 2 $((W - 1)) $((W - 1)) 3 $((D - 2)) Q"
    fi
  fi
  for x in $((ex - 2)) $((ex + 1)); do
    echo "box 1 1 $x $((x + 1)) $((D / 2)) $((D / 2)) t"; echo "box 2 2 $x $((x + 1)) $((D / 2)) $((D / 2)) q"
    echo "set 1 $x $((D / 2 - 1)) n"; echo "set 1 $((x + 1)) $((D / 2 + 1)) s"
  done
  echo "set 1 $ex $((D - 3)) l"; echo "set 1 $ex 3 l"
  echo "set 1 2 2 C"; echo "set 1 $((W - 1)) $((D - 2)) b"
  if [ "$level" -ge 4 ]; then for z in 3 5 7 9 11 13; do echo "set 1 3 $z P"; echo "set 1 $((W - 2)) $z O"; done; fi
  ;;
alchemy)
  echo "set 1 2 3 v"; echo "set 1 2 6 v"; echo "set 1 3 3 V"; echo "set 1 3 6 V"
  echo "set 1 $((W - 1)) 3 C"; echo "set 1 $((W - 1)) 6 b"
  echo "box 1 2 2 $((W - 1)) $((D - 1)) $((D - 1)) Q"
  echo "set 2 3 $((D - 1)) P"; echo "set 2 $((W - 2)) $((D - 1)) O"
  echo "set 1 2 $((D - 2)) T"; echo "set 1 $((W - 2)) $((D - 2)) u"
  if [ "$level" -ge 2 ]; then
    cx=$ex; cz=$((D / 2))
    echo "box $H $H $((cx - 1)) $((cx + 1)) $((cz - 1)) $((cz + 1)) G"
    echo "set 1 $cx $cz V"; echo "set 1 $((cx - 1)) $((cz - 1)) x"; echo "set 1 $((cx + 1)) $((cz + 1)) x"
    echo "set 1 $((cx - 2)) 3 v"; echo "set 1 $((cx + 2)) 3 v"
  fi
  [ "$level" -ge 3 ] && { echo "set 1 $((W - 2)) 2 v"; echo "set 1 $((W - 2)) 4 V"; }
  ;;
enchant_room)
  cx=$ex; cz=$((D / 2))
  echo "set 1 $cx $cz j"
  for dx in -2 -1 0 1 2; do for dz in -2 -1 0 1 2; do
    if [ $((dx == -2 || dx == 2 || dz == -2 || dz == 2)) ]; then
      [ "$dx" -eq 0 ] && [ "$dz" -eq -2 ] && continue
      echo "set 1 $((cx + dx)) $((cz + dz)) Q"
    fi
  done; done
  echo "set 1 $((cx - 2)) $((cz - 2)) l"; echo "set 1 $((cx + 2)) $((cz + 2)) l"
  echo "set 1 2 2 Q"; echo "set 1 $((W - 1)) 2 Q"; echo "set 1 2 $((D - 2)) b"; echo "set 1 $((W - 1)) $((D - 2)) C"
  echo "box $H $H $((cx - 2)) $((cx + 2)) $((cz - 2)) $((cz + 2)) G"
  ;;
esac
