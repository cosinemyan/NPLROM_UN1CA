#!/usr/bin/env python3
"""Generate native direct-call patches from exact decoded Samsung APKs.

Never writes decoded input. Adds locals only to keep references alive across
Samsung methods that reuse p-registers. Rejects raw v aliases into parameters.
"""
import argparse,difflib,json,re
from pathlib import Path
W='Ldev/denver/dressroomadvanced/NativeWidgets;'
E='Ldev/denver/dressroomadvanced/NativeExtras;'
class Patcher:
 def __init__(self,root):self.root=Path(root);self.files={};self.report=[]
 def file(self,suffix):
  matches=list(self.root.glob('smali*/'+suffix+'.smali'))
  if len(matches)!=1:raise ValueError((suffix,len(matches)))
  p=matches[0];r=p.relative_to(self.root).as_posix()
  if r not in self.files:self.files[r]=[p.read_text(),p.read_text()]
  return r
 def method(self,suffix,signature,change):
  r=self.file(suffix);s=self.files[r][1]
  pat=re.compile(r'^\.method [^\n]* '+re.escape(signature)+r'\n.*?^\.end method$',re.M|re.S)
  hits=list(pat.finditer(s))
  if len(hits)!=1:raise ValueError((suffix,signature,'method count',len(hits)))
  m=hits[0];new=change(m.group());self.files[r][1]=s[:m.start()]+new+s[m.end():]
  self.report.append({'class':suffix,'method':signature})
 def callback(self,suffix,sig,api,args,params,when='after',returned=False):
  # Fixed native wrappers preserve every original register and its lifetime.
  # Inserting locals shifted short-form p-register operands beyond v15 in sd/m.
  if sig.startswith('<init>'):return self.constructor_callback(suffix,sig,api,args,params,when,returned)
  r=self.file(suffix);text=self.files[r][1]
  match=re.search(r'^\.method ([^\n]*) '+re.escape(sig)+r'\n.*?^\.end method$',text,re.M|re.S)
  if not match:raise ValueError((suffix,sig,'callback wrapper'))
  flags=match[1];name=sig.split('(')[0];proto=sig[len(name):]
  oldname='nativeAdvancedOriginal'+name[0].upper()+name[1:]
  while re.search(r' '+re.escape(oldname)+r'\(',text):oldname+='Again'
  original=match[0].replace(' '+sig+'\n',' '+oldname+proto+'\n',1)
  descriptors=re.findall(r'\[*L[^;]+;|\[*[ZBCSIFJD]',proto[1:proto.index(')')])
  words=sum(2 if d in ('J','D') else 1 for d in descriptors)+(0 if 'static' in flags else 1)
  result=proto.split(')',1)[1];is_object=result.startswith(('L','['))
  move='move-result-object' if is_object else 'move-result'
  ret='return-object' if is_object else 'return'
  code=''
  for i,(reg,typ)in enumerate(zip(args,params),1):
   code+=f'    {"move-object/from16" if typ in ("O","V") else "move/from16"} v{i}, {reg}\n'
  def call():
   output=''
   if returned:output+=f'    {"move-object/from16" if returned is True else "move/from16"} v{len(args)+1}, v0\n'
   count=len(args)+int(bool(returned))
   output+=f'    invoke-static/range {{v1 .. v{count}}}, {api}\n' if count else f'    invoke-static {{}}, {api}\n'
   if returned is not True and returned:output+='    move-result v0\n'
   return output
  if when=='before':code+=call()
  op='invoke-static' if 'static' in flags else 'invoke-direct' if 'private' in flags else 'invoke-virtual'
  code+=f'    {op}/range {{p0 .. p{words-1}}}, L{suffix};->{oldname}{proto}\n'
  if result!='V':code+=f'    {move} v0\n'
  if when!='before':code+=call()
  code+='    return-void\n' if result=='V' else f'    {ret} v0\n'
  wrapper=f'\n.method {flags} {sig}\n    .locals {len(args)+int(bool(returned))+1}\n'+code+'.end method\n'
  self.files[r][1]=text[:match.start()]+original+text[match.end():]+wrapper
  self.report.append({'class':suffix,'method':sig,'nativeWrapper':True})
 def constructor_callback(self,suffix,sig,api,args,params,when='after',returned=False):
  """Capture object/int p registers into dedicated locals; no p lifetime assumptions."""
  def change(s):
   m=re.search(r'    \.locals (\d+)',s)
   if not m:raise ValueError('locals absent')
   n=int(m[1])
   # Convert only instruction register operands, never descriptors such as
   # Laod/v92;, member names, labels, string literals or debug metadata.
   operands=re.compile(r'^([ \t]+[a-z][\w/-]*[ \t]+)(\{[^}\n]*\}|[vp]\d+(?:[ \t]*,[ \t]*[vp]\d+)*)',re.M)
   def aliases(match):
    registers=re.sub(r'\bv(\d+)\b',lambda reg: 'p'+str(int(reg[1])-n) if int(reg[1])>=n else reg[0],match[2])
    return match[1]+registers
   s=operands.sub(aliases,s)
   m=re.search(r'    \.locals (\d+)',s)
   count=len(args)+int(bool(returned))
   self.report[-1:] # report appended outside
   s=s[:m.start()]+f'    .locals {n+count}'+s[m.end():]
   start='\n'
   for i,(reg,typ) in enumerate(zip(args,params)):
    op='move-object/from16' if typ in ('O','V') else 'move/from16'
    start+=f'    {op} v{n+i}, {reg}\n'
   m=re.search(r'    \.locals \d+',s);s=s[:m.end()]+start+s[m.end():]
   def call(ret=None):
    x=''
    if returned:x=f'    {"move-object/from16" if returned is True else "move/from16"} v{n+len(args)}, {ret}\n'
    if count:x+=f'    invoke-static/range {{v{n} .. v{n+count-1}}}, {api}\n\n'
    else:x+=f'    invoke-static {{}}, {api}\n\n'
    return x
   if when=='before':
    at=s.index(start)+len(start);s=s[:at]+'\n'+call()+s[at:]
   else:
    if returned:
     pattern=r'^(    return-object (\w+))$' if returned is True else r'^(    return (\w+))$'
     def replacement(m):
      code=call(m[2])
      if returned is not True:code+=f'    move-result {m[2]}\n\n'
      return code+m[1]
     s=re.sub(pattern,replacement,s,flags=re.M)
    else:s=re.sub(r'^(    return(?:-void|-object|-wide)?(?: \w+)?)$',lambda m:call()+m[1],s,flags=re.M)
   return s
  self.method(suffix,sig,change)
 def wrapper(self,suffix,sig,code,locals=1):
  r=self.file(suffix);text=self.files[r][1]
  match=re.search(r'^\.method ([^\n]*) '+re.escape(sig)+r'\n.*?^\.end method$',text,re.M|re.S)
  if not match:raise ValueError((suffix,sig,'wrapper'))
  name=sig.split('(')[0];proto=sig[len(name):];oldname='nativeAdvancedOriginal'+name[0].upper()+name[1:]
  while re.search(r' '+re.escape(oldname)+r'\(',text):oldname+='Again'
  original=match[0].replace(' '+sig+'\n',' '+oldname+proto+'\n',1)
  self.files[r][1]=text[:match.start()]+original+text[match.end():]+'\n.method '+match[1]+' '+sig+'\n    .locals '+str(locals)+'\n'+code.replace('@ORIGINAL@',oldname+proto)+'\n.end method\n'
  self.report.append({'class':suffix,'method':sig,'wrapper':True})
 def override(self,suffix,sig,api):
  def change(s):
   m=re.search(r'    \.locals (\d+)',s)
   if int(m[1])<1:raise ValueError('scratch absent')
   add=f'\n\n    invoke-static/range {{p0 .. p0}}, {api}\n\n    move-result v0\n\n    if-ltz v0, :dressroom_advanced_native\n\n    return v0\n\n    :dressroom_advanced_native\n'
   return s[:m.end()]+add+s[m.end():]
  self.method(suffix,sig,change)
 def patch(self):
  out=''
  for r,(old,new) in sorted(self.files.items()):
   if old!=new:out+=''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+r,tofile='b/'+r))
  return out

