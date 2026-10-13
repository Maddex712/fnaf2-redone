package dev.thor.fnaf2;

/** Presentation tracking must never control gameplay or attack deadlines. */
final class ScarePlayback {
    double presented=-1,pending;
    void reset(){presented=-1;pending=0;}
    void presented(double time){presented=Math.max(presented,time);}
    double advance(double time,double dt,int actor){
        return Math.max(0,dt);
    }
}
