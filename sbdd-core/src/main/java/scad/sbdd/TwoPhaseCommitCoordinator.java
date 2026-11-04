package scad.sbdd;

import java.sql.*;

public class TwoPhaseCommitCoordinator {

    public static boolean executarCompra(int produtoId, int quantidade) {
        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[3];
        boolean allPrepared = true;

        System.out.println("\n=== INICIANDO 2PC (ID: " + txId + ") ===");

        // ==========================================
        // FASE 1: PREPARE
        // ==========================================
        System.out.println("--- FASE 1: PREPARE ---");
        for (int i = 0; i < 3; i++) {
            try {
                conns[i] = DriverManager.getConnection(DbConfig.NODE_URLS[i], DbConfig.USER, DbConfig.PASS);
                conns[i].setAutoCommit(false);

                String sql = "UPDATE stock SET quantidade = quantidade - ? WHERE produto_id = ?";
                PreparedStatement pstmt = conns[i].prepareStatement(sql);
                pstmt.setInt(1, quantidade);
                pstmt.setInt(2, produtoId);
                pstmt.executeUpdate();
                pstmt.close();

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

        // ==========================================
        // FASE 2: COMMIT OU ROLLBACK
        // ==========================================
        if (allPrepared) {
            System.out.println("--- FASE 2: COMMIT (Todos os nós preparados!) ---");
            for (int i = 0; i < 3; i++) {
                try {
                    // A CORREÇÃO ESTÁ AQUI: Sair do modo de transação antes do COMMIT!
                    if (!conns[i].getAutoCommit()) {
                        conns[i].setAutoCommit(true);
                    }

                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                        System.out.println("[NÓ " + i + "] COMMIT OK");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro no COMMIT: " + e.getMessage());
                }
            }
            System.out.println("=== 2PC CONCLUÍDO COM SUCESSO ===");
            return true;
        } else {
            System.out.println("--- FASE 2: ROLLBACK (Algum nó falhou!) ---");
            for (int i = 0; i < 3; i++) {
                if (conns[i] != null) {
                    try {
                        // A MESMA CORREÇÃO AQUI PARA O ROLLBACK
                        if (!conns[i].getAutoCommit()) {
                            conns[i].setAutoCommit(true);
                        }

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

    /**
     * Atualiza o stock por delta.
     * delta > 0 -> reposição (compra ao fornecedor)
     * delta < 0 -> venda (saída de stock)
     */
    public static boolean atualizarStock(int produtoId, int delta) {
        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[3];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC (ID: " + txId + ") delta=" + delta + " produto=" + produtoId + " ===");

        // FASE 1: PREPARE
        for (int i = 0; i < 3; i++) {
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
                System.out.println("[NÓ " + i + "] FALHOU PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        // FASE 2
        if (allPrepared) {
            for (int i = 0; i < 3; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro COMMIT: " + e.getMessage());
                }
            }
            return true;
        } else {
            for (int i = 0; i < 3; i++) {
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
            return false;
        }
    }

    /**
     * Insere um novo produto nas 3 bases de dados com o MESMO ID.
     * Retorna o ID gerado, ou -1 se falhar.
     */
    public static int inserirProduto(String nome, int quantidadeInicial) {

        // 1. Calcular próximo ID (consistente nos 3 nós)
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
        Connection[] conns = new Connection[3];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC INSERT produto (ID=" + novoId + ", nome=" + nome + ") ===");

        // 2. FASE 1: PREPARE
        for (int i = 0; i < 3; i++) {
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

                // Alinhar a sequência SERIAL para futuros INSERTs diretos (via DBeaver)
                try (Statement fix = conns[i].createStatement()) {
                    fix.execute("SELECT setval('stock_produto_id_seq', (SELECT MAX(produto_id) FROM stock))");
                }

                Statement stmt = conns[i].createStatement();
                stmt.execute("PREPARE TRANSACTION '" + txId + "_" + i + "'");
                stmt.close();
                System.out.println("[NÓ " + i + "] PREPARE OK");
            } catch (SQLException e) {
                System.out.println("[NÓ " + i + "] FALHOU PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        // 3. FASE 2
        if (allPrepared) {
            for (int i = 0; i < 3; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro COMMIT: " + e.getMessage());
                }
            }
            return novoId;
        } else {
            for (int i = 0; i < 3; i++) {
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
            return -1;
        }
    }

    /**
     * Remove um produto das 3 bases de dados via 2PC.
     */
    public static boolean apagarProduto(int produtoId) {
        String txId = "TX_" + System.currentTimeMillis();
        Connection[] conns = new Connection[3];
        boolean allPrepared = true;

        System.out.println("\n=== 2PC DELETE produto (ID=" + produtoId + ") ===");

        for (int i = 0; i < 3; i++) {
            try {
                conns[i] = DriverManager.getConnection(DbConfig.NODE_URLS[i], DbConfig.USER, DbConfig.PASS);
                conns[i].setAutoCommit(false);

                String sql = "DELETE FROM stock WHERE produto_id = ?";
                PreparedStatement pstmt = conns[i].prepareStatement(sql);
                pstmt.setInt(1, produtoId);
                int rows = pstmt.executeUpdate();
                pstmt.close();

                if (rows == 0) {
                    throw new SQLException("produto não existe no nó " + i);
                }

                Statement stmt = conns[i].createStatement();
                stmt.execute("PREPARE TRANSACTION '" + txId + "_" + i + "'");
                stmt.close();
                System.out.println("[NÓ " + i + "] PREPARE OK");
            } catch (SQLException e) {
                System.out.println("[NÓ " + i + "] FALHOU PREPARE: " + e.getMessage());
                allPrepared = false;
                break;
            }
        }

        if (allPrepared) {
            for (int i = 0; i < 3; i++) {
                try {
                    if (!conns[i].getAutoCommit()) conns[i].setAutoCommit(true);
                    try (Statement stmt = conns[i].createStatement()) {
                        stmt.execute("COMMIT PREPARED '" + txId + "_" + i + "'");
                    }
                    conns[i].close();
                } catch (SQLException e) {
                    System.out.println("[NÓ " + i + "] Erro COMMIT: " + e.getMessage());
                }
            }
            return true;
        } else {
            for (int i = 0; i < 3; i++) {
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
            return false;
        }
    }

    // ==========================================
    // MAIN PARA TESTAR
    // ==========================================
    public static void main(String[] args) {
        // Vamos tentar comprar 5 unidades do produto ID 1 (Parafusos)
        executarCompra(1, 5);
    }
}