def dressroom(p):
 p.callback('v8/y','l()V',W+'->toolbar(Ljava/lang/Object;)V',['p0'],'O','before')
 p.callback('op/m','b()Landroid/view/View;',W+'->create(Ljava/lang/Object;)V',['p0'],'O','before')
 p.callback('op/m','b()Landroid/view/View;',W+'->created(Ljava/lang/Object;Landroid/view/View;)V',['p0'],'O',returned=True)
 widget='Lcom/samsung/android/lockscreen/editor/item/domain/entity/item/widget/'
 profile='Lcom/samsung/android/lockscreen/editor/item/domain/entity/profile/ProfileData;'
 mode='Lcom/samsung/android/lockscreen/editor/item/domain/entity/preview/UIMode;'
 p.callback('sd/q',f'b({profile}{mode}){widget}WidgetData;',W+'->restoreModel(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V',['p0','p2'],'OO',returned=True)
 p.callback('sd/h',f'a({widget}WidgetGridData;Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetAlignType;Z)V',W+'->gridMetrics(Ljava/lang/Object;Ljava/lang/Object;)V',['p1','p2'],'OO')
 suffix='sd/o';sig=next(re.search(r'^\.method.* (a\([^\n]+)',line)[1] for line in Path(p.root/'smali_classes2/sd/o.smali').read_text().splitlines() if line.startswith('.method') and ' a(' in line)
 p.callback(suffix,sig,W+'->cellMetrics(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V',['p0','p1','p2','p8'],'OOOO')
 sig=next(re.search(r'^\.method.* (f\([^\n]+)',line)[1] for line in Path(p.root/'smali_classes2/sd/m.smali').read_text().splitlines() if line.startswith('.method') and ' f(' in line)
 p.callback('sd/m',sig,W+'->placement(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V',['p1','p2','p4','p6'],'OOOO')

 align='Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetAlignType;'
 def return_rotation(s):
  return re.sub(r'^(    return (\w+))$',lambda m:f'    invoke-static/range {{{m[2]} .. {m[2]}}}, '+W+'->rotateResult(Z)Z\n\n    move-result '+m[2]+'\n\n'+m[1],s,flags=re.M)
 p.method('cg/v','a('+align+')Z',return_rotation)
 def decision(api):
  def edit(s):
   m=re.search(r'    \.locals (\d+)',s)
   if not m or int(m[1])<1:raise ValueError('decision scratch')
   code='\n\n    invoke-static/range {p0 .. p1}, '+W+'->'+api+'\n\n    move-result v0\n\n    if-ltz v0, :dressroom_native_decision\n\n    return v0\n\n    :dressroom_native_decision\n'
   return s[:m.end()]+code+s[m.end():]
  return edit
 p.method('cg/v','a('+align+')Z',decision('rotateBefore(Ljava/lang/Object;Ljava/lang/Object;)I'))
 p.method('com/samsung/android/app/dressroom/presentation/preview/activity/EditMainActivity','dispatchTouchEvent(Landroid/view/MotionEvent;)Z',decision('touch(Landroid/app/Activity;Landroid/view/MotionEvent;)I'))
 def trimmed(s):
  m=re.search(r'    \.locals (\d+)',s)
  if not m or int(m[1])<1:raise ValueError('trimmed scratch')
  code='\n\n    invoke-static/range {p0 .. p0}, '+W+'->trimmed(Ljava/lang/Object;)Ljava/lang/Object;\n\n    move-result-object v0\n\n    if-eqz v0, :dressroom_native_alignment\n\n    check-cast v0, '+align+'\n\n    return-object v0\n\n    :dressroom_native_alignment\n'
  return s[:m.end()]+code+s[m.end():]
 p.method('rh/a','i()'+align,trimmed)
 def editable(s):
  m=re.search(r'    \.locals (\d+)',s);code='\n\n    invoke-static/range {p0 .. p1}, '+W+'->editable(Ljava/lang/Object;Z)Z\n\n    move-result p1\n'
  return s[:m.end()]+code+s[m.end():]
 p.method('com/samsung/android/lockscreen/editor/item/domain/entity/item/widget/WidgetData','setEditableAlign(Z)V',editable)

 # Expose two-row provider forms only inside Samsung's widget picker when its
 # current editor draft has two rows. Never spoof DeviceType globally.
 def tallPicker(s):
  old='    invoke-virtual {v3}, Lcom/samsung/android/lockscreen/editor/item/domain/entity/system/DeviceType;->isTablet()Z'
  if s.count(old)!=1:raise ValueError('Tall picker DeviceType callsite moved')
  return s.replace(old,'    invoke-static {v3, v2}, '+W+'->pickerTablet(Ljava/lang/Object;Ljava/lang/Object;)Z')
 p.method('sd/d','a(Lcom/samsung/android/lockscreen/editor/item/domain/entity/preview/UIMode;Lcom/samsung/android/lockscreen/editor/item/domain/entity/system/DeviceType;)Ljava/util/ArrayList;',tallPicker)

