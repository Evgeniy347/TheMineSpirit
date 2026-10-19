# Мини-язык постройки: size X Y Z | shift dx dz (сдвиг всех следующих команд) | box y1 y2 x1 x2 z1 z2 c | walls y1 y2 x1 x2 z1 z2 c | set y x z c
# | row y z x строка | col y1 y2 x z c | label y текст | header-строки начинаются с '#!' и печатаются как есть
function put(y,x,z,c){ x+=SX; z+=SZ; if(x>=0&&x<X&&z>=0&&z<Z&&y>=0&&y<Y) g[y,z,x]=c }
/^#!/ { hdr[++nh]=substr($0,3); next }
/^\s*$/ || /^#/ { next }
$1=="shift"{ SX=$2; SZ=$3; next }
$1=="size" { X=$2; Y=$3; Z=$4; for(y=0;y<Y;y++) for(z=0;z<Z;z++) for(x=0;x<X;x++) g[y,z,x]=" "; next }
$1=="box"  { for(y=$2;y<=$3;y++) for(x=$4;x<=$5;x++) for(z=$6;z<=$7;z++) put(y,x,z,$8); next }
$1=="walls"{ for(y=$2;y<=$3;y++) for(x=$4;x<=$5;x++) for(z=$6;z<=$7;z++) if(x==$4||x==$5||z==$6||z==$7) put(y,x,z,$8); next }
$1=="set"  { put($2,$3,$4,$5); next }
$1=="col"  { for(y=$2;y<=$3;y++) put(y,$4,$5,$6); next }
$1=="row"  { s=$0; sub(/^row +[0-9]+ +[0-9]+ +[0-9]+ /,"",s); for(i=1;i<=length(s);i++){c=substr(s,i,1); if(c!="`") put($2,$3+i-1,$4,c)}; next }
$1=="label"{ s=$0; sub(/^label +[0-9]+ /,"",s); lab[$2]=s; next }
{ print "неизвестная команда: " $0 > "/dev/stderr"; exit 1 }
END { for(i=1;i<=nh;i++) print hdr[i]
  for(y=0;y<Y;y++){ print "  - y" y (lab[y]!=""?" " lab[y]:"") ": |"
    for(z=0;z<Z;z++){ s=""; for(x=0;x<X;x++) s=s g[y,z,x]; sub(/ +$/,"",s); if(z==Z-1 && s=="") { for(i=0;i<int(X/2);i++) s=s " "; s=s "." } print "      " s } } }
