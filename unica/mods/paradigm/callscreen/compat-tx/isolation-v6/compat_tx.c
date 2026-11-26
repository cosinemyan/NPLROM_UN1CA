/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Exact-firmware controlled-test candidate; not a proven audio repair.
 * Isolation is a dedicated ZERO-source codec route plus kernel readback.
 * No unsupported PAL getter and no forced DSP mute on synthesized speech.
 */
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <pthread.h>
#include <dlfcn.h>
#ifndef CS_TEST
#include <android/log.h>
#define NOTE(...) __android_log_print(4,"CallScreenTxCompat",__VA_ARGS__)
#else
#define NOTE(...) ((void)0)
extern void *cs_test_resolve(const char *);
#endif
int cs_sources_zero(void);
#define DEVICE_SIZE 0x170u
/* Device::insertStreamDeviceAttr reads the 128-byte key at device+0xf0. */
#define KEY_OFFSET 0xf0u
#define KEY_SIZE 128u
#define KEY "rezoss_cs_isolated"
#define VOICE_CALL 14u
#define CALL_RECORD 11u
#define IN_PROXY 0x25u
#define OUT_PROXY 0x10u
#define HANDSET_MIC 0x19u
#define MAX_HANDLES 64
struct api {
    int (*open)(void *,uint32_t,void *,uint32_t,void *,void *,uint64_t,void **);
    int (*start)(void *),(*stop)(void *),(*close)(void *);
    int (*devices)(void *,uint32_t,void *);
    int (*mute)(void *,bool);
    int (*param)(void *,uint32_t,void *);
};
static struct api real;
struct entry { void *handle; bool voice,record,proxy,physical,started,broken; };
static struct entry handles[MAX_HANDLES];
static pthread_mutex_t lock;
static pthread_once_t once=PTHREAD_ONCE_INIT;
static uint32_t word(const void *p){uint32_t n;memcpy(&n,p,4);return n;}
static void put_word(void *p,uint32_t n){memcpy(p,&n,4);}
static void init(void){
    pthread_mutexattr_t a;pthread_mutexattr_init(&a);
    pthread_mutexattr_settype(&a,PTHREAD_MUTEX_RECURSIVE);
    pthread_mutex_init(&lock,&a);pthread_mutexattr_destroy(&a);
#ifndef CS_TEST
    void *pal=dlopen("libar-pal.so",RTLD_NOW|RTLD_LOCAL);
#define RESOLVE(n) (pal?dlsym(pal,n):NULL)
#else
#define RESOLVE(n) cs_test_resolve(n)
#endif
    *(void **)(&real.open)=RESOLVE("pal_stream_open");
    *(void **)(&real.start)=RESOLVE("pal_stream_start");
    *(void **)(&real.stop)=RESOLVE("pal_stream_stop");
    *(void **)(&real.close)=RESOLVE("pal_stream_close");
    *(void **)(&real.devices)=RESOLVE("pal_stream_set_device");
    *(void **)(&real.mute)=RESOLVE("pal_stream_set_mute");
    *(void **)(&real.param)=RESOLVE("pal_stream_set_param");
}
static bool ready(void){pthread_once(&once,init);return real.open&&real.start&&real.stop&&real.close&&real.devices&&real.mute&&real.param;}
static struct entry *find(void *h){for(int i=0;i<MAX_HANDLES;i++)if(handles[i].handle==h&&h)return &handles[i];return NULL;}
static struct entry *slot(void){for(int i=0;i<MAX_HANDLES;i++)if(!handles[i].handle)return &handles[i];return NULL;}
static bool screening(void){for(int i=0;i<MAX_HANDLES;i++)if(handles[i].proxy)return true;return false;}
static bool physical_running(struct entry *except){for(int i=0;i<MAX_HANDLES;i++)if(&handles[i]!=except&&handles[i].started&&handles[i].physical)return true;return false;}
static bool proxy_pair(uint32_t n,const void *p){
    if(n!=2||!p)return false;
    uint32_t a=word(p),b=word((const uint8_t *)p+DEVICE_SIZE);
    return (a==IN_PROXY&&b==OUT_PROXY)||(b==IN_PROXY&&a==OUT_PROXY);
}
/* Conservative admission: software in-call recording is explicitly exempt;
 * other input devices, including USB/BT/unknown IDs, conflict with screening.
 * Stock input IDs start at 0x19; outputs precede them in this pinned HAL.
 */
