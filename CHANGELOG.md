# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
No changes yet.

## [2.4.0] - 2026-09-05
### Added
- Adicionada a camada social para fórmulas públicas, com votos positivos e negativos, métricas de avaliação e contagem de comentários.
- Adicionados endpoints autenticados para publicar fórmulas privadas, votar, remover votos e gerenciar comentários e respostas.
- Adicionados comentários hierárquicos com `parentCommentId`, respostas paginadas sob demanda e limite máximo de cinco níveis por thread.
- Adicionado carregamento paginado de comentários raiz e respostas para evitar a renderização integral de árvores grandes.
- Adicionada validação de autorização para impedir votos do proprietário na própria fórmula e alterações de comentários por usuários não autores.
- Adicionados índices MongoDB para métricas sociais e integridade de votos únicos por usuário e fórmula.
- Adicionados testes para votos, comentários, autorização, paginação e profundidade máxima das threads.

## [2.3.0] - 2026-09-05
### Added
- Adicionado o endpoint autenticado `GET /api/users/me` para retornar o contexto do usuário atual, incluindo nome, e-mail, imagem, plano e função.
- Adicionado tratamento específico para usuário não encontrado no endpoint de perfil.
- Adicionados testes do controller e do serviço para o contexto do usuário.

## [2.2.0] - 2026-08-24
### Added
- Adicionado o endpoint `GET /api/formulas/search` com busca textual, filtros por grupo e tipo, escopo público ou do usuário, paginação e ordenação configurável.
- Adicionado suporte a índices MongoDB para a busca de fórmulas, com scripts de atualização para Compass e PowerShell.
- Adicionado contrato `FormulaSearchResponse` e tratamento de erros para consultas de busca inválidas.
- Adicionados testes de serviço para os cenários de busca e filtragem.

## [2.1.0] - 2026-08-22
### Added
- Adicionado builder visual de fórmulas com nós para variáveis, constantes, operadores e composição de fórmulas públicas.
- Adicionados os endpoints `GET /api/formulas/builder/catalog` e `POST /api/formulas/builder/preview` para catálogo e pré-visualização de fórmulas.
- Adicionada compilação de árvores de expressão com operadores aritméticos, validação de profundidade e limite de nós.
- Adicionado registro das fórmulas públicas utilizadas na composição por meio de snapshots de origem.
- Adicionada criação, consulta, atualização, exclusão, validação e execução de fórmulas privadas do usuário em `/api/formulas/mine`.
- Adicionado controle de propriedade e acesso para impedir que usuários executem ou alterem fórmulas privadas de terceiros.
- Adicionados testes para o compilador de árvores e para o fluxo híbrido de entrada de fórmulas.

### Documentation
- Documentado o processo de migração dos índices de fórmulas.
- Atualizada a documentação com exemplos do builder visual e dos fluxos de fórmulas privadas.

## [2.0.0] - 2026-06-17
### Changed
- Substituído o motor de execução de fórmulas de `exp4j` para `EvalEx` (`3.6.2`), mantendo o suporte a variáveis e propagando erros de parsing e avaliação como equações inválidas.
- Atualizado o compilador de equações e as validações para o novo motor matemático.
- Ajustado o modelo de fórmula para suportar os metadados necessários ao novo fluxo de execução.

### Removed
- Removido o campo `operator` da lógica de fórmulas variádicas; essas fórmulas passam a usar `equation`.
- Removido o validador específico de fórmulas variádicas que deixou de ser necessário após a consolidação das validações.

### Security
- Ajustada a configuração de segurança para permitir os fluxos autenticados necessários à execução e ao gerenciamento de fórmulas.

### Documentation
- Adicionada documentação detalhada do projeto e atualizados os exemplos de execução e de fórmulas variádicas.

## [1.1.0] - 2026-01-25
### Added
- Adicionando: suporte para fórmulas variádicas (Feat: adding support for variadic formulas).
- Adicionado: normalização de variáveis para fórmulas fixas (Feat: implementing variable normalization for fixed formulas).
- Adicionado: documentação atualizada para fórmulas variádicas (docs: updating documentation for variadic formulas).
- Adicionado: exemplos de payload para fórmulas variádicas (docs: adding payload examples for variadic formulas).
- Adicionado: validação para remoção de anotações de validação desnecessárias (chore: removing unnecessary validation annotations).

## [1.0.0] - 2026-01-19
### Documentation
- Ajustes para deploy no ec2 aws e integração com o frontend.
 
## [0.4.2] - 2026-01-03
### Documentation
- Atualizado: documentação do projeto (docs: updating docs).

## [0.4.1] - 2025-07-09
### Fixed
- Corrigido: lógica de status para `Formula` (Fix: implementing status logic for formulas).
- Corrigido: salvamento de tradução de comentários (Fix: saving translation for comments).

## [0.4.0] - 2025-07-06
### Added
- Adicionado: validações e tratamento de erros da aplicação (Feat: configuring validations and error handles on application).

### Changed
- Ajustado: anotações TODO adicionadas para acompanhamento (feat: adding some TODO annotations).

## [0.3.0] - 2025-07-05
### Added
- Adicionado: rota de delete para `Formula` (Feat: configuring delete route).

## [0.2.1] - 2024-12-31
### Changed
- Ajustado: fluxo OAuth (feat: adjusting oauth flow).

## [0.2.0] - 2024-09-05
### Added
- Adicionado: lógica de execução de fórmulas (strategy pattern) (Feat: Implementing formula logic using strategy pattern).
- Adicionado: dependências `exp4j` e MongoDB (Feat: Installing exp4j and mongodb dependencies).
- Adicionado: `docker-compose.yml` para MongoDB local (Feat: Creating docker compose.yml for automate Mongodb configuration on localhost).
- Adicionado: propriedades locais do MongoDB (Feat: Adding properties for mongodb database locally).

### Changed
- Ajustado: `SecurityConfig` para permitir métodos POST quando necessário (Feat: Change SecurityConfig settings to allow Post method).

### Fixed / Chore
- Removido comentário em configuração JWT (Feat: Removing comment from Jwt configuration).


## [0.1.0] - 2024-07-16
### Added
* Generating JWT after successful authentication.


## [0.0.3] - 2024-06-25
### Added
* Including authentication with Google provider with Oauth 2.0.

## [0.0.2] - 2024-05-08
### Added 
* Creating Login and Hello world endpoint.

## [0.0.1] - 2024-04-28

### Added
* Adding Initial Spring folder structure and configuration
* Adding README file
* Adding CHANGELOG



