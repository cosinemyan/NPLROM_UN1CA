#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build the pinned ROM backend outside out/fw and out/target (JDK 21, NDK 28, Rust)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

from check_sources import current

ROOT = Path(__file__).resolve().parent


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def rust_environment():
    env = dict(os.environ)
    cargo_bin = Path(env.get("CARGO_HOME", str(Path.home() / ".cargo"))) / "bin"
    env["PATH"] = os.pathsep.join((env.get("PATH", ""), str(cargo_bin)))
    for command in ("cargo", "rustc", "cargo-ndk"):
        if not shutil.which(command, path=env["PATH"]):
            raise SystemExit(
                f"Missing Rust build dependency: {command}. Install Rust, "
                "the aarch64-linux-android target, and cargo-ndk; "
                "for a custom installation set CARGO_HOME and RUSTUP_HOME."
            )
    run("cargo", "--version", env=env)
    run("rustc", "--version", env=env)
    run("cargo", "ndk", "--version", env=env)
    target_libdir = subprocess.check_output(
        ["rustc", "--print", "target-libdir", "--target", "aarch64-linux-android"],
        env=env, text=True).strip()
    if not list(Path(target_libdir).glob("libstd-*.rlib")):
        raise SystemExit("Missing Rust Android target; run: rustup target add aarch64-linux-android")
    return env


