/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
int pal_stream_open(void*,uint32_t,void*,uint32_t,void*,void*,uint64_t,void**);
int pal_stream_start(void*);int pal_stream_close(void*);int pal_stream_set_mute(void*,bool);
int pal_stream_set_device(void*,uint32_t,void*);int pal_stream_set_param(void*,uint32_t,void*);
static bool muted;static int starts,closes,mute_fail,route_fail,start_fail,reset_start_mute,reset_route_mute,stops;static uint8_t last[0x2e0];
static int f_open(void*a,uint32_t n,void*d,uint32_t nm,void*m,void*c,uint64_t k,void**h){
    (void)a;assert(n==2);assert(nm==0);assert(!m&&!c&&k==123);memcpy(last,d,sizeof(last));*h=(void*)1234;muted=false;return 0;
}
static int f_start(void*h){assert(h==(void*)1234);starts++;if(reset_start_mute)muted=false;return start_fail?-EIO:0;}
static int f_stop(void*h){assert(h==(void*)1234);stops++;return 0;}
static int f_close(void*h){assert(h==(void*)1234);closes++;return 0;}
static int f_devices(void*h,uint32_t n,void*d){assert(h==(void*)1234&&n==2);memcpy(last,d,sizeof(last));if(reset_route_mute)muted=false;return route_fail?-EIO:0;}
static int f_mute(void*h,bool b){assert(h==(void*)1234);if(mute_fail)return -EIO;muted=b;return 0;}
static int f_get_mute(void*h,bool*b){assert(h==(void*)1234);*b=muted;return 0;}
static uint8_t last_param[12];
static int f_param(void*h,uint32_t id,void*p){assert(h==(void*)1234&&id==0x2f);memcpy(last_param,p,12);return 0;}
void *cs_test_resolve(const char*n){
#define R(symbol,f) if(!strcmp(n,symbol))return (void*)f
 R("pal_stream_open",f_open);R("pal_stream_start",f_start);R("pal_stream_stop",f_stop);R("pal_stream_close",f_close);
 R("pal_stream_set_device",f_devices);R("pal_stream_set_mute",f_mute);R("pal_stream_get_mute",f_get_mute);R("pal_stream_set_param",f_param);
 return NULL;
}
static uint32_t get(const uint8_t*p){uint32_t n;memcpy(&n,p,4);return n;}
static void pair(uint8_t*p,uint32_t a,uint32_t b){memset(p,0x5a,0x2e0);memcpy(p,&a,4);memcpy(p+0x170,&b,4);}
static void *open_pair(uint32_t type,uint8_t*p){void*h=0;assert(!pal_stream_open(&type,2,p,0,0,0,123,&h));return h;}
int main(void){
 uint8_t proxy[0x2e0],normal[0x2e0],original[0x2e0];pair(proxy,0x25,0x10);pair(normal,0x19,2);memcpy(original,proxy,sizeof(proxy));
 void*h=open_pair(14,proxy);assert(get(last)==0x19&&get(last+0x170)==0x10&&muted);assert(!memcmp(original,proxy,sizeof(proxy)));
 for(int i=0;i<0x2e0;i++)if(i>=4)assert(last[i]==proxy[i]); /* only TX ID changed */
 assert(!pal_stream_start(h)&&muted&&starts==1);
 assert(!pal_stream_set_mute(h,false)&&muted); /* deny physical mic unmute */
 uint32_t param[3]={8,2,0};assert(!pal_stream_set_param(h,0x2f,param));assert(last_param[8]==1&&param[2]==0);
 assert(!pal_stream_set_device(h,2,normal)&&!muted&&last_param[8]==0); /* mode4 -> normal call */
 assert(!pal_stream_set_mute(h,true)&&muted);assert(!pal_stream_set_device(h,2,proxy)&&muted);
 assert(!pal_stream_set_device(h,2,normal)&&muted); /* preserve pre-existing user mute */
 assert(!pal_stream_close(h));
 h=open_pair(14,normal);assert(get(last)==0x19&&get(last+0x170)==2&&!muted);
 assert(!pal_stream_set_device(h,2,proxy)&&muted&&get(last+0x170)==0x10); /* mode2 ->4 */
 route_fail=1;assert(pal_stream_set_device(h,2,proxy)==-EIO);route_fail=0;
 int before=starts;assert(pal_stream_start(h)==-EIO&&starts==before);assert(!pal_stream_close(h));
 h=open_pair(14,proxy);mute_fail=1;before=starts;assert(pal_stream_start(h)==-EIO&&starts==before);mute_fail=0;assert(!pal_stream_close(h));
 h=open_pair(14,proxy);start_fail=1;assert(pal_stream_start(h)==-EIO);start_fail=0;assert(!pal_stream_close(h));
 mute_fail=1;uint32_t type=14;h=0;int cb=closes;assert(pal_stream_open(&type,2,proxy,0,0,0,123,&h)==-EIO&&!h&&closes==cb+1);mute_fail=0;
 h=open_pair(14,proxy);reset_start_mute=1;assert(!pal_stream_start(h)&&muted);reset_start_mute=0;
 reset_route_mute=1;assert(!pal_stream_set_device(h,2,proxy)&&muted);reset_route_mute=0;
 route_fail=1;assert(pal_stream_set_device(h,2,normal)==-EIO);route_fail=0;
 before=starts;assert(pal_stream_start(h)==-EIO&&starts==before&&stops>0);assert(!pal_stream_close(h));
 h=open_pair(7,proxy);assert(get(last)==0x25&&!muted);assert(!pal_stream_set_mute(h,false)&&!muted);assert(!pal_stream_close(h)); /* music unmodified */
 pair(proxy,0x10,0x25);h=open_pair(14,proxy);assert(get(last)==0x10&&get(last+0x170)==0x19&&muted);assert(!pal_stream_close(h));
 puts("PASS: TX-only mapping, original buffers, mute enforcement, in-place transitions, failure containment, normal/music passthrough, reversed devices");
}
