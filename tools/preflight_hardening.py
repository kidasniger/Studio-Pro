from pathlib import Path

p = Path(__file__).resolve().parent / "harden_studio_pro.py"
s = p.read_text(encoding="utf-8")
old = 'main = patch_main(MAIN.read_text(encoding="utf-8"))\n    main = patch_audio_editor_callback(main)'
new = 'main = patch_main(MAIN.read_text(encoding="utf-8"))'
if old in s:
    s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")
print("preflight hardening script repaired")
