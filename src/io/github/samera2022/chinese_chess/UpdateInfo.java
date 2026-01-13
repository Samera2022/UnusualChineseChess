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
    VERSION_1_2_0_26m01c("1.2.0-26m01c","2026-01-10 15:11",
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
                    " - 修复点击对方堆叠棋子时的交互，现在会显示堆叠信息对话框。\n\n"),
    VERSION_1_2_0("1.2.0","2026-01-10 19:25",
                    "## [Added]\n" +
                    " - 添加“取消对将”的玩法。\n" +
                    " - 添加“死战方休”的玩法。\n" +
                    " - 统一并强化玩法配置管理：全面使用 GameRulesConfig 作为规则单一数据源，减少了模块间耦合。\n" +
                    " - 运行时差分同步：主机修改玩法设置时仅发送发生改动的字段（diff），并在客户端合并应用。\n" +
                    " - 在 UI 端对设置变更做 200ms 去抖合并，减少网络抖动。\n" +
                    " - 增加规则变更监听（支持变更来源），通知执行带超时（500ms）与错误日志，提升稳定性与可调试性。\n\n" +
                    "## [Changed]\n" +
                    " - 移除了大量对 GameEngine 的冗余包装 getter，调用方改为直接访问 GameRulesConfig 的 getBoolean/getInt/toJson 接口。\n" +
                    " - RuleSettingsPanel 的绑定逻辑改为直接读写 GameRulesConfig，并在本地 UI 变更时标记变更来源为 UI。\n" +
                    " - GameEngine.shutdown() 集成了规则通知器的关闭（集中化资源清理）。\n" +
                    " - 精简版本与更新信息的调用：将对 UpdateInfo 的全限定调用替换为直接导入后使用，简化了网络握手与 UI 显示逻辑的代码可读性。\n\n" +
                    "## [Fixed]\n" +
                    " - 补充“对将”这一“将”的移动方式。\n" +
                    " - 修正“帥”和“兵”在左右连通中的表现。\n" +
                    " - 修复局域网对局中“撤销”按钮，现在其可以在联机中正常使用了。\n" +
                    " - 修复堆叠棋子的移动验证逻辑，现在选择不同堆栈层的棋子会正确使用对应棋子的移动规则。\n" +
                    " - 修复“允许背负上方棋子”选项，现在未勾选时选择下方棋子移动不会带走上方棋子。\n" +
                    " - 修复残局导入导出功能，现在可以正确保存和恢复堆叠棋子的状态。\n" +
                    " - 修复点击对方堆叠棋子时的交互，现在会显示堆叠信息对话框。\n" +
                    " - 修复/缓解了规则通知过程中异常被吞掉或阻塞的问题（现在会记录关键错误并对超时 listener 进行取消）。\n" +
                    " - 修复联机设置同步与撤销相关的问题，主机端现在会发送设置快照/差分并避免回环。"),
    VERSION_1_3_0("1.3.0","2026-01-13 00:51",
                    "## [Added]\n" +
                    " - 添加断网重连支持。当其中一方掉线后，其按照地址重新加入即可继续原先的对局！\n" +
                    " - 增加主机切换持方的支持。在对局过程中，主机方可以任意修改自己和对方的持方了！\n" +
                    " - 增加“强制走子”和联机时的“规则改变”到“着法记录”中。现在上述的两个内容将会在客户端和服务端的“着法记录”中显示，并可正常导出到残局中。\n\n" +
                    "## [Fixed]\n" +
                    " - 修复联机与本地环境下鼠标左键对于双方“堆叠棋子”的行为。");
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

    // 当前最新版本（枚举中最后一个条目）
    public static String getLatestVersion() {
        UpdateInfo[] all = values();
        if (all.length == 0) return "0.0.0";
        return all[all.length - 1].getVersion();
    }

    /**
     * Compare two semantic-like version strings with optional pre-release suffix (dash).
     * Returns positive if a > b, negative if a < b, 0 if equal.
     * Pre-release is considered lower than the release (so "1.2.0" > "1.2.0-rc1").
     */
    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        try {
            String[] pa = a.split("-", 2);
            String[] pb = b.split("-", 2);
            String coreA = pa[0];
            String coreB = pb[0];
            String preA = pa.length > 1 ? pa[1] : null;
            String preB = pb.length > 1 ? pb[1] : null;

            String[] ca = coreA.split("\\.");
            String[] cb = coreB.split("\\.");
            for (int i = 0; i < Math.max(ca.length, cb.length); i++) {
                int va = i < ca.length ? Integer.parseInt(ca[i]) : 0;
                int vb = i < cb.length ? Integer.parseInt(cb[i]) : 0;
                if (va != vb) return Integer.compare(va, vb);
            }
            // cores equal; handle pre-release: absence of pre-release means greater (release > prerelease)
            if (preA == null && preB == null) return 0;
            if (preA == null) return 1;
            if (preB == null) return -1;
            return preA.compareTo(preB);
        } catch (Exception ex) {
            // fallback to string compare
            return a.compareTo(b);
        }
    }

    public static boolean isNewer(String a, String b) {
        return compareVersions(a, b) > 0;
    }

    public static void main(String[] args) {
        System.out.println(VERSION_1_3_0.getFormattedLog());
    }
}