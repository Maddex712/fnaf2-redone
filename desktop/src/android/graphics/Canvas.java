package android.graphics;
import java.awt.*;import java.awt.geom.*;import java.awt.image.BufferedImage;import java.util.ArrayDeque;
/** Java2D implementation of the drawing subset used by the shared Android renderer. */
public class Canvas {
 public enum EdgeType{BW,AA}
 public boolean quickReject(float l,float t,float r,float b,EdgeType type){return !g.hitClip((int)Math.floor(l),(int)Math.floor(t),(int)Math.ceil(r-l),(int)Math.ceil(b-t));}
 public void clipPath(Path p){g.clip(p.value);}public void drawPath(Path path,Paint p){shape(path.value,p);}
 public void drawOval(RectF r,Paint p){drawOval(r.left,r.top,r.right,r.bottom,p);}public void drawOval(float l,float t,float r,float b,Paint p){shape(new Ellipse2D.Float(l,t,r-l,b-t),p);}
 public void drawRoundRect(RectF r,float rx,float ry,Paint p){shape(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),2*rx,2*ry),p);}
 public void saveLayer(float l,float t,float r,float b,Paint p){saveLayerAlpha(l,t,r,b,p==null?255:p.alpha);}

 Graphics2D g;final int width,height,pixelWidth,pixelHeight;final AffineTransform base;final ArrayDeque<Frame> stack=new ArrayDeque<>();final LayerPool layers;float opacity=1;
 public static final class LayerPool{
  final ArrayDeque<BufferedImage> available=new ArrayDeque<>();
  public void clear(){available.clear();}
  BufferedImage acquire(int w,int h){BufferedImage b=available.pollFirst();if(b==null||b.getWidth()!=w||b.getHeight()!=h)b=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB_PRE);Graphics2D g=b.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,w,h);g.dispose();return b;}
  void release(BufferedImage b){if(available.size()<3)available.addFirst(b);}
 }
 static class Frame{Graphics2D previous;BufferedImage layer;float alpha;Frame(Graphics2D p,BufferedImage b,float a){previous=p;layer=b;alpha=a;}}
 public void close(){g.dispose();}
 public Canvas(Bitmap bitmap){this(bitmap.image.createGraphics(),bitmap.getWidth(),bitmap.getHeight());}
 public void saveLayer(RectF r,Paint p){saveLayerAlpha(r.left,r.top,r.right,r.bottom,255);}
 public void drawRect(RectF r,Paint p){drawRect(r.left,r.top,r.right,r.bottom,p);}
 public Canvas(Graphics2D g,int w,int h){this(g,w,h,new LayerPool());}
 public Canvas(Graphics2D g,int w,int h,LayerPool pool){this.g=g;layers=pool;width=w;height=h;base=g.getTransform();pixelWidth=Math.max(1,(int)Math.round(w*Math.abs(base.getScaleX())));pixelHeight=Math.max(1,(int)Math.round(h*Math.abs(base.getScaleY())));}
 public void scale(float x,float y,float cx,float cy){g.translate(cx,cy);g.scale(x,y);g.translate(-cx,-cy);}
 public void save(){stack.push(new Frame(g,null,opacity));g=(Graphics2D)g.create();}
 public void saveLayerAlpha(float l,float t,float r,float b,int alpha){BufferedImage layer=layers.acquire(pixelWidth,pixelHeight);stack.push(new Frame(g,layer,alpha/255f));Graphics2D next=layer.createGraphics();next.setTransform(g.getTransform());next.setClip(g.getClip());g=next;}
 public void restore(){Frame f=stack.pop();g.dispose();g=f.previous;if(f.layer!=null){Graphics2D v=(Graphics2D)g.create();v.setTransform(new AffineTransform());v.setComposite(AlphaComposite.SrcOver.derive(f.alpha));v.drawImage(f.layer,0,0,null);v.dispose();layers.release(f.layer);}else opacity=f.alpha;}
 public void translate(float x,float y){g.translate(x,y);}public void scale(float x,float y){g.scale(x,y);}public void rotate(float deg,float x,float y){g.rotate(Math.toRadians(deg),x,y);}public void rotate(float deg){g.rotate(Math.toRadians(deg));}public void skew(float x,float y){g.shear(x,y);}public Matrix getMatrix(){try{AffineTransform logical=base.createInverse();logical.concatenate(g.getTransform());return new Matrix(logical);}catch(NoninvertibleTransformException e){throw new IllegalStateException(e);}}
 public void clipRect(float l,float t,float r,float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));}public void clipRect(RectF r){clipRect(r.left,r.top,r.right,r.bottom);}
 void setup(Paint p,boolean bitmap){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,p.aa?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,p.aa?RenderingHints.VALUE_TEXT_ANTIALIAS_ON:RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,p.filter?RenderingHints.VALUE_INTERPOLATION_BILINEAR:RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.setComposite((p.xfermode==null?AlphaComposite.SrcOver:AlphaComposite.SrcAtop).derive(opacity*p.alpha/255f));g.setStroke(new BasicStroke(Math.max(.01f,p.stroke)));g.setPaint(p.shader!=null?p.shader.paint():new java.awt.Color(p.color&0xffffff));}
 void shape(Shape s,Paint p){setup(p,false);if(p.style==Paint.Style.STROKE)g.draw(s);else g.fill(s);}
 public void drawColor(int c){Paint p=new Paint();p.setColor(c);drawRect(0,0,width,height,p);}
 public void drawRect(float l,float t,float r,float b,Paint p){shape(new Rectangle2D.Float(l,t,r-l,b-t),p);}public void drawCircle(float x,float y,float radius,Paint p){shape(new Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2),p);}
 public void drawLine(float x,float y,float xx,float yy,Paint p){setup(p,false);g.draw(new Line2D.Float(x,y,xx,yy));}
 public void drawText(String s,float x,float y,Paint p){setup(p,false);g.setFont(p.typeface.font.deriveFont(p.textSize));g.drawString(s,x,y);}
 public void drawBitmap(Bitmap b,float x,float y,Paint p){drawBitmap(b,null,new RectF(x,y,x+b.getWidth(),y+b.getHeight()),p);}
 /** Column panorama subset: smooth narrow strips, sharing the Android bend geometry. */
 public void drawBitmapMesh(Bitmap b,int columns,int rows,float[] v,int offset,int[] colors,int colorOffset,Paint p){
  if(rows!=1)throw new IllegalArgumentException("Only column panorama meshes are supported");
  setup(p,true);
  for(int col=0;col<columns;col++){
   int a=offset+col*2,z=offset+(columns+1+col)*2;
   float left=v[a],right=v[a+2],top=(v[a+1]+v[a+3])/2,bottom=(v[z+1]+v[z+3])/2;
   Graphics2D slice=(Graphics2D)g.create();slice.clip(new Rectangle2D.Float(left,top,right-left+.02f,bottom-top));
   slice.translate(left,top);slice.scale((right-left)*columns/b.getWidth(),(bottom-top)/b.getHeight());slice.translate(-col*b.getWidth()/(double)columns,0);slice.drawImage(b.image,0,0,null);slice.dispose();
  }
 }
 public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){if(b==null)return;setup(p,true);BufferedImage img=b.filtered(p.colorFilter);Graphics2D v=(Graphics2D)g.create();v.clip(new Rectangle2D.Float(dst.left,dst.top,dst.width(),dst.height()));int sx=src==null?0:src.left,sy=src==null?0:src.top,sw=src==null?b.getWidth():src.right-src.left,sh=src==null?b.getHeight():src.bottom-src.top;v.translate(dst.left,dst.top);v.scale(dst.width()/sw,dst.height()/sh);v.translate(-sx,-sy);AffineTransform tx=v.getTransform();
 // Software affine blits resampled every pixel on every pan, blocking input on the EDT.
 // Cache scale separately from translation; retain crop/alpha in device space.
 if(v.getDeviceConfiguration().getDevice().getType()==GraphicsDevice.TYPE_IMAGE_BUFFER&&tx.getShearX()==0&&tx.getShearY()==0&&tx.getScaleX()>0&&tx.getScaleY()>0){
  BufferedImage scaled=ScaledImages.get(img,(int)Math.round(img.getWidth()*tx.getScaleX()),(int)Math.round(img.getHeight()*tx.getScaleY()),p.filter);
  if(scaled!=null){Shape clip=v.getClip();v.setTransform(new AffineTransform());if(clip!=null)v.setClip(tx.createTransformedShape(clip));v.drawImage(scaled,(int)Math.round(tx.getTranslateX()),(int)Math.round(tx.getTranslateY()),null);v.dispose();return;}
 }
 v.drawImage(img,0,0,null);v.dispose();}
}
