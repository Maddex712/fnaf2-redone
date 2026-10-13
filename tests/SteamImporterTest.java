package dev.thor.fnaf2;
import java.io.*;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Arrays;

/** Integration oracle: compare every decoded pixel and every WAV byte with the
 * independently extracted private reference. Never include the reference in a release. */
public final class SteamImporterTest {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("EXE, private reference directory, fresh output directory required");
        File destination=new File(args[2]);if(destination.exists())throw new IOException("Use a fresh output directory");
        if(!destination.mkdirs())throw new IOException("Cannot create test output");
        File invalid=new File(destination,"invalid.exe");Files.write(invalid.toPath(),new byte[]{'M','Z',0});
        try{SteamImporter.extract(invalid,new File(destination,"must-not-exist"),s->{});throw new AssertionError("Wrong executable accepted");}
        catch(IOException expected){if(!expected.getMessage().startsWith("Unsupported game file"))throw expected;}
        if(new File(destination,"must-not-exist").exists())throw new AssertionError("Wrote before validating EXE");
        Files.delete(invalid.toPath());
        long start=System.nanoTime();
        SteamImporter.extract(new File(args[0]),destination,s->{});
        File reference=new File(args[1]);int images=0,sounds=0;
        for(File f:new File(reference,"Images").listFiles()){
            if(!f.getName().endsWith(".png"))continue;
            BufferedImage want=ImageIO.read(f),got=ImageIO.read(new File(destination,"Images/"+f.getName()));
            if(want.getWidth()!=got.getWidth()||want.getHeight()!=got.getHeight())throw new AssertionError("Dimensions: "+f);
            int w=want.getWidth(),h=want.getHeight();
            if(!Arrays.equals(want.getRGB(0,0,w,h,null,0,w),got.getRGB(0,0,w,h,null,0,w)))throw new AssertionError("Pixels differ: "+f);
            images++;
        }
        for(File f:new File(reference,"Sounds").listFiles()){
            if(!f.getName().endsWith(".wav"))continue;
            if(!Arrays.equals(Files.readAllBytes(f.toPath()),Files.readAllBytes(new File(destination,"Sounds/"+f.getName()).toPath())))throw new AssertionError("Sound differs: "+f);
            sounds++;
        }
        if(images!=804||sounds!=66)throw new AssertionError("Incomplete extraction");
        System.out.printf("PASS: %d image pixel comparisons, %d WAV byte comparisons, rejected wrong EXE (%.1fs)%n",images,sounds,(System.nanoTime()-start)/1e9);
    }
}
