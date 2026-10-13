package dev.thor.fnaf2;
/** Dream frame 13 and its three-second error interstitial. */
final class DreamSequence {
    boolean yellow;int nextNight,ticks;double accumulator,pan,center=1100,puppetX=2170;
    void start(int n){yellow=false;nextNight=n;ticks=0;accumulator=pan=0;center=1100;puppetX=2170;}
    void tick(double dt){accumulator+=dt;while(accumulator>=1.0/60){accumulator-=1.0/60;ticks++;if(ticks<1980){center=Math.max(530,Math.min(1973,center+4*pan));if(nextNight==4&&center<=534)yellow=true;if(puppetX<center)puppetX+=3;else if(puppetX>center)puppetX-=3;}}}
    boolean active(){return nextNight>0;}
    boolean done(){return ticks>=2160;}
    int shade(){return Math.max(0,255-(int)(ticks/6.6));}
}
