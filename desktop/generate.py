from pathlib import Path
root=Path(__file__).resolve().parent
source=root.parent/'app/src/main/java/dev/thor/fnaf2'
out=root/'build/generated/dev/thor/fnaf2';out.mkdir(parents=True,exist_ok=True)
for file in source.glob('*.java'):
    if file.stem=='MainActivity':continue
    text=file.read_text(encoding='utf-8-sig')
    if file.stem=='Fnaf2View':
        text=text.replace('Typeface.createFromAsset(context.getAssets(),"consola.ttf")','Typeface.MONOSPACE')
        start=text.index('    @Override protected void onDraw(');end=text.index('    void drawScaled(',start)
        text=text[:start]+'''    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);drawScaled(c,app.desktopUpper(),getWidth(),getHeight(),0);}
'''+text[end:]
        start=text.index('    boolean pcSingle()');end=text.index('    int pointerBar(',start)
        text=text[:start]+'''    boolean pcSingle(){return true;}
    boolean pcUpper(){return app.desktopUpper();}
'''+text[end:]
        text=text.replace('return !app.minigame.active&&app.assets.ready', 'return app.state.playing&&!app.minigame.active&&app.assets.ready')
        text=text.replace('"SCAN STEAM FILE / SELECT EXE OR ZIP"','"CLICK / ENTER: SCAN STEAM OR SELECT EXE / ZIP"')
        text=text.replace('captureBusy=false;}','captureBusy=false;shot.close();}',1)
        text=text.replace('if(pcSingle()&&pcGameplay()&&app.state.cameraFlipImage()>=0)', 'if(app.state.live()&&app.state.cameraFlipImage()>=0)')
    if file.stem=='Fnaf2Audio':
        text=text.replace('final class Fnaf2Audio {', 'final class Fnaf2Audio {boolean paused;')
        text=text.replace('p.prepare();channels.put','channels.put')
        text=text.replace('done.release();});p.start();','done.release();});p.setOnErrorListener((failed,what,extra)->{if(channels.get(channel)==failed){channels.remove(channel);sources.remove(channel);levels.remove(channel);}failed.release();return true;});p.setOnPreparedListener(done->{if(channels.get(channel)==done&&!paused)done.start();});p.prepareAsync();')
        text=text.replace('void pause(boolean pause){', 'void pause(boolean pause){paused=pause;')
    (out/file.name).write_text(text,encoding='utf-8')
s=(source/'MainActivity.java').read_text(encoding='utf-8-sig')
fields=s[s.index('    Fnaf2State state='):s.index('    @Override public void onCreate')]
fields=fields.replace('DisplayManager displays;Presentation presentation;','').replace('return getPackageName().endsWith(".dev");','return false;')
setup=s[s.index('encounters=new EncounterArchive'):s.index('        displays=(DisplayManager)')]
frame=s[s.index('    final Runnable frame='):s.index('    long nextSceneSeed;')]
frame=frame.replace('if(!resumed)return;', 'if(!resumed||closed)return;if(window!=null&&!window.isFocused()){last=SystemClock.elapsedRealtimeNanos();return;}if(controller!=null)controller.poll();')
frame=frame.replace('handler.postDelayed(this,16);','')
logic=s[s.index('    long nextSceneSeed;'):s.rfind('}')]
start=logic.index('    void importData()');end=logic.index('    String[] menu()',start)
logic=logic[:start]+logic[end:]
logic=logic.replace('    @Override public void onBackPressed(){back();}','')
logic=logic.replace('    @Override public boolean dispatchKeyEvent(KeyEvent e){return keys(e)||super.dispatchKeyEvent(e);}','')
logic=logic.replace('    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return motion(e)||super.dispatchGenericMotionEvent(e);}','')
start=logic.index('try{startActivity(new Intent(');end=logic.index('return;}if(page==0',start)
logic=logic[:start]+'openProject();}'+logic[end:]
template=(root/'src/dev/thor/fnaf2/MainActivity.template').read_text()
(out/'MainActivity.java').write_text(template.replace('/*FIELDS*/',fields).replace('/*SETUP*/',setup).replace('/*FRAME*/',frame).replace('/*LOGIC*/',logic))
