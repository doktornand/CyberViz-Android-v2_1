# CyberViz Android v2.1
### Aide à la vision augmentée — Plateforme de compensation visuelle en temps réel

> **Destiné aux ophtalmologues, orthoptistes et professionnels de la basse vision.**
> CyberViz est une application Android open source qui transforme la caméra d'un smartphone en prothèse visuelle portable. Elle applique en temps réel, image par image, des traitements d'image cliniquement orientés pour compenser ou contourner des déficits visuels liés à des pathologies rétiniennes, du nerf optique, de la perception des couleurs ou du champ visuel.

---

## Pourquoi CyberViz ?

Les aides optiques classiques (loupes, filtres colorés, lentilles de contact teintées) ont des limites physiques : un filtre jaune atténue les éblouissements, mais pas les halos. Une loupe grossit, mais ne compense pas un scotome central. CyberViz propose une approche **logicielle et adaptative** : le traitement est paramétrable, commutable en une seconde, et peut être ajusté par le clinicien ou le patient selon le contexte (lecture, déambulation, conduite, vision nocturne).

L'application fonctionne **en flux continu** sur la caméra arrière. Chaque image capturée (format YUV 420-888) est convertie en espace de couleur ARGB, traitée par le moteur `ImageProcessor`, puis affichée avec une latence inférieure à 40 ms sur la quasi-totalité des smartphones Android récents (ARM Cortex-A55 et supérieur). Aucun accès réseau n'est requis. Aucune donnée n'est transmise à l'extérieur.

---

## Architecture clinique : les catégories de modes

Les 38 modes de traitement sont organisés en **10 catégories cliniques** :

| Catégorie | Nbre de modes | Pathologies ciblées |
|---|---|---|
| `BASE` | 4 | Réglages universels |
| `ACCESSIBILITY` | 6 | Basse acuité, astigmatisme |
| `COLORBLIND` | 7 | Déficiences dyschromatopsiques |
| `ZOOM` | 2 | DMLA, scotome central |
| `PHOTOSENSITIVITY` | 6 | Éblouissement, cataracte, albinisme |
| `FIELD_OF_VIEW` | 5 | Hémianopsie, rétinite pigmentaire |
| `LOW_LIGHT` | 1 | Nyctalopie |
| `MOTOR` | 2 | Diplopie, nystagmus |
| `OCR` | 1 | DMLA, basse acuité avancée |
| `CREATIVE` | 6 | Rééducation, exploration |

---

## Description clinique détaillée de chaque mode

---

### CATÉGORIE BASE — Réglages fondamentaux

Ces quatre modes constituent le socle de l'application. Ils n'ont pas de pathologie cible unique mais sont utiles comme point de départ avant de sélectionner un mode spécialisé.

---

#### RAW — Flux caméra brut
**Paramètre :** aucun

Affiche le flux caméra sans aucun traitement. Sert de référence pour comparer la perception native et la perception améliorée. Utile lors des consultations pour montrer au patient l'effet "avant/après" d'un mode.

---

#### LUMINOSITÉ — Exposition globale
**Paramètre :** `expo` de 0 à 100 (défaut : 50)

Décale uniformément les trois canaux RVB d'une valeur proportionnelle au réglage (±127 niveaux). Équivalent numérique de l'exposition d'un appareil photo.

**Usage clinique :** compensation partielle d'une réduction de sensibilité à la lumière (amblyopie légère, patient post-opératoire en phase de récupération), ou adaptation à un environnement sous-éclairé.

---

#### CONTRASTE — Amplification du contraste global
**Paramètre :** `force` de 0 à 100 (défaut : 50)

Applique une multiplication centrée sur le niveau de gris 128 (formule de Photoshop CS) : chaque canal est éloigné ou rapproché du point médian. À force 100, le contraste est doublé.

**Usage clinique :** pathologies réduisant le contraste perçu comme les opacités cornéennes légères, la neurite optique en phase de récupération, ou le début de DMLA atrophique.

