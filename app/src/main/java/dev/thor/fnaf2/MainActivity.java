package dev.thor.fnaf2;

import android.app.*;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;

public final class MainActivity extends Activity implements DisplayManager.DisplayListener {
    Fnaf2State state=new Fnaf2State();final Handler handler=new Handler(Looper.getMainLooper());
    final ScarePlayback scarePlayback=new ScarePlayback();
    final DreamSequence dream=new DreamSequence();
    final DeathMinigame minigame=new DeathMinigame();MinigameArt minigameArt;boolean deathMinigame;
    DisplayManager displays;Presentation presentation;Fnaf2View top,bottom;Fnaf2Assets assets;Fnaf2Audio audio;
    SharedPreferences prefs;boolean resumed,scareLoading,scareReady,winStarted,cheered,deathStarted,phoneStarted;
    int selected,page,shopSelection,challengeSelection,challengeEarned,unlocked=1,points,owned,equipped,earned,intro,ending;double introTime,endingTime;
    long last;float hatX,hatY;int scareGeneration;
    boolean beat20,perspectiveEnabled;int settingSelection,aiSelection,lookSetting=1,cameraSetting=1;
    static final String[] SPEED_NAMES={"Low","Normal","High","Very high"};
    static final double[] SPEEDS={.6,1,1.4,1.8};
    final int[] editedAI=new int[10];
    boolean aHeld,leftKey,rightKey,leftAxis,rightAxis,rightHeld,controllerFlash;
    final ButtonChord skipChord=new ButtonChord(63),scareChord=new ButtonChord(7);
    static final String[] PLUSH_NAMES={"Freddy","Bonnie","Chica","Foxy","Golden Freddy"};
    static final int[] PLUSH_IMAGES={601,603,604,606,611},PRICES={150,200,250,350,1000};
    boolean arcadeReplay,arcadeReady;int arcadeSelection,arcadeUnlocked;
    boolean controllerInput;float arcadeStickX,arcadeStickY;
    static final int[] ARCADE_KINDS={1,2,3,5};
    static final String[] ARCADE_NAMES={"SAVE THEM","Take Cake to the Children","Give Gifts, Give Life","Foxy Go! Go! Go!"};
    boolean developerBuild(){return getPackageName().endsWith(".dev");}
    String notice="";EncounterArchive encounters;int encounterSelection;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);encounters=new EncounterArchive(this);prefs=getSharedPreferences("fnaf2",MODE_PRIVATE);unlocked=prefs.getInt("unlocked",1);state.night=Math.min(unlocked,6);
        points=prefs.getInt("points",0);owned=prefs.getInt("owned",0);equipped=prefs.getInt("equipped",0);earned=prefs.getInt("earned",0);challengeEarned=prefs.getInt("challengeEarned",0);
        arcadeUnlocked=prefs.getInt("arcadeUnlocked",0);
        state.halloweenMode=Math.max(0,Math.min(2,prefs.getInt("halloweenMode",0)));
        beat20=prefs.getBoolean("beat20",(challengeEarned&512)!=0);
        perspectiveEnabled=prefs.getBoolean("perspective",false);
        lookSetting=Math.max(0,Math.min(3,prefs.getInt("lookSensitivity",1)));cameraSetting=Math.max(0,Math.min(3,prefs.getInt("cameraSensitivity",1)));state.lookSensitivity=SPEEDS[lookSetting];state.cameraSensitivity=SPEEDS[cameraSetting];
        if(developerBuild()){unlocked=7;earned=63;challengeEarned=1023;beat20=true;owned=equipped=31;points=100000;arcadeUnlocked=15;state.night=6;}
        assets=new Fnaf2Assets(this);audio=new Fnaf2Audio(assets);assets.warm();
        displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);displays.registerDisplayListener(this,handler);
        top=new Fnaf2View(this,this,true);setContentView(top);fullscreen(getWindow());
    }
    static void fullscreen(Window w){w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);w.getDecorView().setSystemUiVisibility(5894|1024|512);}
    @Override protected void onResume(){super.onResume();resumed=true;connect();last=SystemClock.elapsedRealtimeNanos();handler.post(frame);audio.pause(state.paused||minigame.paused);fullscreen(getWindow());}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(frame);state.pauseForInterruption();resetControls();if(minigame.active)minigame.paused=true;audio.pause(true);dismiss();super.onPause();}
    @Override protected void onDestroy(){scareGeneration++;displays.unregisterDisplayListener(this);handler.removeCallbacksAndMessages(null);audio.stopAll();if(minigameArt!=null)minigameArt.close();encounters.close();assets.close();super.onDestroy();}
    void dismiss(){if(presentation!=null)presentation.dismiss();presentation=null;bottom=null;}
    void connect(){if(!resumed)return;Display target=null;int current=top.getDisplay()==null?0:top.getDisplay().getDisplayId();for(Display d:displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)){if(d.getDisplayId()==current)continue;if(target==null||d.getName().equalsIgnoreCase("Screen-2"))target=d;}if(target==null){dismiss();return;}if(presentation!=null&&presentation.getDisplay().getDisplayId()==target.getDisplayId())return;dismiss();try{presentation=new Presentation(this,target,android.R.style.Theme_Material_NoActionBar_Fullscreen){@Override public boolean dispatchKeyEvent(KeyEvent e){return keys(e)||super.dispatchKeyEvent(e);}@Override public boolean dispatchGenericMotionEvent(MotionEvent e){return motion(e)||super.dispatchGenericMotionEvent(e);}};bottom=new Fnaf2View(presentation.getContext(),this,false);presentation.setContentView(bottom);presentation.setCancelable(false);presentation.show();fullscreen(presentation.getWindow());}catch(WindowManager.InvalidDisplayException e){dismiss();}}
    @Override public void onDisplayAdded(int id){connect();}@Override public void onDisplayRemoved(int id){if(presentation!=null&&presentation.getDisplay().getDisplayId()==id){state.pauseForInterruption();resetControls();audio.pause(state.paused);}connect();}@Override public void onDisplayChanged(int id){connect();}
    final Runnable frame=new Runnable(){public void run(){if(!resumed)return;long now=SystemClock.elapsedRealtimeNanos();double dt=Math.max(0,(now-last)/1e9);last=now;
        if(minigame.active){updateMinigame(dt);}
        else if(dream.active()){int prior=dream.ticks;double oldCenter=dream.center;dream.tick(dt);if(dream.ticks<1980)audio.loop("dream-pan","machineturn2",oldCenter!=dream.center?.5f:0);if(prior<1800&&dream.ticks>=1800)audio.play("dream-voice","Robot",false,1);if(prior<1920&&dream.ticks>=1920)audio.play("dream-voice","staticend2",false,1);if(prior<1980&&dream.ticks>=1980)audio.stopAll();if(dream.done()){state.night=dream.nextNight;dream.nextNight=0;begin();}}
        else if(ending>0){endingTime+=dt;if(endingTime>=15){ending=0;audio.stopAll();}}
        else if(intro>0){introTime+=dt;if(intro==1&&introTime>=6){intro=2;introTime=0;audio.play("switch","blip3",false,.7f);}else if(intro==2&&introTime>=2.2){intro=0;state.start();audio.startNight(state.night);}}
        else if(state.playing){
            startScareIfNeeded();
            state.tick(dt);refreshFlashlight();
            startScareIfNeeded();
            if(state.shadowEnded){state.stop();audio.stopAll();selected=0;}
            if(state.live()){prefetchThreat();audio.update(state);if(state.elapsed>=2&&!phoneStarted){phoneStarted=true;if(state.night<=6)audio.play("phone","call "+state.night+"b",false,.8f);}}
            if(state.won&&!winStarted){winStarted=true;audio.stopAll();audio.play("bells","Clocks_Chimes_Cl_02480702",false,1);int reward;
                if(state.night==7){int flag=state.challenge<0?0:1<<state.challenge;int total=0;for(int n:state.selectedLevels())total+=n;if(total==200)beat20=true;reward=50+total;if(flag!=0&&(challengeEarned&flag)==0){reward+=challengeReward(state.challenge);challengeEarned|=flag;}}
                else{int flag=1<<(state.night-1);reward=25*state.night;if((earned&flag)==0){reward+=100*state.night;earned|=flag;}unlocked=Math.max(unlocked,Math.min(6,state.night+1));}points+=reward;notice="Shift complete  +"+reward+" points";save();}
            if(state.won&&state.resultTime>=3&&!cheered){cheered=true;audio.play("cheer","CROWD_SMALL_CHIL_EC049202",false,.85f);}
            if(state.won&&state.resultTime>8&&audio.finished("bells")&&audio.finished("cheer")){int n=state.night;state.stop();audio.stopAll();if(n>=5){ending=n==5?587:n==6?590:622;endingTime=0;assets.image(ending);audio.play("ending","musicbox2",true,.75f);state.night=Math.min(unlocked,6);}else if(n>=2){startDream(n+1);}else{state.night=2;begin();}selected=0;}
            if(state.dead&&!deathStarted){deathStarted=true;if(state.random instanceof SourceRandom)((SourceRandom)state.random).beginScene();deathMinigame=state.random.nextInt(10)==0;audio.stopAll();audio.play("static","stare",true,1);}
            if(state.dead&&state.resultTime>=5){audio.stop("static");if(deathMinigame){state.stop();startMinigame();}else if(state.resultTime>=15){state.stop();state.night=Math.min(unlocked,6);state.challenge=-1;audio.stopAll();selected=0;}}
        }else if(assets.ready)audio.menu();
        seedDeveloperEncounters();top.invalidate();if(bottom!=null)bottom.invalidate();handler.postDelayed(this,16);
    }};
    long nextSceneSeed;int anticipatedScare=-1;
    void prefetchThreat(){
        int who=state.intruder>=0?state.scareFor(state.intruder):-1;
        if(who<0)for(int actor:new int[]{Fnaf2State.WF,Fnaf2State.WB,Fnaf2State.WC,Fnaf2State.TB,Fnaf2State.TC,Fnaf2State.TF,Fnaf2State.MANGLE})if(state.pos[actor]==Fnaf2State.CAUGHT){who=state.scareFor(actor);break;}
        if(who<0&&(state.goldenOffice||state.goldenCaught))who=9;
        if(who<0&&state.puppetStage>=2)who=8;
        if(who<0&&(state.pos[Fnaf2State.FOXY]==Fnaf2State.HALL||state.pos[Fnaf2State.FOXY]==Fnaf2State.CAUGHT))who=3;
        if(who>=0&&who!=anticipatedScare){anticipatedScare=who;assets.prepareScare(who,()->{});}
    }
    void startScareIfNeeded(){
        if(state.phase!=1||state.dead||state.won||scareLoading)return;
        scareLoading=true;scarePlayback.reset();int generation=++scareGeneration,attacker=state.attacker;
        audio.stopAll();if(state.cameraDropTicks>0)audio.play("switch","STEREO_CASSETTE__90097701",false,.5f);
        audio.play("scare","Xscream3",false,1);audio.pause(!resumed||state.paused);
        assets.prepareScare(attacker,()->handler.post(()->{if(!isDestroyed()&&!isFinishing()&&generation==scareGeneration&&state.playing&&state.phase==1&&state.attacker==attacker)scareReady=true;}));
    }
    void seedDeveloperEncounters(){
        if(!developerBuild()||!assets.ready||top==null||state.playing||minigame.active||SystemClock.uptimeMillis()<nextSceneSeed)return;
        nextSceneSeed=SystemClock.uptimeMillis()+250;
        for(int i=0;i<EncounterCatalog.TITLES.length;i++)if(encounters.needsDeveloperScene(i)){
            android.graphics.Bitmap image=top.developerScene(i);if(image!=null)encounters.saveDeveloperScene(i,image);return;
        }
    }
    void openArcade(){page=7;arcadeReady=false;if(minigameArt!=null)minigameArt.close();minigameArt=new MinigameArt(assets);MinigameArt art=minigameArt;art.prepare(()->handler.post(()->{if(minigameArt==art)arcadeReady=true;}));}
    void replayArcade(){if((arcadeUnlocked&(1<<arcadeSelection))==0){notice="Discover this minigame during a normal shift first";return;}if(minigameArt!=null)minigameArt.close();startMinigame();arcadeReplay=true;minigame.start(ARCADE_KINDS[arcadeSelection]-1);}
    void startMinigame(){
        arcadeReplay=false;
        resetControls();scareGeneration++;scareLoading=scareReady=false;scarePlayback.reset();audio.stopAll();audio.play("mini-static","staticend2",false,1);
        minigame.start(prefs.getInt("minigameTurn",0));minigameArt=new MinigameArt(assets);MinigameArt art=minigameArt;minigame.collision=art::overlap;
        art.prepare(()->handler.post(()->{if(minigame.active&&minigameArt==art)minigame.ready=true;}));
    }
    void updateMinigame(double dt){
        float targetX=(minigame.right||minigame.stickRight?1:0)-(minigame.left||minigame.stickLeft?1:0);
        float targetY=(minigame.down||minigame.stickDown?1:0)-(minigame.up||minigame.stickUp?1:0);
        float follow=(float)(1-Math.exp(-22*dt));
        arcadeStickX+=(targetX-arcadeStickX)*follow;arcadeStickY+=(targetY-arcadeStickY)*follow;
        if(minigame.paused)return;
            boolean started=minigame.gameStarted;int end=minigame.endTicks;minigame.tick(dt);
            if(minigame.turnChanged){if(!arcadeReplay)prefs.edit().putInt("minigameTurn",minigame.turn).apply();minigame.turnChanged=false;}
            if(!started&&minigame.gameStarted){if(minigame.kind!=DeathMinigame.SAVE)assets.prepareScare(minigame.kind==DeathMinigame.CAKE?8:minigame.kind==DeathMinigame.GIFTS?9:3,()->{});if(!arcadeReplay){for(int i=0;i<ARCADE_KINDS.length;i++)if(minigame.kind==ARCADE_KINDS[i])arcadeUnlocked|=1<<i;prefs.edit().putInt("arcadeUnlocked",arcadeUnlocked).apply();}audio.stopAll();audio.play("mini-background",minigame.kind==DeathMinigame.SAVE?"ComputerInteriorLong_EVL02_14":"staticend2",true,minigame.kind==DeathMinigame.SAVE?.5f:.2f);}
            if(end==0&&minigame.endTicks>0){scareGeneration++;scareLoading=scareReady=false;audio.stopAll();audio.play("mini-static","staticend2",false,1);}
            for(DeathMinigame.Cue cue:minigame.cues)audio.play("mini-"+(cue.name.endsWith("2")&&cue.name.length()==2?"voice":"effect"),cue.name,false,cue.volume);minigame.cues.clear();
            if(minigame.kind==DeathMinigame.FOXY&&minigame.cycles>=1)audio.loop("mini-background","staticend2",.4f);
        if(minigame.attacker>=0&&!scareLoading){scareLoading=true;scareReady=false;scarePlayback.reset();int who=minigame.attacker,generation=++scareGeneration;audio.play("mini-attack","Xscream2",false,1);audio.pause(!resumed||minigame.paused);assets.prepareScare(who,()->handler.post(()->{if(minigame.active&&minigame.attacker==who&&generation==scareGeneration&&!isDestroyed())scareReady=true;}));}
        if(minigame.finished){minigame.active=false;minigameArt.close();minigameArt=null;scareGeneration++;scareReady=scareLoading=false;audio.stopAll();state.stop();state.night=Math.min(unlocked,6);state.challenge=-1;page=selected=0;if(arcadeReplay){arcadeReplay=false;openArcade();}}
    }
    void startDream(int nextNight){dream.start(nextNight);resetControls();for(int id:new int[]{616,615,613,592,617,609,614,624,626,619,628})assets.image(id);audio.play("dream","Scary_Space_B",true,1);audio.play("dream-ambient","ambience2",true,1);}
    void save(){prefs.edit().putBoolean("beat20",beat20).putInt("unlocked",unlocked).putInt("earned",earned).putInt("challengeEarned",challengeEarned).putInt("points",points).putInt("owned",owned).putInt("equipped",equipped).apply();}
    void begin(){if(!assets.ready||assets.importing)return;scareGeneration++;resetControls();if(state.night==7&&state.customLevels==null&&(state.challenge<0||state.challenge>=Fnaf2State.PRESETS.length))state.night=Math.min(unlocked,6);audio.stopAll();intro=state.night==1?1:2;introTime=0;page=0;scareLoading=scareReady=winStarted=cheered=deathStarted=phoneStarted=false;anticipatedScare=3;assets.prepareScare(3,()->{});audio.play("switch","blip3",false,.7f);}
    void importData(){
        if(assets.importing)return;
        state.pauseForInterruption();resetControls();audio.pause(true);
        new AlertDialog.Builder(this).setTitle("Scan FNaF 2 Steam file")
            .setMessage("Copy FiveNightsatFreddys2.exe from your Steam installation to this device, then select it. The game data is prepared here, offline. An existing FNaF 2 data ZIP also works.")
            .setPositiveButton("Select Steam file",(dialog,which)->chooseSteamFile()).setNegativeButton("Cancel",null).show();
    }
    void chooseSteamFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,1);}catch(ActivityNotFoundException ex){new AlertDialog.Builder(this).setMessage("Enable Android's Files app to select your Steam EXE or data ZIP.").setPositiveButton("OK",null).show();}
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==1&&result==RESULT_OK&&data!=null&&data.getData()!=null)assets.importPack(data.getData(),()->handler.post(()->{
            if(isDestroyed()||isFinishing())return;
            boolean failed=assets.status.startsWith("Import failed");
            if(!failed){state.stop();audio.stopAll();assets.warm();page=selected=0;}
            top.invalidate();if(bottom!=null)bottom.invalidate();
            new AlertDialog.Builder(this).setTitle(failed?"Import failed":"Ready to play")
                .setMessage(failed?assets.status:"Your Steam data is stored on this device. Start a night from the main menu.").setPositiveButton("OK",null).show();
        }));
    }
    String[] menu(){return new String[]{"Continue","Night "+state.night,"Challenges","Achievements","Office Shop","Gallery","Arcade","Settings","New Game"};}
    static int challengeReward(int index){int total=0;for(int n:Fnaf2State.PRESETS[index])total+=n;return 200+total*4;}
    void navigate(int direction){if(page==7)arcadeSelection=Math.floorMod(arcadeSelection+direction,4);else if(page==8)encounterSelection=Math.floorMod(encounterSelection+direction,EncounterCatalog.TITLES.length);else if(page==6)aiSelection=Math.floorMod(aiSelection+direction,11);else if(page==5)settingSelection=Math.floorMod(settingSelection+direction,9);else if(page==2)shopSelection=Math.floorMod(shopSelection+direction,5);else if(page==4)challengeSelection=Math.floorMod(challengeSelection+direction,10);else if(page==0)selected=Math.floorMod(selected+direction,menu().length);audio.play("select","blip3",false,.45f);}
    void togglePerspective(){perspectiveEnabled=!perspectiveEnabled;prefs.edit().putBoolean("perspective",perspectiveEnabled).apply();if(!perspectiveEnabled){top.perspective.release();if(bottom!=null)bottom.perspective.release();}audio.play("select","blip3",false,.45f);}
    void editAI(){System.arraycopy(Fnaf2State.PRESETS[challengeSelection],0,editedAI,0,10);aiSelection=0;page=6;}
    void adjustAI(int direction){if(aiSelection<10)editedAI[aiSelection]=Math.max(0,Math.min(20,editedAI[aiSelection]+direction));}
    void confirm(){if(page==7){replayArcade();return;}if(page==8){page=0;return;}if(page==6){if(aiSelection<10){adjustAI(1);return;}if((earned&32)==0){notice="Complete Night 6 to unlock Custom Night";return;}state.customLevels=editedAI.clone();state.challenge=-1;state.night=7;begin();return;}if(page==5){if(settingSelection==0)togglePerspective();else if(settingSelection==1)page=3;else if(settingSelection==2)importData();else if(settingSelection==3){lookSetting=(lookSetting+1)%4;state.lookSensitivity=SPEEDS[lookSetting];prefs.edit().putInt("lookSensitivity",lookSetting).apply();}else if(settingSelection==4){cameraSetting=(cameraSetting+1)%4;state.cameraSensitivity=SPEEDS[cameraSetting];prefs.edit().putInt("cameraSensitivity",cameraSetting).apply();}else if(settingSelection==8){state.halloweenMode=(state.halloweenMode+1)%3;java.util.Calendar today=java.util.Calendar.getInstance();state.halloween=Fnaf2State.seasonal(state.halloweenMode,today.get(java.util.Calendar.MONTH)+1,today.get(java.util.Calendar.DAY_OF_MONTH));prefs.edit().putInt("halloweenMode",state.halloweenMode).apply();}else if(settingSelection==7){openArcade();}else if(settingSelection==6){page=8;encounterSelection=0;}else{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/Maddex712/fnaf2-redone")));}catch(ActivityNotFoundException ex){notice="No browser installed";}}return;}if(page==0&&selected==7){page=5;settingSelection=0;return;}if(!assets.ready){importData();return;}if(page==2){int flag=1<<shopSelection;if((owned&flag)==0){if(points<PRICES[shopSelection]){notice="Not enough points";return;}points-=PRICES[shopSelection];owned|=flag;equipped|=flag;notice=PLUSH_NAMES[shopSelection]+" purchased";}else{equipped^=flag;notice=(equipped&flag)!=0?"Placed in your office":"Removed from office";}save();audio.play("select","coin",false,.5f);return;}if(page==4){if((earned&32)==0){notice="Complete Night 6 to unlock Custom Night";return;}state.customLevels=null;state.challenge=challengeSelection;state.night=7;begin();return;}if(page!=0){page=page==3?5:page==6?4:0;return;}switch(selected){case 0:state.customLevels=null;state.challenge=-1;begin();break;case 1:state.night=state.night>=unlocked?1:state.night+1;break;case 2:page=4;break;case 3:page=1;break;case 4:page=2;break;case 5:page=8;encounterSelection=0;break;case 6:openArcade();break;case 7:page=5;settingSelection=0;break;case 8:state.customLevels=null;state.challenge=-1;state.night=1;begin();break;}}
    void monitor(){state.toggleMonitor();refreshFlashlight();}
    void mask(){boolean before=state.mask;state.toggleMask();if(before!=state.mask)audio.play("switch",state.mask?"FENCING_43_GEN-HDF10954":"FENCING_42_GEN-HDF10953",false,.65f);}
    static final int[] CAMERA_ROUTE={4,0,2,6,7,8,10,11,9,3,5,1};
    void stepCamera(int direction){for(int i=0;i<CAMERA_ROUTE.length;i++)if(CAMERA_ROUTE[i]==state.camera){camera(CAMERA_ROUTE[Math.floorMod(i+direction,CAMERA_ROUTE.length)]);return;}}
    void camera(int n){int before=state.camera;state.camera(n);if(before!=state.camera)audio.play("switch","blip3",false,.45f);assets.image(state.cameraImage());}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return keys(e)||super.dispatchKeyEvent(e);}
    @Override public void onBackPressed(){back();}
    void back(){
        if(minigame.active){minigame.paused=!minigame.paused;resetControls();audio.pause(minigame.paused);return;}
        if(dream.active()||intro>0||ending>0)return;
        if(!state.playing){if(page==7&&minigameArt!=null){minigameArt.close();minigameArt=null;arcadeReady=false;}page=page==3?5:page==6?4:0;return;}
        if(state.won||state.dead||state.phase!=0)return;
        state.paused=!state.paused;resetControls();audio.pause(state.paused);
    }
    final InputProfile inputProfile=new InputProfile();
    boolean ctrlLeft,ctrlRight;
    void useInput(int mode){if(inputProfile.select(mode)){resetControls();if(top!=null)top.clearPointer();if(bottom!=null)bottom.clearPointer();}controllerInput=mode==InputProfile.CONTROLLER;}
    boolean keys(KeyEvent e){int k=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(down&&e.getRepeatCount()==0)useInput(InputProfile.controllerSource(e.getSource())?InputProfile.CONTROLLER:InputProfile.PC);
        if(k==KeyEvent.KEYCODE_CTRL_LEFT||k==KeyEvent.KEYCODE_CTRL_RIGHT){if(k==KeyEvent.KEYCODE_CTRL_LEFT)ctrlLeft=down;else ctrlRight=down;if(inputProfile.mode==InputProfile.PC&&intro==0&&ending==0)state.setFlashlight(ctrlLeft||ctrlRight);return true;}
        if(k==KeyEvent.KEYCODE_ESCAPE){if(down&&e.getRepeatCount()==0)back();return true;}
        if(down&&e.getRepeatCount()>0)return true;
        if(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_BUTTON_SELECT){if(down)back();return true;}
        if(minigame.active){if(k==KeyEvent.KEYCODE_BUTTON_START){if(down)back();return true;}if(!minigame.paused){if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_A)minigame.left=down;if(k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_D)minigame.right=down;if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_W)minigame.up=down;if(k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_S)minigame.down=down;}return true;}
        if(dream.active()){if(k==KeyEvent.KEYCODE_DPAD_LEFT)dream.pan=down?-1:0;if(k==KeyEvent.KEYCODE_DPAD_RIGHT)dream.pan=down?1:0;return true;}
        if(!down){if(k==KeyEvent.KEYCODE_BUTTON_A)aHeld=false;if(k==KeyEvent.KEYCODE_BUTTON_L2)leftKey=false;if(k==KeyEvent.KEYCODE_BUTTON_R2)rightKey=false;controllerTriggers();}
        if(shortcut(k,down))return true;
        if(ending>0)return true;if(intro>0)return k>=KeyEvent.KEYCODE_BUTTON_A&&k<=KeyEvent.KEYCODE_BUTTON_MODE;
        if(!state.playing){if(!down)return false;if(k==KeyEvent.KEYCODE_DPAD_UP){navigate(-1);return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN){navigate(1);return true;}if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){if(page==6)adjustAI(k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);else if(page==2||page==4)navigate(k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);else if(page==0&&(selected==0||selected==1))state.night=Math.max(1,Math.min(unlocked,state.night+(k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));return true;}if(k==KeyEvent.KEYCODE_BUTTON_Y&&page==4){editAI();return true;}if(k==KeyEvent.KEYCODE_BUTTON_A||k==KeyEvent.KEYCODE_ENTER){confirm();return true;}if(k==KeyEvent.KEYCODE_BUTTON_B||k==KeyEvent.KEYCODE_BACK){page=page==3?5:page==6?4:0;return true;}return false;}
        if(inputProfile.mode==InputProfile.PC)return true;
        if(state.won||state.dead)return false;
        if(state.phase==1){if(k==KeyEvent.KEYCODE_BUTTON_A){aHeld=down;refreshFlashlight();return true;}if(k==KeyEvent.KEYCODE_BUTTON_L2){leftKey=down;refreshFlashlight();return true;}return false;}
        if(k==KeyEvent.KEYCODE_BUTTON_START){if(down){state.paused=!state.paused;resetControls();audio.pause(state.paused);}return true;}
        if(state.paused){if(down&&k==KeyEvent.KEYCODE_BUTTON_B){state.stop();state.night=Math.min(unlocked,6);state.challenge=-1;audio.stopAll();}return true;}
        switch(k){case KeyEvent.KEYCODE_BUTTON_L2:leftKey=down;controllerTriggers();break;case KeyEvent.KEYCODE_BUTTON_THUMBL:case KeyEvent.KEYCODE_BUTTON_THUMBR:break;case KeyEvent.KEYCODE_BUTTON_X:if(down)monitor();break;case KeyEvent.KEYCODE_BUTTON_B:if(down)mask();break;case KeyEvent.KEYCODE_BUTTON_R2:rightKey=down;controllerTriggers();break;case KeyEvent.KEYCODE_BUTTON_A:aHeld=down;refreshFlashlight();break;case KeyEvent.KEYCODE_BUTTON_L1:state.setVent(true,down);break;case KeyEvent.KEYCODE_BUTTON_R1:state.setVent(false,down);break;case KeyEvent.KEYCODE_DPAD_LEFT:if(down)stepCamera(-1);break;case KeyEvent.KEYCODE_DPAD_RIGHT:if(down)stepCamera(1);break;case KeyEvent.KEYCODE_DPAD_UP:if(down)camera(state.camera-3);break;case KeyEvent.KEYCODE_DPAD_DOWN:if(down)camera(state.camera+3);break;case KeyEvent.KEYCODE_BUTTON_Y:if(down)audio.stop("phone");break;default:return false;}return true;
    }
    void resetControls(){ctrlLeft=ctrlRight=false;minigame.clearInputs();arcadeStickX=arcadeStickY=0;dream.pan=0;state.setQuickWind(false);state.clearInputs();skipChord.reset();scareChord.reset();hatX=hatY=0;aHeld=leftKey=rightKey=leftAxis=rightAxis=rightHeld=controllerFlash=false;}
    void controllerTriggers(){
        boolean right=rightKey||rightAxis;
        if(right!=rightHeld){rightHeld=right;boolean before=state.monitor,maskBefore=state.mask;if(!right||(state.live()&&intro==0&&ending==0))state.setQuickWind(right);
            if(maskBefore!=state.mask)audio.play("switch","FENCING_42_GEN-HDF10953",false,.65f);
            if(state.monitor)assets.image(state.cameraImage());
        }
        refreshFlashlight();
    }
    void refreshFlashlight(){
        if(inputProfile.mode==InputProfile.PC){if((ctrlLeft||ctrlRight)&&intro==0&&ending==0)state.setFlashlight(true);return;}
        boolean active=state.playing&&!state.paused&&!state.dead&&!state.won&&intro==0&&ending==0&&(aHeld||(state.monitor&&(leftKey||leftAxis)));
        if(active||controllerFlash)state.setFlashlight(active);
        controllerFlash=active;
    }
    static double stickAxis(float value){return Math.abs(value)<=.15?0:Math.copySign((Math.abs(value)-.15)/.85,value);}
    int inputMode(){return InputRouting.mode(state,intro,ending);}
    boolean shortcut(int key,boolean down){
        int bit=0,scareBit=0;
        switch(key){case KeyEvent.KEYCODE_BUTTON_L1:bit=ButtonChord.L1;break;case KeyEvent.KEYCODE_BUTTON_R1:bit=ButtonChord.R1;break;case KeyEvent.KEYCODE_BUTTON_L2:bit=ButtonChord.L2;break;case KeyEvent.KEYCODE_BUTTON_R2:bit=ButtonChord.R2;break;case KeyEvent.KEYCODE_BUTTON_A:bit=ButtonChord.A;break;case KeyEvent.KEYCODE_BUTTON_B:bit=ButtonChord.B;break;case KeyEvent.KEYCODE_BUTTON_THUMBL:scareBit=1;break;case KeyEvent.KEYCODE_BUTTON_THUMBR:scareBit=2;break;case KeyEvent.KEYCODE_BUTTON_Y:scareBit=4;break;}
        boolean skip=bit!=0&&skipChord.button(bit,down),scare=scareBit!=0&&scareChord.button(scareBit,down);
        if(state.live()&&intro==0&&ending==0){if(skip){state.skipToLastFiveSeconds();return true;}if(scare){state.randomScare();return true;}}
        return false;
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return motion(e)||super.dispatchGenericMotionEvent(e);}
    boolean motion(MotionEvent e){if((e.getSource()&InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK)return false;
        boolean active=false;for(int axis:new int[]{MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RX,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_BRAKE,MotionEvent.AXIS_GAS})if(Math.abs(e.getAxisValue(axis))>.2f){active=true;break;}
        if(active)useInput(InputProfile.CONTROLLER);
        if(inputProfile.mode!=InputProfile.CONTROLLER)return true;
        if(minigame.active){if(!minigame.paused){float mx=e.getAxisValue(MotionEvent.AXIS_X)+e.getAxisValue(MotionEvent.AXIS_HAT_X),my=e.getAxisValue(MotionEvent.AXIS_Y)+e.getAxisValue(MotionEvent.AXIS_HAT_Y);minigame.stickLeft=mx<-.3;minigame.stickRight=mx>.3;minigame.stickUp=my<-.3;minigame.stickDown=my>.3;}return true;}
        if(dream.active()){dream.pan=stickAxis(e.getAxisValue(MotionEvent.AXIS_X));return true;}
        float x=e.getAxisValue(MotionEvent.AXIS_X),hx=e.getAxisValue(MotionEvent.AXIS_HAT_X),hy=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if(state.live()&&intro==0&&ending==0){state.stick=stickAxis(x);float rx=e.getAxisValue(MotionEvent.AXIS_Z);InputDevice d=e.getDevice();if(d!=null&&d.getMotionRange(MotionEvent.AXIS_Z,e.getSource())==null)rx=e.getAxisValue(MotionEvent.AXIS_RX);state.cameraStick=stickAxis(rx);}
        float lt=Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rt=Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER),e.getAxisValue(MotionEvent.AXIS_GAS));
        if(skipChord.triggers(lt,rt)&&state.live()&&intro==0&&ending==0){state.skipToLastFiveSeconds();return true;}
        int mode=inputMode();
        if(hx!=0&&hatX==0){if(mode==InputRouting.GAME)stepCamera(hx>0?1:-1);else if(mode==InputRouting.TITLE){if(page==6)adjustAI(hx>0?1:-1);else navigate(hx>0?1:-1);}}
        if(hy!=0&&hatY==0){if(mode==InputRouting.GAME)camera(state.camera+(hy>0?3:-3));else if(mode==InputRouting.TITLE)navigate(hy>0?1:-1);}hatX=hx;hatY=hy;
        leftAxis=lt>.25;rightAxis=rt>.25;controllerTriggers();return true;
    }
}
