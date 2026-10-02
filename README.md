# Oubliettes

Jeu de logique pour Android : retrouver les murs d'un donjon à partir des indices.

## Règles

- Les nombres en bord de grille donnent le nombre de murs par ligne et par colonne.
- Monstres et coffres ne sont jamais des murs.
- Chaque monstre est au fond d'un cul-de-sac, et chaque cul-de-sac contient un monstre.
- Chaque coffre est dans une salle au trésor : 3×3 cases libres, un seul coffre, une seule ouverture.
- Hors salles au trésor, les couloirs font une case de large (pas de bloc 2×2 libre).
- Toutes les cases libres sont reliées.

Les grilles sont générées par l'application et ont une solution unique.

## Installation

Télécharger l'APK depuis les [releases](https://github.com/all3f0r1/oubliettes/releases) et l'ouvrir sur le téléphone (Android 8.0+).

## Compilation

JDK 21 et SDK Android requis.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```
