package dev.thor.fnaf2;
import android.graphics.*;
import java.util.Random;

/** Original title events, adapted to a wider display without stretching faces. */
final class MenuBackdrop {
    static final int[] LINEUP={321,362,470,215};
    static final int[] STATIC={332,334,328,329,330,331};
    static final int[] BLIP={68,64,70,66,67};
    final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    final Random random=new Random();
    final Bitmap[] grain=new Bitmap[4];
    final ColorMatrixColorFilter washed;
    final MenuEffects effects=new MenuEffects();
    final ColorMatrixColorFilter puppetShade;
    final Paint puppetShadow=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Matrix shadowMatrix=new Matrix();
    final Shader faceShadow=new LinearGradient(0,0,1,0,new int[]{0xcc000000,0x16000000,0xa6000000},new float[]{0,.48f,1},Shader.TileMode.CLAMP);
    final Shader neckShadow=new LinearGradient(0,0,0,1,new int[]{0x00000000,0xc9000000},new float[]{.52f,1},Shader.TileMode.CLAMP);
    final Path chicaEdge=new Path();
    boolean edgeReady;
    Rect headSource,handSource;
    long epoch,lastChoice,lastLight,lastStatic;
    int variant,sceneAlpha=255,staticAlpha=155,blipAlpha=25;
    boolean blip;
    MenuBackdrop(){
        ColorMatrix color=new ColorMatrix();color.setSaturation(.62f);washed=new ColorMatrixColorFilter(color);
        ColorMatrix dim=new ColorMatrix(new float[]{.64f,0,0,0,0,0,.64f,0,0,0,0,0,.64f,0,0,0,0,0,1,0});dim.postConcat(color);puppetShade=new ColorMatrixColorFilter(dim);
        puppetShadow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        Random r=new Random(1987);int[] pixels=new int[640*360];
        for(int f=0;f<grain.length;f++){for(int i=0;i<pixels.length;i++)pixels[i]=(r.nextInt(39)<<24)|0x00ffffff;grain[f]=Bitmap.createBitmap(pixels,640,360,Bitmap.Config.ARGB_8888);}
    }
    void update(long now){
        if(epoch==0||now-lastChoice>1000){epoch=lastChoice=lastLight=lastStatic=now;variant=0;}
        lastChoice=now;effects.update(now);variant=effects.variant;
        sceneAlpha=effects.sceneAlpha;staticAlpha=effects.staticAlpha;blipAlpha=effects.blipAlpha;blip=effects.blip;
    }
    void bitmap(Canvas c,Bitmap b,RectF dst,int alpha){if(b==null)return;paint.setColor(Color.WHITE);paint.setAlpha(alpha);c.drawBitmap(b,null,dst,paint);paint.setAlpha(255);}
    void puppet(Canvas c,Fnaf2Assets assets,float w){
        if((effects.puppetAlpha<=0&&effects.handAlpha<=0)||assets.menuPuppet==null)return;
        if(headSource==null){Bitmap atlas=assets.menuPuppet;headSource=opaqueBounds(atlas,0,atlas.getWidth()/2);handSource=opaqueBounds(atlas,atlas.getWidth()/2,atlas.getWidth());}
        if(!edgeReady){
            Bitmap original=assets.image(321);if(original==null)return;
            // Follow the actual lit silhouette against the original pure black background.
            chicaEdge.reset();chicaEdge.moveTo(0,0);
            for(int y=0;y<768;y+=2){int edge=550;for(int x=220;x<550;x++){int pixel=original.getPixel(x,y);if(Math.max(Color.red(pixel),Math.max(Color.green(pixel),Color.blue(pixel)))>12){edge=x;break;}}chicaEdge.lineTo(edge-1,y);}
            chicaEdge.lineTo(0,768);chicaEdge.close();edgeReady=true;
        }
        c.save();c.translate(w-1024,0);
        // The face slides out behind Chica. Her actual silhouette hides the far eye and neck.
        float x=280+95*(1-effects.peekAmount);
        c.saveLayer(x,222,x+220,502,null);c.clipPath(chicaEdge);paint.setColorFilter(puppetShade);paint.setColor(Color.WHITE);
        paint.setAlpha(Math.round(sceneAlpha*effects.puppetAlpha));
        c.drawBitmap(assets.menuPuppet,headSource,new RectF(x,222,x+220,502),paint);
        // Shade only opaque sprite pixels: keep the outside transparent and the lit mask soft.
        shadowMatrix.setScale(220,280);shadowMatrix.postTranslate(x,222);
        faceShadow.setLocalMatrix(shadowMatrix);puppetShadow.setShader(faceShadow);c.drawRect(x,222,x+220,502,puppetShadow);
        neckShadow.setLocalMatrix(shadowMatrix);puppetShadow.setShader(neckShadow);c.drawRect(x,222,x+220,502,puppetShadow);c.restore();
        // Independent foreground fingers reach the shoulder before the head appears.
        float hx=360+14*(1-effects.handAmount);paint.setColorFilter(puppetShade);paint.setAlpha(Math.round(sceneAlpha*.94f*effects.handAlpha));
        c.drawBitmap(assets.menuPuppet,handSource,new RectF(hx,502,hx+56,568),paint);
        paint.setColorFilter(null);paint.setAlpha(255);c.restore();
    }
    static Rect opaqueBounds(Bitmap b,int left,int right){
        int x0=right,y0=b.getHeight(),x1=left,y1=0;
        for(int y=0;y<b.getHeight();y++)for(int x=left;x<right;x++)if(Color.alpha(b.getPixel(x,y))>12){x0=Math.min(x0,x);x1=Math.max(x1,x+1);y0=Math.min(y0,y);y1=Math.max(y1,y+1);}
        return x1>x0&&y1>y0?new Rect(x0,y0,x1,y1):new Rect(left,0,right,b.getHeight());
    }

    void draw(Canvas c,Fnaf2Assets assets,float w,long now){
        update(now);
        Bitmap b=assets.image(LINEUP[variant]);if(b==null)b=assets.image(321);
        paint.setColorFilter(washed);
        // Keep the lineup and Puppet silhouette in the same zoomed coordinate space.
        c.save();c.scale(1.45f,1.45f,w,300);
        if(b!=null){float width=768f*b.getWidth()/b.getHeight();RectF dst=new RectF(w-width,0,w,768);bitmap(c,b,dst,sceneAlpha);puppet(c,assets,w);
        }
        c.restore();paint.setColorFilter(null);
    }
    void finish(Canvas c,Fnaf2Assets assets,float w,long now){
        RectF screen=new RectF(0,0,w,768);
        bitmap(c,assets.image(STATIC[(int)(((now-epoch)*59.4/1000)%STATIC.length)]),screen,Math.round(staticAlpha*.82f));
        if(blip)bitmap(c,assets.image(BLIP[(int)(((now-epoch)*6/1000)%BLIP.length)]),screen,blipAlpha);
    }
    void terminal(Canvas c,float w,long now){
        c.save();c.clipRect(36,134,w-36,730);bitmap(c,grain[(int)(now/110%4)],new RectF(36,134,w-36,730),42);
        paint.setColor(0x1a000000);paint.setStrokeWidth(1);for(int y=134;y<730;y+=4)c.drawLine(36,y,w-36,y,paint);
        float sweep=now%7000/7000f*670+104;paint.setColor(0x0cffffff);c.drawRect(36,sweep,w-36,sweep+20,paint);c.restore();
    }
}
