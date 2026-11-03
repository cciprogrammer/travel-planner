# -*- coding: utf-8 -*-
"""行程生成实测脚本：跑 N 次真实大模型生成，记录耗时与质检结果。

用法：
    python metric_run.py [次数]
结果写入 logs/metric-result.json，同时打印汇总表。
依赖：仅 Python 标准库。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "logs", "metric-result.json")

CASES = [
    {"origin": "东莞", "destination": "长沙", "days": 2, "budget": 1500, "hobbies": "美食,夜景", "preferences": "节奏轻松"},
    {"origin": "东莞", "destination": "成都", "days": 3, "budget": 3000, "hobbies": "美食,熊猫", "preferences": "人文优先"},
    {"origin": "广州", "destination": "北京", "days": 3, "budget": 5000, "hobbies": "历史,博物馆", "preferences": "经典route"},
    {"origin": "东莞", "destination": "杭州", "days": 2, "budget": 2000, "hobbies": "自然风光,摄影", "preferences": "少走路"},
    {"origin": "深圳", "destination": "厦门", "days": 3, "budget": 3500, "hobbies": "海边,美食", "preferences": "亲子"},
    {"origin": "东莞", "destination": "西安", "days": 4, "budget": 4000, "hobbies": "历史,美食", "preferences": "深度游"},
    {"origin": "东莞", "destination": "重庆", "days": 2, "budget": 1800, "hobbies": "美食,夜景", "preferences": "打卡为主"},
    {"origin": "佛山", "destination": "广州", "days": 3, "budget": 2800, "hobbies": "购物,美食", "preferences": "城市漫游"},
    {"origin": "东莞", "destination": "昆明", "days": 4, "budget": 4500, "hobbies": "自然风光", "preferences": "慢节奏"},
    {"origin": "东莞", "destination": "长沙", "days": 3, "budget": 2500, "hobbies": "美食,购物", "preferences": "中等强度"},
]


def request(method, path, body=None, token=None, timeout=180):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def main():
    rounds = int(sys.argv[1]) if len(sys.argv) > 1 else len(CASES)
    stamp = time.strftime("%m%d%H%M%S")
    username = "metric_" + stamp
    password = "Metric@123456"

    st, reg = request("POST", "/api/auth/register", {"username": username, "password": password, "nickname": "指标测试"})
    print("register:", st, reg.get("code"), reg.get("message"))
    if reg.get("code") != 200:
        print("注册失败，终止")
        return 1

    st, login = request("POST", "/api/auth/login", {"username": username, "password": password})
    token = (login.get("data") or {}).get("token")
    print("login:", st, "token=", "ok" if token else "missing")
    if not token:
        print(login)
        return 1

    rows = []
    for i in range(rounds):
        case = CASES[i % len(CASES)]
        t0 = time.time()
        st, gen = request("POST", "/api/plans/generate", case, token)
        elapsed = time.time() - t0
        plan_id = (gen.get("data") or {}).get("planId")
        row = {"seq": i + 1, "case": case, "http": st, "code": gen.get("code"),
               "planId": plan_id, "clientMs": round(elapsed * 1000)}
        if plan_id:
            st2, detail = request("GET", "/api/plans/%s" % plan_id, None, token)
            it = (detail.get("data") or {}).get("itinerary") or {}
            q = it.get("quality") or {}
            row["quality"] = {
                "passed": q.get("passed"),
                "score": q.get("score"),
                "issueCount": q.get("issueCount"),
                "source": q.get("source"),
                "rules": sorted({x.get("rule") for x in (q.get("issues") or []) if x.get("rule")}),
                "levels": sorted({x.get("level") for x in (q.get("issues") or []) if x.get("level")}),
                # 三级链路轨迹：stage/action/trigger/issuesBefore/issuesAfter/adopted
                "stages": [
                    {"stage": s.get("stage"), "action": s.get("action"),
                     "trigger": s.get("trigger"), "before": s.get("issuesBefore"),
                     "after": s.get("issuesAfter"), "adopted": s.get("adopted")}
                    for s in (q.get("stages") or [])
                ],
            }
            row["days"] = len(it.get("days") or [])
        else:
            row["quality"] = None
            row["msg"] = gen.get("message")
        rows.append(row)
        q = row["quality"] or {}
        chain = " > ".join(
            "%s%s" % (s["stage"], "" if s["adopted"] else "(未采纳)")
            for s in (q.get("stages") or []) if s.get("stage") and s.get("adopted") is not None
            and s.get("action") not in ("自动校验",)
        ) or "-"
        print("#%02d %-4s %s天 耗时 %.1fs passed=%s issues=%s rules=%s chain=%s" % (
            row["seq"], case["destination"], case["days"], elapsed,
            q.get("passed"), q.get("issueCount"), ",".join(q.get("rules") or []), chain), flush=True)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"username": username, "rows": rows}, f, ensure_ascii=False, indent=2)
    print("saved:", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
