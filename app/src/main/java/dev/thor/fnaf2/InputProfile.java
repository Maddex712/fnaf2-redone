package dev.thor.fnaf2;
public final class InputProfile {
    public static final int TOUCH=0, PC=1, CONTROLLER=2;
    public int mode=CONTROLLER;
    private float mouseX=Float.NaN,mouseY=Float.NaN;
    public static boolean controllerSource(int source){return (source&0x401)==0x401||(source&0x1000010)==0x1000010||(source&0x201)==0x201;}
    public boolean select(int next){if(mode==next)return false;mode=next;return true;}
    public boolean mouseMoved(float x,float y){boolean changed=x!=mouseX||y!=mouseY;mouseX=x;mouseY=y;return changed;}
    public static boolean controllerActive(float... axes){for(float a:axes)if(Math.abs(a)>.2f)return true;return false;}
    public static float officeAxis(int game,float fraction,float sensitivity){
        if(!(sensitivity>0)||fraction<0||fraction>1)return 0;
        if(game==1){float pixels=fraction<315f/1280?-5:fraction<539f/1280?-2:fraction>999f/1280?5:fraction>759f/1280?2:0;return pixels*120/320/sensitivity;}
        return (fraction<367f/1024?-.2f:fraction>649f/1024?.2f:0)/sensitivity;
    }
    public static final class Hover {
        private int bar=-1;
        public boolean enter(int next){boolean entered=next>=0&&next!=bar;bar=next;return entered;}
        public void reset(){bar=-1;}
    }
}