def aod(p):
 base='com/samsung/android/app/facewidget/complication/'
 for sig,cb in [('getColumn()I','columnOverride'),('getRow()I','rowOverride'),('isBiggerSize()Z','biggerOverride')]:p.override(base+'ComplicationWidgetSizeManager',sig,W+'->'+cb+'(Ljava/lang/Object;)I')
 sig='init(Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetDisplayType;)V'
 p.wrapper(base+'ComplicationWidgetSizeManager',sig,'''    invoke-static {p0}, '''+W+'''->metricScope(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    :native_metric_start
    invoke-virtual {p0, p1}, Lcom/samsung/android/app/facewidget/complication/ComplicationWidgetSizeManager;->@ORIGINAL@
    :native_metric_end
    const/4 v1, 0x1
    invoke-static {p0, v0, v1}, '''+W+'''->metricScopeEnd(Ljava/lang/Object;Ljava/lang/Object;Z)V
    return-void
    :native_metric_failure
    move-exception v2
    const/4 v1, 0x0
    invoke-static {p0, v0, v1}, '''+W+'''->metricScopeEnd(Ljava/lang/Object;Ljava/lang/Object;Z)V
    throw v2
    .catchall {:native_metric_start .. :native_metric_end} :native_metric_failure''',3)
 p.callback(base+'ComplicationGridUIController','setRootContainer(Landroid/view/ViewGroup;Ljava/lang/String;Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetDisplayType;)V',W+'->gridBound(Ljava/lang/Object;Landroid/view/View;)V',['p0','p1'],'OO')
 p.callback(base+'ComplicationGridUIController','onclickChangeWidgetAlign(Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetAlignType;)V',W+'->reflow(Ljava/lang/Object;)V',['p0'],'O')
 ctl=base+'ComplicationWidgetPluginController';text=(p.root/next(iter(p.root.glob('smali*/'+ctl+'.smali'))).relative_to(p.root)).read_text()
 for m in re.finditer(r'^\.method [^\n]* (\w+)(\([^\n]+)',text,re.M):
  if m[1] in ('initialize','setDarkAmount','setDozing','requestAODState','onFinishedWakingUp','refreshComplicationGridAndWidgets','updateWidgetLayout','onCompleteUpdate'):
   p.callback(ctl,m[1]+m[2],W+'->controllerChanged(Ljava/lang/Object;)V',['p0'],'O')
 p.callback(ctl,'release()V',W+'->controllerRelease(Ljava/lang/Object;)V',['p0'],'O','before')
 p.callback('aod/fa2',next(re.search(r'^\.method.* (<init>\([^\n]+)',line)[1] for line in (p.root/'smali/aod/fa2.smali').read_text().splitlines() if line.startswith('.method') and '<init>(' in line),E+'->artworkHolder(Ljava/lang/Object;)V',['p0'],'O')
 stack='com/samsung/android/app/facewidget/utils/NowBarCardStackView'
 for sig,api in [('q(Ljava/util/concurrent/CopyOnWriteArrayList;)V','stackAfterRefresh'),('onAttachedToWindow()V','stackLayout'),('setExpandedCardState(Z)V','stackState'),('setExpandVIStarted(Z)V','stackState'),('z()V','stackState'),('onConfigurationChanged(Landroid/content/res/Configuration;)V','stackConfiguration')]:p.callback(stack,sig,E+'->'+api+'(Landroid/view/ViewGroup;)V',['p0'],'O')
 p.callback(stack,'q(Ljava/util/concurrent/CopyOnWriteArrayList;)V',E+'->stackBeforeRefresh(Landroid/view/ViewGroup;Ljava/util/concurrent/CopyOnWriteArrayList;)V',['p0','p1'],'OO','before')
 p.callback(stack,'onDetachedFromWindow()V',E+'->stackDetached(Landroid/view/ViewGroup;)V',['p0'],'O','before')

 p.callback('aod/n82','invoke()Ljava/lang/Object;',E+'->artworkExpansion(Ljava/lang/Object;)V',['p0'],'O')
 card='com/samsung/android/app/facewidget/presentation/NowBarMediaCardView'
 p.callback(card,'D(Z)V',E+'->cardReveal(Ljava/lang/Object;Z)V',['p0','p1'],'OI','before')
 p.callback(card,'g0(Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;FFFFLaod/xn2;)V',E+'->artworkCollapsed(Ljava/lang/Object;Landroid/view/View;Landroid/view/View;Landroid/view/View;)V',['p0','p1','p2','p3'],'OOOO')
 p.callback(card,'v0(Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;Landroid/view/View;Landroid/view/ViewGroup;Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;Lkotlin/jvm/functions/Function0;)V',E+'->artworkCollapseEnd(Landroid/view/View;)V',['p1'],'O')
 p.callback(card,'x0(Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;Landroid/view/View;Laod/fz5;FLandroid/view/ViewGroup;Landroid/view/ViewGroup;Landroid/view/View;ILandroidx/cardview/widget/CardView;)V',E+'->artworkExpandEnd(Landroid/view/View;)V',['p1'],'O')
 for sig,api in [('setAnimationProgress(F)V','songProgress'),('setRandomOffset(F)V','songRandom')]:p.callback('com/samsung/android/uniform/nowbar/MediaMaskingFrameLayout',sig,E+'->'+api+'(Landroid/view/View;F)V',['p0','p1'],'OI')
 for sig,api in [('f(I)F','stackScale'),('g(I)F','stackOffset')]:p.callback(stack,sig,E+'->'+api+'(Landroid/view/ViewGroup;IF)F',['p0','p1'],'OI',returned='F')
 p.callback(stack,'onTouchEvent(Landroid/view/MotionEvent;)Z',E+'->stackTouchBefore(Landroid/view/ViewGroup;Landroid/view/MotionEvent;)V',['p0','p1'],'OO','before')
 p.callback(stack,'onTouchEvent(Landroid/view/MotionEvent;)Z',E+'->stackTouchAfter(Landroid/view/ViewGroup;)V',['p0'],'O')


 align='Lcom/samsung/android/clockpack/plugins/briefwidget/WidgetAlignType;'
 p.wrapper(base+'KeyguardComplicationViewModel','updateGridLayout('+align+'Z)V','''    invoke-static {p1}, '''+W+'''->arrangementBegin(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    :native_grid_start
    invoke-direct {p0, p1, p2}, Lcom/samsung/android/app/facewidget/complication/KeyguardComplicationViewModel;->@ORIGINAL@
    :native_grid_end
    invoke-static {v0}, '''+W+'''->arrangementEnd(Ljava/lang/Object;)V
    return-void
    :native_grid_failure
    move-exception v1
    invoke-static {v0}, '''+W+'''->arrangementEnd(Ljava/lang/Object;)V
    throw v1
    .catchall {:native_grid_start .. :native_grid_end} :native_grid_failure''',2)
 p.wrapper(base+'WidgetLayoutManager','refreshGridLayout('+align+')V','''    invoke-static {p0, p1}, '''+W+'''->layoutRefresh(Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :native_grid_custom
    invoke-virtual {p0, p1}, Lcom/samsung/android/app/facewidget/complication/WidgetLayoutManager;->@ORIGINAL@
    :native_grid_custom
    return-void''')
 p.wrapper(base+'ComplicationWidgetPluginController','updateClockGravity(ZI)V','''    invoke-static {p0}, '''+W+'''->controllerGravity(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :native_grid_gravity
    invoke-virtual {p0, p1, p2}, Lcom/samsung/android/app/facewidget/complication/ComplicationWidgetPluginController;->@ORIGINAL@
    :native_grid_gravity
    return-void''')
 factory=base+'ComplicationWidgetFactory';path=next(p.root.glob('smali*/'+factory+'.smali'))
 creation=re.findall(r'^\.method public final (createWidget\([^\n]+)',path.read_text(),re.M)
 if len(creation)!=1:raise ValueError('Widget creation ABI')
 p.callback(factory,creation[0],W+'->factoryCreated(Ljava/lang/Object;Ljava/lang/Object;)V',['p0'],'O',returned=True)
 provider='com/samsung/android/app/facewidget/utils/KeyguardClockProvider';text=next(p.root.glob('smali*/'+provider+'.smali')).read_text()
 for sig in re.findall(r'^\.method [^\n]* ((?:onClockTypeChanged|refreshClockFontDataInternal|updateWallpaperColorsItem)\([^\n]+)',text,re.M):p.callback(provider,sig,W+'->profileChanged()V',[],'')
 p.callback(stack,'e(Landroid/view/View;Z)I',E+'->stackDepth(Landroid/view/ViewGroup;Landroid/view/View;I)I',['p0','p1'],'OO',returned='I')
 p.wrapper(card,'y0()V','''    invoke-static {p0}, '''+E+'''->stackWave(Landroid/view/View;)Z
    move-result v0
    if-eqz v0, :native_rear_wave
    invoke-virtual {p0}, Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;->@ORIGINAL@
    :native_rear_wave
    return-void''')
 r=p.file(stack);text=p.files[r][1]
 if ' drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z' in text or ' onLayout(ZIIII)V' in text:raise ValueError('Stack inherited draw/layout ABI changed')
 p.files[r][1]+='\n'+'''.method protected drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z
    .locals 1
    invoke-static {p0, p2}, '''+E+'''->stackDrawSkip(Landroid/view/ViewGroup;Landroid/view/View;)Z
    move-result v0
    if-eqz v0, :native_stack_child
    const/4 v0, 0x1
    return v0
    :native_stack_child
    invoke-super/range {p0 .. p4}, Landroid/widget/FrameLayout;->drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z
    move-result v0
    return v0
.end method
.method protected onLayout(ZIIII)V
    .locals 0
    invoke-super/range {p0 .. p5}, Landroid/widget/FrameLayout;->onLayout(ZIIII)V
    invoke-static {p0}, '''+E+'''->stackLayout(Landroid/view/ViewGroup;)V
    return-void
.end method
'''
 # Native media alpha writers feed the scoped opacity state; animations are
 # synchronized at pre-draw by MediaBlurHost, without a framework interception.
 for suffix in [card,'com/samsung/android/uniform/nowbar/MediaMaskingFrameLayout']:
  r=p.file(suffix);text=p.files[r][1]
  text=re.sub(r'invoke-virtual (\{[^}\n]+\}), Landroid/view/View;->setAlpha\(F\)V',lambda m:'invoke-static '+m[1]+', '+E+'->setAlpha(Landroid/view/View;F)V',text)
  p.files[r][1]=text


 p.wrapper(stack,'dispatchTouchEvent(Landroid/view/MotionEvent;)Z','''    invoke-static {p0, p1}, '''+E+'''->stackDispatch(Landroid/view/ViewGroup;Landroid/view/MotionEvent;)I
    move-result v0
    if-ltz v0, :native_stack_dispatch
    return v0
    :native_stack_dispatch
    invoke-virtual {p0, p1}, Lcom/samsung/android/app/facewidget/utils/NowBarCardStackView;->@ORIGINAL@
    move-result v0
    return v0''')
 p.wrapper('aod/di1','D(Landroid/view/View;II)Z','''    invoke-static {p0, p1, p2}, '''+E+'''->pillBounds(Landroid/view/View;II)I
    move-result v0
    if-ltz v0, :native_pill_bounds
    return v0
    :native_pill_bounds
    invoke-static {p0, p1, p2}, Laod/di1;->@ORIGINAL@
    move-result v0
    return v0''')
 coordinator='aod/ag4';r=p.file(coordinator);text=p.files[r][1]
 anchor='    invoke-virtual {v2}, Landroid/animation/AnimatorSet;->start()V'
 if text.count(anchor)!=1:raise ValueError('Song animator ABI')
 p.files[r][1]=text.replace(anchor,anchor+'\n\n    invoke-static {v2}, '+E+'->songAnimator(Landroid/animation/Animator;)V')
 p.wrapper(coordinator,'d(Landroid/graphics/drawable/Drawable;)V','''    invoke-static {p0}, '''+E+'''->songBegin(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    :native_song_start
    invoke-virtual {p0, p1}, Laod/ag4;->@ORIGINAL@
    :native_song_end
    invoke-static {v0}, '''+E+'''->songEnd(Ljava/lang/Object;)V
    return-void
    :native_song_failure
    move-exception v1
    invoke-static {v0}, '''+E+'''->songEnd(Ljava/lang/Object;)V
    throw v1
    .catchall {:native_song_start .. :native_song_end} :native_song_failure''',2)
 # Replace only the artwork's FrameLayout with a native dispatch override.
 path=p.root/'res/layout/now_bar_music_page_holder.xml';old=path.read_text();tags=list(re.finditer(r'<(/?)FrameLayout\b[^>]*>',old));target=None;depth=0;closing=None
 for tag in tags:
  if target is None:
   if not tag[1] and 'android:id="@id/facewidget_album_art_vi_container_parent"' in tag[0]:target=tag;depth=1
  else:
   depth+=-1 if tag[1] else (0 if tag[0].endswith('/>') else 1)
   if depth==0:closing=tag;break
 if target is None or closing is None:raise ValueError('Artwork FrameLayout XML ABI')
 cls='dev.denver.dressroomadvanced.NativeArtworkFrameLayout'
 new=old[:closing.start()]+closing[0].replace('FrameLayout',cls)+old[closing.end():]
 new=new[:target.start()]+target[0].replace('FrameLayout',cls)+new[target.end():]
 p.files['res/layout/now_bar_music_page_holder.xml']=[old,new]

 # Native wrapper spans must follow a resized provider, not constructor-time 1x1.
 container=base+'ComplicationWidgetHostViewContainer'
 r=p.file(container);text=p.files[r][1]
 for field in ['targetCellWidth','targetCellHeight']:
  old='.field private final '+field+':I'
  if text.count(old)!=1:raise ValueError('Widget container span field changed: '+field)
  text=text.replace(old,'.field private '+field+':I')
 text+='\n.method public final setWidgetCellSpan(II)V\n    .locals 0\n    if-lez p1, :native_widget_span_end\n    if-lez p2, :native_widget_span_end\n    iput p1, p0, L'+container+';->targetCellWidth:I\n    iput p2, p0, L'+container+';->targetCellHeight:I\n    invoke-direct {p0}, L'+container+';->initConfigurationValues()V\n    :native_widget_span_end\n    return-void\n.end method\n'
 p.files[r][1]=text

