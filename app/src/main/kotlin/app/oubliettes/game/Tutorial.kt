package app.oubliettes.game

/** One deduction of the guided solve: what it lets the player mark, and which cells it is about. */
class TutorialStep(val text: String, val walls: List<Int> = emptyList(), val open: List<Int> = emptyList(), val focus: List<Int> = walls + open)

// '#' wall, '.' open, 'M' monster, 'C' chest.
private val DRAWING = listOf(
    "...###",
    ".C.#M#",
    ".....#",
    "####.#",
    "M....#",
    "######",
)
private const val W = 6
private val CELLS = DRAWING.joinToString("")

val tutorialSolution = BooleanArray(CELLS.length) { CELLS[it] == '#' }

val tutorialPuzzle = Puzzle(
    W, DRAWING.size,
    IntArray(DRAWING.size) { y -> DRAWING[y].count { it == '#' } },
    IntArray(W) { x -> DRAWING.count { it[x] == '#' } },
    CELLS.indices.filter { CELLS[it] == 'M' }.toSet(),
    CELLS.indices.filter { CELLS[it] == 'C' }.toSet(),
)

private fun c(x: Int, y: Int) = y * W + x
private fun row(y: Int, xs: IntRange) = xs.map { c(it, y) }
private fun col(x: Int, ys: IntRange) = ys.map { c(x, it) }

val tutorialSteps = listOf(
    TutorialStep(
        "Le but : retrouver tous les murs du donjon. Chaque nombre indique combien de murs contient " +
            "sa ligne ou sa colonne. Les monstres et les coffres ne sont jamais des murs.",
    ),
    TutorialStep(
        "La dernière ligne et la dernière colonne demandent 6 murs pour 6 cases : tout y est mur.",
        walls = row(5, 0..5) + col(5, 0..4),
    ),
    TutorialStep(
        "Les lignes 3 et 5 ne demandent qu'un mur, et il est déjà posé à droite. Leurs autres cases " +
            "sont donc libres : on les marque d'un point.",
        open = row(2, 0..4) + row(4, 1..4),
    ),
    TutorialStep(
        "Un monstre vit au fond d'un cul-de-sac : il n'a qu'une seule case libre autour de lui. " +
            "Celui-ci en a déjà une en dessous, ses autres voisins sont donc des murs.",
        walls = listOf(c(4, 0), c(3, 1)),
        focus = listOf(c(4, 1), c(4, 0), c(3, 1), c(4, 2)),
    ),
    TutorialStep(
        "La colonne 5 a ses 2 murs : sa dernière case inconnue est libre. La ligne 4 demande alors " +
            "5 murs et il ne lui reste que 5 cases possibles : toutes sont des murs.",
        walls = row(3, 0..3),
        open = listOf(c(4, 3)),
    ),
    TutorialStep(
        "La colonne 4 demande 4 murs. Elle en a 3 et une seule case inconnue : c'est le quatrième.",
        walls = listOf(c(3, 0)),
    ),
    TutorialStep(
        "Les lignes 1 et 2 ont maintenant tous leurs murs : ce qui reste est libre.",
        open = row(0, 0..2) + listOf(c(0, 1), c(2, 1)),
    ),
    TutorialStep(
        "Le coffre est dans une salle au trésor : 3×3 cases libres, un seul coffre (n'importe où dans " +
            "la salle) et une seule sortie. Partout ailleurs les couloirs font une case de large, sans " +
            "bloc libre de 2×2, et tout le donjon est relié. Donjon résolu !",
        focus = (0..2).flatMap { row(it, 0..2) },
    ),
)
