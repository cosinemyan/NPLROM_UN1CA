// SPDX-License-Identifier: GPL-3.0-or-later
// Fixed module inventory and reversible marker operations. No shell execution.
#include <sys/stat.h>
#include <sys/file.h>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <cstdint>
#include <map>
#include <vector>
#include <string>
#include <sstream>
#include <stdexcept>
#ifndef TROUBLE_TEST
static const std::string base="/data/misc/unica_troubleshooting", adb="/data/adb", systemRoot="/system";
#else
static std::string base,adb,systemRoot;
#endif
using std::string;
struct FD { int n; explicit FD(int x=-1):n(x){} ~FD(){if(n>=0)close(n);} FD(const FD&)=delete; operator int()const{return n;} };
static bool safe(const string& s){return !s.empty()&&s.size()<=100&&s!="."&&s!=".."&&s.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")==string::npos;}
static string quote(const string&s){string out="\"";for(unsigned char c:s){if(c=='"'||c=='\\'){out+='\\';out+=c;}else if(c<32||c==127){char b[7];snprintf(b,sizeof b,"\\u%04x",c);out+=b;}else out+=c;}return out+'"';}
static string readfd(int fd,size_t limit){if(fd<0)throw std::runtime_error("File unavailable");struct stat st{};if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_size<0||(uint64_t)st.st_size>limit)throw std::runtime_error("Invalid file size/type");string out;char b[4096];ssize_t n;while((n=read(fd,b,sizeof b))>0){out.append(b,n);if(out.size()>limit)throw std::runtime_error("File too large");}if(n<0)throw std::runtime_error("Read denied");return out;}
static string readat(int dir,const string& name,size_t limit){FD fd(openat(dir,name.c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK));return readfd(fd,limit);}
static bool exists(int dir,const char*name){struct stat st{};if(!fstatat(dir,name,&st,AT_SYMLINK_NOFOLLOW))return true;if(errno!=ENOENT)throw std::runtime_error("Marker state unavailable");return false;}
static void writeall(int fd,const string&s){size_t pos=0;while(pos<s.size()){ssize_t n=write(fd,s.data()+pos,s.size()-pos);if(n<=0)throw std::runtime_error("Write failed");pos+=n;}if(fsync(fd))throw std::runtime_error("Sync failed");}
static void publish(const string&name,const string&s){FD d(open((base+"/responses").c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC));if(d<0)throw std::runtime_error("Response directory unavailable");string temp=name+".tmp";FD f(openat(d,temp.c_str(),O_WRONLY|O_CREAT|O_TRUNC|O_NOFOLLOW|O_CLOEXEC,0640));if(f<0)throw std::runtime_error("Response unavailable");struct stat st{};if(fstat(f,&st)||!S_ISREG(st.st_mode)||st.st_nlink!=1||st.st_uid!=getuid())throw std::runtime_error("Unsafe response");if(fchmod(f,0640)||fchown(f,getuid(),1000))throw std::runtime_error("Response permissions");writeall(f,s);if(renameat(d,temp.c_str(),d,name.c_str())||fsync(d))throw std::runtime_error("Publish failed");}
static string digest(const string&s){uint64_t hash=14695981039346656037ULL;for(unsigned char c:s){hash^=c;hash*=1099511628211ULL;}std::ostringstream o;o<<std::hex<<hash;return o.str();}
static std::map<string,string> props(const string&s){std::map<string,string> out;std::istringstream in(s);string line;while(std::getline(in,line)){if(line.empty()||line[0]=='#')continue;size_t p=line.find('=');if(p!=string::npos)out[line.substr(0,p)]=line.substr(p+1);}return out;}
// Traverse every component relative to trusted descriptors; never follow a module symlink.
static int beneath(int dir,const string&path,int flags){int current=dup(dir);std::istringstream in(path);string part;std::vector<string> parts;while(std::getline(in,part,'/')){if(!safe(part)){close(current);errno=EINVAL;return -1;}parts.push_back(part);}for(size_t i=0;i<parts.size();i++){int next=openat(current,parts[i].c_str(),(i+1==parts.size()?flags:O_PATH|O_DIRECTORY)|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK);close(current);if(next<0)return -1;current=next;}return current;}
struct Duplicate {string feature,purpose;};
struct Module { string id,stage,name,version,token,evidence;std::vector<Duplicate> duplicates;bool disabled=false,removed=false,owned=false; };
static string identity(int dir,const string&meta){struct stat st{};if(fstat(dir,&st))throw std::runtime_error("Module disappeared");return digest(meta+"\n"+std::to_string(st.st_dev)+"\n"+std::to_string(st.st_ino));}
static string marker(const Module&m){return "UN1CA troubleshooting v1\n"+m.stage+"\n"+m.id+"\n"+m.token+"\n";}
static string stateName(const Module&m){return "state-"+m.stage+"-"+m.id;}
static int openAdb(){FD data(open("/data",O_DIRECTORY|O_PATH|O_NOFOLLOW|O_CLOEXEC));
#ifdef TROUBLE_TEST
return open(adb.c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC);
#else
return data<0?-1:openat(data,"adb",O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC);
#endif
}
// Exact IDs plus installed ROM evidence. Never infer duplication from a generic name.
static void purposeRules(Module& module){
 FD system(open(systemRoot.c_str(),O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC));if(system<0)return;
 FD input(beneath(system,"etc/unica/troubleshooting-root-purposes.tsv",O_RDONLY));if(input<0)return;
 std::istringstream rules(readfd(input,65536));string line;size_t count=0;
 while(std::getline(rules,line)){
  if(line.empty()||line[0]=='#')continue;
  if(++count>128)throw std::runtime_error("Too many module purpose rules");
  std::istringstream row(line);string id,feature,purpose,required,extra;
  if(!std::getline(row,id,'\t')||!std::getline(row,feature,'\t')||!std::getline(row,purpose,'\t')||!std::getline(row,required,'\t'))continue;
  if(std::getline(row,extra,'\t')||id!=module.id||!safe(id)||feature.empty()||purpose.empty()||required.empty()||feature.size()>80||purpose.size()>160)continue;
  bool present=true;std::istringstream paths(required);string path;size_t files=0;
  while(std::getline(paths,path,'|')){
   if(++files>8){present=false;break;}
   FD file(beneath(system,path,O_PATH));struct stat st{};
   if(file<0||fstat(file,&st)||!S_ISREG(st.st_mode)){present=false;break;}
  }
  if(present&&files>0){bool seen=false;for(const auto& item:module.duplicates)seen|=item.feature==feature;if(!seen)module.duplicates.push_back({feature,purpose});}
 }
}
static Module inspect(int parent,const string&id,const string&stage){
 FD dir(openat(parent,id.c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC));if(dir<0)throw std::runtime_error("Module directory unavailable");
 string meta=readat(dir,"module.prop",16384);auto p=props(meta);if(p["id"]!=id)throw std::runtime_error("Module ID mismatch");
 Module m;m.id=id;m.stage=stage;m.name=p["name"].substr(0,160);m.version=p["version"].substr(0,100);m.token=identity(dir,meta);m.disabled=exists(dir,"disable");m.removed=exists(dir,"remove");
 if(m.disabled){try{FD responses(open((base+"/responses").c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW));m.owned=readat(dir,"disable",1024)==marker(m)&&readat(responses,stateName(m),1024)==marker(m);}catch(...) {}}
 purposeRules(m);
 // Only installed ROM artifacts are considered. Overlap is evidence, never proof of failure.
 const std::vector<std::pair<string,string>> paths={
 {"framework/framework.jar","Android framework / HMA / PIF"},{"framework/services.jar","System server / HMA / PIF"},{"lib64/libunica_pif.so","PIF"},{"lib/libunica_pif.so","PIF"},
 {"bin/prophide.sh","Boot property handling"},{"bin/unica-teesim-inject","TEE Simulator"},{"framework/unica-teesim.jar","TEE Simulator"},{"lib64/libunica_teesim.so","TEE Simulator"},
 {"priv-app/AIOSKernelService/AIOSKernelService.apk","AI backend"},{"priv-app/SecSettings/SecSettings.apk","UN1CA Settings"}};
 for(auto&r:paths){struct stat st{};if(!stat((systemRoot+"/"+r.first).c_str(),&st)){FD f(beneath(dir,"system/"+r.first,O_RDONLY));if(f>=0)m.evidence+=r.second+": overlay system/"+r.first+"\n";}}
 struct stat st{};if(!stat((systemRoot+"/bin/prophide.sh").c_str(),&st)){
  try{auto values=props(readat(dir,"system.prop",65536));for(auto&v:values)if(v.first=="ro.boot.flash.locked"||v.first=="ro.boot.vbmeta.device_state"||v.first=="ro.boot.verifiedbootstate"||v.first=="ro.boot.warranty_bit"||v.first=="ro.vendor.boot.warranty_bit"||v.first=="ro.boot.veritymode")m.evidence+="Boot property handling: system.prop sets "+v.first+"\n";}catch(...){if(errno!=ENOENT)m.evidence+="system.prop could not be inspected; coverage incomplete\n";}
 }
 return m;
}
static string json(const Module&m){
 string duplicates="[";bool first=true;for(const auto& item:m.duplicates){if(!first)duplicates+=",";first=false;duplicates+="{\"feature\":"+quote(item.feature)+",\"purpose\":"+quote(item.purpose)+"}";}duplicates+="]";
 return "{\"id\":"+quote(m.id)+",\"stage\":"+quote(m.stage)+",\"name\":"+quote(m.name)+",\"version\":"+quote(m.version)+",\"token\":"+quote(m.token)+",\"disabled\":"+(m.disabled?"true":"false")+",\"removed\":"+(m.removed?"true":"false")+",\"owned\":"+(m.owned?"true":"false")+",\"evidence\":"+quote(m.evidence)+",\"duplicates\":"+duplicates+"}";
}

