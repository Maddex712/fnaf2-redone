package dev.thor.fnaf2;
import java.util.Random;

/** Independent menu clock: a Puppet fade must finish before Withered Chica returns. */
final class MenuEffects {
    static final long PEEK_MS=5400;
    final Random random;
    long last=-1,nextPeek,peekStart=-1,nextGlitch,nextLight,nextStatic;
    int variant,sceneAlpha=255,staticAlpha=155,blipAlpha=25;
    boolean blip;
    int burstSteps,burstChoice,lastBurst;boolean burstOn;
    float puppetAlpha,peekAmount,handAlpha,handAmount;
    MenuEffects(){this(new Random());}
    MenuEffects(Random r){random=r;}
    static float smooth(float v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
    void reset(long now){last=now;peekStart=-1;nextPeek=now+8000+random.nextInt(6000);nextGlitch=now+500;nextLight=now+300;nextStatic=now+90;variant=burstSteps=lastBurst=0;burstOn=false;sceneAlpha=255;staticAlpha=155;blipAlpha=25;blip=false;puppetAlpha=peekAmount=handAlpha=handAmount=0;}
    void update(long now){
        if(last<0||now<last||now-last>1500)reset(now);
        last=now;
        // User-requested bursts: several abrupt Toy/Withered swaps, then a quiet gap.
        // Keep the imported poses and static; do not slide or morph the faces.
        while(now>=nextGlitch){
            if(burstSteps==0){burstChoice=1+random.nextInt(3);if(burstChoice==lastBurst)burstChoice=burstChoice%3+1;lastBurst=burstChoice;burstSteps=6+random.nextInt(4)*2;burstOn=false;}
            burstOn=!burstOn;variant=burstOn?burstChoice:0;burstSteps--;blipAlpha=55-random.nextInt(30);
            nextGlitch+=burstSteps==0?650+random.nextInt(1000):45+random.nextInt(66);
        }
        while(now>=nextLight){sceneAlpha=255-random.nextInt(250);blip=random.nextInt(3)==1;nextLight+=300;}
        while(now>=nextStatic){staticAlpha=205-random.nextInt(100);nextStatic+=90;}
        if(peekStart<0&&now>=nextPeek)peekStart=now;
        if(peekStart>=0){
            long age=now-peekStart;
            if(age<PEEK_MS){
                handAmount=smooth(age/600f);handAlpha=handAmount*(1-smooth((age-4600)/800f));
                float in=smooth((age-600)/1200f),out=1-smooth((age-3800)/1200f);
                puppetAlpha=in*out;peekAmount=in*out;if(variant==2)variant=0;return;
            }
            // Puppet is completely absent; a short clear frame separates Chica's return.
            puppetAlpha=peekAmount=handAlpha=handAmount=0;
            if(age<PEEK_MS+180){if(variant==2)variant=0;return;}
            peekStart=-1;nextPeek=now+12000+random.nextInt(9000);
        }
        puppetAlpha=peekAmount=handAlpha=handAmount=0;
    }
}
