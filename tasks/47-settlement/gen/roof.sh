# roof X0 W ZF ZB YB winks : двускатная крыша над телом x=X0..X0+W-1, z=ZF..ZB (фронтоны), свес 1, слой k=0 на YB.
# Ступеньки 'e'/'w', подкладка '#', конёк '_' (нечётная ширина) или встреча ступенек (чётная). winks — слои с окном 'G'.
roof() { local X0=$1 W=$2 ZF=$3 ZB=$4 YB=$5 winks=" ${6:-} " k inner y xl xr x z
  for ((k=0;;k++)); do inner=$((W-2*k)); y=$((YB+k)); xl=$((X0-1+k)); xr=$((X0+W-k))
    if [ $inner -le 0 ]; then
      [ $inner -eq -1 ] && echo "box $y $y $((xl)) $((xl)) $((ZF-1)) $((ZB+1)) _"
      [ $inner -eq 0 ] && { echo "box $y $y $xl $xl $((ZF-1)) $((ZB+1)) e"; echo "box $y $y $xr $xr $((ZF-1)) $((ZB+1)) w"; }
      break; fi
    echo "box $y $y $xl $xl $((ZF-1)) $((ZB+1)) e"; echo "box $y $y $xr $xr $((ZF-1)) $((ZB+1)) w"
    if [ $k -eq 0 ]; then
      for z in $((ZF-1)) $((ZB+1)); do echo "set $y $X0 $z #"; echo "set $y $((X0+W-1)) $z #"; done
    else
      echo "box $y $y $((X0+k)) $((X0+k)) $((ZF-1)) $((ZB+1)) #"; echo "box $y $y $((X0+W-1-k)) $((X0+W-1-k)) $((ZF-1)) $((ZB+1)) #"
      echo "box $y $y $((X0+k)) $((X0+W-1-k)) $ZF $ZF #"; echo "box $y $y $((X0+k)) $((X0+W-1-k)) $ZB $ZB #"
      if [[ "$winks" == *" $k "* ]]; then local c1=$((X0+(W-1)/2)) c2=$((X0+W/2))
        for z in $ZF $ZB; do echo "set $y $c1 $z G"; echo "set $y $c2 $z G"; done; fi
    fi
  done; }
# hiproof X0 W Z0 D YB E Wc S N P R : вальмовая крыша над телом x=X0..X0+W-1, z=Z0..Z0+D-1, свес 1, слой 0 на YB.
# Кольцо ступенек на слое k — с отступом k-1 от стен (E — западный скат, Wc — восточный, S — северный, N — южный),
# под ним кольцо подкладки P с отступом k; одна оставшаяся линия — конёк из полублоков R.
hiproof() { local X0=$1 W=$2 Z0=$3 D=$4 YB=$5 E=$6 Wc=$7 S=$8 N=$9 P=${10} R=${11} k y xl xr zl zr
  for ((k=0;;k++)); do y=$((YB+k)); xl=$((X0-1+k)); xr=$((X0+W-k)); zl=$((Z0-1+k)); zr=$((Z0+D-k))
    if [ $xl -gt $xr ] || [ $zl -gt $zr ]; then break; fi
    if [ $zl -eq $zr ] || [ $xl -eq $xr ]; then echo "box $y $y $xl $xr $zl $zr $R"; break; fi
    echo "box $y $y $xl $xr $zl $zl $S"; echo "box $y $y $xl $xr $zr $zr $N"
    echo "box $y $y $xl $xl $zl $zr $E"; echo "box $y $y $xr $xr $zl $zr $Wc"
    if [ $k -ge 1 ] && [ $((X0+k)) -le $((X0+W-1-k)) ] && [ $((Z0+k)) -le $((Z0+D-1-k)) ]; then
      echo "walls $y $y $((X0+k)) $((X0+W-1-k)) $((Z0+k)) $((Z0+D-1-k)) $P"; fi
  done; }
