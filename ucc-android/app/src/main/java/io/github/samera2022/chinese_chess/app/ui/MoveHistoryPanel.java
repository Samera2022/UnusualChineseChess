package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.samera2022.chinese_chess.common.model.Move;

/**
 * 走棋记录面板 - 使用 RecyclerView 显示走棋记录列表
 */
public class MoveHistoryPanel extends RecyclerView {

    private final MoveAdapter adapter;

    public MoveHistoryPanel(Context context) {
        this(context, null);
    }

    public MoveHistoryPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MoveHistoryPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutManager(new LinearLayoutManager(context));
        adapter = new MoveAdapter();
        setAdapter(adapter);
    }

    /**
     * 更新走棋历史列表
     * @param moves 走棋记录列表
     */
    public void setMoveHistory(List<Move> moves) {
        adapter.setMoves(moves);
        adapter.notifyDataSetChanged();
        // 自动滚到底部
        if (moves != null && !moves.isEmpty()) {
            smoothScrollToPosition(moves.size() - 1);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // RecyclerView Adapter
    // ═══════════════════════════════════════════════════════════

    private static class MoveAdapter extends Adapter<MoveViewHolder> {

        private final List<Move> moves = new ArrayList<>();

        void setMoves(List<Move> moves) {
            this.moves.clear();
            if (moves != null) {
                this.moves.addAll(moves);
            }
        }

        @Override
        public MoveViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(8, 4, 8, 4);
            tv.setTextSize(12);
            return new MoveViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(MoveViewHolder holder, int position) {
            Move move = moves.get(position);
            String text = formatMove(position + 1, move);
            holder.textView.setText(text);
            // 红黑交替底色
            int bgColor = (position % 2 == 0) ? 0x10FFFFFF : 0x00000000;
            holder.textView.setBackgroundColor(bgColor);
        }

        @Override
        public int getItemCount() {
            return moves.size();
        }

        private String formatMove(int index, Move move) {
            String pieceName = move.getPiece() != null ? move.getPiece().getDisplayName() : "?";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.getDefault(),
                    "%d. %s (%d,%d)→(%d,%d)",
                    index, pieceName,
                    move.getFromRow(), move.getFromCol(),
                    move.getToRow(), move.getToCol()));
            if (move.isStacking()) {
                sb.append(" [堆叠]");
            } else if (move.isCaptureConversion() && move.getCapturedPiece() != null) {
                sb.append(" [俘虏 ").append(move.getCapturedPiece().getDisplayName()).append("]");
            } else if (move.getCapturedPiece() != null) {
                sb.append(" [吃").append(move.getCapturedPiece().getDisplayName()).append("]");
            }
            return sb.toString();
        }
    }

    private static class MoveViewHolder extends ViewHolder {
        final TextView textView;

        MoveViewHolder(TextView itemView) {
            super(itemView);
            this.textView = itemView;
        }
    }
}