---

#### GAMMA — Correction de courbe tonale
**Paramètre :** `gamma` de 0,5 à 5,0 (défaut : 1,0)

Applique une correction gamma via une table de correspondance (LUT) précalculée. Gamma > 1 éclaircit les tons sombres sans saturer les hautes lumières. Gamma < 1 assombrit et renforce les contrastes dans les tons clairs.

**Usage clinique :** utile pour les patients présentant une sensibilité aux contrastes altérée de façon non-linéaire (glaucome débutant, neuropathie optique ischémique). La correction gamma permet de redistribuer les tons de façon perceptuellement homogène.

---

### CATÉGORIE ACCESSIBILITY — Amélioration de l'acuité perçue

---

#### HI-CONTRAST — Binarisation à seuil luminance
**Paramètre :** `seuil` de 50 à 100 (défaut : 70)  
**Tags cliniques :** basse acuité

Convertit chaque pixel en blanc pur ou noir pur selon que sa luminance dépasse ou non le seuil. Résultat : une image entièrement binaire, extrêmement contrastée.

**Usage clinique :** patients avec acuité inférieure à 1/20 (Snellen : 20/400) pour qui la reconnaissance de formes et contours reste possible même si le détail est perdu. Particulièrement efficace pour lire des pictogrammes, des flèches directionnelles, ou des chiffres d'affichage. Utile également dans les situations de fort contre-jour (sortie de tunnel, vitrines éclairées).

---

#### CONTOURS+ — Rehaussement des bords (Laplacien pondéré)
**Paramètre :** `force` de 1 à 20 (défaut : 5)  
**Tags cliniques :** basse acuité

Applique un filtre Laplacien sur chaque canal RVB séparément, puis ajoute le résultat à l'image source, pondéré par la force choisie. Contrairement à un simple filtre de bords, l'image conserve ses couleurs d'origine tout en voyant ses transitions renforcées.

**Usage clinique :** scotome paracentral (vision maculaire résiduelle), DMLA avec atrophie géographique, amblyopie légère à modérée. Le renforcement des bords compense la perte de netteté liée à une réduction de la densité des cellules ganglionnaires périmaculaires. En pratique : le patient perçoit les bords des objets, des lettres et des visages de manière plus marquée.

---

#### CONTOURS — Extraction des bords (gradient de Sobel)
**Paramètre :** `seuil` de 5 à 60 (défaut : 20)

Calcule le gradient de luminance par l'opérateur de Sobel (convolution 3×3 dans les deux axes). Affiche uniquement les bords détectés sur fond noir. L'image résultante est une carte de contours en niveaux de gris.

**Usage clinique :** évaluation diagnostique de la perception des contours. Peut servir de mode de rééducation visuelle basse vision, ou d'aide à la lecture en forçant l'attention sur la morphologie des lettres uniquement.

---

#### NÉGATIF — Inversion des couleurs
**Paramètre :** aucun  
**Tags cliniques :** photophobie

Inverse les trois canaux (R → 255−R, G → 255−G, B → 255−B). Transforme un fond blanc en fond noir, un texte noir en texte blanc.

**Usage clinique :** photophobie (migraine ophtalmique, iridocyclite, albinisme), achromatopsie complète (les porteurs trouvent souvent les fonds sombres moins agressifs). Reproduit le principe des affichages "Dark Mode" mais appliqué à la vision du monde réel. Particulièrement bénéfique en environnement très lumineux.

---

#### GRIS — Conversion en niveaux de gris
**Paramètre :** aucun

Convertit l'image en luminance pondérée (formule ITU-R BT.601 : L = 0,299×R + 0,587×G + 0,114×B).

**Usage clinique :** évaluation de la perception achromatique, préparation à l'usage d'autres modes monochromes. Utile pour les patients atteints de dyschromatopsie avancée cherchant à éliminer les interférences dues aux fausses couleurs perçues.

