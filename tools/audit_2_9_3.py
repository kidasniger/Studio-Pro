from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app'
JAVA_ROOT = APP / 'src/main/java'
MAIN = next(JAVA_ROOT.rglob('MainActivity.java')).read_text(encoding='utf-8')
BUILD = (APP / 'build.gradle').read_text(encoding='utf-8')
MANIFEST = (APP / 'src/main/AndroidManifest.xml').read_text(encoding='utf-8')
KIDAS = (ROOT / 'kidas.md').read_text(encoding='utf-8')
WORD_TIMED = next(JAVA_ROOT.rglob('WordTimedLyrics.java')).read_text(encoding='utf-8')
SAFETY = next(JAVA_ROOT.rglob('AudioEditSafety.java')).read_text(encoding='utf-8')
ENGINE = next(JAVA_ROOT.rglob('AudioEditEngine.java')).read_text(encoding='utf-8')

java_sources = '\n'.join(
    p.read_text(encoding='utf-8', errors='strict')
    for p in JAVA_ROOT.rglob('*.java')
)

checks = {
    'package': 'applicationId "com.kidas.studiopro"' in BUILD and "namespace 'com.kidas.studiopro'" in BUILD and 'package com.kidas.studiopro;' in java_sources,
    'version': 'versionCode 30' in BUILD and 'versionName "2.9.3"' in BUILD,
    'no_forced_portrait': 'android:screenOrientation="portrait"' not in MANIFEST,
    'six_pages': all(f'create{n}Page()' in MAIN for n in ['Studio','Visual','Lyrics','Audio','Ai','Export']),
    'word_parser': 'static WordTimedLyrics fromWhisperJson' in WORD_TIMED,
    'word_lookup': 'String wordAt(long currentMs)' in WORD_TIMED,
    'editor_safety_wrapper': 'AudioEditEngine.process(' in SAFETY and 'File process(' in SAFETY,
    'engine_input_validation': 'validateArguments(source, destination, startMs, endMs, gain, speed, fadeInSec, fadeOutSec);' in ENGINE and 'La source et la destination audio doivent être différentes.' in ENGINE,
    'engine_atomic_output': '.studiopro.tmp' in ENGINE and 'renameTo(destination)' in ENGINE,
    'mp4_validation': 'validateMp4(' in MAIN,
    'media_pending': 'MediaStore.Video.Media.IS_PENDING' in MAIN,
    'kidas_icon': 'icon: "https://raw.githubusercontent.com/kidasniger/Audio/main/assets/studio-pro-icon.svg"' in KIDAS,
    'kidas_no_github_avatar': 'avatars.githubusercontent.com' not in KIDAS and 'owner.avatar_url' not in KIDAS,
    'kidas_video': 'https://youtube.com/shorts/yYt1xZj02nc?si=umcr-nAAz0pg_wN0' in KIDAS,
    'no_legacy_package_source': 'com.humbleman.visualiseur' not in java_sources,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('2.9.3 audit failed: ' + ', '.join(failed))

print('Studio Pro 2.9.3 focused audit: PASS')
