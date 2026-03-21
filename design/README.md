# Dictus Android — Design Reference

Maquettes de l'application Android dans `dictus-android-design.pen`.
Ce fichier est lisible via les outils MCP Pencil (`batch_get`, `get_screenshot`, `snapshot_layout`).

## Ecrans

| Ecran | Frame ID | Description |
|-------|----------|-------------|
| Home Tab | `wrqi0` | Logo 3 barres, carte modele actif, carte derniere transcription, bouton "Nouvelle dictee", bottom nav |
| Models Tab | `kSWvw` | Sections Telecharges/Disponibles, model cards avec jauges precision/vitesse, badges engine |
| Settings Tab | `CqKvx` | Sections Transcription/Clavier/A propos, toggles, liens |
| Recording Screen | `0yEaN` | Overlay plein ecran : waveform 30 barres, timer, bouton stop rouge |
| Waveform Spec | `VUDbq` | Documentation technique : couleurs, 3 etats visuels avec mini-waveforms, dimensions, specs Android |

## Composants reutilisables

| Composant | ID | Description |
|-----------|----|-------------|
| BottomNavBar | `d7cJl` | Pill tab bar 3 onglets (Accueil, Modeles, Reglages) |
| GlassCard | `eKShU` | Carte glass : surface #161C2C, border #FFFFFF15, corner 16px, shadow |

## Design System

### Couleurs
- Background: `#0A1628`
- Surface/Card: `#161C2C`
- Accent: `#3D7EFF`
- Accent highlight: `#6BA3FF`
- Accent dark: `#2563EB`
- Recording: `#EF4444`
- Success: `#22C55E`
- Text primary: `#FAFAF9`
- Text secondary: `#6B6B70`
- Border subtle: `#2A2A3E`
- Border glass: `#FFFFFF15`

### Typography
- Font: Inter (toutes les tailles)
- Weights: 400 (body), 500 (labels), 600 (titles), 700 (headers)

### Glass Effect (adaptation Liquid Glass iOS -> Android)
- Surface `#161C2C` + border 1px `#FFFFFF15` + shadow(0, 4, 16, #00000040)
- Press animation: scale 0.96 + opacity 0.85, spring(dampingRatio=0.6)
- Corner radius: 16-20px cards, 31px pill nav bar

### Waveform
- 30 barres, espacement 2px, largeur dynamique
- Rendu via Canvas/CustomView (pas 30 Views separees)
- Centre (barres 12-18): gradient #6BA3FF -> #2563EB
- Bords (barres 1-11, 19-30): blanc opacity 15% -> 90%
- Etats: idle (plat 2px), recording (energie live), transcribing (sinusoidal)

## Utilisation

Pour consulter un ecran pendant le developpement :
```
get_screenshot(filePath: "design/dictus-android-design.pen", nodeId: "wrqi0")
```

Pour lire la structure d'un composant :
```
batch_get(filePath: "design/dictus-android-design.pen", nodeIds: ["d7cJl"], readDepth: 3)
```
