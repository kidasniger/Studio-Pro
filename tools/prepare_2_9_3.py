from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'app/build.gradle'
s = path.read_text(encoding='utf-8')
s = s.replace('versionCode 29', 'versionCode 30', 1).replace('versionName "2.9.2"', 'versionName "2.9.3"', 1)
path.write_text(s, encoding='utf-8')
assert 'applicationId "com.kidas.studiopro"' in s
assert "namespace 'com.kidas.studiopro'" in s
assert 'versionCode 30' in s and 'versionName "2.9.3"' in s
