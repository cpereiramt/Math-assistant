# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

##  [1.1.0] - 2026-01-25
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



