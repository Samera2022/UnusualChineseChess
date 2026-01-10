package io.github.samera2022.chinese_chess.rules;

import com.google.gson.JsonObject;

/**
 * 游戏规则配置 - 统一管理所有游戏规则
 * 该类作为单一数据源，避免规则在多处重复定义
 */
public class GameRulesConfig {
    // 基础玩法规则
    private boolean allowFlyingGeneral = false;      // 允许飞将
    private boolean disableFacingGenerals = false;   // 取消对将（允许王见王）
    private boolean pawnCanRetreat = false;          // 兵卒可以后退
    private boolean noRiverLimit = false;            // 取消过河限制（所有棋子）
    private boolean advisorCanLeave = false;         // 仕可以离开宫
    private boolean internationalKing = false;       // 国际象棋风格的王
    private boolean pawnPromotion = false;           // 兵卒晋升规则
    private boolean allowOwnBaseLine = false;        // 兵到达己方底线可以晋升
    private boolean allowInsideRetreat = false;      // 兵可以在宫内后退
    private boolean internationalAdvisor = false;    // 国际象棋风格的仕
    private boolean allowElephantCrossRiver = false; // 象可以过河
    private boolean allowAdvisorCrossRiver = false;  // 仕可以过河
    private boolean allowKingCrossRiver = false;     // 王可以过河
    private boolean leftRightConnected = false;      // 左右相连（所有棋子）
    private boolean leftRightConnectedHorse = false; // 左右相连（仅马）
    private boolean leftRightConnectedElephant = false; // 左右相连（仅象）

    // 取消卡子规则
    private boolean unblockPiece = false;            // 通用取消卡子
    private boolean unblockHorseLeg = false;         // 马脚可以被跳过
    private boolean unblockElephantEye = false;      // 象眼可以被跳过

    // 特殊规则
    private boolean allowCaptureOwnPiece = false;    // 允许吃自己的棋子
    private boolean allowPieceStacking = false;      // 允许棋子堆叠
    private int maxStackingCount = 2;                // 最大堆叠数量
    private boolean allowCarryPiecesAbove = false;   // 允许背负上方棋子
    private boolean allowCaptureConversion = false;  // 允许俘虏：吃子改为转换归己方
    private boolean deathMatchUntilVictory = false;  // 死战方休：必须吃掉全部棋子

    // UI相关配置
    private boolean allowUndo = true;                // 允许悔棋
    private boolean showHints = true;                // 显示提示

    /**
     * 创建默认配置副本
     */
    public GameRulesConfig copy() {
        GameRulesConfig config = new GameRulesConfig();
        config.allowFlyingGeneral = this.allowFlyingGeneral;
        config.disableFacingGenerals = this.disableFacingGenerals;
        config.pawnCanRetreat = this.pawnCanRetreat;
        config.noRiverLimit = this.noRiverLimit;
        config.advisorCanLeave = this.advisorCanLeave;
        config.internationalKing = this.internationalKing;
        config.pawnPromotion = this.pawnPromotion;
        config.allowOwnBaseLine = this.allowOwnBaseLine;
        config.allowInsideRetreat = this.allowInsideRetreat;
        config.internationalAdvisor = this.internationalAdvisor;
        config.allowElephantCrossRiver = this.allowElephantCrossRiver;
        config.allowAdvisorCrossRiver = this.allowAdvisorCrossRiver;
        config.allowKingCrossRiver = this.allowKingCrossRiver;
        config.leftRightConnected = this.leftRightConnected;
        config.leftRightConnectedHorse = this.leftRightConnectedHorse;
        config.leftRightConnectedElephant = this.leftRightConnectedElephant;
        config.unblockPiece = this.unblockPiece;
        config.unblockHorseLeg = this.unblockHorseLeg;
        config.unblockElephantEye = this.unblockElephantEye;
        config.allowCaptureOwnPiece = this.allowCaptureOwnPiece;
        config.allowPieceStacking = this.allowPieceStacking;
        config.maxStackingCount = this.maxStackingCount;
        config.allowCarryPiecesAbove = this.allowCarryPiecesAbove;
        config.allowCaptureConversion = this.allowCaptureConversion;
        config.deathMatchUntilVictory = this.deathMatchUntilVictory;
        config.allowUndo = this.allowUndo;
        config.showHints = this.showHints;
        return config;
    }