def build(args):
    env = dict(os.environ) if args.java_only else rust_environment()
    work = Path(args.work_dir).resolve()
    if work == ROOT or ROOT in work.parents:
        raise SystemExit("Build directory must be outside the mod source")
    work.mkdir(parents=True, exist_ok=True)
    input_snapshot = current(ROOT)
    source = work / "source"
    shutil.copytree(ROOT / "source", source, dirs_exist_ok=True)
    lock = json.loads((ROOT / "upstream.lock.json").read_text())
    for dep in lock["dependencies"]:
        dest = source / dep["path"]
        if not (dest / ".git").exists():
            cached = Path(args.dependency_cache) / Path(dep["path"]).name if args.dependency_cache else None
            if cached and cached.is_dir():
                run("git", "clone", "--shared", cached, dest)
            else:
                dest.mkdir(parents=True, exist_ok=True)
                run("git", "init", dest)
                run("git", "-C", dest, "fetch", "--depth", "1", dep["url"], dep["revision"])
                run("git", "-C", dest, "checkout", "--detach", "FETCH_HEAD")
        revision = subprocess.check_output(["git", "-C", str(dest), "rev-parse", "HEAD"], text=True).strip()
        if revision != dep["revision"]:
            raise SystemExit(f"Wrong dependency revision: {dep['path']}")

    sdk = Path(args.sdk).resolve()
    android_jar = sdk / "platforms" / "android-37.0" / "android.jar"
    if not android_jar.exists():
        android_jar = sdk / "platforms" / "android-37" / "android.jar"
    if not android_jar.is_file():
        raise SystemExit("Android SDK 37 android.jar required")
    tools = sdk / "build-tools" / "37.0.0"
    java = work / "java"
    java.mkdir(exist_ok=True)
    stubs = java / "stubs"
    stubs.mkdir(exist_ok=True)
    run("javac", "--release", "17", "-classpath", android_jar, "-d", stubs,
        *sorted((source / "stub/src/main/java").rglob("*.java")))
    run("jar", "cf", java / "stubs.jar", "-C", stubs, ".")

    # Fixed versions; use the host Gradle cache when available, without starting Gradle.
    coordinates = {
        "compiler": ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.2.10"),
        "stdlib": ("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.10"),
        "script": ("org.jetbrains.kotlin", "kotlin-script-runtime", "2.2.10"),
        "reflect": ("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        "daemon": ("org.jetbrains.kotlin", "kotlin-daemon-embeddable", "2.2.10"),
        "coroutines": ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
        "annotations": ("org.jetbrains", "annotations", "13.0"),
        "bcpkix": ("org.bouncycastle", "bcpkix-jdk18on", "1.85"),
        "bcprov": ("org.bouncycastle", "bcprov-jdk18on", "1.85"),
        "bcutil": ("org.bouncycastle", "bcutil-jdk18on", "1.85"),
    }
    jars = {}
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    downloads = work / "jars"
    downloads.mkdir(exist_ok=True)
    for name, (group, artifact, version) in coordinates.items():
        filename = f"{artifact}-{version}.jar"
        matches = list((cache / group / artifact / version).glob(f"*/{filename}"))
        dest = downloads / filename
        if not dest.exists():
            if matches:
                shutil.copy2(matches[0], dest)
            else:
                url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}/{version}/{filename}"
                urllib.request.urlretrieve(url, dest)
        # Lock artifact bytes as well as versions; never trust a poisoned local cache silently.
        expected = json.loads((ROOT / "jvm-dependencies.sha256.json").read_text())[filename]
        if hashlib.sha256(dest.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"JVM dependency checksum mismatch: {filename}")
        jars[name] = dest
    compiler_cp = os.pathsep.join(str(jars[n]) for n in
        ("compiler", "stdlib", "script", "reflect", "daemon", "coroutines", "annotations"))
    runtime = [jars[n] for n in ("stdlib", "annotations", "bcpkix", "bcprov", "bcutil")]
    cp = os.pathsep.join(map(str, [java / "stubs.jar", android_jar, *runtime]))
    classes = java / "classes"
    classes.mkdir(exist_ok=True)
    run("java", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", cp,
        "-d", classes, *sorted((source / "app/src/main/java").rglob("*.kt")))
    run("jar", "cf", java / "daemon-classes.jar", "-C", classes, ".")
    dex = java / "dex"
    dex.mkdir(exist_ok=True)
    run(tools / "d8", "--release", "--min-api", "35", "--lib", android_jar,
        "--lib", java / "stubs.jar", "--output", dex, java / "daemon-classes.jar", *runtime)

    if not args.java_only:
        ndk = sdk / "ndk/28.2.13676358"
        env.update(ANDROID_HOME=str(sdk), LIBCLANG_PATH=args.libclang)
        run("cmake", "-S", source, "-B", work / "native", "-G", "Ninja",
            f"-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake",
            "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-35",
            "-DCMAKE_BUILD_TYPE=Release", env=env)
        run("cmake", "--build", work / "native", "--target", "inject", "teesim_keymint",
            "--parallel", args.jobs, env=env)
    else:
        print(f"Java-only validation succeeded: {dex}; no ROM payload published")
        return

    staging = Path(tempfile.mkdtemp(prefix="teesim-payload-", dir=work))
    framework = staging / "system/framework"
    framework.mkdir(parents=True)
    with zipfile.ZipFile(framework / "unica-teesim.jar", "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(dex.glob("classes*.dex")):
            z.write(f, f.name)
    for src, dest in ((work / "native/inject", staging / "system/bin/unica-teesim-inject"),
                      (work / "native/libteesim_keymint.so", staging / "system/lib64/libunica_teesim.so")):
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        run(ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip", "--strip-debug", dest)
    if current(ROOT) != input_snapshot:
        raise SystemExit("Backend inputs changed during build; refusing to publish stale payload")
    payload = ROOT / "payload"
    if payload.exists():
        backup = Path(tempfile.mkdtemp(prefix="unica-teesim-payload-backup-", dir="/tmp"))
        shutil.move(payload, backup / "payload")
        print(f"Previous payload preserved at {backup}")
    shutil.move(staging, payload)
    manifest = "".join(f"{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.relative_to(payload)}\n"
                       for f in sorted(payload.rglob("*")) if f.is_file())
    (ROOT / "payload.sha256").write_text(manifest)
    (ROOT / "source.sha256").write_text(input_snapshot)
    provenance = {
        "upstream": lock["revision"], "abi": "arm64-v8a", "native_api_floor": 35,
        "rom_sdk": 37, "ndk": "28.2.13676358",
        "rustc": subprocess.check_output(["rustc", "--version"], env=env, text=True).strip(),
        "source_manifest_sha256": hashlib.sha256((ROOT / "source.sha256").read_bytes()).hexdigest(),
    }
    (ROOT / "build-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"Built ROM payload: {payload}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", default="/tmp/unica-teesim-build")
    parser.add_argument("--dependency-cache")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
    parser.add_argument("--libclang", default="/usr/lib/llvm-18/lib")
    parser.add_argument("--jobs", default="4")
    parser.add_argument("--java-only", action="store_true")
    build(parser.parse_args())
