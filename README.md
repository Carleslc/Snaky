# Snaky

[![Lichess](https://img.shields.io/badge/Lichess-Snaky--Bot-green?style=for-the-badge&logo=lichess&link=https%3A%2F%2Flichess.org%2F%40%2FSnaky-Bot)](https://lichess.org/@/Snaky-Bot)

A chess engine with a "constrictor" playing style: it gains space, controls squares and restricts the opponent's mobility, like a boa snake.

👉 **Play [Snaky-Bot](https://lichess.org/@/Snaky-Bot)** on Lichess.

This project is for personal learning. For professional purposes use advanced chess engines like Stockfish.

*Like the bot?* [![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/carleslc)

## Structure

- [`engine/`](engine): the engine, in Kotlin (bitboards, UCI).
- [`tools/`](tools): Python tooling around the engine (board and game viewer, analysis scripts, lichess-bot).
- [`docs/`](docs): documentation.

See the [PRD](docs/PRD.md) for the design.

Snaky was named Chessy until v1.01, and the first implementation (2018-2019), Chessy v0, is kept in the `v0` branch.

## Engine

Requires JDK 17+.

```
cd engine
./gradlew test
./gradlew installDist
build/install/snaky/bin/snaky play --time 3 --pgn games/last.pgn
build/install/snaky/bin/snaky bench
build/install/snaky/bin/snaky perft 6 --threads 8
```

Without a command, `snaky` speaks the [UCI](https://www.chessprogramming.org/UCI) protocol, so it can be used from any chess GUI, from `tools/match.py` or on Lichess with [lichess-bot](tools/lichess).

```
Usage: snaky [command] [arguments] [options]

Commands:
  perft <depth> [fen]    Count the leaf nodes of the legal move tree (default: starting position)
  divide <depth> [fen]   Perft for each legal move of the position
  d [fen]                Display the position
  moves [fen]            Legal moves of the position, in SAN and UCI notation
  eval [fen]             Evaluation of the position, term by term
  bench [depth]          Search fixed positions to a fixed depth (default: 12), to measure the speed
  play                   Play a game against Snaky in the console (moves in SAN or UCI notation)
  pgn <file>             Read the games of a PGN file and write them again (validates the moves)
  syzygy [fen]           Probe the Syzygy tablebases (--syzygy path): WDL and DTZ of the position, or of each FEN of the input
  weights                Weights of the Space style (or the Full style with --style full), in the format of a weights file
  tune <file>            Tune the weights of the Space style (or Full) with the positions of a training file (Texel's method)

Options:
  --threads <n>          Threads for perft and divide (default: available processors), bench (default: 1)
                         and play (default: 4, or the processors if fewer; 1 with --elo)
  --hash <mb>            Transposition table of bench (default: 64) and play (default: 256), in MB
  --style <name>         Playing style of eval, bench and play: space (default, the constrictor), full (the strongest),
                         material or greedy (the weakest)
  --depth <n>            Maximum depth of the search in play (fixed depth for greedy, default: 2)
  --elo <elo>            Play at this Elo (900 to 2800, in steps of 100) instead of the full strength of the style
  --book <on|off|file>   Opening book of play: the books of Snaky (default), none, or a Polyglot book file
  --opening <name>       Play this opening of the books of Snaky: KingsIndian, Benoni, French, CaroKann, Sicilian,
                         QueensGambit, RuyLopez or Pirc
  --chess960 <n>         Chess960: starting position number (0 to 959), random, or true (with --fen, and in divide and moves)
  --time <s>             Seconds to think per move in play (default: 3)
  --fen <fen>            Position of perft, divide, d, moves, eval and syzygy, or starting position of play (between quotes)
  --snaky <color>        Pieces played by Snaky in play: white, black (default), both or none
  --pgn <file>           Save the game played to this PGN file (added at the end if the file exists)
  --syzygy <path|off>    Folder of the Syzygy tablebases (default: tools/syzygy if downloaded), or off to play without them
  --weights <file>       Weights file of the Space or Full style (default: the tuned weights), also the starting point of tune
  (and the options of tune: --output, --positions, --passes, --all, --freeze, --max-change)
```

### UCI

Commands of the UCI protocol, and some for debugging:

| Command | Description |
|---|---|
| `uci`, `isready`, `ucinewgame`, `quit` | Identification, synchronization, new game (clears the hash) and exit |
| `position [startpos \| fen <fen>] [moves <m1> ...]` | Sets the position, in UCI notation (in Chess960, castling as the king taking its rook) |
| `go [wtime btime winc binc movestogo] [depth n] [nodes n] [mate n] [movetime ms] [infinite] [ponder] [searchmoves ...]` | Searches with these limits and answers `bestmove` (and `ponder`) |
| `ponderhit`, `stop` | The opponent played the move pondered (the search goes on as a normal one), or stop now |
| `setoption name <name> value <value>` | Changes an option (below) |
| `d`, `moves`, `eval`, `perft <depth>`, `bench [depth]` | Debug: the position, its legal moves, its evaluation term by term, perft and the benchmark |

| Option | Type | Default | Description |
|---|---|---|---|
| `Style` | combo | Space | Playing style: Space (the constrictor), Full (all weights tuned for strength), Material (baseline) or Greedy (the weakest) |
| `Threads` | spin 1–256 | 4 | Search threads (Lazy SMP; fewer if the machine has fewer processors). The strength levels use one |
| `Hash` | spin 1–1024 | 256 | Size of the transposition table, in MB |
| `Move Overhead` | spin 0–5000 | 100 | Milliseconds kept on each move for communication delays |
| `Ponder` | check | false | Allows thinking on the opponent's time (`go ponder`, `ponderhit`) |
| `UCI_LimitStrength` | check | false | Plays at the Elo of `UCI_Elo` instead of the full strength |
| `UCI_Elo` | spin 900–2800 | 1500 | Elo of the strength levels, in steps of 100 (see *Strength levels*) |
| `OwnBook` | check | false | Plays the opening book of Snaky (off by default, as rating lists require) |
| `Opening` | combo | All | Plays only one opening of the book: KingsIndian, Benoni, French, CaroKann, Sicilian, QueensGambit, RuyLopez or Pirc |
| `Book File` | string | | Another opening book, in Polyglot format |
| `UCI_Chess960` | check | false | Chess960: castling in UCI as the king taking its rook |
| `SyzygyPath` | string | `tools/syzygy` if downloaded | Folders of the Syzygy tablebases (empty: without tablebases) |
| `SyzygyProbeDepth` | spin 1–100 | 1 | Minimum depth to probe the tablebases inside the search |
| `Greedy Depth` | spin 1–4 | 2 | Fixed depth of the Greedy style |
| `Weights` | string | | Weights file of the Space or Full style, instead of its tuned weights (`snaky weights` shows the format) |

`./gradlew distZip` packages the engine in `build/distributions/snaky-<version>.zip`, with the launchers `bin/snaky`
(macOS, Linux) and `bin/snaky.bat` (Windows), the license and its notice, to install it anywhere with Java 17+.

## How Snaky plays

Four playing styles (`Style`, `--style` in the command line):

- **Space** (default): the "constrictor" evaluation. Space, square control, safe mobility and restriction of the opponent,
  with the value of each piece depending on its activity (see `snaky eval`). Its weights are tuned with Texel's method,
  keeping the style.
- **Full**: the same evaluation with every weight tuned for strength, including the weights of the style:
  as strong as Space, it restricts the mobility of the opponent with active pieces rather than with space.
- **Material**: material and piece-square tables, the baseline evaluation.
- **Greedy**: a fixed-depth search counting only material (`Greedy Depth`), the weakest.

What Snaky implements (see the [PRD](docs/PRD.md) for the design and the experiments behind each technique):

- **Board**: bitboards with magic bitboards for the sliding pieces, Zobrist keys, legal move generation checked with perft.
- **Search**: alpha-beta (negamax) with iterative deepening, principal variation search and aspiration windows,
  quiescence search, a transposition table, null move pruning verified against zugzwang, late move reductions,
  reverse futility, futility and late move pruning (sooner when the position does not improve), SEE pruning of late quiet
  moves that hang the moved piece and of losing captures, ProbCut, check extension for checks that do not lose material,
  move ordering (hash move, MVV-LVA, killers, countermove, history), mate distance pruning, and Lazy SMP for several threads.
- **Evaluation**: tapered (middlegame and endgame), with material, piece-square tables, safe mobility, trapped and worst
  pieces, space, territory, restriction, outposts, rook files, pawn structure and passed pawns, threats, king safety,
  bishop pair, tempo and winnable endgames, an incentive to make progress, and a cache of evaluations; `snaky eval` shows
  every term. Weights tuned with Texel's method on self-play games and real games against engines.
- **Time**: a soft limit (the clock over 30 moves plus 3/4 of the increment) and a hard limit (at most a third of the
  clock), pondering, and a fast answer for a single legal move.
- **And**: an opening book of space openings, Syzygy tablebases (up to 5 pieces), Chess960, strength levels from 900 to 2800.

Each change is an experiment, accepted only if it plays better: a sequential probability ratio test (SPRT,
[`tools/sprt.sh`](tools/sprt.sh)) of up to 1000 games at 8+0.08 against the previous version, with balanced openings
played with both colors. A change is accepted when the test proves it (H1), or when it ends positive without regression;
changes that only make the engine faster (same search tree) are measured with `bench`. The strength is then checked
against Stockfish with limited strength, and the style with [`tools/style.py`](tools/style.py). All the experiments,
accepted and rejected, and the differences between versions are in the [PRD](docs/PRD.md) (§11 Experiments, §12 Versions).

## Strength and style

All the data of this section is of the last version, v1.03, unless noted; the history of each version and its
experiments are in the [PRD](docs/PRD.md) (§11, §12).

Strength measured against Stockfish 19 with limited strength (`UCI_LimitStrength`, `UCI_Elo`), 100 games per level
at 10+0.1 (v0: 40 games per level at 60+0.6), 1 thread, with the balanced openings of `tools/openings/balanced.epd`
(v1.03 with its default hash of 256 MB; Greedy, whose search does not change, was measured with v1.0 and 64 MB).
The `UCI_Elo` scale of Stockfish is anchored to the [CCRL Blitz](https://computerchess.org.uk/404/) list,
where Snaky would be ~100 Elo higher (see the CCRL estimate below, measured at 2+1).

| | Elo (Stockfish scale) | Against Stockfish | WAC (1 s) | STS (0.2 s) | STS Elo |
|---|---|---|---|---|---|
| **v0** (2018–2019, console only) | ~1500 | 87.5% vs 1320, 25% vs 1600 | 122/300 | 6735 | ~1750 |
| **v1 Greedy** (depth 2) | ~900 | 7% vs 1320; v0 wins 97% | 35/300 | 5556 | 1406 |
| **v1 Greedy** (depth 3) | ~1100 | 22% vs 1320 (50 games) | 91/300 | 5427 | 1368 |
| **v1 Greedy** (depth 4) | ~1120 | 24% vs 1320 (50 games) | 84/300 | 6030 | 1547 |
| **v1.03 Material** | ~2590 | 72% vs 2400, 51.5% vs 2600 | 280/300 | 9893 | 2053 |
| **v1.03 Space** (default) | ~2780 | 79.5% vs 2600, 35% vs 2800, 28.5% vs 3000, 9.5% vs 3190 | 283/300 | 11152 | 2290 |
| **v1.03 Full** | ~2805 | 78% vs 2600, 53% vs 2800, 23% vs 3000, 8% vs 3190 | 281/300 | 11193 | 2227 |
| Stockfish 19 (full strength, reference) | 3804 in CCRL Blitz (8 threads) | | 279/300 | 13876 (0.1 s) | 3832 |

- Fitting the four levels, v1.03 is **2782 ± 41** (Space) and **2806 ± 41** (Full), +30–60 over v1.02 against Stockfish
  (and +144 ± 35 in their direct match: games between versions of the same engine exaggerate the gains of the search).
  The `UCI_Elo` of Stockfish goes up to 3190.
- WAC: [Win At Chess](tools/suites/wac.epd), 300 tactical positions. STS: [Strategic Test Suite](tools/suites/sts.epd),
  1500 positional positions, points out of 15000 with `tools/sts.py`; STS Elo is the CCRL 40/4 estimate of
  [`sts_rating.py`](https://github.com/fsmosca/STS-Rating) (v0: from its score with the same formula).
  STS Elo underrates Snaky: it measures single positional moves, while Snaky gains its points in the search.
- WAC is from 1958 and some of its solutions are not the only winning move: Stockfish misses the expected move
  in 21 positions, usually playing another winning move (sometimes a faster mate). WAC measures finding the expected move,
  so it is useful to compare versions of Snaky, not to compare with Stockfish.
- Greedy has no quiescence search, so it does not see the recapture of its last capture (horizon effect): depth 4 is barely
  better than depth 3. Its results are not monotonic: odd depths end on its own move and see more captures (better in WAC),
  even depths end on the reply of the opponent and are more careful (better in STS). Greedy searches a fixed depth,
  so the time per position does not change its moves.

STS by theme (`tools/sts.py`, 0.2 s per position, points out of 1000; Material, Space and Full v1.03; Stockfish with 0.1 s):

| Theme | v0 | Greedy | Material | Space | Full | Stockfish 19 |
|---|---|---|---|---|---|---|
| Undermining | 317 | 230 | 589 | **707** | 704 | 929 |
| Open files and diagonals | 344 | 251 | 517 | 705 | **731** | 912 |
| Knight outposts | 396 | 178 | 672 | **746** | 718 | 923 |
| Square vacancy | 312 | 197 | 603 | 676 | **730** | 941 |
| Bishop vs knight | 543 | **857** | 770 | 825 | 840 | 962 |
| Recapturing | 823 | 767 | 877 | 895 | **911** | 954 |
| Offer of simplification | 434 | 212 | 610 | 706 | **726** | 902 |
| Advancement of f/g/h pawns | 305 | 193 | 517 | **667** | 643 | 902 |
| Advancement of a/b/c pawns | 305 | 190 | 517 | **623** | 600 | 862 |
| Simplification | 652 | **865** | 769 | 822 | 819 | 967 |
| King activity | 389 | 276 | 649 | **673** | 653 | 909 |
| Center control | 436 | 264 | 720 | **793** | **793** | 923 |
| Pawn play in the center | 524 | 278 | 695 | 772 | **792** | 907 |
| 7th rank | 491 | 313 | 760 | 813 | **821** | 941 |
| Avoid pointless exchange | 464 | 485 | 628 | **729** | 712 | 942 |
| **Total** | 6735 | 5556 | 9893 | 11152 | **11193** | 13876 |

Space is better at outposts, the advance of the pawns on both wings, king activity and avoiding pointless exchanges;
Full at open files, square vacancy, recapturing and pawn play in the center (piece activity). Greedy only shines where the answer is a capture or an exchange (bishop vs knight, simplification).

Style metrics (`tools/style.py`, after the opening, against Stockfish at a similar level: v0 and Greedy against
1320–1600, Material v1.03 against 2600, Space and Full v1.03 against 2600 and 2800):

| | Opponent mobility | Space | Territory | Opponent trapped pieces | Wins behind in material |
|---|---|---|---|---|---|
| **v0** | 27.9 | 0.79 | −1.08 | 0.27 | 10% |
| **v1 Greedy** | 38.9 | −0.13 | −6.42 | 0.32 | 2 of 7 wins |
| **v1.03 Material** | 28.8 | 0.94 | −1.22 | 0.16 | 0% |
| **v1.03 Space** | 26.3 | **0.92** | 0.46 | **0.23** | 3% |
| **v1.03 Full** | **25.1** | 0.34 | **1.03** | 0.20 | 3% |
| Stockfish 2600 (against Space) | 28.1 | −1.08 | −1.40 | 0.23 | |
| Stockfish 2800 (against Space) | 21.9 | −0.71 | 0.96 | 0.17 | |

- **v0**: an alpha-beta search to depth 3–4 with material, piece-square tables and mobility: it already gains some space,
  but it does not control the half of the opponent (negative territory).
- **Greedy**: grabs material without looking further than two plies: it is passive, gives away territory
  and lets the opponent play (the highest opponent mobility).
- **Material**: plays sound chess without a plan for space: the opponent keeps its mobility and territory.
- **Space**: the constrictor. It gains the most space and traps the most pieces.
- **Full**: the most restrictive with the mobility of the opponent, with more territory but less space than Space:
  it plays for activity and material more than for space.
- Against Stockfish 2800, Stockfish restricts Snaky more (Snaky keeps 21.9 moves, Stockfish 26.2) and takes more
  territory, but Snaky still gains more space; against 2600 Snaky restricts Stockfish more (Stockfish keeps 26.5 moves,
  Snaky 28.1) and gains more space and territory.

### Threads and hash

Since v1.01 Snaky plays with 4 threads and 256 MB of hash by default (before, 1 thread and 64 MB). Against Stockfish
(1 thread), with the book and the tablebases, 100 games per level at 10+0.1 (3 games at a time with 4 threads, 14 cores):

| Against Stockfish | 2800 | 3000 | Elo (fit of both levels) |
|---|---|---|---|
| Space, 1 thread, 256 MB | 44.5% | 29% | 2798 ± 52 |
| **Space, 4 threads, 256 MB** | 65% | 42.5% | **2928 ± 50** (+130) |
| Full, 1 thread, 256 MB | 60% | 26% | 2847 ± 51 |
| **Full, 4 threads, 256 MB** | 70.5% | 39.5% | **2938 ± 50** (+91) |

Lazy SMP searches about 2.5 times more nodes per second with 4 threads, about +50–65 Elo per doubling of the threads.

At 2+1, closer to a real game, with 4 threads against Stockfish 3000 (1 thread): Space 40% and Full 60% (20 games each),
50% together, **~3000 ± 75**. So the Lichess bot (Space, 4 threads) plays at about **2930–3000** in the scale of
Stockfish. This is not comparable with CCRL, where both engines play with one thread (see below).

### Strength levels (UCI_Elo)

With `UCI_LimitStrength` and `UCI_Elo` (or `--elo` in `play`), Snaky plays at an Elo from 900 to 2800 in steps of 100,
always with one thread (whatever `Threads`), as the levels were calibrated.
The lowest levels play the greedy search, the middle levels the Space style (or the style of the `Style` option)
limited to a number of nodes per move, so it keeps every technique of its search (quiescence, extensions, reductions)
and only sees less, and the top level plays Full without limit. The Elo of the limited search grows with the logarithm
of the nodes: about **148 Elo per doubling** (v1.03, 50 games against Stockfish per point, 10+0.1, 1 thread):

| Nodes per move | 64 | 256 | 1024 | 4096 | 16384 | 65536 | 262144 |
|---|---|---|---|---|---|---|---|
| Against Stockfish | 66% vs 1320 | 38% vs 1500 | 25% vs 1800 | 36% vs 2100 | 47% vs 2400 | 57% vs 2600 | 48% vs 2800 |
| Elo | ~1435 | ~1415 | ~1610 | ~2000 | ~2380 | ~2650 | ~2785 |

| UCI_Elo | 900 | 1000 | 1100 | 1200 | 1300 | 1500 | 1800 | 2000 | 2200 | 2500 | 2700 | 2800 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Plays | Greedy depth 2 | Greedy depth 2 | Greedy depth 3 | Greedy depth 4 | Space, 160 nodes | 409 nodes | 1.7k nodes | 4.3k nodes | 11k nodes | 44k nodes | 113k nodes | Full |

The nodes are reached in a fraction of a second, so the levels play fast and do not depend on the time control.
The weakest alpha-beta search plays at about 1420 even with very few nodes (it completes its first iterations), so the
level 1300 plays somewhat stronger than its Elo, and the levels from 1000 to 1200 use the greedy search
(v1.0 calibration: 135 Elo per doubling).

### CCRL estimate

The `UCI_Elo` of Stockfish is fitted to games against versions of the Stash engine with known CCRL Blitz ratings,
at 120+1 (Stockfish source, `search.h`; the Stash anchors are within ±100 Elo of CCRL and the highest one is 2744),
so it is roughly a **CCRL Blitz** scale. At the time control of CCRL Blitz (2+1) Snaky does better than at 10+0.1:

| Against Stockfish 2800 | 10+0.1 (100 games) | 2+1 (40 games) |
|---|---|---|
| Space | 35% (−108 ± 69) | 50% (0 ± 96) |
| Full | 53% (+21 ± 61) | 80% (+241 ± 136) |

Estimate: **~2850–2950 in CCRL Blitz** for Space and Full (65% together at 2+1, ~2910; v1.02 scored 61.3% and 60%),
with one thread
(CCRL Blitz plays each engine on one CPU; engines with more threads are rated in separate lists), with these caveats:
few games at 2+1 (±75 Elo for both styles together), this machine is faster than the reference machine of CCRL
(Intel i7-4770K), which makes 2+1 here a longer time control, and above 2744 the scale of Stockfish is extrapolated.

- **CCRL Blitz** (2+1, 890 engines): Space and Full would rank around 277–297, next to engines like OliThink 5.10.5 (2904),
  Murka 3 (2900), Daydreamer 2.0.0-pre2 (2898) or Colossus 2026a (2895). Material (~2590 at 10+0.1, ~2690 at 2+1
  if it gains as much) would be around rank 345 (Blunder 8.5.5 is 2663, rank 353), v0 (~1500) around rank 780
  (Magog 1.0, 1499) and Greedy (~900) near the bottom, around rank 872 (Megalodon 1.0.0, 930).
- **CCRL 40/2 FRC** (Chess960, 151 engines): the same engines are 20–50 Elo lower there than in Blitz, so Space and Full
  would be ~2860–2900, around rank 101–104 (EXchess 7.92b 2897, Cepimetheus 17.0.0 2876, Daydreamer 2.0.0-pre2 2865).
  Snaky plays Chess960 since v1.01 (`UCI_Chess960`), so it could enter this list.

How to get a real CCRL rating is described in the [PRD](docs/PRD.md) (§9, CCRL).

## Play against Snaky

👉 **Play [Snaky-Bot](https://lichess.org/@/Snaky-Bot)**

### On Lichess (and with an electronic board)

Snaky can play on Lichess as a BOT with [lichess-bot](tools/lichess) (`LICHESS_BOT_TOKEN=xxx tools/lichess/run.sh`,
or on a server with Docker, so it stays online when your computer is off):
challenge it from your account, from the web or from an electronic board connected to Lichess (for instance, the ChessUp 2 connects to Lichess directly, and [chessconnect](https://chessconnect.de/) connects many boards to Lichess). A bot plays one configuration,
so the bot plays the constrictor style, Space, with 4 threads, pondering, its opening book and the tablebases (set `UCI_LimitStrength` and `UCI_Elo` in its configuration for a weaker bot). ChessConnect only connects boards to online sites, not to local engines, so playing on the board against a local Snaky needs the Lichess bot.
To watch Snaky play, [`tools/lichess/challenge.py`](tools/lichess/README.md#challenge-to-watch-a-game) makes the bot challenge a player or an online bot of similar rating.

Games are saved in PGN, so they can be opened with the viewer (`python tools/game.py games/last.pgn`) or analyzed in Lichess or Chess.com.

### In the console

Snaky plays in the console with moves in SAN (`e4`, `Nf3`, `O-O`, `exd6`, `a8=Q`) or UCI (`e2e4`).
This is also the way to play on an electronic board in two-player mode: play your move on the board, type it,
and play the answer of Snaky on the board. Commands: `moves`, `eval` (evaluation of the position, term by term),
`undo`, `pgn`, `resign`, `draw`, `quit`.

```
cd engine
./gradlew installDist
build/install/snaky/bin/snaky play --style space --time 5 --pgn games/space.pgn
build/install/snaky/bin/snaky play --style full --time 5 --snaky white
build/install/snaky/bin/snaky play --style material --time 5
build/install/snaky/bin/snaky play --style greedy --depth 3
build/install/snaky/bin/snaky play --elo 1600
build/install/snaky/bin/snaky play --opening French --snaky white
build/install/snaky/bin/snaky play --chess960 random
```

Snaky plays black by default (`--snaky white`, `both` or `none`), thinks `--time` seconds per move
(and `--depth` limits the search; for greedy it is its fixed depth), and saves the game with `--pgn`
(at the end of the file if it already exists, so a file can collect all the games against Snaky).
It plays with its opening book (`--book off` to disable it, `--opening` to play one opening of the book) and, if they are
downloaded in `tools/syzygy`, with the endgame tablebases (`--syzygy off` to disable them, or `--syzygy <folder>`).
Both are shown when the game starts.

When it starts, Snaky shows its style and its estimated Elo, which also go to the PGN (`White`/`Black` and
`WhiteElo`/`BlackElo` tags, `-` for the player). After each move it shows its evaluation in pawns from the point of view
of White, like the analysis boards: `+2.40 (White is better)`, `-0.35 (Black is better)` or `mate in 3 for Black`.

Chessy v0 (branch `v0`, Maven) also plays in the console, white by default:

```
git worktree add tools/engines/v0 v0
cd tools/engines/v0
mvn -q compile exec:java -Dexec.args="--black --time 5 --pgn games/v0.pgn"
```

v0 has no UCI, but [`tools/v0_uci.py`](tools/v0_uci.py) adapts it to play in chess GUIs and fastchess (see [tools](tools/README.md)).

### In a chess GUI

Without a command, `snaky` speaks UCI: add `engine/build/install/snaky/bin/snaky` as an engine in any UCI GUI
(Cute Chess, Arena, BanksiaGUI, En Croissant...) and choose the style in the engine options (`Style`), or an Elo
(`UCI_LimitStrength` and `UCI_Elo`). Set `OwnBook` to use its opening book (off by default in UCI, as the rating lists
require); the tablebases of `tools/syzygy` are used by default (`SyzygyPath`).

## License

Copyright (C) 2018-2026 Carles Lázaro Costa.

Snaky is free software, released under the [GNU General Public License v3.0](LICENSE) (GPLv3): you can use, study, modify and share it, and the versions you distribute must keep the same license and include their source code and attribution.

As stated in GPLv3 section 7(b), copies and modified versions must keep the attribution to its author, "Snaky by Carleslc (https://github.com/Carleslc/Snaky)", in their notices and documentation and as the author of the engine (the UCI `id author`), and modified versions must state that they were modified and in what way. See [NOTICE](NOTICE), which also credits the third-party work that Snaky uses or adapts (Stockfish, Syzygy, PolyGlot, lichess-bot...).
