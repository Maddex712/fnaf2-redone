package dev.thor.fnaf2;

/** Physical button chord; trigger axes and trigger buttons may both be reported. */
final class ButtonChord {
    static final int L1=1,R1=2,L2=4,R2=8,A=16,B=32;
    private int buttons,axes;
    private final int required;
    private boolean latched;
    ButtonChord(int required){this.required=required;}
    boolean button(int bit,boolean down){if(down)buttons|=bit;else buttons&=~bit;return check();}
    boolean triggers(float left,float right){axes=(left>.5f?L2:0)|(right>.5f?R2:0);return check();}
    private boolean check(){boolean complete=((buttons|axes)&required)==required;boolean fire=complete&&!latched;latched=complete;return fire;}
    void reset(){buttons=axes=0;latched=false;}
}
