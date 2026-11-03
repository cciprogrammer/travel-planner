#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
DeepSeek 接入自检脚本 —— 一条命令定位「为什么大模型调不通」。

用法：
    python scripts/check-deepseek.py
    python scripts/check-deepseek.py --api-key sk-xxxx --model deepseek-flash

它会依次检查：配置读取 → 网络连通 → 密钥鉴权 → 账户余额 → 模型名 → 真实对话调用，
每项结论都给出「正常 / 异常 + 原因 + 该怎么改」。

退出码：0 = 全部通过；1 = 存在阻断问题。
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
RES = os.path.join(PROJECT, "backend", "src", "main", "resources")

OK, BAD, WARN = "[通过]", "[失败]", "[注意]"


def parse_yaml_value(path, keys):
    """不依赖 pyyaml：在指定 yml 里找出指定键的值，并解析 ${ENV:default} 形式。"""
    if not os.path.exists(path):
        return {}
    found = {}
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    for key in keys:
        # 匹配形如  api-key: ${DEEPSEEK_API_KEY:sk-xxx}  或  model: deepseek-flash
        m = re.search(r"^\s*" + re.escape(key) + r"\s*:\s*(.+?)\s*$", text, re.M)
        if not m:
            continue
        raw = m.group(1).strip().strip('"').strip("'")
        env = re.match(r"^\$\{([A-Za-z0-9_]+)(?::(.*))?\}$", raw)
        if env:
            name, default = env.group(1), (env.group(2) or "")
            found[key] = os.environ.get(name) or default
        else:
            found[key] = raw
    return found


def load_config(explicit_key, explicit_model):
    """local profile 会覆盖 application.yml，所以按覆盖顺序取值。"""
    base = parse_yaml_value(os.path.join(RES, "application.yml"),
                            ["api-key", "base-url", "model", "timeout-seconds"])
    local = parse_yaml_value(os.path.join(RES, "application-local.yml"),
                             ["api-key", "base-url", "model"])
    cfg = dict(base)
    cfg.update({k: v for k, v in local.items() if v})
    if os.environ.get("DEEPSEEK_API_KEY"):
        cfg["api-key"] = os.environ["DEEPSEEK_API_KEY"]
    if explicit_key:
        cfg["api-key"] = explicit_key
    if explicit_model:
        cfg["model"] = explicit_model
    cfg.setdefault("base-url", "https://api.deepseek.com")
    cfg.setdefault("model", "deepseek-flash")
    return cfg


def call(url, key=None, payload=None, timeout=60):
    """返回 (http_status, 解析后的 json 或原始文本, 异常信息)。"""
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET")
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("Authorization", "Bearer " + key)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(body), None
            except Exception:
                return r.status, body, None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(body), None
        except Exception:
            return e.code, body, None
    except Exception as e:
        return None, None, str(e)


def err_message(payload, fallback=""):
    if isinstance(payload, dict):
        return (payload.get("error") or {}).get("message") or fallback
    return fallback


