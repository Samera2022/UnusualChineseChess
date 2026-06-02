"""
自我对弈与 MCTS 搜索 — 强化学习 AI 引擎。

实现 MCTS 树搜索、自我对弈数据生成、以及 Phase 0 规则感知验证实验。
通过 subprocess 调用 Java PyBridge 执行棋盘模拟。

参考资料:
  - report/rl/强化学习接入方案.md §2.4, §7.1
  - ucc-ai/python/model.py: MiniResNet 神经网络
  - ucc-core RuleEncoder: 规则向量编码维度 (27 bool + 1 continuous)
"""

import json
import math
import os
import random
import subprocess
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch

from model import MiniResNet

# ═══════════════════════════════════════════════════════════════════════════
# GPU 设备检测
# ═══════════════════════════════════════════════════════════════════════════

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# ═══════════════════════════════════════════════════════════════════════════
# 常量
# ═══════════════════════════════════════════════════════════════════════════

# Piece.Type 枚举名 → 14 通道索引
# 红方: 将/仕/相/马/车/炮/兵 → 通道 0-6
# 黑方: 将/士/象/马/车/炮/卒 → 通道 7-13
PIECE_TYPE_TO_CHANNEL: Dict[str, int] = {
    "RED_KING": 0,
    "RED_ADVISOR": 1,
    "RED_ELEPHANT": 2,
    "RED_HORSE": 3,
    "RED_CHARIOT": 4,
    "RED_CANNON": 5,
    "RED_SOLDIER": 6,
    "BLACK_KING": 7,
    "BLACK_ADVISOR": 8,
    "BLACK_ELEPHANT": 9,
    "BLACK_HORSE": 10,
    "BLACK_CHARIOT": 11,
    "BLACK_CANNON": 12,
    "BLACK_SOLDIER": 13,
}

# 棋子子力价值（用于启发式评估）
PIECE_VALUE: Dict[str, int] = {
    "RED_KING": 10000,
    "BLACK_KING": 10000,
    "RED_CHARIOT": 900,
    "BLACK_CHARIOT": 900,
    "RED_CANNON": 450,
    "BLACK_CANNON": 450,
    "RED_HORSE": 400,
    "BLACK_HORSE": 400,
    "RED_ELEPHANT": 200,
    "BLACK_ELEPHANT": 200,
    "RED_ADVISOR": 200,
    "BLACK_ADVISOR": 200,
    "RED_SOLDIER": 100,
    "BLACK_SOLDIER": 100,
}

# 14 通道棋盘张量
NUM_CHANNELS = 14

# 规则向量维度（与 Java RuleEncoder 保持一致）
# 27 布尔 + 1 连续值（max_stacking_count / 16）
RULE_BOOL_DIM = 27
RULE_CONTINUOUS_DIM = 1
RULE_FULL_DIM = RULE_BOOL_DIM + RULE_CONTINUOUS_DIM  # 28

# 规则向量中布尔位的名称（与 Java RuleEncoder.encode() 顺序严格一致！）
# 这些名称会通过 _rule_vector_to_rules_json() 传给 PyBridge 的 --rules 参数，
# Java 端 GameRulesConfig.applySnapshot() 按 registryName 查找对应规则。
RULE_BOOL_NAMES: List[str] = [
    "allow_undo",                    # 0
    "show_hints",                    # 1
    "allow_force_move",              # 2
    "allow_flying_general",          # 3
    "disable_facing_generals",       # 4
    "advisor_can_leave",             # 5
    "international_king",            # 6
    "international_advisor",         # 7
    "no_river_limit",                # 8
    "pawn_can_retreat",              # 9
    "allow_inside_retreat",          # 10
    "pawn_promotion",                # 11
    "allow_own_base_line",           # 12
    "unblock_piece",                 # 13
    "unblock_horse_leg",             # 14
    "unblock_elephant_eye",          # 15
    "allow_capture_own_piece",       # 16
    "allow_capture_conversion",      # 17
    "left_right_connected",          # 18
    "death_match_until_victory",     # 19
    "allow_piece_stacking",          # 20
    "top_bottom_connected",          # 21  ← 改变棋盘尺寸的规则
    "left_right_connected_horse",    # 22
    "left_right_connected_elephant", # 23
    "allow_carry_pieces_above",      # 24
    "top_bottom_connected_horse",    # 25
    "top_bottom_connected_elephant", # 26
]

# 改变棋盘尺寸的规则索引
# top_bottom_connected (index 21) 开启时棋盘从 10×9 变为 18×9
TOP_BOTTOM_CONNECTED_IDX = 21

# 棋盘尺寸常量
STANDARD_ROWS = 10
EXPANDED_ROWS = 18
BOARD_COLS = 9

# PyBridge 默认配置 — 自动定位 Maven 构建产物
_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))))  # python → ucc-ai → 项目根
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


def get_rows_for_rules(rule_vector: List[float]) -> int:
    """根据规则向量确定实际棋盘行数。

    当 top_bottom_connected (index 8) 开启时，棋盘从 10×9 扩展为 18×9。

    Args:
        rule_vector: 规则向量。

    Returns:
        10 或 18。
    """
    if len(rule_vector) > TOP_BOTTOM_CONNECTED_IDX:
        if rule_vector[TOP_BOTTOM_CONNECTED_IDX] >= 0.5:
            return EXPANDED_ROWS
    return STANDARD_ROWS


