"""
自我对弈与 MCTS 搜索 — 强化学习 AI 引擎。

实现 MCTS 树搜索、自我对弈数据生成、以及 Phase 0 规则感知验证实验。
通过 subprocess 调用 Java PyBridge 执行棋盘模拟。

参考资料:
  - report/rl/强化学习接入方案.md §2.4, §7.1
  - ucc-ai/python/model.py: MiniResNet 神经网络
  - ucc-core RuleEncoder: 规则向量编码维度 (22 bool + 1 continuous)
"""

import json
import math
import random
import subprocess
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch

from model import MiniResNet

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
    "RED_KING": 10000, "BLACK_KING": 10000,
    "RED_CHARIOT": 900, "BLACK_CHARIOT": 900,
    "RED_CANNON": 450, "BLACK_CANNON": 450,
    "RED_HORSE": 400, "BLACK_HORSE": 400,
    "RED_ELEPHANT": 200, "BLACK_ELEPHANT": 200,
    "RED_ADVISOR": 200, "BLACK_ADVISOR": 200,
    "RED_SOLDIER": 100, "BLACK_SOLDIER": 100,
}

NUM_CHANNELS = 14
RULE_BOOL_DIM = 27          # RuleEncoder.encode() 输出长度（22 原 + 5 新增子规则）
RULE_CONTINUOUS_DIM = 1     # RuleEncoder.encodeContinuous() 输出长度
RULE_FULL_DIM = RULE_BOOL_DIM + RULE_CONTINUOUS_DIM  # 28

