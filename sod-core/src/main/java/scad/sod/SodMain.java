package scad.sod;

public class SodMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: SodMain <nodeId> <ip1,ip2>");
            System.exit(1);
        }
        int nodeId = Integer.parseInt(args[0]);
        String[] ips = args[1].split(",");

        System.out.println("[SOD] Nó " + nodeId + " iniciado.");
        System.out.println("[SOD] Peers: " + String.join(", ", ips));
        System.out.println("[SOD] À escuta na porta " + (6000 + nodeId));

        new SodNode(nodeId, ips, 6000);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[SOD] A terminar...");
        }
    }
}