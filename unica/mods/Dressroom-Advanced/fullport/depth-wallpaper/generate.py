#!/usr/bin/env python3
"""Generate explicit native depth callsites as a patch; never edits decoded APK inputs."""
from pathlib import Path
import argparse
import difflib
import re

BASE = 'Ldev/denver/dressroomadvanced/'

def unique(root, suffix):
    found = list(root.glob('smali*/' + suffix))
    if len(found) != 1:
        raise ValueError(f'Expected one {suffix}; found {len(found)}')
    return found[0]

def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError(f'Anchor must occur once ({text.count(before)}): {before[:100]!r}')
    return text.replace(before, after, 1)

def method(text, descriptor, transform):
    pattern = re.compile(r'^\.method [^\n]*\b' + re.escape(descriptor) + r'\n.*?^\.end method', re.M | re.S)
    matches = list(pattern.finditer(text))
    if len(matches) != 1: raise ValueError(f'Method missing/ambiguous: {descriptor}')
    m = matches[0]
    return text[:m.start()] + transform(m.group()) + text[m.end():]

def entry(body, code):
    match = re.search(r'^    \.(?:locals|registers) \d+\n', body, re.M)
    if not match: raise ValueError('No register declaration')
    return body[:match.end()] + '\n' + code + '\n' + body[match.end():]

def sdk_wrap(text, operation, proto):
    old = f'.method public {operation}{proto}\n'
    renamed = 'nativeDepthOriginal' + operation[0].upper() + operation[1:]
    text = replace_once(text, old, f'.method public {renamed}{proto}\n')
    if operation == 'capture':
        wrapper = f'''\n.method public capture{proto}
    .locals 1
    invoke-static {{p0, p1}}, {BASE}NativeObjectCapture;->capture(Ljava/lang/Object;Landroid/graphics/Bitmap;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/samsung/android/app/sdk/deepsky/objectcapture/base/ObjectResult;
    return-object v0
.end method
'''
    else:
        wrapper = f'''\n.method public {operation}{proto}
    .locals 0
    invoke-static {{p0}}, {BASE}NativeObjectCapture;->{operation}(Ljava/lang/Object;)V
    return-void
.end method
'''
    return text + wrapper

def blur_submissions(s, expected, suffix):
    pattern = re.compile(r'    invoke-virtual \{([vp]\d+), ([vp]\d+)\}, Landroid/view/View;->semSetBlurInfo\(Landroid/view/SemBlurInfo;\)V')
    if len(pattern.findall(s)) != expected: raise ValueError(f'Blur callsite count changed in {suffix}')
    owner = re.search(r'^\.class .* (L[^;]+;)$', s, re.M)[1]
    helper = 'nativeDepthSetBlurInfo(Landroid/view/View;Landroid/view/SemBlurInfo;)V'
    if helper in s: raise ValueError(f'Blur helper already exists in {suffix}')
    # The caller's blur argument may be a live null used by throw or
    # catch handlers. Keep every caller register unchanged, including
    # on exceptional edges, by isolating conversion in a new method.
    return pattern.sub(lambda m: f'    invoke-static {{{m[1]}, {m[2]}}}, {owner}->{helper}', s) + f'''
.method private static {helper}
    .locals 1

    invoke-static {{p0, p1}}, {BASE}NativeDepth;->widgetBlur(Landroid/view/View;Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/view/SemBlurInfo;

    invoke-virtual {{p0, v0}}, Landroid/view/View;->semSetBlurInfo(Landroid/view/SemBlurInfo;)V

    return-void
.end method
'''

