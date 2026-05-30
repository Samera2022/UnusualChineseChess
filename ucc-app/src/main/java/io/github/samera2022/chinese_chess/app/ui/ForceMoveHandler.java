package io.github.samera2022.chinese_chess.app.ui;

import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.api.net.NetModeController;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

/**
 * 强制走子处理 - 负责强制走子请求的发送、确认/拒绝/超时处理、UI 状态管理。
 */
public class ForceMoveHandler {

    private final GameSession session;
    private final NetModeController netController;
    private final BoardPanel boardPanel;
    private final JFrame parentFrame;

    // sequence generator for force-move requests
    private final java.util.concurrent.atomic.AtomicLong forceSeqGenerator = new java.util.concurrent.atomic.AtomicLong(1);
    // pending force requests (seq -> RequestInfo)
    final Map<Long, RequestInfo> pendingForceRequests = new java.util.HashMap<>();
    // processed force requests from peer (to avoid duplicate dialogs)
    final Set<Long> processedPeerRequests = new java.util.HashSet<>();

    static class RequestInfo {
        final int fr, fc, tr, tc;
        final long seq;
        final int historyLen;
        int retries = 0;
        javax.swing.Timer timer;
        RequestInfo(int fr, int fc, int tr, int tc, long seq, int historyLen) {
            this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
            this.seq = seq; this.historyLen = historyLen;
        }
    }

    public ForceMoveHandler(GameSession session, NetModeController netController,
                            BoardPanel boardPanel,
                            JFrame parentFrame) {
        this.session = session;
        this.netController = netController;
        this.boardPanel = boardPanel;
        this.parentFrame = parentFrame;
    }

    /**
     * 注册棋盘面板的强制走子请求监听器。
     * 必须在构造后由 ChineseChessFrame 调用。
     */
    public void install() {
        boardPanel.setForceMoveRequestListener((fromRow, fromCol, toRow, toCol) -> {
            if (!session.getRuleBoolean(RuleRegistry.ALLOW_FORCE_MOVE.registryName)) {
                JOptionPane.showMessageDialog(parentFrame, "强制走子已被禁用！", "提示", JOptionPane.INFORMATION_MESSAGE);
                boardPanel.clearForceMoveIndicator();
                return;
            }
            if (netController.isActive()) {
                sendForceMoveRequest(fromRow, fromCol, toRow, toCol);
            } else {
                // 本地模式：直接执行强制走子
                Piece.Type promotionType = resolveForcePromotionType(fromRow, fromCol, toRow, toCol);
                session.forceApplyMove(fromRow, fromCol, toRow, toCol, promotionType, boardPanel.getSelectedStackIndex());
                boardPanel.clearSelection();
                boardPanel.clearForceMoveIndicator();
                boardPanel.repaint();
            }
        });
    }

