package io.github.samera2022.chinese_chess.core.rules;

import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;

public class MoveValidator {
    private Board board;
    private GameRulesConfig rulesConfig;
    public MoveValidator(Board board) { this.board = board; this.rulesConfig = RulesConfigProvider.get(); }
    public void setRulesConfig(GameRulesConfig c) { if (c != null) this.rulesConfig = c; }
    public boolean isValidMove(int fr,int fc,int tr,int tc) { return isValidMove(fr,fc,tr,tc,-1); }

    public boolean isValidMove(int fr,int fc,int tr,int tc,int si) {
        if (rulesConfig == null) rulesConfig = RulesConfigProvider.get();
        if (!board.isValid(fr,fc) || !board.isValid(tr,tc)) return false;
        Piece p; if (si>=0) { java.util.List<Piece> s=board.getStack(fr,fc); if(si>=s.size())return false; p=s.get(si); } else p=board.getPiece(fr,fc);
        if (p==null) return false;
        Piece tp=board.getPiece(tr,tc);
        if (tp!=null&&tp.isRed()==p.isRed()) { if(rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)&&rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName)>1){ if(board.getStackSize(tr,tc)>=rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName))return false; } else if(!rulesConfig.getBoolean(RuleRegistry.ALLOW_CAPTURE_OWN_PIECE.registryName))return false; }
        if (fr==tr&&fc==tc) return false;
        switch(p.getType()) {
            case RED_KING:case BLACK_KING: return isValidKingMove(fr,fc,tr,tc,p);
            case RED_ADVISOR:case BLACK_ADVISOR: return isValidAdvisorMove(fr,fc,tr,tc,p);
            case RED_ELEPHANT:case BLACK_ELEPHANT: return isValidElephantMove(fr,fc,tr,tc,p);
            case RED_HORSE:case BLACK_HORSE: return isValidHorseMove(fr,fc,tr,tc);
            case RED_CHARIOT:case BLACK_CHARIOT: return isValidChariotMove(fr,fc,tr,tc);
            case RED_CANNON:case BLACK_CANNON: return isValidCannonMove(fr,fc,tr,tc,p);
            case RED_SOLDIER:case BLACK_SOLDIER: return isValidSoldierMove(fr,fc,tr,tc,p);
            default: return false;
        }
    }

    private int H(){return board.getRows();} private int W(){return Board.COLS;}
    private boolean tb(){return rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);}
    private boolean lr(){return rulesConfig.getBoolean(RuleRegistry.LEFT_RIGHT_CONNECTED.registryName);}

    /** 判断棋子是否已在对方境内（已过河） */
    private boolean hasCrossedRiver(int row, boolean isRed) {
        int riverRow = tb() ? 8 : 4;
        return isRed ? row <= riverRow : row > riverRow;
    }

    private int[] hObs(int r,int c1,int c2){ return lr()?hObsW(r,c1,c2):hObsN(r,c1,c2); }
    private int[] hObsN(int r,int c1,int c2){ int t=0;for(int c=0;c<W();c++)if(board.getPiece(r,c)!=null)t++; int mn=Math.min(c1,c2),mx=Math.max(c1,c2),d=0;for(int c=mn+1;c<mx;c++)if(board.getPiece(r,c)!=null)d++; int ep=(board.getPiece(r,c1)!=null?1:0)+(board.getPiece(r,c2)!=null?1:0); return new int[]{d,Math.max(0,t-ep-d)}; }
    private int[] hObsW(int r,int c1,int c2){ int W=W(),e1=c1+W,e2=c2+W,mn=Math.min(e1,e2),mx=Math.max(e1,e2),d=0;for(int ec=mn+1;ec<mx;ec++){int oc=ec<W?ec:ec<2*W?ec-W:ec-2*W;if(board.getPiece(r,oc)!=null)d++;} int mt=0;for(int c=0;c<W;c++)if(board.getPiece(r,c)!=null&&c!=c1&&c!=c2)mt++; return new int[]{d,Math.max(0,mt-d)}; }
    private boolean[] hClr(int r,int fc,int tc){ boolean dc=true;int s=fc<tc?1:-1;for(int c=fc+s;c!=tc;c+=s){if(board.getPiece(r,c)!=null){dc=false;break;}}boolean wc=true;if(lr()){int t=0;for(int c=0;c<W();c++)if(board.getPiece(r,c)!=null)t++;int mn=Math.min(fc,tc),mx=Math.max(fc,tc),dp=0;for(int c=mn+1;c<mx;c++)if(board.getPiece(r,c)!=null)dp++;int ep=(board.getPiece(r,fc)!=null?1:0)+(board.getPiece(r,tc)!=null?1:0);wc=(t-ep-dp)==0;}return new boolean[]{dc,wc};}

    private int[] vObs(int c,int r1,int r2){ return tb()?vObsW(c,r1,r2):vObsN(c,r1,r2); }
    private int[] vObsN(int c,int r1,int r2){ int t=0;for(int r=0;r<H();r++)if(board.getPiece(r,c)!=null)t++;int mn=Math.min(r1,r2),mx=Math.max(r1,r2),d=0;for(int r=mn+1;r<mx;r++)if(board.getPiece(r,c)!=null)d++;int ep=(board.getPiece(r1,c)!=null?1:0)+(board.getPiece(r2,c)!=null?1:0);return new int[]{d,Math.max(0,t-ep-d)};}
    private int[] vObsW(int c,int r1,int r2){ int H=H(),e1=r1+H,e2=r2+H,mn=Math.min(e1,e2),mx=Math.max(e1,e2),d=0;for(int er=mn+1;er<mx;er++){int or=er<H?er:er<2*H?er-H:er-2*H;if(board.getPiece(or,c)!=null)d++;}int mt=0;for(int r=0;r<H;r++)if(board.getPiece(r,c)!=null&&r!=r1&&r!=r2)mt++;return new int[]{d,Math.max(0,mt-d)};}
    private boolean[] vClrW(int fr,int tr,int c){ boolean dc=true;int s=fr<tr?1:-1;for(int r=fr+s;r!=tr;r+=s){if(board.getPiece(r,c)!=null){dc=false;break;}}boolean wc=true;if(tb()){int t=0;for(int r=0;r<H();r++)if(board.getPiece(r,c)!=null)t++;int mn=Math.min(fr,tr),mx=Math.max(fr,tr),dp=0;for(int r=mn+1;r<mx;r++)if(board.getPiece(r,c)!=null)dp++;int ep=(board.getPiece(fr,c)!=null?1:0)+(board.getPiece(tr,c)!=null?1:0);wc=(t-ep-dp)==0;}return new boolean[]{dc,wc};}

    private boolean isValidChariotMove(int fr,int fc,int tr,int tc){ if(fr!=tr&&fc!=tc)return false; if(fr==tr){int[]o=hObs(fr,fc,tc);return lr()?(o[0]==0||o[1]==0):o[0]==0;}else{int[]o=vObs(fc,fr,tr);return tb()?(o[0]==0||o[1]==0):o[0]==0;}}
    private boolean isValidCannonMove(int fr,int fc,int tr,int tc,Piece p){ if(fr!=tr&&fc!=tc)return false;Piece t=board.getPiece(tr,tc);boolean cap=t!=null&&t.isRed()!=p.isRed();if(fr==tr){int[]o=hObs(fr,fc,tc);boolean dv=cap?o[0]==1:o[0]==0,wv=cap?o[1]==1:o[1]==0;return lr()?(dv||wv):dv;}else{int[]o=vObs(fc,fr,tr);boolean dv=cap?o[0]==1:o[0]==0,wv=cap?o[1]==1:o[1]==0;return tb()?(dv||wv):dv;}}
    private boolean isValidHorseMove(int fr,int fc,int tr,int tc){ int rd=Math.abs(tr-fr),cd=Math.abs(tc-fc),H=H(),W=W(),er=rd,ec=cd; boolean tw=tb()&&rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED_HORSE.registryName)&&rd>H/2; if(tw)er=H-rd; boolean lw=lr()&&rulesConfig.getBoolean(RuleRegistry.LEFT_RIGHT_CONNECTED_HORSE.registryName)&&cd>W/2; if(lw)ec=W-cd; if(!((er==1&&ec==2)||(er==2&&ec==1)))return false; int mr,mc; if(er==1&&ec==2){mr=fr;int s=tc>fc?1:-1;if(lw&&Math.abs(s)==1){int c=fc+s;mc=c<0?W-1:(c>=W?0:c);}else mc=fc+s;}else{mc=fc;int s=tr>fr?1:-1;if(tw&&Math.abs(s)==1){int r=fr+s;mr=r<0?H-1:(r>=H?0:r);}else mr=fr+s;} if(rulesConfig.getBoolean(RuleRegistry.UNBLOCK_PIECE.registryName)&&rulesConfig.getBoolean(RuleRegistry.UNBLOCK_HORSE_LEG.registryName))return true; return board.getPiece(mr,mc)==null;}
    private boolean isValidElephantMove(int fr,int fc,int tr,int tc,Piece p){ if(!rulesConfig.getBoolean(RuleRegistry.NO_RIVER_LIMIT.registryName)){ int riverRow=tb()?8:4; if(p.isRed()){ if(tb()){ if(tr<=riverRow)return false; } else { if(tr<=riverRow)return false; } } else { if(tb()){ if(tr>riverRow)return false; } else { if(tr>riverRow)return false; } } } int rd=Math.abs(tr-fr),cd=Math.abs(tc-fc),H=H(),W=W(),er=rd,ec=cd; boolean tw=tb()&&rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED_ELEPHANT.registryName)&&rd>H/2; if(tw)er=H-rd; boolean lw=lr()&&rulesConfig.getBoolean(RuleRegistry.LEFT_RIGHT_CONNECTED_ELEPHANT.registryName)&&cd>W/2; if(lw)ec=W-cd; if(er!=2||ec!=2)return false; int mr=tw?((fr+tr+H)/2)%H:(fr+tr)/2,mc=lw?((fc+tc+W)/2)%W:(fc+tc)/2; if(rulesConfig.getBoolean(RuleRegistry.UNBLOCK_PIECE.registryName)&&rulesConfig.getBoolean(RuleRegistry.UNBLOCK_ELEPHANT_EYE.registryName))return true; return board.getPiece(mr,mc)==null;}
    private boolean isValidKingMove(int fr,int fc,int tr,int tc,Piece p){ boolean tb=tb();int H=H(),W=W();Piece t=board.getPiece(tr,tc); boolean tk=t!=null&&(t.getType()==Piece.Type.RED_KING||t.getType()==Piece.Type.BLACK_KING)&&t.isRed()!=p.isRed(); if(rulesConfig.getBoolean(RuleRegistry.DISABLE_FACING_GENERALS.registryName)&&tk){int rd=Math.abs(tr-fr),er=tb?Math.min(rd,H-rd):rd,rcd=Math.abs(tc-fc),ec=lr()?Math.min(rcd,W-rcd):rcd;if(er>1||ec>1)return false;} if(tk&&fc==tc){boolean[]vp=vClrW(fr,tr,fc);return tb?(vp[0]||vp[1]):vp[0];} if(tk&&fr==tr){boolean[]hp=hClr(fr,fc,tc);return lr()?(hp[0]||hp[1]):hp[0];} if(!rulesConfig.getBoolean(RuleRegistry.NO_RIVER_LIMIT.registryName)&&!rulesConfig.getBoolean(RuleRegistry.ALLOW_FLYING_GENERAL.registryName)){int mc=3,xc=5,mr,xr;if(tb){mr=p.isRed()?11:2;xr=p.isRed()?15:6;}else{mr=p.isRed()?7:0;xr=p.isRed()?9:2;}if(tr<mr||tr>xr||tc<mc||tc>xc)return false;} int rd=Math.abs(tr-fr),er=tb?Math.min(rd,H-rd):rd,rcd=Math.abs(tc-fc),ec=lr()?Math.min(rcd,W-rcd):rcd; if(rulesConfig.getBoolean(RuleRegistry.INTERNATIONAL_KING.registryName))return er<=1&&ec<=1&&(er+ec)>0;else return(er+ec)==1;}
    private boolean isValidAdvisorMove(int fr,int fc,int tr,int tc,Piece p){ boolean tb=tb(); if(!rulesConfig.getBoolean(RuleRegistry.ADVISOR_CAN_LEAVE.registryName)){int mc=3,xc=5,mr,xr;if(tb){mr=p.isRed()?11:2;xr=p.isRed()?15:6;}else{mr=p.isRed()?7:0;xr=p.isRed()?9:2;}if(tr<mr||tr>xr||tc<mc||tc>xc)return false;} int rd=Math.abs(tr-fr),cd=Math.abs(tc-fc); if(!rulesConfig.getBoolean(RuleRegistry.INTERNATIONAL_ADVISOR.registryName)){if(rd==1&&cd==1)return true;if(lr()&&rd==1&&cd==8)return true;if(tb()&&rd==(H()-1)&&cd==1)return true;return false;} if(rd==0&&cd>0){int[]o=hObs(fr,fc,tc);return lr()?(o[0]==0||o[1]==0):o[0]==0;} if(cd==0&&rd>0){int[]o=vObs(fc,fr,tr);return tb()?(o[0]==0||o[1]==0):o[0]==0;} int W=W();int[]ptc=lr()?new int[]{tc,tc-W,tc+W}:new int[]{tc};for(int vtc:ptc){if(rd==Math.abs(vtc-fc)&&rd>0&&chkDiag(fr,fc,tr,vtc))return true;}return false;}
    private boolean chkDiag(int r1,int c1,int r2,int vc2){int W=W(),rs=r2>r1?1:-1,cs=vc2>c1?1:-1,steps=Math.abs(r2-r1),cr=r1,cvc=c1;for(int i=0;i<steps-1;i++){cr+=rs;cvc+=cs;int rc=(cvc%W+W)%W;if(board.getPiece(cr,rc)!=null)return false;}return true;}
    private boolean isValidSoldierMove(int fr,int fc,int tr,int tc,Piece p){ int H=H(),W=W(),rrd=tr-fr;boolean tb=tb(); int rd; if(tb&&rrd==(H-1))rd=-1;else if(tb&&rrd==-(H-1))rd=1;else rd=rrd; int rcd=Math.abs(tc-fc),cd=lr()?Math.min(rcd,W-rcd):rcd; boolean isRed=p.isRed();boolean crossed=hasCrossedRiver(fr,isRed); boolean nr=rulesConfig.getBoolean(RuleRegistry.NO_RIVER_LIMIT.registryName),pr=rulesConfig.getBoolean(RuleRegistry.PAWN_CAN_RETREAT.registryName),ir=rulesConfig.getBoolean(RuleRegistry.ALLOW_INSIDE_RETREAT.registryName); int fw;if(tb){Piece king=isRed?board.getRedKing():board.getBlackKing();fw=(king!=null&&fr<king.getRow())?-1:1;}else fw=isRed?-1:1; if(!nr&&!crossed){if(rd==fw&&cd==0)return true;if(ir&&pr&&rd==-fw&&cd==0)return true;return false;}else{if(rd==fw&&cd==0)return true;if(rd==0&&cd==1)return true;if(pr&&rd==-fw&&cd==0)return true;}return false;}
}
