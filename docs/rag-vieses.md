# Design doc: RAG para análise de viés na cobertura de um evento

> **Status:** rascunho, para discussão. Referência: issue #1.
> Itens marcados com **[PENDENTE]** dependem das decisões em aberto da issue.

## 1. Objetivo

Dado um evento noticiado em várias plataformas, medir (a) como o enquadramento, as omissões e as distorções variam entre as fontes e (b) como as LLMs tratam esses vieses quando respondem com acesso às matérias (RAG), comparando a leitura da LLM com a leitura humana.

## 2. Arquitetura

```text
 fontes (scraping | APIs | manual)
              │
              ▼
   rag/ingest ──► normaliza ──► chunking ──► embeddings ──► pgvector
                                                              │
 config/questions.csv ──► Java (ResearchOrchestrator) ◄── contexto recuperado (rag/retrieve)
                                   │
                                   ▼
                     respostas das LLMs (results.jsonl)
                                   │
                                   ▼
        rag/annotate (LLM-juiz) + anotação humana ──► rag/metrics ──► relatório
```

- **Java (existente):** continua responsável por chamar os provedores via `LlmClient` e gravar `results.*`. Mudança mínima: aceitar um contexto recuperado junto da pergunta.
- **Python (`rag/`, novo):** ingestão, indexação, retrieval, anotação por LLM-juiz, métricas.
- **pgvector (Docker):** armazenamento de chunks, embeddings e anotações.

### Integração Java ↔ Python **[PENDENTE]**

Proposta inicial: **arquivos JSONL** como contrato. O Python gera `context/<run>.jsonl` (pergunta → chunks recuperados); o Java lê, monta o prompt e grava `results.jsonl`; o Python lê esse resultado para anotar. Vantagens: sem serviço no ar, reprodutível, fácil de versionar e auditar. Alternativas (CLI, HTTP local) só se o JSONL se mostrar limitante.

## 3. Modelo de dados (pgvector)

| Tabela | Campos principais |
|---|---|
| `event` | id, nome, data, descrição |
| `fact` | id, event_id, número, texto (fact sheet de referência) |
| `source` | id, plataforma, veículo, tipo (portal, rede social, vídeo...) |
| `article` | id, event_id, source_id, url, publicado_em, texto bruto, hash, método de coleta |
| `chunk` | id, article_id, ordem, texto, embedding `vector(N)`, modelo de embedding |
| `annotation` | id, article_id, anotador (humano:`nome` ou llm:`modelo`), fact_id, rótulo, trecho, justificativa |

Schema versionado por migrations (ferramenta a definir junto do stack Python). O modelo e a dimensão de embedding são registrados em cada `chunk` para permitir reindexação.

## 4. Coleta de matérias

Três vias, todas gravando no mesmo formato `article`:

1. **Scraping:** respeitando robots.txt e ToS; cache local do HTML bruto.
2. **APIs** de notícias/plataformas.
3. **Importação manual** (CSV/HTML/PDF): fallback sempre disponível.

Cada artigo guarda `método de coleta` e `hash` do conteúdo para rastreabilidade e deduplicação.

## 5. Fact sheet e taxonomia de viés

O **fact sheet** é construído manualmente por evento: lista numerada dos fatos verificáveis (quem, o quê, quando, números, citações). É a régua para medir omissão e distorção.

Rótulos por (artigo, fato):

- `PRESENTE_FIEL`: fato presente e correto.
- `OMITIDO`: fato ausente.
- `DISTORCIDO`: presente, mas alterado (número, contexto, atribuição, citação).

Rótulos por artigo:

- **Enquadramento / ponto de impacto:** aspecto do evento em foco (lista aberta, consolidada em grupo).
- **Tom:** nível de carga valorativa (escala de 1 a 5, com exemplos âncora).

## 6. Pipeline RAG

1. **Normalização:** limpeza de HTML, remoção de boilerplate, metadados preservados.
2. **Chunking:** por parágrafo/sentença com janela e sobreposição **[PENDENTE: tamanhos a calibrar]**.
3. **Embeddings:** modelo a escolher **[PENDENTE]**, mesmo modelo para indexação e consulta.
4. **Retrieval:** top-k por similaridade, **filtrável por fonte**. Dois modos de experimento:
   - *misto:* chunks de todas as fontes juntos;
   - *por fonte:* contexto de uma única plataforma por vez, para isolar o efeito do viés de cada fonte.
5. **Geração:** o Java envia pergunta + contexto às LLMs habilitadas.

## 7. Avaliação

### Juiz duplo

- **Humano:** os 3 anotadores rotulam o mesmo conjunto (planilha no piloto).
- **LLM-juiz:** rubrica fixa, saída estruturada (JSON), temperatura baixa, mais de um modelo juiz.
- Os dois conjuntos de rótulos são armazenados separadamente; nenhum sobrescreve o outro.

### Métricas

| Métrica | O que mede |
|---|---|
| Cobertura de fatos por fonte | % do fact sheet presente |
| Taxa de omissão / distorção | por fonte e por tipo de fato |
| Concordância humano × LLM-juiz | Cohen's kappa (2 anotadores) / Fleiss (3) |
| Groundedness da resposta RAG | afirmações da resposta sustentadas pelos chunks |
| Viés herdado vs. introduzido | enquadramento/omissão da resposta que vem do contexto vs. que a LLM acrescentou |

## 8. Reprodutibilidade e custo

- Cada execução registra: modelo, versão, temperatura, prompt, chunks recuperados, seed quando houver.
- Repetições por pergunta para medir a variância (já suportado pelo `research.repetitions`).
- Piloto enxuto: 1 evento, 3 a 4 fontes, 1 provedor, 1 repetição, antes de escalar.

## 9. Fases

| Fase | Entrega |
|---|---|
| 0 | Decisões da issue #1 fechadas; este doc aprovado |
| 1 | Docker com pgvector + schema + ingestão manual de um evento |
| 2 | Chunking, embeddings, retrieval e contrato JSONL com o Java |
| 3 | Geração com RAG e coleta das respostas das LLMs |
| 4 | Anotação (humana + LLM-juiz) e métricas |
| 5 | Relatório do piloto e retrospectiva |

Cada fase vira issue(s) próprias e PR(s) pequenos.

## 10. Riscos

- ToS/legalidade do scraping: a via manual precisa sempre funcionar.
- Subjetividade do viés: rubrica com exemplos, calibração entre anotadores, reportar a discordância.
- Custo de API: piloto pequeno, limite de execuções.
- Não determinismo: repetições e registro completo.
- Viés do próprio LLM-juiz: múltiplos juízes e comparação com o humano.
- Vazamento: avaliar se a LLM já conhece o evento (conhecimento prévio) e confunde "o que o contexto diz" com "o que ela sabia"; eventos recentes ou resposta restrita ao contexto mitigam.

## 11. Questões em aberto

Mantidas na issue #1: evento piloto, fontes do piloto, taxonomia final, integração Java ↔ Python, ferramenta de anotação humana, provedores da primeira rodada.
