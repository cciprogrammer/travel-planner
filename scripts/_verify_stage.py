# -*- coding: utf-8 -*-
"""一次性校验：确认 quality.stages 在接口往返后仍然存在（防止 DTO 字段静默丢弃）。"""
import json
import time
import urllib.request

BASE = "http://127.0.0.1:8081"


def post(path, body, token=None):
    data = json.dumps(body, ensure_ascii=False).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method="POST")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(req, timeout=180) as r:
        return json.loads(r.read().decode())


def get(path, token):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + token})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(req, timeout=60) as r:
        return json.loads(r.read().decode())


u = "stage_%s" % time.strftime("%m%d%H%M%S")
post("/api/auth/register", {"username": u, "password": "Stage@123456", "nickname": "链路校验"})
token = post("/api/auth/login", {"username": u, "password": "Stage@123456"})["data"]["token"]
rid = post("/api/plans/generate", {"origin": "东莞", "destination": "长沙", "days": 2, "budget": 1500}, token)["data"]["planId"]
d = get("/api/plans/%s" % rid, token)["data"]["itinerary"]["quality"]
print("passed=%s score=%s issueCount=%s source=%s" % (d.get("passed"), d.get("score"), d.get("issueCount"), d.get("source")))
for s in d.get("stages") or []:
    print("  [%s] %s | 触发: %s | %s→%s | 采纳=%s | %s" % (
        s.get("stage"), s.get("action"), s.get("trigger"),
        s.get("issuesBefore"), s.get("issuesAfter"), s.get("adopted"), s.get("note")))
if not d.get("stages"):
    print("!! stages 字段丢失 —— 序列化链路有问题")
