#!/bin/bash
# =====================================================================================================
#  Cai server Avatar 250 (client 2.5.8) len VPS Ubuntu / Debian bang 1 lenh (chay duoc tu app SSH tren dien thoai):
#
#    curl -fsSL https://raw.githubusercontent.com/nookc/army-ios/avatar-vps/cai_dat_avatar.sh | sudo bash -s -- MAT_KHAU_GOI
#
#  Lam gi: cai Java 17 + MariaDB (neu VPS chua co MySQL/MariaDB), tai goi game (ma hoa AES-256, MAT_KHAU_GOI de mo),
#  nap database avatar_2x, chay server game (cong 19128, tu bat lai khi VPS khoi dong lai), mo trang web + dang ky
#  tai khoan (cong 80, hoac 8080 neu cong 80 dang ban), ghi san dia chi may chu vao APK / IPA / JAR tren trang web.
#
#  An toan cho VPS dang chay viec khac: KHONG xoa / ghi de database khac, KHONG dung toi Apache / Nginx / panel co san
#  (cong 80 ban thi web Avatar chay cong 8080). Chay lai lenh = cap nhat ban moi, giu nguyen du lieu nguoi choi.
#
#  Tuy chon (dat truoc lenh bash):  MAY_CHU=ten.mien.cua.ban   WEB_PORT=8080   DB_ROOT_PASS=...   KHONG_TUONG_LUA=1
# =====================================================================================================
set -uo pipefail
KHOA="${1:-${MAT_KHAU_GOI:-}}"
NGUON="${NGUON:-https://raw.githubusercontent.com/nookc/army-ios/avatar-vps}"
DIR="${DIR:-/opt/avatar}"
DB="${DB_NAME:-avatar_2x}"
GAME_PORT=19128
TMP=$(mktemp -d /tmp/avatar-cai.XXXXXX)
trap 'rm -rf "$TMP"' EXIT

dang_dung() { ss -ltnH "sport = :$1" 2>/dev/null | grep -q . ; }
cong_trong() { for c in "$@"; do dang_dung "$c" || { echo "$c"; return 0; }; done; return 1; }
xanh() { printf '\033[1;32m%s\033[0m\n' "$*"; }
vang() { printf '\033[1;33m%s\033[0m\n' "$*"; }
loi() { printf '\033[1;31mLOI: %s\033[0m\n' "$*" >&2; exit 1; }
buoc() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

[ "$(id -u)" = 0 ] || loi "can chay voi quyen root (them 'sudo' truoc 'bash')"
[ -n "$KHOA" ] || loi "thieu mat khau goi: ... | sudo bash -s -- MAT_KHAU_GOI"
command -v apt-get >/dev/null || loi "chi ho tro Ubuntu / Debian (apt-get)"
export DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------- 0. kiem tra may
buoc "Kiem tra VPS"
. /etc/os-release 2>/dev/null || true
echo "He dieu hanh: ${PRETTY_NAME:-?}, $(uname -m), RAM $(free -m | awk '/Mem:/{print $2}') MB, trong $(df -m / | awk 'NR==2{print $4}') MB"
[ "$(df -m / | awk 'NR==2{print $4}')" -ge 1500 ] || loi "o dia con trong duoi 1,5 GB"
RAM=$(free -m | awk '/Mem:/{print $2}')
if [ "$RAM" -ge 3500 ]; then XMX=1g; elif [ "$RAM" -ge 1800 ]; then XMX=768m; else XMX=512m; fi
NANG_CAP=0
[ -f "$DIR/server/Avatar2D-server.jar" ] && NANG_CAP=1 && vang "Da co Avatar o $DIR: cap nhat ban moi, giu database va cau hinh"

