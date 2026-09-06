# Journal de suivi — Studio Pro

> Journal technique du projet. Ce fichier décrit l'historique important, l'état réel des fonctionnalités et la feuille de route de la prochaine évolution. Une fonctionnalité ne doit être déclarée opérationnelle qu'après vérification de bout en bout.

## Règles de suivi

1. Décrire chaque modification importante, son résultat réel et les éventuelles erreurs.
2. Après toute modification du code Android, lancer et inspecter le build jusqu'à sa fin.
3. En cas d'échec, identifier la cause, corriger et relancer le build avant de considérer l'étape terminée.
4. Pour une livraison, vérifier l'APK, l'artefact, la Release et le Tag.
5. Le Tag doit pointer exactement vers le commit utilisé pour produire l'APK publiée.
6. Ne jamais présenter une fonction comme terminée uniquement parce que son bouton ou son code existe.
7. Conserver les fonctions existantes sauf décision explicite contraire.

## Historique synthétique

### Android natif
- Migration de l'ancienne approche WebView/HTML vers une application Android native.
- Navigation multi-écrans : Studio, Visuel, Paroles, Audio, IA et Export.
- Lecture audio, analyse du signal, visualiseur et presets.

### IA Groq
- Intégration Groq et fonctions liées à Whisper/Llama.
- Découverte dynamique des modèles et gestion de cascades de modèles ajoutées au cours des évolutions.
- État réel : à vérifier de bout en bout pour la transcription, la génération et la gestion des erreurs API.

### Interface et icônes
- Refonte UI premium.
- Remplacement des emojis par des VectorDrawables Android pour la navigation et les actions.
- Feedback Ripple, état actif des boutons et retour haptique.
- Identité produit actuelle : **Studio Pro**.

### Paroles
- Import/export LRC.
- Synchronisation des paroles, ligne active et modes karaoké.
- Support prévu pour l'édition fine des timings.

### Visualiseur
- Plusieurs styles visuels.
- Synchronisation sur le signal audio, basses/kicks et effets visuels.
- Réglage de la **longueur/occupation de la zone du visualiseur** : ce réglage signifie la hauteur ou l'espace qu'il occupe dans le cadre, et non une réduction de sa surface vidéo globale.

### Export vidéo
- Formats verticaux, carrés et horizontaux.
- Choix de résolution et de FPS.
- Export avec ou sans paroles et avec ou sans visualiseur.
- Incident important observé : export bloqué à **99 % / Muxing audio** avec erreur liée à la libération d'un buffer d'entrée AAC.
- Une correction du pipeline AAC a été préparée, mais la fonction doit rester marquée **à vérifier de bout en bout sur appareil réel** tant qu'un export complet n'a pas été confirmé.

### Métadonnées audio
- Objectif : intégrer directement les paroles dans le fichier MP3 original.
- Utiliser ID3 **USLT** pour les paroles texte et **SYLT** pour les paroles synchronisées.
- Préserver les autres métadonnées existantes, surtout la **pochette**, le titre, l'artiste, l'album et les autres frames ID3.
- Modifier le fichier audio à son **emplacement réel** lorsque Android et le fournisseur de stockage le permettent, sous le même nom.
- Ne pas se limiter à créer un nouveau fichier dans `Documents`.
- En cas de limitation du fournisseur de stockage, utiliser une stratégie sûre de remplacement/écriture atomique au même emplacement au lieu d'une duplication silencieuse.

## Feuille de route — prochaine évolution 2.9.x

### 1. Compatibilité de mise à jour

La prochaine version doit être une mise à jour directe de la version actuelle **2.8.4** :
- conserver exactement le même `applicationId` : `com.humbleman.visualiseur` ;
- conserver le même certificat / la même clé de signature ;
- utiliser un `versionCode` strictement supérieur à 26 ;
- proposer un `versionName` supérieur, cible initiale `2.9.0` ;
- conserver les préférences, paramètres et données locales ;
- prévoir des migrations si une structure interne change ;
- ne jamais transformer la prochaine APK en application Android différente.