---

#### SEUIL — Binarisation ajustable
**Paramètre :** `seuil` de 10 à 245 (défaut : 128)

Similaire à HI-CONTRAST, mais avec un seuil entièrement configurable sur l'ensemble de la plage. Permet d'optimiser la binarisation selon l'éclairage ambiant et la nature du contenu visuel.

**Usage clinique :** personnalisation fine pour patients à basse vision avancée. Un seuil bas préserve les zones sombres (utile pour lire sur papier blanc), un seuil haut efface les zones grises intermédiaires (utile pour les affiches sur fond coloré).

---

#### ASTIGMATISME — Flou directionnel compensatoire
**Paramètre :** `axe` de 0° à 180° (défaut : 90°)  
**Usage clinique :** astigmatisme

Applique un flou directionnel (moyenne mobile sur 7 pixels) dans l'axe défini, avec affichage d'un marqueur de l'axe d'astigmatisme en surimpression jaune. L'opération simule le comportement d'une lentille cylindrique.

**Usage clinique :** astigmatisme non corrigé ou sous-corrigé. Ce mode ne corrige pas l'astigmatisme au sens optique (ce qui nécessiterait une déconvolution paramétrique), mais permet au patient de percevoir à quelle direction son flou est associé — utile en rééducation ou pour visualiser l'axe d'astigmatisme lors d'une consultation. Peut servir d'outil pédagogique pour expliquer visuellement le défaut à un patient.

---

### CATÉGORIE COLORBLIND — Compensation des dyschromatopsies

Ces modes s'adressent aux déficiences de la vision des couleurs, congénitales (daltonisme lié à l'X) ou acquises (pathologies toxiques, rétiniennes).

---

#### DEUTAN — Compensation deutéranopie / deutéranomalie
**Paramètre :** aucun  
**Tags cliniques :** deutéranopie

Applique une matrice de remappage des canaux simulant la substitution chromatique des photorécepteurs L (longs) vers la plage de sensibilité des cônes M. Résultat : renforcement de la discrimination rouge/vert.

**Usage clinique :** deutéranopie (absence des cônes M, ~5% des hommes) et deutéranomalie (cônes M décalés). Le mode renforce les contrastes rouge/vert en redistribuant l'énergie spectrale de manière à maximiser la discrimination perçue malgré l'absence ou l'anomalie du canal M.

---

#### PROTAN — Compensation protanopie / protanomalie
**Paramètre :** aucun  
**Tags cliniques :** protanopie

Matrice de remappage différente : compense la perte des cônes L (sensibles au rouge). Les rouges, perçus comme très sombres en protanopie, sont rehaussés par transfert d'énergie depuis le canal vert.

**Usage clinique :** protanopie et protanomalie (~1% des hommes). Améliore la visibilité des signaux rouges (feux de signalisation, alarmes incendie), qui sont une source connue de risque sécuritaire pour ces patients.

---

#### TRITAN — Compensation tritanopie
**Paramètre :** aucun  
**Tags cliniques :** tritanopie

Compense l'absence des cônes S (courts, sensibles au bleu). Réaffecte le canal bleu vers les canaux vert et rouge pour maximiser la discrimination dans les longueurs d'onde courtes.

**Usage clinique :** tritanopie (rare, ~1/50 000), souvent acquise (glaucome, dégénérescence rétinienne). Ce mode est également pertinent dans les dégénérescences maculaires touchant préférentiellement les cônes bleus (dystrophies rétiniennes de la couche des cônes S).

---

#### PALETTE CHAUDE — Remappage vers les teintes chaudes
**Paramètre :** aucun  
**Tags cliniques :** deutéranopie

Décale les canaux vers les teintes jaune-orangé (+40R, −20G, −30B). Renforce la discrimination dans le spectre moyen à long.

---

#### PALETTE FROIDE — Remappage vers les teintes froides
**Paramètre :** aucun  
**Tags cliniques :** protanopie

