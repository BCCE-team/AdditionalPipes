#!/usr/bin/env python3
"""Post-build validation of an Additional Pipes target jar (no BC classes bundled)."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import tomllib
import zipfile

from materialize_sources import config


def verify(target: str, path: Path):
    p = config()
    assert target in p['targets'].split(','), f'unknown target {target}'
    loader=p[f'target.{target}.source.platform']
    with zipfile.ZipFile(path) as z:
        names=set(z.namelist())
        assert 'buildcraft/additionalpipes/AdditionalPipes.class' in names, 'AP entrypoint missing'
        assert not any(n.startswith('buildcraft/api/') for n in names), 'BCCE API was improperly embedded'
        assert not any(n.startswith('buildcraft/transport/') for n in names), 'BCCE implementation was improperly embedded'
        must='buildcraft/additionalpipes/platform/AdditionalPipesForge.class' if loader=='forge' else 'buildcraft/additionalpipes/platform/AdditionalPipesNeoForge.class'
        assert must in names, f'{target}: expected {must}'
        if loader=='forge':
            assert 'buildcraft/additionalpipes/platform/AdditionalPipesNeoForge.class' not in names
        else:
            assert 'buildcraft/additionalpipes/platform/AdditionalPipesForge.class' not in names
        meta='META-INF/mods.toml' if loader=='forge' else 'META-INF/neoforge.mods.toml'
        metadata=z.read(meta).decode('utf-8')
        assert '${' not in metadata, 'unexpanded metadata template'
        props=tomllib.loads(metadata)
        deps={d['modId'] for d in props['dependencies']['additionalpipes']}
        assert {'minecraft','buildcraftlib','buildcrafttransport',loader}==deps, deps
        assert props['mods'][0]['modId']=='additionalpipes'
        pack=z.read('pack.mcmeta').decode('utf-8')
        assert '${' not in pack
        json.loads(pack)
    print(f'OK: {path}: {target}, AP-only mod; loader and BuildCraft dependencies present')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target')
    parser.add_argument('jar',type=Path)
    args=parser.parse_args()
    try:
        verify(args.target,args.jar)
    except (OSError, KeyError, ValueError, AssertionError, zipfile.BadZipFile) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0

if __name__=='__main__':
    raise SystemExit(main())
