package android.graphics;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded cache of scaled artwork. Panning changes the crop, not the texture size. EDT only. */
final class ScaledImages {
    static final long LIMIT=128L*1024*1024;
    private static final LinkedHashMap<Key,BufferedImage> images=new LinkedHashMap<>(32,.75f,true);
    private static long bytes;
    private record Key(BufferedImage source,int width,int height,boolean smooth) {}
    static BufferedImage get(BufferedImage source,int width,int height,boolean smooth){
        if(width==source.getWidth()&&height==source.getHeight())return source;
        long size=(long)width*height*4;
        if(width<=0||height<=0||size>LIMIT/2)return null;
        Key key=new Key(source,width,height,smooth);
        BufferedImage result=images.get(key);if(result!=null)return result;
        result=new BufferedImage(width,height,source.getColorModel().hasAlpha()?BufferedImage.TYPE_INT_ARGB_PRE:BufferedImage.TYPE_INT_RGB);
        Graphics2D g=result.createGraphics();
        try{g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,smooth?RenderingHints.VALUE_INTERPOLATION_BILINEAR:RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(source,0,0,width,height,null);}finally{g.dispose();}
        while(bytes+size>LIMIT&&!images.isEmpty()){
            Map.Entry<Key,BufferedImage> eldest=images.entrySet().iterator().next();
            bytes-=(long)eldest.getValue().getWidth()*eldest.getValue().getHeight()*4;images.remove(eldest.getKey());
        }
        images.put(key,result);bytes+=size;return result;
    }
}
