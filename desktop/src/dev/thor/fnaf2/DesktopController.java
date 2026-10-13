package dev.thor.fnaf2;

import android.view.KeyEvent;
import android.view.MotionEvent;
import com.sun.jna.Native;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

/** Windows XInput polling; feeds the exact shared controller handlers. */
final class DesktopController {
    interface XInput extends StdCallLibrary {int XInputGetState(int user,Pointer state);}
    final MainActivity app;XInput api;final Memory buffer=new Memory(16);
    int active=-1,buttons;boolean leftTrigger,rightTrigger;float axisX,axisY;
    String status="No XInput controller connected. Keyboard and mouse available.";
    final java.util.concurrent.ArrayBlockingQueue<byte[]> packets=new java.util.concurrent.ArrayBlockingQueue<>(64);
    java.util.concurrent.ScheduledExecutorService reader;
    volatile boolean accepting=true,closed;volatile double driverMillis;volatile int nativeSlot=-1;
    long nextSearch;
    static final int[] MASKS={1,2,4,8,16,32,64,128,256,512,4096,8192,16384,32768};
    static final int[] KEYS={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y};
    DesktopController(MainActivity app){this.app=app;for(String name:new String[]{"xinput1_4","xinput1_3","xinput9_1_0"})try{api=Native.load(name,XInput.class);break;}catch(UnsatisfiedLinkError ignored){}if(api==null)status="XInput unavailable. Keyboard and mouse available.";}
    boolean held(){return buttons!=0||leftTrigger||rightTrigger||axisX!=0||axisY!=0;}
    void reset(){buttons=0;leftTrigger=rightTrigger=false;axisX=axisY=0;}
    void startReader(){
        if(reader!=null||api==null)return;
        reader=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"XInput reader");t.setDaemon(true);return t;});
        reader.scheduleWithFixedDelay(()->{
            if(closed)return;long start=System.nanoTime();byte[] packet=readPacket();driverMillis=(System.nanoTime()-start)/1e6;
            if(packet!=null&&accepting){synchronized(packets){if(!accepting)return;if(!packets.offer(packet)){packets.clear();packets.offer(packet);}}}
        },0,8,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    void suspend(){synchronized(packets){accepting=false;packets.clear();}reset();}
    void resume(){accepting=true;}
    void close(){closed=true;suspend();if(reader!=null)reader.shutdownNow();}
    byte[] readPacket(){
        int found=-1;
        if(nativeSlot>=0&&api.XInputGetState(nativeSlot,buffer)==0)found=nativeSlot;
        else{
            long now=System.nanoTime();if(nativeSlot<0&&now<nextSearch)return null;
            nextSearch=now+500_000_000L;
            for(int i=0;i<4;i++)if(api.XInputGetState(i,buffer)==0){found=i;break;}
        }
        nativeSlot=found;byte[] packet=new byte[17];packet[16]=(byte)found;
        if(found>=0)System.arraycopy(buffer.getByteArray(0,16),0,packet,0,16);
        return packet;
    }
    void poll(){
        if(api==null)return;
        if(reader==null){byte[] packet=readPacket();if(packet!=null)apply(packet);return;}
        byte[] packet,last=null;int count=0;while(count++<64&&(packet=packets.poll())!=null){applyButtons(packet);last=packet;}if(last!=null&&active>=0)applyMotion(last);
    }
    void apply(byte[] packet){applyButtons(packet);if(active>=0)applyMotion(packet);}
    void applyButtons(byte[] packet){
        int found=packet[16];
        if(found<0){if(active>=0){app.resetControls();app.state.pauseForInterruption();reset();status="Controller disconnected • paused";}active=-1;return;}
        active=found;status="XInput controller "+(active+1)+" connected";
        java.nio.ByteBuffer data=java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int next=data.getShort(4)&0xffff;
        float lt=(data.get(6)&255)/255f,rt=(data.get(7)&255)/255f;
        // Digital trigger transitions also support remapping and binding detection.
        boolean l=lt>.25f,r=rt>.25f;
        for(int i=0;i<MASKS.length;i++)if((buttons&MASKS[i])!=(next&MASKS[i]))app.keys(new KeyEvent(KEYS[i],(next&MASKS[i])!=0,0));
        if(l!=leftTrigger)app.keys(new KeyEvent(KeyEvent.KEYCODE_BUTTON_L2,l,0));
        if(r!=rightTrigger)app.keys(new KeyEvent(KeyEvent.KEYCODE_BUTTON_R2,r,0));
        buttons=next;leftTrigger=l;rightTrigger=r;
    }
    void applyMotion(byte[] packet){
        java.nio.ByteBuffer data=java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float lt=(data.get(6)&255)/255f,rt=(data.get(7)&255)/255f;
        axisX=Math.max(-1,data.getShort(8)/32767f);axisY=-Math.max(-1,data.getShort(10)/32767f);
        MotionEvent e=new MotionEvent(MotionEvent.ACTION_MOVE,0,0);e.axes[MotionEvent.AXIS_X]=axisX;e.axes[MotionEvent.AXIS_Y]=axisY;e.axes[MotionEvent.AXIS_Z]=Math.max(-1,data.getShort(12)/32767f);e.axes[MotionEvent.AXIS_RX]=e.axes[MotionEvent.AXIS_Z];e.axes[MotionEvent.AXIS_LTRIGGER]=lt;e.axes[MotionEvent.AXIS_RTRIGGER]=rt;
        // D-pad comes through button events only, avoiding double camera switches.
        app.motion(e);
    }
    static int keyboardCode(int code){switch(code){
        case java.awt.event.KeyEvent.VK_UP:return KeyEvent.KEYCODE_DPAD_UP;
        case java.awt.event.KeyEvent.VK_DOWN:return KeyEvent.KEYCODE_DPAD_DOWN;
        case java.awt.event.KeyEvent.VK_LEFT:return KeyEvent.KEYCODE_DPAD_LEFT;
        case java.awt.event.KeyEvent.VK_RIGHT:return KeyEvent.KEYCODE_DPAD_RIGHT;
        case java.awt.event.KeyEvent.VK_ENTER:return KeyEvent.KEYCODE_ENTER;
        case java.awt.event.KeyEvent.VK_ESCAPE:return KeyEvent.KEYCODE_ESCAPE;
        case java.awt.event.KeyEvent.VK_BACK_SPACE:return KeyEvent.KEYCODE_BUTTON_B;
        case java.awt.event.KeyEvent.VK_A:return KeyEvent.KEYCODE_A;
        case java.awt.event.KeyEvent.VK_D:return KeyEvent.KEYCODE_D;
        case java.awt.event.KeyEvent.VK_W:return KeyEvent.KEYCODE_W;
        case java.awt.event.KeyEvent.VK_S:return KeyEvent.KEYCODE_S;
        case java.awt.event.KeyEvent.VK_CONTROL:return KeyEvent.KEYCODE_CTRL_LEFT;
        default:return -1;
    }}
}
