# Instalação do ambiente — LLM Research

Guia para preparar o computador (macOS) e abrir o projeto no IntelliJ IDEA Community.

Se algo falhar, volte a este arquivo e confira o passo com calma. Depois de concluir, use o `README.md` para rodar o experimento.

---

## 1. O que será instalado

| Item | Para quê |
|------|----------|
| Git | Baixar o projeto do repositório |
| IntelliJ IDEA Community | Editar código e abrir o terminal do projeto |
| JDK 21 | Compilar e executar a aplicação Java |
| (opcional) Conta GitHub | Se o repositório for privado |

Não é necessário instalar Maven separadamente: o projeto já traz o `mvnw` (Maven Wrapper).

---

## 2. Instalar o Git

Abra o **Terminal** (Spotlight → `Terminal`) e rode:

```bash
git --version
```

- Se aparecer algo como `git version 2.x`, o Git já está instalado.
- Se pedir para instalar as *Command Line Tools*, aceite e espere terminar.
- Alternativa: baixe em [https://git-scm.com/download/mac](https://git-scm.com/download/mac)

Confirme de novo:

```bash
git --version
```

---

## 3. Baixar o IntelliJ IDEA Community

1. Acesse: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)
2. Escolha a aba **macOS**
3. Baixe a versão **Community** (gratuita) — **não** a Ultimate, a menos que você já tenha licença
4. Abra o `.dmg` baixado
5. Arraste **IntelliJ IDEA CE** para a pasta **Applications**
6. Na primeira abertura, se o macOS pedir confirmação: **Abrir**

Na primeira execução o IntelliJ pode pedir:

- tema (claro/escuro) — escolha o que preferir
- plugins — pode pular (*Skip*)
- criar launcher — opcional

---

## 4. Configurar o JDK 21 no IntelliJ

O projeto usa **Java 21**.

### Opção recomendada (pelo próprio IntelliJ)

1. Abra o IntelliJ IDEA Community
2. Menu **IntelliJ IDEA → Settings…** (ou `⌘ ,`)
3. Vá em **Build, Execution, Deployment → Build Tools → Maven** (opcional, só para conferir)
4. Vá em **Build, Execution, Deployment → Compiler → Java Compiler**
5. Em **Project bytecode version**, selecione **21** (pode ajustar depois de abrir o projeto)
6. Vá em **File → Project Structure…** (`⌘ ;`) — se o menu File não aparecer ainda, abra qualquer projeto/pasta e tente de novo
7. Em **Project Settings → Project**:
   - **SDK**: clique em **Add SDK → Download JDK…**
   - **Version**: `21`
   - **Vendor**: por exemplo **Microsoft**, **Eclipse Temurin** ou **Amazon Corretto**
   - Clique em **Download**
8. Em **Language level**, selecione **21**
9. **Apply** → **OK**

### Conferir no Terminal do IntelliJ

Depois de abrir o projeto (próximo passo), no terminal integrado:

```bash
java -version
```

Deve aparecer algo com `21` (ou o JBR do IntelliJ). Se aparecer erro de Java, defina:

```bash
# Se baixou JDK pelo IntelliJ (ajuste o nome da pasta se for diferente)
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/ms-21.0.12.1/Contents/Home"

# Ou use o Java embutido do IntelliJ
export JAVA_HOME="/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home"

export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

> O nome do app pode ser `IntelliJ IDEA CE.app` (Community) ou `IntelliJ IDEA.app`. Confira em `/Applications`.

---

## 5. Baixar o projeto do Git

Substitua a URL pelo repositório real que você recebeu.

### No Terminal

```bash
# Pasta sugerida (pode mudar)
mkdir -p ~/Documents/Pessoal/IdeaProjects
cd ~/Documents/Pessoal/IdeaProjects

# Clone (HTTPS)
git clone https://github.com/USUARIO/llm-research.git

# Ou SSH (se você configurou chave SSH no GitHub)
# git clone git@github.com:USUARIO/llm-research.git

