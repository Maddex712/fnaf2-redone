package dev.thor.fnaf2;
import android.graphics.*;

/** Screen-centered panorama bend. Interface and jumpscare overlays stay outside this pass. */
final class PerspectiveView {
    static final int COLUMNS=64,ROWS=24;
    Bitmap screen;Canvas buffer;float width,zoom;float[] vertices;
    final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    Canvas begin(float w){return begin(w,200);}
    Canvas begin(float w,float strength){
        int pixels=(int)Math.ceil(w);
        if(screen==null||screen.getWidth()!=pixels){release();screen=Bitmap.createBitmap(pixels,768,Bitmap.Config.ARGB_8888);buffer=new Canvas(screen);}
        if(vertices==null||width!=w||zoom!=strength){width=w;zoom=strength;vertices=new float[(COLUMNS+1)*(ROWS+1)*2];
            for(int row=0;row<=ROWS;row++)for(int col=0;col<=COLUMNS;col++){
                float u=col/(float)COLUMNS;
                // The Panorama extension samples a centered sine-varying column height.
                float sampleHeight=768-strength*(1-(float)Math.sin(Math.PI*u));
                int i=(row*(COLUMNS+1)+col)*2;
                vertices[i]=w*u;vertices[i+1]=384+(row*768f/ROWS-384)*(768/sampleHeight);
            }
        }
        screen.eraseColor(Color.BLACK);return buffer;
    }
    void finish(Canvas c,float w){c.save();c.clipRect(0,0,w,768);c.drawBitmapMesh(screen,COLUMNS,ROWS,vertices,0,null,0,paint);c.restore();}
    void release(){if(screen!=null)screen.recycle();screen=null;buffer=null;vertices=null;}
}
