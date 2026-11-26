/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Read-only kernel codec-source verification. Never writes a mixer control.
 */
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#ifdef CS_TEST
extern void *cs_test_resolve(const char *);
#define RESOLVE(n) cs_test_resolve(n)
#else
#define RESOLVE(n) dlsym(lib,n)
#endif

int cs_sources_zero(void) {
#ifndef CS_TEST
    void *lib=dlopen("libtinyalsa.so",RTLD_NOW|RTLD_LOCAL);
    if(!lib)return -ENOSYS;
#endif
    void *(*open_card)(unsigned int)=RESOLVE("mixer_open");
    void (*close_card)(void *)=RESOLVE("mixer_close");
    const char *(*card_name)(void *)=RESOLVE("mixer_get_name");
    void *(*get_ctl)(void *,const char *)=RESOLVE("mixer_get_ctl_by_name");
    unsigned int (*num_values)(void *)=RESOLVE("mixer_ctl_get_num_values");
    unsigned int (*num_enums)(void *)=RESOLVE("mixer_ctl_get_num_enums");
    int (*get_value)(void *,unsigned int)=RESOLVE("mixer_ctl_get_value");
    const char *(*enum_name)(void *,unsigned int)=RESOLVE("mixer_ctl_get_enum_string");
    int rc=-ENOSYS;
    if(!open_card||!close_card||!card_name||!get_ctl||!num_values||!num_enums||!get_value||!enum_name)goto done;
    void *mixer=open_card(0);
    if(!mixer){rc=-ENODEV;goto done;}
    const char *name=card_name(mixer);
    rc=-ENODEV;
    if(!name||strcmp(name,"kalama-mtp-snd-card"))goto close;
    for(unsigned int bank=0;bank<2;bank++)for(unsigned int i=0;i<8;i++) {
        char control[32];
        snprintf(control,sizeof(control),"TX %s MUX%u",bank?"SMIC":"DMIC",i);
        void *ctl=get_ctl(mixer,control);
        rc=-ENODEV;
        if(!ctl||num_values(ctl)!=1)goto close;
        int selected=get_value(ctl,0);
        unsigned int count=num_enums(ctl);
        rc=-EIO;
        if(selected<0||(unsigned int)selected>=count)goto close;
        const char *value=enum_name(ctl,(unsigned int)selected);
        if(!value)goto close;
        rc=-EACCES;
        if(strcmp(value,"ZERO"))goto close;
    }
    rc=0;
close:
    close_card(mixer);
done:
#ifndef CS_TEST
    dlclose(lib);
#endif
    return rc;
}
