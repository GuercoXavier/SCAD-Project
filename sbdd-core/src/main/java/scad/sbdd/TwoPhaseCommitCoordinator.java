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
    // ==========================================
    // MAIN PARA TESTAR
    // ==========================================
    public static void main(String[] args) {
        // Vamos tentar comprar 5 unidades do produto ID 1 (Parafusos)
        executarCompra(1, 5);
    }
}