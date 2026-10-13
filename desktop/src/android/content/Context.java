package android.content;
import java.io.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 final java.util.Map<String,SharedPreferences> namedPrefs=new java.util.HashMap<>();
 public synchronized SharedPreferences getSharedPreferences(String name,int mode){return namedPrefs.computeIfAbsent(name,n->{File dir=new File(directory,"preferences/"+n);dir.mkdirs();return new SharedPreferences(dir);});}
 final File directory;
 public Context(){String custom=System.getProperty("fnaf2.dataDir");String base=System.getenv("LOCALAPPDATA");directory=custom!=null?new File(custom):new File(base==null?System.getProperty("user.home"):base,"FNaF2Redone");directory.mkdirs();}
 public File getFilesDir(){return directory;}public File getCacheDir(){File f=new File(directory,"cache");f.mkdirs();return f;}
 public Assets getAssets(){return new Assets();}public Resolver getContentResolver(){return new Resolver();}
 public static class Assets {public InputStream open(String name)throws IOException{InputStream in=Context.class.getResourceAsStream("/assets/"+name);if(in==null)throw new FileNotFoundException(name);return in;}}
 public static class Resolver {public InputStream openInputStream(android.net.Uri uri)throws IOException{return new FileInputStream(uri.file);}}
}
