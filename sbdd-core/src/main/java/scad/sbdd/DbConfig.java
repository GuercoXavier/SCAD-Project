package scad.sbdd;

public class DbConfig {
    // Muda para o teu utilizador e password do PostgreSQL local!
    public static final String USER = "postgres";
    public static final String PASS = "GRo10";

    // O IP e a porta são os mesmos, só muda o nome da base de dados
    public static final String[] NODE_URLS = {
            "jdbc:postgresql://localhost:5432/scad_norte",
            "jdbc:postgresql://localhost:5432/scad_sul",
            "jdbc:postgresql://localhost:5432/scad_centro"
    };
}