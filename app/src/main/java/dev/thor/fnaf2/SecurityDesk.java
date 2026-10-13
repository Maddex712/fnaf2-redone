package dev.thor.fnaf2;
import android.graphics.*;

final class SecurityDesk {
    final Fnaf2View v;final MainActivity a;
    SecurityDesk(Fnaf2View view){v=view;a=view.app;}
    void text(Canvas c,String s,float x,float y,float size){v.text(c,s,x,y,size,0xffb9c2ac);}
    void draw(Canvas c,float w){
        Fnaf2State s=a.state;v.rect(c,0,0,w,768,0xff111613);
        v.rect(c,25,22,w-50,45,0xff30382f);text(c,"SECURITY DESK / 11/1987",42,53,24);
        text(c,"NIGHT "+s.night,42,111,27);text(c,s.hour()+":00 AM",w-220,111,31);
        text(c,"FLASHLIGHT",42,157,19);v.outline(c,205,138,w-255,21,0xff79876b);v.rect(c,208,141,(float)((w-261)*s.flashlightPower),15,0xff94a577);
        // The panel shows only the player's controls, never enemy locations or remote camera feeds.
        v.outline(c,w*.29f,206,w*.42f,157,0xff677362);text(c,"OFFICE",w*.42f,307,28);
        v.outline(c,w*.43f,177,w*.14f,30,0xff677362);v.outline(c,w*.14f,262,w*.15f,39,0xff677362);v.outline(c,w*.71f,262,w*.15f,39,0xff677362);
        lamp(c,w*.50f,216,s.flashlight&&!s.mask);lamp(c,w*.29f,282,s.leftLight);lamp(c,w*.71f,282,s.rightLight);
        text(c,s.maskMotion!=0?"MASK IN MOTION":s.mask?"MASK ON":"MASK OFF",w*.39f,343,18);
        float gap=12,bw=(w-108)/3;
        v.button(c,"LEFT VENT",42,388,bw,68,s.leftLight);v.button(c,"HALL LIGHT",54+bw,388,bw,68,s.flashlight);v.button(c,"RIGHT VENT",66+2*bw,388,bw,68,s.rightLight);
        text(c,a.controllerInput?"HOLD TO LIGHT / L1        A       R1":"HOLD VENT BUTTON / CTRL: HALL LIGHT",42,487,18);
        v.button(c,(a.controllerInput?"X  CAMERAS / CAM ":"CAMERAS / CAM ")+String.format(java.util.Locale.US,"%02d",s.camera+1),42,516,w-84,67,false);
        v.button(c,s.mask?(a.controllerInput?"B  REMOVE FREDDY MASK":"REMOVE FREDDY MASK"):(a.controllerInput?"B  PUT ON FREDDY MASK":"PUT ON FREDDY MASK"),42,598,w-84,58,s.mask);
        v.button(c,a.controllerInput?"Y  MUTE CALL":"MUTE CALL",42,687,(w-96)/2,46,false);v.button(c,a.controllerInput?"START  PAUSE":"PAUSE",54+(w-96)/2,687,(w-96)/2,46,false);
        text(c,a.controllerInput?(s.musicBox<.2?"!  HOLD R2 / WIND MUSIC BOX":"HOLD R2 / PRIZE CORNER + WIND"):"CAM 11: HOLD WIND MUSIC BOX",42,675,17);
        v.menuBackdrop.terminal(c,w,android.os.SystemClock.uptimeMillis());
    }
    void lamp(Canvas c,float x,float y,boolean on){v.p.setColor(on?0xffd6e5a3:0xff343c32);c.drawCircle(x,y,9,v.p);}
    void touch(float x,float y,float w){
        Fnaf2State s=a.state;
        if(y>=388&&y<=456){float cell=(w-84)/3;if(x<42+cell){s.setVent(true,true);v.held=3;}else if(x<42+2*cell){s.setFlashlight(true);v.held=2;}else{s.setVent(false,true);v.held=4;}}
        else if(y>=516&&y<583)a.monitor();else if(y>=598&&y<656)a.mask();
        else if(y>=687&&y<733){if(x<w/2)a.audio.stop("phone");else{s.paused=true;a.resetControls();a.audio.pause(true);}}
    }
}
