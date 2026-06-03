package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * 强制走子手势处理 - 长按 + 拖拽检测
 * <p>
 * 非侵入式设计：不再通过独立的 OnTouchListener 拦截触摸事件，
 * 而是由 BoardView 的 onTouchEvent() 内部通过 OnCellClickListener.onPieceLongPress()
 * 转发长按事件。BoardView 已自带长按检测（500ms Handler）和拖拽走棋逻辑。
 * <p>
 * 此类保留为工具类，可供未来扩展（如长按 + 拖拽触发强制走子 UI 反馈），
 * 但不再绑定 OnTouchListener 覆盖 BoardView 的事件分发。
 */
public class ForceMoveHandler {

    // ── 接口 ──
    public interface ForceMoveListener {
        /**
         * 强制走子触发
         * @param fromRow 起始行
         * @param fromCol 起始列
         * @param toRow 目标行
         * @param toCol 目标列
         */
        void onForceMoveTriggered(int fromRow, int fromCol, int toRow, int toCol);

        /**
         * 强制走子就绪（长按选中）
         * @param row 选中行
         * @param col 选中列
         */
        void onForceMoveArmed(int row, int col);

        /**
         * 强制走子取消
         */
        void onForceMoveCancelled();
    }

    private ForceMoveListener forceMoveListener;

    private final GestureDetector gestureDetector;
    private final BoardView boardView;

    // 长按检测状态
    private boolean isArmed = false;
    private int armedRow = -1;
    private int armedCol = -1;

    // 拖拽检测
    private float dragStartX, dragStartY;
    private static final float DRAG_THRESHOLD = 30f; // dp

    public ForceMoveHandler(Context context, BoardView boardView) {
        this.boardView = boardView;
        this.gestureDetector = new GestureDetector(context, new ForceMoveGestureListener());
    }

    /**
     * 绑定到 BoardView（非侵入式：不再设置 OnTouchListener，避免覆盖 BoardView 自身触摸处理）。
     * BoardView 的长按检测通过 OnCellClickListener.onPieceLongPress() 转发。
     */
    public void attach(BoardView view) {
        // ForceMoveHandler 不再使用 OnTouchListener 拦截触摸事件。
        // BoardView 的 onTouchEvent() 已自带长按检测和拖拽走棋逻辑。
        // 如需扩展功能，请在 BoardView.onTouchEvent() 中通过 onPieceLongPress() 处理。
    }

    /**
     * 解除绑定
     */
    public void detach() {
        // 已无 OnTouchListener 需要清除
    }

    /**
     * 设置强制走子监听器
     */
    public void setForceMoveListener(ForceMoveListener listener) {
        this.forceMoveListener = listener;
    }

    // ── 内部方法 ──

    private void arm(int row, int col) {
        this.isArmed = true;
        this.armedRow = row;
        this.armedCol = col;
        if (forceMoveListener != null) {
            forceMoveListener.onForceMoveArmed(row, col);
        }
    }

    private void disarm() {
        this.isArmed = false;
        this.armedRow = -1;
        this.armedCol = -1;
    }

    private float getBoardLeft() {
        return boardView.getLeft() + boardView.getPaddingLeft();
    }

    private float getBoardTop() {
        return boardView.getTop() + boardView.getPaddingTop();
    }

    // ── GestureDetector 实现 ──

    private class ForceMoveGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public void onLongPress(MotionEvent e) {
            // 计算长按位置对应的格子
            float cellSize = boardView.getCellSize();
            int row = (int) Math.round((e.getY() - getBoardTop()) / cellSize);
            int col = (int) Math.round((e.getX() - getBoardLeft()) / cellSize);
            arm(row, col);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 双击可取消强制走子
            if (isArmed) {
                if (forceMoveListener != null) {
                    forceMoveListener.onForceMoveCancelled();
                }
                disarm();
                return true;
            }
            return false;
        }
    }
}
