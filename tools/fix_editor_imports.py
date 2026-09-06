from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/humbleman/visualiseur/AudioEditorDialog.java'
s = p.read_text(encoding='utf-8')
needle = 'import java.io.File;\nimport java.util.Locale;'
replacement = 'import java.io.File;\nimport java.io.InputStream;\nimport java.io.OutputStream;\nimport java.util.Locale;'
if needle in s:
    s = s.replace(needle, replacement, 1)
p.write_text(s, encoding='utf-8')
print('fixed AudioEditorDialog stream imports')