# ═══════════════════════════════════════════════════════════════════════════
# MCTS 节点
# ═══════════════════════════════════════════════════════════════════════════

class MCTSNode:
    """MCTS 搜索树节点。

    Attributes:
        state: 局面字典，包含 'board' (BoardState JSON dict) 和
               'rules' (规则向量 list)。
        parent: 父节点，根节点为 None。
        children: 子节点字典，key 为着法 JSON 字符串，value 为 MCTSNode。
        visit_count: 节点访问次数。
        total_value: 累计价值（从当前玩家视角）。
        prior: 先验概率 P(s, a)，来自神经网络策略头。
        is_expanded: 是否已展开子节点。
    """

    def __init__(
        self,
        state: Optional[Dict[str, Any]] = None,
        parent: Optional["MCTSNode"] = None,
        prior: float = 0.0,
    ):
        self.state = state or {}
        self.parent = parent
        self.children: Dict[str, "MCTSNode"] = {}
        self.visit_count: int = 0
        self.total_value: float = 0.0
        self.prior: float = prior
        self.is_expanded: bool = False

    def to_json_safe_dict(self) -> Dict[str, Any]:
        """返回节点状态的 JSON 安全字典（用于调试）。"""
        return {
            "visit_count": self.visit_count,
            "total_value": self.total_value,
            "prior": self.prior,
            "is_expanded": self.is_expanded,
            "num_children": len(self.children),
        }


def _rule_vector_to_rules_json(rule_vector: List[float]) -> str:
    """将 RULE_FULL_DIM 维规则向量转为 PyBridge 可接受的 --rules JSON 字符串。

    格式与 GameRulesConfig.applySnapshot() 兼容：
    {"allow_undo": true, ..., "max_stacking_count": 2}
    """
    rules_obj: Dict[str, Any] = {}
    for i, name in enumerate(RULE_BOOL_NAMES):
        rules_obj[name] = bool(rule_vector[i] >= 0.5)
    # max_stacking_count 是连续值，存储在 RULE_BOOL_DIM 索引处
    rules_obj["max_stacking_count"] = int(round(rule_vector[RULE_BOOL_DIM] * 16.0))
    return json.dumps(rules_obj, ensure_ascii=False)


def _build_java_cmd(
    command: str,
    board_json: Optional[str] = None,
    move_json: Optional[str] = None,
    rules_json: Optional[str] = None,
    rows: Optional[int] = None,
    classpath: str = DEFAULT_CLASSPATH,
) -> List[str]:
    """构建调用 PyBridge 的 java 命令行参数列表。"""
    cmd = [
        "java", "-cp", classpath, PYBRIDGE_MAIN,
        "--command", command,
    ]
    if board_json is not None:
        cmd += ["--board", board_json]
    if move_json is not None:
        cmd += ["--move", move_json]
    if rules_json is not None:
        cmd += ["--rules", rules_json]
    if rows is not None:
        cmd += ["--rows", str(rows)]
    return cmd


