package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public enum Style{FILL,STROKE}
 int color=0xff000000,alpha=255;float textSize=16,stroke=1;boolean aa=true,filter=true;Style style=Style.FILL;Typeface typeface=Typeface.MONOSPACE;Shader shader;ColorFilter colorFilter;
 PorterDuffXfermode xfermode;public void setXfermode(PorterDuffXfermode mode){xfermode=mode;}
 public Paint(){}public Paint(int flags){aa=(flags&1)!=0;filter=(flags&2)!=0;}
 public void setColor(int c){color=c;alpha=c>>>24;}public void setAlpha(int a){alpha=Math.max(0,Math.min(255,a));}public void setStrokeWidth(float w){stroke=w;}public void setStyle(Style s){style=s;}public void setTextSize(float s){textSize=s;}public void setTypeface(Typeface t){typeface=t;}public void setAntiAlias(boolean b){aa=b;}public void setFilterBitmap(boolean b){filter=b;}public void setShader(Shader s){shader=s;}public void setColorFilter(ColorFilter f){colorFilter=f;}public ColorFilter getColorFilter(){return colorFilter;}
 public float measureText(String s){return (float)typeface.font.deriveFont(textSize).getStringBounds(s,new java.awt.font.FontRenderContext(null,aa,true)).getWidth();}
}
