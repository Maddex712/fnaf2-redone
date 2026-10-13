package dev.thor.fnaf2;
public final class EncounterCatalogTest {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  int[] subjects={326,681,518,520,Fnaf2Catalog.SCENE[93],Fnaf2Catalog.SCENE[94],Fnaf2Catalog.SCENE[96],Fnaf2Catalog.SCENE[95],65};
  int orderMask=0;for(int i=0;i<subjects.length;i++){Fnaf2State sample=EncounterCatalog.scene(i);check(EncounterCatalog.image(sample,subjects[i])==i,"Staged scene discovery matches entry "+i);if(i>=5&&i<8)check(sample.cameraImage()==subjects[i],"Staged camera uses actual rare frame "+i);orderMask|=1<<EncounterCatalog.ORDER[i];}check(orderMask==511,"Rarity order includes all entries exactly once");
  Fnaf2State s=new Fnaf2State();s.start(1);s.paperOffice=true;
  check(EncounterCatalog.image(s,326)==0,"Visible office paper pal candidate");
  s.mask=true;check(EncounterCatalog.image(s,326)==-1,"Mask hides discovery");s.mask=false;
  s.paused=true;check(EncounterCatalog.image(s,326)==-1,"Pause cannot discover");s.paused=false;
  s.blackoutAlpha=255;check(EncounterCatalog.image(s,326)==-1,"Blackout hides discovery");s.blackoutAlpha=0;
  s.monitor=true;check(EncounterCatalog.image(s,Fnaf2Catalog.SCENE[94])==5,"Rare camera frame candidate");
  s.signalTicks=1;check(EncounterCatalog.image(s,Fnaf2Catalog.SCENE[94])==-1,"Static cannot discover");s.signalTicks=0;
  s.cameraRaiseTicks=1;check(EncounterCatalog.image(s,Fnaf2Catalog.SCENE[94])==-1,"Raising camera hides discovery");s.cameraRaiseTicks=0;
  s.dead=true;check(EncounterCatalog.image(s,Fnaf2Catalog.SCENE[94])==-1,"Death cannot discover");
  double min=0,max=0;for(int tick=0;tick<762;tick++){s.ticks=tick;double x=s.maskOffset(false),y=s.maskOffset(true);min=Math.min(min,x);max=Math.max(max,x);check(x>=-34&&x<=36&&y>=-16&&y<=14,"Mask breathing stays inside source path bounds");}
  check(max-min>60,"Mask breathing is visibly moving");s.ticks=0;double origin=s.maskOffset(false);s.ticks=762;check(s.maskOffset(false)==origin,"Mask path loops without drift");
  System.out.println(checks+" encounter visibility and mask-path checks passed");
 }
}