def query_java_engine(
    board_state: Dict[str, Any],
    rule_vector: List[float],
    move: Dict[str, int],
    classpath: str = DEFAULT_CLASSPATH,
    timeout: int = 10,
) -> Dict[str, Any]:
    """通过 subprocess 调用 Java PyBridge 执行模拟走子。

    使用 --command simulate，传入 --board、--move（JSON）、--rules（JSON）。
    PyBridge 返回的 JSON 包含 "boardState" 字段（走子后的新 BoardState）。

    Args:
        board_state: BoardState JSON 字典。
        rule_vector: 规则向量。
        move: 着法字典，含 fromRow, fromCol, toRow, toCol。
        classpath: Java classpath 字符串。
        timeout: subprocess 超时秒数。

    Returns:
        JSON 解析结果字典。成功示例：
          {"success": true, "legal": true, "boardState": {...}}
    """
    try:
        board_json = json.dumps(board_state, ensure_ascii=False)
        rules_json = _rule_vector_to_rules_json(rule_vector)
        move_json = json.dumps(move, ensure_ascii=False)

        cmd = _build_java_cmd(
            command="simulate",
            board_json=board_json,
            move_json=move_json,
            rules_json=rules_json,
            classpath=classpath,
        )

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

        if result.returncode != 0:
            return {
                "success": False,
                "error": result.stderr.strip() or "Java process returned non-zero",
            }

        return json.loads(result.stdout)
    except subprocess.TimeoutExpired:
        return {"success": False, "error": "Java subprocess timed out"}
    except json.JSONDecodeError as e:
        return {"success": False, "error": f"JSON decode error: {e}"}
    except FileNotFoundError:
        return {"success": False, "error": "java command not found in PATH"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def query_java_new_game(
    rule_vector: List[float],
    rows: int = 10,
    classpath: str = DEFAULT_CLASSPATH,
    timeout: int = 10,
) -> Dict[str, Any]:
    """通过 PyBridge 创建新棋盘（--command new_game）。

    Args:
        rule_vector: 规则向量。
        rows: 棋盘行数。
        classpath: Java classpath。
        timeout: 超时秒数。

    Returns:
        BoardState JSON 字典；若出错则含 "error" 键。
    """
    try:
        rules_json = _rule_vector_to_rules_json(rule_vector)

        cmd = _build_java_cmd(
            command="new_game",
            rules_json=rules_json,
            rows=rows,
            classpath=classpath,
        )

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

        if result.returncode != 0:
            return {"error": result.stderr.strip() or "Java process returned non-zero"}

        return json.loads(result.stdout)
    except subprocess.TimeoutExpired:
        return {"error": "Java subprocess timed out"}
    except json.JSONDecodeError as e:
        return {"error": f"JSON decode error: {e}"}
    except FileNotFoundError:
        return {"error": "java command not found in PATH"}
    except Exception as e:
        return {"error": str(e)}


def query_java_legal_moves(
    board_state: Dict[str, Any],
    rule_vector: List[float],
    classpath: str = DEFAULT_CLASSPATH,
    timeout: int = 10,
) -> List[Dict[str, int]]:
    """通过 PyBridge 获取当前回合方所有合法着法（--command legal_moves）。

    Args:
        board_state: BoardState JSON 字典。
        rule_vector: 规则向量。
        classpath: Java classpath。
        timeout: 超时秒数。

    Returns:
        着法列表；出错时返回空列表。
    """
    try:
        board_json = json.dumps(board_state, ensure_ascii=False)
        rules_json = _rule_vector_to_rules_json(rule_vector)

        cmd = _build_java_cmd(
            command="legal_moves",
            board_json=board_json,
            rules_json=rules_json,
            classpath=classpath,
        )

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

        if result.returncode != 0:
            return []

        data = json.loads(result.stdout)
        return data.get("moves", [])
    except Exception:
        return []


# ═══════════════════════════════════════════════════════════════════════════
# 棋盘 → 张量转换
# ═══════════════════════════════════════════════════════════════════════════

def board_to_tensor(
    board_state_dict: Dict[str, Any],
    rows: int = 10,
    cols: int = 9,
) -> np.ndarray:
    """将 BoardState JSON 字典转为 numpy float32 张量 [14, rows, cols]."""
    tensor = np.zeros((NUM_CHANNELS, rows, cols), dtype=np.float32)
    entries = board_state_dict.get("entries", [])
    for entry in entries:
        r = entry.get("row", 0)
        c = entry.get("col", 0)
        if r < 0 or r >= rows or c < 0 or c >= cols:
            continue
        piece_types = entry.get("pieceTypes", [])
        for pt in piece_types:
            channel = PIECE_TYPE_TO_CHANNEL.get(pt)
            if channel is not None:
                tensor[channel, r, c] = 1.0
    return tensor


def rule_vector_to_numpy(rule_vector: List[float]) -> np.ndarray:
    """将规则向量 list 转为 numpy float32 数组 [RULE_FULL_DIM]."""
    return np.array(rule_vector, dtype=np.float32)


# ═══════════════════════════════════════════════════════════════════════════
# 模拟数据生成（Phase 0 验证实验使用，不调用 Java）
# ═══════════════════════════════════════════════════════════════════════════

def _generate_mock_board_state(
    rows: int = 10,
    cols: int = 9,
    num_pieces_range: Tuple[int, int] = (6, 16),
) -> Dict[str, Any]:
    """生成随机残局 BoardState JSON 字典（模拟数据）。

    保证初始棋盘双方至少各有一个将/帅（KING）。
    """
    all_piece_types = list(PIECE_TYPE_TO_CHANNEL.keys())
    red_types = [t for t in all_piece_types if t.startswith("RED_")]
    black_types = [t for t in all_piece_types if t.startswith("BLACK_")]

    num_pieces = random.randint(*num_pieces_range)
    num_red = max(num_pieces // 2, 2)
    num_black = max(num_pieces - num_red, 2)

    used_positions: set = set()
    entries: List[Dict[str, Any]] = []

    def place_one(piece_type: str) -> None:
        for _ in range(100):
            r = random.randint(0, rows - 1)
            c = random.randint(0, cols - 1)
            if (r, c) not in used_positions:
                used_positions.add((r, c))
                entries.append({
                    "row": r,
                    "col": c,
                    "pieceTypes": [piece_type],
                })
                return

    place_one("RED_KING")
    place_one("BLACK_KING")

    for _ in range(num_red - 1):
        place_one(random.choice(red_types))
    for _ in range(num_black - 1):
        place_one(random.choice(black_types))

    return {
        "rows": rows,
        "cols": cols,
        "entries": entries,
        "redTurn": random.choice([True, False]),
    }


def _generate_mock_legal_moves(
    board_state: Dict[str, Any],
    rows: int = 10,
    cols: int = 9,
) -> List[Dict[str, int]]:
    """基于棋盘状态的确定性的候选着法生成器。

    仅从属于当前回合方的棋子位置生成着法。
    """
    state_hash = hash(json.dumps(board_state, sort_keys=True))
    local_rng = random.Random(state_hash)

    moves: List[Dict[str, int]] = []
    is_current_red = board_state.get("redTurn", True)
    entries = board_state.get("entries", [])

    for entry in entries:
        piece_types = entry.get("pieceTypes", [])
        if not piece_types:
            continue
        top_type = piece_types[-1]
        is_red = top_type.startswith("RED_")
        if is_red != is_current_red:
            continue

        from_row, from_col = entry["row"], entry["col"]
        num_targets = local_rng.randint(1, 3)
        for _ in range(num_targets):
            to_row = local_rng.randint(0, rows - 1)
            to_col = local_rng.randint(0, cols - 1)
            if to_row == from_row and to_col == from_col:
                continue
            moves.append({
                "fromRow": from_row,
                "fromCol": from_col,
                "toRow": to_row,
                "toCol": to_col,
            })

    if not moves:
        moves.append({
            "fromRow": local_rng.randint(0, rows - 1),
            "fromCol": local_rng.randint(0, cols - 1),
            "toRow": local_rng.randint(0, rows - 1),
            "toCol": local_rng.randint(0, cols - 1),
        })

    return moves


def _apply_mock_move(
    board_state: Dict[str, Any],
    move: Dict[str, int],
    rows: int = 10,
    cols: int = 9,
) -> Dict[str, Any]:
    """在模拟棋盘上执行着法，返回新的 BoardState 字典（含吃子逻辑）。"""
    entries = board_state.get("entries", [])
    from_row, from_col = move["fromRow"], move["fromCol"]
    to_row, to_col = move["toRow"], move["toCol"]

    moved_piece_type: Optional[str] = None
    intermediate_entries: List[Dict[str, Any]] = []

    for entry in entries:
        r, c = entry["row"], entry["col"]
        piece_types = list(entry.get("pieceTypes", []))

        if r == from_row and c == from_col:
            if piece_types:
                moved_piece_type = piece_types.pop()
            if piece_types:
                intermediate_entries.append({
                    "row": r, "col": c, "pieceTypes": piece_types,
                })
        else:
            intermediate_entries.append({
                "row": r, "col": c, "pieceTypes": piece_types,
            })

    if moved_piece_type is None:
        return {
            "rows": board_state.get("rows", rows),
            "cols": board_state.get("cols", cols),
            "entries": entries,
            "redTurn": board_state.get("redTurn", True),
        }

    # 吃子逻辑
    moved_side = "RED" if (moved_piece_type or "").startswith("RED_") else "BLACK"
    target_found = False
    final_entries: List[Dict[str, Any]] = []

    for entry in intermediate_entries:
        r, c = entry["row"], entry["col"]
        piece_types = list(entry.get("pieceTypes", []))

        if r == to_row and c == to_col:
            target_found = True
            surviving = [pt for pt in piece_types if pt.startswith(moved_side + "_")]
            surviving.append(moved_piece_type)
            if surviving:
                final_entries.append({
                    "row": r, "col": c, "pieceTypes": surviving,
                })
        else:
            final_entries.append({
                "row": r, "col": c, "pieceTypes": piece_types,
            })

    if not target_found:
        final_entries.append({
            "row": to_row, "col": to_col, "pieceTypes": [moved_piece_type],
        })

    new_red_turn = not board_state.get("redTurn", True)

    return {
        "rows": board_state.get("rows", rows),
        "cols": board_state.get("cols", cols),
        "entries": final_entries,
        "redTurn": new_red_turn,
    }


def _evaluate_mock_position(board_state: Dict[str, Any]) -> float:
    """启发式局面评估（仅用于 mock 模式），返回值 ∈ [-1, 1]."""
    entries = board_state.get("entries", [])
    red_score = 0.0
    black_score = 0.0
    for entry in entries:
        for pt in entry.get("pieceTypes", []):
            val = PIECE_VALUE.get(pt, 0)
            if pt.startswith("RED_"):
                red_score += val
            else:
                black_score += val
    total = max(red_score + black_score, 1.0)
    raw = (red_score - black_score) / total
    return max(-1.0, min(1.0, raw))


def _is_mock_game_over(board_state: Dict[str, Any]) -> Tuple[bool, float]:
    """检查 mock 棋盘终止条件：某方将帅被吃或棋子全灭。"""
    entries = board_state.get("entries", [])
    has_red_king = False
    has_black_king = False
    has_red_piece = False
    has_black_piece = False

    for entry in entries:
        for pt in entry.get("pieceTypes", []):
            if pt == "RED_KING":
                has_red_king = True
                has_red_piece = True
            elif pt.startswith("RED_"):
                has_red_piece = True
            elif pt == "BLACK_KING":
                has_black_king = True
                has_black_piece = True
            elif pt.startswith("BLACK_"):
                has_black_piece = True

    if not has_red_king or not has_red_piece:
        return True, -1.0
    if not has_black_king or not has_black_piece:
        return True, 1.0
    return False, 0.0


# ═══════════════════════════════════════════════════════════════════════════
# 真实规则辅助函数（MCTS 内部使用，use_mock=False 时替代 mock 函数）
# ═══════════════════════════════════════════════════════════════════════════


def _apply_real_move(
    board_state: Dict[str, Any],
    move: Dict[str, int],
    pybridge: Any,
    rows: int,
    cols: int,
    rule_vector: Optional[List[float]] = None,
) -> Dict[str, Any]:
    """通过 Java 引擎执行真实走子，返回新的 BoardState 字典。

    MCTS 内部需要在任意节点展开子节点，而 PyBridgeSession 长驻进程
    不支持设置任意棋盘状态（仅支持线性对弈流程）。因此本函数使用
    per-call subprocess（query_java_engine）来保证状态隔离和正确性。

    Args:
        board_state: 当前棋盘状态 JSON 字典。
        move: 着法字典，含 fromRow, fromCol, toRow, toCol。
        pybridge: PyBridgeSession 实例（备用，当前实现使用 per-call）。
        rows: 棋盘行数。
        cols: 棋盘列数。
        rule_vector: 规则向量列表。

    Returns:
        走子后的新 BoardState 字典。
    """
    if rule_vector is None:
        rule_vector = []
    result = query_java_engine(board_state, rule_vector, move)
    if result.get("success"):
        return result.get("boardState", board_state)
    return board_state


def _is_real_game_over(board_state: Dict[str, Any]) -> Tuple[bool, float]:
    """检查真实棋盘终止条件：某方将帅被吃或棋子全灭。

    与 _is_mock_game_over 逻辑相同，但作用于 pybridge/simulate 返回的
    真实 board_state。

    Returns:
        (是否结束, 局面价值): 1.0 红胜, -1.0 黑胜。
    """
    entries = board_state.get("entries", [])
    has_red_king = False
    has_black_king = False
    has_red_piece = False
    has_black_piece = False

    for entry in entries:
        for pt in entry.get("pieceTypes", []):
            if pt == "RED_KING":
                has_red_king = True
                has_red_piece = True
            elif pt.startswith("RED_"):
                has_red_piece = True
            elif pt == "BLACK_KING":
                has_black_king = True
                has_black_piece = True
            elif pt.startswith("BLACK_"):
                has_black_piece = True

    if not has_red_king or not has_red_piece:
        return True, -1.0
    if not has_black_king or not has_black_piece:
        return True, 1.0
    return False, 0.0


# ═══════════════════════════════════════════════════════════════════════════
# MCTS 搜索
# ═══════════════════════════════════════════════════════════════════════════

def _ucb_score(
    parent_visit: int,
    child_visit: int,
    child_total_value: float,
    prior: float,
    c_puct: float = 2.0,
) -> float:
    """UCB (Upper Confidence Bound) 评分。"""
    if child_visit == 0:
        return float("inf")
    exploit = child_total_value / child_visit
    explore = c_puct * prior * math.sqrt(parent_visit) / (1 + child_visit)
    return exploit + explore


def mcts_search(
    model: Optional[MiniResNet],
    board_state: Dict[str, Any],
    rule_vector: List[float],
    num_simulations: int = 50,
    c_puct: float = 2.0,
    temperature: float = 1.0,
    rows: int = 10,
    cols: int = 9,
    use_mock: bool = True,
    pybridge: Any = None,
    dirichlet_alpha: float = 0.3,
    dirichlet_weight: float = 0.25,
) -> Tuple[np.ndarray, np.ndarray]:
    """神经网络引导的 MCTS 搜索（含 Dirichlet 噪声探索）。

    Args:
        model: 神经网络模型（MiniResNet）。
        board_state: 棋盘状态 JSON 字典。
        rule_vector: 规则向量列表。
        num_simulations: MCTS 模拟次数。
        c_puct: 探索常数。
        temperature: 温度参数。
        rows: 棋盘行数。
        cols: 棋盘列数。
        use_mock: True 时使用纯 Python mock 模拟（不依赖 Java）。
        pybridge: PyBridgeSession 实例（use_mock=False 时需要）。
        dirichlet_alpha: Dirichlet 噪声 alpha 参数。
        dirichlet_weight: Dirichlet 噪声权重。

    Returns:
        (action_probs, training_target): 动作概率和训练目标。
    """
    cells = rows * cols

    rule_tensor = rule_vector_to_numpy(rule_vector)

    # 根节点着法生成
    if use_mock or pybridge is None:
        root_moves = _generate_mock_legal_moves(board_state, rows, cols)
    else:
        root_moves = query_java_legal_moves(board_state, rule_vector)

    if not root_moves:
        return np.zeros(cells, dtype=np.float32), np.zeros(cells, dtype=np.float32)

    root = MCTSNode(state={"board": board_state, "rules": rule_vector})

    # 根节点首次展开 + Dirichlet 噪声
    root_tensor = board_to_tensor(board_state, rows, cols)
    if model is not None:
        with torch.no_grad():
            batch_board = torch.from_numpy(root_tensor).unsqueeze(0).to(DEVICE)
            batch_rules = torch.from_numpy(rule_tensor).unsqueeze(0).to(DEVICE)
            policy_logits, root_value = model(batch_board, batch_rules)
            root_policy_probs = (
                torch.softmax(policy_logits, dim=1).squeeze(0).cpu().numpy()
            )
    else:
        root_policy_probs = np.ones(cells, dtype=np.float32) / cells

    root.is_expanded = True
    num_root_moves = len(root_moves)
    noise = np.random.dirichlet([dirichlet_alpha] * num_root_moves)

    for i, move in enumerate(root_moves):
        move_idx = move["toRow"] * cols + move["toCol"]
        if 0 <= move_idx < cells:
            nn_prior = float(root_policy_probs[move_idx])
        else:
            nn_prior = 1.0 / num_root_moves

        prior = (1.0 - dirichlet_weight) * nn_prior + dirichlet_weight * noise[i]

        if use_mock or pybridge is None:
            child_board = _apply_mock_move(board_state, move, rows, cols)
        else:
            child_board = _apply_real_move(
                board_state, move, pybridge, rows, cols,
                rule_vector=rule_vector,
            )
        child_state = {"board": child_board, "rules": rule_vector}
        child = MCTSNode(state=child_state, parent=root, prior=prior)
        root.children[json.dumps(move, sort_keys=True)] = child

    # MCTS 主循环
    for _ in range(num_simulations):
        node = root
        search_path: List[MCTSNode] = [node]

        # Selection
        while node.is_expanded and len(node.children) > 0:
            best_score = -float("inf")
            best_move_key: Optional[str] = None

            for move_key, child in node.children.items():
                score = _ucb_score(
                    parent_visit=node.visit_count,
                    child_visit=child.visit_count,
                    child_total_value=child.total_value,
                    prior=child.prior,
                    c_puct=c_puct,
                )
                if score > best_score:
                    best_score = score
                    best_move_key = move_key

            if best_move_key is None:
                break
            node = node.children[best_move_key]
            search_path.append(node)

        # Evaluation — 终局检查
        node_board = node.state.get("board", board_state)

        if use_mock or pybridge is None:
            is_over, terminal_value = _is_mock_game_over(node_board)
        else:
            is_over, terminal_value = _is_real_game_over(node_board)

        if is_over:
            is_red_turn = node_board.get("redTurn", True)
            leaf_value = terminal_value if is_red_turn else -terminal_value
            for path_node in reversed(search_path):
                path_node.visit_count += 1
                path_node.total_value += leaf_value
                leaf_value = -leaf_value
            continue

        # 神经网络评估
        node_tensor = board_to_tensor(node_board, rows, cols)

        if model is not None:
            with torch.no_grad():
                batch_board = torch.from_numpy(node_tensor).unsqueeze(0).to(DEVICE)
                batch_rules = torch.from_numpy(rule_tensor).unsqueeze(0).to(DEVICE)
                policy_logits, value = model(batch_board, batch_rules)
                policy_probs = (
                    torch.softmax(policy_logits, dim=1).squeeze(0).cpu().numpy()
                )
                leaf_value = float(value.item())
        else:
            policy_probs = np.ones(cells, dtype=np.float32) / cells
            if use_mock or pybridge is None:
                raw_eval = _evaluate_mock_position(node_board)
                is_red_turn = node_board.get("redTurn", True)
                leaf_value = raw_eval if is_red_turn else -raw_eval
            else:
                # 无模型且使用真实规则时，返回 0 作为 fallback
                # （神经网络 value 不可用，不应使用 mock 启发式评估）
                leaf_value = 0.0

        # Expansion
        if not node.is_expanded:
            if use_mock or pybridge is None:
                node_legal_moves = _generate_mock_legal_moves(node_board, rows, cols)
            else:
                node_legal_moves = query_java_legal_moves(node_board, rule_vector)

            node.is_expanded = True

            for move in node_legal_moves:
                move_idx = move["toRow"] * cols + move["toCol"]
                if 0 <= move_idx < cells:
                    prior = float(policy_probs[move_idx])
                else:
                    prior = 0.0

                if use_mock or pybridge is None:
                    child_board = _apply_mock_move(node_board, move, rows, cols)
                else:
                    child_board = _apply_real_move(
                        node_board, move, pybridge, rows, cols,
                        rule_vector=rule_vector,
                    )
                child_state = {"board": child_board, "rules": rule_vector}
                child = MCTSNode(state=child_state, parent=node, prior=prior)
                node.children[json.dumps(move, sort_keys=True)] = child

        # Backpropagation
        for path_node in reversed(search_path):
            path_node.visit_count += 1
            path_node.total_value += leaf_value
            leaf_value = -leaf_value

    # 构建动作概率
    visit_counts = np.zeros(cells, dtype=np.float32)
    for move_key, child in root.children.items():
        move = json.loads(move_key)
        move_idx = move["toRow"] * cols + move["toCol"]
        if 0 <= move_idx < cells:
            visit_counts[move_idx] += float(child.visit_count)

    if temperature <= 1e-8:
        action_probs = np.zeros(cells, dtype=np.float32)
        if visit_counts.max() > 0:
            max_idx = int(np.argmax(visit_counts))
            action_probs[max_idx] = 1.0
    else:
        powered = np.power(visit_counts, 1.0 / temperature)
        total = powered.sum()
        if total > 1e-12:
            action_probs = powered / total
        else:
            action_probs = powered

    training_target = visit_counts.copy()
    visit_total = training_target.sum()
    if visit_total > 1e-12:
        training_target = training_target / visit_total

    return action_probs, training_target


# ═══════════════════════════════════════════════════════════════════════════
# 规则向量生成
# ═══════════════════════════════════════════════════════════════════════════

def make_all_false_rule_vector() -> List[float]:
    """生成全 false 规则向量（标准规则）。"""
    return [0.0] * RULE_BOOL_DIM + [0.0]


def make_random_rule_vector() -> List[float]:
    """生成完全随机的规则向量。"""
    bool_part = [random.choice([0.0, 1.0]) for _ in range(RULE_BOOL_DIM)]
    continuous_part = random.uniform(0.0, 1.0)
    return bool_part + [continuous_part]


def make_random_rule_vector_with_few_enabled(
    min_enabled: int = 1,
    max_enabled: int = 3,
) -> List[float]:
    """生成随机规则向量，仅随机开启少量扩展规则。

    从索引 3 及以后的规则中随机选择。（跳过前 3 条基础 UI 规则）
    """
    bool_part = [0.0] * RULE_BOOL_DIM
    num_enabled = random.randint(min_enabled, max_enabled)
    pool = list(range(3, RULE_BOOL_DIM))
    chosen = random.sample(pool, min(num_enabled, len(pool)))
    for idx in chosen:
        bool_part[idx] = 1.0
    return bool_part + [0.0]


# ═══════════════════════════════════════════════════════════════════════════
# 自我对弈
# ═══════════════════════════════════════════════════════════════════════════

def selfplay_game(
    model: Optional[MiniResNet],
    rules: Optional[List[float]] = None,
    max_steps: int = 400,
    rows: int = 10,
    cols: int = 9,
    use_mock: bool = True,
    num_simulations: int = 50,
    c_puct: float = 2.0,
    temperature: float = 1.0,
    pybridge: Any = None,  # PyBridgeSession（use_mock=False 时必须提供）
) -> Tuple[List[Dict[str, Any]], float]:
    """执行一局自我对弈。

    两种模式：
      - use_mock=True: 纯 Python 模拟（不依赖 Java）
      - use_mock=False: 通过长驻 PyBridgeSession 调用 Java 引擎
        （新协议：一次启动，全程复用 stdin/stdout 管道）
        MCTS 树搜索内部使用 per-call subprocess 展开节点，
        实际走子时通过长驻管道调用 Java。
    """
    if rules is None:
        rules = make_random_rule_vector()

    actual_rows = get_rows_for_rules(rules)
    rules_dict = {name: bool(rules[i] >= 0.5) for i, name in enumerate(RULE_BOOL_NAMES)}
    rules_dict["max_stacking_count"] = int(round(rules[RULE_BOOL_DIM] * 16.0))

    if use_mock:
        board_state = _generate_mock_board_state(actual_rows, cols)
    else:
        if pybridge is None:
            raise ValueError("use_mock=False 时必须提供 pybridge (PyBridgeSession)")
        board_state = pybridge.new_game(rules_dict, rows=actual_rows)

    trajectory: List[Dict[str, Any]] = []
    cells = rows * cols
    final_value = 0.0

    for step in range(max_steps):
        step_temperature = temperature if step < 30 else max(0.1, temperature * 0.5)

        # MCTS 搜索
        # 当 use_mock=False 时，传递 pybridge 给 mcts_search
        # 使 MCTS 内部使用真实规则（per-call subprocess）展开节点
        action_probs, training_target = mcts_search(
            model=model,
            board_state=board_state,
            rule_vector=rules,
            num_simulations=num_simulations,
            c_puct=c_puct,
            temperature=step_temperature,
            rows=rows,
            cols=cols,
            use_mock=use_mock,
            pybridge=pybridge,
        )

        if action_probs.sum() < 1e-12:
            is_red = board_state.get("redTurn", True)
            final_value = -1.0 if is_red else 1.0
            break

        # 终止检查
        if use_mock:
            is_over, terminal_val = _is_mock_game_over(board_state)
            if is_over:
                final_value = terminal_val
                break

            if step >= 20:
                progress = (step - 20) / max(max_steps - 20, 1)
                termination_prob = progress * 0.8
                if random.random() < termination_prob:
                    final_value = _evaluate_mock_position(board_state)
                    break
        else:
            # 通过长驻 Java 进程获取真实合法着法
            legal_moves = pybridge.legal_moves()
            if not legal_moves:
                is_red = board_state.get("redTurn", True)
                final_value = -1.0 if is_red else 1.0
                break

        # 采样着法
        move_idx = int(np.random.choice(cells, p=action_probs))

        if use_mock:
            legal_moves = _generate_mock_legal_moves(board_state, rows, cols)
        else:
            legal_moves = pybridge.legal_moves()

        chosen_move: Optional[Dict[str, int]] = None
        for move in legal_moves:
            idx = move["toRow"] * cols + move["toCol"]
            if idx == move_idx:
                chosen_move = move
                break

        if chosen_move is None:
            if legal_moves:
                chosen_move = random.choice(legal_moves)
            else:
                break

        # 记录轨迹
        trajectory.append({
            "board": board_state,
            "rules": list(rules),
            "policy": training_target.tolist(),
            "move": chosen_move,
        })

        # 执行着法
        if use_mock:
            board_state = _apply_mock_move(board_state, chosen_move, rows, cols)
        else:
            result = pybridge.simulate(
                fromRow=chosen_move["fromRow"],
                fromCol=chosen_move["fromCol"],
                toRow=chosen_move["toRow"],
                toCol=chosen_move["toCol"],
            )
            if not result.get("legal"):
                break
            board_state = result.get("boardState", board_state)

    # 终局评估：mock 和非 mock 都需要（对局可能因 max_steps 自然结束）
    if final_value == 0.0:
        if use_mock:
            final_value = _evaluate_mock_position(board_state)
        else:
            # 有神经网络时使用模型评估，否则用启发式
            if model is not None:
                tensor = board_to_tensor(board_state, rows, cols)
                rule_t = rule_vector_to_numpy(rules)
                with torch.no_grad():
                    bt = torch.from_numpy(tensor).unsqueeze(0).to(DEVICE)
                    rt = torch.from_numpy(rule_t).unsqueeze(0).to(DEVICE)
                    _, val = model(bt, rt)
                    final_value = float(val.item())
            else:
                raw_eval = _evaluate_mock_position(board_state)
                is_red_turn = board_state.get("redTurn", True)
                final_value = raw_eval if is_red_turn else -raw_eval

    return trajectory, final_value


# ═══════════════════════════════════════════════════════════════════════════
# Phase 0 验证实验
# ═══════════════════════════════════════════════════════════════════════════

def run_validation_experiment(
    num_positions: int = 100,
    rows: int = 10,
    cols: int = 9,
    num_simulations: int = 100,
) -> Dict[str, Any]:
    """Phase 0 规则感知验证实验。

    测试神经网络对不同规则向量的敏感度。
    """
    from model import MiniResNet

    model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=rows,
        board_w=cols,
        rule_dim=RULE_FULL_DIM,
    )
    model.eval()

    results = {
        "num_positions": num_positions,
        "samples": [],
    }

    for i in range(num_positions):
        rules = make_random_rule_vector()
        board = _generate_mock_board_state(rows, cols)
        tensor = board_to_tensor(board, rows, cols)
        rule_t = rule_vector_to_numpy(rules)

        with torch.no_grad():
            bt = torch.from_numpy(tensor).unsqueeze(0)
            rt = torch.from_numpy(rule_t).unsqueeze(0)
            policy, value = model(bt, rt)

        results["samples"].append({
            "index": i,
            "rules_sum": sum(rules),
            "value": float(value.item()),
            "policy_entropy": float(
                -torch.sum(
                    torch.softmax(policy, dim=1) *
                    torch.log_softmax(policy, dim=1)
                ).item()
            ),
        })

    return results


# ═══════════════════════════════════════════════════════════════════════════
# 测试块
# ═══════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("=" * 60)
    print("selfplay.py — 自我对弈与 MCTS 搜索测试")
    print("=" * 60)

    print("\n[1] 测试 board_to_tensor …")
    mock_board = _generate_mock_board_state(10, 9)
    tensor = board_to_tensor(mock_board, 18, 9)
    print(f"    shape={tensor.shape} (期望: (14, 18, 9))")
    assert tensor.shape == (14, 18, 9), f"shape 错误: {tensor.shape}"

    print("\n[2] 测试规则向量生成 …")
    rv = make_all_false_rule_vector()
    print(f"    make_all_false: len={len(rv)} (期望: {RULE_FULL_DIM})")
    assert len(rv) == RULE_FULL_DIM

    rv2 = make_random_rule_vector()
    print(f"    make_random: len={len(rv2)} (期望: {RULE_FULL_DIM})")
    assert len(rv2) == RULE_FULL_DIM

    rv3 = make_random_rule_vector_with_few_enabled()
    print(f"    make_few_enabled: len={len(rv3)} (期望: {RULE_FULL_DIM})")
    assert len(rv3) == RULE_FULL_DIM

    print("\n[3] 测试 MCTS 搜索（无模型） …")
    rules = make_all_false_rule_vector()
    probs, target = mcts_search(
        model=None,
        board_state=mock_board,
        rule_vector=rules,
        num_simulations=10,
        rows=18,
        cols=9,
        use_mock=True,
    )
    print(f"    probs sum={probs.sum():.4f}, target sum={target.sum():.4f}")
    assert probs.shape == (18 * 9,)
    assert target.shape == (18 * 9,)

    print("\n[4] 测试 selfplay_game（mock 模式） …")
    from model import MiniResNet
    m = MiniResNet(board_h=18, board_w=9, rule_dim=RULE_FULL_DIM)
    m.eval()
    traj, val = selfplay_game(
        model=m,
        rules=rules,
        max_steps=20,
        rows=18,
        cols=9,
        use_mock=True,
        num_simulations=10,
    )
    print(f"    trajectory 长度: {len(traj)}, final_value: {val:.2f}")

    print("\n" + "=" * 60)
    print("selfplay.py 所有测试通过！ ✓")
    print("=" * 60)
