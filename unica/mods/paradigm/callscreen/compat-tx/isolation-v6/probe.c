/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <stdio.h>
int cs_sources_zero(void);
int main(void){int rc=cs_sources_zero();printf("Kernel codec-source readback: rc=%d (0 means all 16 muxes ZERO)\n",rc);return rc?1:0;}
