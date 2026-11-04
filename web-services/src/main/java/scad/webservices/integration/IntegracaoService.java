package scad.webservices.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scad.dfs.DfsManager;
import scad.sbdd.TwoPhaseCommitCoordinator;
import scad.webservices.produto.Produto;
import scad.webservices.produto.ProdutoRepository;
import scad.webservices.sod.SodService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class IntegracaoService {

    private final DfsManager dfsManager = new DfsManager();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private SodService sodService;

    public Map<String, Object> comprarAoFornecedor(int produtoId, int quantidade) {

        Optional<Produto> optProduto = produtoRepository.buscarPorId(produtoId);
        if (optProduto.isEmpty()) {
            return erro("Produto com ID " + produtoId + " não existe no sistema.");
        }
        if (quantidade <= 0) {
            return erro("A quantidade deve ser maior que zero.");
        }
        Produto produto = optProduto.get();

        // ═══ O CORAÇÃO DO SOD: executa tudo dentro do lock ═══
        return sodService.executeWithLock(() -> {

            System.out.println("\n=== COMPRA: " + quantidade + "x " + produto.getNome()
                    + " (nó SOD " + sodService.getNodeId() + ") ===");

            Map<String, Object> passos = new LinkedHashMap<>();

            // Simular trabalho para que a exclusão mútua seja visível
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            // 1. SBDD: 2PC
            boolean ok = TwoPhaseCommitCoordinator.atualizarStock(produtoId, quantidade);
            passos.put("Sistema de base de dados", ok ? "Atualizado" : "Falhou");
            if (!ok) {
                Map<String, Object> r = erro("Não foi possível atualizar o stock.");
                r.put("passos", passos);
                return r;
            }

            // 2. DFS: fatura
            String faturaId = "fatura_" + System.currentTimeMillis() + ".pdf";
            try {
                String conteudo = "FATURA SCAD\nProduto: " + produto.getNome()
                        + "\nQuantidade: " + quantidade + "\nData: " + new java.util.Date();
                dfsManager.guardarFatura(faturaId, conteudo.getBytes());
                passos.put("Sistema de ficheiros", "Fatura guardada");
            } catch (Exception e) {
                passos.put("Sistema de ficheiros", "Erro: " + e.getMessage());
            }

            // 3. DFS: log
            String logId = "log_" + System.currentTimeMillis() + ".txt";
            try {
                dfsManager.guardarLog(logId, "[AUDITORIA] " + quantidade + "x " + produto.getNome());
            } catch (Exception ignored) {}

            // 4. SWD: fornecedor
            Object respostaFornecedor = null;
            try {
                String json = String.format(
                        "{\"produto\":\"%s\",\"quantidade\":%d,\"fornecedor\":\"Fornecedor SCAD\"}",
                        produto.getNome(), quantidade);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/fornecedores/comprar"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                respostaFornecedor = resp.body();
                passos.put("Fornecedor", "Pedido enviado");
            } catch (Exception e) {
                passos.put("Fornecedor", "Erro: " + e.getMessage());
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "SUCESSO");
            r.put("mensagem", "Compra de " + quantidade + "x " + produto.getNome() + " realizada.");
            r.put("faturaId", faturaId);
            r.put("logId", logId);
            r.put("respostaFornecedor", respostaFornecedor);
            r.put("passos", passos);
            return r;

        }, "Comprar " + quantidade + "x " + produto.getNome());
    }

    private Map<String, Object> erro(String mensagem) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ERRO");
        r.put("mensagem", mensagem);
        return r;
    }
}