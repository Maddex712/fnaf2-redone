package android.view;
public class View extends javax.swing.JPanel { private android.content.Context context; public android.content.Context getContext(){return context;} public void setFocusableInTouchMode(boolean b){}
 private java.awt.image.BufferedImage frame;
 private final android.graphics.Canvas.LayerPool layers=new android.graphics.Canvas.LayerPool();
 public static final String[] RENDER_MODES={"1080p","720p","Native"};
 private int renderMode;
 private long sampleStart,lastPaint;private int samples;private double fps,drawMs,scaleMs;
 protected boolean recordPerformance(){return true;}
 public void setRenderMode(int mode){renderMode=Math.floorMod(mode,RENDER_MODES.length);frame=null;layers.clear();repaint();}
 public String renderReport(){return String.format(java.util.Locale.US,"Renderer: %s%nDrawn FPS: %.1f / frame gap %.1f ms%nScene %.2f ms / upscale %.2f ms",RENDER_MODES[renderMode]+(frame!=null&&renderMode!=2?" / "+frame.getWidth()+" x "+frame.getHeight()+" composition":""),fps,fps>0?1000/fps:0,drawMs,scaleMs);}
 public View(android.content.Context c){context=c;setOpaque(true);setBackground(java.awt.Color.BLACK);setFocusable(true);java.awt.event.MouseAdapter m=new java.awt.event.MouseAdapter(){void send(java.awt.event.MouseEvent e,int a){onTouchEvent(new MotionEvent(a,e.getX(),e.getY(),InputDevice.SOURCE_MOUSE));}public void mousePressed(java.awt.event.MouseEvent e){requestFocusInWindow();send(e,MotionEvent.ACTION_DOWN);}public void mouseReleased(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_UP);}public void mouseDragged(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_MOVE);mouseMoved(e);}public void mouseMoved(java.awt.event.MouseEvent e){onGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_HOVER_MOVE,e.getX(),e.getY(),InputDevice.SOURCE_MOUSE));}public void mouseExited(java.awt.event.MouseEvent e){onGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_HOVER_EXIT,e.getX(),e.getY(),InputDevice.SOURCE_MOUSE));}};addMouseListener(m);addMouseMotionListener(m);}
 public void setDefaultFocusHighlightEnabled(boolean b){}public void setContentDescription(String s){getAccessibleContext().setAccessibleName(s);}protected void onDetachedFromWindow(){}protected void onDraw(android.graphics.Canvas c){}public void draw(android.graphics.Canvas c){onDraw(c);}public void invalidate(){super.invalidate();repaint();}public boolean onGenericMotionEvent(MotionEvent e){return false;}public boolean onTouchEvent(MotionEvent e){return false;}public boolean performClick(){return true;}
 protected void paintComponent(java.awt.Graphics graphics){
  int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
  long start=System.nanoTime();
  if(renderMode==2){
   java.awt.Graphics2D nativeFrame=(java.awt.Graphics2D)graphics.create();
   try{draw(new android.graphics.Canvas(nativeFrame,w,h,layers));}finally{nativeFrame.dispose();}
   record(start,System.nanoTime(),System.nanoTime());
   return;
  }
  // Compose at the selected physical resolution, independent of Windows display scaling.
  // The logical coordinates stay in component pixels, preserving shared hit boxes and drag input.
  double deviceScale=((java.awt.Graphics2D)graphics).getTransform().getScaleX();
  double scale=Math.min(deviceScale,Math.min((renderMode==0?1920.0:1280.0)/w,(renderMode==0?1080.0:720.0)/h));
  int rw=Math.max(1,(int)Math.round(w*scale)),rh=Math.max(1,(int)Math.round(h*scale));
  if(frame==null||frame.getWidth()!=rw||frame.getHeight()!=rh){frame=new java.awt.image.BufferedImage(rw,rh,java.awt.image.BufferedImage.TYPE_INT_RGB);layers.clear();}
  java.awt.Graphics2D scene=frame.createGraphics();
  try{scene.scale((double)rw/w,(double)rh/h);draw(new android.graphics.Canvas(scene,w,h,layers));}finally{scene.dispose();}
  long composed=System.nanoTime();
  java.awt.Graphics2D output=(java.awt.Graphics2D)graphics.create();
  try{output.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);output.drawImage(frame,0,0,w,h,null);}finally{output.dispose();}
  record(start,composed,System.nanoTime());
 }
 private void record(long start,long composed,long end){
  if(!recordPerformance()){sampleStart=0;samples=0;return;}
  if(sampleStart==0||start-lastPaint>500_000_000L){sampleStart=start;samples=0;}
  lastPaint=start;samples++;drawMs=drawMs*.9+(composed-start)/1e6*.1;scaleMs=scaleMs*.9+(end-composed)/1e6*.1;
  if(end-sampleStart>=500_000_000L){fps=samples*1e9/(end-sampleStart);sampleStart=end;samples=0;}
 }
}
