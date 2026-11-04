package scad.webservices.sod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import scad.sod.SodNode;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Callable;

@Service
public class SodService {

    @Value("${sod.node-id:0}")
    private int nodeId;

    @Value("${sod.peers:127.0.0.1,127.0.0.1,127.0.0.1}")
    private String peers;

    @Value("${sod.base-port:6000}")
    private int basePort;

    private SodNode node;

    @PostConstruct
    public void init() {
        String[] ips = peers.split(",");
        node = new SodNode(nodeId, ips, basePort);
        System.out.println("[SOD] Inicializado como nó " + nodeId + " na porta " + (basePort + nodeId));
    }

    /**
     * Executa a ação com exclusão mútua distribuída.
     */
    public <T> T executeWithLock(Callable<T> action, String descricao) {
        final Object[] result = new Object[1];
        final Exception[] error = new Exception[1];

        node.executeWithLock(() -> {
            try {
                result[0] = action.call();
            } catch (Exception e) {
                error[0] = e;
            }
        }, descricao);

        if (error[0] != null) {
            if (error[0] instanceof RuntimeException) {
                throw (RuntimeException) error[0];
            }
            throw new RuntimeException(error[0]);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }

    public boolean isInCriticalSection() {
        return node.isInCriticalSection();
    }

    public int getNodeId() {
        return nodeId;
    }
}