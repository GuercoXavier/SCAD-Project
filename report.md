# Sistema de Compras Automáticas Distribuídas (SCAD)

## Relatório Técnico

**Disciplina:** Sistemas Distribuídos e Paralelos  
**Curso:** LCC 31  
**Ano Letivo:** 2026  
**Docente:** Msc. Sérgio J. Sambo  
**Instituição:** Instituto Superior de Transportes e Comunicações (ISUTC)

---

## Resumo

O presente relatório descreve o desenho, implementação e validação do **Sistema de Compras Automáticas Distribuídas (SCAD)**, uma plataforma que monitoriza os níveis de stock de múltiplos armazéns regionais e efetua, de forma automática e descentralizada, ordens de compra a fornecedores externos quando um produto atinge o limite mínimo. O sistema foi construído sobre cinco componentes tecnológicas complementares — Sistemas Operativos Distribuídos (SOD), Sistemas de Base de Dados Distribuídos (SBDD), Sistemas de Ficheiros Distribuídos (DFS), Java/CORBA e Serviços Web Distribuídos (SWD) — totalizando 5 módulos Maven independentes. A implementação privilegiou a **transparência de localização**, **tolerância a falhas** e **escalabilidade horizontal**, com validação através de testes de resiliência que simulam falhas de nós individuais.

**Palavras-chave:** Sistemas Distribuídos, Exclusão Mútua, Two-Phase Commit, CORBA, SeaweedFS, REST, SOAP.

---

## Índice

1. Introdução
2. Arquitetura do Sistema
3. Componente 1 — Sistemas Operativos Distribuídos
4. Componente 2 — Sistemas de Base de Dados Distribuídos
5. Componente 3 — Sistemas de Ficheiros Distribuídos
6. Componente 4 — Java/CORBA
7. Componente 5 — Serviços Web Distribuídos
8. Integração e Interface de Utilizador
9. Testes de Resiliência
10. Limitações
11. Trabalho Futuro
12. Conclusão
13. Referências

---

## 1. Introdução

A gestão de stock em ambientes multi-armazém apresenta desafios clássicos de sistemas distribuídos: a necessidade de **coordenação entre nós geograficamente dispersos**, **consistência transacional** e **tolerância a falhas parciais** sem comprometer a disponibilidade global. O SCAD endereça estes desafios através de uma arquitetura modular que isola as preocupações de coordenação (SOD), persistência (SBDD), armazenamento documental (DFS), comunicação intra-sistema (CORBA) e integração externa (SWD).

O objetivo central é garantir que, quando dois armazéns detetam stock baixo do mesmo produto em simultâneo, **apenas um efetua a compra ao fornecedor**, evitando duplicações, e que a atualização de stock é atómica em todas as réplicas, independentemente de falhas de rede ou de nós.

---

## 2. Arquitetura do Sistema

O SCAD adota uma arquitetura **cliente-servidor multi-camada**, com separação clara entre coordenação interna e integração externa. A figura abaixo representa a topologia lógica.


### 2.1 Princípios de Design

| Princípio | Aplicação no SCAD |
|---|---|
| **Transparência de localização** | Configuração centralizada (IPs, portas) permite migrar de `localhost` para 3 PCs sem alterar código |
| **Separação de responsabilidades** | 5 módulos Maven independentes, cada um com um domínio técnico distinto |
| **Tolerância a falhas** | 2PC garante atomicidade; Ricart-Agrawala tem timeout; SeaweedFS replica volumes |
| **Interoperabilidade** | REST/JSON (moderno) + SOAP/XML (legado) expostos em simultâneo |

---

## 3. Componente 1 — Sistemas Operativos Distribuídos

### 3.1 Gestão de Concorrência: Ricart-Agrawala

**Problema:** dois armazéns podem detetar stock baixo ao mesmo tempo e tentar comprar o mesmo lote ao mesmo fornecedor, causando compras duplicadas.

**Solução:** implementação do algoritmo distribuído de **Ricart-Agrawala**, que garante exclusão mútua sem coordenador central, evitando o *single point of failure* inerente a soluções centralizadas.

**Funcionamento:**
1. Cada nó mantém um **relógio lógico de Lamport** e um estado (`RELEASED`, `WANTED`, `HELD`).
2. Para entrar na secção crítica, o nó incrementa o relógio e envia `REQUEST(timestamp)` a todos os outros.
3. Ao receber um `REQUEST`, um nó responde `REPLY` imediatamente **exceto** se já estiver em `WANTED`/`HELD` com timestamp menor (ou empate resolvido por ID de nó).
4. O requerente entra na secção crítica apenas quando recebeu `REPLY` de todos os pares.
5. Ao sair, responde aos pedidos adiados.

**Justificação da escolha:**
- **vs. Algoritmo do Anel:** Ricart-Agrawala tolera falhas de nós (timeout de 10s); o Anel colapsa se um nó falhar.
- **vs. Centralizado:** não introduz um *single point of failure*.
- **Custo:** 2(N−1) mensagens por entrada, aceitável para N=3.

### 3.2 Sincronização de Relógios: Berkeley

**Problema:** os logs de auditoria produzidos por nós distintos têm timestamps inconsistentes, dificultando a correlação temporal.

**Solução:** Algoritmo de **Berkeley**, com o nó 0 como *master*. A cada 30 segundos:
1. O *master* solicita a hora a todos os *slaves* (`BERKELEY_TIME_REQ`).
2. Os *slaves* respondem com o seu relógio local (`BERKELEY_TIME_RES`).
3. O *master* calcula a **média** (incluindo a sua hora) e envia o delta de ajuste (`BERKELEY_ADJUST`).

**Justificação:** Berkeley é mais preciso que Cristian (não depende de um único servidor de tempo) e não requer infraestrutura NTP dedicada, sendo adequado ao contexto académico e à rede local do projeto.

---

## 4. Componente 2 — Sistemas de Base de Dados Distribuídos

### 4.1 Modelo de Dados e Fragmentação

**Fragmentação horizontal** por região geográfica:

| Fragmento | Nó | Domínio |
|---|---|---|
| `scad_norte` | Nó 0 | Armazém Norte |
| `scad_sul` | Nó 1 | Armazém Sul |
| `scad_centro` | Nó 2 | Armazém Centro |

```sql
CREATE TABLE stock (
    produto_id SERIAL PRIMARY KEY,
    nome       VARCHAR(100) NOT NULL,
    quantidade INT NOT NULL
);

CREATE TABLE ordens_compra (
    ordem_id        SERIAL PRIMARY KEY,
    produto_id      INT,
    quantidade      INT,
    fornecedor      VARCHAR(100),
    data_transacao  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20)
);