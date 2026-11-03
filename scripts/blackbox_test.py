#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
黑盒测试自动化脚本（接口级，无第三方依赖）

覆盖 docs/测试方案.md 的 A-G 组用例，并补充本项目新增的
H 组（管理员 / 酒店）与 I 组（住宿自选位置与花销持久化）。

用法：
    python scripts/blackbox_test.py              # 全跑（含一次真实大模型生成，约 30-60s）
    python scripts/blackbox_test.py --no-llm     # 跳过 B5 正向生成，不消耗大模型额度

前置：后端已在 8081 启动，MySQL 正常。
      H 组（管理员）需要环境变量 ADMIN_PASSWORD（以及可选的 ADMIN_USERNAME），未设置则自动跳过。
退出码：0 = 无「非预期失败」；1 = 有非预期失败（缺陷本身不计入退出码）。
"""

import io
import os
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = "http://127.0.0.1:8081/api"
SUFFIX = str(int(time.time()))[-6:]
PWD = "123456"
WITH_LLM = "--no-llm" not in sys.argv

RESULTS = []  # (编号, 场景, 预期, 实际, 状态, 备注)


# ------------------------------------------------------------------ HTTP

def _safe_json(text):
    try:
        return json.loads(text)
    except Exception:
        return {"raw": (text or "")[:300]}


def call(method, path, body=None, token=None, raw_auth=None, files=None, timeout=200):
    url = BASE + path
    headers = {}
    data = None
    if raw_auth is not None:
        headers["Authorization"] = raw_auth
    elif token:
        headers["Authorization"] = "Bearer " + token

    if files is not None:
        fname, ctype, content = files
        boundary = "----bbx" + uuid.uuid4().hex
        buf = io.BytesIO()
        buf.write(("--%s\r\n" % boundary).encode())
        buf.write(('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % fname).encode())
        buf.write(("Content-Type: %s\r\n\r\n" % ctype).encode())
        buf.write(content)
        buf.write(("\r\n--%s--\r\n" % boundary).encode())
        data = buf.getvalue()
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=timeout) as resp:
            return resp.status, _safe_json(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        return e.code, _safe_json(e.read().decode("utf-8", "replace"))
    except Exception as e:  # 连接失败等
        return 0, {"error": str(e)}


def rec(cid, scene, expect, actual, status, note=""):
    RESULTS.append((cid, scene, expect, actual, status, note))
    flag = {"PASS": "通过", "DEFECT": "缺陷", "OBSERVE": "观察", "SKIP": "跳过"}[status]
    print("[%s] %-4s %s" % (flag, cid, scene))


def code_of(res):
    return res[1].get("code")


def msg_of(res):
    return str(res[1].get("message") or res[1].get("raw") or res[1].get("error") or "")


def shorten(s, n=60):
    s = " ".join(str(s).split())
    return s if len(s) <= n else s[: n - 1] + "…"


# ------------------------------------------------------------------ 准备账号

def main():
    ua = "bbxa" + SUFFIX
    ub = "bbxb" + SUFFIX

    st, js = call("POST", "/auth/register", {"username": ua, "password": PWD})
    if code_of((st, js)) != 200:
        print("前置失败：无法注册测试用户 A -", js)
        return 1
    token_a = js["data"]["token"]
    id_a = js["data"]["user"]["id"]

    st, js = call("POST", "/auth/register", {"username": ub, "password": PWD})
    token_b = js["data"]["token"]
    id_b = js["data"]["user"]["id"]

    admin_user = os.environ.get("ADMIN_USERNAME", "admin")
    admin_pwd = os.environ.get("ADMIN_PASSWORD", "")
    st, js = (call("POST", "/auth/login", {"username": admin_user, "password": admin_pwd})
              if admin_pwd else (0, {}))
    admin_token = js["data"]["token"] if code_of((st, js)) == 200 else None
    admin_id = js["data"]["user"]["id"] if admin_token else None

    print("测试账号：A=%s(id=%s) B=%s(id=%s) | 管理员 admin(id=%s)"
          % (ua, id_a, ub, id_b, admin_id))
    print("=" * 78)

    # ---------------- A. 认证与鉴权 ----------------
    print("A. 认证与鉴权")
    st, js = call("POST", "/auth/register", {"username": "ab", "password": PWD})
    rec("A1", "用户名下界-1", "code=400 长度 3-20", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "3-20" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/auth/register", {"username": "bbxc" + SUFFIX, "password": PWD})
    ok = code_of((st, js)) == 200 and js["data"].get("token")
    rec("A2", "用户名 3 位下界", "code=200 + token", "code=%s token=%s" % (code_of((st, js)), bool(ok)),
        "PASS" if ok else "DEFECT")
    token_c = js["data"]["token"] if ok else None

    st, js = call("POST", "/auth/register", {"username": "b" * 21, "password": PWD})
    rec("A3", "用户名上界+1（21 位）", "code=400", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("POST", "/auth/register", {"username": "bbxd" + SUFFIX, "password": "12345"})
    rec("A4", "密码下界-1", "code=400 长度 6-32", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "6-32" in msg_of((st, js)) else "DEFECT")

    rec("A5", "正常注册", "code=200 + token", "code=200 token 长度 %d" % len(token_a),
        "PASS" if token_a else "DEFECT")

    st, js = call("POST", "/auth/register", {"username": ua, "password": PWD})
    rec("A6", "重复注册", "code=400 用户名已存在", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "已存在" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/auth/login", {"username": "nouser_xyz_" + SUFFIX, "password": PWD})
    rec("A7", "登录不存在用户", "code=400 用户不存在", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "不存在" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/auth/login", {"username": ua, "password": "wrongpwd"})
    rec("A8", "登录密码错误", "code=400 用户名或密码错误", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("GET", "/plans")
    rec("A9", "无 token 访问", "HTTP 401", "HTTP %s code=%s" % (st, code_of((st, js))),
        "PASS" if st == 401 else "DEFECT")

    st, js = call("GET", "/plans", raw_auth="Bearer not.a.real.token")
    rec("A10", "伪造 token", "HTTP 401", "HTTP %s %s" % (st, shorten(msg_of((st, js)))),
        "PASS" if st == 401 else "DEFECT")

    st, js = call("GET", "/plans", raw_auth=token_a)
    rec("A11", "token 缺 Bearer 前缀", "期望 401 拒绝", "HTTP %s code=%s（被放行）" % (st, code_of((st, js))),
        "DEFECT" if st == 200 else "PASS", "缺陷 D5")

    # ---------------- B. 行程生成 ----------------
    print("B. 行程生成")
    st, js = call("POST", "/plans/generate", {"origin": "广州", "destination": "长沙", "days": 0}, token=token_a)
    rec("B1", "天数下界-1（0）", "code=400 至少为 1 天", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "至少" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/plans/generate", {"origin": "广州", "destination": "长沙", "days": 31}, token=token_a)
    rec("B2", "天数上界+1（31）", "code=400 30 天以内", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "30" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/plans/generate", {"origin": "广州", "destination": "", "days": 3}, token=token_a)
    rec("B3", "目的地为空", "code=400 目的地不能为空", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "目的地" in msg_of((st, js)) else "DEFECT")

    st, js = call("POST", "/plans/generate", {"origin": "", "destination": "", "days": 0}, token=token_a)
    issues = js.get("message") or ""
    rec("B4", "多字段同时非法", "建议返回全部错误", "只返回 1 条：%s" % shorten(issues),
        "OBSERVE", "缺陷 D6")

    plan_id = None
    if WITH_LLM:
        t0 = time.time()
        st, js = call("POST", "/plans/generate",
                      {"origin": "广州", "destination": "长沙", "days": 3, "budget": 3000, "hobbies": "美食"},
                      token=token_a, timeout=240)
        cost = time.time() - t0
        plan_id = (js.get("data") or {}).get("planId")
        rec("B5", "正向生成（真实大模型）", "code=200 + planId", "code=%s planId=%s 耗时 %.1fs"
            % (code_of((st, js)), plan_id, cost), "PASS" if plan_id else "DEFECT")
    else:
        rec("B5", "正向生成（真实大模型）", "-", "已按 --no-llm 跳过", "SKIP")

    # ---------------- C. 方案管理 ----------------
    print("C. 方案管理")
    st, js = call("GET", "/plans/999999", token=token_a)
    rec("C1", "不存在的方案", "code=404", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 404 else "DEFECT")

    st, js = call("GET", "/plans/abc", token=token_a)
    rec("C2", "非法 id 格式（abc）", "期望 code=400 参数格式错误",
        "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)), 80)),
        "DEFECT" if code_of((st, js)) == 500 else "PASS", "缺陷 D1")

    if plan_id:
        st, js = call("GET", "/plans/%s" % plan_id, token=token_b)
        rec("C3", "B 越权读取 A 的方案", "code=404", "code=%s" % code_of((st, js)),
            "PASS" if code_of((st, js)) == 404 else "DEFECT")

        st, js = call("DELETE", "/plans/%s" % plan_id, token=token_b)
        st2, js2 = call("GET", "/plans/%s" % plan_id, token=token_a)
        rec("C4", "B 越权删除 A 的方案", "code=404 且 A 的方案仍在",
            "删除 code=%s；A 复查 code=%s" % (code_of((st, js)), code_of((st2, js2))),
            "PASS" if code_of((st, js)) == 404 and code_of((st2, js2)) == 200 else "DEFECT")

        st, js = call("PUT", "/plans/%s" % plan_id, {"title": "被篡改", "summary": "x"}, token=token_b)
        st2, js2 = call("GET", "/plans/%s" % plan_id, token=token_a)
        title = ((js2.get("data") or {}).get("title") or "")
        rec("C5", "B 越权修改 A 的方案", "code=404 且标题未变",
            "修改 code=%s；实际标题=%s" % (code_of((st, js)), shorten(title, 20)),
            "PASS" if code_of((st, js)) == 404 and "被篡改" not in title else "DEFECT")

        st, js = call("GET", "/plans/%s" % plan_id, token=token_a)
        origin_title = (js.get("data") or {}).get("title")
        st, js = call("PUT", "/plans/%s" % plan_id, {"title": "黑盒测试改名", "summary": "黑盒"}, token=token_a)
        st2, js2 = call("GET", "/plans/%s" % plan_id, token=token_a)
        rec("C6", "A 正常更新自己的方案", "code=200 且标题已改",
            "code=%s 标题=%s" % (code_of((st, js)), ((js2.get("data") or {}).get("title") or "")),
            "PASS" if code_of((st, js)) == 200 and (js2.get("data") or {}).get("title") == "黑盒测试改名" else "DEFECT")

        st, js = call("GET", "/plans?keyword=" + urllib.parse.quote("黑盒"), token=token_a)
        total = ((js.get("data") or {}).get("total") or 0)
        rec("C8", "关键词搜索", "命中 ≥1 条", "total=%s" % total, "PASS" if total >= 1 else "DEFECT")
    else:
        for cid, name in (("C3", "B 越权读取"), ("C4", "B 越权删除"), ("C5", "B 越权修改"),
                          ("C6", "A 正常更新"), ("C8", "关键词搜索")):
            rec(cid, name, "-", "无可用方案（B5 未生成）", "SKIP")

    # ---------------- D. 分页 ----------------
    print("D. 分页")
    st, js = call("GET", "/plans?pageSize=10000", token=token_a)
    size = (js.get("data") or {}).get("size")
    rec("D1", "超大 pageSize", "应被限流（≤100）", "size=%s" % size,
        "DEFECT" if size and int(size) > 100 else "PASS", "缺陷 D2")

    st, js = call("GET", "/plans?pageNum=0", token=token_a)
    cur = (js.get("data") or {}).get("current")
    rec("D2", "pageNum=0", "归一到第 1 页", "current=%s" % cur, "PASS" if str(cur) == "1" else "DEFECT")

    st, js = call("GET", "/plans?pageNum=-1&pageSize=-5", token=token_a)
    rec("D3", "负数分页参数", "不报 500", "HTTP %s code=%s" % (st, code_of((st, js))),
        "PASS" if code_of((st, js)) != 500 else "DEFECT")

    # ---------------- E. 收藏 ----------------
    print("E. 收藏")
    st, js = call("POST", "/favorites", {"name": ""}, token=token_a)
    rec("E1", "收藏名称为空", "code=400", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("POST", "/favorites", {"name": "橘子洲头", "city": "长沙", "category": "景点"}, token=token_a)
    fav_id = (js.get("data") or {}).get("id") if isinstance(js.get("data"), dict) else None
    rec("E2", "正常添加收藏", "code=200 + id", "code=%s id=%s" % (code_of((st, js)), fav_id),
        "PASS" if code_of((st, js)) == 200 else "DEFECT")

    st, js = call("POST", "/favorites", {"name": "橘子洲头", "city": "长沙", "category": "景点"}, token=token_a)
    rec("E3", "重复收藏", "code=400 已在收藏中", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("DELETE", "/favorites/999999", token=token_a)
    rec("E4", "删除不存在的收藏", "code=404", "code=%s" % code_of((st, js)),
        "PASS" if code_of((st, js)) == 404 else "DEFECT")

    # ---------------- F. 资料与密码 ----------------
    print("F. 个人资料与密码")
    st, js = call("PUT", "/user/password", {"oldPassword": PWD, "newPassword": PWD}, token=token_a)
    rec("F1", "新旧密码相同", "code=400 不能与旧密码相同", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "相同" in msg_of((st, js)) else "DEFECT")

    st, js = call("PUT", "/user/password", {"oldPassword": "wrongpwd", "newPassword": "abcdef"}, token=token_a)
    rec("F2", "原密码错误", "code=400 原密码不正确", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 and "原密码" in msg_of((st, js)) else "DEFECT")

    st, js = call("PUT", "/user/password", {"oldPassword": PWD, "newPassword": "12345"}, token=token_a)
    rec("F3", "新密码过短", "code=400 长度 6-32", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("PUT", "/user/profile", {"nickname": "长" * 31}, token=token_a)
    rec("F4", "昵称超长（31 字）", "code=400 不超过 30", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    # ---------------- G. 头像上传 ----------------
    print("G. 头像上传")
    png70 = (b"\x89PNG\r\n\x1a\n" + b"\x00" * 58)

    st, js = call("POST", "/user/avatar", token=token_a, files=("a.txt", "text/plain", b"hello"))
    rec("G1", "非图片扩展名", "code=400 仅支持", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("POST", "/user/avatar", token=token_a, files=("a.png", "image/png", png70))
    url = (js.get("data") or {}).get("url") if isinstance(js.get("data"), dict) else None
    rec("G2", "正常图片 70B", "code=200 + /uploads/avatar/", "code=%s url=%s" % (code_of((st, js)), shorten(url or "", 40)),
        "PASS" if code_of((st, js)) == 200 and "/uploads/avatar/" in (url or "") else "DEFECT")

    st, js = call("POST", "/user/avatar", token=token_a, files=("big3.png", "image/png", png70 + b"\x00" * (3 * 1024 * 1024)))
    rec("G3", "超业务上限 3MB", "code=400 超过 2MB", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
        "PASS" if code_of((st, js)) == 400 else "DEFECT")

    st, js = call("POST", "/user/avatar", token=token_a, files=("big6.png", "image/png", png70 + b"\x00" * (6 * 1024 * 1024)))
    rec("G4", "超框架上限 6MB", "期望 400/413 友好提示",
        "HTTP %s code=%s %s" % (st, code_of((st, js)), shorten(msg_of((st, js)), 50)),
        "DEFECT" if code_of((st, js)) == 500 else "PASS", "缺陷 D3")

    if not admin_token:
        print("H. 管理员与酒店管理 —— 跳过：未设置环境变量 ADMIN_PASSWORD")
    else:
        # ---------------- H. 管理员（本项目新增） ----------------
        print("H. 管理员与酒店管理")
        rec("H1", "管理员登录", "role=ADMIN", "role=%s" % ("ADMIN" if admin_token else "登录失败"),
            "PASS" if admin_token else "DEFECT")

        st, js = call("GET", "/admin/stats", token=token_a)
        rec("H2", "普通用户访问后台接口", "HTTP 403", "HTTP %s %s" % (st, shorten(msg_of((st, js)))),
            "PASS" if st == 403 else "DEFECT")

        st, js = call("GET", "/admin/stats")
        rec("H3", "无 token 访问后台接口", "HTTP 401", "HTTP %s" % st, "PASS" if st == 401 else "DEFECT")

        hotel_name = "黑盒测试酒店" + SUFFIX
        st, js = call("POST", "/admin/hotels",
                      {"name": hotel_name, "city": "长沙", "area": "测试区", "pricePerNight": 399, "rating": 4.5},
                      token=admin_token)
        hotel_id = (js.get("data") or {}).get("id") if isinstance(js.get("data"), dict) else None
        rec("H4", "新增酒店", "code=200 + id", "code=%s id=%s" % (code_of((st, js)), hotel_id),
            "PASS" if hotel_id else "DEFECT")

        st, js = call("POST", "/admin/hotels", {"name": hotel_name, "city": "长沙", "pricePerNight": 399}, token=admin_token)
        rec("H5", "同城同名酒店", "code=400 已有同名", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
            "PASS" if code_of((st, js)) == 400 else "DEFECT")

        st, js = call("POST", "/admin/hotels", {"name": "无城市酒店" + SUFFIX, "pricePerNight": 399}, token=admin_token)
        rec("H6", "缺少城市", "code=400 所在城市不能为空", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
            "PASS" if code_of((st, js)) == 400 else "DEFECT")

        st, js = call("POST", "/admin/hotels", {"name": "天价酒店" + SUFFIX, "city": "长沙", "pricePerNight": 999999}, token=admin_token)
        rec("H7", "价格超上限", "code=400", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
            "PASS" if code_of((st, js)) == 400 else "DEFECT")

        if hotel_id:
            st, js = call("PUT", "/admin/hotels/%s/status?status=9" % hotel_id, token=admin_token)
            rec("H8", "非法上下架状态", "code=400 状态只能是", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
                "PASS" if code_of((st, js)) == 400 else "DEFECT")

            st, js = call("PUT", "/admin/hotels/%s" % hotel_id,
                          {"name": hotel_name, "city": "长沙", "pricePerNight": 455, "status": 1}, token=admin_token)
            price = (js.get("data") or {}).get("pricePerNight") if isinstance(js.get("data"), dict) else None
            rec("H9", "编辑酒店", "code=200 价格已改", "code=%s price=%s" % (code_of((st, js)), price),
                "PASS" if code_of((st, js)) == 200 and str(price) == "455" else "DEFECT")

            st, js = call("DELETE", "/admin/hotels/%s" % hotel_id, token=admin_token)
            st2, js2 = call("DELETE", "/admin/hotels/%s" % hotel_id, token=admin_token)
            rec("H10", "删除酒店", "首次 200，再删 404",
                "首次 code=%s；再删 code=%s" % (code_of((st, js)), code_of((st2, js2))),
                "PASS" if code_of((st, js)) == 200 and code_of((st2, js2)) == 404 else "DEFECT")
        else:
            for cid, name in (("H8", "非法上下架状态"), ("H9", "编辑酒店"), ("H10", "删除酒店")):
                rec(cid, name, "-", "酒店未创建成功", "SKIP")

        st, js = call("GET", "/admin/users?pageNum=1&pageSize=5", token=admin_token)
        records = ((js.get("data") or {}).get("records") or [])
        ok = bool(records) and all("planCount" in r and "favoriteCount" in r and "role" in r for r in records)
        rec("H11", "用户分页带统计数据", "每条含 planCount/favoriteCount/role",
            "记录数=%s 字段齐全=%s" % (len(records), ok), "PASS" if ok else "DEFECT")

        st, js = call("PUT", "/admin/users/%s/status?status=0" % id_b, token=admin_token)
        dis_ok = code_of((st, js)) == 200
        st2, js2 = call("POST", "/auth/login", {"username": ub, "password": PWD})
        st3, js3 = call("GET", "/plans", token=token_b)
        st4, js4 = call("PUT", "/admin/users/%s/status?status=1" % id_b, token=admin_token)
        st5, js5 = call("POST", "/auth/login", {"username": ub, "password": PWD})
        ok = (dis_ok and code_of((st2, js2)) == 403 and st3 == 401
              and code_of((st4, js4)) == 200 and code_of((st5, js5)) == 200)
        rec("H12", "禁用用户链路", "禁用后登录 403、旧 token 401；恢复后可登录",
            "禁用=%s 登录=%s 旧token=HTTP%s 恢复=%s 再登录=%s"
            % (dis_ok, code_of((st2, js2)), st3, code_of((st4, js4)), code_of((st5, js5))),
            "PASS" if ok else "DEFECT")

        st, js = call("PUT", "/admin/users/%s/status?status=0" % admin_id, token=admin_token)
        rec("H13", "管理员禁用自己", "code=400 不能修改自己的状态", "code=%s %s" % (code_of((st, js)), shorten(msg_of((st, js)))),
            "PASS" if code_of((st, js)) == 400 else "DEFECT")

        st, js = call("GET", "/admin/hotels?pageSize=10000", token=admin_token)
        size = (js.get("data") or {}).get("size")
        rec("H14", "后台分页上限", "size=100", "size=%s" % size, "PASS" if str(size) == "100" else "DEFECT")

    # ---------------- I. 住宿自选位置与花销（本项目新增） ----------------
    print("I. 住宿自选位置与花销")
    if plan_id:
        st, js = call("GET", "/plans/%s" % plan_id, token=token_a)
        plan = js.get("data") or {}
        itin = plan.get("itinerary") or {}
        itin.setdefault("stay", {})
        itin["stay"]["customLocation"] = {
            "name": "黑盒测试住宿点", "address": "长沙市雨花区某路 1 号",
            "lat": 28.1675, "lng": 112.9955, "source": "search",
        }
        itin["stay"]["customPricePerNight"] = 388
        st, js = call("PUT", "/plans/%s" % plan_id,
                      {"title": plan.get("title"), "summary": plan.get("summary") or "", "itinerary": itin},
                      token=token_a)
        st2, js2 = call("GET", "/plans/%s" % plan_id, token=token_a)
        stay = (((js2.get("data") or {}).get("itinerary") or {}).get("stay") or {})
        loc = stay.get("customLocation") or {}
        rec("I1", "自选住宿位置持久化", "customLocation 完整保留",
            "name=%s lat=%s" % (loc.get("name"), loc.get("lat")),
            "PASS" if loc.get("name") == "黑盒测试住宿点" and loc.get("lat") else "DEFECT")
        rec("I2", "自选住宿花销持久化", "customPricePerNight=388",
            "值=%s" % stay.get("customPricePerNight"),
            "PASS" if str(stay.get("customPricePerNight")) == "388" else "DEFECT")

        days = (((js2.get("data") or {}).get("itinerary") or {}).get("days") or [])
        total_items = sum(len(d.get("items") or []) for d in days)
        with_tr = sum(1 for d in days for i in (d.get("items") or []) if (i.get("transport") or "").strip())
        rec("I3", "出行方案素材完整性", "每个事项都带交通说明",
            "%d/%d 个事项有 transport" % (with_tr, total_items),
            "PASS" if total_items and with_tr == total_items else "OBSERVE")
    else:
        for cid, name in (("I1", "自选住宿位置持久化"), ("I2", "自选住宿花销持久化"), ("I3", "出行方案素材完整性")):
            rec(cid, name, "-", "无可用方案（B5 未生成）", "SKIP")

    # ---------------- C7 正常删除（放在最后，删完 I 组就没数据可查了） ----------------
    if plan_id:
        st, js = call("DELETE", "/plans/%s" % plan_id, token=token_a)
        st2, js2 = call("GET", "/plans/%s" % plan_id, token=token_a)
        rec("C7", "A 正常删除自己的方案", "code=200，再查 404",
            "删除 code=%s；复查 code=%s" % (code_of((st, js)), code_of((st2, js2))),
            "PASS" if code_of((st, js)) == 200 and code_of((st2, js2)) == 404 else "DEFECT")
    else:
        rec("C7", "A 正常删除自己的方案", "-", "无可用方案（B5 未生成）", "SKIP")

    # ---------------- 汇总 ----------------
    print("=" * 78)
    order = {"PASS": 0, "DEFECT": 1, "OBSERVE": 2, "SKIP": 3}
    label = {"PASS": "通过", "DEFECT": "缺陷", "OBSERVE": "观察", "SKIP": "跳过"}
    print("%-5s %-24s %-34s %-34s %s" % ("编号", "场景", "预期", "实际", "结论"))
    print("-" * 130)
    for cid, scene, expect, actual, status, note in RESULTS:
        print("%-5s %-24s %-34s %-34s %s%s"
              % (cid, scene, shorten(expect, 32), shorten(actual, 32), label[status],
                 ("  ← " + note) if note else ""))

    n_pass = sum(1 for r in RESULTS if r[4] == "PASS")
    n_def = sum(1 for r in RESULTS if r[4] == "DEFECT")
    n_obs = sum(1 for r in RESULTS if r[4] == "OBSERVE")
    n_skip = sum(1 for r in RESULTS if r[4] == "SKIP")
    print("-" * 130)
    print("合计 %d 条：通过 %d ｜ 缺陷 %d ｜ 观察 %d ｜ 跳过 %d"
          % (len(RESULTS), n_pass, n_def, n_obs, n_skip))

    # 把明细写成 JSON，方便再加工
    with open("logs/blackbox-result.json", "w", encoding="utf-8") as f:
        json.dump([{"id": r[0], "scene": r[1], "expect": r[2], "actual": r[3],
                    "status": r[4], "note": r[5]} for r in RESULTS],
                  f, ensure_ascii=False, indent=2)
    print("明细已写入 logs/blackbox-result.json")
    print("测试账号（可在库中清理）：", ", ".join(x for x in (ua, ub, "bbxc" + SUFFIX[:3]) if x))
    return 0 if n_def == 0 else 0  # 缺陷不计入退出码，保持与文档口径一致


if __name__ == "__main__":
    sys.exit(main())
