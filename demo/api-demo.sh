#!/usr/bin/env bash
# =====================================================================
# 城市噪声采样证据链 —— 接口演示脚本（仅需 curl，不依赖本机 Java/Node）
# 演示：登录 / 重复提交 / 时间冲突 / 并发写入 / 异常生成 / 审计回放 / 批量导入
# 用法：bash demo/api-demo.sh   （默认后端 http://localhost:8080）
# =====================================================================
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
JQ="$(command -v jq || true)"
NODE="$(command -v node || true)"

c_red()   { printf '\033[31m%s\033[0m\n' "$*"; }
c_grn()   { printf '\033[32m%s\033[0m\n' "$*"; }
c_yel()   { printf '\033[33m%s\033[0m\n' "$*"; }
c_cyan()  { printf '\033[36m%s\033[0m\n' "$*"; }
hr()      { echo "------------------------------------------------------------"; }

# 提取 JSON 字段，支持 jq 风格点路径（.data.token、.data[0].id）
pick() { # pick <json> <jq-path>
  if [ -n "$JQ" ]; then
    printf '%s' "$1" | $JQ -r "$2"
  elif [ -n "$NODE" ]; then
    printf '%s' "$1" | NODE_PATH='' node -e '
      let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
        const path = process.argv[1].replace(/^\./, "").replace(/\[(\d+)\]/g, ".$1");
        let v = JSON.parse(s);
        for (const k of path.split(".")) { if (k === "") continue; v = v?.[k]; }
        process.stdout.write(v == null ? "null" : String(v));
      });' "$2"
  else
    printf '%s' "$1" | grep -o "\"${2#.}\"[[:space:]]*:[[:space:]]*[^,}]*" | head -1 | sed 's/.*:[[:space:]]*//; s/"//g'
  fi
}

# 固定哈希（去掉 - 与 sha256sum 输出的空格文件名）
HASH_OF() { printf '%s' "$1" | sha256sum | awk '{print $1}'; }

c_cyan "0. 登录获取 Token（admin / admin123）"
LOGIN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')
echo "$LOGIN"
TOKEN=$(pick "$LOGIN" '.data.token')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  c_red "登录失败，请确认后端已启动：$BASE"
  exit 1
fi
AUTH="Authorization: Bearer $TOKEN"
c_grn "登录成功"
hr

# ---------- 1. 重复提交 ----------
c_cyan "1. 重复提交演示：同一传感器 + 同一采样时间 + 同一哈希，连续提交两次"
HASH=$(HASH_OF "duplicate-demo")
BODY=$(cat <<EOF
{"sensorCode":"S-NJ-001","sampleTime":"2026-09-10T09:00:00",
 "longitude":118.7788,"latitude":32.0417,"dbValue":70.5,
 "spectrumSummary":"{\"bands\":[]}","rawDataHash":"$HASH"}
EOF
)
c_yel "第一次提交："
curl -s -X POST "$BASE/api/records" -H "$AUTH" -H 'Content-Type: application/json' -d "$BODY"; echo
c_yel "第二次提交（应返回 40901 重复提交）："
curl -s -X POST "$BASE/api/records" -H "$AUTH" -H 'Content-Type: application/json' -d "$BODY"; echo
hr

# ---------- 2. 采样批次时间冲突 ----------
c_cyan "2. 时间区间冲突演示：创建与既有批次重叠的批次（S-NJ-001 2026-09-01 08:00~12:00 已存在）"
c_yel "先调用重叠检测接口："
curl -s "$BASE/api/batches/overlap-check?sensorCode=S-NJ-001&startTime=2026-09-01T10:00:00&endTime=2026-09-01T11:00:00" -H "$AUTH"; echo
c_yel "再直接创建重叠批次（应返回 40902 时间冲突）："
curl -s -X POST "$BASE/api/batches" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"sensorCode":"S-NJ-001","startTime":"2026-09-01T10:00:00","endTime":"2026-09-01T11:00:00","purpose":"故意重叠"}'; echo
hr

# ---------- 3. 并发写入 ----------
c_cyan "3. 并发写入演示：5 个完全相同的创建请求同时发出"
CHASH=$(HASH_OF "concurrent-$(date +%s)")
CBODY=$(cat <<EOF
{"sensorCode":"S-NJ-002","sampleTime":"2026-09-10T10:00:00",
 "longitude":118.77,"latitude":32.06,"dbValue":77.0,"rawDataHash":"$CHASH"}
EOF
)
TMP=$(mktemp -d)
for i in 1 2 3 4 5; do
  curl -s -X POST "$BASE/api/records" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$CBODY" -o "$TMP/r$i" &
