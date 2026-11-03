#!/usr/bin/env bash
# 旅行规划助手 接口冒烟测试
# 用法: bash scripts/smoke-test.sh [后端地址]
# 默认后端: http://localhost:8081

BASE="${1:-http://localhost:8081}/api"
U="smoke$(date +%s)"
P="123456"
AUTH_JSON="Content-Type: application/json"

# 本机探测要绕开系统代理，否则 curl 会走代理并拿到 502
export NO_PROXY="localhost,127.0.0.1"
export no_proxy="localhost,127.0.0.1"

hr() { echo; echo "----------------------------------------"; }

echo "后端地址: $BASE"

hr; echo "[1/8] 注册用户 $U"
REG=$(curl -s -m 30 -X POST "$BASE/auth/register" -H "$AUTH_JSON" \
  -d "{\"username\":\"$U\",\"password\":\"$P\",\"nickname\":\"冒烟测试\"}")
echo "$REG"
TOKEN=$(echo "$REG" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then echo "!! 未取到 token，终止"; exit 1; fi
echo "token(len)=${#TOKEN}"

hr; echo "[2/8] 登录"
curl -s -m 30 -X POST "$BASE/auth/login" -H "$AUTH_JSON" \
  -d "{\"username\":\"$U\",\"password\":\"$P\"}" | head -c 400
echo

AUTH="Authorization: Bearer $TOKEN"

hr; echo "[3/8] 查询个人资料"
curl -s -m 30 "$BASE/user/profile" -H "$AUTH" | head -c 400
echo

hr; echo "[4/8] 生成行程（大模型 / mock）"
GEN=$(curl -s -m 180 -X POST "$BASE/plans/generate" -H "$AUTH_JSON" -H "$AUTH" \
  -d '{"origin":"东莞","destination":"成都","days":3,"budget":3000,"hobbies":"美食,历史古迹","preferences":"节奏轻松，少走路","startDate":"2026-10-01","companions":"2人"}')
echo "$GEN"
PID=$(echo "$GEN" | sed -n 's/.*"planId":\([0-9]*\).*/\1/p')
if [ -z "$PID" ]; then echo "!! 未取到 planId，终止"; exit 1; fi
echo "planId=$PID"

hr; echo "[5/8] 方案详情"
curl -s -m 30 "$BASE/plans/$PID" -H "$AUTH" | head -c 900
echo

hr; echo "[6/8] 历史方案列表"
curl -s -m 30 "$BASE/plans?pageNum=1&pageSize=5" -H "$AUTH" | head -c 500
echo

hr; echo "[7/8] 新增地点收藏"
curl -s -m 30 -X POST "$BASE/favorites" -H "$AUTH_JSON" -H "$AUTH" \
  -d '{"name":"宽窄巷子","city":"成都","category":"景点","address":"成都市青羊区金河路口","description":"清代古街道","rating":"4.5"}'
echo

hr; echo "[8/8] 收藏列表"
curl -s -m 30 "$BASE/favorites" -H "$AUTH" | head -c 500
echo

hr; echo "SMOKE TEST DONE"
