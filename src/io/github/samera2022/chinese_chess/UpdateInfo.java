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
                    " - “左右连通”功能目前和其他玩法可能存在不适配。放心，这不会造成崩溃，只是有些其他玩法的棋子无法做到左右连通的效果。"),
    VERSION_1_1_0_26m01b("1.1.0-26m01b","2026-01-09 17:42",
            "## [Added]\n" +
                    " - 新增: “堆叠棋子”玩法，你现在可以将己方棋子堆叠到集中的某个位置了！\n\n" +
                    "## [Changed]\n" +
                    " - 大幅修改项目逻辑以降低项目的耦合度，改为采用GameEngine<-GameRulesConfig->MoveValidator进行各项配置的维护。\n" +
                    " - 由于“堆叠棋子”玩法，你现在可能需要使用左键来选择棋子，右键来进行棋子移动了。\n\n" +
                    "## [Fixed]\n" +
                    " - 修复“左右连通”的逻辑，它现在应该能够兼容其他模式了。\n\n" +
                    "## [Warn]\n" +
                    " - 局域网的持方功能目前仍然存在一些问题，不建议本地主机选择持黑方。\n" +
                    " - “堆叠棋子”目前仍然存在一些bug，并未完成。"),
    VERSION_1_1_0("1.1.0","2026-01-09 21:26",
            "## [Added]\n" +
                    " - 添加了更多的神奇玩法！\n" +
                    " - 添加“更新日志”和“关于作者”的按钮。\n" +
                    " - 添加本地翻转棋盘的功能。\n" +
                    " - 新增: “堆叠棋子”玩法(实验性玩法)，你现在可以将己方棋子堆叠到集中的某个位置了！\n\n" +
                    "## [Changed]\n" +
                    " - 大幅修改项目逻辑以降低项目的耦合度，改为采用GameEngine<-GameRulesConfig->MoveValidator进行各项配置的维护。\n" +
                    " - 由于“堆叠棋子”玩法，你现在可能需要使用左键来选择棋子，右键来进行棋子移动了。\n\n" +
                    "## [Fixed]\n" +
                    " - 修复“左右连通”的逻辑，它现在应该能够兼容其他模式了。\n" +
                    " - 修复局域网持方的问题，现在局域网能够正常进行对局了！\n\n" +
                    "## [Warn]\n" +
                    " - “堆叠棋子”目前仍然存在一些bug，并未完成。"),
    VERSION_1_2_0("1.2.0","2026-01-10",
                    "## [Added]\n" +
                    " - 添加“取消对将”的玩法。\n" +
                    " - 添加“死战方休”的玩法。\n\n" +
                    "## [Fixed]\n" +
                    " - 补充“对将”这一“将”的移动方式。\n" +
                    " - 修正“帥”和“兵”在左右连通中的表现。\n" +
                    " - 修复局域网对局中“撤销”按钮，现在其可以在联机中正常使用了。\n" +
                    " - 修复堆叠棋子的移动验证逻辑，现在选择不同堆栈层的棋子会正确使用对应棋子的移动规则。\n" +
                    " - 修复“允许背负上方棋子”选项，现在未勾选时选择下方棋子移动不会带走上方棋子。\n" +
                    " - 修复残局导入导出功能，现在可以正确保存和恢复堆叠棋子的状态。\n" +
                    " - 修复点击对方堆叠棋子时的交互，现在会显示堆叠信息对话框。\n\n" +
                    "## [Warn]\n" +
                    " - “堆叠棋子”功能已基本完善，但可能仍存在一些边界情况的问题。");
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
        System.out.println(VERSION_1_1_0.getFormattedLog());
    }
}