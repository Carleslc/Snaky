package snaky.core

/**
 * Squares attacked by each piece from each square.
 *
 * Knights, kings and pawns use precomputed tables. Sliding pieces (bishops, rooks and queens) use magic bitboards
 * (https://www.chessprogramming.org/Magic_Bitboards): the blockers of the rays of a square, multiplied by a magic number
 * and shifted, index a table of precomputed attacks, a single lookup instead of a walk along the rays. The tables are
 * built at startup from the rays cut at their first blocker ("classical approach",
 * https://www.chessprogramming.org/Classical_Approach), which is kept as the reference.
 */
object Attacks {

    /** Ray directions as (file, rank) steps. */
    private enum class Direction(val fileStep: Int, val rankStep: Int) {
        NORTH(0, 1), NORTH_EAST(1, 1), EAST(1, 0), SOUTH_EAST(1, -1),
        SOUTH(0, -1), SOUTH_WEST(-1, -1), WEST(-1, 0), NORTH_WEST(-1, 1);

        /** Whether the square index increases along this direction, so the nearest blocker is the lowest square. */
        val isPositive = rankStep > 0 || (rankStep == 0 && fileStep > 0)
    }

    private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val KING_STEPS = Direction.values().map { it.fileStep to it.rankStep }

    private val knight = LongArray(64) { stepAttacks(it, KNIGHT_STEPS) }
    private val king = LongArray(64) { stepAttacks(it, KING_STEPS) }
    private val pawn = arrayOf(
        LongArray(64) { stepAttacks(it, listOf(-1 to 1, 1 to 1)) },  // White pawns capture northwards
        LongArray(64) { stepAttacks(it, listOf(-1 to -1, 1 to -1)) } // Black pawns capture southwards
    )

    /** rays[direction][square]: squares from [square] (excluded) to the edge of the board. */
    private val rays = Array(Direction.values().size) { direction ->
        LongArray(64) { square -> ray(square, Direction.values()[direction]) }
    }

    fun knightAttacks(square: Square): Bitboard = knight[square]

    fun kingAttacks(square: Square): Bitboard = king[square]

    /** Squares attacked by a pawn of [color] on [square] (only diagonal captures, not pushes). */
    fun pawnAttacks(color: Color, square: Square): Bitboard = pawn[color][square]

    fun bishopAttacks(square: Square, occupied: Bitboard): Bitboard = BISHOP_MAGICS.attacks(square, occupied)

    fun rookAttacks(square: Square, occupied: Bitboard): Bitboard = ROOK_MAGICS.attacks(square, occupied)

    /** Bishop attacks walking the rays, the reference of the magic tables. */
    fun classicalBishopAttacks(square: Square, occupied: Bitboard): Bitboard =
        rayAttacks(square, occupied, Direction.NORTH_EAST) or rayAttacks(square, occupied, Direction.SOUTH_EAST) or
        rayAttacks(square, occupied, Direction.SOUTH_WEST) or rayAttacks(square, occupied, Direction.NORTH_WEST)

    /** Rook attacks walking the rays, the reference of the magic tables. */
    fun classicalRookAttacks(square: Square, occupied: Bitboard): Bitboard =
        rayAttacks(square, occupied, Direction.NORTH) or rayAttacks(square, occupied, Direction.EAST) or
        rayAttacks(square, occupied, Direction.SOUTH) or rayAttacks(square, occupied, Direction.WEST)

    fun queenAttacks(square: Square, occupied: Bitboard): Bitboard = bishopAttacks(square, occupied) or rookAttacks(square, occupied)

    /** Squares attacked by a piece of [type] (not a pawn) on [square], given the [occupied] squares. */
    fun attacks(type: PieceType, square: Square, occupied: Bitboard): Bitboard = when (type) {
        KNIGHT -> knightAttacks(square)
        BISHOP -> bishopAttacks(square, occupied)
        ROOK -> rookAttacks(square, occupied)
        QUEEN -> queenAttacks(square, occupied)
        KING -> kingAttacks(square)
        else -> throw IllegalArgumentException("Pawn attacks depend on the color, use pawnAttacks")
    }

    /** The ray from [square] in [direction] until (and including) the first occupied square. */
    private fun rayAttacks(square: Square, occupied: Bitboard, direction: Direction): Bitboard {
        val ray = rays[direction.ordinal][square]
        val blockers = ray and occupied
        if (blockers == EMPTY) {
            return ray
        }
        val firstBlocker = if (direction.isPositive) lsb(blockers) else msb(blockers)
        // Remove the squares behind the first blocker
        return ray xor rays[direction.ordinal][firstBlocker]
    }

