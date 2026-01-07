package io.github.samera2022.chinese_chess;

import java.util.stream.Stream;

public enum UpdateInfo {
    // 定义条目：版本号 + 发布日期 + 描述
    VERSION_0_1_0("0.1.0", "2026-01-06 23:54",
            "## [Added]\n" +
                    " - 支持进行基本的象棋游玩。"),
    VERSION_0_2_0("0.2.0", "2026-01-07 00:18",
            "## [Added]\n" +
                    " - 美化了象棋棋盘和整体UI布局。"),
    VERSION_1_0_0("1.0.0", "2026-01-07 15:37",
            "## [Added]\n" +
                    " - 添加了完整的残局功能！你现在可以导出和导入残局了！\n" +
                    " - 添加了局域网对战功能！你现在可以邀请不同玩家来一起玩象棋了！\n" +
                    " - 添加“玩法”功能，你可以打破传统中国象棋中的一些规则，体验更加独特的中国象棋了！"),
    VERSION_1_1_0_26m01a("1.1.0-26m01a", "2026-01-07 19:33",
            "## [Added]\n" +
                    " - 添加了更多的神奇玩法！\n" +
                    " - 添加“更新日志”和“关于作者”的按钮。\n" +
                    " - 添加本地翻转棋盘的功能。\n\n" +
                    "## [Warn]\n" +
                    " - 局域网的持方功能存在一点问题，不建议本地主机选择持黑方。\n" +
                    " - “左右联通”功能目前和其他玩法可能存在不适配。放心，这不会造成崩溃，只是有些其他玩法的棋子无法做到左右连通的效果。");
    private final String version;
    private final String releaseDate;
    private final String description;

    // 构造方法
    UpdateInfo(String version, String releaseDate, String description) {
        this.version = version;
        this.releaseDate = releaseDate;
        this.description = description;
    }

    public String getVersion() {return version;}
    public String getReleaseDate() {return releaseDate;}
    public String getDisplayName() {return String.format("[%s] %s",releaseDate,version);}
    public String getDescription() {return description;}

    // 自定义方法：获取格式化日志
    public String getFormattedLog() {
        return String.format("[%s] %s\n\n%s", releaseDate, version, description);
    }

    // 按版本搜索 (静态工具方法)
    public static UpdateInfo findByVersion(String version) {
        return Stream.of(values())
                .filter(log -> log.version.equals(version))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid version"));
    }

    // 新增：获取所有版本号的String数组
    public static String[] getAllVersions() {
        return Stream.of(values()).map(log -> log.version).toArray(String[]::new);
    }

    public static String[] getAllDisplayNames() {
        return Stream.of(values()).map(UpdateInfo::getDisplayName).toArray(String[]::new);
    }

    public static void main(String[] args) {
        System.out.println(VERSION_1_1_0_26m01a.getFormattedLog());
    }
}