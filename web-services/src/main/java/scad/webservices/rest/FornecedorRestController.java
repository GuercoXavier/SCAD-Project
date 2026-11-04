package scad.webservices.rest;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/fornecedores")
public class FornecedorRestController {

    @PostMapping("/comprar")
    public Map<String, Object> comprar(@RequestBody Map<String, Object> pedido) {
        String produto = (String) pedido.get("produto");
        int quantidade = (int) pedido.get("quantidade");
        String fornecedor = (String) pedido.get("fornecedor");

        System.out.println("[REST] Pedido recebido:");
        System.out.println("  Produto: " + produto);
        System.out.println("  Quantidade: " + quantidade);
        System.out.println("  Fornecedor: " + fornecedor);

        // Simular processamento
        String ordemId = "ORD-" + System.currentTimeMillis();

        return Map.of(
                "status", "SUCESSO",
                "ordemId", ordemId,
                "mensagem", "Compra de " + quantidade + "x " + produto + " registada com sucesso.",
                "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/status/{ordemId}")
    public Map<String, Object> consultarStatus(@PathVariable String ordemId) {
        return Map.of(
                "ordemId", ordemId,
                "status", "EM_PROCESSAMENTO",
                "previsaoEntrega", "2 dias úteis"
        );
    }
}