package scad.webservices.produto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoRepository repo;

    // ============ LISTAR TODOS ============
    @GetMapping
    public List<Produto> listar() {
        return repo.listarTodos();
    }

    // ============ BUSCAR POR ID ============
    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscar(@PathVariable int id) {
        return repo.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ============ CRIAR NOVO PRODUTO ============
    @PostMapping
    public Produto criar(@RequestBody Produto p) {
        System.out.println("[PRODUTOS] Novo produto: " + p.getNome() + " (" + p.getQuantidade() + ") em " + p.getRegiao());
        return repo.criar(p);
    }

    // ============ ATUALIZAR PRODUTO ============
    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable int id, @RequestBody Produto p) {
        return repo.atualizar(id, p)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ============ APAGAR PRODUTO ============
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> apagar(@PathVariable int id) {
        boolean ok = repo.apagar(id);
        if (ok) {
            return ResponseEntity.ok(Map.of("status", "APAGADO", "id", id));
        }
        return ResponseEntity.notFound().build();
    }

    // ============ ENTRADA DE STOCK (receção) ============
    @PostMapping("/{id}/entrada")
    public ResponseEntity<Produto> entradaStock(@PathVariable int id, @RequestBody Map<String, Integer> body) {
        int quantidade = body.getOrDefault("quantidade", 0);
        return repo.atualizarStock(id, quantidade)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ============ SAÍDA DE STOCK (consumo) ============
    @PostMapping("/{id}/saida")
    public ResponseEntity<Produto> saidaStock(@PathVariable int id, @RequestBody Map<String, Integer> body) {
        int quantidade = body.getOrDefault("quantidade", 0);
        return repo.atualizarStock(id, -quantidade)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}