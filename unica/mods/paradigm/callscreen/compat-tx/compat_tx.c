/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Experimental exact-firmware S23U PAL interposer. Not a verified audio repair.
 * Samsung mode 4/RX proxy/state stay intact; only TX uses a muted handset path.
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
/* Measured in exact HAL VoiceStart/VoiceSetDevice; builder pins its SHA256. */
#define DEVICE_SIZE 0x170u
#define VOICE_CALL 14u
#define IN_PROXY 0x25u
#define OUT_PROXY 0x10u
#define HANDSET_MIC 0x19u
#define DEVICE_MUTE 0x2fu
#define TX_DIRECTION 2u
#define MAX_HANDLES 16
struct api {
    int (*open)(void *,uint32_t,void *,uint32_t,void *,void *,uint64_t,void **);
    int (*start)(void *);
    int (*close)(void *);
    int (*stop)(void *);
    int (*devices)(void *,uint32_t,void *);
    int (*mute)(void *,bool);
    int (*get_mute)(void *,bool *);
    int (*param)(void *,uint32_t,void *);
};
static struct api real;
struct entry { void *handle; bool proxy, requested_mute, broken, tx_mute_seen, requested_tx_mute; };
static struct entry handles[MAX_HANDLES];
static pthread_mutex_t lock;
static pthread_once_t once=PTHREAD_ONCE_INIT;
static uint32_t word(const void *p){uint32_t n;memcpy(&n,p,4);return n;}
static void put_word(void *p,uint32_t n){memcpy(p,&n,4);}
static void init(void){
    pthread_mutexattr_t attr;pthread_mutexattr_init(&attr);
    pthread_mutexattr_settype(&attr,PTHREAD_MUTEX_RECURSIVE);
    pthread_mutex_init(&lock,&attr);pthread_mutexattr_destroy(&attr);
#ifndef CS_TEST
    void *pal=dlopen("libar-pal.so",RTLD_NOW|RTLD_LOCAL);
#define RESOLVE(n) (pal?dlsym(pal,n):NULL)
#else
#define RESOLVE(n) cs_test_resolve(n)
#endif
    *(void **)(&real.open)=RESOLVE("pal_stream_open");
    *(void **)(&real.start)=RESOLVE("pal_stream_start");
    *(void **)(&real.close)=RESOLVE("pal_stream_close");
    *(void **)(&real.stop)=RESOLVE("pal_stream_stop");
    *(void **)(&real.devices)=RESOLVE("pal_stream_set_device");
    *(void **)(&real.mute)=RESOLVE("pal_stream_set_mute");
    *(void **)(&real.get_mute)=RESOLVE("pal_stream_get_mute");
    *(void **)(&real.param)=RESOLVE("pal_stream_set_param");
}
static bool ready(void){pthread_once(&once,init);return real.open&&real.start&&real.close&&real.stop&&real.devices&&real.mute&&real.get_mute&&real.param;}
static struct entry *find(void *h){for(int i=0;i<MAX_HANDLES;i++)if(handles[i].handle==h&&h)return &handles[i];return NULL;}
static struct entry *reserve(void *h){for(int i=0;i<MAX_HANDLES;i++)if(!handles[i].handle){handles[i]=(struct entry){.handle=h};return &handles[i];}return NULL;}
static bool proxy_pair(uint32_t n,const void *p){
    if(n!=2||!p)return false;
    uint32_t a=word(p),b=word((const uint8_t *)p+DEVICE_SIZE);
    return (a==IN_PROXY&&b==OUT_PROXY)||(b==IN_PROXY&&a==OUT_PROXY);
}
static void translate(uint8_t *copy,const void *src){
    memcpy(copy,src,2*DEVICE_SIZE);
    for(int i=0;i<2;i++)if(word(copy+i*DEVICE_SIZE)==IN_PROXY)put_word(copy+i*DEVICE_SIZE,HANDSET_MIC);
}
static int enforce_mute(void *h){
    int rc=real.mute(h,true);bool actual=false;
    if(!rc)rc=real.get_mute(h,&actual);
    return rc?rc:(actual?0:-EACCES);
}
static void quarantine(struct entry *e){
    e->broken=true;
    /* Stop possibly partial startup/routing; no subsequent start is permitted. */
    real.mute(e->handle,true);real.stop(e->handle);
}
int pal_stream_open(void *attr,uint32_t n,void *dev,uint32_t nm,void *mods,void *cb,uint64_t cookie,void **out){
    if(!ready())return -ENOSYS;
    bool voice=attr&&word(attr)==VOICE_CALL,proxy=voice&&proxy_pair(n,dev);
    union {uint64_t align;uint8_t bytes[2*DEVICE_SIZE];} mapped;
    if(proxy)translate(mapped.bytes,dev);
    pthread_mutex_lock(&lock);
    int rc=real.open(attr,n,proxy?mapped.bytes:dev,nm,mods,cb,cookie,out);
    if(!rc&&voice){
        struct entry *e=out&&*out?reserve(*out):NULL;
        if(!e){if(out&&*out){real.close(*out);*out=NULL;}rc=-ENOSPC;}
        else if(proxy){
            e->proxy=true;
            rc=real.get_mute(e->handle,&e->requested_mute);
            if(!rc)rc=enforce_mute(e->handle);
            if(rc){real.close(e->handle);memset(e,0,sizeof(*e));*out=NULL;}
            else NOTE("OPEN mode4 compatibility: TX=handset-mic forced mute, RX=proxy");
        }
    }
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_start(void *h){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);int rc=e&&e->broken?-EIO:0;
    if(!rc&&e&&e->proxy)rc=enforce_mute(h);
    if(!rc)rc=real.start(h);
    if(!rc&&e&&e->proxy)rc=enforce_mute(h);
    if(rc&&e&&e->proxy)quarantine(e);
    NOTE("START handle=%p compat=%d rc=%d",h,e&&e->proxy,rc);
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_set_mute(void *h,bool mute){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);if(e)e->requested_mute=mute;
    int rc=real.mute(h,e&&e->proxy?true:mute);
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_set_param(void *h,uint32_t id,void *data){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);uint8_t safe[12];void *payload=data;
    /* HAL SetDeviceMute: payload_size=8, direction, boolean + padding. */
    if(e&&id==DEVICE_MUTE&&data&&word(data)==8&&word((uint8_t *)data+4)==TX_DIRECTION){
        e->tx_mute_seen=true;e->requested_tx_mute=((uint8_t *)data)[8]!=0;
        if(e->proxy){memcpy(safe,data,sizeof(safe));safe[8]=1;payload=safe;}
    }
    int rc=real.param(h,id,payload);pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_set_device(void *h,uint32_t n,void *dev){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);bool proxy=e&&proxy_pair(n,dev);
    union {uint64_t align;uint8_t bytes[2*DEVICE_SIZE];} mapped;
    int rc=0;
    if(e&&e->broken)rc=-EIO;
    if(!rc&&proxy){
        if(!e->proxy)rc=real.get_mute(h,&e->requested_mute);
        if(!rc){e->proxy=true;rc=enforce_mute(h);}
        if(!rc){translate(mapped.bytes,dev);rc=real.devices(h,n,mapped.bytes);}
        if(!rc)rc=enforce_mute(h);
        if(rc)quarantine(e); /* No unmute after a partially failed switch. */
        NOTE("ROUTE proxy TX compatibility rc=%d",rc);
    }else if(!rc){
        if(e&&e->proxy)rc=enforce_mute(h);
        if(!rc)rc=real.devices(h,n,dev);
        if(!rc&&e&&e->proxy){
            if(e->tx_mute_seen){
                uint32_t saved[3]={8,TX_DIRECTION,e->requested_tx_mute?1u:0u};
                rc=real.param(h,DEVICE_MUTE,saved);
            }
            if(!rc)rc=real.mute(h,e->requested_mute);
            if(!rc){e->proxy=false;NOTE("LEAVE screening: restore requested mic/TX mute");}
        }
        if(rc&&e&&e->proxy)quarantine(e);
    }
    pthread_mutex_unlock(&lock);return rc;
}
int pal_stream_close(void *h){
    if(!ready())return -ENOSYS;
    pthread_mutex_lock(&lock);
    struct entry *e=find(h);int rc=real.close(h);
    if(e){if(!rc)memset(e,0,sizeof(*e));else e->broken=true;}
    pthread_mutex_unlock(&lock);return rc;
}
