#!/usr/bin/env python3
"""Regenerate Chronio brand assets in place, matching each existing file's pixel size.

Usage: branding/generate.py <repo-root>   (works for the NuvioTV and NuvioDesktop forks)
"""
import pathlib, re, subprocess, sys, tempfile

root = pathlib.Path(sys.argv[1]).resolve()
here = pathlib.Path(__file__).resolve().parent
work = pathlib.Path(tempfile.mkdtemp())
renderer = work / 'chronio_brand'
subprocess.run(['swiftc', '-O', '-o', str(renderer), str(here / 'chronio_brand.swift')], check=True)

PALETTES = ['arctic_blue', 'rose_gold', 'emerald', 'copper', 'graphite', 'gold', 'jade', 'original']

def palette(stem):
    return next((p for p in PALETTES if stem.endswith(p) or f'-{p}-' in stem or f'_{p}_' in stem), 'original')

def size(path):
    out = subprocess.run(['sips', '-g', 'pixelWidth', '-g', 'pixelHeight', str(path)], capture_output=True, text=True, check=True).stdout
    return int(re.search(r'pixelWidth: (\d+)', out)[1]), int(re.search(r'pixelHeight: (\d+)', out)[1])

def render(kind, pal, w, h, out):
    subprocess.run([str(renderer), kind, pal, str(w), str(h), str(out)], check=True)

def icns(pal, out):
    iconset = work / f'{out.stem}.iconset'
    iconset.mkdir(exist_ok=True)
    for base in (16, 32, 128, 256, 512):
        render('appicon', pal, base, base, iconset / f'icon_{base}x{base}.png')
        render('appicon', pal, base * 2, base * 2, iconset / f'icon_{base}x{base}@2x.png')
    subprocess.run(['iconutil', '-c', 'icns', str(iconset), '-o', str(out)], check=True)

RULES = [  # (glob, kind)
    ('app/src/main/res/mipmap-*/ic_launcher*.png', 'icon'),
    ('app/src/main/res/mipmap-*/banner*.png', 'banner'),
    ('app/src/main/res/drawable*/app_logo_mark.png', 'icon'),
    ('app/src/main/res/drawable*/tv_banner.png', 'banner'),
    ('app/src/main/res/drawable*/app_logo_wordmark*.png', 'wordmark'),
    ('app/src/main/res/drawable*/nuvio_text.png', 'text'),
    ('composeApp/src/commonMain/composeResources/drawable/app_icon_*_transparent.png', 'mark'),
    ('composeApp/src/commonMain/composeResources/drawable/app_icon_*.png', 'appicon'),
    ('composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark*.png', 'wordmark'),
    ('composeApp/src/desktopMain/resources/icons/*.png', 'appicon'),
]
done = set()
for pattern, kind in RULES:
    for path in sorted(root.glob(pattern)):
        if path in done:
            continue
        done.add(path)
        w, h = size(path)
        render(kind, palette(path.stem), w, h, path)
        print(f'{kind:8} {palette(path.stem):11} {w}x{h} {path.relative_to(root)}')
for path in sorted(root.glob('composeApp/src/desktopMain/resources/icons/*.icns')):
    icns(palette(path.stem), path)
    print(f'icns     {palette(path.stem):11} {path.relative_to(root)}')
