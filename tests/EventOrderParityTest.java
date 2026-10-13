package dev.thor.fnaf2;
import java.util.*;
import java.nio.file.*;

/** Independent build-288 event fixtures; optional CSV is generated from private source events. */
public final class EventOrderParityTest {
 static int checks,failed;
 static void check(boolean pass,String name){checks++;if(!pass){failed++;if(failed<20)System.out.println("FAIL: "+name);}}
 static Fnaf2State state(int night){Fnaf2State s=new Fnaf2State(new Random(){public int nextInt(int n){return 0;}},new Random(1));s.start(night);s.step(0);s.ticks=0;Arrays.fill(s.ai,0);Arrays.fill(s.pos,-1);s.puppetAI=-1;return s;}
 static void due(Fnaf2State s,int event,double period){try{java.lang.reflect.Field r=EventTimers.class.getDeclaredField("remaining"),i=EventTimers.class.getDeclaredField("initialized");r.setAccessible(true);i.setAccessible(true);((double[])r.get(s.eventTimers))[event]=1.0/60;((boolean[])i.get(s.eventTimers))[event]=true;}catch(Exception ex){throw new AssertionError(ex);}}
 public static void main(String[] args)throws Exception{
  Fnaf2State s=state(3);s.monitor=true;s.camera=10;s.music=500;s.wind=true;s.step();
  check(s.music==505&&s.windGrace==9,"Winding event 491 followed by grace decrement 501 on same tick");
  s=state(3);s.music=3;due(s,495,.05);due(s,358,1);due(s,359,1);due(s,360,1);s.puppetAI=20;s.step();
  check(s.music==0&&s.puppetStage==0,"Puppet 358-360 observes music before later drain 495");
  s=state(3);s.monitor=true;s.camera=10;s.music=0;s.wind=true;s.puppetAI=20;due(s,358,1);due(s,359,1);s.step();
  check(s.puppetStage==1&&s.music==300,"Starting winding on escape tick cannot retroactively suppress Puppet");
  s=state(3);s.pos[Fnaf2State.FOXY]=8;s.ai[Fnaf2State.FOXY]=1;s.foxyPressure=19;due(s,197,5);due(s,645,1);s.step();
  check(s.pos[Fnaf2State.FOXY]==8&&s.foxyPressure==20,"Foxy roll 201 precedes pressure increment 645");
  s=state(3);s.pos[Fnaf2State.TB]=3;s.pendingMove[Fnaf2State.TB]=true;s.stun[Fnaf2State.TB]=1;s.step();
  check(s.pos[Fnaf2State.TB]==3&&s.stun[Fnaf2State.TB]==0,"Authorization 217 sees cooldown before decrement 231");
  s.step();check(s.pos[Fnaf2State.TB]==4,"Authorized move begins on following pass");
  s=state(3);s.pos[Fnaf2State.TC]=Fnaf2State.OFFICE;s.mask=true;s.maskSeconds=4;due(s,742,1);s.step();
  check(s.pos[Fnaf2State.TC]==Fnaf2State.OFFICE&&s.maskSeconds==5,"Vent retreat 304 precedes late mask-second counter");
  s.step();check(s.pos[Fnaf2State.TC]==7,"Vent enemy retreats next pass after fifth mask second");
  s=state(3);s.pos[Fnaf2State.WF]=Fnaf2State.OFFICE;s.monitor=true;s.monitorSeconds=13;due(s,620,1);s.step();
  check(s.pos[Fnaf2State.WF]==Fnaf2State.OFFICE&&s.monitorSeconds==14,"Camping check 406 precedes monitor counter 620");
  s.step();check(s.pos[Fnaf2State.WF]==Fnaf2State.CAUGHT,"Camping failure occurs next pass");
  // Clock events 482-484 continue during an attack; death events 450-457 come first.
  s=state(3);s.clockSeconds=419;due(s,482,1);due(s,495,.05);s.attack(0);s.tick(1.0/60);
  check(s.won&&!s.dead,"6 AM can interrupt a newly started scare");
  s=state(3);s.clockSeconds=419;due(s,482,1);due(s,495,.05);s.attack(0);s.phaseTime=39.0/60;s.attackFrames=39;s.attackFresh=false;s.tick(1.0/60);
  check(s.dead&&!s.won,"Completed scare precedes the same-pass 6 AM clock");
  s=state(3);s.clockSeconds=419;due(s,482,1);due(s,495,.05);s.pos[Fnaf2State.TB]=3;s.pendingMove[Fnaf2State.TB]=true;s.music=500;s.step();
  check(s.won&&s.pos[Fnaf2State.TB]==4&&s.music==497,"Routes and music precede next-day event 514");
  s=state(3);s.clockSeconds=419;s.foxyPressure=3;due(s,482,1);due(s,645,1);s.step();
  check(s.won&&s.foxyPressure==4,"Next-day transition finishes later source events before leaving the frame");
  final ArrayList<Integer> bounds=new ArrayList<>();
  Random ordered=new Random(){public int nextInt(int n){bounds.add(n);return (bounds.size()-1)%n;}};
  s=new Fnaf2State(ordered,new Random(1));s.start(3);bounds.clear();Arrays.fill(s.ai,4);Arrays.fill(s.pendingMove,false);s.foxyPressure=21;s.goldenAI=4;s.monitor=true;s.paperAI=11;
  s.rollMovementOpportunities();
  check(bounds.equals(Arrays.asList(20,20,20,20,5,20,20,20,20,20,20)),"Source 197-207 random-call ranges and order");
  check(s.pendingMove[0]&&s.pendingMove[1]&&s.pendingMove[2]&&s.goldenOffice&&s.pendingMove[3]&&!s.pendingMove[4]&&s.paperPending,"Source 197-207 rolls assigned to correct actors");
  bounds.clear();s.monitor=false;s.goldenAI=0;s.rollMovementOpportunities();check(bounds.size()==11,"Golden chance consumes its roll before later visibility/AI rejection");
  s=state(3);s.pos[Fnaf2State.TB]=s.pos[Fnaf2State.TC]=9;s.pendingMove[Fnaf2State.TB]=s.pendingMove[Fnaf2State.TC]=true;s.step();
  check(s.pos[Fnaf2State.TB]==3&&s.pos[Fnaf2State.TC]==9&&!s.pendingMove[Fnaf2State.TC],"Toy Chica authorization sees Bonnie before his same-pass stage departure");
  s=state(3);s.ticks=10;s.pos[Fnaf2State.WB]=7;s.camera=11;s.hallLight=true;s.pendingMove[Fnaf2State.WB]=true;s.step();
  check(s.authorizedMove[Fnaf2State.WB]&&s.pos[Fnaf2State.WB]==7,"Hall light holds an already authorized route request");
  s.camera=6;s.hallLight=false;s.step();check(s.pos[Fnaf2State.WB]==Fnaf2State.HALL,"Authorized route survives later watching of its old room");
  for(int actor:new int[]{Fnaf2State.WF,Fnaf2State.TF}){s=state(3);s.ticks=10;s.monitor=true;s.hallLight=true;s.pos[actor]=Fnaf2State.NEAR;s.pendingMove[actor]=true;s.step();check(s.pos[actor]==Fnaf2State.OFFICE,"Residual hall light does not add a nonexistent office-entry gate for "+actor);}
  s=state(3);s.pos[Fnaf2State.TC]=4;s.pendingMove[Fnaf2State.TC]=true;s.step();check(s.pos[Fnaf2State.TC]==Fnaf2State.HALL,"Source alternate Toy Chica room-4 route");
  s=state(3);s.pos[Fnaf2State.BB]=2;s.pendingMove[Fnaf2State.BB]=true;s.step();check(s.pos[Fnaf2State.BB]==1,"Source alternate Balloon Boy room-2 route");
  s=state(3);s.monitor=true;s.camera=0;s.pos[Fnaf2State.MANGLE]=1;s.pendingMove[Fnaf2State.MANGLE]=true;s.step();check(s.pos[Fnaf2State.MANGLE]==1,"Mangle detour also respects watched-room authorization");
  for(int night:new int[]{3,4,5}){Fnaf2State early=new Fnaf2State(new Random(){public int nextInt(int n){return n-1;}});early.start(night);early.step(0);check(early.goldenAI==0,"Event 625 cancels the initial pre-Night-6 Golden AI roll: "+night);}
  s=state(3);s.hallLight=true;s.pos[Fnaf2State.FOXY]=8;s.pendingMove[Fnaf2State.FOXY]=s.authorizedMove[Fnaf2State.FOXY]=true;due(s,352,1);s.step();
  check(s.pos[Fnaf2State.FOXY]==8&&!s.hallLight,"Routes precede the late hallway-light clear");s.step();check(s.pos[Fnaf2State.FOXY]==Fnaf2State.HALL,"Foxy's held route resumes on the next pass");
  final ArrayList<Integer> retreatRolls=new ArrayList<>();
  s=new Fnaf2State(new Random(){public int nextInt(int n){retreatRolls.add(n);return n==10?1:0;}});s.start(3);s.step(0);retreatRolls.clear();s.mask=true;s.maskMotion=0;s.pos[Fnaf2State.MANGLE]=Fnaf2State.OFFICE;s.pos[Fnaf2State.BB]=7;s.pendingMove[Fnaf2State.BB]=true;due(s,264,1);s.step();
  check(retreatRolls.indexOf(10)>=0&&retreatRolls.indexOf(10)<retreatRolls.indexOf(4),"Mangle retreat random roll precedes later BB route voice roll");
  if(args.length>0){
   int last=-1; s=null;
   for(String line:Files.readAllLines(Paths.get(args[0]))){String[] c=line.split(",");int scenario=Integer.parseInt(c[0]),night=Integer.parseInt(c[1]),tick=Integer.parseInt(c[2]);
    if(scenario!=last){s=state(night);s.monitor=true;s.camera=10;last=scenario;}
    s.wind=Integer.parseInt(c[3])==1;s.step();
    check(s.ticks==tick&&s.music==Integer.parseInt(c[4])&&s.windGrace==Integer.parseInt(c[5]),"Source resource trace scenario "+scenario+" tick "+tick);
   }
  }
  System.out.println("Event order F2: "+(checks-failed)+"/"+checks+" passed");
  if(failed>0)throw new AssertionError(failed+" source-order mismatches");
 }
}
