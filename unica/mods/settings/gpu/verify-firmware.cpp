// SPDX-License-Identifier: GPL-3.0-or-later
// Read-only build preflight using the same compatibility rules as the worker.
#include "native/image.h"
#include <fstream>
#include <iostream>
using namespace gpu;
extern "C" void* avb_memcpy(void* a,const void* b,size_t n){return memcpy(a,b,n);}
extern "C" void* avb_memset(void* a,int v,size_t n){return memset(a,v,n);}
static Bytes load(const char* path,size_t limit){
 std::ifstream in(path,std::ios::binary);require(in.good(),std::string("Cannot open ")+path);
 in.seekg(0,std::ios::end);auto size=in.tellg();require(size>0&&uint64_t(size)<=limit,"Invalid firmware file size");in.seekg(0);
 Bytes b(size);in.read(reinterpret_cast<char*>(b.data()),b.size());require(in.good(),"Cannot read firmware file");return b;
}
int main(int argc,char** argv){try{
 require(argc==3,"Usage: verify-firmware vendor_boot.img msm_kgsl.ko");
 auto image=load(argv[1],IMAGE_SIZE); // Build inputs may have their AVB footer stripped.
 image.resize(IMAGE_SIZE,0);table(image);
 require(stockTables(image),"Build vendor_boot must contain the stock GPU tables; do not ship a personal tuning profile");
 verifyDriver(load(argv[2],8*1024*1024));
 std::cout<<"GPU compatibility PASS: Kalama v2 / Gen7-2-1 table-handling capabilities and stock baseline\n";return 0;
}catch(const std::exception& e){std::cerr<<"GPU compatibility preflight: "<<e.what()<<'\n';return 1;}}
