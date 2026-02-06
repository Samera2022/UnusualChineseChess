package io.github.samera2022.chinese_chess.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class AboutDialog extends JDialog{
    private static final String ABOUT_AUTHOR = "    你好，我是UnusualChineseChess的作者Samera2022。首先感谢您的游玩，谨在此致以最为诚挚的欢迎。\n" +
            "    你可以通过B站UID: 583460263 / QQ: 3517085924来找到我来反馈各类使用问题，当然闲聊之类的也是肯定可以的！不过反馈使用问题还是建议在Github提交Issues，这样我能更及时看得到。\n" +
            "    噢，还没有介绍这个项目的Github地址！但是想必聪明的你已经猜出来这个项目应该就是Samera2022/UnusualChineseChess了。没错！本项目的地址为https://github.com/Samera2022/UnusualChineseChess，如果你觉得本游戏比较有趣的话还请不要吝啬你的star啦！\n";
    public AboutDialog(){
        setTitle("关于作者");
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/UnusualChineseChess.png"))).getImage());
        setModal(true);
        setLayout(new BorderLayout(10, 10));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel aboutTitle = new JLabel("关于作者");
        aboutTitle.setFont(aboutTitle.getFont().deriveFont(Font.BOLD, 18f));
        aboutTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(aboutTitle);
        content.add(Box.createVerticalStrut(10));
        content.add(new JSeparator());

        JTextArea aboutArea = new JTextArea(ABOUT_AUTHOR);
        aboutArea.setPreferredSize(new Dimension(350,200));
        aboutArea.setEditable(false);
        aboutArea.setLineWrap(true);
        aboutArea.setWrapStyleWord(true);
        aboutArea.setOpaque(false);
        aboutArea.setBorder(null);
        aboutArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(10));
        content.add(aboutArea);

        // 创建底部面板用于居中显示 GitHub 按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JButton githubButton = new JButton("GitHub");
        githubButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        githubButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI("https://github.com/Samera2022/UnusualChineseChess"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(AboutDialog.this, "浏览器打开失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        bottomPanel.add(githubButton);

        add(content, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        setSize(500,300);
        setLocationRelativeTo(this);
    }
}
