package dev.thor.fnaf2;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.JSONObject;

final class Fnaf2Assets {
    final Bitmap menuWithered,menuToys,menuPuppet;
    final Context context;final File game;final ExecutorService worker=Executors.newSingleThreadExecutor();
    final LruCache<Integer,Bitmap> cache=new LruCache<Integer,Bitmap>(80*1024*1024){protected int sizeOf(Integer k,Bitmap b){return b.getAllocationByteCount();}};
    final Set<Integer> pending=ConcurrentHashMap.newKeySet();final Map<Integer,Bitmap> scares=new ConcurrentHashMap<>();
    final Map<Integer,Bitmap> maskImages=new ConcurrentHashMap<>();
    final Map<Integer,Bitmap> titleImages=new ConcurrentHashMap<>();
    final Map<Integer,Bitmap> cues=new ConcurrentHashMap<>();volatile int preparedScare=-1;
    volatile boolean ready,importing,closed;volatile String status="Import your FNaF 2 Steam data";
    Fnaf2Assets(Context c){context=c;menuWithered=bundled("menu-withered.jpg");menuToys=bundled("menu-toys.webp");menuPuppet=bundled("menu-puppet-peek.png");game=new File(c.getFilesDir(),"game");ready=new File(game,"Images/00092.png").isFile();}
    Bitmap bundled(String name){try(InputStream in=context.getAssets().open(name)){return BitmapFactory.decodeStream(in);}catch(IOException ex){return null;}}
    File sound(String name){return new File(game,"Sounds/"+name+".wav");}
    Bitmap decode(int id){BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;return BitmapFactory.decodeFile(new File(game,String.format(Locale.US,"Images/%05d.png",id)).getPath(),o);}
    static boolean titleImage(int id){for(int n:MenuBackdrop.LINEUP)if(id==n)return true;for(int n:MenuBackdrop.STATIC)if(id==n)return true;for(int n:MenuBackdrop.BLIP)if(id==n)return true;return false;}
    static boolean cueImage(int id){for(int[] scare:Fnaf2Catalog.SCARE)if(id==scare[0])return true;for(int n:Fnaf2State.CAMERA_DOWN)if(id==n)return true;return id==357||id==361||id==363||id==494||id==495||id==496||id==497||id==307||id==308||id==489||id==490;}
    Bitmap image(int id){if(!ready||closed||id<0)return null;Bitmap b=titleImages.get(id);if(b==null)b=cues.get(id);if(b==null)b=maskImages.get(id);if(b==null)b=scares.get(id);if(b==null)b=cache.get(id);if(b!=null)return b;if(pending.add(id))worker.execute(()->{try{Bitmap loaded=decode(id);if(loaded!=null&&!closed){if(titleImage(id))titleImages.put(id,loaded);else if(cueImage(id))cues.put(id,loaded);else if(id==125||id==88||id==143||(id>=133&&id<=140))maskImages.put(id,loaded);else cache.put(id,loaded);}}finally{pending.remove(id);}});return null;}
    void warm(){for(int[] scare:Fnaf2Catalog.SCARE)image(scare[0]);for(int id:Fnaf2State.MASK_DOWN)image(id);for(int id:Fnaf2State.MASK_UP)image(id);for(int id:MenuBackdrop.LINEUP)image(id);for(int id:MenuBackdrop.STATIC)image(id);for(int id:MenuBackdrop.BLIP)image(id);for(int id:Fnaf2State.CAMERA_DOWN)image(id);for(int id:new int[]{361,357,363,503,507,187,121,221,218,217,219,78,326,681,518,608,610,555,612,65,73,210,216,494,495,496,497,307,308,489,490,469,92,123,125,429,272,361,2,601,603,604,606,611,293,291,296,292})image(id);for(int id:Fnaf2State.CAMERA_IMAGE)image(id);for(int scene:new int[]{35,36,61,69,74,76,97,98,99})image(Fnaf2Catalog.SCENE[scene]);}
    void prepareScare(int who,Runnable done){worker.execute(()->{if(preparedScare!=who){preparedScare=-1;scares.clear();for(int id:Fnaf2Catalog.SCARE[who]){if(closed)return;Bitmap b=decode(id);if(b!=null)scares.put(id,b);}preparedScare=who;}done.run();});}
    static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    void importPack(Uri uri,Runnable done){
        if(importing)return;importing=true;status="Importing Steam data…";
        worker.execute(()->{File temp=new File(game.getParentFile(),"import-temp"),old=new File(game.getParentFile(),"previous-game");
            try{clear(temp);if(!temp.mkdirs())throw new IOException("Cannot create import directory");int count=0;long total=0;Set<String> names=new HashSet<>();
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(context.getContentResolver().openInputStream(uri)))){ZipEntry e;byte[] buf=new byte[65536];while((e=zip.getNextEntry())!=null){String n=e.getName();if(e.isDirectory())continue;if(!(n.matches("Images/[0-9]{5}\\.png")||n.matches("Sounds/[^/\\\\]{1,100}\\.wav")||n.equals("pack.json")))throw new IOException("Unexpected pack entry");if(++count>900||!names.add(n))throw new IOException("Invalid pack entries");File f=new File(temp,n);f.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(f)){int len;while((len=zip.read(buf))!=-1){total+=len;if(total>1024L*1024*1024)throw new IOException("Pack too large");out.write(buf,0,len);}}}}
                try(InputStream in=new FileInputStream(new File(temp,"pack.json"))){if(!"fnaf2-steam-288".equals(new JSONObject(read(in)).optString("format")))throw new IOException("Wrong game pack");}
                if(count!=871||!new File(temp,"Images/00092.png").isFile())throw new IOException("Incomplete FNaF 2 pack");
                clear(old);if(game.exists()&&!game.renameTo(old))throw new IOException("Cannot replace game pack");if(!temp.renameTo(game)){old.renameTo(game);throw new IOException("Cannot activate game pack");}clear(old);cache.evictAll();ready=true;status="Steam data ready";
            }catch(Exception ex){status="Import failed: "+ex.getMessage();}finally{importing=false;done.run();}
        });
    }
    static void clear(File f)throws IOException{if(!f.exists())return;if(f.isDirectory()){File[] cs=f.listFiles();if(cs==null)throw new IOException("Cannot read import folder");for(File c:cs)clear(c);}if(!f.delete())throw new IOException("Cannot clean import folder");}
    void close(){closed=true;worker.shutdown();cache.evictAll();scares.clear();maskImages.clear();titleImages.clear();cues.clear();}
}