def depth_draw_methods(text):
    for name in ('draw', 'dispatchDraw'):
        if re.search(r'\.method .* ' + name + r'\(Landroid/graphics/Canvas;\)V', text):
            raise ValueError(f'AbsClockView now overrides {name}; reassess before integrating')
    for name, access in [('draw', 'public'), ('dispatchDraw', 'protected')]:
        text += f'''
# Both normal drawing and ViewGroup SKIP_DRAW recording must apply depth.
.method {access} {name}(Landroid/graphics/Canvas;)V
    .locals 2
    invoke-static {{p0, p1}}, {BASE}NativeDepth;->beforeDraw(Landroid/view/View;Landroid/graphics/Canvas;)I
    move-result v0
    :try_start_native_depth
    invoke-super {{p0, p1}}, Lcom/samsung/android/clockpack/plugins/clock/ClockView;->{name}(Landroid/graphics/Canvas;)V
    :try_end_native_depth
    .catchall {{:try_start_native_depth .. :try_end_native_depth}} :catch_native_depth
    invoke-static {{p0, p1, v0}}, {BASE}NativeDepth;->afterDraw(Landroid/view/View;Landroid/graphics/Canvas;I)V
    invoke-static {{p0}}, {BASE}NativeClockRuntime;->drawn(Landroid/view/View;)V
    return-void
    :catch_native_depth
    move-exception v1
    invoke-static {{p0, p1, v0}}, {BASE}NativeDepth;->afterDraw(Landroid/view/View;Landroid/graphics/Canvas;I)V
    throw v1
.end method
'''
    return text

