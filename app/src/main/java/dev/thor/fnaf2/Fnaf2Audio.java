package dev.thor.fnaf2;
import android.media.MediaPlayer;
import java.util.*;

final class Fnaf2Audio {
    final Fnaf2Assets assets;final Map<String,MediaPlayer> channels=new HashMap<>();
    final Map<String,String> sources=new HashMap<>();final Map<String,Float> levels=new HashMap<>();
    Fnaf2Audio(Fnaf2Assets a){assets=a;}
    void play(String channel,String file,boolean loop,float volume){stop(channel);if(!assets.ready)return;MediaPlayer p=new MediaPlayer();try{p.setDataSource(assets.sound(file).getPath());p.setLooping(loop);p.setVolume(volume,volume);p.prepare();channels.put(channel,p);sources.put(channel,file);levels.put(channel,volume);if(!loop)p.setOnCompletionListener(done->{if(channels.get(channel)==done){channels.remove(channel);sources.remove(channel);levels.remove(channel);}done.release();});p.start();}catch(Exception ex){if(channels.get(channel)==p){channels.remove(channel);sources.remove(channel);levels.remove(channel);}p.release();android.util.Log.w("Fnaf2Audio","Could not play "+file,ex);}}
    void loop(String channel,String file,float volume){if(!channels.containsKey(channel))play(channel,file,true,volume);else volume(channel,volume);}
    void volume(String ch,float v){MediaPlayer p=channels.get(ch);Float old=levels.get(ch);if(p!=null&&(old==null||old!=v)){p.setVolume(v,v);levels.put(ch,v);}}
    boolean finished(String ch){MediaPlayer p=channels.get(ch);return p==null||!p.isPlaying();}
    void stop(String ch){MediaPlayer p=channels.remove(ch);sources.remove(ch);levels.remove(ch);if(p!=null)p.release();}
    void stopAll(){for(MediaPlayer p:channels.values())p.release();channels.clear();sources.clear();levels.clear();}
    void pause(boolean pause){for(MediaPlayer p:channels.values()){try{if(pause)p.pause();else p.start();}catch(IllegalStateException ignored){}}}
    void menu(){if(!channels.containsKey("title"))play("title-static","static2",false,.5f);loop("title","The_Sand_Temple_Loop_G",.55f);}
    void startNight(int n){stopAll();loop("ambience","In_The_Depths_C",.45f);loop("fan","fansound",.4f);loop("hall-warning","With_S2",0);loop("hall-static","popstatic",0);}
    void update(Fnaf2State s){
        loop("light","buzzlight",s.flashlight||s.leftLight||s.rightLight?.45f:0);
        loop("camera","CMPTR_Low_Tech_Stat",s.monitor?.3f:0);
        loop("mask","deepbreaths",s.maskOn()?.55f:0);
        loop("box","Music_Box_Melody_Playful",s.monitor&&s.music>0?(s.camera==10?.4f:(s.camera==9||s.camera==11?.15f:s.camera==8?.05f:0)):0);
        loop("wind","windup2",s.wind?.6f:0);
        loop("danger","stare",s.intruder>=0||s.signalTicks>0?.6f:0);
        loop("hall-warning","With_S2",s.nearbyDanger?.5f:0);
        loop("hall-static","popstatic",s.hallBlackout()?.6f:0);
        loop("mangle","elec garble",s.pos[Fnaf2State.MANGLE]==Fnaf2State.CAUGHT?(s.monitor?.4f:.6f):s.pos[Fnaf2State.MANGLE]==Fnaf2State.OFFICE?(s.monitor?.25f:.4f):s.monitor&&s.pos[Fnaf2State.MANGLE]==s.camera+1?.25f:0);
        loop("puppet", "jackinthebox",s.puppetPosition!=11?.75f:0);
        Fnaf2State.SoundCue cue;while((cue=s.pollSound())!=null)play(cue.channel,cue.file,false,cue.volume);
    }
}
