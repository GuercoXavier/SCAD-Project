package scad.sod;

import java.io.*;
import java.net.*;
import java.util.*;

public class SodNode {
    private final int myId;
    private int myPort;
    private long lamportClock = 0; // Relógio lógico
    private State state = State.RELEASED;
    private final Set<Integer> pendingReplies = new HashSet<>();
    private final List<Message> deferredRequests = new ArrayList<>();

    private enum State { RELEASED, WANTED, HELD }

    public SodNode(int myId) {
        this.myId = myId;
        this.myPort = NetworkConfig.BASE_PORT + myId;
    }

    // ==========================================
    // 1. EXCLUSÃO MÚTUA (RICART-AGRAWALA)
    // ==========================================
    public synchronized void requestCriticalSection(String action) {
        System.out.println("\n[NÓ " + myId + "] A pedir acesso à Secção Crítica para: " + action);
        state = State.WANTED;
        lamportClock++;

        pendingReplies.clear();
        for (int i = 0; i < NetworkConfig.NODE_IPS.length; i++) {
            if (i != myId) pendingReplies.add(i);
        }

        Message req = new Message(Message.Type.REQUEST, myId, lamportClock);
        broadcast(req);

        // Espera por todas as respostas
        while (!pendingReplies.isEmpty()) {
            try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        state = State.HELD;
        System.out.println("[NÓ " + myId + "] >>> DENTRO DA SECÇÃO CRÍTICA. A executar: " + action);

        // Simula o trabalho (ex: demora a contactar o fornecedor)
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        System.out.println("[NÓ " + myId + "] <<< A SAIR DA SECÇÃO CRÍTICA.");
        releaseCriticalSection();
    }

    private synchronized void releaseCriticalSection() {
        state = State.RELEASED;
        for (Message deferred : deferredRequests) {
            sendMessage(deferred.senderId, new Message(Message.Type.REPLY, myId, lamportClock));
        }
        deferredRequests.clear();
    }

    private synchronized void handleRequest(Message req) {
        lamportClock = Math.max(lamportClock, req.timestamp) + 1;

        boolean defer = (state == State.HELD) ||
                (state == State.WANTED && (req.timestamp < lamportClock || (req.timestamp == lamportClock && req.senderId < myId)));

        if (defer) {
            deferredRequests.add(req);
        } else {
            sendMessage(req.senderId, new Message(Message.Type.REPLY, myId, lamportClock));
        }
    }

    private synchronized void handleReply(Message reply) {
        pendingReplies.remove(reply.senderId);
        if (pendingReplies.isEmpty()) notifyAll();
    }

    // ==========================================
    // 2. SINCRONIZAÇÃO DE BERKELEY (Master no Nó 0)
    // ==========================================
    public void startBerkeleyMaster() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(30000); } catch (InterruptedException e) {} // A cada 30s
                System.out.println("\n[BERKELEY - MASTER] A sincronizar relógios...");

                long myTime = System.currentTimeMillis();
                long sumTime = myTime;
                int count = 1;

                for (int i = 0; i < NetworkConfig.NODE_IPS.length; i++) {
                    if (i == myId) continue;
                    try (Socket socket = new Socket(NetworkConfig.NODE_IPS[i], NetworkConfig.BASE_PORT + i);
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        out.writeObject(new Message(Message.Type.BERKELEY_TIME_REQ, myId, 0));
                        Message res = (Message) in.readObject();
                        sumTime += res.timeValue;
                        count++;
                    } catch (Exception e) { /* Nó offline */ }
                }

                long averageTime = sumTime / count;
                long adjustment = averageTime - myTime;

                System.out.println("[BERKELEY - MASTER] Ajuste calculado: " + adjustment + "ms");

                // Envia o ajuste para os escravos
                for (int i = 0; i < NetworkConfig.NODE_IPS.length; i++) {
                    if (i == myId) continue;
                    Message adj = new Message(Message.Type.BERKELEY_ADJUST, myId, 0);
                    adj.timeValue = adjustment;
                    sendMessage(i, adj);
                }
            }
        }).start();
    }

    private void handleBerkeleyRequest(Message req) {
        try (Socket socket = new Socket(NetworkConfig.NODE_IPS[req.senderId], NetworkConfig.BASE_PORT + req.senderId);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            Message res = new Message(Message.Type.BERKELEY_TIME_RES, myId, 0);
            res.timeValue = System.currentTimeMillis(); // Tempo local do escravo
            out.writeObject(res);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleBerkeleyAdjust(Message adj) {
        long currentTime = System.currentTimeMillis();
        long newTime = currentTime + adj.timeValue;
        System.out.println("[BERKELEY - ESCRAVO NÓ " + myId + "] Relógio ajustado em " + adj.timeValue + "ms. Nova hora: " + new java.util.Date(newTime));
    }

    // ==========================================
    // 3. INFRAESTRUTURA DE REDE (Sockets)
    // ==========================================
    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(myPort)) {
                System.out.println("[NÓ " + myId + "] À escuta na porta " + myPort);
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleIncoming(client)).start();
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void handleIncoming(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            Message msg = (Message) in.readObject();
            switch (msg.type) {
                case REQUEST: handleRequest(msg); break;
                case REPLY: handleReply(msg); break;
                case BERKELEY_TIME_REQ: handleBerkeleyRequest(msg); break;
                case BERKELEY_ADJUST: handleBerkeleyAdjust(msg); break;
                default: break;
            }
        } catch (Exception e) { /* Ignora desconexões */ }
    }

    private void broadcast(Message msg) {
        for (int i = 0; i < NetworkConfig.NODE_IPS.length; i++) {
            if (i != myId) sendMessage(i, msg);
        }
    }

    private void sendMessage(int targetId, Message msg) {
        new Thread(() -> {
            try (Socket socket = new Socket(NetworkConfig.NODE_IPS[targetId], NetworkConfig.BASE_PORT + targetId);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeObject(msg);
            } catch (IOException e) {
                System.out.println("[ERRO] Falha ao contactar Nó " + targetId);
            }
        }).start();
    }

    // ==========================================
    // 4. MAIN (Ponto de entrada para cada PC)
    // ==========================================
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java SodNode <ID_DO_NO (0, 1 ou 2)>");
            return;
        }

        int nodeId = Integer.parseInt(args[0]);
        SodNode node = new SodNode(nodeId);
        node.startServer();

        // Se for o Nó 0, ele é o Master do Berkeley
        if (nodeId == 0) {
            node.startBerkeleyMaster();
        }

        // Simulador: A cada 15 segundos, o nó tenta comprar algo (entrar na secção crítica)
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(15000); } catch (InterruptedException e) {}
                // Só tenta comprar se não estiver em baixo
                if (node.state != State.HELD) {
                    node.requestCriticalSection("Comprar Lote de Parafusos");
                }
            }
        }).start();
    }
}