def generate(root, kind):
    changes = {}
    def edit(suffix, fn):
        p = unique(root, suffix)
        old = changes.get(p, (p.read_text(), p.read_text()))[0]
        current = changes.get(p, (old, old))[1]
        new = fn(current)
        if new == current: raise ValueError(f'No change: {p}')
        changes[p] = (old, new)
    if kind == 'dressroom':
        crop = 'com/samsung/android/app/dressroom/presentation/preview/widget/CropImageView.smali'
        edit(crop, lambda s: method(s, 'onDraw(Landroid/graphics/Canvas;)V', lambda b: entry(b,
            f'    invoke-static/range {{p0 .. p0}}, {BASE}NativeDepth;->editorWallpaperDraw(Landroid/view/View;)V')))
        weather = 'com/samsung/android/app/dressroom/editorcomponent/component/wallpaper/image/effects/weather/WeatherSurfaceView.smali'
        for signature in ['surfaceCreated(Landroid/view/SurfaceHolder;)V', 'surfaceChanged(Landroid/view/SurfaceHolder;III)V']:
            edit(weather, lambda s, sig=signature: method(s, sig, lambda b: entry(b,
                f'    invoke-static/range {{p0 .. p0}}, {BASE}NativeDepth;->weatherSource(Landroid/view/View;)V')))
        edit('i7/g.smali', lambda s: method(s, 'e()V', lambda b: entry(b,
            f'    invoke-static/range {{p0 .. p0}}, {BASE}NativeDepth;->weatherOwner(Ljava/lang/Object;)V')))
        for name in ['clock_item_layout.xml', 'clock_thumbnail_item_layout.xml', 'widget_item_layout_v2.xml']:
            p = root/'res/layout'/name
            old = p.read_text()
            if not old.splitlines()[1].startswith('<FrameLayout '): raise ValueError('Editor root changed')
            new = old.replace('<FrameLayout ', '<dev.denver.dressroomadvanced.DepthFrameLayout ', 1)
            if '</FrameLayout>' in new:
                head, tail = new.rsplit('</FrameLayout>', 1)
                new = head + '</dev.denver.dressroomadvanced.DepthFrameLayout>' + tail
            changes[p] = (old, new)
        for backend in ['ArcSoftObjectCaptureImpl', 'SribObjectCaptureImpl']:
            suffix = 'com/samsung/android/app/sdk/deepsky/objectcapture/impl/' + backend + '.smali'
            for op, proto in [('init','()V'),('release','()V'),('capture','(Landroid/graphics/Bitmap;)Lcom/samsung/android/app/sdk/deepsky/objectcapture/base/ObjectResult;')]:
                edit(suffix, lambda s, op=op, proto=proto: sdk_wrap(s, op, proto))
    elif kind == 'aod':
        absclock = 'com/samsung/android/uniform/widget/clock/AbsClockView.smali'
        edit(absclock, depth_draw_methods)
        anchor = '    invoke-static {v7, v13}, Laod/e70;->a(Landroid/view/SemBlurInfo$Builder;Landroid/graphics/Path;)V'
        # v1's iterator/weather liveness ends here; next use is an unconditional overwrite.
        # p0 is unchanged in b(), although its register is >15; move-object/from16 is required.
        edit('aod/tx0.smali', lambda s: replace_once(s, anchor,
            f'    move-object/from16 v1, p0\n\n    invoke-static {{v1, v13}}, {BASE}NativeDepth;->blurPath(Ljava/lang/Object;Landroid/graphics/Path;)Landroid/graphics/Path;\n\n    move-result-object v13\n\n'+anchor))
        edit('aod/tx0.smali', lambda s: method(s,'c()V', lambda b: entry(b,
            f'    invoke-static {{p0}}, {BASE}NativeDepth;->clearBlur(Ljava/lang/Object;)V')))
        for suffix, expected in [('aod/e70.smali',3),('com/samsung/android/app/facewidget/complication/ComplicationWidgetHostViewContainer.smali',2),('com/samsung/android/app/facewidget/complication/BlurEffectManager.smali',1)]:
            edit(suffix, lambda s, expected=expected: blur_submissions(s, expected, suffix))
    elif kind == 'wallpaper':
        def engine(s):
            def transform(b):
                b = entry(b, f'    invoke-static/range {{p0 .. p0}}, {BASE}NativeWallpaper;->begin(Ljava/lang/Object;)V')
                # No new locals or altered Samsung catch regions.
                b = b.replace('    return-void', f'    invoke-static {{}}, {BASE}NativeWallpaper;->end()V\n\n    return-void')
                b = re.sub(r'^(    throw [vp]\d+)$', f'    invoke-static {{}}, {BASE}NativeWallpaper;->end()V\n\n\\1', b, flags=re.M)
                return b
            return method(s,'b(Landroid/view/SurfaceHolder;)V',transform)
        edit('f3/a.smali', engine)
        anchor = '    invoke-virtual {v0, v3}, Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V'
        edit('i3/c.smali',lambda s: replace_once(s,anchor,
            f'    invoke-static {{v3}}, {BASE}NativeWallpaper;->bitmap(Landroid/graphics/Canvas;)V\n\n'+anchor))
        endpoint = 'com/samsung/android/wallpaper/live/sdk/service/switchable/SwitchableWallpaperService$DelegatorEngine.smali'
        def command(b):
            return entry(b,f'''    invoke-static {{p1, p5}}, {BASE}NativeWallpaper;->command(Ljava/lang/String;Landroid/os/Bundle;)Z
    move-result v0
    if-eqz v0, :native_depth_command_continue
    const/4 v0, 0x0
    return-object v0
    :native_depth_command_continue''')
        edit(endpoint,lambda s: method(s,'onCommand(Ljava/lang/String;IIILandroid/os/Bundle;Z)Landroid/os/Bundle;',command))
    else: raise ValueError(kind)
    return ''.join(''.join(difflib.unified_diff(a.splitlines(True),b.splitlines(True),fromfile='a/'+str(p.relative_to(root)),tofile='b/'+str(p.relative_to(root)))) for p,(a,b) in sorted(changes.items()))

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--kind',required=True,choices=['dressroom','aod','wallpaper'])
    ap.add_argument('--decoded',required=True,type=Path)
    ap.add_argument('--output',required=True,type=Path)
    args=ap.parse_args()
    patch=generate(args.decoded,args.kind)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('x') as stream: stream.write(patch)
    print(f'{args.kind}: generated {args.output} ({len(patch)} bytes)')
