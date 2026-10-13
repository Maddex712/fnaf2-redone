package android.view;
public class InputDevice {public static final int SOURCE_MOUSE=0x2002,SOURCE_KEYBOARD=0x101,SOURCE_GAMEPAD=0x401,SOURCE_JOYSTICK=0x1000010;public MotionRange getMotionRange(int a,int s){return new MotionRange();}public MotionRange getMotionRange(int a){return new MotionRange();}public static class MotionRange{public float getFlat(){return .18f;}}}