// Read a stable copy, including WAL, so SQLite never opens the live LSPosed database.
// Only enabled-state/scope rows leave this private temporary directory.
struct sqlite3;struct sqlite3_stmt;
struct Sqlite {
 void* library=nullptr;
 int(*open)(const char*,sqlite3**,int,const char*)=nullptr;
 int(*close)(sqlite3*)=nullptr;
 int(*prepare)(sqlite3*,const char*,int,sqlite3_stmt**,const char**)=nullptr;
 int(*step)(sqlite3_stmt*)=nullptr;int(*finish)(sqlite3_stmt*)=nullptr;
 const unsigned char*(*column)(sqlite3_stmt*,int)=nullptr;int(*integer)(sqlite3_stmt*,int)=nullptr;
 Sqlite(){
#ifdef TROUBLE_TEST
 library=dlopen("libsqlite3.so.0",RTLD_NOW|RTLD_LOCAL);
#else
 library=dlopen("/system/lib64/libsqlite.so",RTLD_NOW|RTLD_LOCAL);
#endif
 if(!library)throw std::runtime_error("LSPosed state reader unavailable");
#define LOAD(field,symbol) field=reinterpret_cast<decltype(field)>(dlsym(library,symbol));if(!field)throw std::runtime_error("SQLite interface unavailable")
 LOAD(open,"sqlite3_open_v2");LOAD(close,"sqlite3_close");LOAD(prepare,"sqlite3_prepare_v2");LOAD(step,"sqlite3_step");LOAD(finish,"sqlite3_finalize");LOAD(column,"sqlite3_column_text");LOAD(integer,"sqlite3_column_int");
#undef LOAD
 }
 ~Sqlite(){if(library)dlclose(library);}
};
struct Snapshot {
 string path;int dir=-1;
 Snapshot(){string pattern=base+"/lsposed-XXXXXX";std::vector<char> name(pattern.begin(),pattern.end());name.push_back(0);char* p=mkdtemp(name.data());if(!p)throw std::runtime_error("Cannot create LSPosed state snapshot");path=p;dir=::open(path.c_str(),O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);}
 ~Snapshot(){if(dir>=0){for(const char* n:{"state.db","state.db-wal","state.db-shm"})unlinkat(dir,n,0);::close(dir);}if(!path.empty())rmdir(path.c_str());}
};
static void copyState(int source,int target,const string&from,const string&to,size_t limit,bool optional){
 FD in(beneath(source,from,O_RDONLY));if(in<0){if(optional&&errno==ENOENT)return;throw std::runtime_error("LSPosed state is not accessible");}
 struct stat before{},after{};if(fstat(in,&before))throw std::runtime_error("Cannot inspect LSPosed state");string bytes=readfd(in,limit);
 if(fstat(in,&after)||before.st_size!=after.st_size||before.st_mtim.tv_sec!=after.st_mtim.tv_sec||before.st_mtim.tv_nsec!=after.st_mtim.tv_nsec)throw std::runtime_error("LSPosed state changed; scan again");
 FD out(openat(target,to.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));if(out<0)throw std::runtime_error("Cannot snapshot LSPosed state");writeall(out,bytes);
}
static string stateStamp(int root){
 string stamp;
 for(const string file:{"lspd/config/modules_config.db","lspd/config/modules_config.db-wal"}){
  FD fd(beneath(root,file,O_RDONLY));if(fd<0){if(errno==ENOENT){stamp+="absent;";continue;}throw std::runtime_error("Cannot inspect LSPosed snapshot state");}
  struct stat st{};if(fstat(fd,&st))throw std::runtime_error("Cannot inspect LSPosed state");
  stamp+=std::to_string(st.st_ino)+":"+std::to_string(st.st_size)+":"+std::to_string(st.st_mtim.tv_sec)+":"+std::to_string(st.st_mtim.tv_nsec)+":"+std::to_string(st.st_ctim.tv_sec)+":"+std::to_string(st.st_ctim.tv_nsec)+";";
 }return stamp;
}
static string lsposed(){
 try{
  FD root(openAdb());if(root<0)throw std::runtime_error("Root storage unavailable");Snapshot snapshot;string before=stateStamp(root);
  copyState(root,snapshot.dir,"lspd/config/modules_config.db","state.db",2*1024*1024,false);
  copyState(root,snapshot.dir,"lspd/config/modules_config.db-wal","state.db-wal",8*1024*1024,true);
  if(before!=stateStamp(root))throw std::runtime_error("LSPosed settings changed; scan again");
  Sqlite api;sqlite3* db=nullptr;
  if(api.open((snapshot.path+"/state.db").c_str(),&db,1|0x10000,nullptr)!=0){if(db)api.close(db);throw std::runtime_error("Unsupported LSPosed database snapshot");}
  struct Close {Sqlite& api;sqlite3* db;~Close(){api.close(db);}} close{api,db};
  sqlite3_stmt* query=nullptr;
  const char* sql="SELECT s.module_pkg_name,s.enabled,COALESCE(c.app_pkg_name,'') FROM modules_state s LEFT JOIN scope c ON c.module_pkg_name=s.module_pkg_name AND c.user_id=s.user_id WHERE s.user_id=0 ORDER BY s.module_pkg_name,c.app_pkg_name";
  if(api.prepare(db,sql,-1,&query,nullptr)!=0)throw std::runtime_error("Unsupported LSPosed state schema");
  struct Finalize {Sqlite&api;sqlite3_stmt* p;~Finalize(){api.finish(p);}} finalize{api,query};
  std::map<string,std::pair<bool,std::vector<string>>> rows;int status,count=0;
  while((status=api.step(query))==100){if(++count>2048)throw std::runtime_error("LSPosed scope list too large");const unsigned char* pkg=api.column(query,0);const unsigned char* scope=api.column(query,2);if(!pkg)throw std::runtime_error("Invalid LSPosed state");string name=reinterpret_cast<const char*>(pkg);if(name.size()>200)throw std::runtime_error("Invalid LSPosed package");auto& row=rows[name];row.first=api.integer(query,1)==1;if(scope&&*scope)row.second.emplace_back(reinterpret_cast<const char*>(scope));}
  if(status!=101)throw std::runtime_error("Cannot read LSPosed state snapshot");
  string out="{\"available\":true,\"modules\":[";bool first=true;
  for(const auto& row:rows){if(!first)out+=",";first=false;out+="{\"package\":"+quote(row.first)+",\"enabled\":"+(row.second.first?"true":"false")+",\"scopes\":[";bool sf=true;for(const auto& scope:row.second.second){if(!sf)out+=",";sf=false;out+=quote(scope);}out+="]}";}
  return out+"]}";
 }catch(const std::exception&e){return "{\"available\":false,\"modules\":[],\"warning\":"+quote(e.what())+"}";}
}
static string scan(){FD root(openAdb());string out="{\"modules\":[",warnings;bool first=true;size_t count=0;if(root<0)warnings="Root module storage inaccessible; inventory incomplete";else for(const string stage:{"modules","modules_update"}){
 FD parent(openat(root,stage.c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC));if(parent<0){if(errno!=ENOENT)warnings+="Cannot read "+stage+"; ";continue;}DIR* d=fdopendir(dup(parent));if(!d){warnings+="Cannot enumerate "+stage+"; ";continue;}dirent* entry;while((entry=readdir(d))){string id=entry->d_name;if(id=="."||id=="..")continue;if(!safe(id)||++count>512){warnings+="Unsupported module name or too many entries; ";continue;}try{Module m=inspect(parent,id,stage);if(!first)out+=",";first=false;out+=json(m);}catch(const std::exception&e){warnings+="Cannot inspect "+id+": "+e.what()+"; ";}}closedir(d);
 }return out+"],\"warning\":"+quote(warnings)+",\"lsposed\":"+lsposed()+"}";}
