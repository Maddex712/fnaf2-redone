package dev.thor.fnaf2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Saves the first actually rendered sighting, independently from campaign progression. */
final class EncounterArchive {
    private final File directory;
    private final SharedPreferences prefs;
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private int pending;
    private volatile int recorded;
    private Bitmap preview;
    private volatile int previewId=-1;
    EncounterArchive(Context context){directory=new File(context.getFilesDir(),"encounters");prefs=context.getSharedPreferences("encounters",Context.MODE_PRIVATE);for(int i=0;i<EncounterCatalog.TITLES.length;i++)if(prefs.getBoolean("seen_"+i,false)&&file(i).isFile())recorded|=1<<i;}
    boolean seen(int id){return id>=0&&id<EncounterCatalog.TITLES.length&&(recorded&(1<<id))!=0;}
    synchronized boolean needs(int id){return id>=0&&id<EncounterCatalog.TITLES.length&&!seen(id)&&(pending&(1<<id))==0;}
    int count(){return Integer.bitCount(recorded);}
    File file(int id){return new File(directory,"encounter-"+id+".jpg");}
    synchronized boolean needsDeveloperScene(int id){return id>=0&&id<EncounterCatalog.TITLES.length&&(pending&(1<<id))==0&&(!seen(id)||prefs.getString("location_"+id,"").startsWith("DEVELOPER SAMPLE"));}
    void saveDeveloperScene(int id,Bitmap image){saveInternal(id,image,6,"DEV staged gameplay capture",true);}
    void save(final int id,final Bitmap image,final int night,final String location){
        saveInternal(id,image,night,location,false);
    }
    private void saveInternal(final int id,final Bitmap image,final int night,final String location,boolean replace){
        synchronized(this){if(!(replace?needsDeveloperScene(id):needs(id))){image.recycle();return;}pending|=1<<id;}
        writer.execute(()->{
            File temp=new File(directory,"encounter-"+id+".tmp");
            try{
                if(!directory.isDirectory()&&!directory.mkdirs())return;
                try(FileOutputStream out=new FileOutputStream(temp)){if(!image.compress(Bitmap.CompressFormat.JPEG,90,out))return;out.getFD().sync();}
                if(temp.renameTo(file(id))){prefs.edit().putBoolean("seen_"+id,true).putInt("night_"+id,night).putString("location_"+id,location).putLong("time_"+id,System.currentTimeMillis()).apply();synchronized(this){recorded|=1<<id;previewId=-1;}}
            }catch(java.io.IOException ignored){}finally{image.recycle();temp.delete();synchronized(this){pending&=~(1<<id);}}
        });
    }
    Bitmap preview(int id){
        if(!seen(id))return null;
        if(id!=previewId){if(preview!=null)preview.recycle();preview=BitmapFactory.decodeFile(file(id).getAbsolutePath());previewId=id;}
        return preview;
    }
    void close(){writer.shutdown();}
    String detail(int id){return prefs.getString("location_"+id,"")+" / Night "+prefs.getInt("night_"+id,1);}
}
