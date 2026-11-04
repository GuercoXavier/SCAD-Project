package scad.webservices.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class IntegracaoController {

    @Autowired
    private IntegracaoService service;

    @PostMapping("/integrar")
    public Map<String, Object> integrar(@RequestBody Map<String, Object> pedido) {
        int produtoId = (int) pedido.getOrDefault("produtoId", 0);
        int quantidade = (int) pedido.getOrDefault("quantidade", 0);
        return service.comprarAoFornecedor(produtoId, quantidade);
    }
}