package android.os;
public class SystemClock {public static long uptimeMillis(){return System.nanoTime()/1000000;}public static long elapsedRealtimeNanos(){return System.nanoTime();}}
