package dev.thor.fnaf2;
final class EncounterCatalog {
 static final String[] TITLES={"Paper Pal in the office","JJ under the desk","Shadow Bonnie","Golden Freddy in the office","Golden Freddy in the hall","Parts / Service anomaly","Left vent anomaly","Prize Corner anomaly","Puppet apparition"};
 static final int[] ORDER={2,1,6,0,8,3,4,5,7};
 static final String[] ODDS={"1% / night enables it; then 5% / eligible 5 seconds","0.1% / monitor-down picture roll","~0.0061% / native seed at monitor lowering","5-50% / eligible 5 seconds, when its AI is enabled","10% / eligible second, when its AI is enabled","10.1% / picture roll + flashlight/room conditions","0.1% / picture roll + flashlight/room conditions","10.1% / picture roll + escaped Puppet conditions","2% / apparition roll (multiple gated rolls)"};
 static Fnaf2State scene(int id){Fnaf2State s=new Fnaf2State();s.start(6);java.util.Arrays.fill(s.pos,-1);s.ticks=7200;s.cameraRaiseTicks=s.cameraDropTicks=s.signalTicks=0;s.randomImage=999;
  if(id<5){s.monitor=false;s.look=id==2?-.7:0;if(id==0)s.paperOffice=true;if(id==1)s.randomImage=9;if(id==2)s.shadowBonnie=true;if(id==3)s.goldenOffice=true;if(id==4){s.goldenHall=true;s.hallLight=s.flashlight=true;}}
  else{s.monitor=true;s.flashlight=true;if(id==5){s.camera=7;s.randomImage=50;}if(id==6){s.camera=4;s.randomImage=3;}if(id==7){s.camera=10;s.puppetStage=3;s.randomImage=50;}if(id==8){s.camera=0;s.flashlight=false;s.puppetPosition=1;s.puppetApparition=65;s.puppetApparitionAlpha=155;}}
  return s;
 }
 static int image(Fnaf2State s,int id){
  if(!s.live()||s.phase!=0||s.blackoutAlpha>0)return -1;
  if(s.monitor){if(s.signalTicks>0||s.cameraRaiseTicks>0||s.cameraDropTicks>0)return -1;if(id==Fnaf2Catalog.SCENE[94])return 5;if(id==Fnaf2Catalog.SCENE[96])return 6;if(id==Fnaf2Catalog.SCENE[95])return 7;if(id==s.puppetApparition&&s.puppetApparitionAlpha>0)return 8;}
  else if(!s.mask&&s.maskMotion==0){if(id==326&&s.paperOffice)return 0;if(id==681&&s.randomImage==9)return 1;if(id==518&&s.shadowBonnie)return 2;if(id==520&&s.goldenOffice)return 3;if(id==Fnaf2Catalog.SCENE[93]&&s.goldenHall)return 4;}
  return -1;
 }
}
