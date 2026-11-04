package scad.sod;

import java.io.*;
import java.net.*;
import java.util.*;

public class SodNode {
    private final int myId;
    private final int myPort;
    private long lamportClock = 0;
    private State state = State.RELEASED;
    private final Set<Integer> pendingReplies = new HashSet<>();
    private final List<Message> deferredRequests = new ArrayList<>();
    private final String[] nodeIps;

    private enum State { RELEASED, WANTED, HELD }

    public SodNode(int myId, String[] nodeIps, int basePort) {
        this.myId = myId;
        this.nodeIps = nodeIps;
        this.myPort = basePort + myId;
        startServer();
    }

    /**
     * Executa uma ação dentro da secção crítica com exclusão mútua.
     * Bloqueia até conseguir o lock; liberta quando terminar.
     */
    public void executeWithLock(Runnable action, String descricao) {
        System.out.println("[SOD-" + myId + "] A pedir lock para: " + descricao);
        acquire();
        try {
            System.out.println("[SOD-" + myId + "] >>> NA SECÇÃO CRÍTICA: " + descricao);
            action.run();
        } finally {
            System.out.println("[SOD-" + myId + "] <<< A SAIR DA SECÇÃO CRÍTICA");
            release();
        }
    }

    public boolean isInCriticalSection() {
        return state == State.HELD;
    }

    // ============ RICART-AGRAWALA ============

    private synchronized void acquire() {
        state = State.WANTED;
        lamportClock++;
        pendingReplies.clear();
        for (int i = 0; i < nodeIps.length; i++) {
            if (i != myId) pendingReplies.add(i);
        }
        broadcast(new Message(Message.Type.REQUEST, myId, lamportClock));

        // Espera por todas as respostas
        long start = System.currentTimeMillis();
        while (!pendingReplies.isEmpty()) {
            try {
                long timeout = 10000 - (System.currentTimeMillis() - start);
                if (timeout <= 0) break;
                wait(timeout);
            } catch (InterruptedException e) { break; }
        }
        state = State.HELD;
    }

    private synchronized void release() {
        state = State.RELEASED;
        for (Message deferred : deferredRequests) {
            sendMessage(deferred.senderId, new Message(Message.Type.REPLY, myId, lamportClock));
        }
        deferredRequests.clear();
    }

    private synchronized void handleRequest(Message req) {
        lamportClock = Math.max(lamportClock, req.timestamp) + 1;
        boolean defer = (state == State.HELD)
                || (state == State.WANTED
                && (req.timestamp < lamportClock
                || (req.timestamp == lamportClock && req.senderId < myId)));
        if (defer) {
            deferredRequests.add(req);
            System.out.println("[SOD-" + myId + "] Pedido do nó " + req.senderId + " adiado");
        } else {
            sendMessage(req.senderId, new Message(Message.Type.REPLY, myId, lamportClock));
        }
    }

    private synchronized void handleReply(Message reply) {
        pendingReplies.remove(reply.senderId);
        if (pendingReplies.isEmpty()) notifyAll();
    }

    // ============ SERVIDOR SOCKETS ============

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(myPort)) {
                System.out.println("[SOD-" + myId + "] À escuta na porta " + myPort);
                while (true) {
                    Socket client = ss.accept();
                    new Thread(() -> handleIncoming(client)).start();
                }
            } catch (IOException e) {
                System.err.println("[SOD-" + myId + "] Erro no server: " + e.getMessage());
            }
        }, "sod-server-" + myId).start();
    }

    private void handleIncoming(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            Message msg = (Message) in.readObject();
            switch (msg.type) {
                case REQUEST: handleRequest(msg); break;
                case REPLY: handleReply(msg); break;
                default: break;
            }
        } catch (Exception ignored) {}
    }

    private void broadcast(Message msg) {
        for (int i = 0; i < nodeIps.length; i++) {
            if (i != myId) sendMessage(i, msg);
        }
    }

    private void sendMessage(int targetId, Message msg) {
        new Thread(() -> {
            try (Socket s = new Socket(nodeIps[targetId], myPort - myId + targetId);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
            } catch (IOException e) {
                // Se o nó estiver offline, removemos da espera para não bloquear
                synchronized (this) {
                    pendingReplies.remove(targetId);
                    notifyAll();
                }
            }
        }).start();
    }
}