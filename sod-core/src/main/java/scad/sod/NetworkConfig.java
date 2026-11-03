package scad.sod;

public class NetworkConfig {
    // Substituir pelos IPs reais dos teus 3 PCs
    // Se for para testar tudo no mesmo PC, usa "127.0.0.1"
    public static final String[] NODE_IPS = {
            "127.0.0.1", // PC 1 (Armazém Norte)
            "127.0.0.1", // PC 2 (Armazém Sul)
            "127.0.0.1"  // PC 3 (Auditor Central)
    };

    // A porta base. O PC 1 usa a porta 6000, PC 2 usa 6001, PC 3 usa 6002.
    public static final int BASE_PORT = 6000;
}