#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import hashlib,json,sys,datetime,subprocess,os,re
root=Path(sys.argv[1]);entries={}
for backend in ['GPU']:
    p=root/('AIOS-Gemma4-E2B-'+backend+'.apk')
    h=hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
    sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Android/Sdk')))
    badging=subprocess.check_output([str(sdk/'build-tools/37.0.0/aapt2'),'dump','badging',str(p)],text=True)
    match=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging)
    if not match:raise RuntimeError('Cannot read built APK version')
    package,version,version_name=match.groups()
    if package!='com.samsung.android.aioskernelservice':raise RuntimeError('Unexpected APK package')
    fields={'package':package,'versionCode':int(version),'versionName':version_name,'variant':'gemma4-e2b-'+backend.lower(),'model':'Gemma-4-E2B-it','modelIncluded':False,'sizeBytes':p.stat().st_size,'sha256':h,'builtAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain']))}
    p.with_suffix('.apk.version').write_text('\n'.join(str(k)+'='+str(v) for k,v in fields.items())+'\n')
    entries[p.name]=fields
(root/'checksums.json').write_text(json.dumps(entries,indent=2)+'\n')
