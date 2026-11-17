package edu.sfsu.csc413.chess.view;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.model.Move;
import edu.sfsu.csc413.chess.model.Piece;
import edu.sfsu.csc413.chess.model.Position;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A chess board in a web browser, served by the JDK's built-in
 * {@code com.sun.net.httpserver}. Start it with the word {@code web} and open
 * the address it prints.
 *
 * <p>Like a window, a browser is event-driven: the server waits for requests,
 * and each request becomes one {@link PlayerAction} handed to
 * {@link GameController#handle}. So {@link #start} starts the server and
 * returns; it never starts the controller's ask-and-handle loop. The server's thread
 * keeps the program alive.
 *
 * <p>The page asks three things of the server:
 * <ul>
 *   <li>{@code GET /} — the page itself (HTML, CSS and a little JavaScript);
 *   <li>{@code GET /state} — the latest position as JSON;
 *   <li>{@code POST /action} — a line of text such as {@code e2e4} or
 *       {@code undo}, turned into an action by {@link PlayerAction#parse}. The
 *       reply is the new state.
 * </ul>
 *
 * <p>The view never decides a rule. The JSON carries the legal moves from
 * {@link Game#legalMoves()} so the page can highlight targets, and the status
 * from {@link Game#status()}; whatever the player clicks is sent to the
 * controller, which reports an illegal move through {@link #showMessage}.
 */
public class WebView implements BoardView {

    public static final int DEFAULT_PORT = 8080;

    private final int port;
    private final PrintStream log;

    /** Guards {@link #stateJson}, {@link #messages} and every call to the controller. */
    private final Object lock = new Object();
    private String stateJson = "{}";
    private final List<String> messages = new ArrayList<>();
    private boolean finished;

    private HttpServer server;
    private ExecutorService executor;
    private GameController controller;

    public WebView() {
        this(DEFAULT_PORT, System.out);
    }

    public WebView(int port, PrintStream log) {
        this.port = port;
        this.log = log;
    }

    /** Copies the game into JSON. Called by the controller after every change. */
    @Override
    public void render(Game game) {
        synchronized (lock) {
            stateJson = toJson(game);
        }
    }

    /** Queues a message for the page, and echoes it to the console. */
    @Override
    public void showMessage(String message) {
        synchronized (lock) {
            messages.add(message);
        }
        log.println(message);
    }

    /** Starts the HTTP server and returns at once. */
    @Override
    public void start(GameController controller) {
        this.controller = controller;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the web server on port " + port, e);
        }
        server.createContext("/", this::servePage);
        server.createContext("/state", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                send(exchange, 405, "text/plain", "GET only");
                return;
            }
            send(exchange, 200, "application/json", currentState());
        });
        server.createContext("/action", this::serveAction);
        // One thread, so requests are handled one at a time, in order.
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
        log.println("Open http://localhost:" + port + "/ in your browser. Press Ctrl+C to stop.");
    }

    /**
     * Called by the controller when the game is over or the player quits. The
     * page has already been sent the final position, so the server stops
     * shortly afterwards; with no threads left, the program exits.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (finished) {
                return;
            }
            finished = true;
        }
        if (server == null) {
            return;
        }
        Thread stopper = new Thread(() -> {
            server.stop(2);
            executor.shutdown();
            log.println("Web server stopped.");
        }, "web-view-stop");
        stopper.start();
    }

    // ------------------------------------------------------------------ handlers

    private void servePage(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/")) {
            send(exchange, 404, "text/plain", "Not found");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", PAGE);
    }

    private void serveAction(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "text/plain", "POST only");
            return;
        }
        String text;
        try (InputStream body = exchange.getRequestBody()) {
            text = new String(body.readNBytes(200), StandardCharsets.UTF_8);
        }
        synchronized (lock) {
            if (!finished) {
                messages.clear();
                // Only PlayerAction.parse knows the words "undo", "moves", "quit".
                controller.handle(PlayerAction.parse(text));
            }
        }
        send(exchange, 200, "application/json", currentState());
    }

    private String currentState() {
        synchronized (lock) {
            StringBuilder json = new StringBuilder(stateJson);
            json.setLength(json.length() - 1); // drop the closing brace
            json.append(",\"finished\":").append(finished);
            json.append(",\"messages\":[");
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(quote(messages.get(i)));
            }
            json.append("]}");
            return json.toString();
        }
    }

    private static void send(HttpExchange exchange, int code, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------ JSON

    /**
     * The board as 64 FEN letters (rank 8 first, file a first; empty is
     * {@code ""}), plus side to move, status, legal moves and the last move.
     */
    private static String toJson(Game game) {
        StringBuilder json = new StringBuilder("{\"board\":[");
        for (int rank = Position.BOARD_SIZE - 1; rank >= 0; rank--) {
            for (int file = 0; file < Position.BOARD_SIZE; file++) {
                Piece piece = game.board().pieceAt(new Position(file, rank));
                json.append(quote(piece == null ? "" : String.valueOf(piece.symbol())));
                if (rank > 0 || file < Position.BOARD_SIZE - 1) {
                    json.append(',');
                }
            }
        }
        json.append("],\"sideToMove\":").append(quote(game.sideToMove().name()));
        json.append(",\"status\":").append(quote(game.status().name()));
        json.append(",\"legalMoves\":[");
        List<Move> legal = game.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(legal.get(i).toString()));
        }
        List<Move> history = game.history();
        json.append("],\"lastMove\":")
                .append(history.isEmpty() ? "null" : quote(history.get(history.size() - 1).toString()));
        json.append(",\"moveCount\":").append(history.size());
        json.append('}');
        return json.toString();
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------------ page

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>CSC 413 Chess</title>
            <style>
              body { font-family: system-ui, sans-serif; background: #2b2b2b; color: #eee;
                     display: flex; flex-direction: column; align-items: center; margin: 16px; }
              h1 { font-size: 1.3rem; margin: 0 0 12px; }
              #board { display: grid; grid-template-columns: repeat(8, min(11vw, 64px));
                       grid-template-rows: repeat(8, min(11vw, 64px)); border: 4px solid #555;
                       user-select: none; }
              .sq { display: flex; align-items: center; justify-content: center; cursor: pointer;
                    font-size: min(8.5vw, 50px); line-height: 1; position: relative; }
              .light { background: #f0d9b5; } .dark { background: #b58863; }
              .last { box-shadow: inset 0 0 0 100px rgba(205,210,106,.55); }
              .check { box-shadow: inset 0 0 0 100px rgba(220,40,40,.65); }
              .sel { box-shadow: inset 0 0 0 100px rgba(246,246,105,.8); }
              .target::after { content: ""; position: absolute; width: 30%; height: 30%;
                               border-radius: 50%; background: rgba(20,85,30,.5); }
              .target.occupied::after { width: 86%; height: 86%; background: none;
                                        border: 5px solid rgba(20,85,30,.5); box-sizing: border-box; }
              .w { color: #fff; text-shadow: 0 0 2px #000, 0 0 2px #000, 0 0 1px #000; }
              .b { color: #111; text-shadow: 0 0 2px #eee, 0 0 1px #eee; }
              #status { font-size: 1.2rem; font-weight: bold; margin: 12px 0 4px; }
              #messages { min-height: 1.4em; color: #ffd27a; }
              #controls { margin-top: 10px; display: flex; gap: 8px; }
              button, input { font-size: 1rem; padding: 4px 10px; }
            </style>
            </head>
            <body>
            <h1>CSC 413 Chess</h1>
            <div id="board"></div>
            <div id="status"></div>
            <div id="messages"></div>
            <div id="controls">
              <input id="typed" size="8" placeholder="e2e4" aria-label="Type a move">
              <button id="send">Send</button>
              <button id="undo">Undo</button>
            </div>
            <script>
            const FIG = {K:'\\u265A',Q:'\\u265B',R:'\\u265C',B:'\\u265D',N:'\\u265E',P:'\\u265F'};
            const files = 'abcdefgh';
            let state = null, selected = null;

            function name(side) { return side === 'WHITE' ? 'White' : 'Black'; }
            function sq(i) { return files[i % 8] + (8 - Math.floor(i / 8)); }

            async function post(text) {
              selected = null;
              const r = await fetch('/action', {method: 'POST', body: text});
              show(await r.json());
            }

            function click(square) {
              if (!state || state.finished) return;
              const movable = state.legalMoves.some(m => m.startsWith(square));
              const msg = document.getElementById('messages');
              if (selected === null) {
                if (movable) { selected = square; msg.textContent = ''; }
                else msg.textContent = 'Pick one of ' + name(state.sideToMove) + "'s pieces that can move.";
              } else if (square === selected) {
                selected = null;
              } else if (movable) {
                selected = square;
              } else {
                post(selected + square);   // the controller decides; promotion becomes a queen
                return;
              }
              draw();
            }

            function draw() {
              const board = document.getElementById('board');
              board.innerHTML = '';
              const targets = selected === null ? [] :
                state.legalMoves.filter(m => m.startsWith(selected)).map(m => m.substring(2, 4));
              const inCheck = state.status === 'CHECK' || state.status === 'CHECKMATE';
              const king = state.sideToMove === 'WHITE' ? 'K' : 'k';
              state.board.forEach((p, i) => {
                const s = sq(i), d = document.createElement('div');
                const row = Math.floor(i / 8), col = i % 8;
                d.className = 'sq ' + ((row + col) % 2 === 0 ? 'light' : 'dark');
                if (state.lastMove && (state.lastMove.startsWith(s) || state.lastMove.substring(2, 4) === s))
                  d.classList.add('last');
                if (inCheck && p === king) d.classList.add('check');
                if (s === selected) d.classList.add('sel');
                if (targets.includes(s)) { d.classList.add('target'); if (p) d.classList.add('occupied'); }
                if (p) {
                  const span = document.createElement('span');
                  span.className = p === p.toUpperCase() ? 'w' : 'b';
                  span.textContent = FIG[p.toUpperCase()];
                  d.appendChild(span);
                }
                d.title = s;
                d.onclick = () => click(s);
                board.appendChild(d);
              });
              const side = name(state.sideToMove);
              document.getElementById('status').textContent = {
                IN_PROGRESS: side + ' to move',
                CHECK: side + ' to move \\u2014 Check!',
                CHECKMATE: 'Checkmate! ' + name(state.sideToMove === 'WHITE' ? 'BLACK' : 'WHITE') + ' wins.',
                STALEMATE: 'Stalemate. The game is a draw.'
              }[state.status];
              document.getElementById('undo').disabled = state.finished;
              document.getElementById('send').disabled = state.finished;
            }

            function show(s) {
              state = s;
              document.getElementById('messages').textContent = s.messages.join(' ');
              draw();
            }

            document.getElementById('undo').onclick = () => post('undo');
            document.getElementById('send').onclick = () => {
              const t = document.getElementById('typed');
              if (t.value.trim()) post(t.value);
              t.value = '';
            };
            document.getElementById('typed').addEventListener('keydown', e => {
              if (e.key === 'Enter') document.getElementById('send').click();
            });
            fetch('/state').then(r => r.json()).then(show);
            </script>
            </body>
            </html>
            """;
}