cd llm-research
```

Se o repositório for **privado**, o GitHub vai pedir login. Prefira:

- HTTPS + *Personal Access Token*, ou
- SSH com chave cadastrada na conta

### Pelo IntelliJ (alternativa)

1. Na tela inicial: **Get from VCS** (ou **File → New → Project from Version Control…**)
2. Cole a URL do repositório
3. Escolha a pasta de destino
4. **Clone**

---

## 6. Abrir o projeto no IntelliJ

1. **File → Open…**
2. Selecione a pasta `llm-research` (a que contém o `pom.xml`)
3. **Open**
4. Quando perguntar se confia no projeto: **Trust Project**
5. Aguarde o IntelliJ indexar e o Maven baixar dependências (barra inferior)

Sinais de que está ok:

- No painel esquerdo aparece `pom.xml`, `src`, `config`, `scripts`
- Aba **Maven** (lado direito) carrega o módulo `llm-research`
- Não há erro vermelho permanente em `pom.xml` (avisos amarelos leves são normais no início)

Se o Maven não sincronizar:

1. Clique com o botão direito em `pom.xml`
2. **Maven → Reload project**

---

## 7. Configurar as chaves de API (local)

**Não compartilhe suas chaves.** Cada pessoa cria o próprio `.env`.

No terminal do projeto:

```bash
cd ~/Documents/Pessoal/IdeaProjects/llm-research
cp .env.example .env
```

Abra `.env` no IntelliJ e preencha pelo menos a chave do provedor que for usar, por exemplo:

```bash
OPENAI_API_KEY=cole_sua_chave_aqui
```

O arquivo `.env` já está no `.gitignore` e **não** deve ir para o Git.

---

## 8. Habilitar um provedor e rodar

1. Abra `src/main/resources/application.yml`
2. Deixe só o provedor desejado com `enabled: true` (ex.: `openai`)
3. No terminal do IntelliJ:

```bash
bash ./scripts/run.sh
```

Os resultados saem em:

```text
output/AAAAMMDD-HHmmss/
├── results.xlsx
├── results.csv
├── results.jsonl
└── results.json
```

Detalhes de uso, perguntas CSV e erros comuns: veja o **`README.md`**.

---

## 9. Checklist final

- [ ] Git instalado (`git --version`)
- [ ] IntelliJ IDEA **Community** instalado em Applications
- [ ] JDK **21** configurado no Project Structure
- [ ] Projeto clonado do Git
- [ ] Pasta aberta no IntelliJ (com `pom.xml` na raiz)
- [ ] Maven sincronizado (dependências baixadas)
- [ ] Arquivo `.env` criado a partir do `.env.example`
- [ ] Pelo menos um provedor com `enabled: true`
- [ ] `bash ./scripts/run.sh` executou sem erro de Java

---

## Problemas frequentes

### `zsh: command not found: mvn`

Normal. Use o wrapper do projeto:

```bash
bash ./mvnw clean test
# ou
bash ./scripts/run.sh
```

### `Unable to locate a Java Runtime`

Defina `JAVA_HOME` (passo 4) e rode de novo no **mesmo** terminal.

### Clone pede senha e falha

Repositório privado: configure token GitHub ou SSH. Não use a senha da conta GitHub em HTTPS (a GitHub não aceita mais senha pura para Git).

### IntelliJ não acha o JDK

**File → Project Structure → Project → SDK → Add SDK → Download JDK… → 21**

### Dependências Maven não baixam

Verifique internet. Depois: botão direito em `pom.xml` → **Maven → Reload project**.

---

## Links úteis

- IntelliJ Community: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)
- Git para macOS: [https://git-scm.com/download/mac](https://git-scm.com/download/mac)
- OpenJDK (se preferir instalar fora do IntelliJ): vendors como Temurin / Microsoft / Corretto, versão **21**

Depois que o ambiente estiver ok, siga o **`README.md`** para configurar perguntas, provedores e interpretar os resultados.
