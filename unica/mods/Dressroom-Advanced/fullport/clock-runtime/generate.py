#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate exact native AOD runtime callbacks; never changes decoded input."""
import argparse,difflib,re
from pathlib import Path
B='Ldev/denver/dressroomadvanced/NativeClockRuntime;'
D='Ldev/denver/dressroomadvanced/RuntimeClockData;'
p=argparse.ArgumentParser();p.add_argument('decoded',type=Path);p.add_argument('output',type=Path);a=p.parse_args();changes={}
def method(s,sig,fn):
 matches=list(re.finditer(r'^\.method [^\n]* '+re.escape(sig)+r'\n.*?^\.end method',s,re.M|re.S))
 if len(matches)!=1:raise ValueError(sig)
 m=matches[0];return s[:m.start()]+fn(m[0])+s[m.end():]
def edit(suffix,fn):
 paths=list(a.decoded.glob('smali*/'+suffix+'.smali'))
 if len(paths)!=1:raise ValueError(suffix)
 path=paths[0];old,new=changes.get(path,(path.read_text(),path.read_text()));changes[path]=(old,fn(new))
def once(s,old,new):
 if s.count(old)!=1:raise ValueError(old)
 return s.replace(old,new,1)
def entry(body,code):
 m=re.search(r'^    \.locals \d+\n',body,re.M)
 if not m:raise ValueError('locals')
 return body[:m.end()]+'\n'+code+'\n'+body[m.end():]
def wrap(s,sig,code,n=1):
 name=sig.split('(')[0];renamed='nativeAdvancedOriginal'+name[0].upper()+name[1:];heads=[]
 def rename(body):
  heads.append(body.splitlines()[0]);return body.replace(' '+sig+'\n',' '+renamed+sig[len(name):]+'\n',1)
 s=method(s,sig,rename)
 return s+'\n'+heads[0]+'\n    .locals '+str(n)+'\n'+code.replace('@ORIGINAL@',renamed+sig[len(name):])+'\n.end method\n'
# This is the exact capability API used by DressRoom ClockManager; do not
# substitute a guessed font-ID range or a height-list-only eligibility check.
providers=list(a.decoded.glob('smali*/com/samsung/android/uniform/plugins/UfClockProvider.smali'))
if len(providers)!=1:raise ValueError('Samsung clock provider identity')
capability=re.search(r'^\.method public final isStretchFont\(I\)Z\n(.*?)^\.end method',providers[0].read_text(),re.M|re.S)
if capability is None or capability[1].count('invoke-static {p1}, Laod/rl2;->w(I)Z')!=1:raise ValueError('Samsung native stretch capability ABI changed')
clock='com/samsung/android/uniform/widget/clock/AbsClockView';C='L'+clock+';';font='Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;'
edit(clock,lambda s:wrap(s,'C(Landroid/graphics/Typeface;[Landroid/widget/TextView;)V',f'''    invoke-static {{p0, p1, p2}}, {B}->fixed(Landroid/view/View;Landroid/graphics/Typeface;[Landroid/widget/TextView;)Landroid/graphics/Typeface;
    move-result-object p1
    invoke-virtual {{p0, p1, p2}}, {C}->@ORIGINAL@
    invoke-static {{p2, p1}}, {B}->fixedDone([Landroid/widget/TextView;Landroid/graphics/Typeface;)V
    return-void''',0))
edit(clock,lambda s:wrap(s,'B(ILjava/lang/String;[Landroid/widget/TextView;)V',f'''    invoke-static {{p0, p3}}, {B}->variable(Landroid/view/View;[Landroid/widget/TextView;)Z
    move-result v0
    if-nez v0, :native_variable_done
    invoke-virtual {{p0, p1, p2, p3}}, {C}->@ORIGINAL@
    :native_variable_done
    return-void'''))
