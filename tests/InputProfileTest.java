package dev.thor.fnaf2;
public final class InputProfileTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  check(InputProfile.controllerSource(0x401)&&InputProfile.controllerSource(0x1000010)&&InputProfile.controllerSource(0x201)&&!InputProfile.controllerSource(0x101),"Gamepad, joystick and D-pad sources differ from keyboard");
  InputProfile p=new InputProfile();check(p.mode==InputProfile.CONTROLLER,"Default controller preserved");
  check(p.mouseMoved(10,10)&&!p.mouseMoved(10,10),"Only changed pointer positions activate mouse");
  check(p.select(InputProfile.PC)&&!p.select(InputProfile.PC),"No duplicate profile resets");
  check(!InputProfile.controllerActive(0,.15f,-.18f)&&InputProfile.controllerActive(.8f),"Idle/deadzone controller cannot take focus");
  InputProfile.Hover h=new InputProfile.Hover();check(h.enter(1)&&!h.enter(1)&&!h.enter(-1)&&h.enter(1),"One toggle per hover entry");h.reset();check(h.enter(1),"Reset rearms hover");
  for(float sensitivity:new float[]{1.5f,3.8f,5}){
   float axis=InputProfile.officeAxis(2,0,sensitivity),step=axis*sensitivity/12;
   check(Math.abs(step+1f/60)<.000001,"Native per-tick pan unaffected by controller sensitivity");
   check(InputProfile.officeAxis(2,.5f,sensitivity)==0,"Center pointer stops pan");
   check(InputProfile.officeAxis(2,1,sensitivity)==-axis,"Symmetric office panning");
  }
  check(InputProfile.officeAxis(2,-1,3.8f)==0&&InputProfile.officeAxis(2,.5f,0)==0,"Invalid input ignored");
  System.out.println("PASS: profile transitions, idle filtering, hover rearm, native mouse pan rates");
 }
}
