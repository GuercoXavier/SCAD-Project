package scad.dfs;

import io.minio.*;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DfsManager {

    private final MinioClient minioClient;

    public DfsManager() {
        this.minioClient = MinioClient.builder()
                .endpoint(DfsConfig.ENDPOINT)
                .credentials(DfsConfig.ACCESS_KEY, DfsConfig.SECRET_KEY)
                .build();
        criarBucketSeNaoExistir(DfsConfig.BUCKET);
    }

    private void criarBucketSeNaoExistir(String nomeBucket) {
        try {
            boolean existe = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(nomeBucket).build()
            );
            if (!existe) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(nomeBucket).build()
                );
                System.out.println("[DFS] Bucket criado: " + nomeBucket);
            }
        } catch (Exception e) {
            System.err.println("[DFS] Erro ao criar bucket: " + e.getMessage());
        }
    }

    public void guardarFatura(String nomeFicheiro, byte[] conteudo) {
        try {
            String caminho = DfsConfig.PASTA_FATURAS + nomeFicheiro;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(DfsConfig.BUCKET)
                            .object(caminho)
                            .stream(new ByteArrayInputStream(conteudo), conteudo.length, -1)
                            .build()
            );
            System.out.println("[DFS] Fatura guardada: " + caminho);
        } catch (Exception e) {
            System.err.println("[DFS] Erro ao guardar fatura: " + e.getMessage());
        }
    }

    public void guardarLog(String nomeFicheiro, String conteudoLog) {
        try {
            byte[] bytes = conteudoLog.getBytes(StandardCharsets.UTF_8);
            String caminho = DfsConfig.PASTA_LOGS + nomeFicheiro;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(DfsConfig.BUCKET)
                            .object(caminho)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .build()
            );
            System.out.println("[DFS] Log guardado: " + caminho);
        } catch (Exception e) {
            System.err.println("[DFS] Erro ao guardar log: " + e.getMessage());
        }
    }

    public String lerFicheiro(String caminho) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(DfsConfig.BUCKET)
                        .object(caminho)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[DFS] Erro ao ler ficheiro: " + e.getMessage());
            return null;
        }
    }

    public void listarFicheiros(String prefixo) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(DfsConfig.BUCKET)
                            .prefix(prefixo)
                            .recursive(true)
                            .build()
            );
            System.out.println("[DFS] Ficheiros com prefixo '" + prefixo + "':");
            for (Result<Item> result : results) {
                Item item = result.get();
                System.out.println("  -> " + item.objectName() + " (" + item.size() + " bytes)");
            }
        } catch (Exception e) {
            System.err.println("[DFS] Erro ao listar ficheiros: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        DfsManager dfs = new DfsManager();

        String nomeFatura = "fatura_" + System.currentTimeMillis() + ".pdf";
        dfs.guardarFatura(nomeFatura, "Fatura teste - 5 Parafusos - 500 MZN".getBytes());

        String nomeLog = "log_" + System.currentTimeMillis() + ".txt";
        dfs.guardarLog(nomeLog, "[AUDITORIA] Compra efetuada às " + new java.util.Date());

        dfs.listarFicheiros(DfsConfig.PASTA_FATURAS);
        dfs.listarFicheiros(DfsConfig.PASTA_LOGS);
    }
}