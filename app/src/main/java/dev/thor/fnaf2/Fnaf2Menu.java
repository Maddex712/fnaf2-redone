package dev.thor.fnaf2;

import android.graphics.Canvas;
import android.os.SystemClock;
import java.util.Locale;

/** Shared visual language with the first Thor game; FNaF 2 records and controls. */
final class Fnaf2Menu {
    final Fnaf2View v;final MainActivity a;boolean titleText;
    DeathMinigame arcadePreview;int arcadePreviewIndex=-1;
    final Fnaf2State previewState=new Fnaf2State();
    static final int WHITE=0xffeeeeea,MUTED=0xffb9b9b9,GREEN=0xff96c149;
    Fnaf2Menu(Fnaf2View view){v=view;a=view.app;previewState.start(1);java.util.Arrays.fill(previewState.pos,-1);}
    void t(Canvas c,String s,float x,float y,float size,int color){if(titleText){v.p.setStyle(android.graphics.Paint.Style.FILL);v.p.setTypeface(android.graphics.Typeface.MONOSPACE);v.p.setTextSize(size);v.p.setColor(color);c.drawText(s,x,y,v.p);}else v.text(c,s,x,y,size,color);}
    void box(Canvas c,String s,float x,float y,float w,float h,boolean active){v.rect(c,x,y,w,h,active?0xff4c4c47:0xff242424);v.outline(c,x,y,w,h,active?0xffd0d0c8:0xff777777);t(c,s,x+12,y+h*.65f,21,WHITE);}
    String points(){return String.format(Locale.US,"%,d",a.points);}
    String[] rows(){return a.menu();}
    int nextAchievement(){for(int i=0;i<6;i++)if((a.earned&(1<<i))==0)return i;return -1;}
    int nextChallenge(){for(int i=0;i<10;i++)if((a.challengeEarned&(1<<i))==0)return i;return 9;}
    void top(Canvas c,float w){
        if(a.page==7){arcade(c,w);return;}if(a.page==8){encounters(c,w);return;}
        if(a.page==5){if(perspectivePreviewActive())perspectivePreview(c,w,SystemClock.uptimeMillis());else{v.menuBackdrop.draw(c,a.assets,w,SystemClock.uptimeMillis());v.rect(c,0,0,w,768,0xc8000000);}settingsTop(c,w);return;}
        long now=SystemClock.uptimeMillis();v.menuBackdrop.draw(c,a.assets,w,now);
        v.menuBackdrop.finish(c,a.assets,w,now);
        c.save();c.scale(w/1000f,768f/650f);titleText=true;
        android.graphics.Bitmap title=a.assets.image(469);if(title!=null){v.p.setColor(0xffffffff);c.drawBitmap(title,null,new android.graphics.RectF(42,35,220,228),v.p);}
        if(a.page!=0)v.rect(c,0,0,1000,650,0x8a000000);
        if(a.page==0){String[] labels=rows();for(int i=0;i<labels.length;i++){float baseline=245+i*39+39*.7f;t(c,i==a.selected?">>":"",40,baseline,26,0xffffffff);t(c,labels[i],88,baseline,26,0xffffffff);}}
        else{
            String heading=a.page==1?"ACHIEVEMENTS":a.page==2?"OFFICE SHOP":a.page==6?"CUSTOM NIGHT":a.page==4?"CHALLENGES":a.page==5?"SETTINGS":"CONTROLS";
            t(c,heading,44,65,30,0xffffffff);
            t(c,a.page==2?MainActivity.PLUSH_NAMES[a.shopSelection]:a.page==4?Fnaf2State.CHALLENGES[a.challengeSelection]:"FAZBEAR SHIFT MANAGER",45,285,26,WHITE);
            t(c,"Use the lower terminal",45,345,22,MUTED);t(c,"B  Back to title",45,395,22,MUTED);
            if(a.page==2)t(c,"Balance: "+points()+" pts",45,455,22,GREEN);
        }
        boolean[] stars={(a.earned&16)!=0,(a.earned&32)!=0,a.beat20};for(int i=0;i<3;i++)if(stars[i])t(c,"*",48+i*37,633,28,0xffffffff);
        titleText=false;c.restore();
    }
    boolean perspectivePreviewActive(){return a.page==5&&a.settingSelection==0;}
    void perspectivePreview(Canvas c,float w,long now){
        previewState.look=.7*Math.sin(now/6500.0);previewState.ticks=(int)((now/17)%240);
        v.menuBackdrop.update(now);
        Canvas target=a.perspectiveEnabled?v.perspective.begin(w):c;
        v.officeFlat(target,w,previewState);
        // Only this preview bends static together with the scenery.
        v.menuBackdrop.finish(target,a.assets,w,now);
        if(a.perspectiveEnabled)v.perspective.finish(c,w);
        v.rect(c,30,28,470,86,0xb8000000);
        t(c,"PERSPECTIVE / "+(a.perspectiveEnabled?"ON":"OFF"),48,66,28,WHITE);
        t(c,"LIVE PREVIEW",48,96,18,MUTED);
    }
    void bottom(Canvas c,float w){
        v.rect(c,14,10,w-28,744,0xff202020);v.outline(c,14,10,w-28,744,0xff555555);v.outline(c,25,21,w-50,722,0xff090909);
        v.rect(c,36,32,w-72,42,0xff393939);t(c,"FAZBEAR SHIFT MANAGER",50,61,23,WHITE);t(c,"11/1987",w-140,61,21,WHITE);
        t(c,"FAZBEAR ENTERTAINMENT",50,103,22,WHITE);t(c,"NIGHT OPERATIONS / SECURITY TERMINAL",50,125,17,MUTED);
        v.rect(c,36,134,w-72,596,0xff161616);v.outline(c,36,134,w-72,596,0xff8a8a8a);
        String[] tabs={"SHIFTS","RECORDS","ASSIGNMENTS","DESK"};int active=a.page==1?1:a.page==4?2:a.page==2?3:0;float tw=(w-76)/4;
        for(int i=0;i<4;i++){v.rect(c,38+i*tw,136,tw,30,i==active?0xff4c4c47:0xff303030);t(c,tabs[i],48+i*tw,158,18,WHITE);}
        if(a.page==0)home(c,w);else if(a.page==1)records(c,w);else if(a.page==2)shop(c,w);else if(a.page==4)challenge(c,w);else if(a.page==6)customAI(c,w);else if(a.page==7){t(c,"ARCADE",52,218,30,WHITE);t(c,Integer.bitCount(a.arcadeUnlocked)+" / 4 discovered",52,280,23,GREEN);t(c,"D-pad: cabinet / A: play",52,360,22,MUTED);t(c,"Unlock through normal play",52,410,22,MUTED);}else if(a.page==8){t(c,"INCIDENT ARCHIVE",52,218,30,WHITE);t(c,a.encounters.count()+" sightings documented",52,280,23,GREEN);t(c,"D-pad: browse / B: back",52,360,22,MUTED);}else if(a.page==5)settings(c,w);else controls(c,w);
        v.menuBackdrop.terminal(c,w,SystemClock.uptimeMillis());
    }
    void home(Canvas c,float w){
        t(c,"CHOOSE YOUR SHIFT",52,195,18,MUTED);float cell=(w-104)/6;
        for(int i=0;i<6;i++){float x=52+i*cell;boolean unlocked=i<a.unlocked;v.rect(c,x,207,cell-10,48,a.state.night==i+1?0xff55554b:0xff242424);v.outline(c,x,207,cell-10,48,unlocked?0xffcccccc:0xff444444);t(c,"N"+(i+1),x+13,238,24,unlocked?WHITE:0xff666666);}
        box(c,"START NIGHT "+a.state.night,52,273,w-104,52,true);
        int n=nextAchievement();t(c,"NEXT ACHIEVEMENT",52,362,18,MUTED);t(c,n<0?"All night achievements complete":"Survive Night "+(n+1)+"   +"+((n+1)*100)+" pts",52,397,25,WHITE);t(c,"View your personnel record  >",52,425,19,MUTED);
        v.rect(c,52,442,w-104,1,0xff555555);int ch=nextChallenge();t(c,"SPECIAL ASSIGNMENT",52,470,18,MUTED);t(c,Fnaf2State.CHALLENGES[ch]+"   +"+MainActivity.challengeReward(ch)+" pts",52,503,25,WHITE);
        t(c,"YOUR DESK / TAP TO MANAGE",52,548,18,MUTED);float cell5=(w-104)/5;
        for(int i=0;i<5;i++){float x=52+i*cell5;v.outline(c,x,562,cell5-9,104,(a.equipped&(1<<i))!=0?0xffb9b6a1:0xff555555);v.image(c,MainActivity.PLUSH_IMAGES[i],x+10,569,cell5-29,67,false);String label=(a.owned&(1<<i))==0?MainActivity.PRICES[i]+" pts":(a.equipped&(1<<i))!=0?"ON DESK":"STORED";t(c,label,x+10,653,16,MUTED);}
        box(c,"SHOP / "+points()+" pts",52,686,(w-120)*.65f,32,false);box(c,"SETTINGS",68+(w-120)*.65f,686,(w-120)*.35f,32,false);
    }
    void records(Canvas c,float w){
        t(c,"PERSONNEL RECORD",52,207,28,WHITE);t(c,"Saved on this device / "+points()+" pts",52,240,19,MUTED);
        for(int i=0;i<6;i++){boolean done=(a.earned&(1<<i))!=0;t(c,done?"[X]":"[ ]",52,298+i*49,24,done?GREEN:MUTED);t(c,"Survive Night "+(i+1),112,298+i*49,24,WHITE);t(c,"+"+(i+1)*100+" pts",w-193,298+i*49,22,done?GREEN:MUTED);}
        t(c,"Repeat shifts: 25 pts per night level",52,621,19,MUTED);box(c,"B  BACK",52,681,w-104,36,false);
    }
    void shop(Canvas c,float w){
        int i=a.shopSelection,flag=1<<i;t(c,"OFFICE COLLECTION",52,207,28,WHITE);t(c,"BALANCE / "+points()+" pts",52,240,20,MUTED);
        v.image(c,MainActivity.PLUSH_IMAGES[i],w*.3f,259,w*.4f,235,false);t(c,MainActivity.PLUSH_NAMES[i],52,533,31,WHITE);
        box(c,(a.owned&flag)==0?"A  BUY / "+MainActivity.PRICES[i]+" pts":(a.equipped&flag)!=0?"A  STORE PLUSH":"A  PLACE ON DESK",52,558,w-104,53,true);
        t(c,a.notice,52,646,18,MUTED);footer(c,w);
    }
    void challenge(Canvas c,float w){
        int i=a.challengeSelection;t(c,"SPECIAL ASSIGNMENT",52,207,26,WHITE);t(c,Fnaf2State.CHALLENGES[i],52,252,31,WHITE);
        t(c,((a.challengeEarned&(1<<i))!=0?"COMPLETED":"NOT YET COMPLETED")+" / +"+MainActivity.challengeReward(i)+" pts",52,290,20,GREEN);
        String[] who={"Freddy","Bonnie","Chica","Foxy","Toy Freddy","Toy Bonnie","Toy Chica","Mangle","Balloon Boy","Golden Freddy"};
        for(int j=0;j<10;j++){float x=52+(j/5)*(w-104)/2;t(c,who[j],x,346+(j%5)*42,21,WHITE);t(c,"AI "+Fnaf2State.PRESETS[i][j],x+(w-104)/2-81,346+(j%5)*42,20,MUTED);}
        box(c,(a.earned&32)==0?"Unlocks after completing Night 6":"A  START ASSIGNMENT",52,558,w-104,53,(a.earned&32)!=0);t(c,"Y  EDIT INDIVIDUAL AI",52,646,20,MUTED);footer(c,w);
    }
    void customAI(Canvas c,float w){
        t(c,"CUSTOM NIGHT / 0-20",52,207,27,WHITE);
        String[] names={"Freddy","Bonnie","Chica","Foxy","Toy Freddy","Toy Bonnie","Toy Chica","Mangle","Balloon Boy","Golden Freddy"};
        for(int i=0;i<10;i++){float y=243+i*37;box(c,(a.aiSelection==i?"> ":"  ")+names[i],52,y-24,w-230,33,a.aiSelection==i);box(c,"-",w-168,y-24,32,33,false);t(c,""+a.editedAI[i],w-125,y,22,WHITE);box(c,"+",w-84,y-24,32,33,false);}
        box(c,(a.earned&32)==0?"LOCKED / COMPLETE NIGHT 6":"A  START CUSTOM NIGHT",52,622,w-104,45,a.aiSelection==10);
        t(c,"UP/DOWN: select   LEFT/RIGHT: adjust   B: back",52,706,17,MUTED);
    }
    void footer(Canvas c,float w){float third=(w-128)/3;box(c,"< PREVIOUS",52,681,third,36,false);box(c,"NEXT >",64+third,681,third,36,false);box(c,"B  BACK",76+third*2,681,third,36,false);}
    String[] settingRows(){return new String[]{"Perspective: "+(a.perspectiveEnabled?"On":"Off"),"Controls","Scan Steam file","Office sensitivity: "+MainActivity.SPEED_NAMES[a.lookSetting],"Camera sensitivity: "+MainActivity.SPEED_NAMES[a.cameraSetting],"GitHub project","Rare encounters","Arcade","Halloween mode: "+new String[]{"Auto","On","Off"}[a.state.halloweenMode]};}
    void arcade(Canvas c,float w){
        v.rect(c,0,0,w,768,0xff080a0c);t(c,"ARCADE",45,62,34,WHITE);int selected=a.arcadeSelection;boolean unlocked=(a.arcadeUnlocked&(1<<selected))!=0;
        float left=(w-650)/2;v.rect(c,left+13,101,658,638,0xff020302);v.rect(c,left,90,650,640,0xff302a24);
        v.rect(c,left,90,14,640,0xff685841);v.rect(c,left+636,90,14,640,0xff171710);v.rect(c,left+14,90,622,4,0xff8b7960);
        v.rect(c,left+20,108,610,66,0xff0a0b08);v.rect(c,left+20,101,610,66,0xff554b35);t(c,unlocked?MainActivity.ARCADE_NAMES[selected]:"[REDACTED]",left+38,145,25,0xffcec6a3);v.arcadeWear(c,left+22,103,606,60,41);
        v.rect(c,left+29,187,592,445,0xff585548);v.rect(c,left+36,194,578,431,0xff141811);v.rect(c,left+43,201,564,417,0xff020302);
        if(unlocked&&a.arcadeReady&&a.minigameArt!=null){
            if(arcadePreviewIndex!=selected){arcadePreview=new DeathMinigame(new java.util.Random(1987));arcadePreviewIndex=selected;}DeathMinigame preview=arcadePreview;preview.turn=MainActivity.ARCADE_KINDS[selected];preview.beginGame();preview.ticks=180;
            if(preview.kind==DeathMinigame.GIFTS){java.util.Arrays.fill(preview.gifts,true);java.util.Arrays.fill(preview.heads,true);preview.score=8;preview.phase=1;}
            else if(preview.kind==DeathMinigame.CAKE){preview.man=true;preview.manSeconds=5;preview.carSteps=15;preview.manStart=-500;preview.carX=430;}
            else if(preview.kind==DeathMinigame.FOXY){preview.phase=2;preview.cycles=3;preview.x=1500;}
            else{preview.man=true;preview.manX=650;preview.h=4;preview.v=4;}
            c.save();c.clipRect(left+43,201,left+607,618);c.translate(left+43,201);c.scale(564f/1024,417f/768);a.minigameArt.draw(c,preview,1024,v);c.restore();
        }else t(c,unlocked?"LOADING SCENE...":"DISCOVER DURING A NORMAL SHIFT",left+57,407,23,MUTED);
        v.rect(c,left+16,643,618,73,0xff10120d);v.rect(c,left+16,635,618,73,0xff514c3f);v.rect(c,left+16,635,618,3,0xff8f8369);v.arcadeWear(c,left+20,642,610,61,55);
        v.rect(c,left+38,646,18,30,0xff151515);v.p.setColor(0xff9e4938);c.drawCircle(left+47,644,13,v.p);
        v.p.setColor(0xffaaa16b);c.drawCircle(left+346,668,11,v.p);v.p.setColor(0xff803d35);c.drawCircle(left+380,668,11,v.p);
        t(c,unlocked?"A  PLAY":"LOCKED",left+78,683,26,GREEN);t(c,"<  "+(selected+1)+" / 4  >",left+430,683,22,WHITE);
    }
    void encounters(Canvas c,float w){
        v.rect(c,0,0,w,768,0xff101410);t(c,"RARE ENCOUNTERS",45,70,32,WHITE);int rank=a.encounterSelection,i=EncounterCatalog.ORDER[rank];
        t(c,a.encounters.count()+" / "+EncounterCatalog.TITLES.length+" discovered",45,114,21,GREEN);
        t(c,"#"+(rank+1)+"  "+(a.encounters.seen(i)?EncounterCatalog.TITLES[i]:"[UNDISCOVERED]"),45,170,28,WHITE);
        t(c,EncounterCatalog.ODDS[i],45,205,17,GREEN);
        android.graphics.Bitmap image=a.encounters.preview(i);if(image!=null)v.draw(c,image,45,225,w-90,410,false,.5f);else{v.outline(c,45,225,w-90,410,MUTED);t(c,"NO SIGHTING RECORDED",90,425,25,MUTED);}
        if(a.encounters.seen(i))t(c,a.encounters.detail(i),45,680,17,MUTED);
        t(c,"<  D-pad: browse     B: back  >",45,719,22,WHITE);
        t(c,"Base-roll order; conditions differ. Not a per-night chance.",45,752,15,MUTED);
    }
    void settingsTop(Canvas c,float w){
        c.save();c.scale(w/1000f,768f/650f);v.rect(c,20,18,650,612,0xcc080a09);titleText=true;
        t(c,"OPTIONS",44,70,30,WHITE);
        String[] rows=settingRows();for(int i=0;i<rows.length;i++){t(c,i==a.settingSelection?">>":"",40,145+i*43,26,WHITE);t(c,rows[i],88,145+i*43,25,WHITE);}
        t(c,"B  Back",45,550,23,MUTED);t(c,"FNaF 2 Redone / development build",45,598,16,MUTED);titleText=false;c.restore();
    }
    void settings(Canvas c,float w){
        String[] help={"Preview the office perspective on the top screen. This affects presentation only.","View controller bindings for cameras, flashlight, mask, and winding the music box.","Select your Steam EXE or an existing data ZIP. Setup runs offline on this device.","Adjust left-stick office movement. Looking stays locked while the camera monitor is open.","Adjust right-stick camera panning. This changes no enemy behavior or music-box timing.","Open the project page in your browser.","Review first-sighting screenshots captured only when a rare event is actually rendered.","Replay discovered minigames. Undiscovered cabinets remain redacted until a natural encounter.","Office decorations only. Auto follows October 31; On or Off overrides the date without changing AI or difficulty."};
        t(c,"OPERATOR SETTINGS",52,218,29,WHITE);t(c,settingRows()[a.settingSelection],52,270,23,GREEN);
        String[] words=help[a.settingSelection].split(" ");String row="";int line=0;for(String word:words){if((row+word).length()>42){t(c,row,52,330+line++*35,22,MUTED);row="";}row+=word+" ";}t(c,row,52,330+line*35,22,MUTED);
        t(c,"DISPLAY / "+a.top.getWidth()+" x "+a.top.getHeight(),52,551,21,MUTED);
        t(c,a.bottom==null?"ONE DISPLAY":"DUAL DISPLAY CONNECTED",52,589,21,GREEN);
        t(c,"D-pad: navigate / A: select / B: back",52,668,19,MUTED);
    }
    void controls(Canvas c,float w){
        t(c,"SECURITY DESK",52,207,28,WHITE);String[] lines=a.inputProfile.mode==InputProfile.PC?new String[]{"MOUSE  Pan at office edges","HOVER WHITE BAR  Raise / lower monitor","HOVER RED BAR  Put on / remove mask","CLICK + HOLD  Vent lights","CTRL  Flashlight (office / cameras)","CLICK  Select camera","CLICK + HOLD  Wind music box","CLICK MUTE CALL  Silence phone","ESC  Pause / resume"}:new String[]{"X  Raise / lower monitor","B  Put on / remove Freddy mask","L1 / R1  Left / right vent lights","A  Flashlight / L2 also lights cameras","R2  Hold: CAM 11 + wind; release: office","D-PAD  Select camera","LEFT STICK  Look around office","RIGHT STICK  Pan camera view","Y  Mute phone call","START  Pause"};for(int i=0;i<lines.length;i++)t(c,lines[i],52,253+i*39,22,MUTED);box(c,"B  BACK",52,681,w-104,36,false);
    }
    boolean touch(boolean up,float x,float y,float w){
        if(up&&a.page==7){if(y>630)a.confirm();else a.navigate(x<w/2?-1:1);return true;}if(up&&a.page==8){a.navigate(x<w/2?-1:1);return true;}if(up){float ux=x*1000/w,uy=y*650/768;if(a.page==5){if(uy>=108&&uy<108+9*43){a.settingSelection=Math.min(8,(int)((uy-108)/43));a.confirm();}else if(uy>=510)a.page=0;return true;}if(a.page==0&&ux>=40&&ux<690&&uy>=245&&uy<245+rows().length*39){a.selected=(int)((uy-245)/39);a.confirm();}else if(a.page!=0){a.page=a.page==3?5:0;}return true;}
        if(x<36||x>w-36)return true;
        if(y>=134&&y<171){int[] pages={0,1,4,2};a.page=pages[Math.min(3,(int)((x-36)/((w-72)/4)))];a.notice="";return true;}
        if(a.page==0){
            if(y>=207&&y<255){int night=1+(int)((x-52)/((w-104)/6));if(night>=1&&night<=a.unlocked)a.state.night=night;}
            else if(y>=273&&y<325){a.selected=0;a.confirm();}
            else if(y>=347&&y<442)a.page=1;
            else if(y>=450&&y<519){a.challengeSelection=nextChallenge();a.page=4;}
            else if(y>=562&&y<666){a.shopSelection=Math.max(0,Math.min(4,(int)((x-52)/((w-104)/5))));int flag=1<<a.shopSelection;if((a.owned&flag)!=0){a.equipped^=flag;a.save();a.notice=(a.equipped&flag)!=0?"Placed on desk":"Stored";}else{a.notice="Purchase this plush in Office Shop";}}
            else if(y>=681){a.page=x<w*.68f?2:5;a.settingSelection=0;}
        }else if(a.page==6){if(y>=219&&y<589){a.aiSelection=Math.min(9,(int)((y-219)/37));if(x>=w-168)a.adjustAI(x<w-100?-1:1);}else if(y>=622&&y<667){a.aiSelection=10;a.confirm();}else if(y>=681)a.page=4;}else if(a.page==5){if(y>=681)a.page=0;}else if(a.page==2||a.page==4){if(y>=558&&y<611)a.confirm();else if(a.page==4&&y>=621&&y<669)a.editAI();else if(y>=681){float third=(w-128)/3;if(x<52+third)a.navigate(-1);else if(x<64+2*third)a.navigate(1);else a.page=0;}}
        else if(y>=681)a.page=a.page==3?5:0;
        return true;
    }
}
