package dev.thor.fnaf2;

import java.util.*;

/** Frame counters and elapsed-time event timers for implementations of the four post-death scenes. Scene coordinates stay 1024x768. */
final class DeathMinigame {
    static final int SAVE=1,CAKE=2,GIFTS=3,FOXY=5;
    interface Collision { boolean overlaps(Sprite a,Sprite b); }
    static final class Sprite {
        final int id,x,y;final boolean backdrop,solid;
        Sprite(int id,int x,int y,boolean backdrop,boolean solid){this.id=id;this.x=x;this.y=y;this.backdrop=backdrop;this.solid=solid;}
        int left(){return x-(backdrop?0:MinigameSprites.info(id)[3]);}
        int top(){return y-(backdrop?0:MinigameSprites.info(id)[4]);}
    }
    static final class Cue {final String name;final float volume;Cue(String n,float v){name=n;volume=v;}}
    final EventTimers eventTimers=new EventTimers();double frameDelta=1.0/60,sceneTime;boolean cakeSlowing,partyActive,foxyReset,pendingMan;int foxyWait;
    final Random random;final List<Cue> cues=new ArrayList<>();
    Collision collision=DeathMinigame::boundsOverlap;
    boolean active,paused,ready,turnChanged,gameStarted,finished,controls=true,left,right,up,down,stickLeft,stickRight,stickUp,stickDown;
    int barTicks,barX,barY1,barY2,lastSpoken,pendingAttacker=-1;
    int turn,kind,ticks,loadTicks,endTicks,attacker=-1,x,y,dir,timer,slow,voiceTicks,voice,score,phase,phaseSeconds,cycles;
    int heX,heStep,heDir;boolean goldenTouched;
    int h,v,pupp,golden,goldenTicks,manX,phantomX,phantomY,phantomDir,phantomSteps,visited,roomTime,carSteps,carX,manSeconds,manStart;
    int[] hunger=new int[6],hungerAge=new int[6],pops=new int[9];boolean[] gifts=new boolean[4],heads=new boolean[4];
    boolean man,carLeaving,feedingStopped,firstInput;double accumulator,attackTime;int attackAnimationUnits;
    static final int[][] KIDS={{290,280},{290,416},{290,548},{776,278},{770,410},{766,538}};
    static final int[][] HEADS={{728,235,204},{763,200,594},{762,814,190},{765,818,580}};
    static final int[][] CHILDREN={{228,239},{204,635},{830,239},{830,623}};
    static final int[][] FIREWORKS={{1210,280},{1368,200},{1494,282},{1762,208},{1134,208},{1360,384},{1678,366},{1142,360},{1838,394}};
    static final int[] POP={797,789,798,796,786,788,795,793};
    DeathMinigame(){this(new SourceRandom());}DeathMinigame(Random r){random=r;}
    static int nextTurn(int old){return old>=5?1:old+1;}
    void start(int old){if(random instanceof SourceRandom)((SourceRandom)random).beginScene();active=true;paused=ready=turnChanged=gameStarted=finished=false;turn=Math.max(0,Math.min(5,old));loadTicks=ticks=endTicks=0;accumulator=attackTime=0;attacker=-1;barTicks=0;barX=-17;barY1=88;barY2=212;clearInputs();cues.clear();}
    void clearInputs(){left=right=up=down=stickLeft=stickRight=stickUp=stickDown=false;}
    void beginGame(){
        if(random instanceof SourceRandom)((SourceRandom)random).beginScene();
        kind=turn==4?SAVE:turn;gameStarted=true;ticks=timer=slow=voiceTicks=voice=score=phase=phaseSeconds=cycles=0;dir=0;controls=true;firstInput=false;
        attacker=pendingAttacker=-1;lastSpoken=0;attackTime=0;attackAnimationUnits=0;endTicks=0;man=carLeaving=feedingStopped=false;carSteps=manSeconds=manStart=0;carX=1220;eventTimers.reset();sceneTime=0;cakeSlowing=partyActive=false;foxyReset=true;foxyWait=0;
        Arrays.fill(gifts,false);Arrays.fill(heads,false);Arrays.fill(pops,-1);Arrays.fill(hungerAge,0);
        if(kind==SAVE){heX=447;heStep=heDir=0;h=1;v=2;x=503;y=365;pupp=visited=0;random.nextInt(100);int spawn=random.nextInt(4);if(spawn==1){h=1;v=3;pupp=1;x=638;y=396;}else if(spawn==2){h=3;v=4;pupp=2;x=388;y=396;}else if(spawn==3){h=2;v=5;pupp=2;}enterRoom(false);}
        else if(kind==CAKE){x=533;y=459;for(int i=5;i>=0;i--)hunger[i]=random.nextInt(10);}
        else if(kind==GIFTS){x=519;y=461;}else{x=530;y=274;}
    }
    void tick(double dt){if(!active||paused||!ready)return;accumulator+=Math.max(0,dt);if(accumulator+1e-10>=1.0/60&&!finished){double frame=accumulator;accumulator=0;step(frame);}}
    void step(){step(1.0/60);}
    boolean every(int event,double interval){return eventTimers.every(event,interval,frameDelta);}
    void step(double dt){
        frameDelta=Math.max(0,dt);
        if(!gameStarted){bars();loadTicks++;if(loadTicks==60){turn=nextTurn(turn);turnChanged=true;}if(loadTicks>=120)beginGame();return;}
        if(endTicks>0){bars();if(--endTicks==0)finished=true;return;}ticks++;sceneTime+=frameDelta;
        if(attacker>=0){attackTime+=frameDelta;attackAnimationUnits+=(int)(Fnaf2Catalog.SPEED[attacker]*frameDelta*60+1e-7);}
        if(pendingAttacker>=0){attacker=pendingAttacker;pendingAttacker=-1;attackTime=0;attackAnimationUnits=0;}
        if(kind==SAVE)saveStep();else {timer++;if(timer>=10+slow){timer=0;if(kind==CAKE)slow++;if(kind!=FOXY||phase==1)move();}if(kind==CAKE)cakeStep();else if(kind==GIFTS)giftStep();else foxyStep();}
        if(kind==CAKE)voiceStep();
        if(attacker>=0&&attackAnimationUnits>Fnaf2Catalog.SCARE[attacker].length*100){attacker=-1;end(false);}
    }
    void voiceStep(){
        String word=kind==SAVE?"SAVETHEM":kind==CAKE?"SAVEHIM":"HELPTHEM";
        if(kind==SAVE&&every(0,3))voice++;
        if(voice>=1&&voice<=word.length()&&voice!=lastSpoken){
            float volume=kind==SAVE?.5f:kind==CAKE?Math.max(0,.5f-manSeconds*.02f):score<5?.5f:score==5?.4f:score==6?.3f:.15f;
            cues.add(new Cue(word.charAt(voice-1)+"2",volume));lastSpoken=voice;
        }
        if(voice>word.length())voice=1;
        if(kind!=SAVE&&++voiceTicks>=100+slow*(kind==GIFTS?10:1)){voiceTicks=0;voice++;}
    }
    void attack(int who){if(attacker<0&&pendingAttacker<0)pendingAttacker=who;}
    double scareAnimationTime(){return attacker<0?0:Math.max(0,attackAnimationUnits-1)/(Fnaf2Catalog.SPEED[attacker]*60.0);}
    void bars(){if(++barTicks%6==0){barX=-26;barY1=46+52*random.nextInt(14);barY2=46+52*random.nextInt(14);}}
    void end(boolean purple){if(random instanceof SourceRandom)((SourceRandom)random).beginScene();barTicks=0;barX=-17;barY1=88;barY2=212;endTicks=purple?60:120;clearInputs();man=purple;}
    static boolean boundsOverlap(Sprite a,Sprite b){int[] aa=MinigameSprites.info(a.id),bb=MinigameSprites.info(b.id);return a.left()<b.left()+bb[1]&&b.left()<a.left()+aa[1]&&a.top()<b.top()+bb[2]&&b.top()<a.top()+aa[2];}
    Sprite sprite(int id,int xx,int yy){return new Sprite(id,xx,yy,false,false);}
    boolean hit(int id,int xx,int yy){return collision.overlaps(sprite(playerImage(),x+(kind==SAVE?1:0),y+(kind==SAVE?1:0)),sprite(id,xx,yy));}
    int playerImage(){int frame=(ticks*5/100)%2;if(kind==SAVE)return new int[][]{{245,597},{598,605},{618,633},{634,635}}[dir][frame];if(kind==CAKE)return (dir==2?new int[]{412,417}:new int[]{409,406})[frame];if(kind==GIFTS)return dir==2?760:758;return (dir==2?new int[]{722,726}:new int[]{682,694})[frame];}
    Sprite[] feelers(){return kind==SAVE?new Sprite[]{sprite(654,x+79,y-3),sprite(653,x+1,y-61),sprite(654,x-83,y+3),sprite(653,x-1,y+63)}:new Sprite[]{sprite(383,x+79,y-1),sprite(399,x-1,y-59),sprite(383,x-85,y+1),sprite(399,x-1,y+77)};}
    void move(){
        Sprite[] f=feelers();boolean[] block=new boolean[4];for(Sprite wall:scenery())if(wall.solid)for(int i=0;i<4;i++)if(collision.overlaps(f[i],wall))block[i]=true;
        int distance=kind==SAVE?20:kind==FOXY?25:15;
        int[] order=kind==SAVE?new int[]{1,3,0,2}:new int[]{2,0,3,1};boolean[] held={right||stickRight,up||stickUp,left||stickLeft,down||stickDown};
        for(int i:order)if(held[i]&&!block[i]){if(i==0){x+=distance;if(kind==CAKE||kind==GIFTS)y++;}else if(i==1)y-=distance;else if(i==2)x-=distance;else y+=distance;if(kind==SAVE||i==0||i==2)dir=i;controls=false;firstInput=true;}
    }
    void cakeStep(){
        if(!cakeSlowing)slow=0;
        if(sceneTime+1e-9>=20)cakeSlowing=true;
        if(every(11,1))for(int i=0;i<6;i++){int old=hunger[i]++;if(old==9||old==19)hungerAge[i]=ticks;}
        for(int i=0;i<6;i++)if(!feedingStopped&&hunger[i]>=10&&hit(kidImage(i),KIDS[i][0],KIDS[i][1])){hunger[i]=0;hungerAge[i]=ticks;cues.add(new Cue("cake2",.5f));}
        if(cakeSlowing&&carSteps<15&&every(16,.5)){carX-=50;carSteps++;}
        if(carLeaving&&every(17,.5)){carX-=50;slow+=25;}
        if(carSteps==15&&manStart==0){man=true;manStart=ticks;}
        if(man&&every(19,1)){manSeconds++;slow+=10;feedingStopped=true;}
        if(manSeconds>17){man=false;carLeaving=true;}
        // The original cosmetic choices share the gameplay random stream.
        if(every(21,.5))for(int i=0;i<3;i++)random.nextInt(70);
        if(every(22,.5))random.nextInt(9);
        if(every(23,.3))random.nextInt(9);
        if(carLeaving&&every(24,1))phaseSeconds++;
        if(carLeaving&&phaseSeconds<=10&&every(25,.5))random.nextInt(9);
        if(phaseSeconds>5&&phaseSeconds<=10&&every(26,.4))random.nextInt(9);
        if(phaseSeconds>7&&phaseSeconds<=10&&every(27,.24))random.nextInt(9);
        if(carLeaving&&every(43,1)&&random.nextInt(2)==1)attack(8);
    }
    int kidImage(int i){int speed=hunger[i]<10?3:hunger[i]<20?10:30;int frame=((ticks-hungerAge[i])*speed/100)%2;return (hunger[i]<10?new int[]{692,685}:hunger[i]<20?new int[]{519,482}:new int[]{693,696})[frame];}
    int count(boolean[] flags){int n=0;for(boolean b:flags)if(b)n++;return n;}
    void giftStep(){
        minigameStatic();if(count(heads)==4&&every(15,.26))random.nextInt(9);voiceStep();
        for(int i=0;i<4;i++)if(hit(HEADS[i][0],HEADS[i][1],HEADS[i][2])){if(phase==0&&!gifts[i]){gifts[i]=true;score++;cues.add(new Cue("cake2",.4f));}else if(phase==1&&!heads[i]){heads[i]=true;slow+=5;score++;cues.add(new Cue("effect3",.4f));}}
        if(count(heads)==4){attack(9);if(every(45,.2))for(int i=0;i<4;i++)random.nextInt(9);}
        if(count(gifts)==4&&hit(779,526,388))phase=1;
    }
    void minigameStatic(){
        if(every(11,.5)&&count(heads)<4)for(int i=0;i<3;i++)random.nextInt(70);
        if(every(12,.5)&&count(heads)<4)random.nextInt(9);
        if(every(13,.3)&&count(heads)<4)random.nextInt(9);
    }
    void foxyStep(){
        minigameStatic();
        if(phase==0&&foxyReset){x=530;y=274;foxyWait=0;foxyReset=false;}
        if(every(46,1))foxyWait++;
        if(foxyWait>=5)phase=1;
        if(cycles<2&&hit(695,1304,393))phase=2;
        if(phase==2&&!partyActive){Arrays.fill(pops,-1);phaseSeconds=0;}
        if(phase==2&&every(50,.5)){
            int remaining=0;for(int n:pops)if(n<0)remaining++;
            if(remaining>0){int pick=random.nextInt(remaining);for(int i=pops.length-1;i>=0;i--)if(pops[i]<0&&pick--==0){pops[i]=ticks;cues.add(new Cue("pop",.5f));break;}}
        }
        partyActive=phase==2;
        if(phase==2&&every(54,1))phaseSeconds++;
        if(phase==2&&phaseSeconds>5){boolean live=false;for(int t:pops)if(t>=0&&ticks-t<80)live=true;if(!live){cycles++;phase=phaseSeconds=0;foxyReset=true;controls=false;}}
        if(cycles==2){if(hit(695,1462,391))attack(3);if(x>1024)slow=15;else if(x<1024)slow=0;}
    }
    boolean room(int hh,int vv){return h==hh&&v==vv;}
    void enterRoom(boolean rightward){
        roomTime=ticks;golden=random.nextInt(100)+1;goldenTicks=0;goldenTouched=false;man=rightward&&pendingMan;manX=782;phantomDir=-1;phantomSteps=0;
        int code=-1;if(room(2,2))code=0;else if(room(3,2))code=1;else if(room(4,3))code=2;else if(room(4,2))code=3;else if(room(2,3)&&pupp>=1)code=4;else if(room(2,4)&&pupp>=2)code=5;
        if(code>=0&&(visited&(1<<code))==0){visited|=1<<code;phantomDir=code<3?0:code==3?3:1;phantomX=code<3?730:code==3?534:508;phantomY=code<3?376:code==3?476:230;}
    }
    int[] corpse(){if(room(1,4))return new int[]{673,498,616};if(room(4,1)||room(4,4))return new int[]{673,194,600};if(room(5,3))return new int[]{674,498,616};if(room(3,2))return new int[]{674,836,606};return null;}
    void saveStep(){
        voiceStep();
        if(every(66,.5)){if(heDir==0){heStep++;heX+=10;}if(heDir==0&&heStep>=15){heDir=1;heStep=0;}if(heDir==1){heStep++;heX-=10;}if(heDir==1&&heStep>=15){heDir=0;heStep=0;}}
        if(every(12,.2))move();Sprite player=sprite(638,x,y);boolean changed=false,rightward=false;
        if(collision.overlaps(player,sprite(643,1023,377))){h++;x=142;y=377;changed=rightward=true;pendingMan=random.nextInt(100)==0;}
        else if(collision.overlaps(player,sprite(643,-1,369))){h--;x=879;y=369;changed=true;}
        else if(collision.overlaps(player,sprite(644,499,-1))){v--;x=499;y=664;changed=true;}
        else if(collision.overlaps(player,sprite(644,525,771))){v++;x=525;y=122;changed=true;}
        if(every(35,.5))random.nextInt(19);if(every(36,.25)){random.nextInt(100);random.nextInt(25);random.nextInt(25);}if(every(37,.1))random.nextInt(50);
        if(changed){controls=false;enterRoom(rightward);}
        if(phantomDir>=0&&every(phantomDir==0?89:phantomDir==3?90:91,.5)){if(phantomDir==0)phantomX+=50;else phantomY+=phantomDir==1?-50:50;if(++phantomSteps>10)phantomDir=-1;}
        if(golden>=1&&golden<=4){int[][] xy={{308,266},{704,256},{272,576},{718,574}};int[] p=xy[golden-1];if(!goldenTouched)for(Sprite f:feelers())if(collision.overlaps(f,sprite(669,p[0],p[1])))goldenTouched=true;if(goldenTouched&&++goldenTicks>=30)golden=0;}
        if(man&&every(105,.1))manX-=20;if(man&&hit(671,manX,374)){end(true);return;}
        int[] corpse=corpse();if(corpse!=null&&hit(corpse[0],corpse[1],corpse[2])){end(false);return;}
        if(sceneTime>=5)controls=false;if(every(107,30)&&random.nextInt(3)==1)end(false);
    }
    void add(List<Sprite> s,int id,int xx,int yy){s.add(sprite(id,xx,yy));}
    void solid(List<Sprite> s,int id,int xx,int yy){s.add(new Sprite(id,xx,yy,false,true));}
    void wall(List<Sprite> s,int id,int xx,int yy){s.add(new Sprite(id,xx,yy,true,true));}
    List<Sprite> scenery(){
        List<Sprite> s=new ArrayList<>();
        if(kind==SAVE){
            for(int[] p:new int[][]{{-2,-8},{-2,680},{714,-8},{716,682}})wall(s,639,p[0],p[1]);for(int[] p:new int[][]{{-2,80},{-2,550},{926,79},{926,550}})wall(s,640,p[0],p[1]);
            boolean top=room(4,1)||v==2&&h<=3||room(1,3)||room(3,3)||room(5,3)||room(1,4)||room(3,4);
            boolean bottom=room(1,2)||room(3,2)||room(1,3)||room(3,3)||room(5,3)||room(1,4)||room(3,4)||room(4,4)||room(2,5);
            boolean l=room(4,1)||room(1,2)||room(1,3)||room(4,3)||room(1,4)||room(4,4)||room(2,5);
            boolean r=room(4,1)||room(4,2)||room(3,3)||room(5,3)||room(3,4)||room(4,4)||room(2,5);
            if(top)solid(s,641,308,-8);if(bottom)solid(s,641,308,684);if(l)solid(s,642,4,213);if(r)solid(s,642,927,213);
            if(room(2,3)||room(2,4)||room(4,2)||room(4,3))add(s,662,0,0);if(room(3,2)||room(2,2)||room(5,3))add(s,663,0,0);
            if(room(1,3)||room(1,4)){solid(s,652,318,190);solid(s,652,318,410);}if(room(3,4)||room(3,3)||room(4,2)){solid(s,652,722,190);solid(s,652,722,410);}
            if(room(2,5))solid(s,655,662,504);if(room(4,1))solid(s,660,524,296);if(room(1,2)){add(s,656,184,524);add(s,657,280,288);add(s,658,832,190);}
            if(room(3,2)||room(2,4)||room(4,2))add(s,659,492,362);
            if(room(4,1))add(s,380,316,468);if(room(3,3)||room(4,3)||room(5,3))add(s,380,711,468);if(room(4,3)||room(2,4))add(s,380,316,308);
            if(room(5,3))solid(s,664,704,400);
            if(room(4,4)){int frame=(ticks*5/100)%2;add(s,(heDir==0?new int[]{665,666}:new int[]{667,668})[frame],heX,514);}
            if(golden>=1&&golden<=4){int[][] pos={{308,266},{704,256},{272,576},{718,574}};add(s,goldenTicks>0?670:669,pos[golden-1][0],pos[golden-1][1]);}
            int[] body=corpse();if(body!=null)add(s,body[0],body[1],body[2]);if(phantomDir>=0)add(s,675,phantomX,phantomY);if(man)add(s,671,manX,374);
        }else if(kind==CAKE){
            for(int[] p:new int[][]{{405,162,202},{407,162,616},{405,868,212},{410,162,170},{413,346,180},{400,458,171}})wall(s,p[0],p[1],p[2]);
            add(s,715,carX,67);for(int i=0;i<6;i++)add(s,kidImage(i),KIDS[i][0],KIDS[i][1]);
            int kid=carSteps<15?new int[]{683,697}[(ticks*3/100)%2]:new int[]{683,713,716,717,718,701,720,719}[Math.min(7,(ticks-manStart)/100)];add(s,kid,404,118);if(man)add(s,699,467,84);add(s,698,77,659);
        }else if(kind==GIFTS){
            wall(s,724,66,97);wall(s,730,64,712);wall(s,724,936,98);wall(s,727,66,96);
            for(int i=0;i<4;i++){add(s,761,CHILDREN[i][0],CHILDREN[i][1]);if(heads[i])add(s,HEADS[i][0],HEADS[i][1],HEADS[i][2]);else if(phase==0&&gifts[i]){int[][] p={{141,54},{150,28},{-152,44},{-162,34}};add(s,780,HEADS[i][1]+p[i][0],HEADS[i][2]+p[i][1]);}}
            add(s,phase==0?723:782,391,8);if(count(heads)==4)add(s,761,536,425);
        }else{
            wall(s,724,66,97);wall(s,781,64,712);wall(s,382,938,102);wall(s,778,68,96);wall(s,724,1892,102);
            add(s,784,273,268);add(s,784,784,268);if(phase!=0)add(s,787,819,566);
            for(int[] p:new int[][]{{1608,217},{1778,322},{1550,404},{1766,508},{1602,594}})add(s,cycles<2?794:384,p[0],p[1]);if(cycles>=2)add(s,799,178,559);
            for(int i=0;i<9;i++)if(pops[i]>=0&&ticks-pops[i]<80)add(s,POP[(ticks-pops[i])/10],FIREWORKS[i][0],FIREWORKS[i][1]);
            add(s,phase==0?783:phase==1?new int[]{790,791}[(ticks*20/100)%2]:801,231,10);
        }
        return s;
    }
}