def systemui(p):
 base='com/android/systemui/'
 p.callback(base+'facewidget/plugin/FaceWidgetNotificationControllerWrapper','initPlugin(Lcom/android/systemui/plugins/keyguardstatusview/PluginNotificationController;Landroid/content/Context;)V',E+'->nowBarHost(Ljava/lang/Object;Landroid/content/Context;)V',['p0','p2'],'OO')
 ui=base+'power/feature/charginganimation/ChargingAnimationUI'
 def keep(s):
  m=re.search(r'    \.locals (\d+)',s)
  if int(m[1])<1:raise ValueError('keep scratch absent')
  code='\n\n    invoke-static/range {p0 .. p0}, '+E+'->keepCharging(Ljava/lang/Object;)Z\n\n    move-result v0\n\n    if-eqz v0, :dressroom_advanced_remove\n\n    return-void\n\n    :dressroom_advanced_remove\n'
  return s[:m.end()]+code+s[m.end():]
 p.method(ui,'removeNowBarView()V',keep)
 for cls,sig,api in [(base+'power/view/ChargerNowBarView','onAttachedToWindow()V','chargingAttached'),(base+'power/view/ChargerNowBarView','switchAnimationResources(I)V','chargingResources'),(base+'shade/NotificationShadeWindowView','onAttachedToWindow()V','artworkRoot')]:
  arg='Landroid/view/ViewGroup;' if api=='artworkRoot' else 'Landroid/view/View;';p.callback(cls,sig,E+'->'+api+'('+arg+')V',['p0'],'O')

 for cls,sig in [(base+'power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$1','invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;'),(base+'power/feature/charginganimation/ChargingAnimationUI$forceDismissRunnable$1','run()V')]:
  def finishing(s):
   pat=r'^(    invoke-virtual \{[^\n]+\}, Lcom/android/systemui/power/feature/charginganimation/ChargingAnimationUI;->dismissOverlay\(\)V)$'
   updated,n=re.subn(pat,lambda m:'    invoke-static {}, '+E+'->chargingFinishBegin()V\n\n'+m[1]+'\n\n    invoke-static {}, '+E+'->chargingFinishEnd()V',s,flags=re.M)
   if n!=1:raise ValueError(('charging finish call',cls,n))
   return updated
  p.method(cls,sig,finishing)

def main():
 a=argparse.ArgumentParser();a.add_argument('kind',choices=['dressroom','aod','systemui']);a.add_argument('decoded',type=Path);a.add_argument('output',type=Path);args=a.parse_args()
 p=Patcher(args.decoded);globals()[args.kind](p)
 args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(p.patch());args.output.with_suffix('.json').write_text(json.dumps(p.report,indent=2)+'\n')
if __name__=='__main__':main()
