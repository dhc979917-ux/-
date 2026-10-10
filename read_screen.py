import base64
import json
import os
import queue
import re
import threading

import keyboard
import mss
import pyttsx3
import requests
import tkinter as tk

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")

PROMPT = (
    "你是屏幕朗读助手。请把这张屏幕截图的内容整理成适合朗读的中文口语文本："
    "正文按原文朗读；图表、图片、图标、界面状态用一两句自然的语言描述；"
    "跳过广告和无关的导航按钮；"
    "只输出要朗读的文本本身，不要标题、不要解释、不要Markdown格式。"
)

with open(CONFIG_PATH, encoding="utf-8") as f:
    CONFIG = json.load(f)

commands = queue.Queue()
sentences = queue.Queue()


def split_sentences(text):
    text = re.sub(r"[*#`>_~|]+", "", text)
    parts = re.split(r"(?<=[。！？!?；;\n])", text)
    result = []
    for part in parts:
        part = part.strip()
        while len(part) > 60:
            cut = part.find("，", 20, 60)
            if cut == -1:
                cut = 60
            result.append(part[:cut].strip())
            part = part[cut:].lstrip("，")
        if part:
            result.append(part)
    return result


def tts_worker():
    engine = pyttsx3.init()
    for v in engine.getProperty("voices"):
        if "Chinese" in v.name or "zh" in str(v.languages).lower():
            engine.setProperty("voice", v.id)
            break
    engine.setProperty("rate", CONFIG.get("rate", 175))
    while True:
        item = sentences.get()
        if item:
            engine.say(item)
            engine.runAndWait()


def capture_png(region=None):
    with mss.mss() as sct:
        area = region or sct.monitors[1]
        shot = sct.grab(area)
        return mss.tools.to_png(shot.rgb, shot.size)


def ask_vlm(png_bytes):
    image_url = "data:image/png;base64," + base64.b64encode(png_bytes).decode()
    resp = requests.post(
        CONFIG["base_url"].rstrip("/") + "/chat/completions",
        headers={"Authorization": "Bearer " + CONFIG["api_key"]},
        json={
            "model": CONFIG["model"],
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": PROMPT},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                }
            ],
        },
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"]


def fetch_worker(region):
    try:
        text = ask_vlm(capture_png(region))
        for s in split_sentences(text):
            sentences.put(s)
    except Exception as e:
        sentences.put("识别失败。" + str(e))


def clear_speech():
    with sentences.mutex:
        sentences.queue.clear()


def select_region(root, scale, mon_left, mon_top):
    top = tk.Toplevel(root)
    top.attributes("-fullscreen", True)
    top.attributes("-alpha", 0.3)
    top.attributes("-topmost", True)
    top.configure(bg="black")
    canvas = tk.Canvas(top, bg="black", highlightthickness=0, cursor="crosshair")
    canvas.pack(fill=tk.BOTH, expand=True)
    box = [0, 0, 0, 0]

    def press(e):
        box[0], box[1], box[2], box[3] = e.x, e.y, e.x, e.y

    def drag(e):
        box[2], box[3] = e.x, e.y
        canvas.delete("sel")
        canvas.create_rectangle(*box, outline="cyan", width=2, tags="sel")

    def release(e):
        top.destroy()

    canvas.bind("<ButtonPress-1>", press)
    canvas.bind("<B1-Motion>", drag)
    canvas.bind("<ButtonRelease-1>", release)
    top.bind("<Escape>", lambda e: top.destroy())
    top.grab_set()
    root.wait_window(top)
    xs = sorted((box[0], box[2]))
    ys = sorted((box[1], box[3]))
    if xs[1] - xs[0] < 10 or ys[1] - ys[0] < 10:
        return None
    return {
        "left": mon_left + int(xs[0] * scale),
        "top": mon_top + int(ys[0] * scale),
        "width": int((xs[1] - xs[0]) * scale),
        "height": int((ys[1] - ys[0]) * scale),
    }


def main():
    root = tk.Tk()
    root.withdraw()
    with mss.mss() as sct:
        mon = sct.monitors[1]
    scale = mon["width"] / root.winfo_screenwidth()

    threading.Thread(target=tts_worker, daemon=True).start()

    def poll():
        try:
            while True:
                cmd = commands.get_nowait()
                if cmd == "stop":
                    clear_speech()
                elif cmd == "full":
                    clear_speech()
                    threading.Thread(target=fetch_worker, args=(None,), daemon=True).start()
                elif cmd == "region":
                    clear_speech()
                    region = select_region(root, scale, mon["left"], mon["top"])
                    if region:
                        threading.Thread(target=fetch_worker, args=(region,), daemon=True).start()
        except queue.Empty:
            pass
        root.after(80, poll)

    keyboard.add_hotkey(CONFIG.get("hotkey_full", "alt+r"), lambda: commands.put("full"))
    keyboard.add_hotkey(CONFIG.get("hotkey_region", "alt+s"), lambda: commands.put("region"))
    keyboard.add_hotkey(CONFIG.get("hotkey_stop", "alt+q"), lambda: commands.put("stop"))

    print("已启动：Alt+R 读全屏，Alt+S 框选朗读，Alt+Q 停止")
    poll()
    root.mainloop()


if __name__ == "__main__":
    main()
