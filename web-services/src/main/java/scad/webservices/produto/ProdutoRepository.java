package scad.webservices.produto;

import org.springframework.stereotype.Repository;

import org.springframework.stereotype.Repository;
import scad.sbdd.DbConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProdutoRepository {

    // Lê sempre do nó primário (Norte). Como é replicado via 2PC, os 3 nós têm os mesmos dados.
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DbConfig.NODE_URLS[0], DbConfig.USER, DbConfig.PASS);
    }

    public List<Produto> listarTodos() {
        List<Produto> lista = new ArrayList<>();
        String sql = "SELECT produto_id, nome, quantidade FROM stock ORDER BY produto_id";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            System.err.println("[REPO] Erro ao listar: " + e.getMessage());
        }
        return lista;
    }

    public Optional<Produto> buscarPorId(int id) {
        String sql = "SELECT produto_id, nome, quantidade FROM stock WHERE produto_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapear(rs));
            }
        } catch (SQLException e) {
            System.err.println("[REPO] Erro ao buscar: " + e.getMessage());
        }
        return Optional.empty();
    }

    private Produto mapear(ResultSet rs) throws SQLException {
        return new Produto(
                rs.getInt("produto_id"),
                rs.getString("nome"),
                rs.getInt("quantidade"),
                "Nacional"
        );
    }
}