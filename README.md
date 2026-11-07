# Nettoyage Téléphone (APK) — version 2

Application Android de nettoyage du téléphone. Interface en **français** et en **arabe**. Code PIN **optionnel**. Aucune donnée n'est envoyée sur Internet.

## 1. Mise en place sur GitHub (fichiers à plat)

1. Déposer à la racine du dépôt (**Add file → Upload files**, en remplaçant les anciens fichiers de même nom) ces 13 fichiers :
   `MainActivity.java`, `Scan.java`, `Store.java`, `Tr.java`, `Trash.java`, `Photos.java`, `ShareProvider.java`, `ReminderJob.java`, `StorageWidget.java`, `AndroidManifest.xml`, `ic_launcher.xml`, `widget.xml`, `widget_info.xml` (et `README.md`).
2. Remplacer le contenu de `.github/workflows/build.yml` par le nouveau `build.yml` (crayon ✏️ → tout sélectionner → coller → **Commit**).
3. Onglet **Actions** → attendre la fin → **Artifacts** → `Nettoyage-Telephone-APK` → installer `app-debug.apk` par-dessus l'ancienne version.

## 2. Autorisations

- **Accès à tous les fichiers** (analyse et nettoyage) ; **statistiques d'utilisation** (rubrique Applications) ; **notifications** (rappel, si activé).

## 3. Rubriques

| Rubrique | Rôle |
|---|---|
| ⚡ Nettoyage rapide | Après l'analyse : sélection automatique des éléments sûrs (temporaires, fichiers vides, APK déjà installés, captures de plus de 30 jours), un seul bouton de confirmation. |
| Fichiers volumineux | Filtres par type (photos, vidéos, audio, documents, APK, archives, autres) et par taille minimale (10, 50, 100 Mo). |
| Doublons | Contenu strictement identique ; un exemplaire conservé. |
| Photos similaires | Rafales et quasi-doublons ; la plus grande photo est conservée par défaut. |
| Photos floues | Estimation automatique de la netteté ; toujours vérifier avant de supprimer. |
| Compression des photos | JPEG de plus de 1,5 Mo, qualité élevée (≈ −50 %) ou moyenne (≈ −70 %). Date et GPS conservés ; l'original va à la corbeille si elle est activée. |
| Dossiers les plus lourds | Parcours par dossier, du plus lourd au plus léger ; appui long pour exclure un dossier. |
| Anciens téléchargements, Captures, APK, Médias de messagerie | Comme en version 1. |
| Fichiers temporaires, Dossiers/fichiers vides | Présélectionnés. |
| Applications rarement utilisées | Classées par dernière utilisation ; toucher pour désinstaller. |
| Cache | Vide le cache de l'application ; ouvre le nettoyage de cache système ou les réglages de stockage. |
| Cache des applications | Liste de toutes les applications classées par taille de cache (Android 8 et plus, accès aux statistiques d'utilisation requis). Toucher une application ouvre ses informations ; Stockage → Vider le cache, manuellement. |
| Corbeille | 7, 15 ou 30 jours ; restauration possible. |
| Sauvegardes (ZIP) | Archives créées avant suppression ; 📤 pour les envoyer vers Drive ou par e-mail. |
| Rapport | Espace libéré, gain par compression, historique. |

**Dans toutes les listes** : toucher pour cocher, appui long pour les détails et le partage, 📤 pour partager les éléments cochés (50 au maximum), puis **Supprimer** (ou **Compresser**).

## 4. Réglages

- **Langue** : français / arabe.
- **Code PIN** (optionnel) : demandé à l'ouverture et après 2 minutes d'absence. Oubli : Réglages d'Android → Applications → Nettoyage Téléphone → Stockage → Effacer les données.
- **Corbeille** : activation et durée de conservation.
- **Sauvegarder en ZIP avant suppression** : dossier `Téléchargements/Nettoyage-sauvegardes`.
- **Rappel de nettoyage** : jamais, ou tous les 7, 15 ou 30 jours (notification indiquant l'espace récupérable).
- **Dossiers exclus** : jamais analysés ni modifiés (par exemple la thèse). Relancer l'analyse après un changement.
- **Widget** : ajouter « Nettoyage Téléphone » depuis les widgets de l'écran d'accueil (espace libre + bouton Nettoyer, qui lance l'analyse).

## 5. Bon à savoir

- Désactiver la corbeille supprime définitivement et immédiatement.
- La détection des photos floues et similaires est une estimation : l'analyse des photos peut durer une à deux minutes sur un grand nombre d'images.
- Le nettoyage de la RAM n'est volontairement pas proposé : inefficace sur Android récent.
