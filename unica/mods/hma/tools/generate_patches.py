# SPDX-License-Identifier: AGPL-3.0-or-later
from pathlib import Path
import difflib,subprocess,sys
repo=Path(__file__).resolve().parents[4]
tmp=Path(sys.argv[1]).resolve()
assert tmp.is_dir(), 'Pass the temporary build/decode directory'
def diff(path,old,new):
    return 'diff --git a/'+path+' b/'+path+'\n'+''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+path if old else '/dev/null',tofile='b/'+path if new else '/dev/null',n=3))
def patchfile(path,title,changes):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists():
        backup=tmp/'backups'/'generated'/str(__import__('time').time_ns())/path.name
        backup.parent.mkdir(parents=True,exist_ok=True)
        __import__('shutil').copy2(path,backup)
    path.write_text('From: UN1CA contributors\nSubject: [PATCH] '+title+'\n\n'+''.join(diff(*c) for c in changes))
def method(text,name):
    matches=[m for m in __import__('re').finditer(r'^\.method [^\n]+$',text,__import__('re').M) if name in m.group()]
    assert len(matches)==1,(name,len(matches))
    start=matches[0].start();end=text.index('.end method',start)+len('.end method')
    return start,end,text[start:end]
def change_method(text,name,fn):
    a,b,old=method(text,name);return text[:a]+fn(old)+text[b:]
fw=[]
for p in sorted((tmp/'policy-smali').rglob('*.smali')):
    fw.append(('smali_classes6/'+str(p.relative_to(tmp/'policy-smali')),'',p.read_text().replace('const-string ', 'const-string/jumbo ')))
p='smali/android/content/ContentProvider$Transport.smali';old=(tmp/'framework-nodebug'/p).read_text();new=old
for name,sig,body in [
('call','Landroid/content/AttributionSource;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;', '''    iget-object v1, p0, Landroid/content/ContentProvider$Transport;->this$0:Landroid/content/ContentProvider;

    invoke-static {v1, p3, p4, v0}, Lio/mesalabs/unica/HmaPolicy;->filterCall(Landroid/content/ContentProvider;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;
'''),
('query','Landroid/content/AttributionSource;Landroid/net/Uri;[Ljava/lang/String;Landroid/os/Bundle;Landroid/os/ICancellationSignal;)Landroid/database/Cursor;', '''    iget-object v1, p0, Landroid/content/ContentProvider$Transport;->this$0:Landroid/content/ContentProvider;

    invoke-static {v1, p2, v0}, Lio/mesalabs/unica/HmaPolicy;->filterQuery(Landroid/content/ContentProvider;Landroid/net/Uri;Landroid/database/Cursor;)Landroid/database/Cursor;
''')]:
    header='.method public blacklist '+name+'('+sig
    assert new.count(header)==1
    new=new.replace(header,header.replace(name+'(',name+'HmaOriginal('),1)
    new+='\n\n'+header+'\n    .locals 2\n\n    invoke-virtual/range {p0 .. p5}, Landroid/content/ContentProvider$Transport;->'+name+'HmaOriginal('+sig+'\n\n    move-result-object v0\n\n'+body+'\n    move-result-object v0\n\n    return-object v0\n.end method\n'
fw.append((p,old,new))
p='smali_classes3/android/os/ZygoteProcess.smali';old=(tmp/'framework-nodebug'/p).read_text()
hook='''    move/from16 v0, p3

    move-object/from16 v1, p5

    invoke-static {v0, v1}, Lio/mesalabs/unica/HmaPolicy;->restrictGids(I[I)[I

    move-result-object v0

    move-object/16 p5, v0

'''
new=change_method(old,' start(',lambda m:m.replace('    invoke-direct/range {p0 .. p0}, Landroid/os/ZygoteProcess;->fetchUsapPoolEnabledPropWithMinInterval()Z',hook+'    invoke-direct/range {p0 .. p0}, Landroid/os/ZygoteProcess;->fetchUsapPoolEnabledPropWithMinInterval()Z',1))
fw.append((p,old,new))
patchfile(repo/'unica/mods/hma/smali/system/framework/framework.jar/0002-Add-HMA-OSS-privacy-policy.patch','Add HMA-OSS-inspired per-user privacy policy and provider hooks',fw)

