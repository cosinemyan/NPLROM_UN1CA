/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
int cs_sources_zero(void);
int pal_stream_open(void*,uint32_t,void*,uint32_t,void*,void*,uint64_t,void**);
int pal_stream_start(void*);int pal_stream_stop(void*);int pal_stream_close(void*);
int pal_stream_set_mute(void*,bool);int pal_stream_set_device(void*,uint32_t,void*);
int pal_stream_set_param(void*,uint32_t,void*);
struct stream { bool used,isolated,started; } streams[80];
static uint8_t last[0x2e0];
static int sources[16],checks,missing=-1,bad_name,read_fail,open_fail,start_fail,route_fail,close_fail,corrupt_start,corrupt_route;
static int real_opens,real_starts,real_stops;static bool last_mute;
static int controllers[16];
static void *mx_open(unsigned int n){assert(n==0);return (void*)1;}
static void mx_close(void*p){assert(p==(void*)1);}
static const char *mx_name(void*p){(void)p;return bad_name?"wrong-card":"kalama-mtp-snd-card";}
static void *mx_ctl(void*p,const char*n){
    (void)p;char name[32];
    for(int b=0;b<2;b++)for(int i=0;i<8;i++){
        snprintf(name,sizeof(name),"TX %s MUX%d",b?"SMIC":"DMIC",i);
        int k=b*8+i;if(!strcmp(n,name)){if(k==missing)return NULL;controllers[k]=k;return &controllers[k];}
    }
    abort();
}
static unsigned int mx_values(void*p){(void)p;return 1;}
static unsigned int mx_enums(void*p){(void)p;return 2;}
static int mx_value(void*p,unsigned int i){assert(!i);checks++;return read_fail?-EIO:sources[*(int*)p];}
static const char *mx_enum(void*p,unsigned int i){(void)p;assert(i<2);return i?"DMIC1":"ZERO";}
static uint32_t get(const void*p){uint32_t n;memcpy(&n,p,4);return n;}
static void inspect(uint32_t n,void*d,struct stream*s){
    memset(last,0,sizeof(last));if(n<=2&&d)memcpy(last,d,n*0x170);
    s->isolated=false;
    for(uint32_t i=0;i<n;i++){
        uint8_t *dev=(uint8_t*)d+i*0x170;
        if(get(dev)==0x19&&!strcmp((char*)dev+0xf0,"rezoss_cs_isolated"))s->isolated=true;
    }
}
static int f_open(void*a,uint32_t n,void*d,uint32_t nm,void*m,void*c,uint64_t k,void**h){
    (void)a;assert(nm==0&&!m&&!c&&k==123);real_opens++;
    if(open_fail)return -EIO;
    for(int i=0;i<80;i++)if(!streams[i].used){
        streams[i]=(struct stream){.used=true};inspect(n,d,&streams[i]);*h=&streams[i];return 0;
    }
    abort();
}
static int f_start(void*h){struct stream*s=h;real_starts++;if(corrupt_start)sources[7]=1;if(start_fail)return -EIO;s->started=true;return 0;}
static int f_stop(void*h){struct stream*s=h;real_stops++;s->started=false;return 0;}
static int f_close(void*h){if(close_fail)return -EIO;memset(h,0,sizeof(struct stream));return 0;}
static int f_devices(void*h,uint32_t n,void*d){if(route_fail)return -EIO;inspect(n,d,h);if(corrupt_route)sources[15]=1;return 0;}
static int f_mute(void*h,bool b){(void)h;last_mute=b;return 0;}
static uint8_t last_param[12];
static int f_param(void*h,uint32_t id,void*p){(void)h;assert(id==0x2f);memcpy(last_param,p,12);return 0;}
void *cs_test_resolve(const char*n){
#define R(symbol,f) if(!strcmp(n,symbol))return (void*)f
    R("pal_stream_open",f_open);R("pal_stream_start",f_start);R("pal_stream_stop",f_stop);
    R("pal_stream_close",f_close);R("pal_stream_set_device",f_devices);
    R("pal_stream_set_mute",f_mute);R("pal_stream_set_param",f_param);
    R("mixer_open",mx_open);R("mixer_close",mx_close);R("mixer_get_name",mx_name);
    R("mixer_get_ctl_by_name",mx_ctl);R("mixer_ctl_get_num_values",mx_values);
    R("mixer_ctl_get_num_enums",mx_enums);R("mixer_ctl_get_value",mx_value);R("mixer_ctl_get_enum_string",mx_enum);
    /* Unsupported PAL getters must never be requested. */
    abort();
}
static void pair(uint8_t*p,uint32_t a,uint32_t b){memset(p,0,0x2e0);memcpy(p,&a,4);memcpy(p+0x170,&b,4);}
static int open_rc(uint32_t type,uint8_t*p,void**h){*h=NULL;return pal_stream_open(&type,2,p,0,0,0,123,h);}
static void *open_pair(uint32_t type,uint8_t*p){void*h=NULL;assert(!open_rc(type,p,&h)&&h);return h;}
int main(void){
    for(int i=0;i<16;i++){sources[i]=1;assert(cs_sources_zero()==-EACCES);sources[i]=0;}
    assert(!cs_sources_zero());missing=15;assert(cs_sources_zero()==-ENODEV);missing=-1;
    read_fail=1;assert(cs_sources_zero()==-EIO);read_fail=0;
    bad_name=1;assert(cs_sources_zero()==-ENODEV);bad_name=0;
    uint8_t proxy[0x2e0],normal[0x2e0],record[0x2e0],music[0x2e0],saved[0x2e0];
    pair(proxy,0x25,0x10);pair(normal,0x19,2);pair(record,0x25,0x25);pair(music,0x10,0x10);
    memcpy(saved,proxy,sizeof(saved));void*h=open_pair(14,proxy);
    assert(get(last)==0x19&&get(last+0x170)==0x10);
    assert(!strcmp((char*)last+0xf0,"rezoss_cs_isolated"));assert(!memcmp(proxy,saved,sizeof(saved)));
    for(int i=4;i<0x2e0;i++)if(i<0xf0||i>=0x170)assert(last[i]==proxy[i]);
    int before=checks;assert(!pal_stream_start(h)&&checks>=before+32);
    assert(!pal_stream_set_mute(h,false)&&!last_mute);
    uint32_t param[3]={8,2,0};assert(!pal_stream_set_param(h,0x2f,param)&&last_param[8]==0&&param[2]==0);
    void*r=open_pair(11,record);assert(!pal_stream_start(r));assert(!pal_stream_set_device(r,2,record));
    assert(pal_stream_set_device(r,2,normal)==-EBUSY);assert(!pal_stream_close(r));
    void*m=open_pair(7,music);assert(!pal_stream_start(m));assert(!pal_stream_close(m));
    void*p=NULL;before=real_opens;assert(open_rc(2,normal,&p)==-EBUSY&&!p&&real_opens==before);
    assert(open_rc(11,normal,&p)==-EBUSY&&!p);
    uint8_t unknown[0x2e0];pair(unknown,0x70,0x70);assert(open_rc(11,unknown,&p)==-EBUSY&&!p);
    assert(!pal_stream_set_device(h,2,normal));assert(!pal_stream_close(h));
    /* A running microphone blocks screening; stopped microphone handles may
     * remain open, but cannot restart until screening ends. */
    p=open_pair(2,normal);assert(!pal_stream_start(p));
    assert(open_rc(14,proxy,&h)==-EBUSY&&!h);assert(!pal_stream_stop(p));
    h=open_pair(14,proxy);assert(pal_stream_start(p)==-EBUSY);assert(!pal_stream_close(h));assert(!pal_stream_close(p));
    /* Readback failures contain streams and cannot be bypassed by retry. */
    h=open_pair(14,proxy);sources[3]=1;before=real_starts;
    assert(pal_stream_start(h)==-EACCES&&real_starts==before);sources[3]=0;
    assert(pal_stream_start(h)==-EIO);assert(!pal_stream_close(h));
    h=open_pair(14,proxy);corrupt_start=1;before=real_stops;
    assert(pal_stream_start(h)==-EACCES&&real_stops==before+1&&!((struct stream*)h)->started);
    corrupt_start=0;sources[7]=0;assert(!pal_stream_close(h));
    h=open_pair(14,proxy);start_fail=1;assert(pal_stream_start(h)==-EIO);start_fail=0;assert(!pal_stream_close(h));
    h=open_pair(14,proxy);route_fail=1;assert(pal_stream_set_device(h,2,proxy)==-EIO);route_fail=0;
    assert(pal_stream_start(h)==-EIO);assert(!pal_stream_close(h));
    h=open_pair(14,normal);assert(!pal_stream_start(h));before=real_stops;
    assert(!pal_stream_set_device(h,2,proxy)&&real_stops==before+1&&((struct stream*)h)->started);
    assert(!pal_stream_close(h));
    h=open_pair(14,proxy);corrupt_route=1;assert(pal_stream_set_device(h,2,proxy)==-EACCES);
    corrupt_route=0;sources[15]=0;assert(!pal_stream_close(h));
    sources[0]=1;before=real_opens;assert(open_rc(14,proxy,&h)==-EACCES&&real_opens==before);sources[0]=0;
    pair(proxy,0x10,0x25);h=open_pair(14,proxy);
    assert(get(last)==0x10&&get(last+0x170)==0x19&&!strcmp((char*)last+0x170+0xf0,"rezoss_cs_isolated"));
    close_fail=1;assert(pal_stream_close(h)==-EIO);assert(pal_stream_start(h)==-EIO);close_fail=0;assert(!pal_stream_close(h));
    puts("PASS: 16-source kernel verifier, exact custom key, unchanged RX/buffers, no PAL getter, AI/record passthrough, concurrency admission, transitions, readback/start/route/close failures");
}