# ---------------------------------------------------------------- 1. goi phan mem
buoc "Cai Java 17, MariaDB, Python"
APT=(apt-get -o DPkg::Lock::Timeout=300 -o Acquire::Retries=3)
"${APT[@]}" update -qq || vang "apt-get update loi mot phan, tiep tuc"
PKGS="curl unzip zip openssl python3 python3-pymysql python3-cryptography socat ca-certificates iproute2"
JAVA_OK=0
if command -v java >/dev/null && java -version 2>&1 | grep -qE 'version "(1[7-9]|2[0-9])'; then JAVA_OK=1; fi
[ $JAVA_OK = 1 ] || PKGS="$PKGS openjdk-17-jre-headless"
CO_DB=0
# VPS da co MySQL/MariaDB (ke ca cai qua panel nhu aaPanel / HestiaCP): KHONG cai MariaDB de len, chi dung chung
if dang_dung 3306 || pgrep -x mysqld >/dev/null || pgrep -x mariadbd >/dev/null || command -v mysqld >/dev/null || command -v mariadbd >/dev/null \
   || [ -x /www/server/mysql/bin/mysqld ] || [ -x /usr/local/mysql/bin/mysqld ] \
   || systemctl list-unit-files 2>/dev/null | grep -qE '^(mysql|mysqld|mariadb)\.service'; then
  CO_DB=1; echo "VPS da co MySQL/MariaDB: dung chung, chi tao them database $DB (khong dung toi database khac)"
else
  PKGS="$PKGS mariadb-server"
fi
# shellcheck disable=SC2086
"${APT[@]}" install -y -qq $PKGS >/dev/null || {
  # Debian/Ubuntu cu khong co openjdk-17: thu ban java mac dinh
  PKGS=${PKGS/openjdk-17-jre-headless/default-jre-headless}
  "${APT[@]}" install -y -qq $PKGS >/dev/null || loi "khong cai duoc goi: $PKGS"
}
JAVA=""
for j in /usr/lib/jvm/java-17-openjdk-*/bin/java /usr/lib/jvm/java-2[0-9]-openjdk-*/bin/java /usr/lib/jvm/java-1[89]-openjdk-*/bin/java "$(command -v java)"; do
  [ -x "$j" ] && "$j" -version 2>&1 | grep -qE 'version "(1[7-9]|2[0-9])' && { JAVA=$j; break; }
done
[ -n "$JAVA" ] || loi "khong tim thay Java 17 tro len"
echo "Java: $JAVA ($("$JAVA" -version 2>&1 | head -1))"
if [ $CO_DB = 0 ]; then systemctl enable --now mariadb >/dev/null 2>&1 || systemctl enable --now mysql >/dev/null 2>&1 || true; fi

# ---------------------------------------------------------------- 1b. dang nhap MySQL (hoi mat khau truoc khi tai goi)
buoc "Kiem tra MySQL / MariaDB"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || command -v mariadb || ls /www/server/mysql/bin/mysql /usr/local/mysql/bin/mysql 2>/dev/null | head -1)}"
[ -n "$MYSQL_BIN" ] || loi "khong thay lenh mysql tren VPS (dat MYSQL_BIN=/duong/dan/mysql truoc lenh)"
MYSQL=("$MYSQL_BIN")
if ! "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1; then
  # mat khau root database de trong file tam (quyen 600), khong de tren dong lenh
  root_cnf() {
    local pw=${1//\\/\\\\} q="'"
    case "$pw" in *"'"*) q='"' ;; esac
    case "$pw" in *"'"*'"'*|*'"'*"'"*) export MYSQL_PWD="$1"; MYSQL=("$MYSQL_BIN" -uroot); return ;; esac
    ( umask 077; printf '[client]\nuser=root\npassword=%s%s%s\n' "$q" "$pw" "$q" > "$TMP/root.cnf" )
    MYSQL=("$MYSQL_BIN" --defaults-extra-file="$TMP/root.cnf")
  }
  if [ -n "${DB_ROOT_PASS:-}" ]; then
    root_cnf "$DB_ROOT_PASS"
  elif [ -r /etc/mysql/debian.cnf ] && "$MYSQL_BIN" --defaults-file=/etc/mysql/debian.cnf -e "SELECT 1" >/dev/null 2>&1; then
    MYSQL=("$MYSQL_BIN" --defaults-file=/etc/mysql/debian.cnf)
  elif [ -r /dev/tty ]; then
    printf 'MySQL tren VPS can mat khau root (mat khau database, KHONG phai mat khau VPS): ' > /dev/tty
    read -rs DBP < /dev/tty; echo > /dev/tty
    root_cnf "$DBP"
  fi
  "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 || loi "khong dang nhap duoc MySQL bang root (dat DB_ROOT_PASS=... truoc lenh)"