Décale les canaux vers les teintes bleu-cyan (−30R, +10G, +40B). Renforce la discrimination dans le spectre court à moyen.

---

#### PALETTE FEU — LUT thermique (noir → rouge → orange → blanc)
**Paramètre :** aucun

Mappe la luminance vers une rampe colorimétrique allant du noir au rouge, à l'orange puis au blanc. Inspire des représentations thermographiques.

**Usage clinique :** alternative colorimétrique pour patients présentant une vision résiduelle monochromatique. Le codage par "chaleur" peut faciliter la différenciation des zones claires et sombres.

---

#### PALETTE GLACE — LUT froide (noir → bleu → cyan → blanc)
**Paramètre :** aucun

Symétrique de la palette feu dans les longueurs d'onde courtes.

---

#### PALETTE NÉON — Amplification de la luminance en vert
**Paramètre :** aucun

Mappe la luminance vers une échelle de verts brillants (0 → 0, 255 → 510 → clamp 255). Reproduit l'aspect des écrans à phosphore vert des vieux oscilloscopes ou des lunettes de vision nocturne.

**Usage clinique :** patients avec sensibilité spectrale résiduelle dans le vert (pic de sensibilité de la rétine scotopique à ~507 nm). Utilisé conjointement avec le mode NUIT pour les situations de basse luminance.

---

### CATÉGORIE ZOOM — Agrandissement électronique

---

#### ZOOM ×2 — Agrandissement numérique 2×
**Paramètre :** aucun  
**Tags cliniques :** DMLA

Effectue un recadrage central au quart de la surface (zone centrale 50%×50%) et le redimensionne à la taille complète de l'écran par interpolation au pixel le plus proche.

**Usage clinique :** DMLA avec scotome central, toutes pathologies entraînant une réduction de l'acuité centrale. Permet d'agrandir le contenu central sans accessoire optique. Utilisé avec le mode EXCENTRATION, permet au patient de positionner précisément la zone agrandie dans son scotome paracentral.

---

#### ZOOM ×4 — Agrandissement numérique 4×
**Paramètre :** aucun  
**Tags cliniques :** DMLA

Même principe, recadrage sur 25%×25% de la surface centrale. Acuité numérique équivalente à une loupe 4 dioptries, sans contact, sans objet supplémentaire à transporter.

**Usage clinique :** basse vision avancée (acuité < 1/10), lecture de textes petits (ordonnances, étiquettes), identification de visages.

---

### CATÉGORIE PHOTOSENSITIVITY — Gestion de la photosensibilité

---

#### ANTI-ÉBLOUISSEMENT — Compression des hautes lumières
**Paramètre :** `force` de 0 à 100 (défaut : 50)

Applique une compression tonale au-dessus d'un seuil adaptatif (`knee`), en réduisant le gain des valeurs de luminance élevées. Le ratio de compression est fixé à 25% au-dessus du genou.

