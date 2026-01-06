package io.github.samera2022.chinese_chess;

/**
 * 游戏测试类 - 验证基本功能
 */
public class GameTest {
    public static void main(String[] args) {
        System.out.println("=== 中国象棋游戏测试 ===\n");

        // 创建游戏引擎
        GameEngine engine = new GameEngine();
        Board board = engine.getBoard();

        System.out.println("初始棋盘：");
        printBoard(board);
        System.out.println();

        // 测试红方马的移动
        System.out.println("测试：红方右马从(9,7)移动到(7,8)");
        if (engine.makeMove(9, 7, 7, 8)) {
            System.out.println("✓ 移动成功");
            printBoard(board);
        } else {
            System.out.println("✗ 移动失败");
        }
        System.out.println();

        // 测试黑方象的移动
        System.out.println("测试：黑方左象从(0,2)移动到(2,4)");
        if (engine.makeMove(0, 2, 2, 4)) {
            System.out.println("✓ 移动成功");
            printBoard(board);
        } else {
            System.out.println("✗ 移动失败");
        }
        System.out.println();

        // 测试红方兵的移动（未过河）
        System.out.println("测试：红方左兵从(6,0)移动到(5,0)");
        if (engine.makeMove(6, 0, 5, 0)) {
            System.out.println("✓ 移动成功");
            printBoard(board);
        } else {
            System.out.println("✗ 移动失败");
        }
        System.out.println();

        // 测试撤销功能
        System.out.println("测试：撤销上一步棋");
        if (engine.undoLastMove()) {
            System.out.println("✓ 撤销成功");
            printBoard(board);
        } else {
            System.out.println("✗ 撤销失败");
        }
        System.out.println();

        // 统计棋子数量
        System.out.println("棋子数量统计：");
        System.out.println("红方棋子: " + board.getRedPieces().size());
        System.out.println("黑方棋子: " + board.getBlackPieces().size());
        System.out.println("总计: " + (board.getRedPieces().size() + board.getBlackPieces().size()));

        System.out.println("\n=== 测试完成 ===");
    }

    private static void printBoard(Board board) {
        System.out.println("  0 1 2 3 4 5 6 7 8");
        for (int i = 0; i < board.getRows(); i++) {
            System.out.print(i + " ");
            for (int j = 0; j < board.getCols(); j++) {
                Piece piece = board.getPiece(i, j);
                if (piece != null) {
                    System.out.print(piece.toString() + " ");
                } else {
                    System.out.print(". ");
                }
            }
            System.out.println();
        }
    }
}

