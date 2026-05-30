package io.github.samera2022.chinese_chess.common.spi;

public final class MessageProtocol {
    public static final String CMD_CREATE_ROOM     = "create_room";
    public static final String CMD_JOIN_ROOM       = "join_room";
    public static final String CMD_START_MATCH     = "start_matchmaking";
    public static final String CMD_CANCEL_MATCH    = "cancel_matchmaking";
    public static final String CMD_SUBMIT_MOVE     = "submit_move";
    public static final String CMD_REQUEST_UNDO    = "request_undo";
    public static final String CMD_RESIGN          = "resign";
    public static final String CMD_REQUEST_SYNC    = "request_sync";
    public static final String CMD_MATCH_FOUND     = "match_found";
    public static final String CMD_OPPONENT_MOVE   = "opponent_move";
    public static final String CMD_OPPONENT_UNDO   = "opponent_undo";
    public static final String CMD_OPPONENT_RESIGN = "opponent_resigned";
    public static final String CMD_ILLEGAL_MOVE    = "illegal_move";
    public static final String CMD_GAME_OVER       = "game_over";
    public static final String CMD_SYNC_STATE      = "sync_state";
    public static final String CMD_ERROR           = "error";

    private MessageProtocol() {}
}