fi
LA_MARIADB=0; "${MYSQL[@]}" -N -e "SELECT VERSION()" | grep -qi mariadb && LA_MARIADB=1
DBVER=$("${MYSQL[@]}" -N -e "SELECT VERSION()")
echo "Database server: $DBVER"
DBV=$(echo "$DBVER" | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+' | awk -F. '{printf "%d%03d%03d", $1, $2, $3}')
if [ $LA_MARIADB = 1 ]; then [ "${DBV:-0}" -ge 10002001 ] || loi "MariaDB $DBVER qua cu: can 10.2 tro len";
else [ "${DBV:-0}" -ge 8000013 ] || loi "MySQL $DBVER qua cu: can MySQL 8.0.13 tro len (hoac MariaDB 10.2+)"; fi

# ---------------------------------------------------------------- 2. tai + giai ma goi game
buoc "Tai goi game (ma hoa) tu GitHub"
curl -fsSL --retry 3 "$NGUON/goi/DANH_SACH.txt" -o "$TMP/DANH_SACH.txt" || loi "khong tai duoc danh sach goi ($NGUON)"
while read -r sha ten; do
  [ -n "${ten:-}" ] || continue
  echo "  tai $ten"
  curl -fL --retry 3 --progress-bar "$NGUON/goi/$ten" -o "$TMP/$ten" || loi "tai $ten loi"
  echo "$sha  $TMP/$ten" | sha256sum -c --status || loi "$ten bi hong (sai sha256), chay lai lenh"
done < "$TMP/DANH_SACH.txt"
buoc "Giai ma + giai nen"
mkdir -p "$TMP/x"
PHAN=(); while read -r _ ten; do [ -n "${ten:-}" ] && PHAN+=("$TMP/$ten"); done < "$TMP/DANH_SACH.txt"
cat "${PHAN[@]}" | openssl enc -d -aes-256-cbc -pbkdf2 -iter 300000 -pass "pass:$KHOA" 2>/dev/null \
  | tar -x -C "$TMP/x" 2>/dev/null || loi "sai mat khau goi (hoac goi hong)"
rm -f "$TMP"/avatar.enc.*
G="$TMP/x/goi"
[ -f "$G/server/Avatar2D-server.jar" ] || loi "goi thieu server"
cat "$G/PHIEN_BAN.txt" 2>/dev/null || true

# ---------------------------------------------------------------- 3. dia chi may chu
IP=$(curl -4 -fsS --max-time 8 https://api.ipify.org 2>/dev/null || curl -4 -fsS --max-time 8 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
HOSTNAME_GAME="${MAY_CHU:-$IP}"
[ -n "$HOSTNAME_GAME" ] || loi "khong biet IP cong khai cua VPS: dat MAY_CHU=ip-hoac-ten-mien truoc lenh"
DIACHI="$HOSTNAME_GAME:$GAME_PORT"
echo "Dia chi may chu game: $DIACHI"

# ---------------------------------------------------------------- 4. chep file
buoc "Chep server, anh, trang web vao $DIR"
id avatar >/dev/null 2>&1 || useradd --system --home-dir "$DIR" --shell /usr/sbin/nologin avatar
mkdir -p "$DIR/server/logs" "$DIR/web" "$DIR/client" "$DIR/database" "$DIR/vps"
systemctl stop avatar-server >/dev/null 2>&1 || true
sleep 1
dang_dung $GAME_PORT && loi "cong $GAME_PORT (cong game) dang bi chuong trinh khac dung tren VPS"
cp -f "$G/server/Avatar2D-server.jar" "$DIR/server/"
if [ ! -f "$DIR/server/config.properties" ]; then
  cp "$G/server/config.properties" "$DIR/server/"
  # trang quan tri cua server game (chi mo trong VPS): 8088, ban thi lay cong trong khac
  AP=$(cong_trong 8088 8089 8090 18088 28088) || AP=0
  sed -i "s/^admin.web.port=.*/admin.web.port=$AP/; s/^admin.web.host=.*/admin.web.host=127.0.0.1/" "$DIR/server/config.properties"
fi
ADMIN_PORT=$(grep -o '^admin.web.port=[0-9]*' "$DIR/server/config.properties" | cut -d= -f2)
echo "  giai nen anh (khoang 125 000 tep, vai phut)..."
mkdir -p "$TMP/anh"
for z in "$G"/anh/*.zip; do unzip -q -o "$z" -d "$TMP/anh" || loi "giai nen $z loi"; done
mkdir -p "$DIR/server/res"
cp -a "$TMP/anh/AvatarFull/server/res/." "$DIR/server/res/"
rm -rf "$TMP/anh"
cp -f "$G"/database/*.sql "$DIR/database/"
rm -rf "$DIR/web.moi" && cp -a "$G/web/public" "$DIR/web.moi"
cp -f "$G"/client/* "$DIR/client/"
rm -f "$DIR/client/Avatar-BanCa3D.apk" "$DIR/client/Avatar258.jar"   # ten cu (nay la Avatar250.apk / Avatar250.jar)
cp -f "$G/vps/web.py" "$DIR/vps/"

# ---------------------------------------------------------------- 5. database
buoc "Database $DB"
DBUSER=avatar_game
DBPASS_FILE="$DIR/.db_pass"
if [ -f "$DBPASS_FILE" ]; then DBPASS=$(cat "$DBPASS_FILE"); else DBPASS=$(openssl rand -hex 16); echo "$DBPASS" > "$DBPASS_FILE"; fi
chmod 600 "$DBPASS_FILE"
CO_AVATAR=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$DB'")
ADMIN_INFO=""
if [ "$CO_AVATAR" = "0" ]; then
  "${MYSQL[@]}" -e "CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci" || loi "tao database loi"
  echo "  nap avatar_2x_full.sql (18 MB)..."
  if ! sed '/^\/\*M!999999\\- enable the sandbox mode \*\//d' "$DIR/database/avatar_2x_full.sql" | "${MYSQL[@]}" --default-character-set=utf8mb4 "$DB"; then
    "${MYSQL[@]}" -e "DROP DATABASE \`$DB\`" || true   # chi xoa database vua tao trong lan chay nay
    loi "nap database loi (xem dong loi o tren)"
  fi
  # tai khoan quan tri: dat lai mat khau ngau nhien cho admin / ad (mat khau goc trong repo ai cung biet)
  for u in admin ad; do
    P=$(openssl rand -base64 12 | tr -dc 'A-Za-z0-9' | head -c 10)
    if [ "$("${MYSQL[@]}" -N "$DB" -e "SELECT COUNT(*) FROM users WHERE username='$u'")" != "0" ]; then
      "${MYSQL[@]}" "$DB" -e "UPDATE users SET password=MD5('$P'), active=1, login_lock=0 WHERE username='$u'"
      ADMIN_INFO="$ADMIN_INFO$u / $P\n"
    fi
  done
else
  vang "  database $DB da co: giu nguyen (khong nap lai)"
  if [ "$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name IN ('fishgame_player','fashion_upgrade')")" != "2" ]; then
    vang "  CANH BAO: $DB la ban cu (thieu bang ban ca / thoi trang). Nang cap database theo muc 3 HUONG_DAN.md (thu muc database/nang-cap) roi chay lai lenh."
  fi
fi
# MySQL 8: dung mysql_native_password (server game + trang dang ky noi khong SSL); khong co thi de mac dinh
if [ $LA_MARIADB = 0 ] && \
   "${MYSQL[@]}" -e "CREATE USER IF NOT EXISTS '$DBUSER'@'localhost' IDENTIFIED WITH mysql_native_password BY '$DBPASS'; ALTER USER '$DBUSER'@'localhost' IDENTIFIED WITH mysql_native_password BY '$DBPASS';" 2>/dev/null; then
  :
else
  "${MYSQL[@]}" -e "CREATE USER IF NOT EXISTS '$DBUSER'@'localhost' IDENTIFIED BY '$DBPASS'; ALTER USER '$DBUSER'@'localhost' IDENTIFIED BY '$DBPASS';" \
    || loi "tao tai khoan database '$DBUSER' loi"
fi
"${MYSQL[@]}" -e "GRANT ALL PRIVILEGES ON $DB.* TO '$DBUSER'@'localhost'; FLUSH PRIVILEGES;" || loi "cap quyen database loi"
DBPORT=$("${MYSQL[@]}" -N -e "SELECT @@port" 2>/dev/null || echo 3306)
DBNAME_URL=$DB
if [ $LA_MARIADB = 0 ]; then
  DBNAME_URL="$DB?allowPublicKeyRetrieval=true&sessionVariables=sql_mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'"
fi
cat > "$DIR/server/database.properties" <<EOF
driver=com.mysql.cj.jdbc.Driver
host=127.0.0.1
port=${DBPORT:-3306}
dbname=$DBNAME_URL
username=$DBUSER
# server doc dong "passeword" (ten goc trong code), trang web doc ca 2
password=$DBPASS
passeword=$DBPASS
max_connection=100
min_connection=10
EOF
chmod 640 "$DIR/server/database.properties"

# ---------------------------------------------------------------- 6. trang web + file tai (ghi san dia chi may chu)
buoc "Trang web + APK / IPA / JAR co san dia chi $DIACHI"
W="$DIR/web.moi"
echo "$DIACHI" > "$DIR/client/DIA_CHI.txt"   # trang quan tri (tab Trang web) gan dia chi nay khi admin tai app moi len
# app admin tai len tu trang quan tri (client/tai-len/) moi hon ban trong goi thi giu ban tai len
nguon() { if [ -f "$DIR/client/tai-len/$1" ] && [ "$DIR/client/tai-len/$1" -nt "$G/client/$1" ]; then echo "$DIR/client/tai-len/$1"; else echo "$DIR/client/$1"; fi; }
SA=$(nguon Avatar250.apk); SJ=$(nguon Avatar250.jar); SI=$(nguon Avatar.ipa)
case "$SA$SJ$SI" in *tai-len*) echo "  dung app admin da tai len (moi hon ban trong goi): $(echo "$SA $SJ $SI" | tr ' ' '\n' | grep tai-len | xargs -n1 basename | tr '\n' ' ')";; esac
# DoiMayChu nhan loai app theo duoi file: chep sang ten co duoi dung truoc khi gan
gan() { cp -f "$1" "$TMP/vao.$3" && ( cd "$TMP" && "$JAVA" -jar "$DIR/client/DoiMayChu.jar" "$TMP/vao.$3" "$2" "$DIACHI" >/dev/null 2>&1 ); }
gan "$SA" "$W/Avatar250.apk" apk \
  || { vang "  khong ghi duoc dia chi vao APK: dung APK goc (nguoi choi tu nhap)"; cp "$SA" "$W/Avatar250.apk"; }
ln -sf Avatar250.apk "$W/Avatar-BanCa3D.apk"   # link tai cu (da chia se truoc day) van tai duoc ban moi
gan "$SJ" "$W/Avatar250.jar" jar \
  || { vang "  khong ghi duoc dia chi vao JAR: dung JAR goc"; cp "$SJ" "$W/Avatar250.jar"; }
ln -sf Avatar250.jar "$W/Avatar258.jar"   # link tai JAR cu van tai duoc ban moi
gan "$SI" "$W/Avatar.ipa" ipa \
  || { vang "  khong ghi duoc dia chi vao IPA: dung IPA goc"; cp "$SI" "$W/Avatar.ipa"; }
rm -f "$TMP"/vao.*
# khong ghi dia chi may chu len trang web (app da gan san, tranh lo IP); giu ten game + link nhom / Zalo / Facebook chu server da dien
python3 - "$DIR/web/cau-hinh.js" "$W/cau-hinh.js" <<'PY' 2>/dev/null || true
import re, sys
try:
    old = open(sys.argv[1], encoding='utf-8').read()
except Exception:
    sys.exit(0)
p = sys.argv[2]
s = open(p, encoding='utf-8').read()
for k in ('tenGame', 'nhom', 'zalo', 'zaloAdmin', 'facebook', 'thongBao', 'ipaLink'):
    m = re.search(r"\b%s:\s*'([^'\\]*)'" % k, old)
    if not (m and m.group(1)):
        continue
    v = m.group(1)
    if re.search(r"\b%s:\s*'" % k, s):
        s = re.sub(r"\b%s:\s*'[^']*'" % k, lambda _: "%s: '%s'" % (k, v), s, count=1)
    else:
        s = s.replace('};', "  , %s: '%s'\n};" % (k, v), 1)
open(p, 'w', encoding='utf-8').write(s)
PY
rm -rf "$DIR/web.cu"; [ -d "$DIR/web" ] && mv "$DIR/web" "$DIR/web.cu"; mv "$W" "$DIR/web"; rm -rf "$DIR/web.cu"
chown -R avatar:avatar "$DIR"
chmod 600 "$DBPASS_FILE"; chown root:root "$DBPASS_FILE"
chown root:avatar "$DIR/server/database.properties"

# ---------------------------------------------------------------- 7. dich vu (tu chay khi VPS khoi dong)
buoc "Dich vu he thong"
if [ -n "${WEB_PORT:-}" ]; then
  :
elif [ -f /etc/systemd/system/avatar-web.service ]; then
  WEB_PORT=$(grep -o -- '--port [0-9]*' /etc/systemd/system/avatar-web.service | awk '{print $2}'); WEB_PORT=${WEB_PORT:-80}
else
  WEB_PORT=$(cong_trong 80 8080 8081 8888 8000) || loi "cac cong 80 8080 8081 8888 8000 deu ban: dat WEB_PORT=... truoc lenh"
fi
systemctl stop avatar-web >/dev/null 2>&1 || true
if dang_dung "$WEB_PORT"; then loi "cong web $WEB_PORT dang bi chuong trinh khac dung: dat WEB_PORT=cong_khac truoc lenh"; fi
cat > /etc/systemd/system/avatar-server.service <<EOF
[Unit]
Description=Avatar 250 game server (client 2.5.8)
After=network-online.target mariadb.service mysql.service
Wants=network-online.target

[Service]
User=avatar
WorkingDirectory=$DIR/server
Environment=LANG=C.UTF-8
ExecStart=$JAVA -Xms256m -Xmx$XMX -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar Avatar2D-server.jar
Restart=always
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/avatar-web.service <<EOF
[Unit]
Description=Avatar web (trang tai game + dang ky)
After=network-online.target

[Service]
User=avatar
AmbientCapabilities=CAP_NET_BIND_SERVICE
ExecStart=/usr/bin/python3 $DIR/vps/web.py --root $DIR/web --port $WEB_PORT --db $DIR/server/database.properties
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
# cong phu: client goc noi thanh pho bang cong 14444 / 18128 -> chuyen ve 19128 (nguoi choi quen go :19128 van vao duoc)
cat > /etc/systemd/system/avatar-cong@.service <<EOF
[Unit]
Description=Avatar chuyen cong %i -> $GAME_PORT
After=network-online.target

[Service]
User=avatar
AmbientCapabilities=CAP_NET_BIND_SERVICE
ExecStart=/usr/bin/socat TCP-LISTEN:%i,fork,reuseaddr TCP:127.0.0.1:$GAME_PORT
Restart=always

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable -q avatar-server avatar-web
systemctl restart avatar-server avatar-web
for c in 14444 18128; do
  if systemctl is-active -q "avatar-cong@$c" || ! dang_dung $c; then systemctl enable -q --now "avatar-cong@$c" 2>/dev/null || true; fi
done

# ---------------------------------------------------------------- 8. tuong lua
if [ -z "${KHONG_TUONG_LUA:-}" ]; then
  if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
    for c in $GAME_PORT 14444 18128 $WEB_PORT; do ufw allow "$c/tcp" >/dev/null; done; echo "Da mo cong tren ufw"
  elif command -v firewall-cmd >/dev/null && firewall-cmd --state >/dev/null 2>&1; then
    for c in $GAME_PORT 14444 18128 $WEB_PORT; do firewall-cmd -q --permanent --add-port="$c/tcp"; done; firewall-cmd -q --reload; echo "Da mo cong tren firewalld"
  fi
fi

# ---------------------------------------------------------------- 9. kiem tra
buoc "Kiem tra"
OK=0
for _ in $(seq 1 60); do
  if dang_dung $GAME_PORT; then OK=1; break; fi
  sleep 2
done
if [ $OK = 1 ]; then xanh "Server game dang nghe cong $GAME_PORT"; else vang "Server game chua mo cong $GAME_PORT - xem: journalctl -u avatar-server -n 50"; fi
dang_dung "$WEB_PORT" && xanh "Trang web dang chay cong $WEB_PORT" || vang "Trang web chua chay - xem: journalctl -u avatar-web -n 30"
WEBURL="http://$HOSTNAME_GAME$([ "$WEB_PORT" = 80 ] || echo ":$WEB_PORT")/"
{
  echo "Avatar tren VPS - cai luc $(date '+%d/%m/%Y %H:%M')"
  echo "Trang web:        $WEBURL"
  echo "May chu game:     $DIACHI   (da gan san trong app tai tu trang web; khong can nhap)"
  [ -n "$ADMIN_INFO" ] && printf "Tai khoan quan tri (doi mat khau sau khi vao):\n$ADMIN_INFO"
  echo "Database:         $DB, tai khoan '$DBUSER', mat khau trong $DBPASS_FILE"
  echo "Xem log server:   journalctl -u avatar-server -f"
  echo "Khoi dong lai:    systemctl restart avatar-server"
  if [ "${ADMIN_PORT:-0}" != 0 ]; then
    echo "Trang quan tri:   ${WEBURL}tai-khoan.html -> dang nhap tai khoan admin -> nut Trang quan tri (hoac ${WEBURL}quan-tri/)"
    echo "                  Mat khau admin phai kho doan: doi ngay tren trang Tai khoan (muc Doi mat khau)"
  fi
} > "$DIR/THONG_TIN.txt"
if [ -n "$ADMIN_INFO" ]; then cp "$DIR/THONG_TIN.txt" "$DIR/THONG_TIN.txt.lan-dau"; elif [ -f "$DIR/THONG_TIN.txt.lan-dau" ]; then echo "Mat khau quan tri luc cai lan dau: cat $DIR/THONG_TIN.txt.lan-dau" >> "$DIR/THONG_TIN.txt"; fi
chmod 600 "$DIR"/THONG_TIN.txt*
echo
[ $NANG_CAP = 1 ] && xanh "================ DA CAP NHAT (giu nguyen nguoi choi) ================" || xanh "================ XONG ================"
cat "$DIR/THONG_TIN.txt"
echo
vang "Neu dung Hetzner / nha cung cap co Firewall rieng tren trang quan ly: mo them cong TCP $GAME_PORT, 14444, 18128, $WEB_PORT."