def mask(key):
    if not key:
        return "(未配置)"
    return "****" + key[-4:] if len(key) > 4 else "****"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api-key", default=None, help="临时指定 Key，覆盖配置文件")
    ap.add_argument("--model", default=None, help="临时指定模型名")
    args = ap.parse_args()

    cfg = load_config(args.api_key, args.model)
    key, base, model = cfg.get("api-key", ""), cfg.get("base-url", "").rstrip("/"), cfg.get("model", "")

    print("=" * 68)
    print("DeepSeek 接入自检")
    print("=" * 68)
    print(f"  base-url : {base}")
    print(f"  model    : {model}")
    print(f"  api-key  : {mask(key)}")
    print(f"  来源     : 环境变量 DEEPSEEK_API_KEY -> application-local.yml -> application.yml（后者被前两者覆盖）")
    print("-" * 68)

    problems = []

    # 1. 配置完整性
    if not key:
        print(f"{BAD} 1/6 配置读取：api-key 为空，应用会直接走内置示例行程，不会调用大模型。")
        problems.append("配置 api-key：改 application-local.yml 的 deepseek.api-key，或设环境变量 DEEPSEEK_API_KEY")
    elif not key.startswith("sk-"):
        print(f"{WARN} 1/6 配置读取：Key 不是 sk- 开头（当前 {mask(key)}），格式可能有误。")
    else:
        print(f"{OK} 1/6 配置读取：已读取到 Key {mask(key)}")

    # 2. 网络连通
    status, payload, exc = call(base + "/models", key=key, timeout=20)
    if status is None:
        print(f"{BAD} 2/6 网络连通：无法访问 {base} —— {exc}")
        print("         → 检查网络/代理；公司网络或代理需放行 api.deepseek.com:443")
        problems.append("解决网络：确认能访问 https://api.deepseek.com（关掉代理或给代理加白名单）")
        print("=" * 68)
        return 1
    print(f"{OK} 2/6 网络连通：{base} 可达（HTTP {status}）")

    # 3. 密钥鉴权 + 5. 模型名（同一个接口可一次拿到）
    available = []
    if status == 200 and isinstance(payload, dict):
        available = [m.get("id") for m in payload.get("data", []) if isinstance(m, dict)]
        print(f"{OK} 3/6 密钥鉴权：Key 有效，账号可用模型 {available or '(接口未返回列表)'}")
    elif status == 401:
        print(f"{BAD} 3/6 密钥鉴权：401 未授权 —— {err_message(payload)}")
        print("         → Key 错误/已被删除/已重置；去 platform.deepseek.com 重新生成后替换配置")
        problems.append("换一个有效 Key：platform.deepseek.com → API keys → 新建，写入 application-local.yml")
    elif status == 402:
        print(f"{BAD} 3/6 密钥鉴权：402 余额不足 —— {err_message(payload)}")
        problems.append("充值后再试：platform.deepseek.com 费用中心")
    elif status == 403:
        print(f"{BAD} 3/6 密钥鉴权：403 无权限 —— {err_message(payload)}")
        problems.append("确认账号已开通该模型 / 未触发风控")
    else:
        print(f"{BAD} 3/6 密钥鉴权：HTTP {status} —— {err_message(payload, str(payload)[:120])}")
        problems.append(f"排查 HTTP {status}")

    # 4. 余额
    s2, p2, _ = call(base + "/user/balance", key=key, timeout=20)
    if s2 == 200 and isinstance(p2, dict):
        infos = p2.get("balance_infos") or []
        if infos:
            for b in infos:
                total = float(b.get("total_balance") or 0)
                flag = OK if total > 1 else WARN
                print(f"{flag} 4/6 账户余额：{b.get('currency')} {total}（可用={p2.get('is_available')}）")
                if total <= 1:
                    print("         → 余额偏低，长行程生成可能中途 402，建议充值")
        else:
            print(f"{OK} 4/6 账户余额：接口正常（未返回明细）")
    elif s2 in (401, 403):
        print(f"{WARN} 4/6 账户余额：该 Key 无权查询余额（HTTP {s2}），不影响调用")
    else:
        print(f"{WARN} 4/6 账户余额：查询失败（HTTP {s2}）")

    # 5. 模型名（仅提示：/models 列表可能不含兼容别名，最终以第 6 步实测为准）
    model_unlisted = False
    if available:
        if model in available:
            print(f"{OK} 5/6 模型名称：{model} 在 /models 可用列表中")
        else:
            model_unlisted = True
            print(f"{WARN} 5/6 模型名称：{model} 不在 /models 返回的列表里（列表为 {available}）。")
            print("         该服务可能把它作为兼容别名，是否真能用看第 6 步实测结果。")
    else:
        print(f"{WARN} 5/6 模型名称：无法校验（未拿到模型列表），继续用真实调用验证")

    # 6. 真实对话调用（带 json_object，和业务代码参数一致）
    s3, p3, exc3 = call(base + "/chat/completions", key=key, payload={
        "model": model, "stream": False, "temperature": 0.7,
        # 注意：deepseek-flash / deepseek-v4-pro 会先输出 reasoning_content（思考过程），
        # 探针的 max_tokens 给小了会出现「content 为空 + finish_reason=length」的假故障。
        "response_format": {"type": "json_object"}, "max_tokens": 1024,
        "messages": [{"role": "system", "content": "只输出一个合法 JSON 对象。"},
                     {"role": "user", "content": '输出 JSON：{"ok":true}'}],
    }, timeout=90)
    if s3 == 200 and isinstance(p3, dict):
        ch = (p3.get("choices") or [{}])[0]
        content = ((ch.get("message") or {}).get("content") or "").strip()
        finish = ch.get("finish_reason")
        if content:
            print(f"{OK} 6/6 真实调用：成功（finish_reason={finish}，模型回显={p3.get('model')}）")
            print(f"         返回内容：{content[:80]}")
            if model_unlisted:
                print(f"{OK} 5/6 补充：{model} 虽未列入 /models，但实测可正常调用，作为兼容别名可用。")
            # 识别「推理模型陷阱」：短文能出，但长行程 JSON 会被思考过程吃光额度
            u = p3.get("usage") or {}
            rtok = (u.get("completion_tokens_details") or {}).get("reasoning_tokens") or 0
            if rtok > 0:
                print(f"{WARN} 6/6 注意：该模型本次消耗了 {rtok} 个思考 token，属于推理模型。")
                print("         → 短文本没问题，但生成整份行程 JSON 时容易被思考占满额度而只出思考、不出正文。")
                print("         → 建议 deepseek.model 用非推理的 deepseek-chat（实测 4 天行程 18 秒出完整 JSON）。")
        else:
            print(f"{BAD} 6/6 真实调用：返回内容为空（finish_reason={finish}）")
            if finish == "length":
                print("         → 输出被 max_tokens 截断，调大 deepseek.max-tokens")
                problems.append("调大 deepseek.max-tokens（如 8192）")
            else:
                problems.append("返回内容为空，检查请求参数")
    elif s3 == 400:
        print(f"{BAD} 6/6 真实调用：400 参数被拒 —— {err_message(p3)}")
        problems.append("按报错调整模型名 / 请求参数（response_format 需为 json_object）")
    elif s3 == 429:
        print(f"{WARN} 6/6 真实调用：429 触发限流 —— {err_message(p3)}")
        print("         → 降低并发或稍后重试（单账号同时请求过多会限流）")
    elif s3 in (401, 402, 403):
        print(f"{BAD} 6/6 真实调用：HTTP {s3} —— {err_message(p3)}")
        problems.append("回到第 3/4 步处理密钥或余额")
    else:
        print(f"{BAD} 6/6 真实调用：HTTP {s3} —— {err_message(p3, str(exc3)[:120])}")
        problems.append(f"排查 HTTP {s3}")

    print("=" * 68)
    if problems:
        print("结论：存在阻断问题，按下面顺序处理 ——")
        for i, p in enumerate(problems, 1):
            print(f"  {i}. {p}")
        print("=" * 68)
        return 1
    print("结论：全部通过，配置正确，可以直接正常调用大模型。")
    print("=" * 68)
    return 0


if __name__ == "__main__":
    sys.exit(main())
