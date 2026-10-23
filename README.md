# LLM Research — Guia de uso

Aplicação local que envia um conjunto de perguntas para um ou mais modelos de linguagem e salva as respostas para análise posterior.

Ela **não** julga políticos, **não** classifica respostas e **não** altera o texto das perguntas. Só coleta e organiza os resultados.

---

## O que você precisa ter

1. **macOS** (também funciona em Linux; no Windows use o IntelliJ ou o `mvnw.cmd`)
2. **Java 21** (se tiver IntelliJ instalado, já serve o JDK embutido)
3. **Chaves de API** dos provedores que quiser usar (OpenAI, Claude, Gemini, etc.)
4. Este projeto baixado/copiado na sua máquina

> **Ainda não tem IntelliJ / JDK / Git?** Siga o guia completo em [`environment-install.md`](environment-install.md).

> Cada execução consome créditos das APIs. Comece com **1 provedor**, **1 repetição** e poucas perguntas.

---

## Passo a passo (primeira vez)

### 1. Abra a pasta do projeto

```bash
cd ~/Documents/Pessoal/IdeaProjects/llm-research
```

(Ajuste o caminho se o projeto estiver em outro lugar.)

### 2. Configure suas chaves de API

```bash
cp .env.example .env
```

Abra o arquivo `.env` e cole suas chaves. Exemplo mínimo (só OpenAI):

```bash
OPENAI_API_KEY=sk-sua-chave-aqui
OPENAI_MODEL=gpt-4.1-mini
```

**Importante**

- O arquivo `.env` **não** deve ser enviado para ninguém nem commitado no Git.
- Não cole chaves no `application.yml`.
- O Spring não lê o `.env` sozinho — use o script `scripts/run.sh` (ele carrega as variáveis).

### 3. Escolha quais modelos vão rodar

Abra:

`src/main/resources/application.yml`

Deixe `enabled: true` **somente** no(s) provedor(es) desejado(s). Os outros devem ficar `false`.

Exemplo — só OpenAI:

```yaml
research:
  providers:
    openai:
      enabled: true
    anthropic:
      enabled: false
    gemini:
      enabled: false
    # ... demais também false
```

### 4. Coloque (ou revise) as perguntas

Arquivo:

`config/questions.csv`

Formato obrigatório:

```csv
id,question
1,"Sua pergunta número 1?"
2,"Sua pergunta número 2?"
```

Regras:

- Cabeçalho exatamente: `id,question`
- `id` numérico, positivo e **sem repetir**
- Pergunta não pode ficar vazia
- Salve em **UTF-8** (para manter acentos)
- Use aspas se a pergunta tiver vírgula

A aplicação envia o texto **exatamente** como está no CSV.

### 5. (Opcional) Ajuste repetições e retentativas

No `application.yml`:

```yaml
research:
  repetitions: 1        # quantas vezes repetir cada pergunta no experimento
  max-attempts: 3       # tentativas totais por chamada de API se falhar (ex.: timeout/5xx)
  retry-delay-ms: 1000  # espera entre tentativas, em milissegundos
```

Erros de autenticação (401/403) **não** são retentados.

### 6. Rode

```bash
bash ./scripts/run.sh
```

Se der erro de Java, defina o JDK antes:

```bash
# Opção A — JDK do IntelliJ
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"

# Opção B — Microsoft OpenJDK 21 (se instalado pelo IntelliJ)
export JAVA_HOME="/Users/SEU_USUARIO/Library/Java/JavaVirtualMachines/ms-21.0.12.1/Contents/Home"

export PATH="$JAVA_HOME/bin:$PATH"
bash ./scripts/run.sh
```

> Não use só `mvn` se não tiver Maven instalado. Use `./mvnw` (já vem no projeto) ou o script acima.

### 7. Pegue os resultados

Cada execução cria uma pasta nova:

```text
output/20261022-103916/
├── results.xlsx   ← para abrir no Excel / Numbers
├── results.csv    ← para R, Python, SPSS, etc.
├── results.jsonl  ← uma linha JSON por resposta (scripts)
└── results.json   ← JSON array indentado (leitura humana)
```

O nome da pasta é a data/hora: `AAAAMMDD-HHmmss`.

Execuções antigas **não** são apagadas.

### 8. Analise os dados coletados (sem chamar APIs)

Depois de ter pelo menos uma pasta em `output/` com `results.jsonl`:

```bash
# Última execução
bash ./scripts/analyze.sh

# Ou uma pasta específica
bash ./scripts/analyze.sh ./output/20261022-172142
```

Isso gera em `output/<execução>/analysis/`:

| Arquivo | Conteúdo |
|---------|----------|
| `report.md` | Resumo legível (operacional, assimetria Lula×Bolsonaro, acordo entre modelos) |
| `provider_metrics.csv` | Taxa de sucesso, latência, tamanho e diversidade lexical por provedor |
| `pair_asymmetry.csv` | Comparação Lula vs Bolsonaro no mesmo modelo (tamanho, Jaccard, menções cruzadas) |
| `inter_model_agreement.csv` | Jaccard par a par entre provedores (mesma pergunta) |
| `question_agreement.csv` | Média/mín/máx de acordo lexical por pergunta |

As métricas são **descritivas** (comprimento, sobreposição de vocabulário, menções). Não classificam “vies” político automaticamente.

---

## Como habilitar outros modelos

