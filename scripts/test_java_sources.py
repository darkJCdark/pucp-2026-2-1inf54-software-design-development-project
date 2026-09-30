#!/usr/bin/env python3
"""Compile/run existing Java test METHOD BODIES with a minimal offline adapter.
No downloaded dependencies; NOT a Maven/JUnit execution. Temporary copies of imports
point to paqrap.offlinecheck; original JUnit test files remain ready for Maven.
Usage: python scripts/test_java_sources.py
"""
from pathlib import Path
import re, shutil, subprocess, tempfile
from campaign_tools import ROOT

def main():
    if not shutil.which('javac'): raise SystemExit('Se necesita JDK 21 (javac).')
    with tempfile.TemporaryDirectory(prefix='paqrap-tests-') as tmp:
        d=Path(tmp);sources=[];classes=[]
        for module in ('dominio','grasp','sa','experimentos'):
            for path in sorted((ROOT/module/'src/test/java').rglob('*.java')):
                original=path.read_text(encoding='utf-8');text=original.replace('org.junit.jupiter.api.', 'paqrap.offlinecheck.')
                dest=d/module/path.relative_to(ROOT/module/'src/test/java');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,encoding='utf-8');sources.append(dest)
                if '@Test' in original:
                    package=re.search(r'package\s+([\w.]+)\s*;',original).group(1);classes.append(package+'.'+path.stem)
        sources+=list((ROOT/'tests/java_support').glob('*.java'));out=d/'classes';out.mkdir()
        argfile=d/'sources.txt';argfile.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
        jar=ROOT/'dist/paqrap-experimentos.jar'
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',str(jar),'-d',str(out),'@'+str(argfile)],check=True)
        import os
        subprocess.run(['java','-Xmx512m','-cp',str(jar)+os.pathsep+str(out),'paqrap.offlinecheck.Runner',*classes],cwd=ROOT,check=True)

if __name__=='__main__':main()
