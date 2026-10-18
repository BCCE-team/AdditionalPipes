#!/usr/bin/env python3
"""Build one AP target from the shared/family/platform/target source layers.

Target-specific sources take precedence over platform, family and shared source.
Only source files below src/ are copied; this never edits the canonical layers.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"Missing configuration: {path}")
    result: dict[str, str] = {}
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        if '=' not in line:
            raise ValueError(f"{path}:{n}: expected key=value")
        name, value = line.split('=', 1)
        name = name.strip()
        if name in result:
            raise ValueError(f"Duplicate property {name} in {path}")
        result[name] = value.strip()
    return result


def config():
    return read_properties(ROOT / 'build-config/common.properties') | read_properties(ROOT / 'build-config/targets.properties')


def get_source_layers(target: str, properties: dict[str, str]):
    if target not in properties['targets'].split(','):
        raise ValueError(f"Unknown AP target: {target}")
    prefix = f'target.{target}.'
    family = properties[prefix+'source.family']
    loader = properties[prefix+'source.platform']
    roots = [
        properties['common.source.shared_root'],
        properties[f'source.family.{family}.root'],
        properties[f'source.platform.{loader}.root'],
        properties[f'source.family_platform.{family}.{loader}.root'],
        properties[prefix+'source.overlay_root'],
    ]
    output = []
    for root in roots:
        layer = (ROOT/root).resolve()
        if not layer.is_relative_to(ROOT) or not layer.is_dir():
            raise ValueError(f"Missing or unsafe layer {root!r} for {target}")
        output.append((root, layer))
    return output



def _modern_ingredient(value):
    if isinstance(value, dict):
        if set(value) == {'item'} and isinstance(value['item'], str):
            return value['item']
        if set(value) == {'tag'} and isinstance(value['tag'], str):
            return '#' + value['tag']
        result = {name: _modern_ingredient(child) for name, child in value.items()}
        if isinstance(result.get('type'), str) and ':' in result['type']:
            if 'neoforge:ingredient_type' not in result:
                result['neoforge:ingredient_type'] = result.pop('type')
        return result
    if isinstance(value, list):
        return [_modern_ingredient(child) for child in value]
    return value


def _rewrite_modern_recipe(contents: bytes, path: str) -> bytes:
    try:
        recipe = json.loads(contents)
    except (ValueError, UnicodeError) as exc:
        raise ValueError(f'Invalid recipe {path}: {exc}') from exc
    if not isinstance(recipe, dict):
        raise ValueError(f'Invalid recipe {path}: expected JSON object')
    recipe_type = recipe.get('type')
    if recipe_type == 'minecraft:crafting_shaped' and isinstance(recipe.get('key'), dict):
        recipe['key'] = _modern_ingredient(recipe['key'])
    elif recipe_type in ('minecraft:crafting_shapeless', 'buildcraftsilicon:assembly'):
        if isinstance(recipe.get('ingredients'), list):
            recipe['ingredients'] = _modern_ingredient(recipe['ingredients'])
    if recipe_type == 'buildcraftsilicon:assembly':
        ingredients = recipe.get('ingredients')
        counts = recipe.get('ingredient_counts')
        if not isinstance(ingredients, list) or not isinstance(counts, list) or len(ingredients) != len(counts):
            raise ValueError(f'Invalid assembly ingredients in {path}')
    return (json.dumps(recipe, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def _rewrite_modern_pack(contents: bytes) -> bytes:
    marker = b'"pack_format": ${pack_format},'
    if marker not in contents:
        raise ValueError('pack.mcmeta is missing its pack_format template')
    return contents.replace(
        marker,
        marker + b'\n    "min_format": ${pack_format},\n    "max_format": ${pack_format},',
    )


def _rewrite_resource(contents: bytes, *, path: str, modern: bool) -> bytes:
    if not modern:
        return contents
    if path == 'src/main/resources/pack.mcmeta':
        return _rewrite_modern_pack(contents)
    if path.startswith('src/main/resources/data/additionalpipes/recipe/') and path.endswith('.json'):
        return _rewrite_modern_recipe(contents, path)
    return contents


def materialize(target: str, output: Path) -> dict:
    properties = config()
    layers = get_source_layers(target, properties)
    minecraft = properties.get(f'target.{target}.deps.minecraft', target.split('-', 1)[0])
    modern = tuple(map(int, minecraft.split('.'))) >= (1, 21, 11)
    output = output.resolve()
    # Refuse to delete canonical source trees (or their parents) even if an
    # incorrect Gradle/property argument accidentally points at a source dir.
    if ROOT.is_relative_to(output) or any(
        output.is_relative_to(layer) or layer.is_relative_to(output)
        for _, layer in layers
    ):
        raise ValueError('Effective output must be outside all canonical source layers')
    selected: dict[str, tuple[Path, str]] = {}
    overlays: list[dict] = []
    for name, layer in layers:
        for f in sorted((layer/'src').rglob('*')) if (layer/'src').is_dir() else ():
            if not f.is_file():
                continue
            if f.is_symlink():
                raise ValueError(f'Symlinks cannot enter the build: {f}')
            rel = f.relative_to(layer).as_posix()
            if rel in selected:
                overlays.append({'file': rel, 'replaces': selected[rel][1], 'with': name})
            selected[rel] = (f, name)

    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    manifest = []
    for rel, (src, name) in sorted(selected.items()):
        target_path = output/rel
        target_path.parent.mkdir(parents=True,exist_ok=True)
        contents = _rewrite_resource(src.read_bytes(), path=rel, modern=modern)
        target_path.write_bytes(contents)
        manifest.append({'path': rel, 'source_layer': name, 'sha256': hashlib.sha256(contents).hexdigest()})
    report = {'target': target, 'layers': [name for name,_ in layers], 'files': manifest, 'overrides': overlays}
    (output/'source-manifest.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n', encoding='utf-8')
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target',help='e.g. 1.20.1-forge')
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    try:
        result=materialize(args.target,args.output)
    except (ValueError, KeyError, OSError) as exc:
        print(f'ERROR: {exc}',file=sys.stderr)
        return 1
    print(f'{args.target}: {len(result["files"])} effective sources, {len(result["overrides"])} explicit layer overrides -> {args.output}')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
