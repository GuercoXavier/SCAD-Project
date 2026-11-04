# SCAD — Sistema de Compras Automáticas Distribuídas
## Guia de Execução Completo

**Disciplina:** Sistemas Distribuídos e Paralelos  
**Curso:** LCC 31  
**Docente:** Msc. Sérgio J. Sambo  
**Ano Letivo:** 2026

---

## 📋 Índice

1. [Visão Geral](#1-visão-geral)
2. [Pré-Requisitos](#2-pré-requisitos)
3. [Arquitetura do Sistema](#3-arquitetura-do-sistema)
4. [Configuração Base de Dados (SBDD)](#4-configuração-base-de-dados-sbdd)
5. [Componente 1 — SOD](#5-componente-1--sod)
6. [Componente 2 — SBDD (2PC)](#6-componente-2--sbdd-2pc)
7. [Componente 3 — DFS (SeaweedFS)](#7-componente-3--dfs-seaweedfs)
8. [Componente 4 — CORBA](#8-componente-4--corba)
9. [Componente 5 — Web Services (REST + SOAP)](#9-componente-5--web-services-rest--soap)
10. [Testes de Resiliência](#10-testes-de-resiliência)
11. [Execução nos 3 PCs](#11-execução-nos-3-pcs)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Visão Geral

O **SCAD** é um sistema distribuído que monitoriza o stock de múltiplos armazéns regionais e efetua ordens de compra automáticas a fornecedores quando o stock atinge o limite mínimo.

Cobre 5 componentes tecnológicas:

| # | Componente | Tecnologia |
|---|---|---|
| 1 | Sistemas Operativos Distribuídos | Ricart-Agrawala + Berkeley |
| 2 | Sistemas de Base de Dados Distribuídos | PostgreSQL + 2PC |
| 3 | Sistemas de Ficheiros Distribuídos | SeaweedFS (S3) |
| 4 | Java / CORBA | CORBA nativo do JDK 8 |
| 5 | Serviços Web | Spring Boot (REST + SOAP) |

---

## 2. Pré-Requisitos

### Software necessário

| Software | Versão | Uso |
|---|---|---|
| JDK 25 (ou 17+) | 17+ | Módulos `sod-core`, `sbdd-core`, `dfs-core`, `web-services` |
| JDK 8 | 1.8 | **Apenas** o módulo `corba-module` (obrigatório) |
| IntelliJ IDEA | 2024+ | IDE |
| PostgreSQL | 15+ | Base de dados distribuída |
| SeaweedFS | Última | Sistema de ficheiros distribuído |
| Postman / PowerShell | — | Testes HTTP |

### Instalação rápida

**JDK 8 (obrigatório para CORBA):**
1. Descarregar de [https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
2. Instalar (normalmente em `C:\Program Files\Java\jdk1.8.0_xxx`)

**PostgreSQL:**
1. Descarregar de [https://www.postgresql.org/download/windows/](https://www.postgresql.org/download/windows/)
2. Durante a instalação, definir password do user `postgres` (anotar!)
3. **Ativar o 2PC** (editar `postgresql.conf`):
   ```ini
   max_prepared_transactions = 100