from pathlib import Path
root = Path(__file__).resolve().parents[1]
editor = root / 'app/src/main/java/com/humbleman/visualiseur/AudioEditorDialog.java'
main = root / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'

s = editor.read_text(encoding='utf-8')
needle = 'import java.io.File;\nimport java.util.Locale;'
replacement = 'import java.io.File;\nimport java.io.InputStream;\nimport java.io.OutputStream;\nimport java.util.Locale;'
if needle in s:
    s = s.replace(needle, replacement, 1)
editor.write_text(s, encoding='utf-8')

m = main.read_text(encoding='utf-8')
if 'import java.util.regex.Matcher;' not in m:
    anchor = 'import java.util.concurrent.atomic.AtomicBoolean;\n'
    repl = anchor + 'import java.util.regex.Matcher;\nimport java.util.regex.Pattern;\n'
    if anchor in m:
        m = m.replace(anchor, repl, 1)
    else:
        raise SystemExit('MainActivity import anchor not found')
    main.write_text(m, encoding='utf-8')

print('AudioEditorDialog and MainActivity imports ensured')
