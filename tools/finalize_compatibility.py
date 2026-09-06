from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Allow landscape; UI remains scroll-based and the same application id is preserved.
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
s = manifest.read_text(encoding='utf-8')
s = s.replace('        <activity\n            android:name=".MainActivity"\n            android:exported="true"\n            android:screenOrientation="portrait"\n', '        <activity\n            android:name=".MainActivity"\n            android:exported="true"\n')
s = s.replace('        <activity\n            android:name=".OnboardingActivity"\n            android:exported="false"\n            android:screenOrientation="portrait"\n', '        <activity\n            android:name=".OnboardingActivity"\n            android:exported="false"\n')
manifest.write_text(s, encoding='utf-8')

# Downsample imported images to a bounded working size to reduce OOM risk.
main = ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
s = main.read_text(encoding='utf-8')
old = '''    private void loadImage(Uri uri, boolean bg) {\n        try (InputStream in = getContentResolver().openInputStream(uri)) {\n            Bitmap b = BitmapFactory.decodeStream(in);\n            if (b == null) throw new Exception("Image illisible.");\n            if (bg) {\n                backgroundBitmap = b;\n                updateBgSelection("image");\n            } else {\n                watermarkBitmap = b;\n            }\n            visualizerView.invalidate();\n            if (visualPagePreview != null) visualPagePreview.invalidate();\n            showStatus("Image importée.");\n        } catch (Exception e) {\n            showStatus("Image : " + friendlyError(e));\n        }\n    }'''
new = '''    private void loadImage(Uri uri, boolean bg) {\n        try {\n            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();\n            bounds.inJustDecodeBounds = true;\n            try (InputStream in = getContentResolver().openInputStream(uri)) {\n                BitmapFactory.decodeStream(in, null, bounds);\n            }\n            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("Image illisible.");\n            final int maxDim = 2048;\n            int sample = 1;\n            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2;\n            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();\n            opts.inSampleSize = sample;\n            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;\n            Bitmap b;\n            try (InputStream in = getContentResolver().openInputStream(uri)) {\n                b = BitmapFactory.decodeStream(in, null, opts);\n            }\n            if (b == null) throw new Exception("Image illisible.");\n            if (bg) { backgroundBitmap = b; updateBgSelection("image"); }\n            else watermarkBitmap = b;\n            if (visualizerView != null) visualizerView.invalidate();\n            if (visualPagePreview != null) visualPagePreview.invalidate();\n            showStatus("Image importée.");\n        } catch (Exception e) {\n            showStatus("Image : " + friendlyError(e));\n        }\n    }'''
if old in s:
    s = s.replace(old, new, 1)
    print('loadImage patched')
else:
    print('loadImage already hardened; leaving it unchanged')
main.write_text(s, encoding='utf-8')

# Make the UI label accurately describe the local live recognizer.
voice = ROOT / 'app/src/main/java/com/humbleman/visualiseur/VoiceRecorderDialog.java'
v = voice.read_text(encoding='utf-8')
v = v.replace('Transcription en temps réel & Groq Whisper', 'Reconnaissance vocale locale en direct, puis Groq Whisper à l’arrêt')
v = v.replace('Carte : Transcription en Direct (Streaming on-device)', 'Carte : Reconnaissance vocale en direct')
voice.write_text(v, encoding='utf-8')
print('compatibility, image memory and live-transcription labels patched')