    private void sendForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol) {
        long seq = forceSeqGenerator.incrementAndGet();
        int historyLen = session.getMoveHistory().size();
        int selectedStackIndex = boardPanel.getSelectedStackIndex();
        try {
            netController.getSession().sendForceMoveRequest(fromRow, fromCol, toRow, toCol, seq, historyLen, selectedStackIndex);
            RequestInfo info = new RequestInfo(fromRow, fromCol, toRow, toCol, seq, historyLen);
            pendingForceRequests.put(seq, info);
            sendForceRequestWithRetry(seq);
            boardPanel.clearForceMoveIndicator();
        } catch (Throwable t) {
            logError(t);
            JOptionPane.showMessageDialog(parentFrame, "发送强制走子请求失败：" + t.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            boardPanel.clearForceMoveIndicator();
        }
    }

    // retry logic for force-move requests
    void sendForceRequestWithRetry(long seq) {
        RequestInfo info = pendingForceRequests.get(seq);
        if (info == null) return;

        // give up after 3 retries
        if (info.retries++ > 2) {
            pendingForceRequests.remove(seq);
            return;
        }

        // send the request
        try {
            int selectedStackIndex = boardPanel.getSelectedStackIndex();
            netController.getSession().sendForceMoveRequest(info.fr, info.fc, info.tr, info.tc, seq, session.getMoveHistory().size(), selectedStackIndex);
        } catch (Throwable ignored) {}

        // install a timeout
        info.timer = new javax.swing.Timer(3000, e -> sendForceRequestWithRetry(seq));
        info.timer.setRepeats(false);
        info.timer.start();
    }

    // ── 网络回调：收到对方的强制走子请求 ──
    public void onPeerForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol,
                                        long seq, int historyLen, int selectedStackIndex) {
        // 检查是否已经处理过这个请求（防止重复弹窗）
        if (processedPeerRequests.contains(seq)) {
            return;
        }
        processedPeerRequests.add(seq);

        // Validate history length first to avoid replay/old requests
        int localHistory = session.getMoveHistory().size();
        if (historyLen >= 0 && historyLen != localHistory) {
            try { netController.getSession().sendForceMoveReject(fromRow, fromCol, toRow, toCol, seq, "history_mismatch"); } catch (Throwable ignored) {}
            return;
        }

        // 在棋盘上显示对方选中的棋子（黄色高亮）和红色移动指示器
        boardPanel.setRemotePieceHighlight(fromRow, fromCol);
        boardPanel.setForceMoveIndicator(fromRow, fromCol, toRow, toCol);
        boardPanel.repaint();

        // 总是显示确认对话框让用户决定
        int ans = JOptionPane.showConfirmDialog(parentFrame,
                String.format("对方申请强制走子 %d,%d → %d,%d，是否同意？", fromRow, fromCol, toRow, toCol),
                "强制走子申请", JOptionPane.YES_NO_OPTION);

        if (ans == JOptionPane.YES_OPTION) {
            try { netController.getSession().sendForceMoveConfirm(fromRow, fromCol, toRow, toCol, seq, selectedStackIndex); } catch (Throwable ignored) {}
        } else {
            try { netController.getSession().sendForceMoveReject(fromRow, fromCol, toRow, toCol, seq, "user_rejected"); } catch (Throwable ignored) {}
        }
        boardPanel.clearRemotePieceHighlight();
        boardPanel.clearForceMoveIndicator();
    }

    // ── 网络回调：对方确认了我们的强制走子 ──
    public void onPeerForceMoveConfirm(int fromRow, int fromCol, int toRow, int toCol,
                                        long seq, int selectedStackIndex) {
        RequestInfo info = pendingForceRequests.remove(seq);
        if (info != null && info.timer != null) { info.timer.stop(); }
        try {
            JOptionPane.showMessageDialog(parentFrame, "对方已同意你的强制走子", "强制走子成功", JOptionPane.INFORMATION_MESSAGE);
            Piece.Type promotionType = resolveForcePromotionType(fromRow, fromCol, toRow, toCol);
            boolean applied = false;
            try {
                java.lang.reflect.Method m = session.getClass().getMethod("forceApplyMove", int.class, int.class, int.class, int.class, Piece.Type.class, int.class);
                Object res = m.invoke(session, fromRow, fromCol, toRow, toCol, promotionType, selectedStackIndex);
                if (res instanceof Boolean) applied = (Boolean) res;
            } catch (NoSuchMethodException nsme) {
                applied = session.makeMove(fromRow, fromCol, toRow, toCol, promotionType, selectedStackIndex);
            }
            if (applied) {
                boardPanel.clearSelection();
                boardPanel.repaint();
                try {
                    String promoName = promotionType != null ? promotionType.name() : null;
                    netController.getSession().sendForceMoveApplied(fromRow, fromCol, toRow, toCol, seq, promoName, selectedStackIndex);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { logError(t); }
    }

    // ── 网络回调：对方已应用强制走子 ──
    public void onPeerForceMoveApplied(int fromRow, int fromCol, int toRow, int toCol,
                                        long seq, String promotionTypeName, int selectedStackIndex) {
        RequestInfo info = pendingForceRequests.remove(seq);
        if (info != null && info.timer != null) { info.timer.stop(); }
        try {
            Piece.Type promoType = null;
            try { if (promotionTypeName != null) promoType = Piece.Type.valueOf(promotionTypeName); } catch (Exception ignored) {}
            boolean applied = false;
            try {
                java.lang.reflect.Method m = session.getClass().getMethod("forceApplyMove", int.class, int.class, int.class, int.class, Piece.Type.class, int.class);
                Object res = m.invoke(session, fromRow, fromCol, toRow, toCol, promoType, selectedStackIndex);
                if (res instanceof Boolean) applied = (Boolean) res;
            } catch (NoSuchMethodException nsme) {
                applied = session.makeMove(fromRow, fromCol, toRow, toCol, promoType, selectedStackIndex);
            }
            if (applied) {
                boardPanel.clearSelection();
                boardPanel.repaint();
            }
        } catch (Throwable t) { logError(t); }
    }

    // ── 网络回调：对方拒绝了强制走子 ──
    public void onPeerForceMoveReject(int fromRow, int fromCol, int toRow, int toCol,
                                       long seq, String reason) {
        RequestInfo info = pendingForceRequests.remove(seq);
        if (info != null && info.timer != null) info.timer.stop();
        boardPanel.clearForceMoveIndicator();
        JOptionPane.showMessageDialog(parentFrame, "对方已拒绝你的强制走子", "强制走子被拒绝", JOptionPane.WARNING_MESSAGE);
    }

    /** 网络断开/重开时清理待处理请求 */
    public void clearPendingRequests() {
        for (RequestInfo info : pendingForceRequests.values()) {
            if (info.timer != null) info.timer.stop();
        }
        pendingForceRequests.clear();
        processedPeerRequests.clear();
    }

    // helper: prompt promotion type when a forced move drives a soldier to baseline
    Piece.Type resolveForcePromotionType(int fromRow, int fromCol, int toRow, int toCol) {
        ReadonlyBoard board = session.getBoard();
        if (!board.isValid(fromRow, fromCol) || !board.isValid(toRow, toCol)) return null;
        Piece p = board.getPiece(fromRow, fromCol);
        if (p == null) return null;
        if (!session.getRuleBoolean(RuleRegistry.PAWN_PROMOTION.registryName)) return null;
        boolean isSoldier = p.getType() == Piece.Type.RED_SOLDIER || p.getType() == Piece.Type.BLACK_SOLDIER;
        if (!isSoldier) return null;
        boolean isAtOpponentBaseLine = (p.isRed() && toRow == 0) || (!p.isRed() && toRow == 9);
        boolean isAtOwnBaseLine = (p.isRed() && toRow == 9) || (!p.isRed() && toRow == 0);
        boolean allowOwnBaseLine = session.getRuleBoolean(RuleRegistry.ALLOW_OWN_BASE_LINE.registryName);
        if (!(isAtOpponentBaseLine || (isAtOwnBaseLine && allowOwnBaseLine))) return null;

        Piece.Type[] types = p.isRed()
                ? new Piece.Type[]{Piece.Type.RED_CHARIOT, Piece.Type.RED_HORSE, Piece.Type.RED_CANNON, Piece.Type.RED_ELEPHANT, Piece.Type.RED_ADVISOR}
                : new Piece.Type[]{Piece.Type.BLACK_CHARIOT, Piece.Type.BLACK_HORSE, Piece.Type.BLACK_CANNON, Piece.Type.BLACK_ELEPHANT, Piece.Type.BLACK_ADVISOR};
        String[] options = new String[types.length];
        for (int i = 0; i < types.length; i++) options[i] = types[i].getChineseName();
        int choice = JOptionPane.showOptionDialog(parentFrame,
                "选择晋升的棋子：",
                "兵卒晋升",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice >= 0 && choice < types.length) return types[choice];
        return null;
    }

    private static void logError(Throwable t) {
        if (t == null) return;
        System.err.println("[ForceMoveHandler] " + t);
        try { t.printStackTrace(System.err); } catch (Throwable ignored) {}
    }
}
