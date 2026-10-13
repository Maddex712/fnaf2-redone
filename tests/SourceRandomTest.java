package dev.thor.fnaf2;
import java.nio.file.*;
public final class SourceRandomTest {
 public static void main(String[] args)throws Exception{
  int checks=0;
  int[][] cases={{0,20,0},{0,100000,0},{1,20,9},{1,1000000,8130},{65535,20,10}};
  for(int[] c:cases){int actual=new SourceRandom(c[0]).nextInt(c[1]);if(actual!=c[2])throw new AssertionError("Native fixture "+c[0]+","+c[1]+" got "+actual);checks++;}
  final long[] time={65537};SourceRandom timed=new SourceRandom(()->time[0]);
  if(timed.nextInt(20)!=new SourceRandom(1).nextInt(20))throw new AssertionError("Initial low-word clock seed");checks++;
  time[0]=131079;timed.beginScene();if(timed.nextInt(1000)!=new SourceRandom(7).nextInt(1000))throw new AssertionError("New scene reseeds from the clock");checks++;
  SourceRandom fixed=new SourceRandom(123),control=new SourceRandom(123);fixed.nextInt(20);control.nextInt(20);fixed.beginScene();if(fixed.nextInt(1000)!=control.nextInt(1000))throw new AssertionError("Explicit replay seed remains deterministic");checks++;
  if(args.length>0)for(String line:Files.readAllLines(Paths.get(args[0]))){String[] c=line.split(",");int actual=new SourceRandom(Integer.parseInt(c[0])).nextInt(Integer.parseInt(c[1]));if(actual!=Integer.parseInt(c[2]))throw new AssertionError("Original executable mismatch: "+line);checks++;}
  System.out.println("Source RNG: "+checks+" native arithmetic fixtures passed");
 }
}
