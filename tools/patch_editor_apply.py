from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EDITOR = ROOT / "app/src/main/java/com/humbleman/visualiseur/AudioEditorDialog.java"

def replace_method(text, signature, replacement):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"Method not found: {signature}")
    brace = text.find("{", start)
    if brace < 0:
        raise SystemExit("Opening brace not found")
    depth = 0
    in_string = False
    escape = False
    i = brace
    while i < len(text):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"': in_string = True
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement + text[i + 1:]
        i += 1
    raise SystemExit("Unbalanced braces")

text = EDITOR.read_text(encoding="utf-8")
if "Traitement audio appliqué au fichier de travail." in text:
    print("audio editor apply operation already repaired")
else:
    replacement = '''    private void applyChanges() {\n        if (audioFile == null || !audioFile.exists()) {\n            Toast.makeText(getContext(), "Fichier audio indisponible.", Toast.LENGTH_LONG).show();\n            return;\n        }\n        final File source = audioFile;\n        new Thread(() -> {\n            File processed = new File(getContext().getCacheDir(), "edited_" + System.nanoTime() + ".wav");\n            try {\n                AudioEditEngine.process(source, processed, currentStartMs, currentEndMs, volumeGain, playbackSpeed, fadeInSec, fadeOutSec, normalize);\n                if (source.exists()) {\n                    try (InputStream in = new java.io.FileInputStream(processed); OutputStream out = new java.io.FileOutputStream(source)) {\n                        byte[] buffer = new byte[65536];\n                        int n;\n                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);\n                        out.flush();\n                    }\n                }\n                handler.post(() -> {\n                    if (listener != null) listener.onAudioEdited(currentStartMs, currentEndMs, volumeGain, playbackSpeed, fadeInSec, fadeOutSec, normalize, loopMode);\n                    Toast.makeText(getContext(), "Traitement audio appliqué au fichier de travail.", Toast.LENGTH_SHORT).show();\n                    dismiss();\n                });\n            } catch (Exception e) {\n                handler.post(() -> Toast.makeText(getContext(), "Édition audio impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());\n            } finally {\n                if (processed.exists()) processed.delete();\n            }\n        }).start();\n    }\n'''
    text = replace_method(text, "    private void applyChanges()", replacement)
    EDITOR.write_text(text, encoding="utf-8")
    print("audio editor apply operation repaired")
