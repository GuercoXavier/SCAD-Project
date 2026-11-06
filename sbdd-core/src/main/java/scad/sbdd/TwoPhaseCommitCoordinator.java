package scad.sbdd;

import java.sql.*;

public class TwoPhaseCommitCoordinator {

    // ==========================================
    // 1. ATUALIZAR STOCK (por delta: + repõe, - vende)
    // ==========================================
    public static boolean atualizarStock(int produtoId, int delta) {
        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[DbConfig.NODE_URLS.length];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC ATUALIZAR STOCK (ID: " + txId + ") delta=" + delta + " produto=" + produtoId + " ===");

        // FASE 1: PREPARE
        System.out.println("--- FASE 1: PREPARE ---");
        for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
            try {
                conns[i] = DriverManager.getConnection(DbConfig.NODE_URLS[i], DbConfig.USER, DbConfig.PASS);
                conns[i].setAutoCommit(false);

                String sql = "UPDATE stock SET quantidade = quantidade + ? WHERE produto_id = ?";
                PreparedStatement pstmt = conns[i].prepareStatement(sql);
                pstmt.setInt(1, delta);
                pstmt.setInt(2, produtoId);
                int rows = pstmt.executeUpdate();
                pstmt.close();

                if (rows == 0) {
                    throw new SQLException("produto_id " + produtoId + " não existe no nó " + i);
                }

                Statement stmt = conns[i].createStatement();
                stmt.execute("PREPARE TRANSACTION '" + txId + "_" + i + "'");
                stmt.close();
                System.out.println("[NÓ " + i + "] PREPARE OK");
            } catch (SQLException e) {
                System.out.println("[NÓ " + i + "] FALHOU O PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        // FASE 2: COMMIT ou ROLLBACK
        if (allPrepared) {
            System.out.println("--- FASE 2: COMMIT ---");
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    System.out.println("[NÓ " + i + "] COMMIT OK");
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro no COMMIT: " + e.getMessage());
                }
            }
            System.out.println("=== 2PC CONCLUÍDO COM SUCESSO ===");
            return true;
        } else {
            System.out.println("--- FASE 2: ROLLBACK ---");
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                if (conns[i] != null) {
                    try {
                        if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                        try (Statement stmt = conns[i].createStatement()) {
                            stmt.execute("ROLLBACK PREPARED '" + txId + "_" + i + "'");
                            System.out.println("[NÓ " + i + "] ROLLBACK PREPARED OK");
                        } catch (SQLException e) {
                            System.out.println("[NÓ " + i + "] (Transação não chegou a ser preparada)");
                        }
                        conns[i].close();
                    } catch (SQLException e) {
                        // Ignora erros de nós offline
                    }
                }
            }
            System.out.println("=== 2PC ABORTADO (Nenhum stock foi alterado) ===");
            return false;
        }
    }

    // ==========================================
    // 2. INSERIR NOVO PRODUTO (mesmo ID nos 2 nós)
    // ==========================================
    public static int inserirProduto(String nome, int quantidadeInicial) {

        // 1. Calcular próximo ID a partir do primeiro nó (consistente para todos)
        int novoId = -1;
        try (Connection conn = DriverManager.getConnection(DbConfig.NODE_URLS[0], DbConfig.USER, DbConfig.PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(produto_id), 0) + 1 FROM stock")) {
            if (rs.next()) novoId = rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("[2PC INSERT] Erro a obter próximo ID: " + e.getMessage());
            return -1;
        }

        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[DbConfig.NODE_URLS.length];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC INSERT (ID=" + novoId + ", nome=" + nome + ") ===");

        // FASE 1: PREPARE
        for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
            try {
                conns[i] = DriverManager.getConnection(DbConfig.NODE_URLS[i], DbConfig.USER, DbConfig.PASS);
                conns[i].setAutoCommit(false);

                String sql = "INSERT INTO stock (produto_id, nome, quantidade) VALUES (?, ?, ?)";
                PreparedStatement pstmt = conns[i].prepareStatement(sql);
                pstmt.setInt(1, novoId);
                pstmt.setString(2, nome);
                pstmt.setInt(3, quantidadeInicial);
                pstmt.executeUpdate();
                pstmt.close();

                // Alinhar a sequência SERIAL para futuros INSERTs manuais
                try (Statement fix = conns[i].createStatement()) {
                    fix.execute("SELECT setval('stock_produto_id_seq', (SELECT MAX(produto_id) FROM stock))");
                }

                Statement stmt = conns[i].createStatement();
                stmt.execute("PREPARE TRANSACTION '" + txId + "_" + i + "'");
                stmt.close();
                System.out.println("[NÓ " + i + "] PREPARE OK");
            } catch (SQLException e) {
                System.out.println("[NÓ " + i + "] FALHOU O PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        // FASE 2: COMMIT ou ROLLBACK
        if (allPrepared) {
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro no COMMIT: " + e.getMessage());
                }
            }
            System.out.println("=== 2PC INSERT CONCLUÍDO (produto ID=" + novoId + ") ===");
            return novoId;
        } else {
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                if (conns[i] != null) {
                    try {
                        if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                        try (Statement stmt = conns[i].createStatement()) {
                            stmt.execute("ROLLBACK PREPARED '" + txId + "_" + i + "'");
                        }
                    } catch (SQLException ignored) {}
                    try { conns[i].close(); } catch (SQLException ignored) {}
                }
            }
            System.out.println("=== 2PC INSERT ABORTADO ===");
            return -1;
        }
    }

    // ==========================================
    // 3. APAGAR PRODUTO
    // ==========================================
    public static boolean apagarProduto(int produtoId) {
        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[DbConfig.NODE_URLS.length];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC DELETE (ID=" + produtoId + ") ===");

        // FASE 1: PREPARE
        for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
            try {
                conns[i] = DriverManager.getConnection(DbConfig.NODE_URLS[i], DbConfig.USER, DbConfig.PASS);
                conns[i].setAutoCommit(false);

                String sql = "DELETE FROM stock WHERE produto_id = ?";
                PreparedStatement pstmt = conns[i].prepareStatement(sql);
                pstmt.setInt(1, produtoId);
                int rows = pstmt.executeUpdate();
                pstmt.close();

                if (rows == 0) {
                    throw new SQLException("produto_id " + produtoId + " não existe no nó " + i);
                }

                Statement stmt = conns[i].createStatement();
                stmt.execute("PREPARE TRANSACTION '" + txId + "_" + i + "'");
                stmt.close();
                System.out.println("[NÓ " + i + "] PREPARE OK");
            } catch (SQLException e) {
                System.out.println("[NÓ " + i + "] FALHOU O PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        // FASE 2: COMMIT ou ROLLBACK
        if (allPrepared) {
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro no COMMIT: " + e.getMessage());
                }
            }
            System.out.println("=== 2PC DELETE CONCLUÍDO ===");
            return true;
        } else {
            for (int i = 0; i < DbConfig.NODE_URLS.length; i++) {
                if (conns[i] != null) {
                    try {
                        if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                        try (Statement stmt = conns[i].createStatement()) {
                            stmt.execute("ROLLBACK PREPARED '" + txId + "_" + i + "'");
                        }
                    } catch (SQLException ignored) {}
                    try { conns[i].close(); } catch (SQLException ignored) {}
                }
            }
            System.out.println("=== 2PC DELETE ABORTADO ===");
            return false;
        }
    }

    // ==========================================
    // 4. EXECUTAR COMPRA (método legado — mantido para compatibilidade)
    // ==========================================
    public static boolean executarCompra(int produtoId, int quantidade) {
        return atualizarStock(produtoId, quantidade);
    }

    // ==========================================
    // MAIN (teste rápido manual)
    // ==========================================
    public static void main(String[] args) {
        // Teste: comprar 5 unidades do produto ID 1
        System.out.println("=== TESTE 2PC ===");
        boolean ok = atualizarStock(1, 5);
        System.out.println("Resultado: " + (ok ? "SUCESSO" : "FALHA"));
    }
}