    /**
     * 转换为JsonObject（用于保存/序列化）
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(RuleConstants.ALLOW_FLYING_GENERAL, allowFlyingGeneral);
        json.addProperty(RuleConstants.DISABLE_FACING_GENERALS, disableFacingGenerals);
        json.addProperty(RuleConstants.PAWN_CAN_RETREAT, pawnCanRetreat);
        json.addProperty(RuleConstants.NO_RIVER_LIMIT, noRiverLimit);
        json.addProperty(RuleConstants.ADVISOR_CAN_LEAVE, advisorCanLeave);
        json.addProperty(RuleConstants.INTERNATIONAL_KING, internationalKing);
        json.addProperty(RuleConstants.PAWN_PROMOTION, pawnPromotion);
        json.addProperty(RuleConstants.ALLOW_OWN_BASE_LINE, allowOwnBaseLine);
        json.addProperty(RuleConstants.ALLOW_INSIDE_RETREAT, allowInsideRetreat);
        json.addProperty(RuleConstants.INTERNATIONAL_ADVISOR, internationalAdvisor);
        json.addProperty(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, allowElephantCrossRiver);
        json.addProperty(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, allowAdvisorCrossRiver);
        json.addProperty(RuleConstants.ALLOW_KING_CROSS_RIVER, allowKingCrossRiver);
        json.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED, leftRightConnected);
        json.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, leftRightConnectedHorse);
        json.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, leftRightConnectedElephant);
        json.addProperty(RuleConstants.UNBLOCK_PIECE, unblockPiece);
        json.addProperty(RuleConstants.UNBLOCK_HORSE_LEG, unblockHorseLeg);
        json.addProperty(RuleConstants.UNBLOCK_ELEPHANT_EYE, unblockElephantEye);
        json.addProperty(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, allowCaptureOwnPiece);
        json.addProperty(RuleConstants.ALLOW_PIECE_STACKING, allowPieceStacking);
        json.addProperty(RuleConstants.MAX_STACKING_COUNT, maxStackingCount);
        json.addProperty(RuleConstants.ALLOW_CARRY_PIECES_ABOVE, allowCarryPiecesAbove);
        json.addProperty(RuleConstants.ALLOW_CAPTURE_CONVERSION, allowCaptureConversion);
        json.addProperty(RuleConstants.DEATH_MATCH_UNTIL_VICTORY, deathMatchUntilVictory);
        json.addProperty(RuleConstants.ALLOW_UNDO, allowUndo);
        json.addProperty(RuleConstants.SHOW_HINTS, showHints);
        return json;
    }

    /**
     * 从JsonObject加载配置
     */
    public void loadFromJson(JsonObject json) {
        if (json.has(RuleConstants.ALLOW_FLYING_GENERAL)) allowFlyingGeneral = json.get(RuleConstants.ALLOW_FLYING_GENERAL).getAsBoolean();
        if (json.has(RuleConstants.DISABLE_FACING_GENERALS)) disableFacingGenerals = json.get(RuleConstants.DISABLE_FACING_GENERALS).getAsBoolean();
        if (json.has(RuleConstants.PAWN_CAN_RETREAT)) pawnCanRetreat = json.get(RuleConstants.PAWN_CAN_RETREAT).getAsBoolean();
        if (json.has(RuleConstants.NO_RIVER_LIMIT)) noRiverLimit = json.get(RuleConstants.NO_RIVER_LIMIT).getAsBoolean();
        if (json.has(RuleConstants.ADVISOR_CAN_LEAVE)) advisorCanLeave = json.get(RuleConstants.ADVISOR_CAN_LEAVE).getAsBoolean();
        if (json.has(RuleConstants.INTERNATIONAL_KING)) internationalKing = json.get(RuleConstants.INTERNATIONAL_KING).getAsBoolean();
        if (json.has(RuleConstants.PAWN_PROMOTION)) pawnPromotion = json.get(RuleConstants.PAWN_PROMOTION).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_OWN_BASE_LINE)) allowOwnBaseLine = json.get(RuleConstants.ALLOW_OWN_BASE_LINE).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_INSIDE_RETREAT)) allowInsideRetreat = json.get(RuleConstants.ALLOW_INSIDE_RETREAT).getAsBoolean();
        if (json.has(RuleConstants.INTERNATIONAL_ADVISOR)) internationalAdvisor = json.get(RuleConstants.INTERNATIONAL_ADVISOR).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER)) allowElephantCrossRiver = json.get(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER)) allowAdvisorCrossRiver = json.get(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_KING_CROSS_RIVER)) allowKingCrossRiver = json.get(RuleConstants.ALLOW_KING_CROSS_RIVER).getAsBoolean();
        if (json.has(RuleConstants.LEFT_RIGHT_CONNECTED)) leftRightConnected = json.get(RuleConstants.LEFT_RIGHT_CONNECTED).getAsBoolean();
        if (json.has(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE)) leftRightConnectedHorse = json.get(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE).getAsBoolean();
        if (json.has(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT)) leftRightConnectedElephant = json.get(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT).getAsBoolean();
        if (json.has(RuleConstants.UNBLOCK_PIECE)) unblockPiece = json.get(RuleConstants.UNBLOCK_PIECE).getAsBoolean();
        if (json.has(RuleConstants.UNBLOCK_HORSE_LEG)) unblockHorseLeg = json.get(RuleConstants.UNBLOCK_HORSE_LEG).getAsBoolean();
        if (json.has(RuleConstants.UNBLOCK_ELEPHANT_EYE)) unblockElephantEye = json.get(RuleConstants.UNBLOCK_ELEPHANT_EYE).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_CAPTURE_OWN_PIECE)) allowCaptureOwnPiece = json.get(RuleConstants.ALLOW_CAPTURE_OWN_PIECE).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_PIECE_STACKING)) allowPieceStacking = json.get(RuleConstants.ALLOW_PIECE_STACKING).getAsBoolean();
        if (json.has(RuleConstants.MAX_STACKING_COUNT)) maxStackingCount = json.get(RuleConstants.MAX_STACKING_COUNT).getAsInt();
        if (json.has(RuleConstants.ALLOW_CARRY_PIECES_ABOVE)) allowCarryPiecesAbove = json.get(RuleConstants.ALLOW_CARRY_PIECES_ABOVE).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_CAPTURE_CONVERSION)) allowCaptureConversion = json.get(RuleConstants.ALLOW_CAPTURE_CONVERSION).getAsBoolean();
        if (json.has(RuleConstants.DEATH_MATCH_UNTIL_VICTORY)) deathMatchUntilVictory = json.get(RuleConstants.DEATH_MATCH_UNTIL_VICTORY).getAsBoolean();
        if (json.has(RuleConstants.ALLOW_UNDO)) allowUndo = json.get(RuleConstants.ALLOW_UNDO).getAsBoolean();
        if (json.has(RuleConstants.SHOW_HINTS)) showHints = json.get(RuleConstants.SHOW_HINTS).getAsBoolean();
    }

    // ========== Getters ==========
    public boolean isAllowFlyingGeneral() { return allowFlyingGeneral; }
    public boolean isDisableFacingGenerals() { return disableFacingGenerals; }
    public boolean isPawnCanRetreat() { return pawnCanRetreat; }
    public boolean isNoRiverLimit() { return noRiverLimit; }
    public boolean isAdvisorCanLeave() { return advisorCanLeave; }
    public boolean isInternationalKing() { return internationalKing; }
    public boolean isPawnPromotion() { return pawnPromotion; }
    public boolean isAllowOwnBaseLine() { return allowOwnBaseLine; }
    public boolean isAllowInsideRetreat() { return allowInsideRetreat; }
    public boolean isInternationalAdvisor() { return internationalAdvisor; }
    public boolean isAllowElephantCrossRiver() { return allowElephantCrossRiver; }
    public boolean isAllowAdvisorCrossRiver() { return allowAdvisorCrossRiver; }
    public boolean isAllowKingCrossRiver() { return allowKingCrossRiver; }
    public boolean isLeftRightConnected() { return leftRightConnected; }
    public boolean isLeftRightConnectedHorse() { return leftRightConnectedHorse; }
    public boolean isLeftRightConnectedElephant() { return leftRightConnectedElephant; }
    public boolean isUnblockPiece() { return unblockPiece; }
    public boolean isUnblockHorseLeg() { return unblockHorseLeg; }
    public boolean isUnblockElephantEye() { return unblockElephantEye; }
    public boolean isAllowCaptureOwnPiece() { return allowCaptureOwnPiece; }
    public boolean isAllowPieceStacking() { return allowPieceStacking; }
    public int getMaxStackingCount() { return maxStackingCount; }
    public boolean isAllowCarryPiecesAbove() { return allowCarryPiecesAbove; }
    public boolean isDeathMatchUntilVictory() { return deathMatchUntilVictory; }
    public boolean isAllowUndo() { return allowUndo; }
    public boolean isShowHints() { return showHints; }
    public boolean isAllowCaptureConversion() { return allowCaptureConversion; }

    // ========== Setters ==========
    public void setAllowFlyingGeneral(boolean value) { this.allowFlyingGeneral = value; }
    public void setDisableFacingGenerals(boolean value) { this.disableFacingGenerals = value; }
    public void setPawnCanRetreat(boolean value) { this.pawnCanRetreat = value; }
    public void setNoRiverLimit(boolean value) { this.noRiverLimit = value; }
    public void setAdvisorCanLeave(boolean value) { this.advisorCanLeave = value; }
    public void setInternationalKing(boolean value) { this.internationalKing = value; }
    public void setPawnPromotion(boolean value) { this.pawnPromotion = value; }
    public void setAllowOwnBaseLine(boolean value) { this.allowOwnBaseLine = value; }
    public void setAllowInsideRetreat(boolean value) { this.allowInsideRetreat = value; }
    public void setInternationalAdvisor(boolean value) { this.internationalAdvisor = value; }
    public void setAllowElephantCrossRiver(boolean value) { this.allowElephantCrossRiver = value; }
    public void setAllowAdvisorCrossRiver(boolean value) { this.allowAdvisorCrossRiver = value; }
    public void setAllowKingCrossRiver(boolean value) { this.allowKingCrossRiver = value; }
    public void setLeftRightConnected(boolean value) { this.leftRightConnected = value; }
    public void setLeftRightConnectedHorse(boolean value) { this.leftRightConnectedHorse = value; }
    public void setLeftRightConnectedElephant(boolean value) { this.leftRightConnectedElephant = value; }
    public void setUnblockPiece(boolean value) { this.unblockPiece = value; }
    public void setUnblockHorseLeg(boolean value) { this.unblockHorseLeg = value; }
    public void setUnblockElephantEye(boolean value) { this.unblockElephantEye = value; }
    public void setAllowCaptureOwnPiece(boolean value) { this.allowCaptureOwnPiece = value; }
    public void setAllowPieceStacking(boolean value) { this.allowPieceStacking = value; }
    public void setMaxStackingCount(int value) { this.maxStackingCount = Math.max(1, value); }
    public void setAllowCarryPiecesAbove(boolean value) { this.allowCarryPiecesAbove = value; }
    public void setDeathMatchUntilVictory(boolean value) { this.deathMatchUntilVictory = value; }
    public void setAllowUndo(boolean value) { this.allowUndo = value; }
    public void setShowHints(boolean value) { this.showHints = value; }
    public void setAllowCaptureConversion(boolean value) { this.allowCaptureConversion = value; }

    // ========== 通用动态访问方法 ==========

    /**
     * 根据规则常量获取规则值
     * @param ruleName 规则常量（来自RuleConstants）
     * @return 规则值，可能是Boolean或Integer
     */
    public Object get(String ruleName) {
        switch (ruleName) {
            case RuleConstants.ALLOW_FLYING_GENERAL: return allowFlyingGeneral;
            case RuleConstants.DISABLE_FACING_GENERALS: return disableFacingGenerals;
            case RuleConstants.PAWN_CAN_RETREAT: return pawnCanRetreat;
            case RuleConstants.NO_RIVER_LIMIT: return noRiverLimit;
            case RuleConstants.ADVISOR_CAN_LEAVE: return advisorCanLeave;
            case RuleConstants.INTERNATIONAL_KING: return internationalKing;
            case RuleConstants.PAWN_PROMOTION: return pawnPromotion;
            case RuleConstants.ALLOW_OWN_BASE_LINE: return allowOwnBaseLine;
            case RuleConstants.ALLOW_INSIDE_RETREAT: return allowInsideRetreat;
            case RuleConstants.INTERNATIONAL_ADVISOR: return internationalAdvisor;
            case RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER: return allowElephantCrossRiver;
            case RuleConstants.ALLOW_ADVISOR_CROSS_RIVER: return allowAdvisorCrossRiver;
            case RuleConstants.ALLOW_KING_CROSS_RIVER: return allowKingCrossRiver;
            case RuleConstants.LEFT_RIGHT_CONNECTED: return leftRightConnected;
            case RuleConstants.LEFT_RIGHT_CONNECTED_HORSE: return leftRightConnectedHorse;
            case RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT: return leftRightConnectedElephant;
            case RuleConstants.UNBLOCK_PIECE: return unblockPiece;
            case RuleConstants.UNBLOCK_HORSE_LEG: return unblockHorseLeg;
            case RuleConstants.UNBLOCK_ELEPHANT_EYE: return unblockElephantEye;
            case RuleConstants.ALLOW_CAPTURE_OWN_PIECE: return allowCaptureOwnPiece;
            case RuleConstants.ALLOW_PIECE_STACKING: return allowPieceStacking;
            case RuleConstants.MAX_STACKING_COUNT: return maxStackingCount;
            case RuleConstants.ALLOW_CARRY_PIECES_ABOVE: return allowCarryPiecesAbove;
            case RuleConstants.ALLOW_CAPTURE_CONVERSION: return allowCaptureConversion;
            case RuleConstants.DEATH_MATCH_UNTIL_VICTORY: return deathMatchUntilVictory;
            case RuleConstants.ALLOW_UNDO: return allowUndo;
            case RuleConstants.SHOW_HINTS: return showHints;
            default: return null;
        }
    }

    /**
     * 根据规则常量设置规则值
     * @param ruleName 规则常量（来自RuleConstants）
     * @param value 规则值（Boolean或Integer）
     */
    public void set(String ruleName, Object value) {
        if (value instanceof Boolean) {
            boolean boolValue = (Boolean) value;
            switch (ruleName) {
                case RuleConstants.ALLOW_FLYING_GENERAL: allowFlyingGeneral = boolValue; break;
                case RuleConstants.DISABLE_FACING_GENERALS: disableFacingGenerals = boolValue; break;
                case RuleConstants.PAWN_CAN_RETREAT: pawnCanRetreat = boolValue; break;
                case RuleConstants.NO_RIVER_LIMIT: noRiverLimit = boolValue; break;
                case RuleConstants.ADVISOR_CAN_LEAVE: advisorCanLeave = boolValue; break;
                case RuleConstants.INTERNATIONAL_KING: internationalKing = boolValue; break;
                case RuleConstants.PAWN_PROMOTION: pawnPromotion = boolValue; break;
                case RuleConstants.ALLOW_OWN_BASE_LINE: allowOwnBaseLine = boolValue; break;
                case RuleConstants.ALLOW_INSIDE_RETREAT: allowInsideRetreat = boolValue; break;
                case RuleConstants.INTERNATIONAL_ADVISOR: internationalAdvisor = boolValue; break;
                case RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER: allowElephantCrossRiver = boolValue; break;
                case RuleConstants.ALLOW_ADVISOR_CROSS_RIVER: allowAdvisorCrossRiver = boolValue; break;
                case RuleConstants.ALLOW_KING_CROSS_RIVER: allowKingCrossRiver = boolValue; break;
                case RuleConstants.LEFT_RIGHT_CONNECTED: leftRightConnected = boolValue; break;
                case RuleConstants.LEFT_RIGHT_CONNECTED_HORSE: leftRightConnectedHorse = boolValue; break;
                case RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT: leftRightConnectedElephant = boolValue; break;
                case RuleConstants.UNBLOCK_PIECE: unblockPiece = boolValue; break;
                case RuleConstants.UNBLOCK_HORSE_LEG: unblockHorseLeg = boolValue; break;
                case RuleConstants.UNBLOCK_ELEPHANT_EYE: unblockElephantEye = boolValue; break;
                case RuleConstants.ALLOW_CAPTURE_OWN_PIECE: allowCaptureOwnPiece = boolValue; break;
                case RuleConstants.ALLOW_PIECE_STACKING: allowPieceStacking = boolValue; break;
                case RuleConstants.ALLOW_CARRY_PIECES_ABOVE: allowCarryPiecesAbove = boolValue; break;
                case RuleConstants.ALLOW_UNDO: allowUndo = boolValue; break;
                case RuleConstants.SHOW_HINTS: showHints = boolValue; break;
                case RuleConstants.ALLOW_CAPTURE_CONVERSION: allowCaptureConversion = boolValue; break;
                case RuleConstants.DEATH_MATCH_UNTIL_VICTORY: deathMatchUntilVictory = boolValue; break;
            }
        } else if (value instanceof Integer) {
            int intValue = (Integer) value;
            switch (ruleName) {
                case RuleConstants.MAX_STACKING_COUNT: maxStackingCount = Math.max(1, intValue); break;
            }
        }
    }

    /**
     * 根据规则常量获取布尔值规则
     * @param ruleName 规则常量
     * @return 布尔值，如果规则不存在或不是布尔值则返回false
     */
    public boolean getBoolean(String ruleName) {
        Object value = get(ruleName);
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * 根据规则常量获取整数值规则
     * @param ruleName 规则常量
     * @return 整数值，如果规则不存在或不是整数值则返回0
     */
    public int getInt(String ruleName) {
        Object value = get(ruleName);
        return value instanceof Integer ? (Integer) value : 0;
    }
}

