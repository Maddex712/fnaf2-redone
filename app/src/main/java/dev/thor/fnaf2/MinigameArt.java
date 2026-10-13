package dev.thor.fnaf2;

import android.graphics.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Imported pixel art is also the collision mask; transparent pixels do not block movement. */
final class MinigameArt {
    final Fnaf2Assets assets;final Map<Integer,Bitmap> images=new ConcurrentHashMap<>();
    final Map<Integer,BitSet> masks=new ConcurrentHashMap<>();final Paint paint=new Paint();
    volatile boolean closed;
    MinigameArt(Fnaf2Assets a){assets=a;}
    void prepare(Runnable complete){assets.worker.execute(()->{for(int[] info:MinigameSprites.INFO){if(closed)return;Bitmap b=assets.decode(info[0]);if(b==null)continue;images.put(info[0],b);int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());BitSet mask=new BitSet(pixels.length);for(int i=0;i<pixels.length;i++)if((pixels[i]>>>24)!=0)mask.set(i);masks.put(info[0],mask);}complete.run();});}
    boolean overlap(DeathMinigame.Sprite a,DeathMinigame.Sprite b){
        if(!DeathMinigame.boundsOverlap(a,b))return false;
        int[] aa=MinigameSprites.info(a.id),bb=MinigameSprites.info(b.id);BitSet am=masks.get(a.id),bm=masks.get(b.id);if(am==null||bm==null)return false;
        int l=Math.max(a.left(),b.left()),r=Math.min(a.left()+aa[1],b.left()+bb[1]),t=Math.max(a.top(),b.top()),bottom=Math.min(a.top()+aa[2],b.top()+bb[2]);
        for(int y=t;y<bottom;y++)for(int x=l;x<r;x++)if(am.get((y-a.top())*aa[1]+x-a.left())&&bm.get((y-b.top())*bb[1]+x-b.left()))return true;return false;
    }
    void draw(Canvas c,DeathMinigame.Sprite s){Bitmap b=images.get(s.id);if(b!=null)c.drawBitmap(b,s.left(),s.top(),paint);}
    void sprite(Canvas c,int id,int x,int y){draw(c,new DeathMinigame.Sprite(id,x,y,false,false));}
    void draw(Canvas c,DeathMinigame m,float w,Fnaf2View view){
        c.drawColor(Color.BLACK);if(!m.gameStarted||m.endTicks>0){c.save();float k=Math.min(w/1024,1);c.translate((w-1024*k)/2,(768-768*k)/2);c.scale(k,k);c.clipRect(0,0,1024,768);paint.setColor(Color.WHITE);paint.setAlpha(255);sprite(c,m.man?661:678,m.barX,m.barY1);sprite(c,m.man?672:680,m.barX,m.barY2);if(m.man&&m.endTicks>30)sprite(c,679,18,720);c.restore();return;}
        c.save();float k=Math.min(w/1024,1);c.translate((w-1024*k)/2,(768-768*k)/2);c.scale(k,k);c.clipRect(0,0,1024,768);
        if(m.kind==DeathMinigame.FOXY)c.translate(m.x<1024?0:-916,0);
        paint.setColor(Color.WHITE);paint.setAlpha(255);paint.setFilterBitmap(false);for(DeathMinigame.Sprite s:m.scenery())if(m.kind!=DeathMinigame.FOXY||s.id!=783&&s.id!=790&&s.id!=791&&s.id!=801)draw(c,s);
        sprite(c,m.playerImage(),m.x+(m.kind==DeathMinigame.SAVE?1:0),m.y+(m.kind==DeathMinigame.SAVE?1:0));
        if(m.kind==DeathMinigame.FOXY)sprite(c,800,119,-200);
        if(m.kind==DeathMinigame.GIFTS){String score="0"+m.score+"00";for(int i=0;i<4;i++)sprite(c,559+score.charAt(i)-'0',128+i*69,96);}
        if(m.kind==DeathMinigame.FOXY){c.translate(m.x<1024?0:916,0);sprite(c,m.phase==0?783:m.phase==1?new int[]{790,791}[(m.ticks*20/100)%2]:801,231,10);}
        paint.setAlpha(20+(m.ticks*17/6)%30);sprite(c,(m.ticks/2)%2==0?650:651,0,0);paint.setAlpha(255);
        c.restore();
    }
    void close(){closed=true;images.clear();masks.clear();}
}
