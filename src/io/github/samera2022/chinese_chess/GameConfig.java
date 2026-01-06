package io.github.samera2022.chinese_chess;

/**
 * 游戏配置类 - 管理游戏的各种设置
 */
public class GameConfig {
    // UI配置
    public static final int CELL_SIZE = 50; // 每个格子的像素大小
    public static final int BOARD_WIDTH = 450; // 9列 * 50
    public static final int BOARD_HEIGHT = 500; // 10行 * 50

    // 棋子颜色配置
    public static final int RED_PIECE_COLOR = 0xC80000;
    public static final int BLACK_PIECE_COLOR = 0x323232;
    public static final int BOARD_COLOR = 0xE6B450;
    public static final int GRID_COLOR = 0x000000;
    public static final int SELECTED_COLOR = 0xFFFF00;
    public static final int VALID_MOVE_COLOR = 0x00FF00;

    // 游戏规则配置
    public static final int BOARD_ROWS = 10;
    public static final int BOARD_COLS = 9;

    // 宫内坐标范围
    public static final int PALACE_MIN_COL = 3;
    public static final int PALACE_MAX_COL = 5;
    public static final int RED_PALACE_MIN_ROW = 7;
    public static final int RED_PALACE_MAX_ROW = 9;
    public static final int BLACK_PALACE_MIN_ROW = 0;
    public static final int BLACK_PALACE_MAX_ROW = 2;

    // 过河界限
    public static final int RIVER_RED_LIMIT = 5; // 红方过河（row < 5）
    public static final int RIVER_BLACK_LIMIT = 4; // 黑方过河（row > 4）
}

