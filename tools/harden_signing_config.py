from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app/build.gradle'
s = p.read_text(encoding='utf-8')
old = 'signingConfig (System.getenv("STUDIO_PRO_KEYSTORE_BASE64") || System.getenv("STUDIO_PRO_KEYSTORE") ? signingConfigs.studioRelease : signingConfigs.debug)'
new = '''signingConfig ((System.getenv("STUDIO_PRO_KEYSTORE_BASE64") && System.getenv("STUDIO_PRO_KEYSTORE_PASSWORD") && System.getenv("STUDIO_PRO_KEY_ALIAS") && System.getenv("STUDIO_PRO_KEY_PASSWORD")) || (System.getenv("STUDIO_PRO_KEYSTORE") && System.getenv("STUDIO_PRO_KEYSTORE_PASSWORD") && System.getenv("STUDIO_PRO_KEY_ALIAS") && System.getenv("STUDIO_PRO_KEY_PASSWORD")) ? signingConfigs.studioRelease : signingConfigs.debug)'''
if old in s:
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('release signing selection hardened')
