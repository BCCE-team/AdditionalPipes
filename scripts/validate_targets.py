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
            assert 'APContentRegistry.java' in java_files, target
            assert 'APContent.java' in java_files, target
            assets = layout/'resources/assets/additionalpipes'
            assert len(list((assets/'blockstates').glob('*.json'))) == 20, target
            assert len(list((assets/'models/item').glob('*.json'))) == 21, target
            assert len(list((assets/'textures/block').glob('*.png'))) == 44, target
            assert (assets/'textures/item/dog_deaggravator.png').exists(), target
            lang = json.loads((assets/'lang/en_us.json').read_text())
            assert len([key for key in lang if key.startswith('block.additionalpipes.')]) == 20, target
            assert not (assets/'lang/ru_ru.json').exists(), target
            recipe_dir = layout/'resources/data/additionalpipes'/('recipes' if generation=='old' else 'recipe')
            assert len(list(recipe_dir.glob('*.json'))) == 20, target
            assembly = recipe_dir/'assembly/pipe_items_teleport.json'
            assembly_recipe = json.loads(assembly.read_text())
            assert assembly_recipe['MJ'] == 10000000000, target
            assert assembly_recipe['result']['count'] == 8, target
            assert len(assembly_recipe['ingredients']) == len(assembly_recipe['ingredient_counts']), target
            modern = target.startswith(('26.', '1.21.11'))
            for recipe_path in recipe_dir.rglob('*.json'):
                recipe = json.loads(recipe_path.read_text())
                recipe_type = recipe['type']
                if recipe_type == 'minecraft:crafting_shaped':
                    ingredients = recipe['key'].values()
                else:
                    ingredients = recipe.get('ingredients', ())
                for ingredient in ingredients:
                    if modern:
                        assert isinstance(ingredient, str) and ingredient, (target, recipe_path, ingredient)
                    else:
                        assert isinstance(ingredient, dict) and 'item' in ingredient, (target, recipe_path, ingredient)
            if target.startswith(('26.', '1.21.11')):
                assert len(list((assets/'items').glob('*.json'))) == 21, target
            assert 'AdditionalPipes.java' in java_files, target
            forge = 'AdditionalPipesForge.java' in java_files
            neo = 'AdditionalPipesNeoForge.java' in java_files
            assert forge != neo, f'{target}: wrong or mixed loader bootstrap'
            assert forge == (loader == 'forge'), target
            mandatory_classes = ('APConfiguration.java', 'TeleportManager.java',
                                 'APProtocol.java', 'PacketHandler.java',
                                 'ChunkLoadingService.java', 'ChunkLoadingHandler.java',
                                 'PlatformNetwork.java', 'PlatformChunkTickets.java')
            assert all(name in java_files for name in mandatory_classes), f'{target}: incomplete service layer'
            chunk_code = (layout/'java/buildcraft/additionalpipes/platform/chunk/PlatformChunkTickets.java').read_text('utf-8')
            wire_code = (layout/'java/buildcraft/additionalpipes/platform/network/PlatformNetwork.java').read_text('utf-8')
            if loader == 'forge':
                assert 'ForgeChunkManager' in chunk_code and 'SimpleChannel' in wire_code, target
                assert 'net.neoforged.' not in chunk_code + wire_code, target
            else:
                assert 'TicketController' in chunk_code and 'playBidirectional' in wire_code, target
                if target == '1.21.1-neoforge':
                    assert 'ResourceLocation' in chunk_code + wire_code, target
                    assert 'ClientPacketDistributor.sendToServer' not in wire_code, target
                    assert 'PlatformNetwork::handlePayload);' in wire_code, target
                else:
                    assert 'net.minecraft.resources.Identifier' in chunk_code, target
                    assert 'net.minecraft.resources.Identifier' in wire_code, target
                    assert 'ClientPacketDistributor.sendToServer' in wire_code, target
                    assert 'PlatformNetwork::handlePayload, PlatformNetwork::handlePayload' in wire_code, target
            metadata_name = 'mods.toml' if loader == 'forge' else 'neoforge.mods.toml'
            metadata_path = layout/'resources/META-INF'/metadata_name
            assert metadata_path.exists(), f'{target}: metadata missing: {metadata_name}'
            opposite = 'neoforge.mods.toml' if loader == 'forge' else 'mods.toml'
            assert not (layout/'resources/META-INF'/opposite).exists(), target
            metadata = tomllib.loads(metadata_path.read_text('utf-8'))
            assert metadata['mods'][0]['modId'] == 'additionalpipes', target
            dependencies = {dep['modId']:dep for dep in metadata['dependencies']['additionalpipes']}
            assert {'minecraft','buildcraftlib','buildcrafttransport','buildcraftcore','buildcraftsilicon',loader} == set(dependencies), target
            for mid in ('buildcraftlib','buildcrafttransport','buildcraftcore','buildcraftsilicon'):
                key='mandatory' if loader == 'forge' else 'type'
                assert dependencies[mid][key] == (True if loader == 'forge' else 'required'), target
            pack = (layout/'resources/pack.mcmeta').read_text('utf-8')
            for attr in ('pack.format','pack.resource_format','pack.data_format'):
                # Expand our normal Gradle template values in the static probe.
                key={'pack.format':'pack_format','pack.resource_format':'resource_pack_format',
                     'pack.data_format':'data_pack_format'}[attr]
                pack=pack.replace('${'+key+'}',p[f'{"target."+target}.{attr}'])
            pack_values = json.loads(pack)['pack']
            if modern:
                assert pack_values['min_format'] == pack_values['pack_format'], target
                assert pack_values['max_format'] == pack_values['pack_format'], target
            else:
                assert 'min_format' not in pack_values and 'max_format' not in pack_values, target
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
