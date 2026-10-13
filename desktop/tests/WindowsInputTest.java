package dev.thor.fnaf2;
import android.graphics.*;import android.view.*;import java.nio.file.*;import javax.swing.*;
public final class WindowsInputTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void tick(MainActivity a,int frames){for(int i=0;i<frames;i++)a.state.tick(1.0/60);}
 static void hover(MainActivity a,float x,float y){a.top.onGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_HOVER_MOVE,x,y,InputDevice.SOURCE_MOUSE));}
 static void render(MainActivity a,String name)throws Exception{Bitmap b=Bitmap.createBitmap(1024,768,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);a.top.onDraw(c);c.close();try(java.io.OutputStream out=Files.newOutputStream(java.nio.file.Path.of(System.getProperty("fnaf2.dataDir"),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Fresh private data directory required");System.setProperty("fnaf2.dataDir",java.nio.file.Path.of(args[0]).toAbsolutePath().toString());
  MainActivity a=new MainActivity();SwingUtilities.invokeAndWait(a::initialize);a.assets.worker.submit(()->{}).get(30,java.util.concurrent.TimeUnit.SECONDS);
  SwingUtilities.invokeAndWait(()->{try{
   a.top=new Fnaf2View(a,a,true);a.top.setSize(1024,768);check(a.assets.ready,"Owned data loaded");a.inputProfile.select(InputProfile.PC);render(a,"windows-menu");
   a.state.start(1);hover(a,80,200);check(a.state.stick<0,"Mouse pan");MotionEvent idle=new MotionEvent(MotionEvent.ACTION_MOVE,0,0);a.motion(idle);check(a.inputProfile.mode==InputProfile.PC&&a.state.stick<0,"Idle controller steals focus");
   a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,true,0,InputDevice.SOURCE_KEYBOARD));check(a.state.flashlight,"Ctrl flashlight");a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_RIGHT,true,0,InputDevice.SOURCE_KEYBOARD));a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,false,0,InputDevice.SOURCE_KEYBOARD));check(a.state.flashlight,"Second Ctrl remains held");a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_RIGHT,false,0,InputDevice.SOURCE_KEYBOARD));check(!a.state.flashlight,"Ctrl release");
   render(a,"windows-office");hover(a,760,730);check(a.state.cameraRaiseTicks>0,"Monitor hover starts animation");tick(a,30);check(a.state.monitor&&!a.desktopUpper(),"Fullscreen camera");render(a,"windows-camera");
   hover(a,500,500);hover(a,760,730);check(!a.state.monitor,"Monitor hover lowers");tick(a,30);hover(a,230,730);check(a.state.mask,"Mask hover");tick(a,30);render(a,"windows-mask");
   a.state.mask=false;a.state.maskMotion=0;MotionEvent stick=new MotionEvent(MotionEvent.ACTION_MOVE,0,0);stick.axes[MotionEvent.AXIS_X]=.7f;a.motion(stick);check(a.inputProfile.mode==InputProfile.CONTROLLER&&a.state.stick>.6,"Original controller pan");
   a.keys(new KeyEvent(KeyEvent.KEYCODE_BUTTON_X,true,0));tick(a,30);check(a.state.monitor&&!a.desktopUpper(),"Controller camera fills same screen");
   a.state.stop();for(int page:new int[]{1,2,3,4,6}){a.page=page;check(!a.desktopUpper(),"Single-screen page "+page);render(a,"windows-page-"+page);}a.page=5;check(a.desktopUpper(),"Settings top");render(a,"windows-settings");
   a.state.start(1);a.ctrlLeft=true;a.state.setFlashlight(true);a.loseFocus();check(a.state.paused&&!a.state.flashlight&&!a.ctrlLeft,"Focus loss clears and pauses");
   check(DesktopController.keyboardCode(java.awt.event.KeyEvent.VK_CONTROL)==KeyEvent.KEYCODE_CTRL_LEFT,"Windows Ctrl translation");
   check(DesktopController.keyboardCode(java.awt.event.KeyEvent.VK_W)==KeyEvent.KEYCODE_W,"Minigame keyboard translation");
  }catch(Exception e){throw new RuntimeException(e);}});
  a.audio.stopAll();a.assets.close();a.encounters.close();a.handler.removeCallbacksAndMessages(null);System.out.println("PASS: "+checks+" Windows input/render/focus checks; PNG evidence in private data directory");
 }
}