static string change(const string&op,const string&stage,const string&id,const string&token){
 if((stage!="modules"&&stage!="modules_update")||!safe(id)||!safe(token))throw std::runtime_error("Invalid module request");
 FD root(openAdb());FD parent(root<0?-1:openat(root,stage.c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW));if(parent<0)throw std::runtime_error("Module storage unavailable");
 Module m=inspect(parent,id,stage);if(m.token!=token||m.removed)throw std::runtime_error("Module changed; scan again");
 FD dir(openat(parent,id.c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC));if(dir<0||identity(dir,readat(dir,"module.prop",16384))!=token)throw std::runtime_error("Module changed during operation");
 if(op=="disable"){
  if(m.disabled)throw std::runtime_error("Module already disabled; preserving existing marker");
  publish(stateName(m),marker(m)); // Persist ownership before mutation; recovery can identify the marker.
  FD f(openat(dir,"disable",O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0644));if(f<0)throw std::runtime_error("Cannot create disable marker");
  try{writeall(f,marker(m));if(fsync(dir))throw std::runtime_error("Cannot sync module directory");}catch(...){unlinkat(dir,"disable",0);throw;}
  if(readat(dir,"disable",1024)!=marker(m))throw std::runtime_error("Marker verification failed");
 }else if(op=="restore"){
  if(!m.owned||readat(dir,"disable",1024)!=marker(m))throw std::runtime_error("Only this menu's unchanged markers can be restored");
  if(unlinkat(dir,"disable",0)||fsync(dir))throw std::runtime_error("Cannot restore module");
 }else throw std::runtime_error("Unsupported operation");
 return "{\"changed\":true,\"restart_required\":true,\"module\":"+quote(id)+",\"stage\":"+quote(stage)+"}";
}
int main([[maybe_unused]] int argc,[[maybe_unused]] char**argv){
#ifdef TROUBLE_TEST
 if(argc!=4)return 2;
 base=argv[1];adb=argv[2];systemRoot=argv[3];
#endif
 FD lock(open((base+"/lock").c_str(),O_CREAT|O_RDWR|O_NOFOLLOW|O_CLOEXEC,0600));if(lock<0||flock(lock,LOCK_EX|LOCK_NB))return 1;
 string id;
 try{
  FD requests(open((base+"/requests").c_str(),O_DIRECTORY|O_RDONLY|O_NOFOLLOW|O_CLOEXEC));FD f(requests<0?-1:openat(requests,"request",O_RDONLY|O_NOFOLLOW|O_NONBLOCK|O_CLOEXEC));struct stat st{};
  if(f<0||fstat(f,&st)||st.st_uid!=1000||st.st_nlink!=1)throw std::runtime_error("Unauthorized request");
  std::istringstream in(readfd(f,1024));string op,stage,module,token,extra;std::getline(in,id);std::getline(in,op);std::getline(in,stage);std::getline(in,module);std::getline(in,token);std::getline(in,extra);
  if(!safe(id)||!extra.empty())throw std::runtime_error("Invalid request");
  string result=op=="scan"?scan():change(op,stage,module,token);
  publish("response.json","{\"id\":"+quote(id)+",\"result\":"+result+"}\n");return 0;
 }catch(const std::exception&e){try{if(safe(id))publish("response.json","{\"id\":"+quote(id)+",\"error\":"+quote(e.what())+"}\n");}catch(...){}return 1;}
}