# Drawing may use a plain font request while saved Samsung identity stays intact.
for path in sorted(a.decoded.glob('smali*/com/samsung/android/uniform/widget/clock/*.smali')):
 text=path.read_text();suffix=path.relative_to(a.decoded).as_posix().split('/',1)[1][:-6]
 for flags,name in re.findall(r'^\.method ([^\n]*) (\w+)\('+re.escape(font)+r'\)V$',text,re.M):
  if 'static' in flags or 'abstract' in flags or name in ('setFontType','startSpringAnimation'):continue
  sig=name+'('+font+')V'
  edit(suffix,lambda s,sig=sig:method(s,sig,lambda b:entry(b,f'''    invoke-static/range {{p0 .. p1}}, {B}->render(Landroid/view/View;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object p1
    check-cast p1, {font}''')))
# Adjust the post-normalization result only for the exact saved clock/font/path.
def norm(s):
 anchor='    invoke-static {v4, v3, v5}, Laod/qn1;->X(ILcom/samsung/android/clockpack/plugins/clock/ClockFontData;Ljava/util/List;)Ljava/util/List;\n\n    move-result-object v4'
 return once(s,anchor,anchor+f'\n\n    invoke-static {{v0, v3, v4}}, {B}->normalized(Landroid/view/View;Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;\n\n    move-result-object v4')
edit(clock,lambda s:method(s,'setFontType('+font+')V',norm))
edit(clock,lambda s:wrap(s,'setFontType('+font+')V',f'''    invoke-static {{p0, p1}}, {B}->font(Landroid/view/View;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object p1
    check-cast p1, {font}
    invoke-virtual {{p0, p1}}, {C}->@ORIGINAL@
    invoke-static {{p0}}, {B}->changed(Landroid/view/View;)V
    return-void''',0))
edit(clock,lambda s:wrap(s,'isStretchable(Ljava/util/List;)Z',f'''    invoke-static {{p0, p1}}, {B}->stretchable(Landroid/view/View;Ljava/util/List;)I
    move-result v0
    if-ltz v0, :native_clock_validate
    return v0
    :native_clock_validate
    invoke-virtual {{p0, p1}}, {C}->@ORIGINAL@
    move-result v0
    return v0'''))
for sig,cb,regs in [('onAttachedToWindow()V','attached','p0'),('setDozing(Z)V','changed','p0, p1'),('setShadowStyle(Lcom/samsung/android/clockpack/plugins/clock/ShadowStyle;)V','changed','p0, p1'),('setShowDateInfoFirst(Z)V','date','p0, p1'),('setShowWeatherInformation(Z)V','date','p0, p1'),('updateWeatherInfo(Lcom/samsung/android/clockpack/plugins/clock/WeatherInfo;)V','date','p0, p1')]:
 edit(clock,lambda s,sig=sig,cb=cb,regs=regs:wrap(s,sig,f'''    invoke-virtual {{{regs}}}, {C}->@ORIGINAL@
    invoke-static {{p0}}, {B}->{cb}(Landroid/view/View;)V
    return-void''',0))
# Date visibility is overridden on concrete clock styles, not AbsClockView.
# Match every exact declaration so later Samsung visibility updates cannot undo Hide date.
for path in sorted(a.decoded.glob('smali*/com/samsung/android/uniform/widget/clock/*.smali')):
 text=path.read_text()
 if not re.search(r'^\.method public setDateVisibility\(I\)V$',text,re.M):continue
 suffix=path.relative_to(a.decoded).as_posix().split('/',1)[1][:-6]
 owner='L'+suffix+';'
 edit(suffix,lambda s,owner=owner:wrap(s,'setDateVisibility(I)V',f'''    invoke-virtual {{p0, p1}}, {owner}->@ORIGINAL@
    invoke-static {{p0}}, {B}->date(Landroid/view/View;)V
    return-void''',0))
edit(clock,lambda s:wrap(s,'setMediaExpanded(Z)V',f'''    invoke-static {{p0, p1}}, {B}->mediaBefore(Landroid/view/View;Z)V
    invoke-virtual {{p0, p1}}, {C}->@ORIGINAL@
    invoke-static {{p0, p1}}, {B}->mediaAfter(Landroid/view/View;Z)V
    return-void''',0))
