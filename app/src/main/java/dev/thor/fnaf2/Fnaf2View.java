package dev.thor.fnaf2;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.util.Locale;

final class Fnaf2View extends View {
    final MainActivity app;final boolean upper;final Paint p=new Paint(3);final Rect src=new Rect();
    final MenuBackdrop menuBackdrop=new MenuBackdrop();
    final Fnaf2Menu menus;final Typeface menuFont;
    final RetroMonitor monitorShell=new RetroMonitor();final SecurityDesk desk;
    final PerspectiveView perspective=new PerspectiveView();
    static final int WHITE=0xffd8d8ce;
    static final int[][] MAP={{602,557},{734,558},{602,491},{737,491},{608,651},{723,651},{758,431},{603,420},{915,390},{847,509},{950,465},{933,558}};
    float vw=1024;int held=-1;Bitmap lastScene,lastOffice;
    final Paint arcadePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Shader arcadeLaminate=new LinearGradient(0,0,0,768,new int[]{0xff424237,0xff242620,0xff171912},null,Shader.TileMode.CLAMP);
    final Shader arcadeRed=new RadialGradient(-.32f,-.45f,1.65f,new int[]{0xffb66b50,0xff8c3d31,0xff321c17},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
    final Shader arcadeYellow=new RadialGradient(-.32f,-.45f,1.65f,new int[]{0xffcfbd7b,0xffaa9350,0xff393321},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
    final Shader arcadeGrime=new RadialGradient(0,0,1,new int[]{0x3c130e07,0x20130e07,0},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
    final Path arcadeDeck=new Path();
    Fnaf2View(Context context,MainActivity a,boolean up){super(context);app=a;upper=up;menuFont=Typeface.MONOSPACE;menus=new Fnaf2Menu(this);desk=new SecurityDesk(this);setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);}
    void text(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setTypeface(menuFont);p.setTextSize(size);p.setColor(color);c.drawText(s,x,y,p);}
    void centered(Canvas c,String s,float w,float baseline,float size,int color,boolean bold){p.setStyle(Paint.Style.FILL);p.setTypeface(bold?Typeface.create(menuFont,Typeface.BOLD):menuFont);p.setTextSize(size);p.setColor(color);c.drawText(s,(w-p.measureText(s))/2,baseline,p);}
    void rect(Canvas c,float x,float y,float w,float h,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(x,y,x+w,y+h,p);}
    void outline(Canvas c,float x,float y,float w,float h,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(color);c.drawRect(x,y,x+w,y+h,p);p.setStyle(Paint.Style.FILL);}
    void image(Canvas c,int id,float x,float y,float w,float h,boolean cover){Bitmap b=app.assets.image(id);if(b==null)return;draw(c,b,x,y,w,h,cover,.5f);if(!c.quickReject(x,y,x+w,y+h,Canvas.EdgeType.BW))observe(id);}
    void draw(Canvas c,Bitmap b,float x,float y,float w,float h,boolean cover,float pan){float scale=cover?Math.max(w/b.getWidth(),h/b.getHeight()):Math.min(w/b.getWidth(),h/b.getHeight());float bw=b.getWidth()*scale,bh=b.getHeight()*scale;p.setColor(Color.WHITE);p.setAlpha(255);c.save();c.clipRect(x,y,x+w,y+h);c.drawBitmap(b,null,new RectF(x+(w-bw)*pan,y+(h-bh)/2,x+(w-bw)*pan+bw,y+(h-bh)/2+bh),p);c.restore();}
    void scene(Canvas c,int id,float w,double pan){Bitmap b=app.assets.image(id);if(b!=null){lastScene=b;observe(id);}else b=lastScene;if(b!=null){Canvas target=app.perspectiveEnabled?perspective.begin(w,70):c;draw(target,b,0,0,w,768,true,(float)pan);Fnaf2State s=app.state;int overlay=s.mangleOverlay();if(overlay>=0){float k=Math.max(w/b.getWidth(),768f/b.getHeight());target.save();target.translate((float)((w-b.getWidth()*k)*pan),(768-b.getHeight()*k)/2);target.scale(k,k);dreamSprite(target,overlay,overlay==218?637:overlay==78?671:0,overlay==219?177:overlay==78?455:0);target.restore();}if(s.puppetApparition>=0){Bitmap apparition=app.assets.image(s.puppetApparition);if(apparition!=null){p.setColor(Color.WHITE);p.setAlpha(s.puppetApparitionAlpha);observe(s.puppetApparition);target.drawBitmap(apparition,null,new RectF(0,0,w,768),p);p.setAlpha(255);}}if(app.perspectiveEnabled)perspective.finish(c,w);}}
    int encounterCandidate=-1;boolean captureBusy;
    void observe(int id){int candidate=EncounterCatalog.image(app.state,id);if(candidate>=0)encounterCandidate=candidate;}
    @Override public void draw(Canvas canvas){
        if(!captureBusy)encounterCandidate=-1;super.draw(canvas);
        int id=encounterCandidate;if(!captureBusy&&app.encounters.needs(id)&&getWidth()>0&&getHeight()>0){
            int w=Math.min(960,getWidth()),h=Math.max(1,getHeight()*w/getWidth());Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas shot=new Canvas(image);shot.scale(w/(float)getWidth(),h/(float)getHeight());captureBusy=true;try{draw(shot);}finally{captureBusy=false;}
            app.encounters.save(id,image,app.state.night,app.state.monitor?"CAM "+(app.state.camera+1):"Office");
        }
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);if(pcSingle()){drawScaled(c,pcUpper(),getWidth(),getHeight(),0);}else if(app.bottom==null){float h=getHeight()/2f;drawScaled(c,true,getWidth(),h,0);drawScaled(c,false,getWidth(),h,h);}else drawScaled(c,upper,getWidth(),getHeight(),0);}
    void drawScaled(Canvas c,boolean up,float width,float height,float y){c.save();c.translate(0,y);float scale=height/768;vw=width/scale;c.scale(scale,scale);if(!up&&useMonitorShell())monitorShell.draw(c,this,vw);else pane(c,up,vw);if(pcSingle()&&pcGameplay()&&app.state.cameraFlipImage()>=0)image(c,app.state.cameraFlipImage(),0,0,vw,768,true);if(pcGameplay()&&(up||app.state.monitor))pointerBars(c,vw);if(pcGameplay()&&app.audio.channels.containsKey("phone"))image(c,191,172,42,121,31,false);c.restore();}
    boolean useMonitorShell(){Fnaf2State s=app.state;return !app.minigame.active&&app.assets.ready&&app.intro==0&&app.ending==0&&(!s.playing||s.dead||s.paused||s.phase==1||(!s.monitor&&!s.won&&s.phase==0));}
    void pane(Canvas c,boolean up,float w){
        Fnaf2State s=app.state;
        if(!app.assets.ready){text(c,"FIVE NIGHTS AT FREDDY'S 2",45,190,34,WHITE);text(c,app.assets.status,45,370,22,WHITE);text(c,app.assets.importing?"PREPARING YOUR STEAM DATA…":"SCAN STEAM FILE / SELECT EXE OR ZIP",45,520,22,WHITE);return;}
        if(app.minigame.active){drawMinigame(c,up,w);return;}
        if(app.dream.active()){drawDream(c,up,w);return;}
        if(app.ending>0){if(up)image(c,app.ending,0,0,w,768,false);else{text(c,"SHIFT COMPLETE",45,300,35,WHITE);text(c,app.notice,45,370,24,WHITE);}return;}
        if(app.intro>0){if(up){if(app.intro==1)image(c,272,0,0,w,768,false);else image(c,new int[]{429,430,431,436,437,426,425}[s.night-1],w*.25f,225,w*.5f,300,false);}else if(app.intro==1)image(c,new int[]{429,430,431,436,437,426,425}[s.night-1],w*.25f,240,w*.5f,260,false);float fade=(float)Math.max(0,Math.min(Math.min(1,app.introTime/.45),((app.intro==1?6:2.2)-app.introTime)/.35));rect(c,0,0,w,768,((int)((1-fade)*255))<<24);return;}
        if(!s.playing){title(c,up,w);return;}
        if(s.won){if(up){float t=(float)Math.min(1,s.resultTime/3);c.save();c.clipRect(w*.28f,275,w*.72f,440);text(c,"5",w*.37f,410-t*150,112,WHITE);text(c,"6",w*.37f,560-t*150,112,WHITE);text(c,"AM",w*.49f,410,95,WHITE);c.restore();if(s.resultTime>=3)for(int i=0;i<35;i++){float x=(i*139)%((int)w),y=(float)((s.resultTime*75+i*43)%850)-80;rect(c,x,y,5,12,Color.HSVToColor(new float[]{i*31%360,.55f,.8f}));}}else{text(c,"SHIFT COMPLETE",55,300,36,WHITE);text(c,app.notice,55,370,24,WHITE);}return;}
        if(s.dead&&s.resultTime<5){if(up)image(c,new int[]{361,357,363}[(int)(s.resultTime*30)%3],0,0,w,768,true);else drawScareTerminal(c,w,SystemClock.uptimeMillis());return;}
        if(s.dead){rect(c,0,0,w,768,Color.BLACK);if(up){
            float scale=Math.min(w/1024f,1),left=(w-1024*scale)/2,top=(768-768*scale)/2;
            image(c,226,left,top,1024*scale,768*scale,false);
            Bitmap grain=app.assets.image(new int[]{361,357,363,358,359,360}[(int)(s.resultTime*30)%6]);if(grain!=null){p.setColor(WHITE);p.setAlpha(82);c.drawBitmap(grain,null,new RectF(left,top,left+1024*scale,top+768*scale),p);p.setAlpha(255);}
            image(c,228,left+180*scale,top+700*scale,206*scale,31*scale,false);
        }else drawScareTerminal(c,w,SystemClock.uptimeMillis());return;}
        if(s.phase==1){if(up){office(c,w);drawScare(c,w);}else drawScareTerminal(c,w,SystemClock.uptimeMillis());return;}
        if(s.paused){if(up){office(c,w);rect(c,0,0,w,768,0xe6000000);centered(c,"PAUSED",w,348,48,WHITE,true);centered(c,"START / BACK / TAP: resume   B: title",w,420,24,WHITE,false);}else{rect(c,0,0,w,768,Color.BLACK);rect(c,w/2-40,331,25,106,WHITE);rect(c,w/2+15,331,25,106,WHITE);}return;}
        if(up){office(c,w);if(s.mask||s.maskMotion!=0){drawMask(c,w);}else{text(c,s.hour()+" AM",w-190,58,29,WHITE);text(c,"Night "+s.night,w-190,94,22,WHITE);text(c,"FLASHLIGHT",35,47,18,WHITE);outline(c,35,61,120,16,WHITE);rect(c,38,64,(float)(114*s.flashlightPower),10,WHITE);}if(s.blackoutAlpha>0)rect(c,0,0,w,768,s.blackoutAlpha<<24);if(!s.monitor)musicWarning(c,w,false);}
        else{if(s.monitor){if(s.signalTicks>0){rect(c,0,0,w,768,Color.BLACK);image(c,MenuBackdrop.STATIC[(s.ticks/3)%6],0,0,w,768,true);}else scene(c,s.cameraImage(),w,s.cameraPan());cameraOverlay(c,w);}else{desk.draw(c,w);if(s.cameraFlipImage()>=0)image(c,s.cameraFlipImage(),0,0,w,768,true);}}
    }
    Bitmap developerScene(int id){
        Fnaf2State original=app.state,preview=EncounterCatalog.scene(id);boolean busy=captureBusy;Bitmap oldOffice=lastOffice,oldScene=lastScene;
        int[] subjects={326,681,518,520,Fnaf2Catalog.SCENE[93],Fnaf2Catalog.SCENE[94],Fnaf2Catalog.SCENE[96],Fnaf2Catalog.SCENE[95],65};
        boolean ready=app.assets.image(subjects[id])!=null;ready&=app.assets.image(id<5?preview.officeImage():preview.cameraImage())!=null;if(!ready)return null;
        Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(960f/1365,540f/768);
        try{app.state=preview;captureBusy=true;if(id<5)pane(canvas,true,1365);else{scene(canvas,preview.cameraImage(),1365,.5);cameraOverlay(canvas,1365);}return bitmap;}
        finally{app.state=original;captureBusy=busy;lastOffice=oldOffice;lastScene=oldScene;}
    }
    void arcadeControls(Canvas c,float w){
        DeathMinigame m=app.minigame;rect(c,0,0,w,768,0xff0c0b09);
        rect(c,0,0,30,768,0xff3e3023);rect(c,w-30,0,30,768,0xff2b221a);
        for(int i=0;i<23;i++){rect(c,3+i%5,0,1,768,0x203c1d09);rect(c,w-29+i,0,1,768,0x205d4028);}
        rect(c,31,20,w-56,120,0xff030403);rect(c,28,16,w-56,116,0xff514b34);
        rect(c,32,20,w-64,3,0xff92815c);rect(c,32,123,w-64,6,0xff241f16);
        centered(c,"FAZBEAR ARCADE",w,86,38,0xffc8bb80,true);arcadeWear(c,32,22,w-64,100,55);
        // A sloping laminate deck above the darker cabinet face.
        arcadeDeck.reset();arcadeDeck.moveTo(51,158);arcadeDeck.lineTo(w-51,158);arcadeDeck.lineTo(w-30,542);arcadeDeck.lineTo(30,542);arcadeDeck.close();
        arcadePaint.setShader(arcadeLaminate);c.drawPath(arcadeDeck,arcadePaint);arcadePaint.setShader(null);
        rect(c,31,542,w-62,17,0xff11110d);rect(c,35,542,w-70,3,0xff787563);
        centered(c,m.paused?"PAUSED - TAP TO RESUME":app.controllerInput?"D-PAD / LEFT STICK TO MOVE":app.inputProfile.mode==InputProfile.PC?"WASD / ARROW KEYS TO MOVE":"TOUCH THE D-PAD TO MOVE",w,209,21,0xffb4b5a4,false);
        arcadeWear(c,57,226,w-114,299,125);
        if(!app.controllerInput)for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){float x=w/2+d[0]*104,y=405+d[1]*104;rect(c,x-44,y-41,88,88,0xff080a08);rect(c,x-44,y-44,88,84,0xff33372d);outline(c,x-44,y-44,88,84,0xff5e6353);text(c,d[0]<0?"<":d[0]>0?">":d[1]<0?"^":"v",x-9,y+10,30,0xffb9bca7);}
        float jx=w*.17f,jy=405,bx=jx+app.arcadeStickX*34,by=340+app.arcadeStickY*28;
        p.setColor(0x88000000);c.drawOval(jx-55,jy-23,jx+65,jy+30,p);p.setColor(0xff080a08);c.drawOval(jx-49,jy-22,jx+49,jy+22,p);
        p.setColor(0xff55564b);c.drawOval(jx-39,jy-16,jx+39,jy+16,p);p.setColor(0xff151814);c.drawOval(jx-31,jy-13,jx+31,jy+13,p);
        p.setStrokeWidth(16);p.setColor(0xff55564d);c.drawLine(jx,jy,bx,by,p);p.setStrokeWidth(5);p.setColor(0xffb6b3a2);c.drawLine(jx-3,jy-2,bx-3,by,p);
        arcadeBall(c,bx,by,35,0xff843b2e);arcadeBall(c,w*.83f,365,29,0xff8c3d31);arcadeBall(c,w*.89f,437,29,0xffaa9350);
        rect(c,30,559,w-60,209,0xff23231c);arcadeWear(c,40,575,w-80,157,85);
        rect(c,w/2-91,594,188,115,0xff0b0c0a);rect(c,w/2-90,590,180,110,0xff434438);outline(c,w/2-90,590,180,110,0xff69695b);
        rect(c,w/2-49,617,98,15,0xff090b09);rect(c,w/2-44,619,88,2,0xffb0aea0);centered(c,"1 PLAYER",w,677,19,0xffbab8a2,false);
        for(float x:new float[]{w/2-78,w/2+78}){arcadeScrew(c,x,602);arcadeScrew(c,x,688);}
        centered(c,"PROPERTY OF FAZBEAR ENTERTAINMENT",w,741,14,0xff847d66,false);
    }
    void arcadeBall(Canvas c,float x,float y,float r,int color){
        p.setColor(0x8a000000);c.drawOval(x-r+5,y-r+10,x+r+9,y+r+15,p);
        p.setColor(0xff171811);c.drawCircle(x,y+3,r+4,p);
        c.save();c.translate(x,y);c.scale(r,r);arcadePaint.setShader(color==0xffaa9350?arcadeYellow:arcadeRed);c.drawCircle(0,0,1,arcadePaint);arcadePaint.setShader(null);c.restore();
        p.setColor(0x28e9dac1);c.drawOval(x-r*.48f,y-r*.62f,x-r*.15f,y-r*.43f,p);
    }
    void arcadeScrew(Canvas c,float x,float y){p.setColor(0xff141610);c.drawCircle(x+1,y+2,5,p);p.setColor(0xff7a7967);c.drawCircle(x,y,4,p);p.setColor(0xff23271e);p.setStrokeWidth(1.5f);c.drawLine(x-2,y-2,x+2,y+2,p);}
    void arcadeWear(Canvas c,float x,float y,float w,float h,int seed){
        c.save();c.clipRect(x,y,x+w,y+h);
        for(int i=0;i<seed;i++){float sx=x+Math.floorMod(i*163+seed*13,997)/997f*w,sy=y+Math.floorMod(i*89+seed*7,991)/991f*h;p.setStrokeWidth(i%5==0?2:1);p.setColor(i%3==0?0x246d6043:0x28000000);c.drawLine(sx,sy,sx+4+i%17,sy+i%4-2,p);}
        c.save();c.translate(x+w*.77f,y+h*.7f);c.scale(w*.15f,h*.18f);arcadePaint.setShader(arcadeGrime);c.drawCircle(0,0,1,arcadePaint);arcadePaint.setShader(null);c.restore();c.restore();
        arcadeScrew(c,x+5,y+5);arcadeScrew(c,x+w-5,y+5);arcadeScrew(c,x+5,y+h-5);arcadeScrew(c,x+w-5,y+h-5);
    }
    void drawMinigame(Canvas c,boolean up,float w){
        DeathMinigame m=app.minigame;
        if(up){if(app.minigameArt!=null)app.minigameArt.draw(c,m,w,this);if(m.attacker>=0){drawScareFrame(c,w,m.attacker,m.scareAnimationTime());}if(m.paused){rect(c,0,0,w,768,0xdd000000);centered(c,"PAUSED",w,370,48,WHITE,true);}}
        else arcadeControls(c,w);
    }
    void dreamSprite(Canvas c,int id,float x,float y){Bitmap b=app.assets.image(id);if(b!=null){p.setColor(Color.WHITE);c.drawBitmap(b,x,y,p);if(!c.quickReject(x,y,x+b.getWidth(),y+b.getHeight(),Canvas.EdgeType.BW))observe(id);}}
    void drawDream(Canvas c,boolean up,float w){DreamSequence d=app.dream;rect(c,0,0,w,768,Color.BLACK);if(!up)return;
        if(d.ticks>=1980){image(c,628,0,0,w,768,false);return;}
        c.save();c.translate((float)(w/2-d.center),0);dreamSprite(c,616,-2,-2);
        boolean late=d.nextNight==4;
        dreamSprite(c,late?592:d.nextNight==3?615:609,late?-154:d.nextNight==3?-136:-116,late?-39:d.nextNight==3?-9:-1);
        dreamSprite(c,late?617:d.nextNight==3?613:614,late?1637:d.nextNight==3?1653:1668,late?20:d.nextNight==3?12:-1);
        if(late&&d.yellow)dreamSprite(c,624,1156,5);
        if(d.nextNight==5)dreamSprite(c,626,(float)d.puppetX-255,53);c.restore();
        image(c,619,-74,-50,w+176,900,true);staticLines(c,w,.12f);
        if(d.ticks>=1920)image(c,new int[]{68,64,70,66,67}[(d.ticks-1920)%5],0,0,w,768,true);
        if(d.shade()>0)rect(c,0,0,w,768,d.shade()<<24);
    }
    void drawScareTerminal(Canvas c,float w,long now){
        desk.draw(c,w);
        // Interference stays inside the curved glass; the physical computer remains intact.
        Bitmap noise=app.assets.image(MenuBackdrop.STATIC[(int)(now/45%MenuBackdrop.STATIC.length)]);
        if(noise!=null){p.setColor(Color.WHITE);p.setAlpha(225);c.drawBitmap(noise,null,new RectF(0,0,w,768),p);p.setAlpha(255);}
        for(int y=0;y<768;y+=4)rect(c,0,y,w,1,0x48000000);
        float sweep=(now%900)/900f*880-112;
        rect(c,0,sweep,w,75,0x68000000);
    }
    void drawScare(Canvas c,float w){
        drawScareFrame(c,w,app.state.attacker,app.state.scareAnimationTime());
    }
    void drawScareFrame(Canvas c,float w,int who,double time){
        Bitmap b=app.assets.image(Fnaf2Catalog.scareFrame(who,time));if(b==null)b=app.assets.image(Fnaf2Catalog.SCARE[who][0]);if(b==null)return;
        // Scares are screen-space overlays: map the complete frame to the viewport.
        // Contain leaves visible 4:3 edges; cover crops off the top/bottom of the attack.
        p.setColor(Color.WHITE);p.setAlpha(255);
        c.drawBitmap(b,null,new RectF(0,0,w,768),p);
        app.scarePlayback.presented(time);
    }
    void drawMask(Canvas c,float w){
        int id=app.state.maskImage();if(id!=125){image(c,id,0,0,w,768,true);return;}
        Bitmap b=app.assets.image(125);if(b==null)return;
        // Original mask is an oversized overlay at (-100,-66), not a fitted 4:3 frame.
        float scale=Math.max(w/1024f,1),x=(w-1024*scale)/2-100*scale,y=(768-768*scale)/2-66*scale;
        x+=(float)app.state.maskOffset(false)*scale;y+=(float)app.state.maskOffset(true)*scale;
        p.setColor(Color.WHITE);c.save();c.clipRect(0,0,w,768);c.drawBitmap(b,null,new RectF(x,y,x+1224*scale,y+918*scale),p);c.restore();
    }
    void button(Canvas c,String label,float x,float y,float w,float h,boolean selected){rect(c,x,y,w,h,selected?0xff454b36:0xbb141714);outline(c,x,y,w,h,selected?0xffd4e3a7:0xff72786b);text(c,label,x+14,y+h*.65f,22,WHITE);}
    void title(Canvas c,boolean up,float w){if(up)menus.top(c,w);else menus.bottom(c,w);}
    void cameraOverlay(Canvas c,float w){
        Fnaf2State s=app.state;outline(c,17,17,w-34,734,0xffb6b6ac);text(c,Fnaf2State.CAMERA_NAME[s.camera],35,64,29,WHITE);text(c,String.format(Locale.US,"CAM %02d",s.camera+1),35,100,20,WHITE);
        if(s.signalTicks>0)centered(c,"SIGNAL INTERRUPTED",w,330,25,WHITE,false);
        float mx=w-380,my=435,mw=340,mh=255;image(c,2,mx,my,mw,mh,false);
        for(int i=0;i<12;i++){float x=mx+(MAP[i][0]-545)/416f*mw-23,y=my+(MAP[i][1]-347)/312f*mh-13;rect(c,x,y,46,27,s.camera==i?0xff81973b:0xff202521);outline(c,x,y,46,27,WHITE);text(c,String.format(Locale.US,"%02d",i+1),x+8,y+20,17,WHITE);}
        if(s.camera==10){button(c,app.controllerInput?(s.quickWind?"RELEASE R2 / OFFICE":"HOLD R2 / WIND"):"HOLD TO WIND MUSIC BOX",35,558,290,62,s.wind);outline(c,35,643,245,23,WHITE);rect(c,38,646,(float)(239*s.musicBox),17,s.musicBox<.2?0xffffcc77:WHITE);}else if(s.musicBox<.2)text(c,"! MUSIC BOX",35,669,25,0xffffcc77);
        text(c,app.controllerInput?(s.quickWind?"R2 RELEASE: OFFICE  /  A OR L2 LIGHT":"X CLOSE  /  A OR L2 FLASHLIGHT"):"HOVER BAR: OFFICE  /  CTRL: FLASHLIGHT",35,733,18,WHITE);staticLines(c,w,.045f);musicWarning(c,w,true);
    }
    void musicWarning(Canvas c,float w,boolean cameraView){int id=app.state.musicWarningImage(cameraView);if(id<0)return;if(cameraView)image(c,id,w-84,384,44,40,false);else image(c,id,w-105,627,70,62,false);}
    void staticLines(Canvas c,float w,float strength){long t=SystemClock.uptimeMillis()/65;for(int i=0;i<32;i++){int y=(int)((i*137+t*31)%768);int alpha=(int)(strength*(40+(i*37+t)%100));rect(c,0,y,w,1,Color.argb(alpha,220,220,210));}}
    void office(Canvas c,float w){Canvas target=app.perspectiveEnabled?perspective.begin(w):c;officeFlat(target,w);if(app.perspectiveEnabled)perspective.finish(c,w);}
    void officeFlat(Canvas c,float w){officeFlat(c,w,app.state);}
    void officeFlat(Canvas c,float w,Fnaf2State s){float offset=(float)((w-1600)*(s.look+1)/2);c.save();c.translate(offset,0);Bitmap background=app.assets.image(s.officeImage());if(background!=null){lastOffice=background;observe(s.officeImage());}if(lastOffice!=null)draw(c,lastOffice,0,0,1600,768,false,.5f);if(s.halloween&&!s.monitor){dreamSprite(c,507,0,0);}if(s.pos[Fnaf2State.TF]==Fnaf2State.OFFICE)image(c,512,814,2,521,768,false);if(s.goldenOffice&&!s.monitor)image(c,520,310,242,505,505,false);if(s.pos[Fnaf2State.MANGLE]==Fnaf2State.CAUGHT&&!s.monitor&&!s.mangleArmed&&s.intruder<0)image(c,121,696,0,486,191,false);if(s.bbInside&&!s.monitor&&s.intruder<0)image(c,221,259,256,379,442,false);if(!s.monitor&&s.intruder<0){if(s.paperOffice)dreamSprite(c,326,1024,130);if(s.randomImage==9)dreamSprite(c,681,1022,645);if(s.shadowBonnie)dreamSprite(c,518,253,-3);}if(s.toyWalk.active)dreamSprite(c,187,s.toyWalk.x-266,s.toyWalk.y-513);image(c,new int[]{293,291,296,292}[(s.ticks/2)%4],568,332,851,435,false);int[] xs={712,812,740,876,1282},ys={554,558,584,592,600},ww={113,116,131,133,129},hh={149,173,144,154,156};int rewardMask=((app.challengeEarned&64)!=0?1:0)|((app.challengeEarned&4)!=0?2:0)|((app.challengeEarned&32)!=0?4:0)|((app.challengeEarned&16)!=0?8:0)|((app.challengeEarned&512)!=0?16:0);for(int i=0;i<5;i++)if(((app.equipped|rewardMask)&(1<<i))!=0)image(c,MainActivity.PLUSH_IMAGES[i],xs[i]-ww[i]/2f,ys[i]-hh[i]/2f,ww[i],hh[i],false);if((app.challengeEarned&2)!=0)dreamSprite(c,608,1038,476);if((app.challengeEarned&8)!=0)dreamSprite(c,610,991,517);if((app.challengeEarned&128)!=0)dreamSprite(c,555,1122,509);if((app.challengeEarned&256)!=0)dreamSprite(c,612,870,613);if(s.halloween&&!s.monitor)dreamSprite(c,503,730,531);c.restore();}
    @Override protected void onDetachedFromWindow(){perspective.release();super.onDetachedFromWindow();}
    final InputProfile.Hover pointerHover=new InputProfile.Hover();
    void clearPointer(){held=-1;pointerHover.reset();}
    boolean pcGameplay(){return app.inputProfile.mode==InputProfile.PC&&app.state.playing&&app.state.live()&&app.intro==0&&app.ending==0&&!app.minigame.active&&!app.dream.active();}
    boolean pcSingle(){return app.bottom==null&&app.inputProfile.mode==InputProfile.PC&&app.assets.ready&&app.state.playing&&app.intro==0&&app.ending==0&&!app.minigame.active&&!app.dream.active();}
    boolean pcUpper(){return !app.state.monitor||!app.state.live();}
    int pointerBar(float x,float y,float w){return y>=691&&y<765?(x>=w*.484&&x<w?1:!app.state.monitor&&x>=0&&x<w*.479?0:-1):-1;}
    void pointerBars(Canvas c,float w){
        pointerBar(c,w*496/1024,691,w*544/1024,0xffbcbcbc);
        if(!app.state.monitor)pointerBar(c,-w*54/1024,693,w*544/1024,0xffbb4b4b);
    }
    void pointerBar(Canvas c,float x,float y,float width,int color){
        rect(c,x,y,width,74,0x44000000);outline(c,x+3,y+3,width-6,68,color);
        p.setColor(color);p.setStrokeWidth(4);
        for(float f:new float[]{.32f,.68f}){float cx=x+width*f,cy=y+34;c.drawLine(cx-14,cy,cx,cy+13,p);c.drawLine(cx,cy+13,cx+14,cy,p);}
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e){
        if((e.getSource()&InputDevice.SOURCE_MOUSE)!=InputDevice.SOURCE_MOUSE)return super.onGenericMotionEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){pointerHover.reset();if(app.inputProfile.mode==InputProfile.PC)app.state.stick=0;return true;}
        if(app.inputProfile.mouseMoved(e.getX(),e.getY()))app.useInput(InputProfile.PC);
        if(!pcGameplay()){pointerHover.reset();return true;}
        float h=pcSingle()||app.bottom!=null?getHeight():getHeight()/2f,scale=h/768,w=getWidth()/scale,x=e.getX()/scale,y=e.getY()/scale;
        boolean up=pcSingle()?pcUpper():app.bottom==null?e.getY()<h:upper;
        if(app.bottom==null&&!pcSingle()&&!up)y-=768;
        int bar=up||app.state.monitor?pointerBar(x,y,w):-1;if(pointerHover.enter(bar)){if(bar==1)app.monitor();else app.mask();}
        app.state.stick=up&&!app.state.monitor&&!app.state.paused?InputProfile.officeAxis(2,x/w,(float)app.state.lookSensitivity):0;
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        boolean mouse=(e.getSource()&InputDevice.SOURCE_MOUSE)==InputDevice.SOURCE_MOUSE;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)app.useInput(mouse?InputProfile.PC:InputProfile.TOUCH);
        float h=pcSingle()||app.bottom!=null?getHeight():getHeight()/2f;float offset=app.bottom==null&&!pcSingle()&&e.getY()>=h?h:0;boolean up=pcSingle()?pcUpper():app.bottom==null?offset==0:upper;float scale=h/768,w=getWidth()/scale,x=e.getX()/scale,y=(e.getY()-offset)/scale;
        boolean down=e.getActionMasked()==MotionEvent.ACTION_DOWN,release=e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL;

        if(app.minigame.active){DeathMinigame m=app.minigame;if(release){m.left=m.right=m.up=m.down=false;return true;}if(down&&m.paused){app.back();return true;}if(!up&&!m.paused){float dx=x-w/2,dy=y-405;m.left=Math.abs(dy)<48&&dx< -55&&dx> -155;m.right=Math.abs(dy)<48&&dx>55&&dx<155;m.up=Math.abs(dx)<48&&dy< -55&&dy> -155;m.down=Math.abs(dx)<48&&dy>55&&dy<155;}return true;}
        if(app.dream.active()){app.dream.pan=release?0:x<w*.35?-1:x>w*.65?1:0;return true;}
        Fnaf2State s=app.state;if(release){if(held==1)s.setWind(false);if(held==2)s.setFlashlight(false);if(held==3)s.setVent(true,false);if(held==4)s.setVent(false,false);held=-1;return true;}
        if(mouse&&e.getActionMasked()==MotionEvent.ACTION_MOVE){
            onGenericMotionEvent(e);
            float wx=x-(float)((w-1600)*(s.look+1)/2),wy=app.perspectiveEnabled?384+(y-384)*(768-200*(1-(float)Math.sin(Math.PI*x/w)))/768:y;
            if(held==1)s.setWind(!up&&x<300&&y>550&&y<690);
            if(held==3)s.setVent(true,up&&wx>=105&&wx<197&&wy>=361&&wy<502);
            if(held==4)s.setVent(false,up&&wx>=1395&&wx<1487&&wy>=359&&wy<500);
            return true;
        }
        if(!down)return true;
        if(!app.assets.ready){app.importData();return true;}if(app.intro>0||app.ending>0)return true;
        if(!up&&useMonitorShell()){float[] point=monitorShell.source(x,y,w);if(point==null)return true;x=point[0];y=point[1];}
        if(!s.playing)return menus.touch(up,x,y,w);
        if(s.paused){s.paused=false;app.resetControls();app.audio.pause(false);return true;}
        if(!s.live())return true;
        if(mouse&&app.audio.channels.containsKey("phone")&&x>=172&&x<293&&y>=42&&y<73){app.audio.stop("phone");return true;}
        if(mouse&&(up||s.monitor)&&pointerBar(x,y,w)>=0){int bar=pointerBar(x,y,w);if(pointerHover.enter(bar)){if(bar==1)app.monitor();else app.mask();}return true;}
        if(up){float worldX=x-(float)((w-1600)*(s.look+1)/2),worldY=app.perspectiveEnabled?384+(y-384)*(768-200*(1-(float)Math.sin(Math.PI*x/w)))/768:y;if(!s.monitor&&!s.mask&&Math.abs(worldX-153)<=8&&Math.abs(worldY-167)<=8){app.audio.play("honk","PartyFavorraspyPart_AC01__3",false,1);return true;}if(mouse?worldX>=105&&worldX<197&&worldY>=361&&worldY<502:x<w*.18){s.setVent(true,true);held=3;}else if(mouse?worldX>=1395&&worldX<1487&&worldY>=359&&worldY<500:x>w*.82){s.setVent(false,true);held=4;}else if(!mouse){s.setFlashlight(true);held=2;}return true;}
        if(!s.monitor){desk.touch(x,y,w);return true;}
        float mx=w-380,my=435;for(int i=0;i<12;i++){float bx=mx+(MAP[i][0]-545)/416f*340,by=my+(MAP[i][1]-347)/312f*255;if(Math.abs(x-bx)<30&&Math.abs(y-by)<22){app.camera(i);return true;}}
        if(s.camera==10&&x<300&&y>550&&y<690){s.setWind(true);held=1;}else if(y>710)app.monitor();else if(!mouse){s.setFlashlight(true);held=2;}return true;
    }
}
