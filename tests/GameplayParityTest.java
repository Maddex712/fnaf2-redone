package dev.thor.fnaf2;
import java.util.*;
import java.nio.file.*;

/** Regression cases tied to independently inspected Steam build 288 event rules. */
public final class GameplayParityTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static Fnaf2State isolated(int night){Fnaf2State s=new Fnaf2State(new Random(){public int nextInt(int n){return 0;}},new Random(123));s.start(night);s.step(0);s.ticks=0;Arrays.fill(s.ai,0);Arrays.fill(s.pos,-1);s.puppetAI=-1;return s;}
 static void ticks(Fnaf2State s,int n){for(int i=0;i<n;i++)s.tick(1.0/60);}
 static void due(Fnaf2State s,int event,double period){try{java.lang.reflect.Field r=EventTimers.class.getDeclaredField("remaining"),i=EventTimers.class.getDeclaredField("initialized");r.setAccessible(true);i.setAccessible(true);((double[])r.get(s.eventTimers))[event]=1.0/60;((boolean[])i.get(s.eventTimers))[event]=true;}catch(Exception ex){throw new AssertionError(ex);}}
 public static void main(String[] args)throws Exception{
  Fnaf2State s;
  // Source 300/301 and the native path oracle: random entry and complete blackout recovery.
  Fnaf2State walk=new Fnaf2State(new Random(){public int nextInt(int n){return n==2||n==3?1:0;}},new Random(1));
  walk.start(3);walk.step(0);Arrays.fill(walk.pos,-1);Arrays.fill(walk.ai,0);walk.puppetAI=-1;walk.pos[Fnaf2State.TB]=Fnaf2State.OFFICE;walk.mask=true;walk.maskMotion=0;
  ticks(walk,30);check(!walk.toyWalk.active,"Toy Bonnie waits for half-second opportunity");ticks(walk,1);check(walk.toyWalk.active&&walk.intruder==Fnaf2State.TB,"Winning entry roll starts real walk and intrusion");
  ticks(walk,1);check(walk.toyWalk.x==1874&&walk.toyWalk.y==435,"First walk frame matches original executable");walk.toggleMask();check(walk.mask,"Walk-by holds mask during intrusion");
  ticks(walk,298);check(!walk.toyWalk.active&&walk.pos[Fnaf2State.TB]==3&&walk.intruder<0,"Defended walk resolves to CAM 3 at blackout endpoint");
  walk.start(3);walk.step(0);Arrays.fill(walk.pos,-1);Arrays.fill(walk.ai,0);walk.puppetAI=-1;walk.pos[Fnaf2State.TB]=Fnaf2State.OFFICE;walk.mask=true;walk.maskMotion=0;walk.blackoutFade=100;walk.ticks=29;ticks(walk,1);check(!walk.toyWalk.active,"Previous blackout fade prevents overlapping walk entry");
  ToyBonnieWalk path=new ToyBonnieWalk();path.start();for(int i=0;i<1381;i++)path.tick();check(path.active,"Native path does not finish early");path.tick();check(!path.active&&path.x==1882&&path.y==441,"Native path terminates on tick 1382");
  Fnaf2State custom=isolated(6);custom.customLevels=new int[10];Arrays.fill(custom.customLevels,20);custom.start(7);check(custom.challenge==-1,"Hand-edited AI does not grant a preset trophy");check(custom.ai[Fnaf2State.FOXY]==17&&custom.goldenAI==10,"Manual AI retains original internal caps");
  DreamSequence dream=new DreamSequence();dream.start(4);dream.pan=-1;for(int i=0;i<145;i++)dream.tick(1.0/60);check(dream.yellow,"Looking left reveals Golden Freddy");dream.pan=1;for(int i=0;i<150;i++)dream.tick(1.0/60);check(dream.yellow&&dream.center>1000,"Dream apparition stays revealed when looking back");dream.start(3);for(int i=0;i<2159;i++)dream.tick(1.0/60);check(!dream.done(),"Dream and error interstitial last 36 seconds");dream.tick(1.0/60);check(dream.done(),"Dream completes at original boundary");

  // Events 240–243: Freddy has a detour and a mask retreat before office entry.
  s=isolated(5);s.pos[0]=3;s.decidePath=2;s.move(0);check(s.pos[0]==7,"Freddy can double back to Main Hall");s.pos[0]=3;s.decidePath=1;s.move(0);check(s.pos[0]==Fnaf2State.NEAR,"Freddy can reach near hall");s.mask=true;s.move(0);check(s.pos[0]==3&&s.stun[0]==2500,"Seated mask repels near-hall Freddy with night-scaled delay");
  // Events 260–263: both Mangle branches; flashlight blocks movement from the office.
  s=isolated(4);s.pos[7]=2;s.decidePath=2;s.move(7);check(s.pos[7]==1,"Mangle has Party Room 1 detour");s.move(7);check(s.pos[7]==2,"Mangle detour returns to Party Room 2");s.decidePath=1;s.move(7);check(s.pos[7]==6,"Other branch reaches right vent");s.pos[7]=12;s.hallLight=true;s.move(7);check(s.pos[7]==12,"Office hallway flashlight blocks Mangle move authorization");
  // Events 557–560: an unseen intruder still occupies the office.
  s=isolated(6);s.monitor=true;s.pos[1]=Fnaf2State.OFFICE;s.pos[2]=6;s.move(2);check(s.pos[2]==6,"Second Withered cannot stack behind raised camera");
  // Events 175–179: BB enters permanently on the NEXT raise, not five seconds later.
  s=isolated(2);s.toggleMonitor();check(!s.monitor&&s.cameraFlipImage()==114,"Raise begins with original first frame and office viewing");
  s.setWind(true);s.setFlashlight(true);s.toggleMask();s.toggleMonitor();check(!s.wind&&!s.flashlight&&!s.mask&&s.cameraRaiseTicks==13,"No free winding, mask or repeated toggle during raising");
  ticks(s,12);check(!s.monitor&&s.cameraFlipImage()==113,"Final raise frame appears before camera becomes active");ticks(s,1);check(s.monitor&&s.cameraFlipImage()==-1,"Camera activates after 11 frames at 54 FPS");
  s.toggleMonitor();check(!s.monitor&&s.cameraDropTicks==22&&s.cameraFlipImage()==113,"Lowering immediately restores office viewing");s.toggleMonitor();check(s.cameraRaiseTicks==0,"Cannot instantly reopen during lowering");ticks(s,21);check(s.cameraDropImage()==114,"Lower animation presents last source frame");ticks(s,1);check(s.cameraDropImage()==-1,"Eleven lower frames take 22 ticks at 30 FPS");
  s=isolated(2);s.setQuickWind(true);ticks(s,5);s.setQuickWind(false);ticks(s,8);check(!s.monitor&&!s.wind&&s.cameraDropTicks==22,"Early quick-wind release finishes raise then closes without winding");ticks(s,22);check(s.cameraRaiseTicks==0&&!s.monitor,"Canceled quick wind never reopens itself");
  s=isolated(2);s.toggleMonitor();ticks(s,5);s.paused=true;ticks(s,20);check(s.cameraRaiseTicks==8,"Pause freezes monitor flip");s.paused=false;ticks(s,8);check(s.monitor,"Resume finishes remaining monitor frames");s.attack(3);check(s.cameraRaiseTicks==0,"Attack clears raise overlay");
  s=isolated(2);s.pos[Fnaf2State.WB]=Fnaf2State.OFFICE;s.toggleMonitor();ticks(s,1);check(s.intruder==Fnaf2State.WB,"Starting a camera raise cannot delay an existing office intrusion");
  s=isolated(2);s.monitor=true;s.pos[Fnaf2State.TC]=Fnaf2State.OFFICE;ticks(s,360);check(s.pos[Fnaf2State.TC]==Fnaf2State.OFFICE,"Toy Chica survives five eligible one-second events");ticks(s,1);check(s.pos[Fnaf2State.TC]==Fnaf2State.CAUGHT,"Sixth eligible second commits Toy Chica");
  s=isolated(3);s.pos[8]=5;s.monitor=true;s.move(8);check(!s.bbInside&&s.pos[8]==Fnaf2State.OFFICE,"Vent arrival alone is not BB's penalty");s.toggleMonitor();ticks(s,22);s.toggleMonitor();check(!s.bbInside,"BB penalty waits for raise completion");ticks(s,13);check(s.bbInside&&s.pos[8]==Fnaf2State.CAUGHT,"Next camera raise commits BB");s.setFlashlight(true);check(!s.flashlight,"Committed BB disables flashlight");
  s=isolated(3);s.pos[8]=Fnaf2State.OFFICE;s.toggleMask();ticks(s,312);check(s.pos[8]==Fnaf2State.OFFICE,"Event 742 reaches five mask seconds after event 179");ticks(s,1);check(s.pos[8]==10&&!s.bbInside,"Next pass repels BB after five global mask seconds");
  // Events 266–267, 573–574: Mangle waits on ceiling until armed, then camera down kills.
  s=isolated(4);s.pos[7]=Fnaf2State.OFFICE;s.toggleMonitor();ticks(s,13);check(s.pos[7]==Fnaf2State.CAUGHT&&s.phase==0,"Mangle reaches ceiling without instant scare");ticks(s,60);check(!s.mangleArmed&&s.phase==0,"Non-winning roll leaves Mangle waiting");s.mangleArmed=true;s.toggleMonitor();check(s.phase==1&&s.attacker==7,"Armed Mangle attacks on camera lowering");
  // Events 410 and 292: Toy Bonnie's opportunity uses a night-dependent stun.
  for(int night=1;night<=7;night++){s=isolated(night);s.pos[5]=6;s.move(5);check(s.stun[5]==1000-night*100,"Toy Bonnie office timer night "+night);s.monitor=true;ticks(s,s.stun[5]-1);check(s.pos[5]==Fnaf2State.OFFICE,"Toy Bonnie cannot commit before cooldown");ticks(s,1);check(s.pos[5]==Fnaf2State.CAUGHT,"Toy Bonnie commits at cooldown");}
  // Events 396 precedes 397: a completed mask must beat, not equal, the deadline.
  int[] allowed={100,80,60,55,50,50,45};
  for(int night=1;night<=7;night++){s=isolated(night);s.pos[1]=Fnaf2State.OFFICE;ticks(s,allowed[night-1]-13);s.toggleMask();ticks(s,12);check(s.defended,"Mask completes one frame before deadline N"+night);s.toggleMask();check(s.mask,"Cannot remove mask during intrusion");
   s=isolated(night);s.pos[1]=Fnaf2State.OFFICE;ticks(s,allowed[night-1]-12);s.toggleMask();ticks(s,12);check(!s.defended,"Mask at expired deadline is too late N"+night);}
  // Events 405/411/412/416 then 420–433: failed defense queues the attack.
  for(int actor:new int[]{0,1,2,4}){s=isolated(2);s.pos[actor]=Fnaf2State.OFFICE;ticks(s,300);check(s.phase==0&&s.pos[actor]==Fnaf2State.CAUGHT,"Failed defense remains queued for actor "+actor);s.toggleMonitor();ticks(s,13);s.toggleMonitor();check(s.phase==1&&s.attacker==s.scareFor(actor),"Correct queued actor attacks on monitor down");}
  // Events 434–437: Foxy and Golden Freddy retain distinct delayed triggers.
  s=isolated(4);s.pos[3]=Fnaf2State.HALL;s.move(3);check(s.phase==0&&s.pos[3]==Fnaf2State.CAUGHT,"Foxy queues attack");s.setFlashlight(true);ticks(s,1);check(s.phase==1&&s.attacker==3,"Hall flash exposes committed Foxy attack");
  s=isolated(6);s.goldenOffice=true;s.toggleMonitor();check(s.goldenCaught&&s.phase==0,"Golden Freddy waits for one-second event");ticks(s,60);check(s.phase==0,"Golden Freddy cannot attack before clock boundary");ticks(s,1);check(s.phase==1&&s.attacker==9,"Golden Freddy attacks at boundary");
  // Events 613/686: hall exposure accumulates until the apparition changes.
  s=isolated(6);s.goldenHall=true;s.hallLight=true;s.goldenHallTicks=50;s.ticks=1;ticks(s,1);s.hallLight=false;ticks(s,1);check(s.goldenHallTicks==51,"Releasing flash does not reset same Golden apparition");s.goldenHall=false;ticks(s,1);check(s.goldenHallTicks==0,"Changing apparition resets exposure");
  // Events 693–695 and 685: later events override earlier Foxy grace rules.
  s=isolated(1);s.ticks=18000;s.foxyPressure=8;ticks(s,60);check(s.foxyPressure==0,"Night 1 Foxy pressure grace lasts all night");s=isolated(2);s.clockSeconds=70;s.foxyPressure=8;ticks(s,61);check(s.foxyPressure==0,"Night 2 1 AM grace");s.clockSeconds=140;s.foxyPressure=8;ticks(s,60);check(s.foxyPressure==9,"Night 2 pressure resumes at 2 AM");
  // Events 320 and 648–649: Mangle's Prize Corner exception and Custom Night blindness.
  s=isolated(4);s.monitor=true;s.camera=10;s.pos[7]=11;s.setFlashlight(true);ticks(s,1);check(s.stun[7]==0,"Prize Corner flash does not stun Mangle");s.camera=11;s.pos[7]=12;ticks(s,1);check(s.stun[7]==400,"Kid's Cove flash does stun Mangle");
  s=isolated(7);s.monitor=true;s.camera=7;ticks(s,100);check(s.signalTicks>0,"Custom Night Parts/Service continuously loses signal");s.camera=8;ticks(s,100);check(s.signalTicks>0,"Custom Night Show Stage continuously loses signal");s.camera=6;ticks(s,6);check(s.signalTicks==0,"Other camera clears interference");
  // Events 358-361 authorize movement after the movement events, on the next tick.
  s=isolated(2);s.puppetAI=20;s.music=0;s.puppetStage=2;due(s,358,1);due(s,359,1);due(s,360,1);ticks(s,1);check(s.puppetStage==3&&s.puppetPending&&s.puppetPosition==11,"Puppet can authorize escape on final emergence roll");ticks(s,1);check(s.puppetPosition==10,"Authorized Puppet moves next frame, not one second later");
  s=isolated(3);s.pos[1]=7;s.camera=6;s.monitor=false;s.move(1);check(s.pos[1]==7,"Withered camera-position gate persists with monitor down");
  s=isolated(1);s.pos[5]=9;s.clockSeconds=139;due(s,482,1);due(s,197,5);ticks(s,1);check(s.pos[5]==9&&s.ai[5]==2,"Hour-boundary movement rolls use prior AI before difficulty adjustment");ticks(s,300);check(s.pos[5]==3,"New AI applies at next movement opportunity");
  // Independent expected AI schedules optionally generated directly from event dump.
  if(args.length>0)for(String line:Files.readAllLines(Paths.get(args[0]))){String[] v=line.split(",");int night=Integer.parseInt(v[0]),hour=Integer.parseInt(v[1]);s=isolated(night);for(int h=0;h<=hour;h++){s.clockSeconds=h*70;s.setDifficulty();}for(int i=0;i<9;i++)check(s.ai[i]==Integer.parseInt(v[i+2]),"Source schedule N"+night+" H"+hour+" actor"+i);check(s.goldenAI==Integer.parseInt(v[11]),"Source Golden schedule");}
  for(int mode=0;mode<3;mode++){check(Fnaf2State.seasonal(mode,10,31)==(mode!=2),"Halloween original date");check(Fnaf2State.seasonal(mode,11,1)==(mode==1),"Halloween automatic and forced modes");}
  System.out.println("PASS: "+checks+" source-based gameplay boundary checks");
 }
}
