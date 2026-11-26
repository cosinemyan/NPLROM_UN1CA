// SPDX-License-Identifier: GPL-3.0-or-later
#include "alarm_logic.h"
#include <jni.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <linux/rtc.h>
#include <string>
#include <sys/ioctl.h>
#include <unistd.h>

namespace {
struct Fd {
    int value;
    Fd(const char* path, int mode): value(open(path, mode | O_CLOEXEC | O_NOFOLLOW)) {
        if (value < 0) throw std::runtime_error(std::string("Cannot open ") + path + ": " + strerror(errno));
    }
    ~Fd() { close(value); }
};
int64_t epoch(const rtc_time& r) {
    tm t{};
    t.tm_sec=r.tm_sec; t.tm_min=r.tm_min; t.tm_hour=r.tm_hour;
    t.tm_mday=r.tm_mday; t.tm_mon=r.tm_mon; t.tm_year=r.tm_year;
    return timegm(&t);
}
int64_t readTime(int fd) {
    rtc_time rtc{};
    if (ioctl(fd, RTC_RD_TIME, &rtc)) throw std::runtime_error("Cannot read hardware RTC.");
    return epoch(rtc);
}
void fail(JNIEnv* env, const std::exception& e) {
    env->ThrowNew(env->FindClass("java/io/IOException"), e.what());
}
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_io_mesalabs_rezoss_poweron_AlarmHardware_snapshot(JNIEnv* env, jclass) {
    try {
        Fd rtc("/dev/rtc0", O_RDONLY);
        int64_t hardware = readTime(rtc.value);
        jlong values[] = {time(nullptr), hardware};
        jlongArray result = env->NewLongArray(2);
        if (result) env->SetLongArrayRegion(result, 0, 2, values);
        return result;
    } catch (const std::exception& e) { fail(env,e); return nullptr; }
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_mesalabs_rezoss_poweron_AlarmHardware_program(JNIEnv* env, jclass, jlong target) {
    try {
        int64_t wall = time(nullptr);
        if (target) poweron::validate(target, wall);
        Fd rtc("/dev/rtc0", O_RDONLY);
        int64_t before = readTime(rtc.value);
        int64_t expected = target ? poweron::expectedRtc(target, wall, before) : poweron::cancelledRtc;
        // Reject unexpected RTC rollover/overflow rather than arming the wrong date.
        if (target && (expected < before + 116 || expected > UINT32_MAX))
            throw std::runtime_error("Hardware RTC cannot represent this schedule.");
        auto command = poweron::payload(target);
        Fd pon("/dev/power_on_alarm", O_WRONLY);
        if (ioctl(pon.value, poweron::setAlarmIoctl, command.data()))
            throw std::runtime_error("Samsung power-on alarm command failed.");
        // pmic_rtc_setalarm only updates pwron_alarm in memory. The RTC shutdown
        // callback applies it later. RTC_WKALM_RD reads a different live alarm;
        // it CANNOT verify this pending power-on schedule or sec_param persistence.
        return expected;
    } catch (const std::exception& e) { fail(env,e); return -1; }
}
