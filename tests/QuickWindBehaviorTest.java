package dev.thor.fnaf2;
import java.util.Arrays;
import java.util.Random;
public final class QuickWindBehaviorTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Fnaf2State game(){Fnaf2State s=new Fnaf2State(new Random(){public int nextInt(int bound){return bound-1;}},new Random(123));s.start(1);s.step(0);Arrays.fill(s.ai,0);Arrays.fill(s.pos,-1);return s;}
    static void ticks(Fnaf2State s,int count){for(int i=0;i<count;i++)s.tick(1.0/60);}
    public static void main(String[] args){
        for(boolean stay:new boolean[]{false,true}){
            Fnaf2State s=game();s.quickWindStayOnCamera=stay;s.music=400;s.setQuickWind(true);ticks(s,30);
            check(s.monitor&&s.camera==10&&s.wind&&s.music>400,"R2 opens CAM 11 and winds");
            s.setQuickWind(false);check(!s.wind&&!s.quickWind,"Release stops winding");check(s.monitor==stay,"Configured return mode");
            if(stay){s.camera(2);check(s.camera==2,"Camera selection works immediately after release");s.toggleMonitor();check(!s.monitor,"X can close monitor after stay mode");}
            s=game();s.quickWindStayOnCamera=stay;s.setQuickWind(true);ticks(s,2);s.setQuickWind(false);ticks(s,35);
            check(s.monitor==stay&&!s.wind&&s.cameraRaiseTicks==0,"Release during monitor animation");
            s.start(1);check(s.quickWindStayOnCamera==stay,"Mode survives a new night");
            s.setQuickWind(true);ticks(s,20);s.pauseForInterruption();int t=s.ticks;ticks(s,60);
            check(s.paused&&s.ticks==t&&!s.wind&&!s.quickWind,"Interruption cancels held input and freezes time");
        }
        Fnaf2State s=game();s.pos[Fnaf2State.TC]=Fnaf2State.OFFICE;s.toyChicaOfficeSeconds=6;ticks(s,1);
        check(s.pos[Fnaf2State.TC]==Fnaf2State.OFFICE&&s.phase==0,"Unmasked left-vent Chica does not retreat");
        s.toggleMonitor();ticks(s,15);check(s.pos[Fnaf2State.TC]==Fnaf2State.CAUGHT,"Ignored Chica commits with monitor raised");
        s.toggleMonitor();check(s.phase==1&&s.attacker==5,"Lowering monitor produces Toy Chica attack");
        s=game();s.pos[Fnaf2State.TC]=Fnaf2State.OFFICE;s.mask=true;s.maskSeconds=5;ticks(s,1);
        check(s.pos[Fnaf2State.TC]==7&&s.phase==0,"Successful mask defense retreats Chica safely");
        System.out.println("PASS: "+checks+" winding/transition/interruption/Chica checks");
    }
}
