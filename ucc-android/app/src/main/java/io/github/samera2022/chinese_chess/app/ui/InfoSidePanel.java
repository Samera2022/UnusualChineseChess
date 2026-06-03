package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import io.github.samera2022.chinese_chess.common.model.Move;

/**
 * 侧边信息面板 - 显示走子方和游戏状态
 */
public class InfoSidePanel extends LinearLayout {

    private final TextView turnTextView;
    private final TextView statusTextView;

    public InfoSidePanel(Context context) {
        this(context, null);
    }

    public InfoSidePanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoSidePanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        turnTextView = new TextView(context);
        turnTextView.setTextSize(14);
        turnTextView.setPadding(8, 8, 8, 4);
        addView(turnTextView);

        statusTextView = new TextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setPadding(8, 4, 8, 8);
        addView(statusTextView);
    }

    /**
     * 显示当前走子方信息（独立于走棋历史摘要）
     */
    public void setCurrentTurn(boolean isRedTurn) {
        String turnText = isRedTurn ? "红方走棋" : "黑方走棋";
        turnTextView.setText(turnText);
        turnTextView.setTextColor(isRedTurn ? 0xFFCC0000 : 0xFF323232);
        // 清除之前 onMoveHistoryChanged 可能设置的摘要文字
        turnTextView.setTag(null);
    }

    /**
     * 设置游戏状态
     * @param status 状态字符串 (GameStatus.name())
     */
    public void setGameStatus(String status) {
        if (status == null) return;
        String displayText;
        switch (status) {
            case "RUNNING":
                displayText = "游戏中";
                break;
            case "RED_CHECKMATE":
                displayText = "红方被将杀！黑方胜";
                break;
            case "BLACK_CHECKMATE":
                displayText = "黑方被将杀！红方胜";
                break;
            case "DRAW":
                displayText = "平局";
                break;
            default:
                displayText = status;
        }
        statusTextView.setText(displayText);
    }

    /**
     * 走棋历史变更回调 — 在 statusTextView 中显示最后一步摘要，不影响 turnTextView
     */
    public void onMoveHistoryChanged(List<Move> moves) {
        if (moves != null && !moves.isEmpty()) {
            Move last = moves.get(moves.size() - 1);
            String summary = String.format(Locale.getDefault(),
                    "上一步: %s (%d,%d)→(%d,%d)",
                    last.getPiece() != null ? last.getPiece().getDisplayName() : "?",
                    last.getFromRow(), last.getFromCol(),
                    last.getToRow(), last.getToCol());
            statusTextView.setText(summary);
        } else {
            statusTextView.setText("游戏中");
        }
    }
}
