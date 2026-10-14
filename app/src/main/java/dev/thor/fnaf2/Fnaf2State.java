package dev.thor.fnaf2;
import java.util.Arrays;
import java.util.Random;
import java.util.ArrayDeque;

/** Fixed 60 Hz simulation. Verified constants are recorded in MODLOG.md. */
final class Fnaf2State {
    double lookSensitivity=1,cameraSensitivity=1;
    // Original mask breathing path. Fractional interpolation uses the original speed 4 (0.5 px/tick).
    private static final int[][] MASK_BREATH={{0,-3,3},{0,-1,1},{-2,-2,2},{-1,-3,3},{-2,-1,2},{-2,-2,2},{-1,-1,1},{-2,0,2},{-3,0,3},{-2,-1,2},{-3,-1,3},{-3,0,3},{-3,0,3},{-3,0,3},{-2,2,2},{-3,4,5},{-1,3,3},{0,4,4},{4,3,5},{3,1,3},{3,2,3},{3,0,3},{4,0,4},{5,0,5},{4,-2,4},{5,-3,5},{1,-1,1},{4,-2,4},{6,-3,6},{4,-3,5},{2,-1,2},{3,0,3},{3,0,3},{4,0,4},{3,1,3},{3,2,3},{2,2,2},{1,1,1},{1,2,2},{-2,5,5},{-2,3,3},{-4,1,4},{-3,1,3},{-3,0,3},{-3,1,3},{-3,0,3},{-3,0,3},{-4,0,4},{-2,-1,2},{-2,-1,2},{-3,-3,4},{-2,-3,3},{-2,-2,2},{-3,-1,3},{-1,0,1},{-6,0,6},{-4,2,4},{-3,3,4},{-5,4,6},{0,3,3},{2,1,2},{4,1,4},{3,0,3},{3,0,3},{5,-2,5},{4,-4,5},{3,-2,3},{3,-3,4},{5,-3,5},{3,-1,3},{5,0,5},{4,0,4},{4,0,4},{3,0,3},{3,1,3},{2,2,2},{1,3,3},{0,3,3},{0,2,2},{-3,4,5},{-3,1,3},{-3,1,3},{-3,-1,3},{-4,-2,4},{-3,-3,4},{-3,-2,3},{-2,-4,4},{-3,-2,3},{-4,-2,4},{-3,-2,3},{-4,-1,4},{-2,-1,2},{-3,0,3},{-2,0,2},{-2,1,2},{-3,2,3},{-2,2,2},{0,1,1},{0,2,2},{2,1,2},{1,3,3},{3,0,3},{3,2,3},{5,0,5},{3,0,3},{5,-3,5},{4,-3,5},{3,-2,3},{4,0,4},{3,0,3},{3,1,3},{0,2,2},{1,1,1},{-2,2,2},{-3,0,3},{-3,0,3},{-3,0,3},{-2,0,2},{-2,-1,2},{-3,-2,3},{-1,-1,1},{0,-2,2}};
    double maskOffset(boolean vertical){double distance=(ticks*.5)%381,position=0;for(int[] n:MASK_BREATH){int offset=n[vertical?1:0];if(distance<n[2])return position+offset*distance/n[2];distance-=n[2];position+=offset;}return position;}

