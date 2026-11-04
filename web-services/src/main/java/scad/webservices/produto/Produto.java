package scad.webservices.produto;

public class Produto {
    private int id;
    private String nome;
    private int quantidade;
    private String regiao; // "Norte", "Sul", "Centro"

    public Produto() {}

    public Produto(int id, String nome, int quantidade, String regiao) {
        this.id = id;
        this.nome = nome;
        this.quantidade = quantidade;
        this.regiao = regiao;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public String getRegiao() { return regiao; }
    public void setRegiao(String regiao) { this.regiao = regiao; }
}