**Usage clinique :** photophobie (iritis, kératite, albinisme, migraine ophtalmique), cataracte sous-capsulaire postérieure (très susceptible à l'éblouissement), post-opératoire de chirurgie réfractive (LASIK, PRK). Reproduit l'effet des verres photochromiques sans le délai d'activation. Peut être utilisé en continu en intérieur éclairé ou sous lumière artificielle intense.

---

#### DÉJAUNISSEMENT — Correction de la teinte jaune
**Paramètre :** `intens.` de 0 à 100 (défaut : 60)

Décale le spectre couleur vers le bleu froid en atténuant le rouge et le vert et en renforçant le bleu (−15R, −5G, +35B à intensité maximale).

**Usage clinique :** cataracte nucléaire (jaunissement progressif du cristallin qui filtre les longueurs d'onde courtes). Le patient perçoit le monde avec une teinte jaunâtre ; ce mode compense en rétablissant l'équilibre spectral. Utile également dans la pseudophakie avec implant coloré ou chez les sujets âgés présentant un jaunissement pré-cataracteux.

---

#### ANTI-HALO — Réduction des halos lumineux
**Paramètre :** `seuil` de 20 à 150 (défaut : 60)

Compare chaque pixel à la moyenne de ses 8 voisins. Si la différence de luminance dépasse le seuil, le pixel est ramené au niveau moyen du voisinage (ratio avgN/L). Les pixels normaux sont conservés intacts.

**Usage clinique :** halos post-chirurgie réfractive (LASIK, implants multifocaux), début de cataracte postérieure, glaucome avancé. Les halos autour des sources lumineuses (phares, réverbères) sont l'une des plaintes fonctionnelles les plus invalidantes la nuit. Ce mode détecte et atténue les points lumineux anormalement isolés.

---

#### ACHROMATOPSIE — Simulation LUT pour vision scotopique
**Paramètre :** aucun

Convertit l'image en niveaux de gris via une LUT spécialisée qui compresse les hautes lumières (genou à 160, taux de compression 35% au-delà) pour reproduire la courbe de réponse d'une rétine fonctionnant uniquement en mode bâtonnets.

**Usage clinique :** achromatopsie complète (absence totale de cônes, acuité ~1/10, photophobie majeure, nystagmus). Ce mode reproduit la vision que le patient a *réellement*, permettant à l'ophtalmologue de comprendre son expérience visuelle. Il sert aussi d'aide à la vision en atténuant les éblouissements qui résultent de la surcharge des bâtonnets par des lumières intenses.

---

#### MÉLANOPIE — Optimisation pour les cellules ipRGC
**Paramètre :** aucun

Renforce le canal bleu (+50%), atténue le rouge (−20%) et amplifie légèrement le vert (+10%). Applique ensuite un micro-boost de contraste selon la zone tonale (zones claires : ×1,2 ; zones sombres : ×0,8).

**Usage clinique :** ce mode exploite la sensibilité maximale des cellules ganglionnaires intrinsèquement photosensibles (ipRGC, aussi appelées cellules mélanopsiques) qui répondent préférentiellement à la lumière bleue (~480 nm). Pertinent pour les patients dont la vision par les cônes et bâtonnets est très réduite mais dont les ipRGC sont préservées (stade avancé de rétinite pigmentaire, neuropathie optique héréditaire de Leber). Ces cellules ne forment pas d'images mais transmettent des informations sur la luminosité ambiante.

---

#### ALBINISME — Combinaison multi-étapes pour vision albinos
**Paramètre :** `force` de 0 à 100 (défaut : 50)

Pipeline de traitement en 4 étapes :
1. Conversion en luminance (LUT achromatopsie)
2. Compression des hautes lumières anti-éblouissement (intensité proportionnelle au paramètre)
3. Renforcement des contours (Laplacien, force 5 à 15)
4. Dilatation morphologique des bords + fusion pondérée avec l'image source

**Usage clinique :** albinisme oculo-cutané (OCA) et albinisme oculaire (OA). Ces patients cumulent trois déficits majeurs : achromatopsie partielle (hypopigmentation rétinienne), photophobie sévère (absence de mélanine irienne et choroïdienne), et basse acuité (hypoplasie fovéolaire). Ce mode combine dans un pipeline unique les compensations nécessaires à ces trois déficits simultanément.

---

### CATÉGORIE FIELD_OF_VIEW — Gestion du champ visuel

---

#### CONTRASTE LOCAL — CLAHE (égalisation adaptative par tuiles)
**Paramètre :** `force` de 0 à 100 (défaut : 60)

Divise l'image en tuiles de taille adaptative (1/16 de la dimension courte, 8–48 px), calcule les min/max locaux, puis effectue une normalisation par étirement de l'histogramme avec interpolation bilinéaire entre tuiles pour éviter les artefacts de frontière.

**Usage clinique :** équivalent logiciel du CLAHE (Contrast Limited Adaptive Histogram Equalization) utilisé en imagerie médicale. Particulièrement utile pour les patients dont le contraste perçu varie selon les zones du champ visuel (scotome sectoriel, glaucome avancé avec défect arciforme). Améliore la lisibilité dans les zones où l'œil a une sensibilité réduite sans saturer les zones encore fonctionnelles.

---

#### CHAMP LARGE — Dézoom avec bandes noires
**Paramètre :** `taille` de 30 à 90 (défaut : 60)

Rétrécit l'image dans une proportion définie et l'affiche centrée sur fond noir. Opération inverse du zoom : agrandit le champ capturé.

**Usage clinique :** rétinite pigmentaire (rétrécissement tubulaire du champ visuel), glaucome avancé (défects arciformes, atteinte du champ central). Ces patients voient au travers d'un "tunnel" ; ce mode leur permet de voir plus large sur l'écran qu'ils ne le pourraient avec leur champ résiduel seul. Utilisable également pour les porteurs de prothèses rétiniennes (Argus II, PRIMA) dont le champ stimulé est très réduit.

---

#### HÉMI DROIT — Affichage de l'héminappe droite uniquement
**Paramètre :** aucun

Conserve et étire la moitié droite de l'image sur toute la largeur de l'écran. La moitié gauche est masquée en noir.

**Usage clinique :** hémianopsie homonyme gauche (lésion des voies visuelles droites, notamment AVC sylvien droit, tumeur occipitale droite). Le patient ne perçoit pas le champ visuel gauche ; ce mode lui permet de voir le côté droit sans tourner la tête, en consultant l'écran. Peut être combiné avec des lunettes à prisme pour une aide fonctionnelle complète.

---

#### HÉMI GAUCHE — Affichage de l'héminappe gauche uniquement
**Paramètre :** aucun

Symétrique : conserve la moitié gauche de l'image.

**Usage clinique :** hémianopsie homonyme droite (lésion des voies visuelles gauches).

---

#### EXCENTRATION — Décalage du centre de capture
**Paramètre :** `angle` de 0° à 350° (défaut : 0°)

Effectue un zoom ×1,6 dont le centre de recadrage est décalé de 22% dans la direction angulaire choisie. Le décalage est calculé en coordonnées cartésiennes (cos/sin de l'angle en radians).

**Usage clinique :** vision excentrique. Lorsque le scotome central est très étendu (DMLA avancée, maculopathie cicatricielle), les patients développent naturellement un Preferred Retinal Locus (PRL) — une zone rétinienne périphérique qu'ils utilisent pour fixer. Ce mode permet de pointer la caméra vers un objet tout en faisant apparaître l'image sur la zone rétinienne fonctionnelle utilisée par le patient, selon l'angle de son PRL. Un ophtalmologue peut calibrer l'angle d'excentration lors d'une session de microperymétrie.

---

### CATÉGORIE LOW_LIGHT — Vision en basse luminance

---

#### NYCTALOPIE+ — Mode vision nocturne augmentée
**Paramètre :** `gain` de 20 à 100 (défaut : 50)

Pipeline en deux étapes par pixel :
- Si l'amplitude du gradient de Sobel dépasse 25 : affichage en jaune-blanc pour signaler un bord (contour de danger potentiel)
- Sinon : affichage en vert amplifié (×gain/50) simulant une vision intensifiée

**Usage clinique :** nyctalopie (héméralopie). Causes fréquentes : carence en vitamine A, rétinite pigmentaire débutante, glaucome touchant les zones périphériques. Le gain de sensibilité en vert exploite la sensibilité photopique du système scotopique (courbe de Purkinje). Les contours sont mis en jaune pour alerter le patient d'obstacles potentiels dans un environnement sombre. Ce mode peut permettre à un patient nyctalope de se déplacer dans un couloir faiblement éclairé.

---

### CATÉGORIE MOTOR — Troubles moteurs et oculomoteurs

---

#### STABILISATION — Filtrage temporel inter-trames
**Paramètre :** `lissage` de 0 à 90 (défaut : 40)

Applique une moyenne exponentielle entre la trame courante et la trame précédente via un coefficient alpha = (1 − lissage/100). Avec alpha = 0,6 (lissage = 40), chaque pixel vaut 60% de la valeur courante et 40% de la valeur précédente.

**Usage clinique :** nystagmus. Le nystagmus pendulaire ou à ressort provoque une oscillation involontaire de l'image rétinienne à des fréquences de 1 à 10 Hz. Ce filtre temporel atténue les oscillations rapides sans perdre l'information de mouvement lent (déambulation, lecture de texte). L'effet est analogue à une stabilisation d'image optique (OIS) mais implémenté purement en logiciel. Également utile pour les tremblements essentiels qui affectent la tenue du téléphone.

---

#### MONO OCCLUSION — Occlusion alternante (amblyopie, diplopie)
**Paramètre :** `altern.` de 0 à 100 (défaut : 50)  
**Tags cliniques :** diplopie

Masque en noir alterné la moitié gauche ou droite de l'écran, séparées par un trait rouge. La fréquence d'alternance est proportionnelle au paramètre : bas → côté gauche fixe, haut → côté droit fixe, milieu → alternance entre 0,5 et 4 Hz. Un indicateur coloré (vert = gauche actif, bleu = droit actif) s'affiche en coin supérieur.

**Usage clinique :** diplopie binoculaire (vision double). Que la cause soit un strabisme, une paralysie oculomotrice, ou une décompensation de phorie, la vision double est désactivante. Ce mode fournit une occlusion fonctionnelle rapide sans patch adhésif. En mode alternatif, il reproduit le principe de l'occlusion alternée utilisée en rééducation orthoptique. Le clinicien peut choisir quelle moitié du champ est occultée selon l'œil dominant ou l'œil déviant.

---

### CATÉGORIE OCR — Aide à la lecture

---

#### LECTURE — Détection et binarisation de zones textuelles
**Paramètre :** `sensib.` de 20 à 100 (défaut : 60)  
**Tags cliniques :** DMLA, basse acuité

Algorithme en 4 étapes :

1. **Détection des zones textuelles** : calcul de la variance locale sur une fenêtre 5×5. Les zones à forte variance (texte, bords de caractères) sont marquées.
2. **Bounding box du texte** : détermination de la boîte englobante des régions à forte variance, avec marge de 5% de la dimension courte.
3. **Binarisation de Sauvola** : à l'intérieur de la boîte, binarisation adaptative basée sur la moyenne locale et l'écart-type local sur une fenêtre 7×7 (k = −0,2). Cette méthode est robuste aux variations d'éclairage et aux fonds hétérogènes.
4. **Atténuation du hors-texte** : zones hors de la boîte assombries à 33% pour ne pas distraire.

Un cadre cyan est tracé autour de la zone textuelle détectée. Si aucun texte n'est détecté, un cadre de guidage en pointillés est affiché.

**Usage clinique :** DMLA (scotome central rendant la lecture impossible ou pénible), basse acuité avancée. La binarisation de Sauvola est spécifiquement conçue pour les documents de mauvaise qualité ou sous éclairage variable — situations typiques de la vie quotidienne (ordonnances imprimées, menus, étiquettes de médicaments). Ce mode ne remplace pas une loupe électronique dédiée, mais offre une solution immédiate intégrée à l'application.

---

## Flux de traitement technique

```
Caméra (YUV_420_888)
        │
        ▼
FrameAnalyzer.analyze()
  ├── Conversion YUV → ARGB (BT.601)
  ├── ImageProcessor.process(src, dst, mode, param)
  │     └── [38 fonctions de traitement]
  ├── rawBitmap.setPixels(dst)
  └── Correction rotation (Matrix CameraX)
        │
        ▼
CyberVizOverlayView.setBitmap()
  └── Affichage sur Canvas
```

**Latence typique :** < 40 ms sur SoC Snapdragon 660 et équivalents.  
**Résolution traitée :** native caméra (généralement 1080×720 ou 1920×1080 selon le téléphone).  
**Thread de traitement :** `ExecutorService` à 1 thread dédié (séparé du thread UI).  
**Gestion mémoire :** buffers `src` et `dst` réutilisés entre trames (pas d'allocation à chaque frame).

---

## Accès par pathologie — Index rapide

| Pathologie | Modes recommandés |
|---|---|
| DMLA (scotome central) | ZOOM ×2, ZOOM ×4, EXCENTRATION, LECTURE, CONTRASTE_LOCAL |
| Rétinite pigmentaire | CHAMP_LARGE, CONTRASTE_LOCAL, NYCTALOPIE+ |
| Glaucome avancé | CHAMP_LARGE, HEMI_DROIT / HEMI_GAUCHE, CONTRASTE_LOCAL |
| Cataracte nucléaire | DÉJAUNISSEMENT, ANTI-ÉBLOUISSEMENT, CONTRASTE |
| Cataracte post-op | ANTI-HALO, ANTI-ÉBLOUISSEMENT |
| Deutéranopie | DEUTAN, PAL CHAUD |
| Protanopie | PROTAN, PAL FROID |
| Tritanopie | TRITAN |
| Achromatopsie | ACHROMATOPSIE, NÉGATIF, MÉLANOPIE |
| Albinisme | ALBINISME, ANTI-ÉBLOUISSEMENT, NÉGATIF |
| Nyctalopie | NYCTALOPIE+, NUIT, PAL NÉON |
| Photophobie | ANTI-ÉBLOUISSEMENT, NÉGATIF, ALBINISME |
| Hémianopsie gauche | HEMI_DROIT |
| Hémianopsie droite | HEMI_GAUCHE |
| Diplopie / Strabisme | MONO_OCCLUSION |
| Nystagmus | STABILISATION |
| Astigmatisme | ASTIGMATISME (diagnostic / pédagogie) |
| Basse acuité générale | HI-CONTRAST, CONTOURS+, SEUIL, GRIS |
| Neurite optique | CONTRASTE, GAMMA, CONTRASTE_LOCAL |

---

## Prérequis et installation

**Android :** 8.0 (API 26) minimum. Android 11+ recommandé.  
**Matériel :** Caméra arrière requise. Capteur de lumière ambiante recommandé (adaptation automatique de l'overlay).  
**Permissions :** `CAMERA` uniquement. Aucune permission réseau, stockage ou localisation.

```bash
git clone https://github.com/doktornand/CyberViz-Android-v2_1.git
cd CyberViz-Android-v2_1
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Une release précompilée (APK signé) est disponible dans l'onglet **Releases** du dépôt.

---

## Utilisation

- **Changer de mode :** glissement latéral sur l'overlay
- **Ajuster le paramètre :** glissement vertical (pour les modes avec curseur)
- **Retour au RAW :** double-tap

---

## Roadmap clinique envisagée

- Calibration du PRL par microperymétrie (import de fichier JSON)
- Mode Argus II / PRIMA (simulation de la résolution de prothèse rétinienne)
- Export d'une session de comparaison (avant/après) pour compte-rendu
- Mode Voice-over (synthèse vocale pour les zones textuelles détectées par LECTURE)
- Profils patients sauvegardables (combinaison de mode + paramètre)

---

## Licence et contact

Projet open source — INGEN Systems  
Dépôt : [github.com/doktornand/CyberViz-Android-v2_1](https://github.com/doktornand/CyberViz-Android-v2_1)

> *CyberViz n'est pas un dispositif médical certifié. Il est destiné à un usage de recherche, de démonstration clinique et d'aide à la vie quotidienne. Il ne se substitue pas à une prise en charge ophtalmologique.*