services=[]
# Read baseline after the existing HMA patch, without editing decoded source smali.
baseline=tmp/'services-hma-nodebug-complete'
if not baseline.exists():
    subprocess.run(['cp','-a',str(tmp/'services-nodebug'),str(baseline)],check=True)
    subprocess.run(['patch','--fuzz=0','-p1','-d',str(baseline),'-i',str(repo/'unica/mods/hma/smali/system/framework/services.jar/0001-Introduce-HideAppListUtils.patch')],check=True,stdout=subprocess.DEVNULL)
p='smali_classes2/com/android/server/pm/ComputerEngine.smali';old=(baseline/p).read_text()
newbody='''.method public final shouldFilterApplicationCustom(Lcom/android/server/pm/pkg/PackageStateInternal;II)Z
    .locals 2

    const/4 v0, 0x0

    if-eqz p1, :hma_done

    invoke-interface {p1}, Lcom/android/server/pm/pkg/PackageState;->getPackageName()Ljava/lang/String;

    move-result-object v0

    iget-object v1, p0, Lcom/android/server/pm/ComputerEngine;->mContext:Landroid/content/Context;

    invoke-static {v1, v0, p2}, Lio/mesalabs/unica/HmaPolicy;->shouldHide(Landroid/content/Context;Ljava/lang/String;I)Z

    move-result v0

    :hma_done
    return v0
.end method'''
new=change_method(old,' shouldFilterApplicationCustom(',lambda m:newbody)
for name,args,ret,body in [
('getInstallSourceInfo','Ljava/lang/String;I','Landroid/content/pm/InstallSourceInfo;','''    invoke-virtual {p0, p1, p2}, Lcom/android/server/pm/ComputerEngine;->getInstallSourceInfoHmaOriginal(Ljava/lang/String;I)Landroid/content/pm/InstallSourceInfo;

    move-result-object v0

    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v1

    invoke-static {p1, v1, v0}, Lio/mesalabs/unica/HmaPolicy;->filterInstallSourceInfo(Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/content/pm/InstallSourceInfo;
'''),
('getInstallerPackageName','ILjava/lang/String;','Ljava/lang/String;','''    invoke-virtual {p0, p1, p2}, Lcom/android/server/pm/ComputerEngine;->getInstallerPackageNameHmaOriginal(ILjava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v1

    invoke-static {p2, v1, v0}, Lio/mesalabs/unica/HmaPolicy;->filterInstallerPackageName(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;

    move-result-object v0
''')]:
    header='.method public final '+name+'('+args+')'+ret
    assert new.count(header)==1
    new=new.replace(header,header.replace(name+'(',name+'HmaOriginal('),1)
    new+='\n\n'+header+'\n    .locals 2\n\n'+body+'\n    return-object v0\n.end method\n'
