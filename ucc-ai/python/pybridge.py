"""
长驻 Java 子进程管理 — 为 MCTS 训练提供高性能 PyBridge 会话。

基于 stdin/stdout 协议与 Java PyBridge 进程通信：
  - Python 发送 JSON 行 → Java 处理 → 返回 JSON 行
  - 一次启动，全程复用，消除 per-call 的 JVM 启停开销（~2-5秒/次）
"""

import json
import os
import subprocess
import sys
import time
from typing import Any, Dict, List, Optional

# ═══════════════════════════════════════════════════════════════════════════
# 路径常量
# ═══════════════════════════════════════════════════════════════════════════

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))))  # pybridge.py → python → ucc-ai → 项目根

def _find_gson() -> str:
    """定位 gson JAR。优先 target/lib/，回退 Maven 本地仓库。"""
    lib_dir = os.path.join(_PROJECT_ROOT, "ucc-ai", "target", "lib")
    if os.path.isdir(lib_dir):
        for f in os.listdir(lib_dir):
            if f.startswith("gson") and f.endswith(".jar"):
                return os.path.join(lib_dir, f)
    m2 = os.path.join(os.path.expanduser("~"), ".m2", "repository",
                       "com", "google", "code", "gson", "gson", "2.11.0",
                       "gson-2.11.0.jar")
    if os.path.isfile(m2):
        return m2
    raise FileNotFoundError(
        "找不到 gson JAR。请先运行: mvn dependency:copy-dependencies "
        "-pl ucc-ai -DoutputDirectory=target/lib -DincludeScope=runtime"
    )

DEFAULT_CLASSPATH = (
    os.path.join(_PROJECT_ROOT, "ucc-core", "target", "ucc-core.jar")
    + os.pathsep +
    os.path.join(_PROJECT_ROOT, "ucc-common", "target", "ucc-common.jar")
    + os.pathsep +
    os.path.join(_PROJECT_ROOT, "ucc-ai", "target", "ucc-ai.jar")
    + os.pathsep +
    _find_gson()
)

PYBRIDGE_MAIN = "io.github.samera2022.chinese_chess.ai.PyBridge"


# ═══════════════════════════════════════════════════════════════════════════
# PyBridgeSession — 长驻 Java 子进程
# ═══════════════════════════════════════════════════════════════════════════

class PyBridgeSession:
    """与 PyBridge 长驻 Java 进程通信的会话管理器。

    用法:
        with PyBridgeSession() as session:
            state = session.new_game(rules_raw=..., rows=10)
            moves = session.legal_moves()
            result = session.simulate(fromRow=..., fromCol=..., toRow=..., toCol=...)
    """

    def __init__(
        self,
        classpath: str = DEFAULT_CLASSPATH,
        timeout: float = 30.0,
    ):
        self._classpath = classpath
        self._timeout = timeout
        self._proc: Optional[subprocess.Popen] = None
        self._req_id = 0

    def start(self) -> None:
        """启动 Java 进程。"""
        if self._proc is not None:
            return
        cmd = ["java", "-cp", self._classpath, PYBRIDGE_MAIN]
        self._proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,  # 行缓冲
        )
        # 验证连通性
        result = self._send({"command": "ping"}, expect_ok=False, timeout=10.0)
        if not result.get("ok"):
            raise RuntimeError(f"PyBridge 启动失败: {result}")

    def new_game(self, rules_raw: Dict[str, Any], rows: int = 10) -> Dict[str, Any]:
        """创建新棋盘。"""
        return self._send({
            "command": "new_game",
            "rules": rules_raw,
            "rows": rows,
        })

    def legal_moves(self) -> List[Dict[str, int]]:
        """获取当前回合所有合法着法。"""
        result = self._send({"command": "legal_moves"})
        return result.get("moves", [])

    def simulate(self, fromRow: int, fromCol: int, toRow: int, toCol: int) -> Dict[str, Any]:
        """执行走子。"""
        return self._send({
            "command": "simulate",
            "fromRow": fromRow,
            "fromCol": fromCol,
            "toRow": toRow,
            "toCol": toCol,
        })

    def close(self) -> None:
        """关闭 Java 进程。"""
        if self._proc is not None:
            try:
                self._proc.stdin.close()
                self._proc.stdout.close()
                self._proc.wait(timeout=5)
            except Exception:
                self._proc.kill()
                self._proc.wait()
            self._proc = None

    def __enter__(self) -> "PyBridgeSession":
        self.start()
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()

    # ── 内部方法 ──

    def _send(
        self,
        request: Dict[str, Any],
        timeout: Optional[float] = None,
        expect_ok: bool = True,
    ) -> Dict[str, Any]:
        if self._proc is None:
            raise RuntimeError("PyBridge 未启动")

        timeout = timeout or self._timeout
        payload = json.dumps(request, ensure_ascii=False) + "\n"
        deadline = time.time() + timeout

        # 写 stdin
        try:
            self._proc.stdin.write(payload)     # type: ignore[union-attr]
            self._proc.stdin.flush()            # type: ignore[union-attr]
        except BrokenPipeError:
            self._read_stderr_and_raise("PyBridge 进程已意外终止")

        # 读 stdout（阻塞，带超时）
        line = ""
        while time.time() < deadline:
            line = self._proc.stdout.readline()    # type: ignore[union-attr]
            if line:
                break
            time.sleep(0.01)
        else:
            self._read_stderr_and_raise(f"PyBridge 响应超时 ({timeout}s)")

        try:
            result = json.loads(line)
        except json.JSONDecodeError:
            raise RuntimeError(f"PyBridge 返回非法 JSON: {line[:200]}")

        if expect_ok and not result.get("ok"):
            raise RuntimeError(f"PyBridge 错误: {result.get('error', result)}")

        return result

    def _read_stderr_and_raise(self, msg: str) -> None:
        stderr = ""
        if self._proc and self._proc.stderr:
            try:
                import select
                while select.select([self._proc.stderr], [], [], 0.1)[0]:
                    line = self._proc.stderr.readline()
                    if line:
                        stderr += line
            except Exception:
                pass
        raise RuntimeError(f"{msg}\nstderr: {stderr[:500]}")
