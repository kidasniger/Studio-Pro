from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD_PACKAGE = 'com.humbleman.visualiseur'
NEW_PACKAGE = 'com.kidas.studiopro'

# Make the Android application identity consistent with the new package.
build = ROOT / 'app/build.gradle'
s = build.read_text(encoding='utf-8')
s = s.replace("namespace 'com.humbleman.visualiseur'", f"namespace '{NEW_PACKAGE}'", 1)
s = s.replace('applicationId "com.humbleman.visualiseur"', f'applicationId "{NEW_PACKAGE}"', 1)
s = s.replace('versionCode 28', 'versionCode 29', 1)
s = s.replace('versionName "2.9.1"', 'versionName "2.9.2"', 1)
build.write_text(s, encoding='utf-8')

# Move all Java source/test package declarations and imports together.
changed = 0
for p in ROOT.glob('app/src/**/*.java'):
    text = p.read_text(encoding='utf-8')
    new = text.replace(f'package {OLD_PACKAGE};', f'package {NEW_PACKAGE};')
    new = new.replace(f'import {OLD_PACKAGE}.', f'import {NEW_PACKAGE}.')
    if new != text:
        p.write_text(new, encoding='utf-8')
        changed += 1

# Responsive layout tuning for small phones and larger screens.
main = ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
text = main.read_text(encoding='utf-8')
old = '        LinearLayout shell = vertical();\n        shell.setPadding(dp(16), dp(8), dp(16), dp(8));'
new = '        LinearLayout shell = vertical();\n        final boolean compact = getResources().getConfiguration().screenWidthDp < 360;\n        final boolean largeScreen = getResources().getConfiguration().screenWidthDp >= 600;\n        final int shellHorizontalPad = dp(compact ? 10 : (largeScreen ? 24 : 14));\n        shell.setPadding(shellHorizontalPad, dp(8), shellHorizontalPad, dp(8));'
if old in text:
    text = text.replace(old, new, 1)
old = '            LinearLayout.LayoutParams iconPillLp = new LinearLayout.LayoutParams(dp(40), dp(26));'
new = '            LinearLayout.LayoutParams iconPillLp = new LinearLayout.LayoutParams(dp(compact ? 34 : 40), dp(26));'
if old in text:
    text = text.replace(old, new, 1)
old = '            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);'
new = '            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(compact ? 16 : 18), dp(compact ? 16 : 18), Gravity.CENTER);'
if old in text:
    text = text.replace(old, new, 1)
old = '            lb.setTextSize(10);'
new = '            lb.setTextSize(compact ? 9 : 10);'
if old in text:
    text = text.replace(old, new, 1)
old = '        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(62)));'
new = '        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(compact ? 56 : (largeScreen ? 66 : 62))));'
if old in text:
    text = text.replace(old, new, 1)
main.write_text(text, encoding='utf-8')

# Ensure the generated source identity is unambiguous for CI review.
assert f'applicationId "{NEW_PACKAGE}"' in build.read_text(encoding='utf-8')
assert f'namespace \'{NEW_PACKAGE}\'' in build.read_text(encoding='utf-8')
assert 'versionCode 29' in build.read_text(encoding='utf-8')
assert 'versionName "2.9.2"' in build.read_text(encoding='utf-8')
assert f'package {NEW_PACKAGE};' in main.read_text(encoding='utf-8')
print(f'2.9.2 package migration applied: {NEW_PACKAGE}; Java files updated: {changed}')
