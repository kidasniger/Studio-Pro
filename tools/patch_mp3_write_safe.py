from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
s = p.read_text(encoding='utf-8')
# Convert the unsafe fallback that deletes the original before retrying into fail-closed behavior.
s = s.replace('if (!tmp.renameTo(original)) {\n                            if (original.exists() && !original.delete()) throw new Exception("Impossible de préparer le remplacement du MP3.");\n                            if (!tmp.renameTo(original)) throw new Exception("Remplacement atomique du MP3 impossible.");\n                        }', 'if (!tmp.renameTo(original)) {\n                            tmp.delete();\n                            throw new Exception("Remplacement atomique du MP3 impossible; fichier original conservé.");\n                        }')
p.write_text(s, encoding='utf-8')
print('MP3 atomic replacement hardened')
