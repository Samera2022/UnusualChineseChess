package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.model.Move;

public interface AIStrategy {
    String getName();
    Move findBestMove(SimulationContext ctx, int maxDepth, long timeLimitMs);
    AIStrategyConfig getConfig();
    void applyConfig(AIStrategyConfig config);
}