    static final int WF=0,WB=1,WC=2,FOXY=3,TF=4,TB=5,TC=6,MANGLE=7,BB=8;
    static final int HALL=13,NEAR=14,OFFICE=15,CAUGHT=16;
    static final String[] CAMERA_NAME={"Party Room 1","Party Room 2","Party Room 3","Party Room 4","Left Air Vent","Right Air Vent","Main Hall","Parts / Service","Show Stage","Game Area","Prize Corner","Kid's Cove"};
    static final int[] CAMERA_IMAGE={174,80,82,43,38,32,51,37,117,41,76,77};
    final EventTimers eventTimers=new EventTimers();double frameDelta=1.0/60;int clockSeconds;boolean initialEvents;
    final Random random;
    final ToyBonnieWalk toyWalk=new ToyBonnieWalk();
    // The original shares one stream for gameplay and sound choices. A second
    // stream can still be injected by isolated tests, never by the default app.
    final Random soundRandom;
    static final class SoundCue {
        final String channel,file;final float volume;
        SoundCue(String channel,String file,float volume){this.channel=channel;this.file=file;this.volume=volume;}
    }
    final ArrayDeque<SoundCue> sounds=new ArrayDeque<>();
    int hallMovementTicks;boolean hallLight,nearbyDanger,flashHeld,leftVentHeld,rightVentHeld;
    float bbVoiceVolume=.5f;int bbVoicePending;
    int blackoutAlpha,blackoutFade,cameraDropTicks,cameraRaiseTicks;
    boolean closeAfterRaise,bbRaisePending,mangleRaisePending;
    static final int[] CAMERA_UP={114,97,116,99,103,104,105,106,110,112,113};
    static final int[] CAMERA_DOWN={113,112,110,106,105,104,103,99,116,97,114};
    final int[] pos=new int[9],ai=new int[9],stun=new int[9],officeTime=new int[9],maskedTime=new int[9];
    final boolean[] pendingMove=new boolean[9];
    final boolean[] authorizedMove=new boolean[9],cooldownBlocked=new boolean[9];
    final boolean[] footstepQueued=new boolean[9],ventQueued=new boolean[9];
    final int[] movementNoise=new int[9];
    boolean playing,monitor,mask,flashlight,leftLight,rightLight,wind,paused,won,dead,bbInside,quickWind;
    boolean quickWindStayOnCamera;
    int camera=8,night=1,ticks,phase,attacker=-1,puppetStage,puppetPosition=11,puppetAI,puppetStun,maskFrames,challenge=-1,goldenAI,goldenHallTicks,signalTicks;
    int attackFrames,attackAnimationUnits;boolean attackFresh;
    boolean pictureOffice,lowerRollPending,attackDropPending;
    boolean goldenOffice,goldenHall,goldenCaught,mangleArmed,puppetPending;
    int decidePath=1,puppetPath=1,monitorSeconds,maskSeconds,rareGolden,difficultyHour;
    static final int[] MASK_DOWN={140,139,138,137,136,135,134,143,88};
    static final int[] MASK_UP={88,143,134,135,136,137,138,139,140,133,133};
    int maskMotion,maskAnimationTicks,toyChicaOfficeSeconds;
    int[] customLevels;
    int randomImage=999,rareExposure,paperAI,paperStun,puppetFlashTicks,puppetApparition=-1,puppetApparitionAlpha=255;
    boolean paperOffice,paperPending,shadowBonnie,shadowEnded,halloween;
    int halloweenMode;
    static boolean seasonal(int mode,int month,int day){return mode==1||(mode==0&&month==10&&day==31);}
    int mangleOverlay(){if(!monitor||signalTicks>0||pos[MANGLE]!=camera+1)return -1;switch(camera+1){case 11:return 218;case 10:return 217;case 1:case 7:return 78;case 2:return 219;default:return -1;}}
    void rareStep(){
        puppetApparition=-1;
        if(paperStun>0)paperStun--;
        if(!paperOffice&&monitor&&camera==3&&flashlight)paperStun=400-night*50;
        if(paperPending&&paperStun==0&&monitor&&camera!=3){paperOffice=true;paperPending=false;}
        if(shadowBonnie&&flashlight)shadowBonnie=false;
        if(shadowBonnie&&every(627,4))shadowEnded=true;
        if(monitor&&flashlight&&signalTicks==0){boolean rare=(camera==4&&pos[WB]!=5&&pos[TC]!=5&&randomImage==3)||(camera==7&&pos[WF]!=8&&pos[FOXY]!=8&&randomImage<=100)||(camera==10&&puppetStage>=3&&randomImage<=100);if(rare&&++rareExposure>=30){signalTicks=200;rareExposure=0;randomImage=0;cue("rare","metalrun",.8f);}}
        if(every(370,.11))puppetFlashTicks=0;
        if(monitor&&puppetPosition==camera+1&&puppetPosition!=11){
            if(!flashlight&&puppetFlashTicks>0){for(int id:new int[]{65,73,210})if(random.nextInt(50)==1)puppetApparition=id;if(every(367,1)&&random.nextInt(50)==1)puppetApparition=216;}
            if(flashlight)puppetFlashTicks=1;
        }
        if(puppetApparition>=0)puppetApparitionAlpha=155-soundRandom.nextInt(150);
        if(pos[TF]==NEAR&&!monitor&&!flashlight&&maskOn()&&every(134,1)&&random.nextInt(10)==1)pos[TF]=9;
    }
    static int matchingPreset(int[] levels){for(int i=0;i<PRESETS.length;i++)if(Arrays.equals(levels,PRESETS[i]))return i;return -1;}
    int[] selectedLevels(){return customLevels!=null?customLevels:PRESETS[Math.max(0,Math.min(9,challenge))];}
    static final String[] CHALLENGES={"20/20/20/20","New and Shiny","Double Trouble","Night of Misfits","Foxy Foxy","Ladies Night","Freddy's Circus","Cupcake Challenge","Fazbear Fever","Golden Freddy"};
    // WF, WB, WC, Foxy, TF, TB, TC, Mangle, BB, Golden Freddy.
    static final int[][] PRESETS={{20,20,20,20,0,0,0,0,0,0},{0,0,0,0,10,10,10,10,10,0},{0,20,0,5,0,20,0,0,0,0},{0,0,0,0,0,0,0,20,20,10},{0,0,0,20,0,0,0,20,0,0},{0,0,20,0,0,0,20,20,0,0},{20,0,0,10,20,0,0,0,10,10},{5,5,5,5,5,5,5,5,5,5},{10,10,10,10,10,10,10,10,10,10},{20,20,20,20,20,20,20,20,20,20}};
    int music=2000,windGrace,battery,batteryMax,foxyPressure,foxyFlashed,intruder=-1,intrusionTicks;
    boolean defended;
    double elapsed,flashlightPower=1,musicBox=1,accumulator,resultTime,phaseTime,look,stick,cameraStick;
    final double[] cameraPan=new double[12];
    final boolean[] manualPan=new boolean[12];
    Fnaf2State(){this(new SourceRandom());} Fnaf2State(Random r){this(r,r);}
    Fnaf2State(Random r,Random sound){random=r;soundRandom=sound;}
    void start(){start(night);}
    void start(int n){
        if(random instanceof SourceRandom)((SourceRandom)random).beginScene();
        java.util.Calendar today=java.util.Calendar.getInstance();halloween=seasonal(halloweenMode,today.get(java.util.Calendar.MONTH)+1,today.get(java.util.Calendar.DAY_OF_MONTH));
        night=Math.max(1,Math.min(7,n));if(night==7)challenge=customLevels==null?Math.max(0,Math.min(PRESETS.length-1,challenge)):-1;playing=true;paused=won=dead=bbInside=goldenOffice=goldenHall=false;clearInputs();
        monitor=mask=false;maskMotion=maskAnimationTicks=cameraRaiseTicks=cameraDropTicks=0;closeAfterRaise=bbRaisePending=mangleRaisePending=false;camera=night==7?6:8;ticks=phase=maskFrames=puppetStage=puppetStun=foxyPressure=foxyFlashed=goldenHallTicks=signalTicks=0;
        goldenCaught=mangleArmed=puppetPending=false;decidePath=1;puppetPath=0;monitorSeconds=maskSeconds=toyChicaOfficeSeconds=0;difficultyHour=-1;rareGolden=0;eventTimers.reset();clockSeconds=0;initialEvents=true;
        pictureOffice=lowerRollPending=attackDropPending=false;
        toyWalk.active=false;elapsed=accumulator=resultTime=phaseTime=look=0;attacker=intruder=-1;intrusionTicks=0;defended=false;
        Arrays.fill(cameraPan,.5);Arrays.fill(manualPan,false);
        music=2000;windGrace=0;batteryMax=8000-Math.min(night,5)*1000;battery=batteryMax;
        musicBox=flashlightPower=1;puppetPosition=11;puppetAI=new int[]{1,5,8,9,10,15,15}[night-1];
        int[] initial={8,8,8,8,9,9,9,12,10};System.arraycopy(initial,0,pos,0,9);
        Arrays.fill(stun,0);Arrays.fill(officeTime,0);Arrays.fill(maskedTime,0);Arrays.fill(pendingMove,false);Arrays.fill(authorizedMove,false);setDifficulty();
        paperAI=(random.nextInt(100)+1)/100;randomImage=-1;rareExposure=paperStun=puppetFlashTicks=0;paperOffice=paperPending=shadowBonnie=shadowEnded=false;puppetApparition=-1;
        sounds.clear();hallMovementTicks=blackoutAlpha=blackoutFade=cameraDropTicks=0;hallLight=nearbyDanger=false;bbVoiceVolume=.5f;bbVoicePending=0;
        Arrays.fill(footstepQueued,false);Arrays.fill(ventQueued,false);
        Arrays.fill(movementNoise,0);
    }
    void stop(){toyWalk.active=false;playing=paused=false;clearInputs();sounds.clear();monitor=mask=false;maskMotion=maskAnimationTicks=cameraRaiseTicks=cameraDropTicks=0;closeAfterRaise=bbRaisePending=mangleRaisePending=false;}
    boolean pauseForInterruption(){if(!live())return false;paused=true;setQuickWind(false);clearInputs();return true;}
    void clearInputs(){flashlight=leftLight=rightLight=wind=quickWind=flashHeld=leftVentHeld=rightVentHeld=false;stick=cameraStick=0;}
    void cue(String channel,String file,float volume){if(sounds.size()==64)sounds.removeFirst();sounds.addLast(new SoundCue(channel,file,volume));}
    SoundCue pollSound(){return sounds.pollFirst();}
    void bbVoice(float volume){bbVoiceVolume=volume;bbVoicePending=1+soundRandom.nextInt(4);}
    void resolveBbVoice(){if(bbVoicePending>0&&bbVoicePending<4){cue("bb-voice",new String[]{"echo1","echo4b","echo3b"}[bbVoicePending-1],bbVoiceVolume);bbVoicePending=0;}if(bbVoicePending==4)bbVoicePending=1+soundRandom.nextInt(3);}
    static boolean inHall(int p){return p==HALL||p==NEAR;}
    static boolean closeRoom(int p){return p==1||p==2||p==5||p==6||p==NEAR||p==OFFICE;}
    boolean enemyNearby(){
        for(int i=0;i<BB;i++)if(closeRoom(pos[i])||(pos[i]==HALL&&(i==WF||i==FOXY||i==TF||i==MANGLE)))return true;
        return closeRoom(puppetPosition);
    }
    boolean hallBlackout(){return hallMovementTicks>0&&flashlight&&!monitor;}
    int musicWarningImage(boolean cameraView){
        if(music>400||puppetPosition!=11)return -1;
        boolean critical=music<=200;int frame=(ticks/(critical?5:20))%2;
        return (cameraView?(critical?new int[]{489,490}:new int[]{307,308}):(critical?new int[]{496,497}:new int[]{494,495}))[frame];
    }
    void forceMonitorDown(){if(!monitor)return;monitor=false;clearInputs();cameraDropTicks=22;lowerRollPending=true;cue("switch","STEREO_CASSETTE__90097701",.5f);monitorLowered();}
    int cameraFlipImage(){return cameraRaiseTicks>0?CAMERA_UP[Math.min(10,(13-cameraRaiseTicks)*9/10)]:cameraDropImage();}
    void advanceMonitor(){
        if(attackDropPending){attackDropPending=false;forceMonitorDown();}
        if(cameraDropTicks>0)cameraDropTicks--;
        if(cameraRaiseTicks>0&&--cameraRaiseTicks==0){
            monitor=true;monitorSeconds=0;
            if(bbRaisePending){bbInside=true;pos[BB]=CAUGHT;}
            if(mangleRaisePending)pos[MANGLE]=CAUGHT;
            bbRaisePending=mangleRaisePending=false;
            if(closeAfterRaise){closeAfterRaise=false;forceMonitorDown();}
        }
    }
    int cameraDropImage(){return cameraDropTicks>0?CAMERA_DOWN[Math.min(CAMERA_DOWN.length-1,(22-cameraDropTicks)/2)]:-1;}
    void movementSounds(int actor,int before,int after){
        if(before==after)return;
        if(inHall(after)&&!inHall(before))hallMovementTicks=300;
        // The original footsteps region covers party rooms, hall stages and the office.
        if(after==1||after==2||after==3||after==4||inHall(after)||after==OFFICE){
            footstepQueued[actor]=true;
        }
        if(after==5||after==6)ventQueued[actor]=true;
        if((before==5||before==6)&&after==OFFICE)cue("movement","ventwalk1",.5f);
        if(actor==BB){
            if(before==7||before==3||before==1){bbVoiceVolume=.25f;bbVoice(bbVoiceVolume);}
            if(after==5||before==5&&after==OFFICE)soundRandom.nextInt(4);
            if(after==OFFICE)cue("bb-voice","echo1",bbVoiceVolume);
        }
    }
    void resolveMovementSounds(){
        for(int i:new int[]{TC,WB,TB,WC,MANGLE})if(ventQueued[i]){soundRandom.nextInt(4);cue("movement","ventwalk1",.5f);}
        if(ventQueued[BB])cue("movement","ventwalk1",.5f);
        Arrays.fill(ventQueued,false);
        int last=-1;
        for(int i:new int[]{WF,WB,WC,FOXY,TF,TB,TC,BB})if(footstepQueued[i])last=soundRandom.nextInt(5);
        if(last>=0)cue("movement","walk"+(last+1),.5f);
        if(footstepQueued[MANGLE])cue("metal-step",new String[]{"metalwalk2","metalwalk1","metalwalk3"}[soundRandom.nextInt(3)],.5f);
        Arrays.fill(footstepQueued,false);
    }
    void maskedVentRetreat(int actor,int event){
        if(pos[actor]!=OFFICE||!maskOn())return;
        boolean leave=every(event,1)&&random.nextInt(10)==1;
        if(leave||maskSeconds>=5){
            pos[actor]=actor==BB?10:7;pendingMove[actor]=false;
            soundRandom.nextInt(4);cue("movement","ventwalk1",.5f);
            if(actor==TC)toyChicaOfficeSeconds=0;
        }
    }
    void setQuickWind(boolean on){
        if(!on){boolean active=quickWind;quickWind=false;wind=false;if(active){if(!quickWindStayOnCamera){if(cameraRaiseTicks>0)closeAfterRaise=true;else forceMonitorDown();}clearInputs();}return;}
        if(!live())return;
        if(mask&&maskMotion==0)toggleMask();
        quickWind=true;advanceQuickWind();
    }
    void advanceQuickWind(){
        if(!quickWind)return;
        if(mask&&maskMotion==0){quickWind=false;toggleMask();quickWind=true;}
        if(mask||maskMotion!=0||intruder>=0||cameraDropTicks>0||cameraRaiseTicks>0)return;
        if(!monitor){raiseMonitor();return;}camera=10;wind=true;leftLight=rightLight=false;
    }
    double cameraPan(){return manualPan[camera]?cameraPan[camera]:.5+.45*Math.sin(elapsed*.14);}
    boolean skipToLastFiveSeconds(){if(!live()||ticks>=24900)return false;eventTimers.every(482,1,0);ticks=24900;clockSeconds=415;elapsed=415;accumulator=0;setDifficulty();clearInputs();return true;}
    void randomScare(){if(live())attack(random.nextInt(10)==0?9:random.nextInt(4));}
    void tick(double seconds){
        if(!playing||paused)return;double dt=Math.max(0,seconds);
        if(dead||won){resultTime+=dt;return;}
        accumulator+=dt;
        if(accumulator+1e-9>=1.0/60){double frame=accumulator;accumulator=0;step(frame);}
    }
    boolean every(int event,double interval){return eventTimers.every(event,interval,frameDelta);}
    void advanceClock(){if(every(482,1))clockSeconds++;}
    void step(){step(1.0/60);}
    void step(double delta){
        frameDelta=Math.max(0,delta);boolean opening=initialEvents;initialEvents=false;
        if(phase==1&&!attackFresh){phaseTime+=frameDelta;attackAnimationUnits+=(int)(Fnaf2Catalog.SPEED[attacker]*frameDelta*60+1e-7);}
        if(toyWalk.active){toyWalk.tick();if(!toyWalk.active){pos[TB]=3;pendingMove[TB]=false;}}
        ticks++;elapsed+=frameDelta;if(!monitor)look=Math.max(-1,Math.min(1,look+stick*lookSensitivity/12.0));
        advanceMonitor();if(dead||won||!playing)return;
        if(every(28,.1))random.nextInt(50);if(every(29,.49))random.nextInt(5);if(every(116,.1))random.nextInt(31);
        if(blackoutFade>0){if(monitor)blackoutFade=0;else blackoutFade--;blackoutAlpha=blackoutFade;}
        if(hallMovementTicks>0)hallMovementTicks--;
        if(monitor&&cameraStick!=0){if(!manualPan[camera]){cameraPan[camera]=cameraPan();manualPan[camera]=true;}cameraPan[camera]=Math.max(0,Math.min(1,cameraPan[camera]+cameraStick*cameraSensitivity/60.0));}
        if(maskMotion!=0){maskAnimationTicks++;if(maskAnimationTicks*3/4>=(maskMotion==1?MASK_DOWN.length:MASK_UP.length))maskMotion=0;}
        advanceQuickWind();
        if(mask)maskFrames++;else maskFrames=0;
        if(pos[TC]!=OFFICE)toyChicaOfficeSeconds=0;
        if(!monitor)monitorSeconds=0;
        if(!maskOn())maskSeconds=0;
        if(flashlight&&!mask&&!bbInside&&battery>0)battery--;
        if(battery==0)flashlight=false;if(bbInside)flashlight=leftLight=rightLight=false;
        flashlightPower=battery/(double)batteryMax;
        if(puppetStun>0)puppetStun--;if(monitor&&flashlight&&camera+1==puppetPosition&&puppetPosition!=11)puppetStun=10;
        for(int i=0;i<9;i++){
            cooldownBlocked[i]=stun[i]>0;
            if(stun[i]>0)stun[i]--;
            if(monitor&&flashlight&&pos[i]==camera+1&&i!=FOXY&&i!=BB&&((i<=WC&&pos[i]!=8)||(i>=TF&&i<=TC&&pos[i]!=9)||(i==MANGLE&&pos[i]!=11)))stun[i]=400;
            if(pos[i]==OFFICE){officeTime[i]++;if(maskFrames>=12)maskedTime[i]++;else maskedTime[i]=0;}
            else{officeTime[i]=0;maskedTime[i]=0;}
        }
        if(foxyFlashed>100*night&&!flashlight&&!hallLight&&stun[FOXY]==0&&pos[FOXY]==HALL){pos[FOXY]=8;foxyFlashed=foxyPressure=0;pendingMove[FOXY]=false;hallMovementTicks=300;stun[FOXY]=500+random.nextInt(500);}
        if(bbInside&&every(635,2))cue("bb-laugh","echo4b",1);
        maskedVentRetreat(BB,177);
        if(every(197,5))rollMovementOpportunities();
        // Events 208-224 authorize every actor before any route changes. An
        // authorized request survives later camera/stun changes until consumed.
        for(int i=0;i<9;i++)if(pendingMove[i]&&!authorizedMove[i]&&!cooldownBlocked[i]&&canAuthorize(i)){authorizedMove[i]=true;movementNoise[i]=10;}
        for(int i:new int[]{WF,WB,WC,FOXY,MANGLE,BB,TF,TB,TC}){
            if(pendingMove[i]&&authorizedMove[i]&&pos[i]!=OFFICE&&pos[i]!=CAUGHT){int before=pos[i];moveAuthorized(i);if(pos[i]!=before||phase!=0){pendingMove[i]=authorizedMove[i]=false;}}
            // Mangle's mask retreat precedes BB and the toys' route events.
            if(i==MANGLE)maskedVentRetreat(MANGLE,264);
        }
        if(pos[TC]==OFFICE&&monitor&&toyChicaOfficeSeconds>5)pos[TC]=CAUGHT;
        if(pos[TB]==OFFICE&&monitor&&stun[TB]==0)pos[TB]=CAUGHT;
        // Source 300/301: masked half-second entry roll; another intrusion may repel him.
        if(pos[TB]==OFFICE&&maskOn()&&!toyWalk.active){
            if(intruder<0&&blackoutFade==0&&every(300,.5)&&random.nextInt(2)==1){toyWalk.start();intruder=TB;intrusionTicks=0;defended=false;}
            else if(intruder>=0&&every(301,1)&&random.nextInt(3)==1){pos[TB]=3;pendingMove[TB]=false;}
        }
        maskedVentRetreat(TC,303);
        // Original events 406–409 count time with the monitor raised, not time since entry.
        if(monitor&&monitorSeconds>=20-night*2)for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==OFFICE){pos[i]=CAUGHT;pendingMove[i]=false;}
        // Original events 561–564 force a raised monitor down on the ten-second clock.
        if(every(561,10)&&monitor)for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==OFFICE){forceMonitorDown();break;}
        if(intruder<0&&!monitor){for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==OFFICE){intruder=i;intrusionTicks=0;blackoutAlpha=blackoutFade=0;defended=false;clearInputs();break;}}
        if(intruder>=0){intrusionTicks++;int allowed=new int[]{100,80,60,55,50,50,45}[night-1];
            // Original blackout: intermittent full darkness, increasing to a held blackout.
            int flicker=intrusionTicks<=20?0:intrusionTicks<100?soundRandom.nextInt(50):intrusionTicks<200?20+soundRandom.nextInt(50):100;
            blackoutAlpha=flicker>=25?255:0;
            if(intrusionTicks<allowed&&maskOn())defended=true;
            if(intrusionTicks>=300){int who=intruder;intruder=-1;pendingMove[who]=false;stun[who]=random.nextInt(500)/night;blackoutAlpha=blackoutFade=249;if(who==TB)toyWalk.active=false;if(defended){pos[who]=who==WF?8:who==WB?7:who==WC?4:who==TB?3:9;if(who==WB||who==WC)cue("movement","ventwalk1",.5f);}else pos[who]=CAUGHT;}
        }
        // Events 322-346 retain the movement flag across frames. Each watched
        // actor rerolls interference while its flag remains positive.
        for(int i=0;i<9;i++)if(movementNoise[i]>0)movementNoise[i]--;
        if(monitor)for(int i=0;i<9;i++)if(movementNoise[i]>0&&pos[i]==camera+1)signalTicks=20+random.nextInt(100);
        if(signalTicks>0)signalTicks--;
        // Routes 240-303 see the previous hall-light counter. Its one-second
        // clear and the current input are applied later, by events 352/353.
        if(every(352,1))hallLight=false;
        if(flashlight&&!monitor&&!mask&&battery>0&&!bbInside)hallLight=true;
        if(puppetPending){puppetPending=false;switch(puppetPosition){case 11:puppetPosition=10;break;case 10:puppetPosition=7;break;case 7:puppetPosition=puppetPath==1?3:4;break;case 3:puppetPosition=1;break;case 4:puppetPosition=2;break;case 1:case 2:puppetPosition=OFFICE;break;}}
        if(music==0&&every(358,1)&&puppetStage<3&&random.nextInt(20)<=puppetAI&&monitor&&camera==10&&!flashlight)puppetStage++;
        if(music==0&&every(359,1)&&puppetStage<3&&random.nextInt(20)<=puppetAI&&(!monitor||camera!=10))puppetStage++;
        if(every(360,1)&&random.nextInt(20)<=puppetAI&&puppetStage>=3&&puppetStun==0)puppetPending=true;
        if(every(361,1))puppetPath=1+random.nextInt(2);
        if(every(362,.2)&&camera+1==puppetPosition)random.nextInt(100);
        resolveCaught();
        if(phase==1){attackFresh=false;attackFrames++;if(attackFrames>=40||Fnaf2Catalog.REPEAT[attacker]>0&&attackAnimationUnits>Fnaf2Catalog.SCARE[attacker].length*100){dead=true;resultTime=0;}}
        resolveBbVoice();
        if(puppetPosition==OFFICE&&every(478,1)&&random.nextInt(10)==1)puppetPosition=CAUGHT;
        advanceClock();
        // Music events 491-501 run after the Puppet's escape check. The grace
        // counter decrements on winding ticks too, immediately after it is set.
        if(wind&&monitor&&camera==10){music=Math.min(2000,Math.max(300,music+5));windGrace=10;}
        else if(windGrace==0&&music>0&&(night>1||clockSeconds>=140)&&every(night==1?493:night==2?494:night==3?495:493+night,.05))music=Math.max(0,music-new int[]{2,2,3,4,5,6,6}[night-1]);
        if(windGrace>0)windGrace--;
        musicBox=music/2000.0;
        // Source next-day event 514 follows routes, attack triggers and music.
        if(clockSeconds>=420&&!dead){won=true;resultTime=0;}
        rareStep();
        // Source AI adjustment events follow the movement rolls at an hour boundary.
        if(opening&&night>=3&&night<=6){int roll=(random.nextInt(night==3?1000:night==6?10:100)+1)/(night==3?1000:night==6?10:100);rareGolden=night==6?roll:0;}
        if(opening||clockSeconds/70!=difficultyHour)setDifficulty();
        resolveMovementSounds();
        if(every(588,1))decidePath=1+random.nextInt(2);
        if(pos[FOXY]==HALL&&hallLight){foxyPressure=0;foxyFlashed++;}
        // Events 620,645-646 are after movement, intrusion and camping checks.
        if(goldenOffice&&maskOn())goldenOffice=false;
        if(goldenOffice&&hallLight){goldenOffice=false;goldenCaught=true;}
        if(goldenHall&&hallLight&&hallMovementTicks==0){boolean clear=true;for(int i=0;i<9;i++)if(pos[i]==HALL||pos[i]==NEAR)clear=false;if(clear&&++goldenHallTicks>100)goldenCaught=true;}
        if(!goldenHall)goldenHallTicks=0;
        if(goldenAI>0&&!hallLight&&every(615,1))goldenHall=random.nextInt(10)==1;
        // Events 616-618 clear early Night 1 pressure on every pass, before
        // the later masked-pressure increment; event 693 is a separate timer.
        if(night==1&&clockSeconds<210)foxyPressure=0;
        if(monitor&&every(620,1))monitorSeconds++;
        if(lowerRollPending){if(random.nextInt(1000000)==1)shadowBonnie=true;lowerRollPending=false;}
        if(!monitor&&!pictureOffice)randomImage=random.nextInt(1000);
        pictureOffice=!monitor;
        if(intruder<0&&every(645,1))foxyPressure++;
        if(intruder<0&&maskOn()&&pos[TB]!=OFFICE&&pos[TC]!=OFFICE&&pos[MANGLE]!=OFFICE&&pos[BB]!=OFFICE&&every(646,1))foxyPressure++;
        if(night==7&&monitor&&(camera==7||camera==8))signalTicks=5;
        if(hallLight){for(int i=0;i<BB;i++)if(inHall(pos[i]))stun[i]=Math.max(stun[i],i==FOXY?50:40);}
        if(every(685,.5)&&pos[FOXY]==8&&hallLight&&foxyPressure>0)foxyPressure--;
        if(opening){battery=batteryMax;flashlightPower=1;}
        if(night==1&&every(693,1)||night==2&&clockSeconds<140&&every(clockSeconds<70?694:695,1))foxyPressure=0;
        if(every(719,1))nearbyDanger=false;
        if(enemyNearby())nearbyDanger=true;
        // Source events 737-739 run after movement and danger checks.
        if(pos[TC]==OFFICE&&every(738,1))toyChicaOfficeSeconds++;
        if(toyChicaOfficeSeconds>5&&monitor)pos[TC]=CAUGHT;
        if(monitor&&pos[BB]==camera+1&&every(740,5)&&soundRandom.nextInt(20)==1){bbVoiceVolume=.6f;bbVoice(bbVoiceVolume);}
        if(maskOn()&&every(742,1))maskSeconds++;
        // A scene jump is committed after the original finishes this event pass.
        if(won){clearInputs();sounds.clear();}
    }
    void rollMovementOpportunities(){
        // Source events 197-207: the Golden roll is between Chica and Foxy,
        // and is evaluated even when a later viewing/AI condition rejects it.
        for(int i=0;i<9;i++){
            if(i==FOXY){int roll=random.nextInt(20);if(roll<goldenAI&&monitor&&!goldenOffice&&cameraRaiseTicks==0&&cameraDropTicks==0)goldenOffice=true;}
            boolean move=i==FOXY?(21+random.nextInt(5)-foxyPressure<=ai[i]):random.nextInt(20)<ai[i];
            if(move){if(i==FOXY)foxyPressure=0;pendingMove[i]=true;authorizedMove[i]=false;}
        }
        if(random.nextInt(20)<paperAI)paperPending=true;
    }
    void move(int i){
        if(canAuthorize(i))moveAuthorized(i);
    }
    boolean canAuthorize(int i){
        int p=pos[i];if(i<=WC&&camera+1==p)return false;
        if(night!=7){if(i==WF&&(pos[WB]==8||pos[WC]==8))return false;if(i==WC&&pos[WB]==8)return false;
        if(i==TF&&pos[TC]==9){pendingMove[i]=false;return false;}if(i==TC&&pos[TB]==9){pendingMove[i]=false;return false;}}
        return i!=MANGLE||(monitor?camera+1!=p:!hallLight);
    }
    void moveAuthorized(int i){
        int p=pos[i];
        if(hallLight&&((i==WF&&p==3)||(i==WB&&(p==7||p==HALL))||(i==FOXY&&(p==8||p==HALL))||(i==TF&&(p==10||p==HALL))||(i==TC&&(p==7||p==4||p==HALL))||(i==MANGLE&&(p==7||p==HALL||p==1))))return;
        if(i==WF&&p==3&&decidePath==2){pos[i]=7;movementSounds(i,p,pos[i]);return;}
        if(i==WF&&p==NEAR&&maskOn()&&!hallLight){pos[i]=3;stun[i]=5000-night*500;movementSounds(i,p,pos[i]);return;}
        if(i==TC&&p==4){pos[i]=HALL;movementSounds(i,p,pos[i]);return;}
        if(i==BB&&p==2){pos[i]=1;movementSounds(i,p,pos[i]);return;}
        if(i==MANGLE&&p==2&&decidePath==2){pos[i]=1;movementSounds(i,p,pos[i]);return;}
        if(i==MANGLE&&p==1){pos[i]=2;movementSounds(i,p,pos[i]);return;}
        int[] route;
        switch(i){
            case WF:route=new int[]{8,7,3,NEAR,OFFICE};break;
            case WB:route=new int[]{8,7,HALL,1,5,OFFICE};break;
            case WC:route=new int[]{8,4,2,6,OFFICE};break;
            case FOXY:if(p==8){pos[i]=HALL;foxyPressure=0;movementSounds(i,p,pos[i]);}else if(p==HALL)pos[i]=CAUGHT;return;
            case TF:route=new int[]{9,10,HALL,NEAR,OFFICE};break;
            case TB:route=new int[]{9,3,4,2,6,OFFICE};break;
            case TC:route=new int[]{9,7,HALL,1,5,OFFICE};break;
            case MANGLE:route=new int[]{12,11,10,7,HALL,2,6,OFFICE};break;
            default:route=new int[]{10,7,3,1,5,OFFICE};break;
        }
        for(int n=0;n<route.length-1;n++)if(route[n]==p){
            if(route[n+1]==OFFICE){if((i<=WC||i==TF||i==MANGLE||i==BB)&&!monitor)return;if((i==WB||i==WC)&&intruder>=0)return;if(i==TB&&(monitor||rightLight))return;if((i<=WC||i==TF)&&officeOccupied())return;}
            pos[i]=route[n+1];movementSounds(i,p,pos[i]);
            if(i==WB&&p==8){pendingMove[WF]=pendingMove[WC]=authorizedMove[WF]=authorizedMove[WC]=false;}if(i==WC&&p==8)pendingMove[WF]=authorizedMove[WF]=false;
            if(i==TB&&pos[i]==6)stun[i]=500;if(i==TB&&pos[i]==OFFICE)stun[i]=1000-night*100;
            officeTime[i]=0;if(i==TC&&pos[i]==OFFICE)toyChicaOfficeSeconds=0;return;
        }
    }
    boolean maskOn(){return mask&&maskMotion==0;}
    boolean officeOccupied(){for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==OFFICE)return true;return false;}
    void raiseMonitor(){
        if(cameraRaiseTicks>0||cameraDropTicks>0)return;
        if(goldenOffice){goldenOffice=false;goldenCaught=true;}
        bbRaisePending=pos[BB]==OFFICE;mangleRaisePending=pos[MANGLE]==OFFICE;
        cameraRaiseTicks=13;closeAfterRaise=false;wind=flashlight=leftLight=rightLight=false;
        cue("switch","STEREO_CASSETTE__90097704",.7f);
    }
    void resolveCaught(){
        for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==CAUGHT&&maskOn()){
            if(every(i==TF?425:422+i,1)&&random.nextInt(2)==1){attack(scareFor(i));return;}
            if(every(i==TF?594:591+i,1)&&random.nextInt(10)==1){pos[i]=i==WC?4:i==TF?9:7;stun[i]=500;}
        }
        if((every(435,10)||(!monitor&&hallLight))&&pos[FOXY]==CAUGHT&&intruder<0){attack(3);return;}
        if(pos[TB]==CAUGHT&&monitor&&every(565,10)){attack(4);return;}
        if(pos[MANGLE]==CAUGHT){if(monitor&&every(573,1)&&random.nextInt(20)==1)mangleArmed=true;if(mangleArmed&&!monitor){attack(7);return;}}
        if(goldenCaught&&every(434,1))attack(9);
        if(puppetPosition==CAUGHT)attack(8);
    }
    void monitorLowered(){for(int i:new int[]{WF,WB,WC,TB,TC,TF})if(pos[i]==CAUGHT){attack(scareFor(i));return;}if(mangleArmed)attack(7);}
    int scareFor(int actor){return new int[]{0,1,2,3,6,4,5,7,3}[actor];}
    double scareAnimationTime(){return attacker<0?0:Math.max(0,attackAnimationUnits-1)/(Fnaf2Catalog.SPEED[attacker]*60.0);}
    void attack(int who){if(!live())return;sounds.clear();phase=1;toyWalk.active=false;attackDropPending=monitor;cameraRaiseTicks=0;closeAfterRaise=bbRaisePending=mangleRaisePending=false;attacker=who;phaseTime=0;attackFrames=attackAnimationUnits=0;attackFresh=true;boolean lit=flashlight,held=flashHeld,winding=wind;clearInputs();flashlight=lit;flashHeld=held;wind=winding;mask=false;maskMotion=maskAnimationTicks=blackoutAlpha=blackoutFade=0;}
    void setDifficulty(){
        Arrays.fill(ai,0);int h=clockSeconds/70;if(night==2&&h>=1&&difficultyHour<1)rareGolden=(random.nextInt(1000)+1)/1000;goldenAI=rareGolden;difficultyHour=h;
        switch(night){
            case 1:if(h>=2){ai[TB]=2;ai[TC]=2;}if(h>=3){ai[TB]=3;ai[TF]=2;}break;
            case 2:if(h>=1){ai[TF]=2;ai[TB]=ai[TC]=ai[MANGLE]=ai[BB]=3;ai[FOXY]=1;}break;
            case 3:ai[WB]=ai[WC]=ai[BB]=1;ai[FOXY]=2;if(h>=1){ai[WF]=ai[WC]=ai[BB]=2;ai[FOXY]=ai[WB]=3;ai[TB]=ai[TC]=1;}break;
            case 4:ai[MANGLE]=5;ai[BB]=3;ai[FOXY]=7;ai[WB]=1;if(h>=2){ai[WC]=ai[WB]=4;ai[WF]=3;ai[TB]=1;}break;
            case 5:ai[TF]=ai[BB]=ai[FOXY]=5;ai[WF]=ai[WB]=ai[WC]=ai[TB]=ai[TC]=2;ai[MANGLE]=1;if(h>=1){ai[WF]=ai[WB]=ai[WC]=5;ai[FOXY]=7;ai[MANGLE]=10;ai[TF]=1;}break;
            case 6:ai[WF]=ai[WB]=ai[WC]=ai[BB]=5;ai[MANGLE]=3;ai[FOXY]=10;if(h>=2){ai[WF]=ai[WB]=ai[WC]=ai[MANGLE]=10;ai[TB]=ai[TC]=ai[TF]=5;ai[BB]=9;ai[FOXY]=15;goldenAI=3;}break;
            case 7:int[] preset=selectedLevels();for(int i=0;i<9;i++)ai[i]=Math.min(i==FOXY?17:15,preset[i]);goldenAI=Math.min(10,preset[9]);break;
        }
    }
    boolean live(){return playing&&!paused&&!dead&&!won&&phase==0;}
    void camera(int index){if(live()&&monitor&&!quickWind){camera=Math.floorMod(index,12);wind=false;}}
    void toggleMonitor(){if(!live()||quickWind||mask||maskMotion!=0||intruder>=0||cameraRaiseTicks>0||cameraDropTicks>0)return;clearInputs();if(monitor)forceMonitorDown();else raiseMonitor();}
    void toggleMask(){if(!live()||quickWind||maskMotion!=0||cameraRaiseTicks>0||(mask&&intruder>=0))return;if(mask)for(int i:new int[]{WF,WB,WC,TF})if(pos[i]==CAUGHT){attack(scareFor(i));return;}mask=!mask;maskMotion=mask?1:-1;maskAnimationTicks=0;maskFrames=0;if(mask&&monitor){forceMonitorDown();}clearInputs();}
    int maskImage(){if(maskMotion==0)return mask?125:-1;int[] frames=maskMotion==1?MASK_DOWN:MASK_UP;return frames[Math.min(frames.length-1,maskAnimationTicks*3/4)];}
    void setFlashlight(boolean on){if(on&&!flashHeld&&live()&&!mask&&maskMotion==0&&battery>0&&intruder<0&&bbInside)cue("light-error","error",.5f);flashHeld=on;flashlight=playing&&!paused&&!dead&&!won&&cameraRaiseTicks==0&&!mask&&maskMotion==0&&battery>0&&intruder<0&&!bbInside&&on;}
    void setWind(boolean on){wind=live()&&monitor&&camera==10&&on;}
    void setVent(boolean left,boolean on){boolean held=left?leftVentHeld:rightVentHeld;if(on&&!held&&live()&&!monitor&&!mask&&maskMotion==0&&bbInside)cue("light-error","error",.5f);boolean lit=live()&&cameraRaiseTicks==0&&!monitor&&!mask&&maskMotion==0&&!bbInside&&on;if(left){leftLight=lit;leftVentHeld=on;}else{rightLight=lit;rightVentHeld=on;}}
    int hour(){int h=clockSeconds/70;return h==0?12:Math.min(h,6);}
    int officeImage(){
        int a=35;if(flashlight&&!monitor){a=goldenHall?93:36;if(pos[MANGLE]==HALL)a=84;if(pos[FOXY]==HALL)a=pos[MANGLE]==HALL?98:76;if(pos[TC]==HALL)a=50;if(pos[TF]==HALL)a=55;if(pos[WB]==HALL)a=pos[FOXY]==HALL?97:58;if(pos[TF]==NEAR)a=56;if(pos[WF]==NEAR)a=73;}
        if(hallBlackout())a=99;
        if(leftLight)a=pos[BB]==OFFICE?82:pos[TC]==OFFICE?53:37;
        if(rightLight)a=pos[MANGLE]==OFFICE?83:pos[TB]==OFFICE?47:38;
        if(pos[WB]==OFFICE)a=61;if(pos[WC]==OFFICE)a=69;if(pos[WF]==OFFICE)a=74;
        return Fnaf2Catalog.SCENE[a];
    }
    int cameraImage(){
        int c=camera+1;boolean f=flashlight;int a=-1;
        if(c==9)a=pos[TB]==9&&pos[TC]==9?(f?12:46):pos[TC]==9?(f?40:39):pos[TF]==9?(f?42:41):89;
        else if(c==10)a=pos[TF]==10&&f?(pos[BB]==10?54:86):pos[BB]==10?(f?18:17):(f?87:85);
        else if(c==11)a=!f?25:puppetStage==0?26:puppetStage==1?77:puppetStage==2?78:79;
        else if(c==12)a=!f?19:pos[MANGLE]==12?20:80;
        else if(c==8)a=!f?23:pos[WB]==8?24:pos[WC]==8?62:pos[WF]==8?63:pos[FOXY]==8?75:64;
        else if(c==7)a=pos[WB]==7&&f?57:pos[WF]==7&&f?70:pos[TC]==7?(f?49:48):(f?22:21);
        else if(c==5)a=!f?13:pos[WB]==5?60:pos[TC]==5?52:pos[BB]==5?100:14;
        else if(c==6)a=!f?15:pos[WC]==6?68:pos[TB]==6?45:pos[MANGLE]==6?81:16;
        else if(c==1)a=!f?27:pos[WB]==1?59:pos[TC]==1?51:28;
        else if(c==2)a=pos[WC]==2?(f?66:65):pos[TB]==2&&f?44:(f?34:33);
        else if(c==3)a=pos[WF]==3?(f?72:71):pos[TB]==3&&f?43:(f?30:29);
        else if(c==4)a=pos[TB]==4?(f?91:90):!f?31:pos[WC]==4?67:pos[TC]==4?92:32;
        if(c==4&&f&&pos[WC]!=4&&pos[TB]!=4&&paperOffice)a=88;
        if(c==5&&f&&pos[WB]!=5&&pos[TC]!=5&&randomImage==3)a=96;
        if(c==8&&f&&pos[WF]!=8&&pos[FOXY]!=8&&randomImage<=100)a=94;
        if(c==11&&f&&puppetStage>=3&&randomImage<=100)a=95;
        return a<0?CAMERA_IMAGE[camera]:Fnaf2Catalog.SCENE[a];
    }
}