edit(clock,lambda s:wrap(s,'K0(FZ)V',f'''    invoke-static {{p0}}, {B}->transitionBegin(Landroid/view/View;)Ljava/lang/Object;
    move-result-object v0
    :native_transition_start
    invoke-virtual {{p0, p1, p2}}, {C}->@ORIGINAL@
    :native_transition_end
    invoke-static {{p0, v0}}, {B}->transitionEnd(Landroid/view/View;Ljava/lang/Object;)V
    return-void
    :native_transition_failure
    move-exception v1
    invoke-static {{p0, v0}}, {B}->transitionEnd(Landroid/view/View;Ljava/lang/Object;)V
    throw v1
    .catchall {{:native_transition_start .. :native_transition_end}} :native_transition_failure''',2))
provider='com/samsung/android/app/facewidget/utils/KeyguardClockProvider';P='L'+provider+';'
for sig,api,cast,ret in [('getClockFontData()'+font,'source(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;',font,'object'),('getClockFontHeights()Ljava/util/List;','sourceHeights(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;','Ljava/util/List;','object')]:
 edit(provider,lambda s,sig=sig,api=api,cast=cast:wrap(s,sig,f'''    invoke-virtual {{p0}}, {P}->@ORIGINAL@
    move-result-object v0
    invoke-static {{p0, v0}}, {D}->{api}
    move-result-object v0
    check-cast v0, {cast}
    return-object v0'''))
edit(provider,lambda s:method(s,'updateWallpaperColorsItem(Ljava/lang/Object;)V',lambda b:entry(b,f'    invoke-static {{}}, {D}->invalidate()V')))
page='com/samsung/android/uniform/widget/facewidget/AODFaceWidgetClockPage';PAGE='L'+page+';'
edit(page,lambda s:wrap(s,'getAODClockFontDataInternal()'+font,f'''    invoke-direct {{p0}}, {PAGE}->@ORIGINAL@
    move-result-object v0
    invoke-virtual {{p0}}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v1
    invoke-static {{v1, v0}}, {B}->lockFont(Landroid/content/Context;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, {font}
    return-object v0''',2))
# Blur draw has its own try/finally wrapper so nested submissions restore radius.
strategy='aod/tx0';S='Laod/tx0;';sig='b(Landroid/widget/ImageView;Lcom/samsung/android/clockpack/plugins/clock/WeatherInfo;Laod/xb0;)Laod/pv2;'
edit(strategy,lambda s:wrap(s,sig,f'''    invoke-static {{p0}}, {B}->blurBegin(Ljava/lang/Object;)V
    :native_blur_start
    invoke-virtual {{p0, p1, p2, p3}}, {S}->@ORIGINAL@
    move-result-object v0
    :native_blur_end
    invoke-static {{}}, {B}->blurEnd()V
    return-object v0
    :native_blur_failure
    move-exception v0
    invoke-static {{}}, {B}->blurEnd()V
    throw v0
    .catchall {{:native_blur_start .. :native_blur_end}} :native_blur_failure'''))
edit(strategy,lambda s:once(s,'    invoke-virtual {v7, v0}, Landroid/view/SemBlurInfo$Builder;->setRadius(I)Landroid/view/SemBlurInfo$Builder;',f'    invoke-static {{v0}}, {B}->radius(I)I\n\n    move-result v0\n\n    invoke-virtual {{v7, v0}}, Landroid/view/SemBlurInfo$Builder;->setRadius(I)Landroid/view/SemBlurInfo$Builder;'))
edit('aod/cq0',lambda s:method(s,'d(Ljava/util/ArrayList;IF)V',lambda b:entry(b,f'    invoke-static {{p0, p3}}, {B}->visibility(Ljava/lang/Object;F)F\n\n    move-result p3')))
# Preserve custom shadow while native AOD view effects change. No framework hooks.
for path in sorted(a.decoded.glob('smali*/**/*.smali')):
 if '/aod/' not in str(path) and '/uniform/widget/clock/' not in str(path):continue
 old,new=changes.get(path,(path.read_text(),path.read_text()))
 pattern=r'invoke-virtual (\{[^}\n]+\}), Landroid/view/View;->setRenderEffect\(Landroid/graphics/RenderEffect;\)V'
 new,n=re.subn(pattern,lambda m:'invoke-static '+m[1]+', '+B+'->effect(Landroid/view/View;Landroid/graphics/RenderEffect;)V',new)
 if n:changes[path]=(old,new)
