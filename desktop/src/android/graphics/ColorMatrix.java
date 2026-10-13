package android.graphics;
public class ColorMatrix {
 float saturation=1;float[] values={1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0};
 public ColorMatrix(){}public ColorMatrix(float[] matrix){if(matrix.length!=20)throw new IllegalArgumentException("20 matrix values required");values=matrix.clone();}
 public void postConcat(ColorMatrix post){float[] a=post.values,b=values,c=new float[20];for(int row=0;row<4;row++)for(int col=0;col<5;col++){float v=col==4?a[row*5+4]:0;for(int k=0;k<4;k++)v+=a[row*5+k]*b[k*5+col];c[row*5+col]=v;}values=c;}
 public void setSaturation(float s){saturation=s;float r=.213f*(1-s),g=.715f*(1-s),b=.072f*(1-s);values=new float[]{r+s,g,b,0,0,r,g+s,b,0,0,r,g,b+s,0,0,0,0,0,1,0};}
}