static bool has_input(uint32_t n,const void *p){
    if(!p)return false;
    for(uint32_t i=0;i<n;i++)if(word((const uint8_t *)p+i*DEVICE_SIZE)>=HANDSET_MIC)return true;
    return false;
}
static bool software_record(bool record,uint32_t n,const void *p){
    if(!record||!p||!n||n>2)return false;
    /* Stream type alone is insufficient: only proxy recording devices are
     * allowed to bypass physical-capture admission. Never exempt a handset,
     * USB, Bluetooth or unknown input carried by a type11 handle.
     */
    for(uint32_t i=0;i<n;i++)if(word((const uint8_t *)p+i*DEVICE_SIZE)!=IN_PROXY)return false;
    return true;
}
static void translate(uint8_t *copy,const void *src){
    memcpy(copy,src,2*DEVICE_SIZE);
    for(int i=0;i<2;i++)if(word(copy+i*DEVICE_SIZE)==IN_PROXY){
        uint8_t *tx=copy+i*DEVICE_SIZE;
        put_word(tx,HANDSET_MIC);memset(tx+KEY_OFFSET,0,KEY_SIZE);
        memcpy(tx+KEY_OFFSET,KEY,sizeof(KEY));
    }
}
static int verify(const char *phase){
    int rc=cs_sources_zero();
    NOTE("ISOLATION %s: 16 codec source muxes ZERO rc=%d",phase,rc);
    (void)phase;return rc;
}
static void quarantine(struct entry *e){
    e->broken=true;
    int rc=real.stop(e->handle);
    if(!rc)e->started=false;
    NOTE("ISOLATION failed: stopped stream rc=%d",rc);
}
int pal_stream_open(void *attr,uint32_t n,void *dev,uint32_t nm,void *mods,void *cb,uint64_t cookie,void **out){
    if(!ready())return -ENOSYS;
    uint32_t type=attr?word(attr):0;
    bool voice=type==VOICE_CALL,proxy=voice&&proxy_pair(n,dev);
    bool physical=!software_record(type==CALL_RECORD,n,dev)&&has_input(n,dev)&&!proxy;
    union {uint64_t align;uint8_t bytes[2*DEVICE_SIZE];} mapped;
    pthread_mutex_lock(&lock);
    int rc=0;struct entry *e=NULL;
    if((proxy&&(screening()||physical_running(NULL)))||(physical&&screening()))rc=-EBUSY;
    if(!rc&&!(e=slot()))rc=-ENOSPC;
    if(!rc&&proxy)rc=verify("before-open");
    if(!rc){
        if(proxy)translate(mapped.bytes,dev);
        rc=real.open(attr,n,proxy?mapped.bytes:dev,nm,mods,cb,cookie,out);
        if(!rc&&e){
            if(!out||!*out)rc=-EIO;
            else *e=(struct entry){.handle=*out,.voice=voice,.record=type==CALL_RECORD,.proxy=proxy,.physical=physical};
            if(!rc&&proxy)rc=verify("after-open");
            if(rc&&e->handle){
                if(!real.close(e->handle))memset(e,0,sizeof(*e));
                else {e->broken=true;real.stop(e->handle);}
                *out=NULL;
            }
        }
    }
    if(rc&&out)*out=NULL;
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_start(void *h){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);int rc=e&&e->broken?-EIO:0;
    if(!rc&&e&&e->physical&&screening())rc=-EBUSY;
    if(!rc&&e&&e->proxy){
        if(physical_running(e))rc=-EBUSY;
        else rc=verify("before-start");
    }
    if(!rc){rc=real.start(h);if(!rc&&e)e->started=true;}
    if(!rc&&e&&e->proxy)rc=verify("after-start");
    if(rc&&e&&e->proxy)quarantine(e);
    NOTE("START compat=%d rc=%d",e&&e->proxy,rc);
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_stop(void *h){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);int rc=real.stop(h);struct entry *e=find(h);
    if(!rc&&e)e->started=false;
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_set_device(void *h,uint32_t n,void *dev){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);bool proxy=e&&e->voice&&proxy_pair(n,dev);
    bool physical=has_input(n,dev)&&!proxy&&!software_record(e&&e->record,n,dev);
    union {uint64_t align;uint8_t bytes[2*DEVICE_SIZE];} mapped;
    int rc=e&&e->broken?-EIO:0;
    if(!rc&&proxy&&(physical_running(e)||(screening()&&!e->proxy)))rc=-EBUSY;
    if(!rc&&physical&&screening()&&!(e&&e->proxy))rc=-EBUSY;
    /* A running normal call cannot enter screening without first stopping:
     * avoid a physical path surviving into the route transition.
     */
    bool resume=!rc&&proxy&&!e->proxy&&e->started;
    if(resume){rc=real.stop(h);if(!rc)e->started=false;}
    if(!rc&&proxy)rc=verify("before-route");
    if(!rc){
        if(proxy)translate(mapped.bytes,dev);
        rc=real.devices(h,n,proxy?mapped.bytes:dev);
        if(!rc&&e){e->proxy=proxy;e->physical=physical;}
        if(!rc&&proxy)rc=verify("after-route");
        if(!rc&&resume){rc=real.start(h);if(!rc)e->started=true;}
        if(!rc&&resume)rc=verify("after-route-start");
    }
    if(rc&&e&&(e->proxy||proxy))quarantine(e);
    NOTE("ROUTE compat=%d rc=%d",proxy,rc);
    pthread_mutex_unlock(&lock);return rc;
}
/* Hardware sources are disconnected upstream of AI injection. Preserve HAL
 * mute semantics instead of muting the entire voice DSP stream ourselves.
 */
int pal_stream_set_mute(void *h,bool mute){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);struct entry *e=find(h);
    int rc=e&&e->broken?-EIO:0;
    if(!rc&&e&&e->proxy)rc=verify("mute");
    if(!rc)rc=real.mute(h,mute);
    if(!rc&&e&&e->proxy)rc=verify("after-mute");
    if(rc&&e&&e->proxy)quarantine(e);
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_set_param(void *h,uint32_t id,void *data){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);struct entry *e=find(h);
    int rc=e&&e->broken?-EIO:0;
    if(!rc&&e&&e->proxy)rc=verify("param");
    if(!rc)rc=real.param(h,id,data);
    if(!rc&&e&&e->proxy)rc=verify("after-param");
    if(rc&&e&&e->proxy)quarantine(e);
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_close(void *h){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);struct entry *e=find(h);int rc=real.close(h);
    if(e){if(!rc)memset(e,0,sizeof(*e));else quarantine(e);}
    pthread_mutex_unlock(&lock);return rc;
}
