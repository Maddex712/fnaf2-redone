package android.view;
public class Display {public final java.awt.GraphicsDevice device;final int id;public Display(java.awt.GraphicsDevice d,int i){device=d;id=i;}public int getDisplayId(){return id;}public String getName(){return device.getIDstring();}}
