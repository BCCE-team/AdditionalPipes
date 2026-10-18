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
        for required in ('service/APServices.class', 'service/BCCEIntegration.class',
                         'pipes/TeleportManager.class', 'network/APProtocol.class',
                         'network/ChunkloadRequestHandler.class', 'chunkloader/ChunkLoadingService.class',
                         'platform/network/PlatformNetwork.class', 'platform/chunk/PlatformChunkTickets.class'):
            assert 'buildcraft/additionalpipes/' + required in names, f'{target}: missing {required}'
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
        assert {'minecraft','buildcraftlib','buildcrafttransport','buildcraftcore','buildcraftsilicon',loader}==deps, deps
        assert props['mods'][0]['modId']=='additionalpipes'
        pack=z.read('pack.mcmeta').decode('utf-8')
        assert '${' not in pack
        pack_values = json.loads(pack)['pack']
        modern = target.startswith(('26.', '1.21.11'))
        if modern:
            assert pack_values['min_format'] == pack_values['pack_format'], 'pack min_format mismatch'
            assert pack_values['max_format'] == pack_values['pack_format'], 'pack max_format mismatch'
        for name in names:
            if not name.startswith('assets/additionalpipes/models/item/pipe_') or not name.endswith('.json'):
                continue
            model = json.loads(z.read(name))
            assert model['elements'][0]['from'] == [4, 0, 4], (target, name)
            assert model['elements'][0]['to'] == [12, 16, 12], (target, name)
            assert len(model['elements']) == 1, (target, name)
            for side, face in model['elements'][0]['faces'].items():
                expected = [4, 4, 12, 12] if side in ('up', 'down') else [4, 0, 12, 16]
                assert face['uv'] == expected, (target, name, side)
        recipe_dir = 'recipes' if target.startswith(('1.19.2', '1.20.1')) else 'recipe'
        recipe_paths = [name for name in names if name.startswith('data/additionalpipes/' + recipe_dir + '/') and name.endswith('.json')]
        assert len(recipe_paths) == 20, (target, len(recipe_paths))
        for name in recipe_paths:
            recipe = json.loads(z.read(name))
            if recipe['type'] == 'minecraft:crafting_shaped':
                ingredients = recipe['key'].values()
            else:
                ingredients = recipe.get('ingredients', ())
            for ingredient in ingredients:
                if modern:
                    assert isinstance(ingredient, str) and ingredient, (name, ingredient)
                else:
                    assert isinstance(ingredient, dict) and 'item' in ingredient, (name, ingredient)
            if recipe['type'] == 'buildcraftsilicon:assembly':
                assert len(recipe['ingredients']) == len(recipe['ingredient_counts']), name
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