    /**
     * Magic bitboards of a sliding piece ("fancy" magics: each square has its own shift and part of a shared table).
     * The relevant blockers of a square are the squares of its rays except the edges (a piece on the edge cannot hide
     * anything behind it), and each subset of them indexes the attacks with `(blockers * magic) >>> shift`.
     */
    private class Magics(attacksOf: (Square, Bitboard) -> Bitboard) {
        private val masks = LongArray(64)
        private val magics = LongArray(64)
        private val shifts = IntArray(64)
        private val offsets = IntArray(64)
        private val table: LongArray

        fun attacks(square: Square, occupied: Bitboard): Bitboard =
            table[offsets[square] + ((occupied and masks[square]) * magics[square] ushr shifts[square]).toInt()]

        init {
            var size = 0
            for (square in 0 until 64) {
                val edges = ((RANK_1 or RANK_8) and rankMask(rankOf(square)).inv()) or ((FILE_A or FILE_H) and fileMask(fileOf(square)).inv())
                masks[square] = attacksOf(square, EMPTY) and edges.inv()
                shifts[square] = 64 - popCount(masks[square])
                offsets[square] = size
                size += 1 shl popCount(masks[square])
            }
            table = LongArray(size)
            val blockers = LongArray(MAX_SUBSETS)
            val reference = LongArray(MAX_SUBSETS)
            val tried = IntArray(MAX_SUBSETS) // Attempt that wrote each entry of the table of the square
            for (square in 0 until 64) {
                // Every subset of the mask (Carry-Rippler) with its attacks
                var subsets = 0
                var subset = EMPTY
                do {
                    blockers[subsets] = subset
                    reference[subsets] = attacksOf(square, subset)
                    subsets++
                    subset = (subset - masks[square]) and masks[square]
                } while (subset != EMPTY)
                // Random sparse numbers until one maps every subset to its attacks, sharing entries only with equal attacks
                tried.fill(0)
                val random = SparseRandom(SEEDS[rankOf(square)])
                var attempt = 0
                var magic: Long
                while (true) {
                    do {
                        magic = random.next()
                    } while (popCount((magic * masks[square]) ushr 56) < 6)
                    attempt++
                    var found = true
                    for (i in 0 until subsets) {
                        val index = ((blockers[i] * magic) ushr shifts[square]).toInt()
                        if (tried[index] < attempt) {
                            tried[index] = attempt
                            table[offsets[square] + index] = reference[i]
                        } else if (table[offsets[square] + index] != reference[i]) {
                            found = false
                            break
                        }
                    }
                    if (found) break
                }
                magics[square] = magic
            }
        }
    }

    /** xorshift64* with few bits set, the candidates of magic numbers. */
    private class SparseRandom(private var state: Long) {
        private fun nextLong(): Long {
            state = state xor (state ushr 12)
            state = state xor (state shl 25)
            state = state xor (state ushr 27)
            return state * 2685821657736338717L
        }

        fun next(): Long = nextLong() and nextLong() and nextLong()
    }

    /** Seeds by rank that find the magics quickly (the 64-bit seeds of Stockfish). */
    private val SEEDS = longArrayOf(728, 10316, 55013, 32803, 12281, 15100, 16645, 255)

    /** Subsets of the largest mask (a rook in a corner: 12 relevant squares). */
    private const val MAX_SUBSETS = 1 shl 12

    private val ROOK_MAGICS = Magics(::classicalRookAttacks)
    private val BISHOP_MAGICS = Magics(::classicalBishopAttacks)

    private fun stepAttacks(square: Square, steps: List<Pair<Int, Int>>): Bitboard {
        var attacks = EMPTY
        for ((fileStep, rankStep) in steps) {
            val file = fileOf(square) + fileStep
            val rank = rankOf(square) + rankStep
            if (file in 0..7 && rank in 0..7) {
                attacks = attacks or bitboardOf(square(file, rank))
            }
        }
        return attacks
    }

    private fun ray(square: Square, direction: Direction): Bitboard {
        var ray = EMPTY
        var file = fileOf(square) + direction.fileStep
        var rank = rankOf(square) + direction.rankStep
        while (file in 0..7 && rank in 0..7) {
            ray = ray or bitboardOf(square(file, rank))
            file += direction.fileStep
            rank += direction.rankStep
        }
        return ray
    }
}
