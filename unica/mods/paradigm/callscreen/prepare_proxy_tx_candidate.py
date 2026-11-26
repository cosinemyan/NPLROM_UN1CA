#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

"""Reproduce a FAILED dm3q proxy-TX ACDB experiment for forensic comparison.

Live test on 2026-10-28 retained graph-open status 24 and caller silence.
DO NOT DEPLOY OR INTEGRATE THIS EXPERIMENT AS A REPAIR.

Not called by customize.sh. Never edits its input or deploys to a device.
The recorded MODE_AUDIO_PROCESSING call opens proxy RX before proxy TX.
Both graph blobs declare the same four RX/TX control links. Status 24 from
the TX graph open is AR_EDUPLICATE; this candidate tests whether retaining
those links in RX alone resolves that failure. It is not a proven repair.

Only the TX control-link count changes (5 -> 1). Its unique first record is
retained. The four trailing records stay byte-for-byte in the parameter's
allocated payload but are outside its declared record count. AudioReach's
APM parser accepts payload sizes greater than the declared records require.
All chunk sizes, offsets, calibration, modules and audio data links remain.
The TX graph data offset must be exclusive to this exact graph key vector.
"""

import argparse
import hashlib
import json
from pathlib import Path
import struct


SOURCE_SHA256 = "0de5793f2dd2cea17642427922bbf5be18cf960b67cda2ce7ee162df9be72684"
TX_KEYS = {0xA3000000: 0xA3000008, 0xB1000000: 0xB1000007,
           0xB3000000: 0xB3000001}
RX_KEYS = {0xA1000000: 0xA1000010, 0xA2000000: 0xA2000007,
           0xAC000000: 0xAC000005, 0xAF000000: 0xAF000001,
           0xB3000000: 0xB3000001}
CTRL_LINKS = 0x08001061


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Acdb:
    def __init__(self, data):
        self.data = data
        require(data[:4] == b"ACDB", "Not an ACDB file")
        self.chunks = {}
        pos = 12
        while pos < len(data):
            tag, size = struct.unpack_from("<4sI", data, pos)
            require(pos + 8 + size <= len(data), "Chunk exceeds file")
            require(tag not in self.chunks, "Duplicate chunk")
            self.chunks[tag] = (pos + 8, size)
            pos += 8 + size
        require(pos == len(data), "Trailing/incomplete chunk")
        pos = self.chunks[b"GKVT"][0]
        tables = self.words(pos)[0]
        pos += 4
        self.graphs = []
        for _ in range(tables):
            count, rows = self.words(pos, 2)
            pos += 8
            for _ in range(rows):
                row = self.words(pos, count + 1)
                pos += 4 * (count + 1)
                keys = row[:-1]
                lookup = self.chunks[b"GKVL"][0] + row[-1]
                width, entries = self.words(lookup, 2)
                require(width == count, "Inconsistent GKV width")
                lookup += 8
                for _ in range(entries):
                    values = self.words(lookup, count + 2)
                    lookup += 4 * (count + 2)
                    self.graphs.append((dict(zip(keys, values[:-2])), values[-1]))

    def words(self, pos, count=1):
        return struct.unpack_from("<" + "I" * count, self.data, pos)

    def graph(self, keys):
        matches = [g for g in self.graphs if g[0] == keys]
        require(len(matches) == 1, "Graph key vector is missing/ambiguous")
        return matches[0]

    def control_links(self, graph, wanted_sg):
        pos = self.chunks[b"POOL"][0] + graph[1]
        _, subgraphs = self.words(pos, 2)
        pos += 8
        for _ in range(subgraphs):
            sg, size = self.words(pos, 2)
            pos += 8
            end = pos + size
            driver_size = self.words(pos)[0]
            pos += 4 + driver_size
            spf_size = self.words(pos)[0]
            pos += 4
            spf_end = pos + spf_size
            require(spf_end == end, "Unexpected subgraph layout")
            while pos < spf_end:
                _, pid, length = self.words(pos, 3)
                payload = pos + 12
                next_prop = payload + ((length + 7) // 8) * 8
                require(next_prop <= spf_end, "SPF parameter exceeds subgraph")
                if sg == wanted_sg and pid == CTRL_LINKS:
                    count = self.words(payload)[0]
                    cursor = payload + 4
                    records = []
                    for _ in range(count):
                        start = cursor
                        *_, properties = self.words(cursor, 5)
                        cursor += 20
                        for _ in range(properties):
                            _, prop_size = self.words(cursor, 2)
                            cursor += 8 + prop_size
                        require(cursor <= payload + length, "Control record exceeds payload")
                        records.append(self.data[start:cursor])
                    return payload, records
                pos = next_prop
            pos = end
        raise ValueError("Control-link parameter not found")


def candidate(data):
    require(hashlib.sha256(data).hexdigest() == SOURCE_SHA256,
            "Unsupported firmware or already modified input")
    db = Acdb(data)
    tx = db.graph(TX_KEYS)
    rx = db.graph(RX_KEYS)
    require(sum(g[1] == tx[1] for g in db.graphs) == 1,
            "TX data block is shared with another graph")
    offset, tx_links = db.control_links(tx, 0xB000003F)
    _, rx_links = db.control_links(rx, 0xB0000040)
    require(len(tx_links) == 5 and len(rx_links) == 4,
            "Unexpected control-link counts")
    require(tx_links[1:] == rx_links, "Trailing TX links do not match RX")
    require(struct.unpack_from("<4I", tx_links[0]) ==
            (0x4654, 0xC0000003, 0x41B0, 0x80000002), "Unique TX link changed")
    output = bytearray(data)
    struct.pack_into("<I", output, offset, 1)
    check = Acdb(output)
    _, remaining = check.control_links(check.graph(TX_KEYS), 0xB000003F)
    _, preserved = check.control_links(check.graph(RX_KEYS), 0xB0000040)
    require(remaining == tx_links[:1] and preserved == rx_links, "Post-edit verification failed")
    require(check.graphs == db.graphs, "Graph lookup tables changed")
    require([i for i, (a, b) in enumerate(zip(data, output)) if a != b] == [offset],
            "Unexpected byte changes")
    return bytes(output), {
        "status": "FAILED live test 2026-10-28; forensic reproduction only; do not deploy",
        "source_sha256": SOURCE_SHA256,
        "candidate_sha256": hashlib.sha256(output).hexdigest(),
        "changed_byte_offset": hex(offset),
        "declared_tx_control_links": "5 -> 1",
        "rx_control_links_preserved": len(rx_links),
        "graph_vectors_preserved": len(db.graphs),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path, help="New file under /tmp; never overwritten")
    args = parser.parse_args()
    output, report = candidate(args.input.read_bytes())
    if args.output:
        destination = args.output.resolve()
        require(destination.is_relative_to(Path("/tmp")), "Candidate output must be under /tmp")
        with destination.open("xb") as stream:
            stream.write(output)
        report["output"] = str(destination)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
