#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import sys,hashlib
source=Path(__file__).resolve().parent/'selinux'; platform=Path(sys.argv[1])/'system/system/etc/selinux'
for name,target in [('ota.cil','plat_sepolicy.cil'),('file_contexts','plat_file_contexts'),('property_contexts','plat_property_contexts')]:
 p=platform/target; old=p.read_text(); prefix=';' if name.endswith('.cil') else '#'; begin=prefix+' BEGIN UN1CA OTA'; end=prefix+' END UN1CA OTA'
 block=begin+'\n'+(source/name).read_text().rstrip()+'\n'+end
 if begin in old:
  first,tail=old.split(begin,1); _,last=tail.split(end,1); new=first+block+last
 else: new=old.rstrip()+'\n\n'+block+'\n'
 p.write_text(new)
(platform/'plat_sepolicy_and_mapping.sha256').write_text(hashlib.sha256((platform/'plat_sepolicy.cil').read_bytes()).hexdigest()+'\n')
