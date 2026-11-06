# Nettoyage Téléphone (APK)

Application Android de nettoyage du téléphone. Interface en **français** et en **arabe** (changement de langue dans Réglages). Code PIN **optionnel**. Aucune donnée n'est envoyée sur Internet.

## 1. Mise en place sur GitHub (fichiers à plat)

1. Créer un dépôt (privé) puis **Add file → Upload files** et déposer, à la racine, ces 8 fichiers :
   `MainActivity.java`, `Scan.java`, `Store.java`, `Tr.java`, `Trash.java`, `AndroidManifest.xml`, `ic_launcher.xml`, `README.md`.
2. **Add file → Create new file**. Dans le champ du nom, taper exactement `.github/workflows/build.yml` (la barre oblique crée les dossiers), puis coller le contenu du fichier `build.yml` fourni. **Commit**.
3. Onglet **Actions** → exécution « Construire l'APK » (environ 5 minutes).
4. Ouvrir l'exécution terminée → **Artifacts** → télécharger `Nettoyage-Telephone-APK`, extraire `app-debug.apk` et l'installer (autoriser les sources inconnues si demandé).

Le dossier de projet Android est reconstitué automatiquement par le workflow : aucun dossier à créer à la main.

## 2. Autorisations demandées

- **Accès à tous les fichiers** : indispensable pour analyser et nettoyer le stockage.
- **Statistiques d'utilisation** : seulement pour la rubrique « Applications rarement utilisées ».

## 3. Fonctions

| Rubrique | Rôle |
|---|---|
| Analyse | Parcourt le stockage une fois ; affiche la répartition (photos, vidéos, audio, documents, APK, archives, autres). À lancer avant les rubriques de fichiers. |
| Fichiers volumineux | Fichiers de 10 Mo et plus, du plus lourd au plus léger. |
| Doublons | Fichiers au contenu strictement identique (comparaison par empreinte). Un exemplaire est toujours conservé : le plus ancien est décoché par défaut. |
| Anciens téléchargements | Fichiers du dossier Téléchargements de plus de 30 jours. |
| Captures d'écran | Toutes les captures enregistrées. |
| Fichiers APK | Installateurs d'applications devenus inutiles. |
| Médias de messagerie | Médias WhatsApp/Telegram de plus de 30 jours (les sauvegardes de discussions ne sont pas touchées). |
| Fichiers temporaires | .tmp, .log, .bak, miniatures, téléchargements interrompus (présélectionnés). |
| Dossiers et fichiers vides | Dossiers vides et fichiers de 0 octet (présélectionnés, suppression directe). |
| Applications rarement utilisées | Classées de la moins récemment utilisée à la plus récente ; toucher pour désinstaller. |
| Cache | Vide le cache de l'application ; ouvre le nettoyage de cache système ou les réglages de stockage d'Android (Android interdit de vider directement le cache des autres applications). |
| Corbeille | Les suppressions passent d'abord par la corbeille (30 jours par défaut, réglable : 7/15/30). Restauration possible ; l'espace n'est libéré qu'au vidage. |
| Rapport | Espace libéré définitivement, contenu de la corbeille, historique des opérations. |

**Utilisation d'une liste** : toucher une ligne pour cocher/décocher, appui long pour voir le chemin complet, puis **Supprimer** (confirmation demandée). Les photos et vidéos affichent une miniature.

## 4. Code PIN (optionnel)

Réglages → Code PIN : activer (4 à 8 chiffres), modifier ou désactiver. Une fois activé, il est demandé à l'ouverture et après 2 minutes d'absence ; les captures d'écran de l'application sont alors bloquées. Après 5 erreurs, 30 secondes d'attente.
En cas d'oubli : Réglages d'Android → Applications → Nettoyage Téléphone → Stockage → Effacer les données (la corbeille et l'historique sont alors réinitialisés).

## 5. Bon à savoir

- Désactiver la corbeille (Réglages) supprime définitivement et immédiatement.
- Le nettoyage de la RAM n'est volontairement pas proposé : inefficace sur Android récent.
- Relancer l'analyse après de gros changements pour actualiser les chiffres.