edit(clock,lambda s:wrap(s,'y(Z)V',f'''    invoke-static {{p0}}, {B}->blurForced(Landroid/view/View;)Z
    move-result v0
    if-nez v0, :native_blur_supported
    invoke-virtual {{p0, p1}}, {C}->@ORIGINAL@
    :native_blur_supported
    return-void'''))
edit('aod/tx0',lambda s:wrap(s,'h(ILandroid/widget/ImageView;)V',f'''    invoke-static {{p0}}, {B}->blurStable(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :native_blur_stable
    invoke-virtual {{p0, p1, p2}}, Laod/tx0;->@ORIGINAL@
    :native_blur_stable
    return-void'''))
edit('aod/cq0',lambda s:wrap(s,'h()Laod/l49;',f'''    invoke-static {{p0}}, {B}->blurTone(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :native_blur_tone
    check-cast v0, Laod/l49;
    return-object v0
    :native_blur_tone
    invoke-virtual {{p0}}, Laod/cq0;->@ORIGINAL@
    move-result-object v0
    return-object v0'''))
edit('aod/cq0',lambda s:wrap(s,'c(IIZ)V',f'''    invoke-static {{p0, p1, p2, p3}}, {B}->blurDefer(Ljava/lang/Object;IIZ)Z
    move-result v0
    if-nez v0, :native_blur_deferred
    invoke-virtual {{p0, p1, p2, p3}}, Laod/cq0;->@ORIGINAL@
    :native_blur_deferred
    return-void'''))
def sdk(body):
 body=once(body,'    .locals 33','    .locals 39')
 anchor='    const/16 v10, 0x2bc\n\n    if-ne v2, v10, :cond_f'
 return once(body,anchor,'    iget-object v10, v0, Laod/ys2;->o:Laod/ue3;\n\n    iget-object v10, v10, Laod/ue3;->a:Ljava/lang/Object;\n\n    check-cast v10, Landroid/content/Context;\n\n    new-instance v33, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;\n\n    move/from16 v34, v2\n\n    move-object/from16 v35, v3\n\n    move-object/from16 v36, v4\n\n    move-object/from16 v37, v6\n\n    move-object/from16 v38, v8\n\n    invoke-direct/range {v33 .. v38}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;-><init>(ILjava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/util/List;)V\n\n    move-object/from16 v11, v33\n\n    invoke-static/range {v10 .. v11}, Ldev/denver/dressroomadvanced/NativeClockRuntime;->lockFont(Landroid/content/Context;Ljava/lang/Object;)Ljava/lang/Object;\n\n    move-result-object v11\n\n    check-cast v11, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;\n\n    invoke-virtual {v11}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;->getFontType()I\n\n    move-result v2\n\n    invoke-virtual {v11}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;->getFontPath()Ljava/lang/String;\n\n    move-result-object v3\n\n    invoke-virtual {v11}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;->getFontWeight()Ljava/lang/Integer;\n\n    move-result-object v4\n\n    invoke-virtual {v11}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;->getFontRoundedCorners()Ljava/lang/Integer;\n\n    move-result-object v6\n\n    invoke-virtual {v11}, Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;->getFontHeights()Ljava/util/List;\n\n    move-result-object v8\n\n    invoke-static {v10, v11, v9}, Ldev/denver/dressroomadvanced/NativeClockRuntime;->sdkStretch(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Boolean;)Ljava/lang/Boolean;\n\n    move-result-object v9\n\n'+anchor)
edit('aod/ys2',lambda s:method(s,'b(Lcom/samsung/android/sdk/aodservice/entities/signature/ItemKey;Lcom/samsung/android/sdk/aodservice/entities/attrs/Display;Ljava/lang/String;)Lcom/samsung/android/sdk/aodservice/entities/item/LockScreenItem;',sdk))
if a.output.exists():raise SystemExit('Existing patch retained')
a.output.write_text('AUTHOR BlueOutside, Rezoss\nSubject: [PATCH] Native Advanced runtime clock, AOD transitions and effects\n\n'+''.join(''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+str(path.relative_to(a.decoded)),tofile='b/'+str(path.relative_to(a.decoded))))for path,(old,new)in sorted(changes.items())))