services.append((p,old,new))
p='smali_classes2/com/android/server/wm/ActivityStarter.smali';old=(baseline/p).read_text()
hook='''    :goto_2
    if-nez v1, :hma_activity_checked

    move-object/from16 v2, v18

    invoke-static {v10, v3, v2}, Lio/mesalabs/unica/HmaPolicy;->blockActivity(ILandroid/content/Intent;Landroid/content/pm/ActivityInfo;)Z

    move-result v2

    if-eqz v2, :hma_activity_checked

    const/16 v1, -0x5c

    :hma_activity_checked
    const-string v2, ""
'''
new=change_method(old,' executeRequest(',lambda m:m.replace('    :goto_2\n    const-string v2, ""\n',hook,1))
services.append((p,old,new))
p='smali/com/android/server/am/ActivityManagerService.smali';old=(baseline/p).read_text()
new=change_method(old,' finishBooting()',lambda m:m.replace('    move-object/from16 v1, p0\n','''    move-object/from16 v1, p0

    iget-object v0, v1, Lcom/android/server/am/ActivityManagerService;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lio/mesalabs/unica/HmaPolicy;->init(Landroid/content/Context;)V
''',1))
services.append((p,old,new))
for p,wrappers in [
('smali/com/android/server/accessibility/AccessibilityManagerService.smali',[
 ('getEnabledAccessibilityServiceList','II','Ljava/util/List;','filterAccessibility','Ljava/util/List;','binder'),
 ('getInstalledAccessibilityServiceList','I','Landroid/content/pm/ParceledListSlice;','filterAccessibilitySlice','Ljava/lang/Object;','binder')]),
('smali/com/android/server/inputmethod/InputMethodManagerService.smali',[
 ('getEnabledInputMethodListInternal','II','Ljava/util/List;','filterInputMethods','Ljava/util/List;','p2'),
 ('getInputMethodListInternal','III','Ljava/util/List;','filterInputMethods','Ljava/util/List;','p3'),
 ('getCurrentInputMethodInfoAsUser','I','Landroid/view/inputmethod/InputMethodInfo;','filterCurrentInputMethod','Landroid/view/inputmethod/InputMethodInfo;','binder'),
 ('getCurrentInputMethodSubtype','I','Landroid/view/inputmethod/InputMethodSubtype;','filterInputSubtype','Landroid/view/inputmethod/InputMethodSubtype;','binder'),
 ('getLastInputMethodSubtype','I','Landroid/view/inputmethod/InputMethodSubtype;','filterInputSubtype','Landroid/view/inputmethod/InputMethodSubtype;','binder'),
 ('getEnabledInputMethodSubtypeList','Ljava/lang/String;ZI','Lcom/android/internal/inputmethod/InputMethodSubtypeSafeList;','filterInputSubtypeList','Ljava/lang/Object;','binder')])]:
    old=(baseline/p).read_text();new=old
    cls='L'+p.split('/',1)[1][:-6]+';'
    for name,args,ret,fn,filterret,uid in wrappers:
        header='.method public final '+name+'('+args+')'+ret
        assert new.count(header)==1
        new=new.replace(header,header.replace(name+'(',name+'HmaOriginal('),1)
        begin='    invoke-static {}, Landroid/os/Binder;->getCallingUid()I\n\n    move-result v0\n' if uid=='binder' else '    move v0, '+uid+'\n'
        argc=len(__import__('re').findall(r'L[^;]+;|[IZ]',args))
        new+='\n\n'+header+'\n    .locals 2\n\n'+begin+'\n    invoke-virtual/range {p0 .. p'+str(argc)+'}, '+cls+'->'+name+'HmaOriginal('+args+')'+ret+'\n\n    move-result-object v1\n\n    invoke-static {v0, v1}, Lio/mesalabs/unica/HmaPolicy;->'+fn+'(I'+filterret+')'+filterret+'\n\n    move-result-object v1\n'
        if ret!=filterret:new+='\n    check-cast v1, '+ret+'\n'
        new+='\n    return-object v1\n.end method\n'
    if 'accessibility/AccessibilityManagerService' in p:
        marker="    move-result v3\n\n    invoke-virtual {p0, v3}, Lcom/android/server/accessibility/AccessibilityManagerService;->getUserStateLocked(I)Lcom/android/server/accessibility/AccessibilityUserState;"
        hook="""    move-result v3

    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v4

    const-string/jumbo v5, "accessibility"

    invoke-static {v4, v5}, Lio/mesalabs/unica/HmaPolicy;->hideServiceList(ILjava/lang/String;)Z

    move-result v4

    if-eqz v4, :hma_register_client

    monitor-exit v2

    const-wide/16 v4, 0x0

    return-wide v4

    :hma_register_client
    invoke-virtual {p0, v3}, Lcom/android/server/accessibility/AccessibilityManagerService;->getUserStateLocked(I)Lcom/android/server/accessibility/AccessibilityUserState;"""
        new=change_method(new,' addClient(',lambda m:m.replace(marker,hook,1))
    services.append((p,old,new))
patchfile(repo/'unica/mods/hma/smali/system/framework/services.jar/0002-Integrate-HMA-OSS-protections.patch','Integrate HMA policy with package visibility, launches and service lists',services)

ui=[]
for p in sorted((tmp/'ui-smali').rglob('*.smali')):
    rel='smali_classes4/'+str(p.relative_to(tmp/'ui-smali'))
    original=repo/'unica/mods/settings/SecSettings.apk'/rel
    ui.append((rel,original.read_text() if original.exists() else '',p.read_text().replace('const-string ', 'const-string/jumbo ')))
for oldfile in sorted((repo/'unica/mods/settings/SecSettings.apk/smali_classes4/io/mesalabs/unica/settings/spoof').glob('HideDeveloperStatusFragment$$*.smali')):
    ui.append(('smali_classes4/io/mesalabs/unica/settings/spoof/'+oldfile.name,oldfile.read_text(),''))
patchfile(repo/'unica/mods/settings/smali/system/priv-app/SecSettings/SecSettings.apk/0001-Add-HMA-privacy-submenu.patch','Replace developer-status picker with the unified HMA privacy submenu',ui)
