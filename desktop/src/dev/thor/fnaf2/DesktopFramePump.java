package dev.thor.fnaf2;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Monotonic 60 Hz deadlines; at most one pending update, all game state stays on the EDT. */
final class DesktopFramePump implements AutoCloseable {
    private final AtomicBoolean queued=new AtomicBoolean();
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Frame deadlines");t.setDaemon(true);return t;});
    private volatile boolean closed;
    DesktopFramePump(Runnable frame){
        clock.scheduleAtFixedRate(()->{
            if(!closed&&queued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{
                try{if(!closed)frame.run();}finally{queued.set(false);}
            });
        },0,16_666_667,TimeUnit.NANOSECONDS);
    }
    public void close(){closed=true;clock.shutdownNow();}
}
