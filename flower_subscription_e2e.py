#!/usr/bin/env python3
"""flower-subscription E2E：通过业务应用 SSE 代理调用 DSH Agent，验证工具全链路。"""
import json, subprocess, sys

AGENT = "flower-copilot"
URL = "http://127.0.0.1:18105/api/assistant/stream"

CASES = [
    ("T1 套餐查询", "鲜花订阅有哪些套餐？轻氧周刊多少钱？简洁回答", ["轻氧周刊", "99"]),
    ("T2 订阅办理", "我是新客户唐小姐，电话13600001111，地址锦江区樱花路 8 号，想订阅轻氧周刊，每周一送，帮我办理订阅，告诉我订阅号", ["S5", "唐小姐"]),
    ("T3 订阅查询", "查一下订阅记录里有哪些客户？简洁回答", ["陈小姐", "S5001"]),
    ("T4 配送查询", "今天鲜花配送情况怎么样？简洁回答", ["D8001", "陈小姐"]),
    ("T5 养护咨询", "收到玫瑰鲜切花后怎么养护能开更久？简洁回答", ["玫瑰", "水"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== flower-subscription E2E: {passed}/{len(cases)} PASS =====")

if __name__ == "__main__":
    main()
