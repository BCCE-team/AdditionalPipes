#!/usr/bin/env python3
"""Verify the seven AP+BCCE targets, source layers, and loader metadata.

This check needs Python 3.11+ but no Minecraft/Gradle downloads.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import tempfile
import tomllib

from materialize_sources import ROOT, config, materialize

EXPECTED = {
    '1.19.2-forge': ('old', 'forge', 17),
    '1.20.1-forge': ('old', 'forge', 17),
    '1.21.1-neoforge': ('1.21.X', 'neoforge', 21),
    '1.21.11-neoforge': ('1.21.X', 'neoforge', 21),
    '26.1.2-neoforge': ('26.X', 'neoforge', 25),
    '26.2-neoforge': ('26.X', 'neoforge', 25),
    '26.3-neoforge': ('26.X', 'neoforge', 25),
}


def check() -> None:
    p = config()
    assert list(EXPECTED) == p['targets'].split(','), 'Target registry differs from BCCE 8.0.24 snapshot'
    assert p['common.bcce.version'] == '8.0.24'
    for target, (generation, loader, java) in EXPECTED.items():
        prefix = f'target.{target}.'
        assert p[prefix+'build.generation'] == generation, target
        assert p[prefix+'source.family'] == generation, target
        assert p[prefix+'source.platform'] == loader, target
        assert int(p[prefix+'java.version']) == java, target
        assert p[prefix+'deps.minecraft'] == target.split('-')[0], target
        if loader == 'forge':
            assert p[prefix+'deps.forge']
        else:
            assert p[prefix+'deps.neoforge']
        root = ROOT/'builds'/generation
        assert (root/'settings.gradle.kts').is_file()
        assert (root/'stonecutter.gradle.kts').is_file()
        assert (root/'gradle/wrapper/gradle-wrapper.jar').is_file()
        assert (root/f'build.{loader}.gradle').is_file()

    with tempfile.TemporaryDirectory(prefix='ap-bcce-layout-') as tmp:
        for target, (generation, loader, java) in EXPECTED.items():
            report = materialize(target, Path(tmp)/target)
            layout = Path(tmp)/target/'src/main'
            java_files = {f.name for f in (layout/'java').rglob('*.java')}
            assert 'AdditionalPipes.java' in java_files, target
            forge = 'AdditionalPipesForge.java' in java_files
            neo = 'AdditionalPipesNeoForge.java' in java_files
            assert forge != neo, f'{target}: wrong or mixed loader bootstrap'
            assert forge == (loader == 'forge'), target
            metadata_name = 'mods.toml' if loader == 'forge' else 'neoforge.mods.toml'
            metadata_path = layout/'resources/META-INF'/metadata_name
            assert metadata_path.exists(), f'{target}: metadata missing: {metadata_name}'
            opposite = 'neoforge.mods.toml' if loader == 'forge' else 'mods.toml'
            assert not (layout/'resources/META-INF'/opposite).exists(), target
            metadata = tomllib.loads(metadata_path.read_text('utf-8'))
            assert metadata['mods'][0]['modId'] == 'additionalpipes', target
            dependencies = {dep['modId']:dep for dep in metadata['dependencies']['additionalpipes']}
            assert {'minecraft','buildcraftlib','buildcrafttransport',loader} == set(dependencies), target
            for mid in ('buildcraftlib','buildcrafttransport'):
                key='mandatory' if loader == 'forge' else 'type'
                assert dependencies[mid][key] == (True if loader == 'forge' else 'required'), target
            pack = (layout/'resources/pack.mcmeta').read_text('utf-8')
            for attr in ('pack.format','pack.resource_format','pack.data_format'):
                # Expand our normal Gradle template values in the static probe.
                key={'pack.format':'pack_format','pack.resource_format':'resource_pack_format',
                     'pack.data_format':'data_pack_format'}[attr]
                pack=pack.replace('${'+key+'}',p[f'{"target."+target}.{attr}'])
            assert 'pack' in json.loads(pack),target
            assert report['layers'][0] == 'source-shared'
            assert report['layers'][-1] == f'version-src/{target}'
            print(f'OK {target:19s} | Java {java} | {len(report["files"])} source files | {loader} metadata and dependencies')
    print('SUCCESS: all seven Additional Pipes scaffolds have consistent source, loader and BCCE contracts')


def main():
    try:
        check()
    except (KeyError, OSError, ValueError, AssertionError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
