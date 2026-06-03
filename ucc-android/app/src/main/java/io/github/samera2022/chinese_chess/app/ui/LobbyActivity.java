package io.github.samera2022.chinese_chess.app.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class LobbyActivity extends AppCompatActivity {

    private LobbyPanel lobbyPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lobbyPanel = new LobbyPanel(this);
        setContentView(lobbyPanel);

        lobbyPanel.setLobbyCallback(new LobbyPanel.LobbyCallback() {
            @Override
            public void onConnected(String host, int port, boolean isHost) {
                Intent intent = new Intent(LobbyActivity.this, GameActivity.class);
                intent.putExtra("host", host);
                intent.putExtra("port", port);
                intent.putExtra("isHost", isHost);
                startActivity(intent);
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() ->
                    android.widget.Toast.makeText(LobbyActivity.this,
                        "已断开", android.widget.Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                    android.widget.Toast.makeText(LobbyActivity.this,
                        "错误: " + message, android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lobbyPanel != null) lobbyPanel.cleanup();
    }
}
