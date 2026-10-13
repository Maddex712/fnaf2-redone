package android.media;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;

/** Native audio calls never run on the game/paint thread, including stop/close/gain. */
public class MediaPlayer {
 public interface Prepared{void run(MediaPlayer m);}public interface Completion{void run(MediaPlayer m);}public interface Error{boolean run(MediaPlayer m,int w,int e);}
 static final ExecutorService loader=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"Audio loader");t.setDaemon(true);return t;});
 static final ExecutorService controls=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Audio controls");t.setDaemon(true);return t;});
 volatile Clip clip;volatile boolean released,loop,playing;int pausedFrame=-1;
 volatile float volume=1;float appliedVolume=Float.NaN;String path;Prepared prepared;Completion completion;Error error;
 public boolean isPlaying(){return playing||(clip==null&&!released);}
 public void setAudioAttributes(AudioAttributes a){}public void setDataSource(String p){path=p;}public void setLooping(boolean b){loop=b;}
 public void setVolume(float l,float r){float next=Math.max(0,Math.min(1,(l+r)/2));if(next==volume)return;volume=next;controls.execute(()->{if(!released)applyVolume();});}
 void applyVolume(){Clip c=clip;if(c==null||volume==appliedVolume)return;float value=volume;if(c.isControlSupported(FloatControl.Type.MASTER_GAIN)){FloatControl gain=(FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN);gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),value==0?gain.getMinimum():(float)(20*Math.log10(value)))));}appliedVolume=value;}
 public void setOnPreparedListener(Prepared p){prepared=p;}public void setOnCompletionListener(Completion c){completion=c;}public void setOnErrorListener(Error e){error=e;}
 public void prepareAsync(){loader.execute(()->{
  Clip c=null;
  try(AudioInputStream stream=AudioSystem.getAudioInputStream(new java.io.File(path))){
   if(released)return;c=AudioSystem.getClip();c.open(stream);Clip ready=c;
   controls.execute(()->{
    if(released){ready.close();return;}
    clip=ready;applyVolume();
    ready.addLineListener(event->{if(event.getType()==LineEvent.Type.STOP&&playing&&!loop&&ready.getFramePosition()>=ready.getFrameLength()-1){playing=false;SwingUtilities.invokeLater(()->{if(!released&&completion!=null)completion.run(this);});}});
    SwingUtilities.invokeLater(()->{if(!released&&prepared!=null)prepared.run(this);});
   });
  }catch(Exception ex){if(c!=null)c.close();SwingUtilities.invokeLater(()->{if(!released&&error!=null)error.run(this,0,0);});}
 });}
 public void start(){controls.execute(()->{Clip c=clip;if(c==null||released)return;if(pausedFrame>=0){c.setFramePosition(pausedFrame);pausedFrame=-1;}playing=true;if(loop)c.loop(Clip.LOOP_CONTINUOUSLY);else c.start();});}
 public void pause(){controls.execute(()->{Clip c=clip;if(c==null||released)return;playing=false;c.stop();pausedFrame=c.getFramePosition();});}
 public void release(){released=true;playing=false;controls.execute(()->{Clip c=clip;clip=null;if(c!=null){c.stop();c.close();}});}
}
