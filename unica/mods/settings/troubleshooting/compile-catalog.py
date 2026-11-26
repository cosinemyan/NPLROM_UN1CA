#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the editable compatibility catalog to fixed, offline ROM detection tables."""
from pathlib import Path
import argparse,datetime,json,re,shutil,sys
BASE=Path(__file__).resolve().parent
REPO=BASE.parents[3]
def require(ok,message):
    if not ok:raise ValueError(message)
def relative(value):
    require(isinstance(value,str) and value and not value.startswith('/') and all(re.fullmatch(r'[A-Za-z0-9._$-]+',p) and p not in ('.','..') for p in value.split('/')),'Invalid relative path')
    return value

def compile_catalog(data,repo):
    require(data.get('schema')==1,'Unsupported catalog schema')
    features=data['features'];active={}
    for key,feature in features.items():
        require(re.fullmatch(r'[a-z0-9_]+',key),'Invalid feature key')
        require(feature.get('sources') and len(feature['name'])<=80 and '\t' not in feature['name'] and '\n' not in feature['name'],'Missing/invalid feature evidence')
        enabled=True
        for path in feature['sources']:
            source=repo/relative(path)
            if not source.is_file():enabled=False;continue
            for parent in (source.parent,*source.parents):
                if parent==repo:break
                if (parent/'disable').exists():enabled=False
        active[key]=enabled
    roots=set();purposes={};conflicts={};keys=set()
    for entry in data['entries']:
        require(entry['key'] not in keys,'Duplicate catalog key');keys.add(entry['key'])
        require(entry['verification'] in ('verified','pending','unverified','not_applicable'),'Invalid verification state')
        policy=entry.get('policy_conflict')
        if policy is not None:
            require(policy.get('group') in data.get('classification_groups',{}),'Unknown policy conflict group')
            require(policy.get('authority')=='user' and policy.get('status')=='classified','Invalid policy classification')
        rules=entry.get('rules',[])
        require(not rules or entry['verification']=='verified','Unverified identity cannot activate a rule')
        require(not rules or entry.get('identities'),'Rules require verified exact identities')
        for identity in entry.get('identities',[]):
            kind=identity['kind'];identifier=identity['value']
            require(kind in ('root','lsposed','app'),'Unknown identity kind')
            require(re.fullmatch(r'[A-Za-z0-9._-]{1,100}' if kind=='root' else r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',identifier),'Invalid exact identity')
            require(identity.get('sources') and identity.get('verified_on'),'Missing identity evidence')
            for rule in rules:
                require(kind!='app','Standalone apps do not enter module detection')
                feature=features[rule['feature']]
                require(rule['classification'] in ('duplicate','confirmed_conflict'),'Unknown classification')
                if not active[rule['feature']]:continue
                if kind=='root':
                    require(rule['classification']=='duplicate','Root confirmed-conflict matching is not implemented; do not downgrade it to a generic match')
                    files=feature.get('required_files',[])
                    require(0<len(files)<=8,'Root rules require installed file evidence')
                    for path in files:relative(path)
                    roots.add((identifier,feature['name'],'Same purpose as built-in '+feature['name'],'|'.join(files)))
                else:
                    require(feature.get('feature_class'),'LSPosed rules require an installed class gate')
                    scopes=rule.get('scopes',[]);require(scopes and '*' not in scopes,'Exact LSPosed target scopes required')
                    for scope in scopes:
                        require(scope in ('android','system') or re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',scope),'Invalid LSPosed scope')
                        row={'package':identifier,'scope':scope,'unica_mod':feature['name'],'feature_class':feature['feature_class'],'source':identity['sources'][0],'catalog_key':entry['key']}
                        key=(identifier,scope,feature['name'])
                        if rule['classification']=='duplicate':purposes[key]=row
                        else:
                            require(isinstance(rule.get('version_code'),int) and rule['version_code']>=0 and re.fullmatch(r'[0-9a-f]{64}',rule.get('signer_sha256','')) and rule.get('conflict_evidence'),'Confirmed conflict needs version, signer and conflict evidence')
                            row.update(confirmed=True,version_code=rule['version_code'],signer_sha256=rule['signer_sha256'],evidence=rule['conflict_evidence'])
                            conflicts[key+(rule['version_code'],rule['signer_sha256'])]=row
    require(len(roots)<=128,'Native rule limit exceeded')
    dump=lambda obj:json.dumps(obj,ensure_ascii=False,indent=2)+'\n'
    return {
      'troubleshooting-root-purposes.tsv':'# SPDX-License-Identifier: GPL-3.0-or-later\n# Generated by troubleshooting/compile-catalog.py; edit compatibility-catalog.json.\n# Exact module ID<TAB>UN1CA mod<TAB>purpose<TAB>required installed ROM files\n'+''.join('\t'.join(row)+'\n' for row in sorted(roots)),
      'troubleshooting-lsposed-purposes.json':dump({'schema':1,'rules':list(purposes.values())}),
      'troubleshooting-lsposed-conflicts.json':dump({'schema':1,'rules':list(conflicts.values())}),
      'troubleshooting-registry-info.json':dump({'schema':1,'updated':data['updated'],'catalog_entries':len(data['entries']),'verified_entries':sum(e['verification']=='verified' for e in data['entries']),'pending_entries':sum(e['verification']=='pending' for e in data['entries']),'not_applicable_entries':sum(e['verification']=='not_applicable' for e in data['entries']),'unverified_identity_entries':sum(e['verification']=='unverified' for e in data['entries']),'user_classified_conflict_entries':sum(bool(e.get('policy_conflict')) for e in data['entries']),'root_rules':len(roots),'lsposed_purpose_rules':len(purposes),'lsposed_confirmed_conflict_rules':len(conflicts)})}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--catalog',type=Path,default=BASE/'compatibility-catalog.json');p.add_argument('--repo-root',type=Path,default=REPO);p.add_argument('--output-dir',type=Path,default=BASE.parent/'system/etc/unica');p.add_argument('--check',action='store_true');p.add_argument('--no-backup',action='store_true',help='Use only for generated ROM work-directory output');a=p.parse_args()
    outputs=compile_catalog(json.loads(a.catalog.read_text()),a.repo_root.resolve())
    for name,content in outputs.items():
        dest=a.output_dir/name
        if a.check:require(dest.is_file() and dest.read_text()==content,'Generated table is stale: '+str(dest));continue
        if dest.exists() and dest.read_text()==content:continue
        dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists() and not a.no_backup:shutil.copy2(dest,str(dest)+'.bak.'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S_%f'))
        temp=dest.with_name(dest.name+'.tmp');temp.write_text(content);temp.chmod(0o644);temp.replace(dest)
    print('Compatibility tables '+('verified' if a.check else 'generated')+': '+str(a.output_dir))
if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,TypeError) as e:sys.exit('Invalid compatibility catalog: '+str(e))
