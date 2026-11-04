package scad.webservices.produto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scad.sbdd.TwoPhaseCommitCoordinator;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoRepository repo;

    @GetMapping
    public List<Produto> listar() {
        return repo.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscar(@PathVariable int id) {
        return repo.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody Map<String, Object> body) {
        String nome = (String) body.get("nome");
        if (nome == null || nome.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "O nome do produto é obrigatório."));
        }
        int quantidade = body.containsKey("quantidade")
                ? ((Number) body.get("quantidade")).intValue()
                : 0;
        if (quantidade < 0) quantidade = 0;

        int novoId = TwoPhaseCommitCoordinator.inserirProduto(nome.trim(), quantidade);
        if (novoId < 0) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("erro", "Não foi possível adicionar o produto. Verifique se a base de dados está acessível."));
        }
        return ResponseEntity.ok(Map.of(
                "id", novoId,
                "nome", nome.trim(),
                "quantidade", quantidade,
                "mensagem", "Produto adicionado com sucesso."
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> apagar(@PathVariable int id) {
        if (repo.buscarPorId(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        boolean ok = TwoPhaseCommitCoordinator.apagarProduto(id);
        return ok
                ? ResponseEntity.ok(Map.of("mensagem", "Produto removido."))
                : ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao remover."));
    }
}