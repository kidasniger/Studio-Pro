from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java').read_text(encoding='utf-8')
BUILD = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
MANIFEST = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')

checks = {
    'applicationId': 'applicationId "com.kidas.studiopro"' in BUILD,
    'namespace': "namespace 'com.kidas.studiopro'" in BUILD,
    'javaPackage': 'package com.kidas.studiopro;' in MAIN,
    'versionCode': ('versionCode 30' in BUILD or 'versionCode 29' in BUILD),
    'versionName': ('versionName "2.9.3"' in BUILD or 'versionName "2.9.2"' in BUILD),
    'responsiveShell': 'screenWidthDp < 360' in MAIN and 'screenWidthDp >= 600' in MAIN,
    'radialBackground': '"radial".equals(backgroundMode)' in MAIN,
    'visualizerWidthPreserved': 'c.scale(1.0f, Math.max(0.50f' in MAIN,
    'visualizerLengthPersisted': 'o.put("visualizerLengthScale", Math.max' in MAIN,
    'mp4Validation': 'private void validateMp4' in MAIN,
    'mediaStorePending': 'MediaStore.Video.Media.IS_PENDING' in MAIN,
    'safeLrcCleanup': 'DISPLAY_NAME + " LIKE ?"' not in MAIN,
    'landscapeAllowed': 'android:screenOrientation="portrait"' not in MANIFEST,
    'translatedTimingSourceControlled': 'timestamps always come from the ORIGINAL lyric timeline' in MAIN,
    'translationLineMarkers': 'LINE_%04d: %s' in MAIN and 'Pattern.compile("(?m)^\\\\s*LINE_' in MAIN,
    'translationDoesNotFallbackToOneTimedLine': 'result.add(new LyricLine(sourceLyrics.get(0).startMs, sourceLyrics.get(sourceLyrics.size() - 1).endMs, translated));' not in MAIN,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('Hardening assertions failed: ' + ', '.join(failed))
print('All hardened-source assertions passed')
