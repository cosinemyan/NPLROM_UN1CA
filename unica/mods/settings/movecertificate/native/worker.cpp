/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 * ROM adaptation of the temporary combined-store approach used by
 * https://github.com/ys1231/MoveCertificate (ys1231, Apache-2.0).
 * No shell, network, pinning hooks, or writes to stock certificate stores.
 */
#include <sys/mount.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>
#include <algorithm>

static const char* root="/data/misc/unica_movecert";
static const char* stage="/mnt/unica-movecert";
static const char* systemStore="/system/etc/security/cacerts";
static const char* apexStore="/apex/com.android.conscrypt/cacerts";
static const char* userStore="/data/misc/user/0/cacerts-added";
static const char* personalHash="0f4ed297";
static void check(bool ok,const std::string& why){if(!ok)throw std::runtime_error(why+": "+strerror(errno));}
static bool certName(const std::string& s){
    if(s.size()<10||s.size()>14||s[8]!='.')return false;
    for(size_t i=0;i<s.size();i++){if(i==8)continue;if(i<8){if(!((s[i]>='0'&&s[i]<='9')||(s[i]>='a'&&s[i]<='f')))return false;}else if(s[i]<'0'||s[i]>'9')return false;}
    return true;
}
static bool directory(const char* path){struct stat s{};return stat(path,&s)==0&&S_ISDIR(s.st_mode);}
static std::string readFile(const std::string& path){
    int fd=open(path.c_str(),O_RDONLY|O_CLOEXEC|O_NOFOLLOW);check(fd>=0,"open "+path);
    struct stat s{};if(fstat(fd,&s)||!S_ISREG(s.st_mode)||s.st_size<=0||s.st_size>65536){close(fd);throw std::runtime_error("Invalid certificate file: "+path);}
    std::string data;char buf[4096];ssize_t n;
    while((n=read(fd,buf,sizeof(buf)))>0){data.append(buf,n);if(data.size()>65536){close(fd);throw std::runtime_error("Oversized certificate");}}
    int saved=errno;close(fd);errno=saved;check(n==0,"read "+path);return data;
}
static std::vector<std::string> names(const char* path){
    DIR* dir=opendir(path);check(dir!=nullptr,std::string("list ")+path);std::vector<std::string> out;
    while(dirent* e=readdir(dir))if(certName(e->d_name))out.emplace_back(e->d_name);
    closedir(dir);std::sort(out.begin(),out.end());return out;
}
static std::string personalName(const char* path){
    auto entries=names(path);std::string best;int highest=-1;
    for(const auto& entry:entries){
        if(entry.compare(0,8,personalHash)!=0)continue;
        int index=std::stoi(entry.substr(9));
        if(index>highest){highest=index;best=entry;}
    }
    return best;
}
static void writeFile(const std::string& path,const std::string& data,mode_t mode){
    int fd=open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,mode);check(fd>=0,"create "+path);
    size_t offset=0;while(offset<data.size()){ssize_t n=write(fd,data.data()+offset,data.size()-offset);if(n<=0){close(fd);throw std::runtime_error("Write failed: "+path);}offset+=n;}
    int rc=fsync(fd);close(fd);check(rc==0,"sync "+path);
}
static void status(const std::string& text){
    std::string temp=std::string(root)+"/status.tmp",dest=std::string(root)+"/status";
    char boot[64]={};FILE* f=fopen("/proc/sys/kernel/random/boot_id","re");
    check(f!=nullptr,"read boot identity");bool valid=fgets(boot,sizeof(boot),f)!=nullptr;fclose(f);
    if(!valid)throw std::runtime_error("Cannot read boot identity");
    std::string id(boot);id.erase(id.find_last_not_of("\r\n")+1);
    unlink(temp.c_str());writeFile(temp,id+"\n"+text+"\n",0640);check(chown(temp.c_str(),0,1000)==0,"status ownership");check(rename(temp.c_str(),dest.c_str())==0,"status rename");
}
static void add(const std::string& store,const std::string& name,const std::string& data){
    const std::string hash=name.substr(0,8);
    for(int i=0;i<10000;i++){
        std::string dest=store+"/"+hash+"."+std::to_string(i);
        if(access(dest.c_str(),F_OK)!=0){writeFile(dest,data,0644);return;}
        if(readFile(dest)==data)return;
    }
    throw std::runtime_error("Certificate hash collision limit exceeded");
}
int main(){
    std::vector<std::string> mounted;bool temporary=false;
    try{
        // Certificate files must remain readable by apps in every domain, and
        // the status file must remain readable by Settings via the system group.
        umask(0);
        int config=open((std::string(root)+"/request/adcert_enabled").c_str(),O_RDONLY|O_CLOEXEC|O_NOFOLLOW);char enabled=0;
        if(config>=0){read(config,&enabled,1);close(config);}
        if(enabled!='1'){status("Disabled for this boot. No certificates applied by this service.");return 0;}
        std::string selected=directory(userStore)?personalName(userStore):"";
        if(selected.empty()){
            std::string temp=std::string(root)+"/request/adcert_enabled.tmp",flag=std::string(root)+"/request/adcert_enabled";
            unlink(temp.c_str());writeFile(temp,"0\n",0644);check(rename(temp.c_str(),flag.c_str())==0,"disable missing AdGuard CA");
            status("Disabled: AdGuard Personal CA is not installed in the owner user store.");return 0;
        }
        std::string data=readFile(std::string(userStore)+"/"+selected);
        if(data.empty()||(unsigned char)data[0]!=0x30)throw std::runtime_error("Invalid AdGuard user CA encoding");
        // Preserve the user's Android trust decision; do not silently clear
        // a removed-system-CA marker as the upstream Magisk module does.
        if(access("/data/misc/user/0/cacerts-removed/0f4ed297.0",F_OK)==0)
            throw std::runtime_error("AdGuard system CA disabled in Android certificate settings");
        // Init launches this service in its configured default mount namespace
        // after APEX activation and before starting Zygote. Apply mounts there
        // so later Zygote and app namespaces can inherit them.
        std::vector<std::string> targets;
        if(directory(systemStore))targets.emplace_back(systemStore);
        if(directory(apexStore)){
            char resolved[4096];check(realpath(apexStore,resolved)!=nullptr,"resolve Conscrypt store");
            targets.emplace_back(resolved);
            if(targets.back()!=apexStore)targets.emplace_back(apexStore);
        }
        if(targets.empty())throw std::runtime_error("No supported certificate store found");
        check(mount("tmpfs",stage,"tmpfs",MS_NOSUID|MS_NODEV|MS_NOEXEC,"mode=0755,size=8m,context=u:object_r:system_security_cacerts_file:s0")==0,"create temporary certificate store");temporary=true;
        std::string sys=std::string(stage)+"/system",apex=std::string(stage)+"/apex";
        check(mkdir(sys.c_str(),0755)==0&&mkdir(apex.c_str(),0755)==0,"create separate stores");
        // Put the selected Personal CA at .0, as in AdGuard's module, while
        // keeping any colliding original certificates at subsequent indices.
        if(directory(systemStore))add(sys,selected,data);
        if(directory(apexStore))add(apex,selected,data);
        if(directory(systemStore))for(const auto& name:names(systemStore))add(sys,name,readFile(std::string(systemStore)+"/"+name));
        if(directory(apexStore))for(const auto& name:names(apexStore))add(apex,name,readFile(std::string(apexStore)+"/"+name));
        check(mount(nullptr,stage,nullptr,MS_REMOUNT|MS_RDONLY|MS_NOSUID|MS_NODEV|MS_NOEXEC,nullptr)==0,"protect temporary store");
        for(const auto& target:targets){const std::string& source=target==systemStore?sys:apex;check(mount(source.c_str(),target.c_str(),nullptr,MS_BIND,nullptr)==0,"mount "+target);mounted.push_back(target);}
        status("Active this boot: AdGuard Personal CA " + selected + ". Changes require reboot.");return 0;
    }catch(const std::exception& e){
        bool restored=true;
        for(auto i=mounted.rbegin();i!=mounted.rend();++i)if(umount2(i->c_str(),MNT_DETACH)!=0)restored=false;
        if(temporary&&umount2(stage,MNT_DETACH)!=0)restored=false;
        try{status(std::string(restored?"Failed; worker mounts removed: ":"Failed; rollback incomplete, reboot with feature disabled: ")+e.what());}catch(...){}
        fprintf(stderr,"MoveCertificate: %s\n",e.what());return 1;
    }
}
