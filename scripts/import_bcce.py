#!/usr/bin/env python3
"""Install matching BCCE release jars as LOCAL, non-distributed Maven dependencies.

Accepts a BCCE build.zip (seven Maven/Mojmap release JARs) or a BCCE
repository build/ directory. No Minecraft or BCCE code is vendored into AP.
"""
from __future__ import annotations

import argparse
import hashlib
import io
from pathlib import Path
import sys
import zipfile
from materialize_sources import ROOT, config


def expected_targets() -> dict[str, str]:
    values = config()
    names = values['targets'].split(',')
    ver = values['common.bcce.version']
    return {t: f'BuildCraft-Community-Edition-{ver}+{t.replace("-", "+")}.jar' for t in names}


def pick_file(archive: zipfile.ZipFile, target: str, name: str):
    mc = target.split('-')[0]
    candidates=[n for n in archive.namelist() if n == f'{mc}/{name}' or n.endswith(f'/{mc}/{name}')]
    if len(candidates)!=1:
        raise ValueError(f'Expected exactly one jar for {target}: {mc}/{name}, got {len(candidates)}')
    return archive.read(candidates[0])


def check_jar(blob: bytes, target: str) -> None:
    try:
        with zipfile.ZipFile(io.BytesIO(blob)) as jar:
            entries = set(jar.namelist())
            required = {'buildcraft/api/v2/BuildCraftServices.class',
                        'buildcraft/api/v2/pipe/PipeBehaviourDefinition.class'}
            absent = required - entries
            if absent:
                raise ValueError(f'{target}: missing API v2 exports {sorted(absent)}')
            loader = target.rsplit('-',1)[1]
            metadata = 'META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml'
            if metadata not in entries:
                raise ValueError(f'{target}: missing {metadata}; wrong BCCE loader or JAR')
            description = jar.read(metadata).decode('utf-8')
            for mod in ('buildcraftlib','buildcrafttransport'):
                if f'modId="{mod}"' not in description:
                    raise ValueError(f'{target}: BCCE jar does not declare {mod}')
    except zipfile.BadZipFile as e:
        raise ValueError(f'{target}: invalid BCCE jar') from e


def install(target: str, data: bytes, dry_run: bool) -> Path:
    p = config()
    version = p['common.bcce.version']+'-'+target
    group = p['common.bcce.group'].replace('.','/')
    module = p['common.bcce.module']
    path = ROOT / p['common.bcce.maven_root'] / group / module / version
    jar_name = f'{module}-{version}.jar'
    if not dry_run:
        path.mkdir(parents=True,exist_ok=True)
        (path / jar_name).write_bytes(data)
        pom = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{p['common.bcce.group']}</groupId><artifactId>{module}</artifactId><version>{version}</version>
  <packaging>jar</packaging>
  <description>Local external BCCE development dependency - not for redistribution</description>
</project>
'''
        (path/f'{module}-{version}.pom').write_text(pom,encoding='utf-8')
    print(f'{target}: {len(data):,} bytes sha256={hashlib.sha256(data).hexdigest()[:12]} -> {path.relative_to(ROOT)}/{jar_name}')
    return path / jar_name


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    src=parser.add_mutually_exclusive_group(required=True)
    src.add_argument('--archive',type=Path,help='Path to build.zip from BCCE')
    src.add_argument('--build-dir',type=Path,help='Path to BCCE repo build/ directory')
    parser.add_argument('--verify-only',action='store_true',help='Validate input without copying')
    args=parser.parse_args()
    try:
        targets=expected_targets()
        if args.archive:
            with zipfile.ZipFile(args.archive) as z:
                contents={t: pick_file(z,t,name) for t,name in targets.items()}
        else:
            contents={t:(args.build_dir/t.split('-')[0]/name).read_bytes() for t,name in targets.items()}
        for t,data in contents.items():
            check_jar(data,t)
        for t,data in contents.items():
            install(t,data,args.verify_only)
        print(f'OK: {len(targets)} BCCE {config()["common.bcce.version"]} platform jars verified; imported={not args.verify_only}')
        return 0
    except (OSError, zipfile.BadZipFile, ValueError) as exc:
        print(f'ERROR: {exc}',file=sys.stderr)
        return 1


if __name__=='__main__':
    raise SystemExit(main())
