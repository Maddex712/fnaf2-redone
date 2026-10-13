package dev.thor.fnaf2;
import java.util.Random;
public final class MenuEffectsTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        MenuEffects burst=new MenuEffects(new Random(){@Override public int nextInt(int bound){return 0;}});burst.update(0);burst.nextPeek=Long.MAX_VALUE;
        burst.update(499);check(burst.variant==0,"Quiet introduction before first glitch");burst.update(500);check(burst.variant==1,"Withered flashes into Toy lineup");burst.update(545);check(burst.variant==0,"Toy pose flashes back inside burst");burst.update(590);check(burst.variant==1,"Same Withered flickers back in");burst.update(635);burst.update(680);burst.update(725);check(burst.variant==0&&burst.burstSteps==0,"Burst ends on Toys");burst.update(1374);check(burst.variant==0,"Quiet gap between bursts");burst.update(1375);check(burst.variant==2,"Next burst changes character");
        MenuEffects sampled=new MenuEffects(new Random(1987));sampled.update(0);sampled.nextPeek=Long.MAX_VALUE;int[] counts=new int[4];
        for(long t=100;t<=1000000;t+=100){sampled.update(t);counts[sampled.variant]++;check(sampled.sceneAlpha>=6&&sampled.sceneAlpha<=255,"Original scene opacity");check(sampled.staticAlpha>=106&&sampled.staticAlpha<=205,"Original static opacity");check(sampled.blipAlpha>=6&&sampled.blipAlpha<=55,"Original flash opacity");}
        check(counts[0]>6500&&counts[0]<8800,"Withered presence increases materially over former 6 percent");for(int i=1;i<=3;i++)check(counts[i]>350,"Every Withered gets frequent bursts");
        MenuEffects e=new MenuEffects(new Random(1987));e.update(1000);e.nextPeek=1000;e.update(1000);
        float previous=0;int peeks=0,returns=0;
        for(long now=1016;now<181000;now+=16){e.update(now);check(e.puppetAlpha>=0&&e.puppetAlpha<=1,"Opacity bounds");check(Math.abs(e.puppetAlpha-previous)<.04f,"Smooth fade");if(e.puppetAlpha>0){check(e.variant!=2,"Puppet overlaps Withered Chica");if(previous==0)peeks++;}if(e.variant==2)returns++;previous=e.puppetAlpha;}
        check(peeks>=4&&returns>0,"Peeks and Chica returns occur");
        e.update(200000);check(e.puppetAlpha==0&&e.variant==0,"Resume resets apparition");
        e.reset(0);e.nextPeek=0;e.update(0);e.update(400);check(e.handAlpha>0&&e.puppetAlpha==0,"Hand is seen before the face");e.update(1000);check(e.handAlpha==1&&e.puppetAlpha>0,"Head begins peeking after hand settles");e.update(2000);e.update(3000);e.update(4000);e.update(5000);check(e.puppetAlpha==0&&e.handAlpha>0,"Head retreats before fingers disappear");e.update(5400);check(e.handAlpha==0&&e.puppetAlpha==0,"Puppet fully clears before Withered return");
        System.out.println("PASS: frequent Toy/Withered glitch bursts and blend ranges, hand-first peek, head-first retreat and Withered Chica exclusion");
    }
}