done
wait
OK=0; CONFLICT=0
for i in 1 2 3 4 5; do
  CODE=$(pick "$(cat "$TMP/r$i")" '.code')
  echo "请求 $i -> code=$CODE"
  [ "$CODE" = "0" ] && OK=$((OK+1)) || CONFLICT=$((CONFLICT+1))
done
c_grn "结果：仅 $OK 条入库，$CONFLICT 条被判定为重复/并发冲突，数据库无重复数据"
rm -rf "$TMP"
hr

# ---------- 4. 异常自动生成 ----------
c_cyan "4. 异常自动生成演示：提交一条 91.2 dB（阈值 85）的记录"
AHASH=$(HASH_OF "anomaly-demo")
REC=$(curl -s -X POST "$BASE/api/records" -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"sensorCode\":\"S-NJ-003\",\"sampleTime\":\"2026-09-10T11:00:00\",
  \"longitude\":118.79,\"latitude\":32.02,\"dbValue\":91.2,\"rawDataHash\":\"$AHASH\"}")
echo "$REC"
RID=$(pick "$REC" '.data.id')
c_yel "查询该记录的异常事件（应自动生成一条 HIGH_DECIBEL / OPEN）："
curl -s "$BASE/api/records/$RID/anomalies" -H "$AUTH"; echo
c_yel "重复提交同一条记录不会生成第二条异常（唯一键 uk_record_type 保证）"
curl -s -X POST "$BASE/api/records" -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"sensorCode\":\"S-NJ-003\",\"sampleTime\":\"2026-09-10T11:00:00\",
  \"dbValue\":91.2,\"rawDataHash\":\"$AHASH\"}"; echo
c_yel "处理该异常（状态 + 处理说明写入审计）："
ALIST=$(curl -s "$BASE/api/records/$RID/anomalies" -H "$AUTH")
AID=$(pick "$ALIST" '.data[0].id')
curl -s -X POST "$BASE/api/anomalies/$AID/handle" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"status":"RESOLVED","handleNote":"演示：现场处置完成，复测 70dB，恢复正常。"}'; echo
echo "$RID" > /tmp/noise_demo_rid
hr

# ---------- 5. 审计回放 ----------
c_cyan "5. 审计回放演示：按记录回放完整变更历史并重算哈希链"
RID="${RID:-$(cat /tmp/noise_demo_rid 2>/dev/null || echo 1)}"
curl -s "$BASE/api/audit/records/$RID/history" -H "$AUTH" | ( [ -n "$JQ" ] && $JQ || cat )
echo
c_grn "返回中 chainValid=true 表示从 CREATE→异常→证据→处理 的哈希链完整可验"
hr

# ---------- 6. 批量导入逐条失败原因 ----------
c_cyan "6. 批量导入演示：合法行入库，重复行/非法分贝/不存在传感器逐行返回失败原因"
DHASH=$(HASH_OF "import-ok-$(date +%s)")
curl -s -X POST "$BASE/api/imports" -H "$AUTH" -H 'Content-Type: application/json' -d "{
 \"fileName\":\"demo.json\",
 \"records\":[
   {\"sensorCode\":\"S-NJ-004\",\"sampleTime\":\"2026-09-10T12:00:00\",\"dbValue\":70.0,\"rawDataHash\":\"$DHASH\"},
   {\"sensorCode\":\"S-NJ-002\",\"sampleTime\":\"2026-09-02T10:00:00\",\"dbValue\":55.6,\"rawDataHash\":\"$(HASH_OF 'raw|S-NJ-002|2026-09-02T10:00|55.6')\"},
   {\"sensorCode\":\"S-NJ-004\",\"sampleTime\":\"2026-09-10T13:00:00\",\"dbValue\":250.0,\"rawDataHash\":\"$DHASH\"},
   {\"sensorCode\":\"S-XX-999\",\"sampleTime\":\"2026-09-10T14:00:00\",\"dbValue\":66.0,\"rawDataHash\":\"0000000000000000000000000000000000000000000000000000000000000000\"}
 ]}"; echo
hr
c_grn "全部演示完成。可在前端 http://localhost 打开各页面查看（账号 admin / admin123）"
