#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate strict patch without mutating any extracted APK smali."""
from pathlib import Path
import difflib,re,sys
root=Path(sys.argv[1]); output=Path(sys.argv[2])
changes={}
def change(name,fn):
 old=(root/name).read_text(); new=fn(old)
 assert old!=new,name
 changes[name]=(old,new)
def once(text,old,new):
 assert text.count(old)==1,(old,text.count(old)); return text.replace(old,new)
def replace_method(text,name,body):
 pattern=r'(?m)^\.method '+re.escape(name)+r'\n.*?^\.end method'
 text,count=re.subn(pattern,lambda m:'.method '+name+'\n'+body+'\n.end method',text,flags=re.S|re.M)
 assert count==1,(name,count); return text
sig='g(Landroid/content/Context;Ldi/c;Lci/a;Lcom/samsung/android/libaihub/aicore/prompt/PromptType;Lt20/c;)Ljava/lang/Object;'
new='memoryCloud'+sig[1:]
def router(s):
 s=once(s,'.method public final '+sig,'.method public final '+new)
 return s+'\n.method public final '+sig+'''\n    .locals 0
    invoke-static/range {p0 .. p5}, Lio/mesalabs/rezoss/memory/MemoryBackend;->request(Lli/w;Landroid/content/Context;Ldi/c;Lci/a;Lcom/samsung/android/libaihub/aicore/prompt/PromptType;Lt20/c;)Ljava/lang/Object;
    move-result-object p0
    return-object p0
.end method
'''
change('smali_classes2/li/w.smali',router)
change('smali_classes2/li/p.smali',lambda s:once(s,'Lli/w;->'+sig,'Lli/w;->'+new))
vertex='smali_classes2/com/samsung/android/libaihub/geminihub/model/gemini/delegate/VertexAIDelegate$getRequestResult$2$deferred$1$1.smali'
# The original delegate converts Result.failure to null, erasing the HTTP/auth
# cause. Preserve only classified retryable failures; refusals keep original flow.
vertex_log='    invoke-static {p0}, Lcom/samsung/android/libaihub/geminihub/model/gemini/delegate/VertexAIDelegate;->access$getLog$p(Lcom/samsung/android/libaihub/geminihub/model/gemini/delegate/VertexAIDelegate;)Lcom/samsung/android/libaihub/aicore/util/log/Logger;'
change(vertex,lambda s:once(s,vertex_log,'    invoke-static {v0}, Lio/mesalabs/rezoss/memory/MemoryFailurePolicy;->throwIfEligible(Ljava/lang/Throwable;)V\n\n'+vertex_log))
change('smali_classes2/com/samsung/android/libaihub/aicore/util/device/AITestUtils.smali',lambda s:replace_method(s,'public static final getAnalysisModelValue(Landroid/content/Context;)Ljava/lang/String;','''    .locals 0
    invoke-static {p0}, Lio/mesalabs/rezoss/memory/MemoryBackend;->model(Landroid/content/Context;)Ljava/lang/String;
    move-result-object p0
    return-object p0'''))
activity='smali_classes2/com/samsung/android/app/memory/analysis/develop/menu/ModelConfigurationActivity.smali'
change(activity,lambda s:once(s,'    invoke-virtual {p0, p1}, Lh/m;->setContentView(I)V','    invoke-virtual {p0, p1}, Lh/m;->setContentView(I)V\n\n    invoke-static {p0}, Lio/mesalabs/rezoss/memory/MemoryBackend;->addModel(Landroid/app/Activity;)V'))
def enum(s):
 s=once(s,'# direct methods','.field public static final enum UN1CA_AI_BACKEND:Lai/a0;\n\n# direct methods')
 insert='''    # Append a real enum and stable, programmatically inflated view ID.
    new-instance v0, Lai/a0;
    const/16 v1, 0x13
    const v2, 0x00ee0001
    const-string v3, "UN1CA_AI_BACKEND"
    const/4 v4, 0x1
    invoke-direct {v0, v1, v2, v3, v4}, Lai/a0;-><init>(IILjava/lang/String;Z)V
    sput-object v0, Lai/a0;->UN1CA_AI_BACKEND:Lai/a0;
    sput-object v0, Lai/a0;->debugDefault:Lai/a0;
    sput-object v0, Lai/a0;->releaseDefault:Lai/a0;
    sget-object v1, Lai/a0;->$VALUES:[Lai/a0;
    const/16 v2, 0x14
    invoke-static {v1, v2}, Ljava/util/Arrays;->copyOf([Ljava/lang/Object;I)[Ljava/lang/Object;
    move-result-object v1
    check-cast v1, [Lai/a0;
    const/16 v2, 0x13
    aput-object v0, v1, v2
    sput-object v1, Lai/a0;->$VALUES:[Lai/a0;
    invoke-static {v1}, Lct/i;->j([Ljava/lang/Enum;)Lu20/b;
    move-result-object v1
    sput-object v1, Lai/a0;->$ENTRIES:Lu20/a;
    sget-object v1, Lai/a0;->RELEASE_SUPPORTED_MODEL_IDS:[I
    invoke-static {v1}, Lio/mesalabs/rezoss/memory/MemoryBackend;->appendId([I)[I
    move-result-object v1
    sput-object v1, Lai/a0;->RELEASE_SUPPORTED_MODEL_IDS:[I
    sget-object v1, Lai/a0;->DEBUG_SUPPORTED_MODEL_IDS:[I
    invoke-static {v1}, Lio/mesalabs/rezoss/memory/MemoryBackend;->appendId([I)[I
    move-result-object v1
    sput-object v1, Lai/a0;->DEBUG_SUPPORTED_MODEL_IDS:[I
'''
 # Existing locals suffice; no original registers changed.
 pos=s.index('    return-void',s.index('.method static constructor <clinit>'))
 return s[:pos]+insert+'\n'+s[pos:]
change('smali_classes2/ai/a0.smali',enum)
# Add only explicit signature permission and service visibility; no data/provider mutation.
manifest='AndroidManifest.xml'
def permission(s):
 s=once(s,'    <queries>','''    <queries>
        <package android:name="com.samsung.android.aioskernelservice" />
        <intent><action android:name="android.intellivoiceservice.SpacClientService" /></intent>''')
 return once(s,'    <application ','''    <uses-permission android:name="io.mesalabs.rezoss.permission.QWEN_ENGINE" />
    <application ''')
change(manifest,permission)
parts=[]
for name,(old,new) in sorted(changes.items()):
 parts.extend(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+name,tofile='b/'+name,n=3))
output.parent.mkdir(parents=True,exist_ok=True); output.write_text(''.join(parts))
print(output)
