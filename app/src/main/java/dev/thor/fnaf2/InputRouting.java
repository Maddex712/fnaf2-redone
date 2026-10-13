package dev.thor.fnaf2;

/** Only the visible, interactive screen receives controller navigation. */
final class InputRouting {
    static final int TITLE=0, GAME=1, PAUSED=2, RESULT=3, TRANSITION=4;
    static int mode(Fnaf2State s,int intro,int ending){
        if(intro>0||ending>0)return TRANSITION;
        if(!s.playing)return TITLE;
        if(s.won||s.dead||s.phase!=0)return RESULT;
        return s.paused?PAUSED:GAME;
    }
}
