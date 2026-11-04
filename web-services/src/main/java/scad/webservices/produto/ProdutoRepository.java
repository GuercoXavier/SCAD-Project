package scad.webservices.produto;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class ProdutoRepository {

    private final ConcurrentHashMap<Integer, Produto> produtos = new ConcurrentHashMap<>();
    private final AtomicInteger proximoId = new AtomicInteger(1);

    public ProdutoRepository() {
        // Produtos de exemplo (simulam os dados das 3 BDs)
        criar(new Produto(0, "Parafusos", 50, "Norte"));
        criar(new Produto(0, "Porcas", 100, "Norte"));
        criar(new Produto(0, "Martelos", 30, "Sul"));
        criar(new Produto(0, "Chaves", 20, "Centro"));
    }

    public Produto criar(Produto p) {
        int id = proximoId.getAndIncrement();
        p.setId(id);
        produtos.put(id, p);
        return p;
    }

    public List<Produto> listarTodos() {
        return new ArrayList<>(produtos.values());
    }

    public Optional<Produto> buscarPorId(int id) {
        return Optional.ofNullable(produtos.get(id));
    }

    public Optional<Produto> atualizar(int id, Produto novo) {
        if (!produtos.containsKey(id)) return Optional.empty();
        novo.setId(id);
        produtos.put(id, novo);
        return Optional.of(novo);
    }

    public boolean apagar(int id) {
        return produtos.remove(id) != null;
    }

    public Optional<Produto> atualizarStock(int id, int delta) {
        Produto p = produtos.get(id);
        if (p == null) return Optional.empty();
        int nova = Math.max(0, p.getQuantidade() + delta);
        p.setQuantidade(nova);
        return Optional.of(p);
    }
}