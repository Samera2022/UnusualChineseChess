package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Piece;

public class BoardView extends View {

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
        void onPieceDrag(int fromRow, int fromCol, int toRow, int toCol);
        void onPieceLongPress(int row, int col);
    }

    private int boardRows = 10;
    private int boardCols = 9;
    private static final float PIECE_RADIUS_RATIO = 0.42f;

    private static final int COLOR_BOARD_BG = 0xFFE6B450;
    private static final int COLOR_GRID = 0xFF000000;
    private static final int COLOR_RED_PIECE = 0xFFCC0000;
    private static final int COLOR_RED_TEXT = 0xFFCC0000;
    private static final int COLOR_BLACK_PIECE = 0xFF323232;
    private static final int COLOR_BLACK_TEXT = 0xFF323232;
    private static final int COLOR_SELECTED = 0x80FFFF00;
    private static final int COLOR_VALID_MOVE = 0x8000FF00;
    private static final int COLOR_PIECE_BG = 0xFFFFF8DC;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint palacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint validMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stackBadgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stackBadgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stackBadgeText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private BoardState boardState;
    private boolean isRedTurn = true;
    private int selectedRow = -1, selectedCol = -1;
    private List<Point> validMoves = new ArrayList<>();
    private OnCellClickListener cellClickListener;

    private float touchDownX, touchDownY;
    private int touchDownRow = -1, touchDownCol = -1;
    private static final int LONG_PRESS_THRESHOLD = 500;
    private static final float DRAG_THRESHOLD = 20;
    private final android.os.Handler longPressHandler = new android.os.Handler(msg -> {
        if (cellClickListener != null)
            cellClickListener.onPieceLongPress(msg.arg1, msg.arg2);
        return true;
    });

    private float cellSize, paddingLeft, paddingTop, pieceRadius, density;

    public BoardView(Context context) { super(context); init(); }
    public BoardView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(2);
        palacePaint.setColor(COLOR_GRID);
        palacePaint.setStyle(Paint.Style.STROKE);
        palacePaint.setStrokeWidth(2);
        pieceBgPaint.setColor(COLOR_PIECE_BG);
        pieceBgPaint.setStyle(Paint.Style.FILL);
        redPiecePaint.setColor(COLOR_RED_PIECE);
        redPiecePaint.setStyle(Paint.Style.STROKE);
        redPiecePaint.setStrokeWidth(3);
        blackPiecePaint.setColor(COLOR_BLACK_PIECE);
        blackPiecePaint.setStyle(Paint.Style.STROKE);
        blackPiecePaint.setStrokeWidth(3);
        textPaint.setTextAlign(Paint.Align.CENTER);
        selectedPaint.setColor(COLOR_SELECTED);
        validMovePaint.setColor(COLOR_VALID_MOVE);
        validMovePaint.setStyle(Paint.Style.FILL);
        stackBadgeFill.setColor(0xFFFFC107);
        stackBadgeFill.setStyle(Paint.Style.FILL);
        stackBadgeStroke.setColor(0xFF000000);
        stackBadgeStroke.setStyle(Paint.Style.STROKE);
        stackBadgeStroke.setStrokeWidth(1);
        stackBadgeText.setColor(0xFF000000);
        stackBadgeText.setTextAlign(Paint.Align.CENTER);
        stackBadgeText.setFakeBoldText(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcLayout(w, h);
    }

    private void recalcLayout(int w, int h) {
        float aw = w - getPaddingLeft() - getPaddingRight();
        float ah = h - getPaddingTop() - getPaddingBottom();
        cellSize = Math.min(aw / (boardCols - 1), ah / (boardRows - 1));
        paddingLeft = (w - cellSize * (boardCols - 1)) / 2f;
        paddingTop = (h - cellSize * (boardRows - 1)) / 2f;
        pieceRadius = cellSize * PIECE_RADIUS_RATIO;
        textPaint.setTextSize(cellSize * 0.5f);
        stackBadgeText.setTextSize(cellSize * 0.25f);
    }

    public void setBoardDimensions(int rows, int cols) {
        this.boardRows = rows;
        this.boardCols = cols;
        recalcLayout(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(COLOR_BOARD_BG);
        drawBoardGrid(canvas);
        drawPieces(canvas);
        drawHighlights(canvas);
    }

    // ── Grid ──

    private void drawBoardGrid(Canvas canvas) {
        float gridW = (boardCols - 1) * cellSize;
        float gridH = (boardRows - 1) * cellSize;
        int riverSplit = boardRows / 2;

        for (int r = 0; r < boardRows; r++) {
            float y = paddingTop + r * cellSize;
            canvas.drawLine(paddingLeft, y, paddingLeft + gridW, y, gridPaint);
        }
        for (int c = 0; c < boardCols; c++) {
            float x = paddingLeft + c * cellSize;
            float y0 = paddingTop;
            float y1 = paddingTop + (riverSplit - 1) * cellSize;
            float y2 = paddingTop + riverSplit * cellSize;
            float y3 = paddingTop + gridH;
            canvas.drawLine(x, y0, x, y1, gridPaint);
            canvas.drawLine(x, y2, x, y3, gridPaint);
        }
        // river-side borders
        canvas.drawLine(paddingLeft, paddingTop + (riverSplit - 1) * cellSize,
                paddingLeft, paddingTop + riverSplit * cellSize, gridPaint);
        canvas.drawLine(paddingLeft + gridW, paddingTop + (riverSplit - 1) * cellSize,
                paddingLeft + gridW, paddingTop + riverSplit * cellSize, gridPaint);

        // 楚河漢界
        float ry = paddingTop + riverSplit * cellSize;
        float q = gridW / 4f;
        textPaint.setTextSize(cellSize * 0.35f);
        textPaint.setColor(COLOR_GRID);
        canvas.drawText("楚  河", paddingLeft + q, ry - cellSize * 0.15f, textPaint);
        canvas.drawText("漢  界", paddingLeft + q * 3, ry - cellSize * 0.15f, textPaint);
        textPaint.setTextSize(cellSize * 0.5f);

        // 将宫（参考 PC 端 BoardPanel.drawPalaceGlobal）
        float px1 = paddingLeft + 3 * cellSize;
        float px2 = paddingLeft + 5 * cellSize;
        if (boardRows == 10) {
            // 标准模式 10行
            // 黑方九宫: 行 0-2
            drawPalaceRect(canvas, px1, paddingTop + 0 * cellSize, px2, paddingTop + 2 * cellSize);
            drawPalaceX(canvas, px1, paddingTop + 0 * cellSize, px2, paddingTop + 2 * cellSize);
            // 红方九宫: 行 7-9
            drawPalaceRect(canvas, px1, paddingTop + 7 * cellSize, px2, paddingTop + 9 * cellSize);
            drawPalaceX(canvas, px1, paddingTop + 7 * cellSize, px2, paddingTop + 9 * cellSize);
        } else {
            // 上下连通扩展模式 18行
            // 黑方九宫: 行 2-6（将宫范围扩展，中间岔开在行4）
            float by0 = paddingTop + 2 * cellSize;
            float byM = paddingTop + 4 * cellSize;
            float by1 = paddingTop + 6 * cellSize;
            drawPalaceRect(canvas, px1, by0, px2, by1);
            drawPalaceX(canvas, px1, by0, px2, byM);
            drawPalaceX(canvas, px1, byM, px2, by1);
            // 红方九宫: 行 11-15
            float ry0 = paddingTop + 11 * cellSize;
            float ryM = paddingTop + 13 * cellSize;
            float ry1 = paddingTop + 15 * cellSize;
            drawPalaceRect(canvas, px1, ry0, px2, ry1);
            drawPalaceX(canvas, px1, ry0, px2, ryM);
            drawPalaceX(canvas, px1, ryM, px2, ry1);
        }
    }

    /** 绘制将宫矩形框架（双层） */
    private void drawPalaceRect(Canvas c, float x1, float y1, float x2, float y2) {
        palacePaint.setStyle(Paint.Style.STROKE);
        c.drawRect(x1, y1, x2, y2, palacePaint);
        c.drawRect(x1 + 2, y1 + 2, x2 - 2, y2 - 2, palacePaint);
    }

    /** 绘制将宫 X 对角线（米字的一对） */
    private void drawPalaceX(Canvas c, float x1, float y1, float x2, float y2) {
        palacePaint.setStyle(Paint.Style.STROKE);
        c.drawLine(x1, y1, x2, y2, palacePaint);
        c.drawLine(x2, y1, x1, y2, palacePaint);
    }

    // ── Pieces with stack support ──

    private void drawPieces(Canvas canvas) {
        if (boardState == null) return;
        for (BoardState.StackEntry entry : boardState.getEntries()) {
            List<Piece.Type> types = entry.pieceTypes;
            int ss = types.size();
            if (ss == 0) continue;
            float cx = paddingLeft + entry.col * cellSize;
            float cy = paddingTop + entry.row * cellSize;

            if (ss == 1) {
                drawSinglePiece(canvas, types.get(0), cx, cy);
            } else {
                // Stack rendering: offset each piece, draw badge
                for (int i = 0; i < ss; i++) {
                    int ox = (i % 2 == 0) ? -4 : 4;
                    int oy = (i % 2 == 0) ? -4 : 4;
                    drawSinglePiece(canvas, types.get(i), cx + ox * density, cy + oy * density);
                }
                drawStackBadge(canvas, ss, cx, cy);
            }
        }
    }

    private void drawSinglePiece(Canvas canvas, Piece.Type type, float cx, float cy) {
        boolean isRed = type.isRed();
        canvas.drawCircle(cx, cy, pieceRadius, pieceBgPaint);
        canvas.drawCircle(cx, cy, pieceRadius, isRed ? redPiecePaint : blackPiecePaint);
        String name = type.getChineseName();
        textPaint.setColor(isRed ? COLOR_RED_TEXT : COLOR_BLACK_TEXT);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText(name, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    private void drawStackBadge(Canvas canvas, int count, float cx, float cy) {
        float br = pieceRadius * 0.3f;
        float bx = cx + pieceRadius - br;
        float by = cy + pieceRadius - br;
        canvas.drawCircle(bx, by, br, stackBadgeFill);
        canvas.drawCircle(bx, by, br, stackBadgeStroke);
        Paint.FontMetrics fm = stackBadgeText.getFontMetrics();
        canvas.drawText(String.valueOf(count), bx, by - (fm.ascent + fm.descent) / 2f, stackBadgeText);
    }

    private void drawHighlights(Canvas canvas) {
        if (selectedRow >= 0 && selectedCol >= 0) {
            float cx = paddingLeft + selectedCol * cellSize;
            float cy = paddingTop + selectedRow * cellSize;
            canvas.drawCircle(cx, cy, pieceRadius + 4, selectedPaint);
            // re-draw the piece on top
            if (boardState != null) {
                for (BoardState.StackEntry e : boardState.getEntries()) {
                    if (e.row == selectedRow && e.col == selectedCol && !e.pieceTypes.isEmpty()) {
                        drawSinglePiece(canvas, e.pieceTypes.get(e.pieceTypes.size() - 1), cx, cy);
                        break;
                    }
                }
            }
        }
        for (Point p : validMoves) {
            float cx = paddingLeft + p.y * cellSize;
            float cy = paddingTop + p.x * cellSize;
            canvas.drawCircle(cx, cy, pieceRadius * 0.25f, validMovePaint);
        }
    }

    // ── Touch ──

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        int row = Math.round((y - paddingTop) / cellSize);
        int col = Math.round((x - paddingLeft) / cellSize);
        if (row < 0 || row >= boardRows || col < 0 || col >= boardCols) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = x; touchDownY = y;
                touchDownRow = row; touchDownCol = col;
                longPressHandler.removeMessages(0);
                longPressHandler.sendMessageDelayed(longPressHandler.obtainMessage(0, row, col), LONG_PRESS_THRESHOLD);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - touchDownX) > DRAG_THRESHOLD * density
                        || Math.abs(y - touchDownY) > DRAG_THRESHOLD * density)
                    longPressHandler.removeMessages(0);
                return true;
            case MotionEvent.ACTION_UP:
                longPressHandler.removeMessages(0);
                float dist = (float) Math.hypot(x - touchDownX, y - touchDownY);
                if (dist > DRAG_THRESHOLD * density) {
                    if (cellClickListener != null)
                        cellClickListener.onPieceDrag(touchDownRow, touchDownCol, row, col);
                } else {
                    if (cellClickListener != null)
                        cellClickListener.onCellClick(row, col);
                }
                return true;
        }
        return false;
    }

    // ── Public API ──

    public void setBoardState(BoardState state) {
        this.boardState = state;
        if (state != null && (state.getRows() != boardRows || state.getCols() != boardCols)) {
            setBoardDimensions(state.getRows(), state.getCols());
        }
        invalidate();
    }

    public void setCurrentTurn(boolean isRedTurn) { this.isRedTurn = isRedTurn; }

    public void setSelectedPosition(int row, int col) {
        this.selectedRow = row; this.selectedCol = col;
        invalidate();
    }

    public void clearSelectedPosition() {
        this.selectedRow = -1; this.selectedCol = -1;
        invalidate();
    }

    public void setValidMoves(List<Point> moves) {
        this.validMoves = moves != null ? moves : new ArrayList<>();
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.cellClickListener = listener;
    }

    public float getCellSize() { return cellSize; }
}
