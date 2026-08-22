# Math-assistant

## Quick Start

### Atualizar índices da collection de fórmulas

Depois de subir o MongoDB local ou depois de puxar mudanças da feature de Formula Builder, atualize os índices da collection `formulas`.

Opção recomendada usando MongoDB Compass:

1. Abra o MongoDB Compass.
2. Conecte no MongoDB local.
3. Abra um Playground.
4. Cole ou abra o arquivo `scripts/update-formula-indexes.compass.js`.
5. Execute o script contra o database `mathAssistant`.

O script remove o índice antigo `unique_name_group` quando ele existir e garante o novo índice único `unique_formula_scope`:

```javascript
{ name: 1, group: 1, status: 1, ownerUserId: 1 }
```

Opção alternativa via PowerShell/Docker:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-formula-indexes.ps1
```

Siga estes passos rápidos para compilar, executar e testar o backend localmente (Windows):

### Preparar banco de dados local (Docker)

```powershell
docker-compose up -d
```

### Build e executar a aplicação (Windows)

```powershell
.\gradlew.bat build
.\gradlew.bat bootRun
```

### Executar testes

```powershell
.\gradlew.bat test
```

### Configurações e variáveis

* As propriedades do Spring Boot ficam em `src/main/resources/application.properties`.
* Para configurar o MongoDB local, ajuste `spring.data.mongodb.*` ou use o `docker-compose.yml` já presente.

---

## Local dev & secrets

* Copie `.env.example` para `.env` e preencha os valores necessários (mantenha `.env` no `.gitignore`).
* Scripts auxiliares para rodar localmente com variáveis de ambiente:

  * Windows PowerShell: `scripts/run-local.ps1`
  * Unix/macOS: `scripts/run-local.sh` (torne executável)

### Usage examples

**Windows PowerShell (executar na raiz do repositório):**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

**Unix/macOS:**

```bash
chmod +x ./scripts/run-local.sh
./scripts/run-local.sh
```

* Alternativa: usar `dotenv-cli`:

```bash
dotenv -e .env -- gradlew bootRun
```

* Docker Compose suporta `env_file: .env` para que os containers leiam o mesmo arquivo.

---

## 🔄 Atualizações Recentes – Modelo de Fórmulas

O backend agora suporta **fórmulas fixas** e **fórmulas variádicas (número dinâmico de inputs)**, ambas executadas por **um único endpoint de cálculo**.

### Fórmulas Fixas

* Quantidade fixa de parâmetros
* Equação armazenada diretamente no banco
* Validação estrita de parâmetros

```json
{
  "name": "ADDITION_2",
  "group": "ARITHMETIC",
  "variable": false,
  "equation": "X + Y",
  "parameters": ["X", "Y"],
  "status": "PUBLIC"
}
```

**Regras:**

* `variable = false`
* `equation` obrigatória
* `parameters` obrigatório e não vazio

---

### Fórmulas Variádicas

* Aceitam quantidade variável de valores
* A equação é armazenada no banco e pode usar `{VALUES}` para receber todos os valores enviados
* Indicadas para operações como soma, média, mínimo e máximo

```json
{
  "name": "ADDITION",
  "group": "ARITHMETIC",
  "variable": true,
  "equation": "SUM({VALUES})",
  "minParams": 1,
  "status": "PUBLIC"
}
```

**Regras:**

* `variable = true`
* `equation` obrigatória
* Use `{VALUES}` quando a função precisar receber todos os itens de `values`
* `parameters` não deve ser utilizada
* `minParams` / `maxParams` são opcionais

---

## ▶️ Execução das Fórmulas (Endpoint Único)

Todas as fórmulas são executadas através de um único endpoint:

```
POST /api/formulas/execute
```

O backend identifica automaticamente se a fórmula é fixa ou variádica.

### Payload – Fórmula Fixa

```json
{
  "formulaName": "RECTANGLE_AREA",
  "variables": {
    "BASE": 10,
    "HEIGHT": 5
  }
}
```

### Payload – Fórmula Variádica

```json
{
  "formulaName": "ADDITION",
  "values": [1, 2, 3, 2, 3, 2]
}
```

> ⚠️ Para fórmulas variádicas, **não utilize Map de variáveis** (`X1`, `X2`, ...). Use sempre `values: []` para permitir valores repetidos e ordenados.

### Respostas

* Em caso de sucesso: retorna o resultado como `String`
* Em caso de erro: retorna mensagem clara explicando o problema

Exemplos:

```
"12.0"
```

```
"validation_error: values is required for variadic formulas"
```

---

## Construtor híbrido de fórmulas privadas

Fórmulas privadas aceitam duas formas de entrada no mesmo contrato:

- `TEXT`: mantém o comportamento existente, usando `equation` e `parameters` digitados pelo usuário.
- `BUILDER`: recebe `expressionTree`; o backend gera `equation`, `displayEquation`, `parameters` e snapshots das fórmulas públicas utilizadas. A equação enviada pelo cliente é ignorada nesse modo.

Se `inputMode` não for informado, o backend usa `BUILDER` quando existir uma árvore e `TEXT` nos demais casos. Isso mantém compatibilidade com clientes anteriores.

### Exemplo em modo texto

```json
{
  "name": "DOBRO_MAIS_UM",
  "group": "ALGEBRA",
  "description": "Dobro de um valor mais um",
  "inputMode": "TEXT",
  "equation": "X * 2 + 1",
  "parameters": ["X"]
}
```

### Exemplo em modo visual

```json
{
  "name": "AREA_DUPLA",
  "group": "GEOMETRY",
  "description": "Duas vezes uma fórmula pública de área",
  "inputMode": "BUILDER",
  "expressionTree": {
    "id": "multiply-root",
    "type": "OPERATOR",
    "operator": "MULTIPLY",
    "children": [
      {
        "id": "source-area",
        "type": "FORMULA",
        "sourceFormulaId": "ID_DA_FORMULA_PUBLICA",
        "bindings": {
          "WIDTH": {
            "id": "width-variable",
            "type": "VARIABLE",
            "variableName": "BASE"
          },
          "HEIGHT": {
            "id": "height-variable",
            "type": "VARIABLE",
            "variableName": "ALTURA"
          }
        }
      },
      {
        "id": "constant-two",
        "type": "CONSTANT",
        "constantValue": "2"
      }
    ]
  }
}
```

Tipos de nó disponíveis: `FORMULA`, `OPERATOR`, `VARIABLE` e `CONSTANT`. Operadores disponíveis: `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, `POWER` e `NEGATE`.