### 2. Export vidéo professionnel

Refondre le pipeline en étapes clairement séparées :
`Préparation → rendu vidéo → encodage AAC → muxage MP4 → finalisation → validation`.

Corriger définitivement le problème de finalisation AAC :
- drainer régulièrement les buffers de sortie avant de demander de nouveaux buffers d'entrée ;
- traiter correctement `BUFFER_FLAG_END_OF_STREAM` ;
- conserver tous les restes PCM ;
- vider tous les buffers de sortie après EOS ;
- arrêter et libérer `MediaCodec` dans le bon ordre ;
- ne démarrer le muxage final qu'après disponibilité des pistes ;
- fermer proprement le muxer ;
- supprimer les fichiers temporaires même en cas d'erreur ;
- éviter les deadlocks et les fuites de buffers.

Ajouter :
- progression détaillée par étape ;
- ETA réel ;
- annulation propre ;
- export en arrière-plan ;
- gestion de la mémoire sur appareils modestes ;
- validation du MP4 après création ;
- plusieurs résolutions et 30/60 FPS ;
- 9:16, 16:9 et 1:1 ;
- avec/sans paroles ;
- avec/sans visualiseur.

### 3. Longueur du visualiseur dans le cadre

Le visualiseur doit rester capable de couvrir toute la largeur de la vidéo. Le nouveau réglage concerne **la longueur/hauteur de la zone qu'il occupe** dans le cadre, sans réduire artificiellement le visualiseur à une petite fenêtre.

Réglages prévus :
- longueur/hauteur de la zone ;
- position ;
- sensibilité ;
- réponse aux basses ;
- nombre de barres ;
- style ;
- vitesse ;
- couleurs ;
- effets ;
- aperçu identique à l'export.

### 4. Zone de paroles

Le contrôle doit être indépendant du visualiseur.

Réglages prévus :
- largeur/occupation de la zone des paroles ;
- position verticale et horizontale ;
- taille du texte ;
- espacement ;
- alignement ;
- style ;
- ligne active ;
- défilement ;
- mode karaoké mot par mot ;
- décalage temporel global ;
- édition du timing ligne par ligne.

Le réglage de zone ne doit pas être confondu avec l'intensité, l'opacité ou la taille de police.

### 5. Prévisualisation temps réel

Créer une vraie prévisualisation du rendu final :
- lecture ;
- pause ;
- navigation dans la timeline ;
- aperçu du visualiseur ;
- aperçu des paroles ;
- aperçu du fond et des effets ;
- aperçu selon le ratio choisi ;
- rendu identique au moteur d'export autant que possible.

### 6. Éditeur audio

Ajouter :
- découpe début/fin ;
- suppression d'une portion ;
- fondu entrant ;
- fondu sortant ;
- volume/gain ;
- normalisation ;
- vitesse ;
- détection et suppression des silences ;
- boucle ;
- aperçu instantané.

### 7. Intégration réelle des paroles dans les MP3

Le bouton **Intégrer à l'audio** doit écrire les paroles dans le véritable fichier MP3 et non simplement créer un fichier séparé.

Exigences :
- `USLT` pour texte non synchronisé ;
- `SYLT` pour paroles synchronisées ;
- conservation de la pochette existante ;
- conservation des autres frames ID3 ;
- même nom de fichier ;
- même emplacement lorsque possible ;
- vérification après écriture que les frames existent réellement ;
- relecture des métadonnées après sauvegarde pour confirmer que la pochette et les tags sont toujours présents.

L'expérience recherchée doit être comparable à une intégration de paroles dans un lecteur comme Musicolet : les paroles appartiennent au fichier audio lui-même.

### 8. Interface responsive et accessibilité

Adapter l'interface aux petits et grands téléphones, aux tablettes et aux orientations portrait/paysage.

Améliorer :
- tailles adaptatives ;
- espacements ;
- zones tactiles ;
- contraste ;
- lisibilité ;
- états actifs ;
- feedback Ripple/haptique ;
- cohérence entre tous les écrans.

