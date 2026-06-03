package io.github.samera2022.chinese_chess.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.samera2022.chinese_chess.R;
import io.github.samera2022.chinese_chess.app.viewmodel.GameViewModel;
import io.github.samera2022.chinese_chess.common.GameConfig;
import io.github.samera2022.chinese_chess.common.model.Move;

public class GameActivity extends AppCompatActivity {

    private GameViewModel viewModel;
    private BoardView boardView;
    private InfoSidePanel infoSidePanel;
    private RuleSettingsPanel ruleSettingsPanel;
    private MoveHistoryPanel moveHistoryPanel;
    private TextView gameStatusText;
    private Button btnUndo, btnRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);

        // 读取 LobbyActivity 传递的局域网连接参数
        Intent intent = getIntent();
        String remoteHost = intent.getStringExtra("host");
        int remotePort = intent.getIntExtra("port", 0);
        boolean isHost = intent.getBooleanExtra("isHost", false);
        if (remoteHost != null && remotePort > 0) {
            // 局域网模式：复用桌面端 NetworkSession (Raw TCP)
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    io.github.samera2022.chinese_chess.api.net.NetworkSession session =
                        new io.github.samera2022.chinese_chess.api.net.NetworkSession();
                    if (isHost) {
                        session.startServer(remotePort);
                    } else {
                        session.connect(remoteHost, remotePort);
                    }
                    viewModel.setupNetworkSession(session, isHost);
                } catch (Exception e) {
                    android.util.Log.e("GameActivity", "LAN setup failed", e);
                }
            });
        }

        boardView = findViewById(R.id.board_view);
        infoSidePanel = findViewById(R.id.info_side_panel);
        ruleSettingsPanel = findViewById(R.id.rule_settings_panel);
        moveHistoryPanel = findViewById(R.id.move_history_panel);
        gameStatusText = findViewById(R.id.game_status_text);
        btnUndo = findViewById(R.id.btn_undo);
        btnRestart = findViewById(R.id.btn_restart);

        ruleSettingsPanel.setOnRuleChangeListener((key, value) -> {
            viewModel.getEngine().setRule(key, value);
            // 上下连通开关：重建棋盘（10行 ↔ 18行对称棋盘）
            if (io.github.samera2022.chinese_chess.common.rules.RuleRegistry.TOP_BOTTOM_CONNECTED.registryName.equals(key)) {
                viewModel.getEngine().rebuildBoardForTopBottom();
                // 更新棋盘视图尺寸
                boardView.setBoardDimensions(
                    viewModel.getEngine().getBoardRows(),
                    viewModel.getEngine().getBoardCols()
                );
                // 刷新全部 LiveData 以更新 UI
                viewModel.refreshAll();
            }
        });

        btnUndo.setOnClickListener(v -> viewModel.undoLastMove());
        btnRestart.setOnClickListener(v -> viewModel.restart());

        boardView.setOnCellClickListener(new BoardView.OnCellClickListener() {
            @Override
            public void onCellClick(int row, int col) {
                android.graphics.Point sel = viewModel.getSelectedPosition().getValue();
                if (sel != null) {
                    if (!viewModel.makeMove(sel.x, sel.y, row, col))
                        viewModel.selectPiece(row, col);
                } else {
                    viewModel.selectPiece(row, col);
                }
            }

            @Override
            public void onPieceDrag(int fromRow, int fromCol, int toRow, int toCol) {
                viewModel.makeMove(fromRow, fromCol, toRow, toCol);
            }

            @Override
            public void onPieceLongPress(int row, int col) {
                viewModel.selectPiece(row, col);
            }
        });

        observeLiveData();
        viewModel.initGame((GameConfig) null);
    }

    private void observeLiveData() {
        viewModel.getBoardStateLD().observe(this, state -> {
            if (state != null) {
                boardView.setBoardState(state);
                boardView.setCurrentTurn(state.isRedTurn());
            }
        });

        viewModel.getIsRedTurn().observe(this, isRed -> {
            if (isRed != null) infoSidePanel.setCurrentTurn(isRed);
        });

        viewModel.getGameStatus().observe(this, status -> {
            if (status != null) {
                gameStatusText.setText(formatGameStatus(status));
                infoSidePanel.setGameStatus(status);
            }
        });

        viewModel.getMoveHistory().observe(this, moves -> {
            if (moves != null) {
                moveHistoryPanel.setMoveHistory(moves);
                infoSidePanel.onMoveHistoryChanged(moves);
            }
        });

        viewModel.getSelectedPosition().observe(this, pos -> {
            if (pos != null) boardView.setSelectedPosition(pos.x, pos.y);
            else boardView.clearSelectedPosition();
        });

        viewModel.getValidMoves().observe(this, moves -> {
            boardView.setValidMoves(moves);
        });
    }

    private String formatGameStatus(String status) {
        if (status == null) return "游戏中";
        switch (status) {
            case "RUNNING": return "游戏中";
            case "RED_CHECKMATE": return "红方被将杀！黑方胜";
            case "BLACK_CHECKMATE": return "黑方被将杀！红方胜";
            case "DRAW": return "平局";
            default: return status;
        }
    }
}
