package dev.thor.fnaf2;
import javax.swing.*;import java.nio.file.*;import java.awt.image.BufferedImage;import android.view.*;
public final class WindowsLaunchTest {
 public static void main(String[] args){System.setProperty("fnaf2.dataDir",Path.of(args[0]).toAbsolutePath().toString());SwingUtilities.invokeLater(()->{try{
  MainActivity a=new MainActivity();a.start();final int[] step={0};final long[] started={System.nanoTime()};
  Timer timer=new Timer(500,null);timer.addActionListener(e->{try{
   if(step[0]==0){if(System.nanoTime()-started[0]<2_000_000_000L)return;shot(a,"live-menu");a.begin();step[0]++;}
   else if(step[0]==1){if(a.intro>0)return;if(!a.state.live())throw new AssertionError("Night did not start");shot(a,"live-office");a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,true,0,InputDevice.SOURCE_KEYBOARD));step[0]++;}
   else if(step[0]==2){if(!a.state.flashlight)throw new AssertionError("Live Ctrl flashlight");shot(a,"live-flashlight");a.keys(new KeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,false,0,InputDevice.SOURCE_KEYBOARD));a.keys(new KeyEvent(KeyEvent.KEYCODE_BUTTON_X,true,0));step[0]++;}
   else if(step[0]==3){if(!a.state.monitor)throw new AssertionError("Live monitor transition");shot(a,"live-camera");a.keys(new KeyEvent(KeyEvent.KEYCODE_ESCAPE,true,0,InputDevice.SOURCE_KEYBOARD));step[0]++;}
   else{if(!a.state.paused)throw new AssertionError("Live pause");shot(a,"live-paused");timer.stop();a.finish();System.out.println("PASS: native Windows window, live night, flashlight, tablet/camera, pause, and shutdown");System.exit(0);}
  }catch(Throwable t){t.printStackTrace();timer.stop();a.finish();System.exit(1);}});timer.start();
 }catch(Throwable t){t.printStackTrace();System.exit(1);}});}
 static void shot(MainActivity a,String name)throws Exception{BufferedImage image=new BufferedImage(a.top.getWidth(),a.top.getHeight(),BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=image.createGraphics();a.top.paint(g);g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("fnaf2.dataDir"),name+".png").toFile());}
}
