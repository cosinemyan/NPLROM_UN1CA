#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exact DressRoom native lifecycle callsites. Reads decode; emits patch only."""
import argparse,difflib,re
from pathlib import Path
BASE='Ldev/denver/dressroomadvanced/NativeEditor;'
p=argparse.ArgumentParser();p.add_argument('decoded',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
changes={}
def edit(suffix,fn):
 paths=list(a.decoded.glob('smali*/'+suffix+'.smali'))
 if len(paths)!=1:raise ValueError(suffix)
 path=paths[0];old,new=changes.get(path,(path.read_text(),path.read_text()));changes[path]=(old,fn(new))
def method(s,signature,fn):
 pat=re.compile(r'^\.method [^\n]* '+re.escape(signature)+r'\n.*?^\.end method',re.M|re.S);ms=list(pat.finditer(s))
 if len(ms)!=1:raise ValueError(signature)
 m=ms[0];return s[:m.start()]+fn(m[0])+s[m.end():]
def wrap(s,signature,code,locals=1):
 name=signature.split('(')[0];renamed='nativeAdvancedOriginal'+name[0].upper()+name[1:];original=[]
 def change(b):
  original.append(b.splitlines()[0]);return b.replace(' '+signature+'\n',' '+renamed+signature[len(name):]+'\n',1)
 s=method(s,signature,change)
 return s+'\n'+original[0]+'\n    .locals '+str(locals)+'\n'+code.replace('@ORIGINAL@',renamed+signature[len(name):])+'\n.end method\n'
def before(s,signature,code):
 def change(b):
  m=re.search(r'^    \.(?:locals|registers) \d+\n',b,re.M)
  return b[:m.end()]+'\n'+code+'\n'+b[m.end():]
 return method(s,signature,change)
edit('vo/x',lambda s:wrap(s,'a()Z',f'''    invoke-virtual {{p0}}, Lvo/x;->@ORIGINAL@
    move-result v0
    invoke-static {{v0, p0}}, {BASE}->opened(ZLjava/lang/Object;)V
    return v0'''))
edit('oc/a',lambda s:wrap(s,'e(Lcom/samsung/android/clockpack/plugins/clock/ClockView;)V',f'''    invoke-virtual {{p0, p1}}, Loc/a;->@ORIGINAL@
    invoke-static {{p0}}, {BASE}->bound(Ljava/lang/Object;)V
    return-void''',0))
activity='com/samsung/android/app/dressroom/presentation/preview/activity/EditMainActivity'
# session starts immediately after native super; close runs before native body can clobber p0.
edit(activity,lambda s:before(s,'onCreate(Landroid/os/Bundle;)V',f'    invoke-static/range {{p0 .. p0}}, {BASE}->session(Landroid/app/Activity;)V'))
edit(activity,lambda s:before(s,'onDestroy()V',f'    invoke-static/range {{p0 .. p0}}, {BASE}->close(Landroid/app/Activity;)V'))
edit(activity,lambda s:before(s,'dispatchTouchEvent(Landroid/view/MotionEvent;)Z',f'    invoke-static/range {{p0 .. p1}}, {BASE}->touch(Landroid/app/Activity;Landroid/view/MotionEvent;)V'))
# This shared Samsung LinearLayout also serves TabLayout. The helper checks exact
# root identity and Advanced visibility, leaving every unrelated instance alone.
def floatingTouch(s):
 if ' dispatchTouchEvent(Landroid/view/MotionEvent;)Z' in s:raise ValueError('Floating root ABI changed')
 return s+f'''\n.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 1
    invoke-static {{p0, p1}}, {BASE}->floatingTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z
    move-result v0
    if-eqz v0, :native_advanced_floating_dispatch
    const/4 v0, 0x1
    return v0
    :native_advanced_floating_dispatch
    invoke-super {{p0, p1}}, Landroid/widget/LinearLayout;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v0
    return v0
.end method
'''
edit('com/google/android/material/tabs/f',floatingTouch)

# Add after the existing EditMainActivity onDestroy mapping; before p8/a save mapping.
# Wrappers preserve p0/p1 across Samsung bodies that overwrite parameter registers.
edit(activity,lambda s:wrap(s,'onConfigurationChanged(Landroid/content/res/Configuration;)V',f'''    invoke-static {{p0, p1}}, {BASE}->configurationBefore(Landroid/app/Activity;Landroid/content/res/Configuration;)V
    invoke-virtual {{p0, p1}}, L{activity};->@ORIGINAL@
    invoke-static {{p0}}, {BASE}->configurationAfter(Landroid/app/Activity;)V
    return-void''',0))
edit('o6/e0',lambda s:wrap(s,'c1(Lcom/samsung/android/app/dressroom/data/profile/wallpaper/EditorData;)V',f'''    invoke-virtual {{p0, p1}}, Lo6/e0;->@ORIGINAL@
    invoke-static {{}}, {BASE}->sourceChanged()V
    return-void''',0))
wallpaperData='com/samsung/android/app/dressroom/data/profile/wallpaper/EditorData'
for signature in ['setOriginalUri(Landroid/net/Uri;)V','setUri(Landroid/net/Uri;)V','setWallpaperSubType(I)V','setWallpaperParentSubType(I)V']:
 edit(wallpaperData,lambda s,signature=signature:wrap(s,signature,f'''    invoke-virtual {{p0, p1}}, L{wallpaperData};->@ORIGINAL@
    invoke-static {{}}, {BASE}->sourceChanged()V
    return-void''',0))

edit('p8/a',lambda s:wrap(s,'i()V',f'''    invoke-static {{p0}}, {BASE}->beforeSave(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :native_advanced_save_return
    :native_advanced_save_start
    invoke-virtual {{p0}}, Lp8/a;->@ORIGINAL@
    :native_advanced_save_end
    invoke-static {{p0}}, {BASE}->returned(Ljava/lang/Object;)V
    :native_advanced_save_return
    return-void
    :native_advanced_save_failure
    move-exception v0
    invoke-static {{p0}}, {BASE}->returned(Ljava/lang/Object;)V
    throw v0
    .catchall {{:native_advanced_save_start .. :native_advanced_save_end}} :native_advanced_save_failure'''))
def completed(s):
 anchor='    const-string v0, "Save done. Finish the Activity."'
 if s.count(anchor)!=1:raise ValueError('save completion count')
 return s.replace(anchor,f'    invoke-static {{v1}}, {BASE}->completed(Ljava/lang/Object;)V\n\n'+anchor)
edit('j6/d',lambda s:method(s,'w()V',completed))
clock='com/samsung/android/lockscreen/editor/item/domain/entity/item/clock/'
stretch='L'+clock+'ClockStretchData;'
edit(clock+'ClockStretchData',lambda s:wrap(s,'setFontHeight(Ljava/util/List;)V',f'''    invoke-static {{p0, p1}}, {BASE}->heights(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;
    move-result-object p1
    invoke-virtual {{p0, p1}}, {stretch}->@ORIGINAL@
    return-void''',0))
edit(clock+'ClockStretchData',lambda s:wrap(s,'setFontHeightRects(Ljava/util/List;)V',f'''    invoke-static {{p0}}, {BASE}->locked(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :native_advanced_rect_locked
    invoke-virtual {{p0, p1}}, {stretch}->@ORIGINAL@
    :native_advanced_rect_locked
    return-void'''))
def copy(s):
 signatures=re.findall(r'^\.method public final (copy\([^\n]+)',s,re.M)
 if len(signatures)!=1:raise ValueError('stretch copy')
 return wrap(s,signatures[0],f'''    invoke-virtual/range {{p0 .. p9}}, {stretch}->@ORIGINAL@
    move-result-object v0
    invoke-static {{p0, v0}}, {BASE}->copied(Ljava/lang/Object;Ljava/lang/Object;)V
    return-object v0''')
edit(clock+'ClockStretchData',copy)
for cls,setter,field in [('ClockFontData','setClockFontType','clockFontType'),('ClockTypeData','setClockType','clockType')]:
 owner='L'+clock+cls+';'
 edit(clock+cls,lambda s,owner=owner,setter=setter,field=field:wrap(s,setter+'(I)V',f'''    iget v0, p0, {owner}->{field}:I
    invoke-static {{v0, p1}}, {BASE}->selection(II)V
    invoke-virtual {{p0, p1}}, {owner}->@ORIGINAL@
    return-void'''))
for sig,support in [('isSupportTextBlurClock()Z',1),('isBlurEffectEnabled()Z',0)]:
 owner='L'+clock+'ClockData;'
 edit(clock+'ClockData',lambda s,sig=sig,support=support:wrap(s,sig,f'''    const/4 v1, {support}
    invoke-static {{p0, v1}}, {BASE}->blurModel(Ljava/lang/Object;Z)I
    move-result v0
    if-ltz v0, :native_blur_model
    return v0
    :native_blur_model
    invoke-virtual {{p0}}, {owner}->@ORIGINAL@
    move-result v0
    return v0''',2))
edit('ah/g',lambda s:wrap(s,'m(Lcom/samsung/android/clockpack/plugins/clock/ClockView;Z)V',f'''    const/4 v0, 0x0
    invoke-static {{p1, p2, v0}}, {BASE}->blurHelper(Ljava/lang/Object;ZZ)Z
    move-result v0
    if-nez v0, :native_blur_enable_done
    invoke-virtual {{p0, p1, p2}}, Lah/g;->@ORIGINAL@
    :native_blur_enable_done
    return-void'''))
edit('ah/g',lambda s:wrap(s,'o(Lcom/samsung/android/clockpack/plugins/clock/ClockView;)V',f'''    const/4 v0, 0x0
    const/4 v1, 0x1
    invoke-static {{p1, v0, v1}}, {BASE}->blurHelper(Ljava/lang/Object;ZZ)Z
    move-result v0
    if-nez v0, :native_blur_text_done
    invoke-virtual {{p0, p1}}, Lah/g;->@ORIGINAL@
    :native_blur_text_done
    return-void''',2))
manifest=a.decoded/'AndroidManifest.xml';old=manifest.read_text();anchor='</application>'
new=old.replace(anchor,'    <activity android:name="dev.denver.dressroomadvanced.FontPickerActivity" android:exported="false" />\n'+anchor)
changes[manifest]=(old,new)
if a.output.exists():raise SystemExit('Refusing to overwrite existing patch')
a.output.write_text('AUTHOR BlueOutside, Rezoss\nSubject: [PATCH] Native Advanced editor lifecycle and manual Samsung font data\n\n'+''.join(''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+str(path.relative_to(a.decoded)),tofile='b/'+str(path.relative_to(a.decoded)))) for path,(old,new) in sorted(changes.items())))