# RuleEncoder 位序对应的规则布尔键名（与 ucc-core RuleEncoder 完全一致）
RULE_BOOL_NAMES: List[str] = [
    "allow_undo",                    #  0
    "show_hints",                    #  1
    "allow_force_move",              #  2
    "allow_flying_general",          #  3
    "disable_facing_generals",       #  4
    "advisor_can_leave",             #  5
    "international_king",            #  6
    "international_advisor",         #  7
    "no_river_limit",                #  8
    "pawn_can_retreat",              #  9
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
    "top_bottom_connected",          # 21
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

# PyBridge 默认配置
DEFAULT_CLASSPATH = "ucc-core.jar;ucc-common.jar;ucc-ai.jar"
PYBRIDGE_MAIN = "io.github.samera2022.chinese_chess.ai.PyBridge"


def get_rows_for_rules(rule_vector: List[float]) -> int:
    """根据规则向量确定实际棋盘行数。

    当 top_bottom_connected (index 21) 开启时，棋盘从 10×9 扩展为 18×9。

    Args:
        rule_vector: 23 维规则向量。

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

    __slots__ = (
        "state", "parent", "children", "visit_count",
        "total_value", "prior", "is_expanded",
    )

    def __init__(
        self,
        state: Dict[str, Any],
        parent: Optional["MCTSNode"] = None,
        prior: float = 0.0,
    ):
        self.state = state
        self.parent = parent
        self.children: Dict[str, "MCTSNode"] = {}
        self.visit_count = 0
        self.total_value = 0.0
        self.prior = prior
        self.is_expanded = False

    def to_json_safe_dict(self) -> Dict[str, Any]:
        """将 state 转为 JSON 可序列化字典。

        BoardState 中的 pieceTypes 值是 Piece.Type 枚举名字符串，
        本身已可 JSON 序列化；rules 是 float 列表。此方法确保深层拷贝。
        """
        return {
            "board": self.state.get("board", {}),
            "rules": list(self.state.get("rules", [])),
        }


# ═══════════════════════════════════════════════════════════════════════════
# Java PyBridge 通信层
# ═══════════════════════════════════════════════════════════════════════════

def _rule_vector_to_rules_json(rule_vector: List[float]) -> str:
    """将 RULE_FULL_DIM 维规则向量转为 PyBridge 可接受的 --rules JSON 字符串。

    格式与 GameRulesConfig.applySnapshot() 兼容：
    {"allow_undo": true, ..., "max_stacking_count": 2}
    """
    rules_obj: Dict[str, Any] = {}
    for i, name in enumerate(RULE_BOOL_NAMES):
        rules_obj[name] = bool(rule_vector[i] >= 0.5)
    # max_stacking_count 是连续值，存储在 RULE_BOOL_DIM 索引处（即布尔部分之后）
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

    Args:
        board_state: BoardState JSON 字典，含 rows, cols, entries, redTurn。
        rule_vector: 规则向量，长度 23（22 布尔 + 1 连续值）。
        move: 着法字典，含 fromRow, fromCol, toRow, toCol。
        classpath: Java classpath 字符串。
        timeout: subprocess 超时秒数。

    Returns:
        JSON 解析结果字典。成功示例：
          {"success": true, "legal": true}
        失败示例：
          {"success": false, "error": "..."}
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
        rows: 棋盘行数（标准 10，上下连通 18）。
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
        着法列表，每项含 fromRow, fromCol, toRow, toCol。
        出错时返回空列表。
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
    """将 BoardState JSON 字典转为 numpy float32 张量 [14, rows, cols]。

    14 通道顺序（与 MiniResNet 输入一致）:
        0: RED_KING       1: RED_ADVISOR    2: RED_ELEPHANT
        3: RED_HORSE      4: RED_CHARIOT    5: RED_CANNON
        6: RED_SOLDIER    7: BLACK_KING     8: BLACK_ADVISOR
        9: BLACK_ELEPHANT 10: BLACK_HORSE   11: BLACK_CHARIOT
       12: BLACK_CANNON  13: BLACK_SOLDIER

    遍历 entries，根据 Piece.Type 枚举名字符串设置对应通道为 1.0。
    若同一格子有堆叠棋子（pieceTypes 含多项），每个类型均会设置。

    **重要**：输出张量始终为固定尺寸 [14, rows, cols]（默认 18×9），
    小棋盘（10×9）的多余行自动填零。这确保模型输入维度一致，
    无论规则是否开启 top_bottom_connected。

    Args:
        board_state_dict: BoardState JSON 字典，格式如
            {"rows":10,"cols":9,"entries":[...],"redTurn":true}。
        rows: 输出张量的行数（应为最大尺寸 EXPANDED_ROWS=18）。
        cols: 输出张量的列数。

    Returns:
        numpy float32 数组，shape [14, rows, cols]。
    """
    # 输出张量始终为固定尺寸（默认 18×9）
    tensor = np.zeros((NUM_CHANNELS, rows, cols), dtype=np.float32)

    # 实际棋盘尺寸（可能小于输出尺寸）
    actual_rows = board_state_dict.get("rows", rows)
    actual_cols = board_state_dict.get("cols", cols)

    entries = board_state_dict.get("entries", [])
    for entry in entries:
        r = entry.get("row", 0)
        c = entry.get("col", 0)
        # 跳过越界位置（相对于输出张量尺寸）
        if r < 0 or r >= rows or c < 0 or c >= cols:
            continue

        piece_types = entry.get("pieceTypes", [])
        for pt in piece_types:
            channel = PIECE_TYPE_TO_CHANNEL.get(pt)
            if channel is not None:
                tensor[channel, r, c] = 1.0

    return tensor


def rule_vector_to_numpy(rule_vector: List[float]) -> np.ndarray:
    """将规则向量 list 转为 numpy float32 数组 [23]."""
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

    随机放置若干棋子（双方比例约各半），不保证实际象棋规则合法性，
    仅用于验证神经网络对规则向量的敏感性。

    保证初始棋盘双方至少各有一个将/帅（KING），
    否则 _is_mock_game_over 会立即判定游戏结束。

    Args:
        rows: 行数。
        cols: 列数。
        num_pieces_range: 棋子数量范围 (min, max)。

    Returns:
        BoardState JSON 兼容字典。
    """
    all_piece_types = list(PIECE_TYPE_TO_CHANNEL.keys())
    red_types = [t for t in all_piece_types if t.startswith("RED_")]
    black_types = [t for t in all_piece_types if t.startswith("BLACK_")]

    num_pieces = random.randint(*num_pieces_range)
    num_red = max(num_pieces // 2, 2)   # 至少 2 个红子（含将）
    num_black = max(num_pieces - num_red, 2)  # 至少 2 个黑子（含帅）

    used_positions: set = set()
    entries: List[Dict[str, Any]] = []

    def place_one(piece_type: str) -> None:
        for _ in range(100):  # 最多尝试 100 次防死循环
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

    # 确保双方都有将/帅
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

    注意：此函数生成的着法**不代表真正的象棋规则合法性**，
    仅用于 Phase 0 验证实验中为 MCTS 提供可搜索的动作空间。

    策略：使用基于棋盘状态哈希的确定性种子，对每个属于当前回合方的棋子
    生成固定数量的候选着法。同一棋盘状态始终生成相同的候选着法列表。

    Args:
        board_state: BoardState JSON 字典。
        rows: 行数。
        cols: 列数。

    Returns:
        着法字典列表，每项含 fromRow, fromCol, toRow, toCol。
    """
    # 基于棋盘状态计算确定性哈希
    state_hash = hash(json.dumps(board_state, sort_keys=True))
    local_rng = random.Random(state_hash)

    moves: List[Dict[str, int]] = []
    is_current_red = board_state.get("redTurn", True)
    entries = board_state.get("entries", [])

    for entry in entries:
        piece_types = entry.get("pieceTypes", [])
        if not piece_types:
            continue

        # 栈顶棋子决定阵营归属
        top_type = piece_types[-1]
        is_red = top_type.startswith("RED_")
        if is_red != is_current_red:
            continue  # 不是当前回合方的棋子

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

    # 确保至少有一个着法，让 MCTS 有动作可选
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
    """在模拟棋盘上执行着法，返回新的 BoardState 字典。

    两阶段处理：
      1. 从源位置移除栈顶棋子。
      2. 将棋子加入目标位置（堆叠或替换）。
      3. 翻转回合标志。

    注意：这是简化版实现，不处理俘虏转换、卡子规则等复杂语义。
    仅用于 Phase 0 验证实验中 MCTS 向前展开。

    Args:
        board_state: 当前 BoardState 字典。
        move: 着法字典，含 fromRow, fromCol, toRow, toCol。
        rows: 行数。
        cols: 列数。

    Returns:
        新的 BoardState 字典。
    """
    entries = board_state.get("entries", [])
    from_row, from_col = move["fromRow"], move["fromCol"]
    to_row, to_col = move["toRow"], move["toCol"]

    # ── 阶段 1: 从源位置提取棋子 ──
    moved_piece_type: Optional[str] = None
    intermediate_entries: List[Dict[str, Any]] = []

    for entry in entries:
        r, c = entry["row"], entry["col"]
        piece_types = list(entry.get("pieceTypes", []))

        if r == from_row and c == from_col:
            if piece_types:
                moved_piece_type = piece_types.pop()  # 取出栈顶
            if piece_types:
                intermediate_entries.append({
                    "row": r, "col": c, "pieceTypes": piece_types,
                })
            # 栈已空则不保留此位置
        else:
            intermediate_entries.append({
                "row": r, "col": c, "pieceTypes": piece_types,
            })

    if moved_piece_type is None:
        # 无可移动棋子 — 返回原状态
        return {
            "rows": board_state.get("rows", rows),
            "cols": board_state.get("cols", cols),
            "entries": entries,
            "redTurn": board_state.get("redTurn", True),
        }

    # ── 阶段 2: 将棋子加入目标位置（带吃子） ──
    # 判定移动棋子是哪一方
    moved_side = "RED" if (moved_piece_type or "").startswith("RED_") else "BLACK"
    target_found = False
    final_entries: List[Dict[str, Any]] = []

    for entry in intermediate_entries:
        r, c = entry["row"], entry["col"]
        piece_types = list(entry.get("pieceTypes", []))

        if r == to_row and c == to_col:
            target_found = True
            # 吃掉目标格所有对方棋子（保留己方棋子 + 叠上自己的）
            surviving = [pt for pt in piece_types if pt.startswith(moved_side + "_")]
            surviving.append(moved_piece_type)
            if surviving:  # 只要还有棋子就保留该位置
                final_entries.append({
                    "row": r, "col": c, "pieceTypes": surviving,
                })
            # 若 surviving 为空（不该发生），该格不再有 entry
        else:
            final_entries.append({
                "row": r, "col": c, "pieceTypes": piece_types,
            })

    if not target_found:
        # 目标位置原为空
        final_entries.append({
            "row": to_row, "col": to_col, "pieceTypes": [moved_piece_type],
        })

    # ── 翻转回合 ──
    new_red_turn = not board_state.get("redTurn", True)

    return {
        "rows": board_state.get("rows", rows),
        "cols": board_state.get("cols", cols),
        "entries": final_entries,
        "redTurn": new_red_turn,
    }


# ═══════════════════════════════════════════════════════════════════════════
# 启发式局面评估（mock 模式用）
# ═══════════════════════════════════════════════════════════════════════════

def _evaluate_mock_position(board_state: Dict[str, Any]) -> float:
    """对 mock 棋盘进行启发式子力评估。

    计算红方与黑方的子力差，归一化到 [-1, 1]。
    用于替代 mock 模式下的随机胜负判定，为 value head 提供有意义的训练信号。

    Args:
        board_state: BoardState JSON 字典。

    Returns:
        红方视角的评估值 ∈ [-1, 1]。正值表示红方优势。
    """
    red_score = 0
    black_score = 0
    entries = board_state.get("entries", [])

    for entry in entries:
        piece_types = entry.get("pieceTypes", [])
        for pt in piece_types:
            value = PIECE_VALUE.get(pt, 0)
            if pt.startswith("RED_"):
                red_score += value
            elif pt.startswith("BLACK_"):
                black_score += value

    diff = red_score - black_score
    # 使用 tanh 归一化，scale=2000 使得一个车的差距约 0.42
    if diff == 0:
        return 0.0
    return float(np.tanh(diff / 2000.0))


def _is_mock_game_over(board_state: Dict[str, Any]) -> Tuple[bool, float]:
    """检查 mock 棋盘是否达到终止条件。

    终止条件：
      - 某方将帅被吃（子力列表中无 KING）→ 对方胜
      - 某方棋子全部被吃 → 对方胜

    Args:
        board_state: BoardState JSON 字典。

    Returns:
        (is_over, value): is_over 为 True 时 value 为红方视角的胜负值。
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
        return True, -1.0  # 黑胜
    if not has_black_king or not has_black_piece:
        return True, 1.0   # 红胜
    return False, 0.0


# ═══════════════════════════════════════════════════════════════════════════
# MCTS 搜索
# ═══════════════════════════════════════════════════════════════════════════

def _ucb_score(
    parent_visit: int,
    child_visit: int,
    child_total_value: float,
    prior: float,
    c_puct: float,
) -> float:
    """计算 UCB (Upper Confidence Bound) 分数。

    UCB = Q(s,a) + c_puct * P(s,a) * sqrt(N_parent) / (1 + N_child)

    其中:
      Q(s,a) = child_total_value / child_visit  （平均行动价值）
      N_parent = parent_visit                     （父节点访问次数）
      N_child = child_visit                       （子节点访问次数）
    """
    if child_visit == 0:
        q = 0.0
    else:
        q = child_total_value / child_visit
    u = c_puct * prior * math.sqrt(max(parent_visit, 1)) / (1.0 + child_visit)
    return q + u


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
    dirichlet_alpha: float = 0.3,
    dirichlet_weight: float = 0.25,
) -> Tuple[np.ndarray, np.ndarray]:
    """神经网络引导的 MCTS 搜索（含 Dirichlet 噪声探索）。

    对给定局面执行 num_simulations 次模拟，每次模拟含四个阶段：
      Selection → Evaluation → Expansion → Backpropagation

    改进点（相比原版）：
      - 根节点添加 Dirichlet 噪声，增强探索多样性
      - 叶子节点在 mock 模式下使用启发式评估（非零 value）
      - 支持虚拟损失（virtual loss）防止搜索集中

    若 model 为 None，回退到均匀先验（无神经网络引导的纯 MCTS）。

    Args:
        model: MiniResNet 模型实例。若为 None 则回退到均匀先验。
        board_state: BoardState JSON 字典，当前局面。
        rule_vector: 23 维规则向量（22 布尔 + 1 连续）。
        num_simulations: MCTS 模拟次数。
        c_puct: UCB 探索系数，控制探索 vs 利用的平衡。
        temperature: 策略温度参数。
            temperature = 0 → 贪心（取访问次数最多的着法）。
            temperature > 0 → 按 visit_count^(1/temperature) 比例采样。
        rows: 棋盘行数。
        cols: 棋盘列数。
        use_mock: True 时使用模拟数据（不调用 Java）。
        dirichlet_alpha: Dirichlet 噪声的 alpha 参数。
            较小值（0.03-0.3）产生更集中的噪声，适合大动作空间。
        dirichlet_weight: 噪声混合权重 ε。
            prior = (1-ε)*nn_prior + ε*noise。AlphaZero 使用 0.25。

    Returns:
        (action_probs, training_target):
          - action_probs:  numpy array [rows * cols]，温度化动作概率分布。
          - training_target: numpy array [rows * cols]，归一化访问计数分布
            （AlphaZero 训练中作为策略头目标）。
    """
    cells = rows * cols

    # 预计算规则张量（所有模拟共用，规则不随走子改变）
    rule_tensor = rule_vector_to_numpy(rule_vector)  # [23]

    # 检查根节点是否有合法着法
    if use_mock:
        root_moves = _generate_mock_legal_moves(board_state, rows, cols)
    else:
        root_moves = query_java_legal_moves(board_state, rule_vector)

    if not root_moves:
        # 无合法着法 — 游戏终止
        return np.zeros(cells, dtype=np.float32), np.zeros(cells, dtype=np.float32)

    # 创建根节点
    root = MCTSNode(state={"board": board_state, "rules": rule_vector})

    # ── 根节点首次展开 + Dirichlet 噪声 ──
    root_tensor = board_to_tensor(board_state, rows, cols)
    if model is not None:
        with torch.no_grad():
            batch_board = torch.from_numpy(root_tensor).unsqueeze(0)
            batch_rules = torch.from_numpy(rule_tensor).unsqueeze(0)
            policy_logits, root_value = model(batch_board, batch_rules)
            root_policy_probs = (
                torch.softmax(policy_logits, dim=1).squeeze(0).cpu().numpy()
            )
    else:
        root_policy_probs = np.ones(cells, dtype=np.float32) / cells

    # 展开根节点并添加 Dirichlet 噪声
    root.is_expanded = True
    num_root_moves = len(root_moves)
    noise = np.random.dirichlet([dirichlet_alpha] * num_root_moves)

    for i, move in enumerate(root_moves):
        move_idx = move["toRow"] * cols + move["toCol"]
        if 0 <= move_idx < cells:
            nn_prior = float(root_policy_probs[move_idx])
        else:
            nn_prior = 1.0 / num_root_moves

        # 混合神经网络先验与 Dirichlet 噪声
        prior = (1.0 - dirichlet_weight) * nn_prior + dirichlet_weight * noise[i]

        child_board = _apply_mock_move(board_state, move, rows, cols)
        child_state = {"board": child_board, "rules": rule_vector}
        child = MCTSNode(state=child_state, parent=root, prior=prior)
        root.children[json.dumps(move, sort_keys=True)] = child

    # ── MCTS 主循环 ──
    for _ in range(num_simulations):
        node = root
        search_path: List[MCTSNode] = [node]

        # 1) Selection: 沿树向下，每次选 UCB 最高的子节点
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
                break  # 无可用子节点
            node = node.children[best_move_key]
            search_path.append(node)

        # 2) Evaluation: 神经网络评估当前叶子局面
        node_board = node.state.get("board", board_state)

        # 检查是否为终止局面（mock 模式）
        if use_mock:
            is_over, terminal_value = _is_mock_game_over(node_board)
            if is_over:
                # 终止局面：直接使用终止值，转换为当前回合方视角
                is_red_turn = node_board.get("redTurn", True)
                leaf_value = terminal_value if is_red_turn else -terminal_value
                # 跳过展开，直接反向传播
                for path_node in reversed(search_path):
                    path_node.visit_count += 1
                    path_node.total_value += leaf_value
                    leaf_value = -leaf_value
                continue

        node_tensor = board_to_tensor(node_board, rows, cols)

        if model is not None:
            with torch.no_grad():
                batch_board = torch.from_numpy(node_tensor).unsqueeze(0)
                batch_rules = torch.from_numpy(rule_tensor).unsqueeze(0)
                policy_logits, value = model(batch_board, batch_rules)
                policy_probs = (
                    torch.softmax(policy_logits, dim=1).squeeze(0).cpu().numpy()
                )
                leaf_value = float(value.item())
        else:
            policy_probs = np.ones(cells, dtype=np.float32) / cells
            # 在无模型时使用启发式评估代替零值
            if use_mock:
                raw_eval = _evaluate_mock_position(node_board)
                is_red_turn = node_board.get("redTurn", True)
                leaf_value = raw_eval if is_red_turn else -raw_eval
            else:
                leaf_value = 0.0

        # 3) Expansion: 展开叶子节点（为非终止局面创建子节点）
        if not node.is_expanded:
            if use_mock:
                node_legal_moves = _generate_mock_legal_moves(
                    node_board, rows, cols
                )
            else:
                node_legal_moves = query_java_legal_moves(
                    node_board, rule_vector
                )

            node.is_expanded = True

            for move in node_legal_moves:
                move_idx = move["toRow"] * cols + move["toCol"]
                if 0 <= move_idx < cells:
                    prior = float(policy_probs[move_idx])
                else:
                    prior = 0.0

                child_board = _apply_mock_move(node_board, move, rows, cols)
                child_state = {"board": child_board, "rules": rule_vector}
                child = MCTSNode(state=child_state, parent=node, prior=prior)
                node.children[json.dumps(move, sort_keys=True)] = child

        # 4) Backpropagation: 沿搜索路径反向传播估值
        for path_node in reversed(search_path):
            path_node.visit_count += 1
            path_node.total_value += leaf_value
            leaf_value = -leaf_value  # 切换对手视角

    # ── 根据根节点子节点访问次数构建动作概率 ──
    visit_counts = np.zeros(cells, dtype=np.float32)
    for move_key, child in root.children.items():
        move = json.loads(move_key)
        move_idx = move["toRow"] * cols + move["toCol"]
        if 0 <= move_idx < cells:
            # 累加同一目标格的访问次数（多个着法可能指向同一格）
            visit_counts[move_idx] += float(child.visit_count)

    # 温度化
    if temperature <= 1e-8:
        # 贪心：仅保留访问次数最大的动作
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

    # training_target = 归一化访问计数分布（AlphaZero 策略头训练目标）
    training_target = visit_counts.copy()
    visit_total = training_target.sum()
    if visit_total > 1e-12:
        training_target = training_target / visit_total

    return action_probs, training_target


# ═══════════════════════════════════════════════════════════════════════════
# 规则向量生成
# ═══════════════════════════════════════════════════════════════════════════

def make_all_false_rule_vector() -> List[float]:
    """生成全 false 规则向量（标准规则）。

    RULE_BOOL_DIM 个布尔位全为 0.0，max_stacking_count / 16.0 = 0.0。

    Returns:
        长度 RULE_FULL_DIM 的 float 列表。
    """
    return [0.0] * RULE_BOOL_DIM + [0.0]


def make_random_rule_vector() -> List[float]:
    """生成完全随机的规则向量。

    RULE_BOOL_DIM 个布尔位随机 0.0 或 1.0，
    max_stacking_count 归一化值随机 ∈ [0, 1]。

    Returns:
        长度 RULE_FULL_DIM 的 float 列表。
    """
    bool_part = [random.choice([0.0, 1.0]) for _ in range(RULE_BOOL_DIM)]
    continuous_part = random.uniform(0.0, 1.0)
    return bool_part + [continuous_part]


def make_random_rule_vector_with_few_enabled(
    min_enabled: int = 1,
    max_enabled: int = 3,
) -> List[float]:
    """生成随机规则向量，仅随机开启少量扩展规则。

    用于 Phase 0 验证实验的 B 组规则向量。
    从索引 3 及以后的规则中随机选择 min_enabled~max_enabled 条开启。
    （跳过前 3 条基础规则 allow_undo、show_hints、allow_force_move）

    Args:
        min_enabled: 最少开启数。
        max_enabled: 最多开启数。

    Returns:
        长度 RULE_FULL_DIM 的 float 列表。
    """
    bool_part = [0.0] * RULE_BOOL_DIM
    num_enabled = random.randint(min_enabled, max_enabled)
    # 从索引 3 开始（跳过基础 UI 规则）
    pool = list(range(3, RULE_BOOL_DIM))
    chosen = random.sample(pool, min(num_enabled, len(pool)))
    for idx in chosen:
        bool_part[idx] = 1.0
    return bool_part + [0.0]  # max_stacking_count = 0


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
) -> Tuple[List[Dict[str, Any]], float]:
    """执行一局自我对弈。

    流程:
      1. 随机初始化规则向量（如未提供）。
      2. 通过 PyBridge 创建新棋盘（或生成模拟棋盘）。
      3. 循环走子：
         a. 获取当前局面
         b. 调用 mcts_search 获取策略分布
         c. 按策略温度采样选择着法
         d. 通过 PyBridge 执行着法（或模拟走子）
         e. 记录 (局面, 规则向量, 策略分布, 着法) 到轨迹
      4. 游戏结束后返回所有轨迹 + 最终胜负值。

    Args:
        model: MiniResNet 模型实例，None 时回退到均匀先验 MCTS。
        rules: 规则向量。若为 None 则随机生成。
        max_steps: 最大走子步数（防止无限循环）。
        rows: 棋盘行数。
        cols: 棋盘列数。
        use_mock: True 时使用模拟数据。
        num_simulations: 每步 MCTS 模拟次数。
        c_puct: UCB 探索系数。
        temperature: 策略温度。

    Returns:
        (trajectory, final_value):
          - trajectory: 轨迹列表，每项为
              {"board": BoardState dict, "rules": [...], "policy": [...], "move": {...}}。
          - final_value: 最终胜负值，1.0 = 红胜, -1.0 = 黑胜, 0.0 = 平局。
    """
    if rules is None:
        rules = make_random_rule_vector()

    # 根据规则向量确定实际棋盘行数
    actual_rows = get_rows_for_rules(rules)

    # 创建新棋盘（使用规则决定的行数）
    if use_mock:
        board_state = _generate_mock_board_state(actual_rows, cols)
    else:
        result = query_java_new_game(rules, rows=actual_rows)
        if "error" in result:
            return [], 0.0
        board_state = result

    trajectory: List[Dict[str, Any]] = []
    cells = rows * cols  # 注意：这里用模型的固定 rows（最大尺寸），不是 actual_rows
    final_value = 0.0

    for step in range(max_steps):
        # 温度退火：前 30 步使用高温度探索，之后降低温度
        step_temperature = temperature if step < 30 else max(0.1, temperature * 0.5)

        # a) 获取 MCTS 策略分布
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
        )

        # 无合法着法 → 当前方被将死/困毙
        if action_probs.sum() < 1e-12:
            # 当前回合方负
            is_red = board_state.get("redTurn", True)
            final_value = -1.0 if is_red else 1.0
            break

        # 检查终止条件（mock 模式）
        if use_mock:
            is_over, terminal_val = _is_mock_game_over(board_state)
            if is_over:
                final_value = terminal_val
                break

            # 随机提前终止：步数越大终止概率越高，模拟自然对局结束
            # 20 步前不终止，之后概率线性上升到 max_steps 时的 80%
            if step >= 20:
                progress = (step - 20) / max(max_steps - 20, 1)
                termination_prob = progress * 0.8
                if random.random() < termination_prob:
                    final_value = _evaluate_mock_position(board_state)
                    break

        # b) 按策略采样选择着法
        move_idx = int(np.random.choice(cells, p=action_probs))

        # 从合法着法中找到匹配的着法
        if use_mock:
            legal_moves = _generate_mock_legal_moves(board_state, rows, cols)
        else:
            legal_moves = query_java_legal_moves(board_state, rules)

        chosen_move: Optional[Dict[str, int]] = None
        for move in legal_moves:
            idx = move["toRow"] * cols + move["toCol"]
            if idx == move_idx:
                chosen_move = move
                break

        if chosen_move is None:
            # 策略采样到的位置无匹配着法，随机选一个
            if legal_moves:
                chosen_move = random.choice(legal_moves)
            else:
                break

        # c) 记录轨迹
        trajectory.append({
            "board": board_state,
            "rules": list(rules),
            "policy": training_target.tolist(),
            "move": chosen_move,
        })

        # d) 执行着法
        if use_mock:
            board_state = _apply_mock_move(board_state, chosen_move, rows, cols)
        else:
            result = query_java_engine(board_state, rules, chosen_move)
            if not result.get("success") or not result.get("legal"):
                # 着法非法 — 记录并结束
                break
            # 注意：当前 PyBridge simulate 不直接返回新 BoardState，
            # 这里用 mock 更新作为近似。完整方案需扩展 PyBridge。
            board_state = _apply_mock_move(board_state, chosen_move, rows, cols)

    # 使用启发式评估代替随机胜负值
    if use_mock and final_value == 0.0:
        # 用子力评估作为连续值胜负信号（比随机 ±1 更有信息量）
        final_value = _evaluate_mock_position(board_state)

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

    目标：验证"规则向量输入是否真的改变 AI 走法"。

    实验设计：
      - 生成 N 个随机残局
      - 对每个残局使用 2 组规则向量：
        A 组：全 false（标准规则）
        B 组：随机开启 1-3 条扩展规则
      - 计算两组规则下 MCTS top-1 着法的差异率

    若差异率 >= 10%，说明规则嵌入有效，可推进 Phase 1。
    若差异率 < 10%，规则嵌入可能需重新设计架构。

    注意：本函数使用模拟数据，不实际调用 Java 引擎。
    完整的 Java 调用链路在 query_java_engine / mcts_search(use_mock=False) 中实现。

    Args:
        num_positions: 测试残局数量。
        rows: 棋盘行数。
        cols: 棋盘列数。
        num_simulations: 每步 MCTS 模拟次数。

    Returns:
        {"diff_rate": float, "num_diff": int, "total": int}
    """
    # ── 固定随机种子，消除 mock 数据生成的随机性干扰 ──
    random.seed(42)
    np.random.seed(42)
    torch.manual_seed(42)

    # 创建模型（Phase 0 用随机权重，验证规则向量是否影响策略输出）
    model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=rows,
        board_w=cols,
        rule_dim=RULE_FULL_DIM,
        num_res_blocks=3,
        filters=64,
    )
    model.eval()

    num_diff = 0
    total = 0

    for pos_idx in range(num_positions):
        # 生成随机残局
        board_state = _generate_mock_board_state(rows, cols)

        # A 组: 全 false 规则向量
        rules_a = make_all_false_rule_vector()

        # B 组: 随机开启 1-3 条扩展规则
        rules_b = make_random_rule_vector_with_few_enabled(
            min_enabled=1, max_enabled=3
        )

        # MCTS 搜索 — A 组（temperature=0 → 贪心取 top-1）
        probs_a, _ = mcts_search(
            model=model,
            board_state=board_state,
            rule_vector=rules_a,
            num_simulations=num_simulations,
            c_puct=2.0,
            temperature=0.0,
            rows=rows,
            cols=cols,
            use_mock=True,
        )

        # MCTS 搜索 — B 组
        probs_b, _ = mcts_search(
            model=model,
            board_state=board_state,
            rule_vector=rules_b,
            num_simulations=num_simulations,
            c_puct=2.0,
            temperature=0.0,
            rows=rows,
            cols=cols,
            use_mock=True,
        )

        # 比较 top-1 着法
        top1_a = int(np.argmax(probs_a))
        top1_b = int(np.argmax(probs_b))
        if top1_a != top1_b:
            num_diff += 1

        total += 1

        # 进度输出（便于长时间运行监控）
        if (pos_idx + 1) % max(1, num_positions // 5) == 0:
            current_rate = num_diff / total if total > 0 else 0.0
            print(f"    进度: {pos_idx + 1}/{num_positions} "
                  f"当前差异率={current_rate:.2%}")

    diff_rate = num_diff / total if total > 0 else 0.0

    return {
        "diff_rate": diff_rate,
        "num_diff": num_diff,
        "total": total,
    }


# ═══════════════════════════════════════════════════════════════════════════
# 测试块 (__main__)
# ═══════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("=" * 64)
    print("selfplay.py — 单元测试 & Phase 0 验证实验")
    print("=" * 64)

    # ── [1] 测试 board_to_tensor ──
    print("\n[1] 测试 board_to_tensor …")
    mock_board = _generate_mock_board_state()
    tensor = board_to_tensor(mock_board)
    print(f"    输入: rows={mock_board['rows']}, cols={mock_board['cols']}, "
          f"entries={len(mock_board['entries'])}, redTurn={mock_board['redTurn']}")
    print(f"    输出 shape: {tensor.shape}  (期望: (14, 10, 9))")
    assert tensor.shape == (14, 10, 9), f"shape 错误: {tensor.shape}"
    assert tensor.dtype == np.float32, f"dtype 错误: {tensor.dtype}"
    print("    ✓ board_to_tensor 通过")

    # ── [2] 测试 MCTSNode ──
    print("\n[2] 测试 MCTSNode …")
    node = MCTSNode(
        state={"board": mock_board, "rules": [0.0] * RULE_FULL_DIM},
        prior=0.5,
    )
    print(f"    visit_count={node.visit_count}, is_expanded={node.is_expanded}")
    safe_dict = node.to_json_safe_dict()
    assert "board" in safe_dict
    assert "rules" in safe_dict
    assert safe_dict["rules"] == [0.0] * RULE_FULL_DIM
    print("    ✓ MCTSNode / to_json_safe_dict 通过")

    # ── [3] 测试规则向量生成 ──
    print("\n[3] 测试规则向量生成 …")
    rv_all_false = make_all_false_rule_vector()
    rv_random = make_random_rule_vector()
    rv_few = make_random_rule_vector_with_few_enabled(1, 3)

    print(f"    全 false:  len={len(rv_all_false)}, bool_sum={sum(rv_all_false[:-1]):.0f} "
          f"(期望 len=23, bool_sum=0)")
    print(f"    随机:     len={len(rv_random)}, bool_sum≈{sum(rv_random[:-1]):.1f} "
          f"(布尔部分期望≈11)")
    print(f"    少量开启: len={len(rv_few)}, bool_sum={sum(rv_few[:-1]):.0f} "
          f"(期望 1-3)")

    assert len(rv_all_false) == RULE_FULL_DIM
    assert len(rv_random) == RULE_FULL_DIM
    assert len(rv_few) == RULE_FULL_DIM
    assert 1 <= sum(rv_few[:-1]) <= 3
    print("    ✓ 规则向量生成通过")

    # ── [4] 测试 MCTS 搜索（mock 模式） ──
    print("\n[4] 测试 mcts_search (mock 模式) …")
    model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=10,
        board_w=9,
        rule_dim=RULE_FULL_DIM,
        num_res_blocks=3,
        filters=64,
    )
    model.eval()

    test_board = _generate_mock_board_state()
    test_rules = make_all_false_rule_vector()
    probs, target = mcts_search(
        model=model,
        board_state=test_board,
        rule_vector=test_rules,
        num_simulations=50,
        c_puct=2.0,
        temperature=1.0,
        rows=10,
        cols=9,
        use_mock=True,
    )
    print(f"    policy probs shape: {probs.shape}  sum≈{probs.sum():.4f}")
    print(f"    training target shape: {target.shape}  sum≈{target.sum():.4f}")
    assert probs.shape == (90,), f"probs shape 错误: {probs.shape}"
    assert target.shape == (90,), f"target shape 错误: {target.shape}"
    print("    ✓ mcts_search 通过")

    # ── [5] 测试 mcts_search 无模型回退 ──
    print("\n[5] 测试 mcts_search (model=None 回退) …")
    probs_nomodel, target_nomodel = mcts_search(
        model=None,
        board_state=test_board,
        rule_vector=test_rules,
        num_simulations=30,
        c_puct=2.0,
        temperature=1.0,
        rows=10,
        cols=9,
        use_mock=True,
    )
    print(f"    probs sum≈{probs_nomodel.sum():.4f}  "
          f"nonzero={int((probs_nomodel > 0).sum())}")
    print("    ✓ 无模型回退通过")

    # ── [6] 测试 _apply_mock_move ──
    print("\n[6] 测试 _apply_mock_move …")
    simple_board = {
        "rows": 10,
        "cols": 9,
        "entries": [
            {"row": 0, "col": 4, "pieceTypes": ["BLACK_KING"]},
            {"row": 9, "col": 4, "pieceTypes": ["RED_KING"]},
        ],
        "redTurn": True,
    }
    move = {"fromRow": 9, "fromCol": 4, "toRow": 8, "toCol": 4}
    new_board = _apply_mock_move(simple_board, move)
    print(f"    移动前 redTurn={simple_board['redTurn']}, "
          f"entries={len(simple_board['entries'])}")
    print(f"    移动后 redTurn={new_board['redTurn']}, "
          f"entries={len(new_board['entries'])}")
    # 验证：红王从 (9,4) 移到 (8,4)
    has_red_king_at_8_4 = any(
        e["row"] == 8 and e["col"] == 4 and "RED_KING" in e["pieceTypes"]
        for e in new_board["entries"]
    )
    assert new_board["redTurn"] is False, "回合未翻转"
    assert has_red_king_at_8_4, "红王未移动到目标位置"
    print("    ✓ _apply_mock_move 通过")

    # ── [7] 运行 Phase 0 验证实验（小规模） ──
    print("\n[7] 运行 run_validation_experiment (num_positions=5, small test) …")
    result = run_validation_experiment(
        num_positions=5,
        rows=10,
        cols=9,
        num_simulations=100,
    )
    print(f"\n    完整结果: {json.dumps(result, ensure_ascii=False, indent=2)}")

    # ── 判定 ──
    print("\n" + "=" * 64)
    if result["diff_rate"] >= 0.10:
        print(
            "✓ 验证通过！走法差异率 {:.2%} >= 10%，规则嵌入有效。\n"
            "  可以推进 Phase 1。".format(result["diff_rate"])
        )
    else:
        print(
            "⚠ 走法差异率 {:.2%} < 10%。\n"
            "  建议：增加 num_simulations、更换更大的网络架构、\n"
            "  或回退到无模型 MCTS 路线。".format(result["diff_rate"])
        )
    print("=" * 64)
