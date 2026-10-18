# Snaky on Lichess

Snaky plays on [Lichess](https://lichess.org) as a BOT through [lichess-bot](https://github.com/lichess-bot-devs/lichess-bot),
which connects any UCI engine to the [Lichess Bot API](https://lichess.org/api#tag/Bot). Bots only play challenges
(from humans or other bots), not tournaments or the lobby pools.

Chess.com has no API for bots, so Snaky cannot play there (games can still be analyzed there from the exported PGN).

## Setup

1. Create a **new** Lichess account for the bot (for example *SnakyBot*). Upgrading an account to BOT is permanent
   and only works on accounts that have not played any game.
2. Logged in with that account, create an [API access token](https://lichess.org/account/oauth/token/create)
   with the `bot:play` scope. Keep it secret: anyone with it can play with the account.
3. Install lichess-bot (Python 3.10+) next to this repository, so that `lichess-bot` and `Snaky` share the parent folder:
   ```
   git clone https://github.com/lichess-bot-devs/lichess-bot.git
   cd lichess-bot
   python3 -m venv venv
   . venv/bin/activate
   pip install -r requirements.txt
   ```
   (or set `LICHESS_BOT_DIR` to its folder).
4. Check that everything works offline, before going online: a game of Snaky through lichess-bot against a mocked
   lichess.org (the one of the tests of lichess-bot), with Stockfish limited to 2000 as the opponent (or Snaky if there
   is no Stockfish). It needs no token,
   and checks the configuration, the UCI communication, pondering and the clock:
   ```
   pip install -r test_bot/test-requirements.txt   # in the lichess-bot folder, with its venv active
   tools/lichess/run.sh --test
   ```
5. Upgrade the account to BOT and start the bot (it needs Java 17+ to run Snaky):
   ```
   LICHESS_BOT_TOKEN=xxx tools/lichess/run.sh --upgrade
   ```
   The next times, without `--upgrade`:
   ```
   LICHESS_BOT_TOKEN=xxx tools/lichess/run.sh
   ```

`run.sh` builds the engine, copies [`config.yml`](config.yml) into the lichess-bot folder with the path of the engine and
starts lichess-bot. The token is only read from the `LICHESS_BOT_TOKEN` environment variable, never written in a file.
Stop the bot with Ctrl+C.

## Deploy on a server with Docker

lichess-bot and Snaky run on the machine that starts them: Lichess only relays the moves. To keep the bot online
when your computer is off, run it on a server (a VPS, a Raspberry Pi 4/5, the free tier of a cloud...) with Docker.
The image ([`Dockerfile`](Dockerfile)) builds the engine and installs lichess-bot (a tested version, `LICHESS_BOT_COMMIT`)
with Python and the Java runtime, for x86-64 and ARM64 servers (436 MB). Each game with the default configuration
needs 4 cores and 1.5 GB of RAM: `compose.yaml` gives the container 8 cores and 3 GB, for a second game started by hand
(see [Challenge to watch a game](#challenge-to-watch-a-game)); on a smaller server, lower `Threads` in `config.yml` (and `cpus` and `mem_limit` in `compose.yaml`).

1. On the server, install [Docker](https://docs.docker.com/engine/install/) and clone this repository.
2. Write the token of the BOT account in `tools/lichess/.env` (copy [`.env.example`](.env.example); it is ignored by git).
3. Optional: download the tablebases into `tools/syzygy` (939 MiB, see [tools](../README.md)); they are mounted read-only.
4. Check it offline, then upgrade the account to BOT (once) and start the bot:
   ```
   docker compose -f tools/lichess/compose.yaml build
   docker compose -f tools/lichess/compose.yaml run --rm bot test
   docker compose -f tools/lichess/compose.yaml run --rm bot upgrade
   docker compose -f tools/lichess/compose.yaml up -d
   ```
   ([`compose.yaml`](compose.yaml)). Stop `run --rm bot upgrade` with Ctrl+C once the account is upgraded (it starts playing too).
5. The bot restarts with the server (`restart: unless-stopped`). Follow it, stop it or update it:
   ```
   docker compose -f tools/lichess/compose.yaml logs -f
   docker compose -f tools/lichess/compose.yaml down
   git pull && docker compose -f tools/lichess/compose.yaml up -d --build
   ```

The image uses [`config.yml`](config.yml); to change it without rebuilding the image (another style, an Elo,
the accepted challenges...), mount a copy as `/snaky-config/config.yml` (commented in `compose.yaml`).
The offline test plays against Snaky with the greedy search, because the image has no Stockfish.

## Configuration

[`config.yml`](config.yml) sets:

- **Engine**: style Space, the constrictor (UCI option `Style`), 4 threads (`Threads`, up to the cores of the machine), 256 MB of hash
  and pondering (`ponder: true`: Snaky thinks on the opponent's time, in real-time games; not in correspondence games).
- **Challenges**: one game at a time (`concurrency: 1`), standard chess and Chess960, from bullet to classical (1 to 60 minutes, increment up to 30 s)
  and correspondence (1 to 14 days per move: Snaky thinks 1 minute per move and only takes the game slot while it plays),
  casual and rated, from humans and bots.
- **Draws and resignation**: Snaky resigns after 11 moves below −30 pawns (after a move of the opponent) and offers or accepts draws
  in drawn endgames: with 7 pieces or fewer, a score from −0.25 to 0 for 10 moves (a draw is −0.15, the contempt;
  [`snaky_bot.py`](snaky_bot.py) adds the upper bound, so Snaky does not offer draws when it is slightly better).
- **Time**: lichess-bot keeps 1 s for network delays (`move_overhead`) and Snaky 100 ms (`Move Overhead`):
  increase them if the bot loses on time. If the connection of a game breaks while it goes on, [`snaky_bot.py`](snaky_bot.py)
  reconnects it (lichess-bot would leave the game and lose it on time).
- The opening book and the Syzygy tablebases of Snaky (`OwnBook`, `SyzygyPath`, up to 5 pieces), and the online tablebases
  of Lichess (`online_egtb`) in endgames of up to 7 pieces with at least 10 s on the clock: they play won endgames
  perfectly, in drawn endgames Snaky chooses among the drawing moves, and lost endgames are played by Snaky
  ([`snaky_bot.py`](snaky_bot.py)): the longest defense of the tablebase gives away material without resistance,
  as every move loses against perfect play, while Snaky keeps it and makes the win harder.
  The other books and analysis of lichess-bot (`polyglot`, `online_moves`, `lichess_bot_tbs`) are disabled:
  Snaky plays every other move. Snaky does not resign or offer draws because of the online tablebases, as the opponent may still err.

To run another style (Full, as strong but less constrictor, Material or Greedy), change `Style` in `config.yml`; each style needs its own BOT account
to run at the same time.

## Chat commands

The bot runs lichess-bot through [`snaky_bot.py`](snaky_bot.py), which adds the commands of Snaky to the chat (`!help`):

- `!eval`: the score of the search of Snaky and its principal variation (lichess-bot).
- `!explain`, `!explain 23`, `!explain 23b`: the static evaluation of Snaky (without searching) term by term, now or after
  the move 23 of white or black, such as `After 4...Nf6 (static Full) -1.04: Mobility -0.68, Piece squares -0.40, ...`
  (in pawns from the point of view of white). Like `!eval`, only for the spectators (and the bot), not for the opponent.
- `!name` and `!queue` (lichess-bot): the engine and the queue of challenges.

`!wait` of lichess-bot (an extra minute before the first move, before the game is aborted) is disabled.

## Challenge to watch a game

The bot only plays the challenges it receives (`matchmaking` is off), but it can also challenge when you want to watch
a game: [`challenge.py`](challenge.py) challenges a player or a bot with the BOT account, and the running bot
(`run.sh` or Docker) plays the game like any other. It needs only Python 3 and reads the token from `.env`:

```
python challenge.py Maia1 --tc 3+2                       # a bot or a player (a player has 60 s to accept)
python challenge.py someone --days 3 --color white       # correspondence
python challenge.py --bot --tc 5+3 --rated               # online bots close to the rating of Snaky, until one accepts
python challenge.py --bot --tc 3+2 --min-rating 2600 --max-rating 3000 --variant chess960
python challenge.py --bot --tc 3+2 --list                # only list the bots it would challenge
python challenge.py --accept                             # list the challenges to Snaky
python challenge.py --accept someone                     # accept the challenge of someone (or its ID)
python challenge.py --pause                              # decline the challenges of bots, until --resume
python challenge.py --resume
```

It prints the link of the game to watch it. With Docker (on a server), run it in a new container of the image:
`docker compose -f tools/lichess/compose.yaml run --rm bot challenge --bot --tc 3+2`.
It does not challenge while Snaky is playing, and it cancels the challenge if Snaky starts another game while it waits
(`--force` to do it anyway). lichess-bot accepts one game at a time, and keeps waiting the challenges that arrive
while Snaky plays, but it plays every game that starts: a second game at the same time can be started by hand, challenging
with `--force` (both challenges with `--force` to send two at once) or accepting a waiting challenge with `--accept`.
Each game thinks with its own 4 threads: with `cpus: 8` in `compose.yaml` (and at least 8 CPUs for Docker Desktop)
two games do not slow each other down.
To keep Snaky free when you want to challenge a bot or play it, `--pause` declines the challenges of bots
("not accepting challenges at the moment") until `--resume`, while the challenges of players and those of `challenge.py`
are still played ([`snaky_bot.py`](snaky_bot.py) reads the file `state/paused`, a volume of `compose.yaml`).
While the rating of Snaky is provisional
(its first games), `--bot` looks for bots from 2200 to 2800. Bots can play 100 games a day against other bots
(unlimited against humans).

## Before going online

Test the engine locally with `tools/match.py`, which uses the same UCI protocol:

```
python match.py --games 100 --tc 60+1
```

## Playing with an electronic board

Once the bot is online, challenge it from your own (human) Lichess account. Any electronic board connected to Lichess
can then play against Snaky:

- **ChessUp 2**: connect the board to your Lichess account in the ChessUp app and challenge the bot.
- **[chessconnect](https://chessconnect.de/)** (Chrome extension, Android and iOS app) connects many boards, ChessUp included,
  to Lichess through its official API. It allows rapid, classical and correspondence games, and also blitz
  in direct challenges, such as a challenge to the bot.

chessconnect and the board apps only connect to online sites, not to local engines, so the Lichess bot is the way
to play Snaky on the board. A bot plays a single configuration: the default style, Space (set `Style` in `config.yml`
to run another one, for example a second bot account with `Full`).
