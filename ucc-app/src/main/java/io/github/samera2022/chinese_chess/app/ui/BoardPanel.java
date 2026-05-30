package io.github.samera2022.chinese_chess.app.ui;

import io.github.samera2022.chinese_chess.common.GameConfig;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BoardPanel extends JPanel {
    private final GameSession session;

    // 依赖注入：替代向上穿透 getNetController()
    private final BooleanSupplier isNetSessionActive;
    private final Consumer<JsonObject> boardSetupSender;
    private final Supplier<Boolean> localControlsRedSupplier;

    public enum ViewMode { GLOBAL, SELF, OPPONENT }
    private ViewMode viewMode = ViewMode.GLOBAL;
    public void setViewMode(ViewMode m) { this.viewMode = m; repaint(); }
    public ViewMode getViewMode() { return viewMode; }

    private int cellSize = GameConfig.CELL_SIZE;
    private int selectedRow = -1, sdr = -1;
    public void setCellSizeForRows(int rows) { this.cellSize = (rows <= 10) ? GameConfig.CELL_SIZE : 28; repaint(); }
    private int selectedCol = -1;
    private int selectedStackIndex = -1;
    private java.util.List<Point> validMoves = new java.util.ArrayList<>();
    private int forceMoveFromRow = -1, forceMoveFromCol = -1, forceMoveToRow = -1, forceMoveToCol = -1;
    private boolean boardSetupMode = false;
    private boolean style = true;
    private Boolean localControlsRed = null;
    private boolean boardFlipped = false;

    public interface LocalMoveListener { void onLocalMove(int fromRow, int fromCol, int toRow, int toCol); }
    private LocalMoveListener localMoveListener;
    public void setLocalMoveListener(LocalMoveListener listener) { this.localMoveListener = listener; }
    private int offsetX = 0, offsetY = 0;

    private static final Color BOARD_COLOR = new Color(230, 180, 80);
    private static final Color GRID_COLOR = new Color(0, 0, 0);
    private static final Color VALID_MOVE_COLOR = new Color(0, 255, 0);
    private static final Color RED_PIECE_COLOR = new Color(200, 0, 0);
    private static final Color BLACK_PIECE_COLOR = new Color(50, 50, 50);

    /**
     * @param session                 游戏会话
     * @param isNetSessionActive     判断网络会话是否活跃
     * @param boardSetupSender       发送棋盘布置消息（接受 JsonObject）
     * @param localControlsRedSupplier 网络模式下本地持方（红方为 true，null 表示非网络模式）
     */
    public BoardPanel(GameSession session,
                      BooleanSupplier isNetSessionActive,
                      Consumer<JsonObject> boardSetupSender,
                      Supplier<Boolean> localControlsRedSupplier) {
        this.session = session;
        this.isNetSessionActive = isNetSessionActive != null ? isNetSessionActive : () -> false;
        this.boardSetupSender = boardSetupSender != null ? boardSetupSender : (json) -> {};
        this.localControlsRedSupplier = localControlsRedSupplier != null ? localControlsRedSupplier : () -> localControlsRed;
        setBackground(BOARD_COLOR);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (boardSetupMode) { handleSetupModeClick(e); } else {
                    if (e.getButton() == MouseEvent.BUTTON1) handleLeftClick(e);
                    else if (e.getButton() == MouseEvent.BUTTON3) handleRightClick(e);
                    else if (e.getButton() == MouseEvent.BUTTON2) { if (session.getRuleBoolean(RuleRegistry.ALLOW_FORCE_MOVE.registryName)) handleMiddleClick(e); }
                }
            }
        });
    }

    public void setLocalControlsRed(Boolean v) { this.localControlsRed = v; repaint(); }
    public Boolean getLocalControlsRed() { return localControlsRed; }
    public void setBoardFlipped(boolean v) { this.boardFlipped = v; repaint(); }
    public boolean isBoardFlipped() { return boardFlipped; }
    public void setForceMoveIndicatorStyle(boolean s) { this.style = s; repaint(); }
    public void setForceMoveIndicator(int fr,int fc,int tr,int tc) { forceMoveFromRow=fr;forceMoveFromCol=fc;forceMoveToRow=tr;forceMoveToCol=tc;repaint(); }
    public void clearForceMoveIndicator() { forceMoveFromRow=-1;forceMoveFromCol=-1;forceMoveToRow=-1;forceMoveToCol=-1;repaint(); }
    public void setRemotePieceHighlight(int r,int c) { selectedRow=r;selectedCol=c;sdr=-1;repaint(); }
    public void clearRemotePieceHighlight() { selectedRow=-1;selectedCol=-1;sdr=-1;repaint(); }
    public void clearSelection() { selectedRow=-1;selectedCol=-1;sdr=-1;selectedStackIndex=-1;validMoves.clear();forceMoveFromRow=-1;forceMoveFromCol=-1;forceMoveToRow=-1;forceMoveToCol=-1;repaint(); }

    private ReadonlyBoard board() { return session.getBoard(); }
    private boolean tb(){return session.getRuleBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);}

    private int[] displayToLogic(int dr,int dc) {
        ReadonlyBoard b=board(); int tr=b.getRows();
        if(tb()&&viewMode!=ViewMode.GLOBAL) { int[] fr=buildFocusedRows(b); if(dr>=0&&dr<fr.length) return new int[]{fr[dr],dc}; else return new int[]{dr,dc}; }
        return boardFlipped?new int[]{tr-1-dr,dc}:new int[]{dr,dc};
    }
    private int[] logicToDisplay(int lr,int lc) {
        ReadonlyBoard b=board(); int tr=b.getRows();
        if(tb()&&viewMode!=ViewMode.GLOBAL) { int[] fr=buildFocusedRows(b); for(int i=0;i<fr.length;i++) if(fr[i]==lr) return new int[]{i,lc}; return new int[]{lr,lc}; }
        return boardFlipped?new int[]{tr-1-lr,lc}:new int[]{lr,lc};
    }

    // ==================== Interaction ====================
    private void handleLeftClick(MouseEvent e) {
        ReadonlyBoard board=board(); int dc=Math.round((float)(e.getX()-offsetX)/cellSize),dr=Math.round((float)(e.getY()-offsetY)/cellSize);
        int[] logic=displayToLogic(dr,dc); int row=logic[0],col=logic[1];
        if(!board.isValid(row,col)) return;
        if(row==selectedRow&&col==selectedCol&&dr==sdr){selectedRow=-1;selectedCol=-1;sdr=-1;selectedStackIndex=-1;validMoves.clear();repaint();return;}
        Piece piece=board.getPiece(row,col);
        Boolean lcr = localControlsRedSupplier.get();
        if(session.getRuleBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)&&isStackedPiece(piece))
            if(piece.isRed()==session.isRedTurn())
                if(isNetSessionActive.getAsBoolean())
                    if(session.isRedTurn()==lcr) showStackSelectionForSourceDialog(row,col);
                    else showStackInfoDialog(row,col);
                else showStackSelectionForSourceDialog(row,col);
            else showStackInfoDialog(row,col);
        else { if(!canSelectPieceForMove(piece)) return; else {selectedRow=row;selectedCol=col;sdr=dr;selectedStackIndex=-1;calculateValidMoves();} } repaint();
    }
    private boolean canSelectPieceForMove(Piece piece) {
        if(piece==null)return false;
        if(!isNetSessionActive.getAsBoolean())return piece.isRed()==session.isRedTurn();
        Boolean lcr = localControlsRedSupplier.get();
        if(lcr==null)return false;
        return lcr==session.isRedTurn()&&lcr==piece.isRed();
    }

    private void handleRightClick(MouseEvent e) {
        ReadonlyBoard board=board();int dc=Math.round((float)(e.getX()-offsetX)/cellSize),dr=Math.round((float)(e.getY()-offsetY)/cellSize);
        int[] logic=displayToLogic(dr,dc);int toRow=logic[0],toCol=logic[1];
        if(!board.isValid(toRow,toCol)||selectedRow==-1||selectedCol==-1)return; int fromR=selectedRow,fromC=selectedCol;
        Piece tp=board.getPiece(toRow,toCol),sp=selectedStackIndex>=0?board.getStack(fromR,fromC).get(selectedStackIndex):board.getPiece(fromR,fromC);
        if(sp!=null&&tp!=null&&tp.isRed()==session.isRedTurn()&&tp.isRed()==sp.isRed()){
            if(session.getRuleBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)&&session.getRuleInt(RuleRegistry.MAX_STACKING_COUNT.registryName)>1){
                if(session.makeMove(fromR,fromC,toRow,toCol,null,selectedStackIndex)){if(localMoveListener!=null)localMoveListener.onLocalMove(fromR,fromC,toRow,toCol);selectedRow=-1;selectedCol=-1;sdr=-1;selectedStackIndex=-1;validMoves.clear();}repaint();return;}}
        Piece mp=selectedStackIndex>=0?board.getStack(fromR,fromC).get(selectedStackIndex):board.getPiece(fromR,fromC); Piece.Type pt=null;
        if(mp!=null&&session.getRuleBoolean(RuleRegistry.PAWN_PROMOTION.registryName)){
            boolean isSol=mp.getType()==Piece.Type.RED_SOLDIER||mp.getType()==Piece.Type.BLACK_SOLDIER; int H=board.getRows();
            int oppoRow, ownRow;
            if(tb()){ oppoRow=mp.isRed()?4:13; ownRow=mp.isRed()?13:4; }
            else{ oppoRow=mp.isRed()?(H-1):0; ownRow=mp.isRed()?0:(H-1); }
            boolean oppo=toRow==oppoRow, own=toRow==ownRow;
            if(isSol&&(oppo||(own&&session.getRuleBoolean(RuleRegistry.ALLOW_OWN_BASE_LINE.registryName)))){
                if(session.isValidMove(fromR,fromC,toRow,toCol,selectedStackIndex)){pt=showPromotionDialog(mp.isRed());if(pt==null){repaint();return;}}}}
        if(session.makeMove(fromR,fromC,toRow,toCol,pt,selectedStackIndex)){if(localMoveListener!=null)localMoveListener.onLocalMove(fromR,fromC,toRow,toCol);selectedRow=-1;selectedCol=-1;sdr=-1;selectedStackIndex=-1;validMoves.clear();}repaint();
    }

    private void handleMiddleClick(MouseEvent e) {
        Boolean lcr = localControlsRedSupplier.get();
        boolean r=isNetSessionActive.getAsBoolean(),oc=!r; if(r&&lcr!=null&&session.isRedTurn()!=lcr)return;
        ReadonlyBoard board=board(); int dc=Math.round((float)(e.getX()-offsetX)/cellSize),dr=Math.round((float)(e.getY()-offsetY)/cellSize);
        int[] logic=displayToLogic(dr,dc);int toRow=logic[0],toCol=logic[1];
        if(!board.isValid(toRow,toCol)||selectedRow==-1||selectedCol==-1)return;
        Piece src=selectedStackIndex>=0?board.getStack(selectedRow,selectedCol).get(selectedStackIndex):board.getPiece(selectedRow,selectedCol);
        if(r&&(src==null||src.isRed()!=session.isRedTurn()||(lcr!=null&&src.isRed()!=lcr)))return;
        if(oc&&(src==null||src.isRed()!=session.isRedTurn()))return; if(src==null)return;
        setForceMoveIndicator(selectedRow,selectedCol,toRow,toCol);
        int a=JOptionPane.showConfirmDialog(this,"是否进行强制走子？","强制走子确认",JOptionPane.YES_NO_OPTION);
        if(a==JOptionPane.YES_OPTION&&forceMoveRequestListener!=null)forceMoveRequestListener.onForceMoveRequest(selectedRow,selectedCol,toRow,toCol);
        else clearForceMoveIndicator(); repaint();
    }
    public interface ForceMoveRequestListener { void onForceMoveRequest(int fr,int fc,int tr,int tc); }
    private ForceMoveRequestListener forceMoveRequestListener;
    public void setForceMoveRequestListener(ForceMoveRequestListener l) { this.forceMoveRequestListener=l; }

    private Piece.Type showPromotionDialog(boolean isRed) {
        Piece.Type[] types=isRed?new Piece.Type[]{Piece.Type.RED_CHARIOT,Piece.Type.RED_HORSE,Piece.Type.RED_CANNON,Piece.Type.RED_ELEPHANT,Piece.Type.RED_ADVISOR}:new Piece.Type[]{Piece.Type.BLACK_CHARIOT,Piece.Type.BLACK_HORSE,Piece.Type.BLACK_CANNON,Piece.Type.BLACK_ELEPHANT,Piece.Type.BLACK_ADVISOR};
        String[] o=new String[types.length]; for(int i=0;i<types.length;i++)o[i]=types[i].getChineseName();
        int ch=JOptionPane.showOptionDialog(this,"选择晋升的棋子：","兵卒晋升",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,o,o[0]);
        return(ch>=0&&ch<types.length)?types[ch]:null;
    }

    private void showStackSelectionForSourceDialog(int r,int c){}
    private void showStackInfoDialog(int r,int c){}
    public boolean isStackedPiece(Piece p){return p!=null&&board().getStack(p.getRow(),p.getCol()).size()>1;}
    private void calculateValidMoves(){validMoves.clear();ReadonlyBoard b=board();for(int r=0;r<b.getRows();r++)for(int c=0;c<b.getCols();c++)if(session.isValidMove(selectedRow,selectedCol,r,c,selectedStackIndex))validMoves.add(new Point(r,c));}

    // ==================== Rendering ====================
    private Font riverFont(){return new Font("LiSu",Font.BOLD,(int)Math.max(14,cellSize/1.5f));}

    /** 19行环绕排列：己方帅/将在中间。联机时根据 localControlsRed 确定己方颜色 */
    private int[] buildFocusedRows(ReadonlyBoard b) {
        Boolean lcr = localControlsRedSupplier.get();
        boolean selfIsRed = (lcr != null)
            ? (viewMode == ViewMode.SELF ? lcr : !lcr)
            : (viewMode == ViewMode.SELF);
        int oppR = selfIsRed ? 4 : 13; // 对手将/帅所在行
        int tr = b.getRows();
        return new int[]{
            oppR,(oppR+1)%tr,(oppR+2)%tr,(oppR+3)%tr,(oppR+4)%tr,
            (oppR+5)%tr,(oppR+6)%tr,(oppR+7)%tr,(oppR+8)%tr,(oppR+9)%tr,
            (oppR+10)%tr,(oppR+11)%tr,(oppR+12)%tr,(oppR+13)%tr,
            (oppR+14)%tr,(oppR+15)%tr,(oppR+16)%tr,(oppR+17)%tr, oppR
        };
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g); Graphics2D g2d=(Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        ReadonlyBoard b=board();
        if(!tb()||viewMode==ViewMode.GLOBAL) drawGlobal(g2d,b);
        else drawFocused(g2d,b);
    }

    private void drawGlobal(Graphics2D g2d,ReadonlyBoard b){
        int rows=b.getRows(),cols=b.getCols(),pr=cellSize/2-2,bw=(cols-1)*cellSize,bh=(rows-1)*cellSize;
        boolean tb=tb(); int yPad=tb?cellSize:0;
        offsetX=pr+(getWidth()-(bw+2*pr))/2; offsetY=yPad+pr+(getHeight()-(yPad*2+bh+2*pr))/2;
        offsetX=Math.max(pr,offsetX); offsetY=Math.max(pr+yPad,offsetY);
        g2d.translate(offsetX,offsetY);
        drawBoardGrid(g2d,b);
        drawPalaceGlobal(g2d);
        drawPiecesGlobal(g2d,b);
        drawMoveIndicators(g2d,b);
    }

    private void drawFocused(Graphics2D g2d,ReadonlyBoard b){
        int[] fr = buildFocusedRows(b);
        int drs = fr.length, cols = b.getCols(), pr = cellSize/2-2;
        int bw = (cols-1)*cellSize, bh = (drs-1)*cellSize;
        offsetX = pr + (getWidth() - (bw + 2*pr))/2;
        offsetY = pr + (getHeight() - (bh + 2*pr))/2;
        offsetX = Math.max(pr, offsetX); offsetY = Math.max(pr, offsetY);
        g2d.translate(offsetX, offsetY);

        int rv1 = 4, rv2 = 5, rv3 = 13, rv4 = 14; // 河界显示行索引

        // 左右边框
        g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(0,0,0,bh); g2d.drawLine(bw,0,bw,bh);

        // 横线：河界行用±2双线
        g2d.setColor(GRID_COLOR); g2d.setStroke(new BasicStroke(1));
        for (int dr = 0; dr < drs; dr++) {
            int y = dr*cellSize;
            if (dr == rv1 || dr == rv2 || dr == rv3 || dr == rv4) {
                g2d.drawLine(0, y-2, bw, y-2);
                g2d.drawLine(0, y+2, bw, y+2);
            } else {
                g2d.drawLine(0, y, bw, y);
            }
        }

        // 竖线：河界两行之间断开
        for (int c = 1; c < cols-1; c++) {
            int x = c*cellSize;
            g2d.drawLine(x, 0, x, rv1*cellSize);
            g2d.drawLine(x, rv2*cellSize, x, rv3*cellSize);
            g2d.drawLine(x, rv4*cellSize, x, bh);
        }

        // 河界文字
        g2d.setFont(riverFont());
        drawRiverLabel(g2d, rv1*cellSize, bw, "楚河汉界2");
        drawRiverLabel(g2d, rv3*cellSize, bw, "楚河汉界1");

        // 宫线：固定 row13(红) 和 row4(黑)，最底下的只画上方米字
        for (int dr = 0; dr < drs; dr++) {
            int lr = fr[dr];
            if (lr == 13 || lr == 4) drawPalaceAt(g2d, dr, dr != drs-1);
        }

        // 棋子
        for (int dr = 0; dr < drs; dr++) {
            int lr = fr[dr]; int y = dr*cellSize;
            for (int c = 0; c < cols; c++) {
                int ss = b.getStackSize(lr, c); if (ss == 0) continue;
                java.util.List<Piece> stack = b.getStack(lr, c);
                int x = c*cellSize, pr2 = cellSize/2-2;
                if (ss == 1) drawSinglePiece(g2d, stack.get(0), x, y, pr2);
                else { for (int s = 0; s < ss; s++) { int ox = s%2==0?-4:4, oy = s%2==0?-4:4; drawSinglePiece(g2d,stack.get(s),x+ox,y+oy,pr2); } drawStackBadge(g2d, ss, x, y, pr2); }
            }
        }
        drawMoveIndicators(g2d, b);
    }

    private void drawPalaceAt(Graphics2D g2d, int dr, boolean drawLower) {
        g2d.setColor(GRID_COLOR); g2d.setStroke(new BasicStroke(1));
        int x1 = 3*cellSize, x2 = 5*cellSize;
        int yTop = Math.max(0, (dr-2)*cellSize);
        int yMid = dr*cellSize;
        int yBot = drawLower ? (dr+2)*cellSize : yMid;
        drawPalaceFrame(g2d, x1, yTop, x2, yBot);
        drawPalaceDiag(g2d, x1, yTop, x2, yMid);
        if (drawLower)
            drawPalaceDiag(g2d, x1, yMid, x2, yBot);
    }

    private void drawBoardGrid(Graphics2D g2d,ReadonlyBoard b){
        int rows=b.getRows(),cols=b.getCols(),bw=(cols-1)*cellSize,bh=(rows-1)*cellSize; boolean tb=tb();
        g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(0,0,0,bh); g2d.drawLine(bw,0,bw,bh);
        g2d.setColor(GRID_COLOR); g2d.setStroke(new BasicStroke(1));
        for(int c=1;c<cols-1;c++){ int ry=getRiverY(0),ry2=getRiverY(1); g2d.drawLine(c*cellSize,0,c*cellSize,ry); g2d.drawLine(c*cellSize,ry2,c*cellSize,bh); }
        for(int r=0;r<rows;r++){ int y=r*cellSize; boolean isEdge=(tb&&(r==0||r==rows-1)); if(isEdge||isRiverRow(r)){ g2d.drawLine(0,y-2,bw,y-2); g2d.drawLine(0,y+2,bw,y+2); } else g2d.drawLine(0,y,bw,y); }
        if(tb){ Font rf=riverFont(); g2d.setFont(rf); g2d.setColor(GRID_COLOR); int tw=g2d.getFontMetrics().stringWidth("楚河汉界2"); g2d.drawString("楚河汉界2",(bw-tw)/2,-cellSize/2+g2d.getFontMetrics().getAscent()/3); drawRiverLabel(g2d,bh,bw,"楚河汉界2"); }
        drawRiverLabel(g2d,getRiverY(0),bw,tb?"楚河汉界1":(boardFlipped?"汉界      楚河":"楚河      汉界"));
    }

    private void drawRiverLabel(Graphics2D g2d,int y,int bw,String text) {
        g2d.setColor(GRID_COLOR); g2d.setStroke(new BasicStroke(1));
        g2d.drawLine(0,y-2,bw,y-2); g2d.drawLine(0,y+2,bw,y+2);
        g2d.setFont(riverFont()); FontMetrics fm=g2d.getFontMetrics();
        g2d.drawString(text,(bw-fm.stringWidth(text))/2,y+cellSize/2+fm.getAscent()/3);
    }
    private int getRiverY(int p){ boolean tb=tb(); return tb?(p==0?8:9)*cellSize:(p==0?4:5)*cellSize; }
    private boolean isRiverRow(int r){ boolean tb=tb(); return tb?(r==8||r==9):(r==4||r==5); }

    private void drawPalaceGlobal(Graphics2D g2d){
        g2d.setColor(GRID_COLOR);g2d.setStroke(new BasicStroke(1));
        int x1=3*cellSize,x2=5*cellSize;
        if(tb()){
            drawPalaceFrame(g2d,x1,2*cellSize,x2,6*cellSize);
            drawPalaceDiag(g2d,x1,2*cellSize,x2,4*cellSize);
            drawPalaceDiag(g2d,x1,4*cellSize,x2,6*cellSize);
            drawPalaceFrame(g2d,x1,11*cellSize,x2,15*cellSize);
            drawPalaceDiag(g2d,x1,11*cellSize,x2,13*cellSize);
            drawPalaceDiag(g2d,x1,13*cellSize,x2,15*cellSize);
        } else {
            drawPalaceFrame(g2d,x1,0,x2,2*cellSize);
            drawPalaceDiag(g2d,x1,0,x2,2*cellSize);
            drawPalaceFrame(g2d,x1,7*cellSize,x2,9*cellSize);
            drawPalaceDiag(g2d,x1,7*cellSize,x2,9*cellSize);
        }
    }
    private void drawPalaceFrame(Graphics2D g2d,int x1,int y1,int x2,int y2){ g2d.drawRect(x1,y1,x2-x1,y2-y1);g2d.drawRect(x1+2,y1+2,(x2-x1)-4,(y2-y1)-4); }
    private void drawPalaceDiag(Graphics2D g2d,int x1,int y1,int x2,int y2){ g2d.drawLine(x1,y1,x2,y2);g2d.drawLine(x2,y1,x1,y2); }

    private void drawMoveIndicators(Graphics2D g2d,ReadonlyBoard board){
        if(selectedRow!=-1&&selectedCol!=-1){
            int hr=cellSize/2;g2d.setColor(new Color(255,255,0,100));
            if(tb()&&viewMode!=ViewMode.GLOBAL){
                int[] fr=buildFocusedRows(board);
                for(int i=0;i<fr.length;i++){
                    if(fr[i]==selectedRow){
                        int hx=selectedCol*cellSize,hy=i*cellSize;
                        g2d.fillOval(hx-hr,hy-hr,hr*2,hr*2);
                    }
                }
            }else{
                int[]d=logicToDisplay(selectedRow,selectedCol);
                g2d.fillOval(d[1]*cellSize-hr,d[0]*cellSize-hr,hr*2,hr*2);
            }
        }
        if(session.getRuleBoolean(RuleRegistry.SHOW_HINTS.registryName)){
            g2d.setColor(VALID_MOVE_COLOR);
            if(tb()&&viewMode!=ViewMode.GLOBAL){
                int[] fr=buildFocusedRows(board);
                for(Point p:validMoves){
                    for(int i=0;i<fr.length;i++){
                        if(fr[i]==p.x){g2d.fillOval(p.y*cellSize-5,i*cellSize-5,10,10);}
                    }
                }
            }else{
                for(Point p:validMoves){int[]d=logicToDisplay(p.x,p.y);g2d.fillOval(d[1]*cellSize-5,d[0]*cellSize-5,10,10);}
            }
        }
        if(forceMoveToRow!=-1&&forceMoveToCol!=-1){int[]d=logicToDisplay(forceMoveToRow,forceMoveToCol);int mx=d[1]*cellSize,my=d[0]*cellSize;if(!style){g2d.setColor(new Color(255,0,0));g2d.setStroke(new BasicStroke(3));int r=cellSize/2;g2d.drawOval(mx-r,my-r,r*2,r*2);}else{g2d.setColor(new Color(0,38,255));g2d.fillOval(mx-5,my-5,10,10);}}
    }
    private void drawPiecesGlobal(Graphics2D g2d,ReadonlyBoard board){int pr=cellSize/2-2;for(int r=0;r<board.getRows();r++)for(int c=0;c<board.getCols();c++){int ss=board.getStackSize(r,c);if(ss==0)continue;java.util.List<Piece> stack=board.getStack(r,c);int[]d=logicToDisplay(r,c);int x=d[1]*cellSize,y=d[0]*cellSize;if(ss==1)drawSinglePiece(g2d,stack.get(0),x,y,pr);else{for(int i=0;i<ss;i++){int ox=(i%2==0?-4:4),oy=(i%2==0?-4:4);drawSinglePiece(g2d,stack.get(i),x+ox,y+oy,pr);}drawStackBadge(g2d,ss,x,y,pr);}}}
    private void drawSinglePiece(Graphics2D g2d,Piece piece,int x,int y,int r){g2d.setColor(piece.isRed()?RED_PIECE_COLOR:BLACK_PIECE_COLOR);g2d.fillOval(x-r,y-r,r*2,r*2);g2d.setColor(Color.WHITE);g2d.setStroke(new BasicStroke(2));g2d.drawOval(x-r,y-r,r*2,r*2);g2d.setColor(Color.WHITE);g2d.setFont(new Font("SimHei",Font.BOLD,cellSize/2));String t=piece.getDisplayName();FontMetrics fm=g2d.getFontMetrics();g2d.drawString(t,x-fm.stringWidth(t)/2,y+fm.getAscent()/2-fm.getDescent());}
    private void drawStackBadge(Graphics2D g2d,int cnt,int x,int y,int r){int br=r/3,bx=x+r-br,by=y+r-br;g2d.setColor(new Color(255,200,0));g2d.fillOval(bx-br,by-br,br*2,br*2);g2d.setColor(Color.BLACK);g2d.setStroke(new BasicStroke(1));g2d.drawOval(bx-br,by-br,br*2,br*2);g2d.setFont(new Font("Dialog",Font.BOLD,(int)(br*1.2)));String t=String.valueOf(cnt);FontMetrics fm=g2d.getFontMetrics();g2d.drawString(t,bx-fm.stringWidth(t)/2,by+(fm.getAscent()-fm.getDescent())/2);}

    @Override public Dimension getPreferredSize(){
        ReadonlyBoard b=board();int pr=cellSize/2-2;int w=(b.getCols()-1)*cellSize+2*pr;
        if(tb()&&viewMode!=ViewMode.GLOBAL){int[] fr=buildFocusedRows(b);int h=(fr.length-1)*cellSize+2*pr;return new Dimension(w,h);}
        int h=(b.getRows()-1)*cellSize+2*pr;if(tb())h+=cellSize*2;return new Dimension(w,h);
    }
    public void unbind(){/* no-op: RulesConfigProvider binding removed; GameSession lifecycle managed externally */}
    public int getSelectedStackIndex(){return selectedStackIndex;}
    public void setBoardSetupMode(boolean v){boardSetupMode=v;if(!v)clearSelection();repaint();}
    public boolean isBoardSetupMode(){return boardSetupMode;}

    private void handleSetupModeClick(MouseEvent e){ReadonlyBoard b=board();int dc=Math.round((float)(e.getX()-offsetX)/cellSize),dr=Math.round((float)(e.getY()-offsetY)/cellSize);int[]logic=displayToLogic(dr,dc);int row=logic[0],col=logic[1];if(!b.isValid(row,col))return;Container p=getParent();if(p!=null){Container tl=SwingUtilities.getWindowAncestor(p);if(tl instanceof ChineseChessFrame){InfoSidePanel info=((ChineseChessFrame)tl).infoSidePanel;if(e.getButton()==MouseEvent.BUTTON1){Piece.Type sel=info.getSelectedPieceType();if(sel!=null)placePieceInSetupMode(row,col,sel);}else if(e.getButton()==MouseEvent.BUTTON3)removePieceInSetupMode(row,col);}}}
    public void placePieceInSetupMode(int row,int col,Piece.Type t){if(!boardSetupMode||t==null)return;ReadonlyBoard b=board();if(!b.isValid(row,col))return;/* 注意：ReadonlyBoard 不支持 setPiece；棋盘布置需通过 GameSession 的其他机制 */if(isNetSessionActive.getAsBoolean())sendBoardSetupPlace(row,col,t);repaint();}
    public void removePieceInSetupMode(int row,int col){if(!boardSetupMode)return;ReadonlyBoard b=board();if(!b.isValid(row,col))return;/* 注意：ReadonlyBoard 不支持 setPiece；棋盘布置需通过 GameSession 的其他机制 */if(isNetSessionActive.getAsBoolean())sendBoardSetupRemove(row,col);repaint();}

    /** 发送"放置棋子"消息到远端（通过依赖注入的 boardSetupSender） */
    private void sendBoardSetupPlace(int row, int col, Piece.Type t) {
        try {
            JsonObject jo = new JsonObject();
            jo.addProperty("cmd", "BOARD_SETUP_PLACE");
            jo.addProperty("row", row);
            jo.addProperty("col", col);
            jo.addProperty("pieceType", t.name());
            boardSetupSender.accept(jo);
        } catch (Throwable ignored) {}
    }

    /** 发送"移除棋子"消息到远端（通过依赖注入的 boardSetupSender） */
    private void sendBoardSetupRemove(int row, int col) {
        try {
            JsonObject jo = new JsonObject();
            jo.addProperty("cmd", "BOARD_SETUP_REMOVE");
            jo.addProperty("row", row);
            jo.addProperty("col", col);
            boardSetupSender.accept(jo);
        } catch (Throwable ignored) {}
    }
}
