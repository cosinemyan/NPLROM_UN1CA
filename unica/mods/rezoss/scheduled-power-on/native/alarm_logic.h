// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <array>
#include <cstdint>
#include <cstdio>
#include <ctime>
#include <stdexcept>

namespace poweron {
// Exact stock sec_pon_alarm ABI: _IOW('a', 7, char[14]).
constexpr unsigned long setAlarmIoctl = 0x400e6107UL;
constexpr int64_t maxDelay = 7 * 24 * 60 * 60;
constexpr int64_t cancelledRtc = 1580518864; // 2020-02-01 01:01:04 UTC
inline void validate(int64_t target, int64_t now) {
    if (target < 1577836800 || target > 4102444740LL)
        throw std::runtime_error("The selected date is outside the supported range.");
    if (now < 1577836800 || now > 4102444740LL)
        throw std::runtime_error("Correct the phone's date and time before scheduling.");
    if (target % 60 || target - now < 120 || target - now > maxDelay)
        throw std::runtime_error("Choose a time at least 2 minutes and at most 7 days ahead.");
}
inline std::array<char, 14> payload(int64_t target) {
    std::array<char, 14> out{};
    if (!target) {
        // Samsung's disable command; the driver writes its reserved RTC date.
        std::snprintf(out.data(), out.size(), "0202002010101");
        return out;
    }
    time_t t = static_cast<time_t>(target);
    tm utc{};
    if (!gmtime_r(&t, &utc) || utc.tm_year < 120 || utc.tm_year > 199)
        throw std::runtime_error("Cannot encode the selected UTC date.");
    std::snprintf(out.data(), out.size(), "1%04d%02d%02d%02d%02d",
                  utc.tm_year + 1900, utc.tm_mon + 1, utc.tm_mday, utc.tm_hour, utc.tm_min);
    return out;
}
inline int64_t expectedRtc(int64_t target, int64_t wall, int64_t rtc) {
    // The driver does this conversion, then reserves the low two bits for mode.
    return (target - (wall - rtc)) & ~int64_t(3);
}
}