Aucun emoji ne doit être utilisé comme icône d'interface. Utiliser de vraies icônes Android vectorielles ou des icônes graphiques dédiées.

### 9. IA Studio Pro

Transformer l'IA en assistant de projet capable de comprendre les réglages :
- « réduis la longueur du visualiseur » ;
- « augmente la zone des paroles » ;
- « centre les paroles » ;
- « exporte en 9:16 à 60 FPS ».

Prévoir aussi :
- transcription ;
- génération de citations ;
- analyse de projet ;
- vérification des paramètres ;
- gestion claire des erreurs réseau/API ;
- découverte dynamique des modèles lorsque nécessaire.

Toutes les fonctions Groq doivent rester marquées comme non vérifiées tant qu'elles n'ont pas été testées de bout en bout.

### 10. Architecture interne

Réduire progressivement la concentration de responsabilités dans `MainActivity.java` et séparer les composants :

```text
 audio/
   AudioPlayer
   AudioEditor
   AudioMetadata

 visual/
   VisualizerRenderer
   VisualStyleManager

 lyrics/
   LyricsManager
   LyricsRenderer
   LyricsSync

 export/
   VideoExporter
   AacTranscoder
   Mp4Muxer
   ExportJob

 ai/
   GroqClient
   AiAssistant

 ui/
   HomeScreen
   AudioScreen
   LyricsScreen
   VisualScreen
   ExportScreen
```

Objectifs : moins de mémoire utilisée, moins de crashs, meilleure gestion du cycle de vie et modifications futures plus sûres.

### 11. Tests et validation

Avant chaque nouvelle Release :
- test de compilation ;
- test de démarrage ;
- test d'import audio ;
- test de lecture ;
- test des paroles ;
- test de visualiseur ;
- test d'intégration ID3 avec pochette ;
- test d'export sans paroles ;
- test d'export avec paroles ;
- test d'export sans visualiseur ;
- test d'export avec visualiseur ;
- test de finalisation MP4 jusqu'à 100 % ;
- vérification réelle du fichier MP4 produit ;
- test d'installation **par-dessus Studio Pro 2.8.4** avec conservation des données.

Aucune fonctionnalité ne doit être déclarée « terminée » seulement parce que le build est vert.

## Procédure de livraison

Pour toute version contenant du code :

1. modifier le code ;
2. vérifier le diff et les fichiers réellement modifiés ;
3. lancer le build ;
4. attendre la fin réelle du build ;
5. corriger toute erreur et reconstruire ;
6. vérifier l'APK ;
7. vérifier l'installation/mise à jour ;
8. créer la Release ;
9. créer le Tag correspondant exactement au commit livré ;
10. vérifier une dernière fois la Release, l'APK et le Tag.

## État de validation actuel

### Studio Pro 2.8.4
- Version publiée précédente.
- `applicationId` à conserver pour les mises à jour.
- La version suivante doit augmenter le `versionCode`.

### Export vidéo
- Le problème de blocage à 99 % / muxage AAC a été identifié comme un problème du pipeline de buffers AAC.
- Toute nouvelle affirmation de correction doit être accompagnée d'un test d'export complet jusqu'à 100 % sur appareil réel.

### IA Groq
- Présence du code et des contrôles UI : oui.
- Validation fonctionnelle complète : à réaliser.

## Feuille de route résumée

**2.9.0 — Studio Pro**

- compatibilité de mise à jour depuis 2.8.4 ;
- export vidéo robuste jusqu'à 100 % ;
- correction définitive AAC/muxage ;
- preview temps réel ;
- longueur réglable du visualiseur ;
- longueur/zone réglable des paroles ;
- éditeur audio ;
- éditeur de paroles avancé ;
- intégration USLT/SYLT dans les MP3 en conservant les pochettes ;
- export avec/sans paroles et avec/sans visualiseur ;
- interface responsive ;
- zéro emoji dans l'interface ;
- assistant IA Studio Pro ;
- architecture interne plus modulaire ;
- tests de non-régression et validation d'installation par-dessus 2.8.4.