Endpoints auxiliares:

- `GET /api/formulas/builder/catalog`: fórmulas públicas fixas disponíveis para arrastar.
- `POST /api/formulas/mine/validate`: valida texto ou árvore sem salvar e retorna a equação compilada.
- `POST /api/formulas/builder/preview`: valida e executa uma fórmula com valores de teste sem salvar.

O preview recebe `{ "formula": { ... }, "variables": { "BASE": 10, "ALTURA": 5 } }`.

Fórmulas variádicas ainda não podem ser usadas como blocos do construtor visual, mas continuam funcionando nos endpoints existentes.

## Validações Aplicadas no Backend

### Fórmulas Fixas

* `equation` obrigatória
* `parameters` obrigatório e sem duplicatas
* Todos os parâmetros devem ser enviados
* Parâmetros extras não são permitidos

### Fórmulas Variádicas

* `equation` obrigatória
* `values` obrigatório
* `minParams >= 1` (quando informado)
* `maxParams >= minParams` (quando informado)

---

# Documento de Design do Sistema de Assistente de Matemática (Java + ReactJS / React Native)

## 1. Introdução

Este documento descreve a arquitetura e os requisitos do sistema de assistente de matemática. O backend é Java (Spring Boot). O frontend web utiliza ReactJS e o mobile React Native. A integração é realizada via APIs RESTful.

## 2. Requisitos Funcionais

### 2.1 Registro e Autenticação de Usuários

* Permitir que os usuários se registrem e façam login na aplicação.
* Autenticar usuários através de tokens de acesso.

### 2.2 Consulta de Fórmulas

* Permitir que os usuários pesquisem e visualizem fórmulas matemáticas disponíveis.

### 2.3 Cálculo de Fórmulas

* Permitir a execução de fórmulas fixas e variádicas.
* Receber parâmetros ou valores via REST e retornar o resultado calculado.

## 3. Requisitos Não Funcionais

### 3.1 Desempenho

* APIs RESTful otimizadas para respostas rápidas.
* Boas práticas de performance no frontend (ReactJS / React Native).

### 3.2 Segurança

* Autenticação e autorização robustas.
* Comunicação via HTTPS.

### 3.3 Interface do Usuário

* Interface intuitiva e responsiva para web e mobile.

## 4. Arquitetura do Sistema

### 4.1 Frontend

* Web: ReactJS (SPA).
* Mobile: React Native (cross-platform).
* Comunicação via `fetch` ou `axios`.

### 4.2 Backend

* Spring Boot para APIs RESTful.
* MongoDB para persistência de fórmulas.
* EvalEx para avaliação de expressões matemáticas.

## 5. Fluxo de Trabalho

1. Usuário autentica.
2. Seleciona fórmula.
3. Informa valores.
4. Backend valida e calcula.
5. Resultado é retornado ao frontend.

## 6. Considerações de Implementação

* Spring Security para autenticação.
* Testes automatizados no backend e frontend.

## 7. Considerações de Manutenção

* Monitorar desempenho.
* Atualizar dependências e aplicar patches de segurança.

## 8. Conclusão

Este documento estabelece a base arquitetural e funcional do Math Assistant, permitindo evolução contínua com suporte a fórmulas dinâmicas e escaláveis.
