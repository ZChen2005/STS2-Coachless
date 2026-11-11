package coachless.GameSessionFramework.Network;

import coachless.GameSessionFramework.Player;
import coachless.GameSessionFramework.session.GameSession;
import coachless.GameSessionFramework.session.InvalidCodeException;
import coachless.GameSessionFramework.session.SessionException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/*
 * Runs inside the host's app. One text line per message:
 *   client -> host:   JOIN <code> <name>
 *   host -> client:   OK <playerId>   or   ERROR <reason>
 *   host -> everyone: LOBBY name1,name2,...   (whenever someone joins or leaves)
 *   both directions:  PING every 5s
 *   host -> everyone: CLOSED   
 */
public class HostServer {

    // How the host's app finds out what happened.
    public interface Listener {
        void onPlayersChanged(List<Player> players);
        void onEvent(String message);
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int HEARTBEAT_MS = 5000;
    private static final int TIMEOUT_MS = 15000;

    private final GameSession session;
    private final int requestedPort;
    private final Listener listener;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private final List<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile String latestState;   // last shared line (e.g. the run) then given to new joiners

    // Pass port 0 to let the OS pick a free port
    public HostServer(GameSession session, int port, Listener listener) {
        this.session = session;
        this.requestedPort = port;
        this.listener = listener;
    }

    // log text
    public HostServer(GameSession session, int port, Consumer<String> log) {
        this(session, port, new Listener() {
            @Override
            public void onPlayersChanged(List<Player> players) {
            }

            @Override
            public void onEvent(String message) {
                log.accept(message);
            }
        });
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        running = true;
        pool.submit(this::acceptLoop);
        heartbeat.scheduleAtFixedRate(() -> {
            for (PrintWriter w : clients) {
                w.println("PING");
            }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

    // Remembers one line of shared state and sends it to everyone connected.
    // Players who join later are sent the latest one automatically.
    public void publishState(String line) {
        latestState = line;
        for (PrintWriter w : clients) {
            w.println(line);
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        heartbeat.shutdownNow();
        for (PrintWriter w : clients) {
            w.println("CLOSED");
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        for (Socket s : sockets) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                pool.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running) {
                    listener.onEvent("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        PrintWriter out = null;
        Player player = null;           // set once this connection has joined
        String reason = "disconnected";
        try {
            // Applies to every read: a client that goes silent for 15s is treated as gone
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            AtomicInteger failures = failedAttempts.computeIfAbsent(ip, k -> new AtomicInteger());
            if (failures.get() >= MAX_FAILED_ATTEMPTS) {
                out.println("ERROR Too many failed attempts");
                return;
            }

            String line = in.readLine();
            String[] parts = line == null ? new String[0] : line.split(" ", 3);
            // commas are removed because LOBBY messages use them to separate names
            String name = parts.length < 3 ? "" : parts[2].trim().replace(",", "");
            if (parts.length < 3 || !parts[0].equals("JOIN") || name.isBlank()) {
                out.println("ERROR Bad request");
                return;
            }

            Player candidate = new Player(UUID.randomUUID().toString(), name);
            try {
                session.join(parts[1], candidate);
            } catch (InvalidCodeException e) {
                failures.incrementAndGet();
                listener.onEvent("Rejected join from " + ip + ": " + e.getMessage());
                out.println("ERROR " + e.getMessage());
                return;
            } catch (SessionException e) {
                listener.onEvent("Rejected join from " + ip + ": " + e.getMessage());
                out.println("ERROR " + e.getMessage());
                return;
            }
            player = candidate;

            out.println("OK " + player.id());
            clients.add(out);
            String state = latestState;
            if (state != null) {
                out.println(state); // catch the new player up on the latest shared state
            }
            listener.onEvent(player.name() + " joined (" + session.getPlayers().size()
                    + "/" + GameSession.MAX_PLAYERS + ")");
            playersChanged();

            // Stay connected. Any line means still alive.
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.equals("PING")) {
                    continue;
                }
                // Game-state messages from this player
            }
            reason = "left the session";
        } catch (SocketTimeoutException e) {
            reason = "timed out";
        } catch (IOException e) {
            reason = "connection lost";
        } finally {
            if (out != null) {
                clients.remove(out);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            sockets.remove(socket);
            // running == false means the host is shutting everything down
            if (player != null && running) {
                session.removePlayer(player);
                listener.onEvent(player.name() + " left (" + reason + ")");
                playersChanged();
            }
        }
    }

    private void playersChanged() {
        listener.onPlayersChanged(session.getPlayers());
        broadcastLobby();
    }

    private void broadcastLobby() {
        String names = session.getPlayers().stream()
                .map(Player::name)
                .collect(Collectors.joining(","));
        for (PrintWriter w : clients) {
            w.println("LOBBY " + names);
        }
    }
}