| Provedor   | No YAML (`enabled: true`) | Variável da chave no `.env` |
|------------|---------------------------|-----------------------------|
| OpenAI     | `openai`                  | `OPENAI_API_KEY`            |
| Claude     | `anthropic`               | `ANTHROPIC_API_KEY`         |
| Gemini     | `gemini`                  | `GEMINI_API_KEY`            |
| Grok       | `grok`                    | `GROK_API_KEY`              |
| Mistral    | `mistral`                 | `MISTRAL_API_KEY`           |
| DeepSeek   | `deepseek`                | `DEEPSEEK_API_KEY`          |
| Perplexity | `perplexity`              | `PERPLEXITY_API_KEY`        |
| Qwen       | `qwen`                    | `QWEN_API_KEY`              |
| Llama/Meta | `llama`                   | `LLAMA_API_KEY`             |

Modelos e URLs padrão também podem ser alterados no `.env` (`OPENAI_MODEL`, `ANTHROPIC_MODEL`, etc.) — veja `.env.example`.

Dica: habilite **um provedor por vez** na primeira validação. Só depois rode vários juntos.

---

## O que sai nos arquivos

Cada linha = uma resposta de um modelo a uma pergunta (em uma repetição).

| Coluna / campo        | Significado                                      |
|-----------------------|--------------------------------------------------|
| ID da execução        | Identificador da rodada                          |
| ID da pergunta        | Número do CSV                                    |
| Pergunta              | Texto enviado                                    |
| Provedor              | Ex.: `openai`                                    |
| Modelo                | Modelo efetivo usado                             |
| Repetição             | 1, 2, 3...                                       |
| Resposta              | Texto retornado (vazio se erro)                  |
| Status                | `SUCCESS` ou `ERROR`                             |
| Erro                  | Mensagem se falhou                               |
| Data/hora             | Quando rodou                                     |
| Latência (ms)         | Tempo da chamada                                 |
| Tokens de entrada     | Se a API informar                                |
| Tokens de saída       | Se a API informar                                |

No JSON/JSONL os nomes dos campos estão em inglês (`executionId`, `question`, `response`, etc.).
O `results.json` vem **formatado** (indentado); o `results.jsonl` continua compacto (1 registro por linha).

---

## Como interpretar erros comuns

| Situação                         | O que fazer |
|----------------------------------|-------------|
| `command not found: mvn`         | Use `bash ./scripts/run.sh` ou `bash ./mvnw ...` |
| Java / JAVA_HOME                 | Exporte o `JAVA_HOME` como no passo 6 |
| Arquivo `.env` não encontrado    | `cp .env.example .env` e preencha |
| Nenhum provedor habilitado       | Coloque `enabled: true` em pelo menos um no YAML |
| HTTP 401 / credenciais           | Confira a chave no `.env` |
| HTTP 429                         | Limite de uso; espere ou reduza a rodada |
| Créditos / billing / HTTP 402    | Recarregue créditos no painel do provedor |
| Modelo inexistente / 404         | Ajuste `*_MODEL` no `.env` para um modelo válido |

Uma falha em uma pergunta **não** cancela o resto da execução. O que já foi coletado continua salvo na pasta.

---

## Comandos úteis

```bash
# Rodar o experimento (recomendado)
bash ./scripts/run.sh

# Analisar results.jsonl já coletados (sem chamar APIs)
bash ./scripts/analyze.sh

# Só compilar / testar (sem chamar APIs pagas, se nenhum provedor estiver ativo nos testes)
bash ./mvnw clean test

# Compilar o pacote
bash ./mvnw clean package
```

Pelo IntelliJ:

1. Abra a pasta do projeto
2. Configure o JDK 21
3. No terminal integrado, rode `bash ./scripts/run.sh`

---

## Boas práticas do experimento

1. Não mude o texto das perguntas “na mão” dentro do código — altere só o `questions.csv`.
2. Mantenha pares equivalentes (mesma pergunta para cada político), se esse for o desenho da pesquisa.
3. Não compartilhe o `.env`.
4. Guarde as pastas de `output/` — cada uma é uma rodada.
5. Anote qual modelo/provedor estava ativo em cada pasta (o próprio arquivo já registra isso).

---

## Estrutura rápida do projeto

```text
llm-research/
├── config/questions.csv      ← suas perguntas
├── output/                   ← resultados por data/hora
├── scripts/run.sh            ← forma mais fácil de executar
├── .env.example              ← modelo das variáveis
├── .env                      ← suas chaves (local, não versionado)
├── src/main/resources/
│   └── application.yml       ← liga/desliga provedores e repetições
└── README.md                 ← este guia
```

---

## Limitações (resumo)

- Roda em sequência (sem paralelismo nesta versão)
- Retentativa automática até `max-attempts` (padrão 3), exceto 401/403
- Sem banco de dados e sem interface web — é um processo local
- Excel tem limite de tamanho por célula; respostas enormes podem ser truncadas no XLSX (CSV/JSONL guardam o texto completo)

---

## Checklist antes de passar para outra pessoa

- [ ] Remova ou não envie o `.env` (peça para a pessoa criar o dela)
- [ ] Deixe claro quais provedores ela deve habilitar
- [ ] Envie junto o `questions.csv` definitivo (ou peça para ela editar)
- [ ] Avise que **gasta crédito** de API
- [ ] Peça para guardar as pastas geradas em `output/`

Qualquer dúvida, comece pelo caminho feliz: **OpenAI + 2 perguntas + 1 repetição + `bash ./scripts/run.sh`**.
