package scad.dfs;

public class DfsConfig {
    public static final String ENDPOINT = "http://localhost:8333";
    public static final String ACCESS_KEY = "admin";
    public static final String SECRET_KEY = "secret";

    // Usamos apenas 1 bucket e separamos por "pastas" (prefixos)
    public static final String BUCKET = "scad-fs";
    public static final String PASTA_FATURAS = "faturas/";
    public static final String PASTA_LOGS = "logs/";
}