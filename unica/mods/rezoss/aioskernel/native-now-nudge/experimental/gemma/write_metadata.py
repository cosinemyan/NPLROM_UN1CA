#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import hashlib,json,sys,datetime,subprocess
root=Path(sys.argv[1]);entries={}
for backend in ['CPU','GPU']:
    p=root/('AIOS-Gemma4-E2B-'+backend+'-experimental.apk')
    h=hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
    fields={'package':'com.samsung.android.aioskernelservice','versionCode':142801210,'versionName':'1.4.28','variant':'gemma4-e2b-'+backend.lower()+'-experimental','model':'Gemma-4-E2B-it','modelIncluded':False,'sizeBytes':p.stat().st_size,'sha256':h,'builtAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain']))}
    p.with_suffix('.apk.version').write_text('\n'.join(str(k)+'='+str(v) for k,v in fields.items())+'\n')
    entries[p.name]=fields
(root/'checksums.json').write_text(json.dumps(entries,indent=2)+'\n')
