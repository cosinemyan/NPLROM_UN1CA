// SPDX-License-Identifier: GPL-3.0-or-later
#include "hold.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <time.h>
#include <unistd.h>

static int64_t milliseconds() {
    timespec t{};
    clock_gettime(CLOCK_MONOTONIC, &t);
    return int64_t(t.tv_sec) * 1000 + t.tv_nsec / 1000000;
}
static void report(const char* text) {
    fprintf(stderr, "UN1CA_VOLUMEUP: %s\n", text);
    int fd = open("/dev/kmsg", O_WRONLY | O_CLOEXEC | O_NONBLOCK);
    if (fd >= 0) {
        char line[256];
        int count = snprintf(line, sizeof(line), "<6>UN1CA_VOLUMEUP: %s\n", text);
        if (count > 0 && count < int(sizeof(line))) (void)write(fd, line, count);
        close(fd);
    }
}
static bool bit(const unsigned char* bits, int code) {
    return bits[code / 8] & (1U << (code % 8));
}
struct Device { int fd = -1; Hold hold; };
int main() {
    // Fixed recovery request only; no arguments, shell or writable configuration.
    struct stat disabled{};
    if (lstat("/metadata/unica-volumeup.disabled", &disabled) == 0) {
        report("DISABLED by /metadata/unica-volumeup.disabled");
        return 0;
    }
    if (errno != ENOENT) {
        report("ERROR cannot check disable marker; continuing normal boot");
        return 1;
    }
    report("START recovery-enabled window=7000ms hold=600ms");
    const int64_t start = milliseconds();
    Device devices[32];
    int count = 0;
    int64_t scan = -100;
    bool found = false;
    while (milliseconds() - start < 7000) {
        int64_t now = milliseconds();
        // Retry discovery: ueventd may still be publishing input nodes.
        if (now - scan >= 100) {
            scan = now;
            DIR* dir = opendir("/dev/input");
            if (dir) {
                while (dirent* e = readdir(dir)) {
                    if (strncmp(e->d_name, "event", 5) || count == 32) continue;
                    char path[300];
                    snprintf(path, sizeof(path), "/dev/input/%s", e->d_name);
                    int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW);
                    if (fd < 0) continue;
                    // Compare device numbers; event indices are not stable across boots.
                    input_id id{};
                    unsigned char keys[(KEY_MAX + 8) / 8]{};
                    bool duplicate = false;
                    struct stat st{}, other{};
                    if (fstat(fd, &st)) { close(fd); continue; }
                    for (int i = 0; i < count; ++i)
                        if (!fstat(devices[i].fd, &other) && st.st_rdev == other.st_rdev)
                            duplicate = true;
                    if (!duplicate && !ioctl(fd, EVIOCGID, &id) &&
                        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0 &&
                        bit(keys, KEY_VOLUMEUP)) {
                        devices[count++].fd = fd;
                        found = true;
                        report("Volume Up capable input discovered");
                    } else close(fd);
                }
                closedir(dir);
            }
        }
        for (int i = 0; i < count; ++i) {
            unsigned char keys[(KEY_MAX + 8) / 8]{};
            // Snapshot handles a button already held when the service starts.
            bool down = ioctl(devices[i].fd, EVIOCGKEY(sizeof(keys)), keys) >= 0 &&
                        bit(keys, KEY_VOLUMEUP);
            if (devices[i].hold.update(down, milliseconds())) {
                report("DETECTED hold>=600ms; requesting recovery reboot");
                for (int j = 0; j < count; ++j) close(devices[j].fd);
                // Init owns the reboot operation; this domain cannot set sys.powerctl.
                if (__system_property_set("sys.unica.volumeup.recovery", "1") != 0) {
                    report("ERROR recovery request rejected; continuing normal boot");
                    return 1;
                }
                report("Recovery request accepted by property service");
                return 0;
            }
        }
        timespec delay{0, 20000000};
        nanosleep(&delay, nullptr);
    }
    for (int i = 0; i < count; ++i) close(devices[i].fd);
    report(found ? "TIMEOUT no qualifying hold" : "TIMEOUT no accessible Volume Up input");
    return 0;
}
