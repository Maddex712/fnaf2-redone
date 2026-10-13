package dev.thor.fnaf2;
import android.graphics.*;

/** Curved CRT glass with inverse input mapping; content never stretches independently. */
final class RetroMonitor {
    static final int COLS=24,ROWS=20;
    static final float CURVE=.055f;
    final Paint paint=new Paint(3);final RectF glass=new RectF();
    Bitmap screen;Canvas buffer;float[] vertices;float width;
    void layout(float w){glass.set(53,47,w-53,684);width=w;}
    float[] source(float x,float y,float w){
        layout(w);float px=(x-glass.centerX())/(glass.width()/2),py=(y-glass.centerY())/(glass.height()/2),u=px,v=py;
        for(int i=0;i<7;i++){u=px/(1-CURVE*v*v);v=py/(1-CURVE*u*u);}
        return Math.abs(u)>1||Math.abs(v)>1?null:new float[]{(u+1)*w/2,(v+1)*384};
    }
    float[] destination(float x,float y,float w){layout(w);float u=x/w*2-1,v=y/384-1;return new float[]{glass.centerX()+u*glass.width()/2*(1-CURVE*v*v),glass.centerY()+v*glass.height()/2*(1-CURVE*u*u)};}
    void draw(Canvas c,Fnaf2View view,float w){
        layout(w);int bw=(int)Math.ceil(w);
        if(screen==null||screen.getWidth()!=bw){if(screen!=null)screen.recycle();screen=Bitmap.createBitmap(bw,768,Bitmap.Config.ARGB_8888);buffer=new Canvas(screen);vertices=new float[(COLS+1)*(ROWS+1)*2];for(int j=0;j<=ROWS;j++)for(int i=0;i<=COLS;i++){float[] xy=destination(w*i/COLS,768f*j/ROWS,w);int n=(j*(COLS+1)+i)*2;vertices[n]=xy[0];vertices[n+1]=xy[1];}}
        screen.eraseColor(Color.BLACK);view.pane(buffer,false,w);
        paint.setShader(new LinearGradient(0,0,w,768,new int[]{0xffb1aa96,0xff827d6e,0xff57574f},null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(7,7,w-7,761),24,24,paint);paint.setShader(null);
        paint.setColor(0xff3a3b34);c.drawRoundRect(new RectF(36,30,w-36,704),38,38,paint);
        paint.setColor(0xff090e0c);c.drawRoundRect(new RectF(45,39,w-45,695),35,35,paint);
        paint.setColor(Color.WHITE);c.drawBitmapMesh(screen,COLS,ROWS,vertices,0,null,0,paint);
        c.save();Path clip=new Path();clip.addRoundRect(glass,35,35,Path.Direction.CW);c.clipPath(clip);
        paint.setShader(new RadialGradient(glass.centerX(),glass.centerY(),glass.width()*.69f,new int[]{0,0,0x80000000},new float[]{0,.59f,1},Shader.TileMode.CLAMP));paint.setColor(Color.WHITE);c.drawRect(glass,paint);paint.setShader(null);
        paint.setShader(new LinearGradient(glass.left,glass.top,glass.left+160,glass.top+145,new int[]{0x14e0e6dc,0},null,Shader.TileMode.CLAMP));c.drawOval(new RectF(glass.left+25,glass.top+5,glass.right-40,glass.top+62),paint);paint.setShader(null);c.restore();
        paint.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));paint.setTextSize(17);paint.setColor(0xff252a24);
        String brand="PROPERTY OF FAZBEAR ENTERTAINMENT";float max=w-305;if(paint.measureText(brand)>max)paint.setTextSize(17*max/paint.measureText(brand));c.drawText(brand,58,738,paint);
        paint.setColor(0xff82a550);c.drawCircle(w-76,734,4,paint);paint.setColor(0xff44483d);for(int i=0;i<8;i++)c.drawRect(w-218+i*12,723,w-214+i*12,741,paint);
    }
}
