package dev.thor.fnaf2;
import java.util.Random;

/** Original runtime Random(n) arithmetic, independently checked against both owned executables.
 * Only the generator is matched here; global call order is audited separately. */
public final class SourceRandom extends Random {
    private int state;
    private final java.util.function.LongSupplier clock;
    public SourceRandom(){this(()->System.nanoTime()/1000000L);}
    SourceRandom(java.util.function.LongSupplier millis){super(0);clock=millis;state=(int)clock.getAsLong()&65535;}
    /** Native scene entry seeds from the low word of the monotonic millisecond clock.
     * Explicit seeds remain deterministic for reference replay. */
    public synchronized void beginScene(){if(clock!=null)setSeed(clock.getAsLong());}
    public SourceRandom(long seed){super(0);clock=null;state=(int)seed&65535;}
    @Override public synchronized void setSeed(long seed){super.setSeed(seed);state=(int)seed&65535;}
    @Override public synchronized int nextInt(int bound){
        if(bound<=0)throw new IllegalArgumentException("bound must be positive");
        state=(state*31415+1)&65535;
        return (int)(((long)state*(bound&65535))>>>16);
    }
}
