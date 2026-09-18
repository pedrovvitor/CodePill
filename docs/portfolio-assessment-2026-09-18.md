# Assessment de portfólio — 18/09/2026

## Parecer

**Sim, já vale compartilhar seu GitHub como portfólio de engenharia em evolução.** Há implementação, testes e decisões técnicas suficientes para uma conversa séria de backend/full stack. Ainda não apresentaria os três como produtos completos ou prontos para produção.

Para enviar hoje: destaque **Talent Intelligence Platform**, use **wexchange** como caso de engenharia backend e deixe **CodePill** como projeto privado disponível sob convite até resolver sua exposição pública. O link público do CodePill retorna 404 porque sua visibilidade é privada, confirmada pela API autenticada.

O maior retorno agora vem de fechar jornadas demonstráveis, corrigir apresentação e publicar evidências. Expandir infraestrutura ou adicionar agentes antes disso tem retorno menor para o showcase.

## Método e limites

Revisão estática amostral de código, configurações, testes, README, governança, issues, PRs e resultados de GitHub Actions. Não executei as aplicações, novas suítes, pentest, varredura completa de segredos ou medição visual/acessibilidade. CI verde significa sucesso dos gates configurados, não ausência de vulnerabilidades ou qualidade de produto comprovada. Não avaliei seu CV, que não foi fornecido.

| Projeto | Revisão avaliada | Evidência de CI | Visibilidade |
|---|---|---|---|
| wexchange | `7f3361e14e1d6feb2ba795b06917344cddb23c48` | [main verde](https://github.com/pedrovvitor/wexchange/actions/runs/33925387979) | Público |
| Talent Intelligence | `96987373c4da2884f0851a71d78e3a3ef6be6059` | [main verde](https://github.com/pedrovvitor/talent-intelligence-platform/actions/runs/34923766721) | Público |
| CodePill | código local `1119c769e4836be6a81a23e25c24a5bafe6930f3`; remoto `6e19eb4d15e13dd959b5c13460ffa80583cdcd27` | [código avaliado verde](https://github.com/pedrovvitor/CodePill/actions/runs/35321160514); [HEAD sem conclusão na consulta](https://github.com/pedrovvitor/CodePill/actions/runs/35339723443) | Privado |

A comparação do CodePill confirmou que o remoto só altera o título do README; a análise de código local continua aplicável. Links GitHub abaixo apontam para main, salvo os runs; os SHAs acima fixam o estado desta avaliação. Nenhuma issue foi publicada por este assessment. As propostas abaixo estão prontas para registro; onde já há issue, reutilizá-la.

## Comparação

| Dimensão | wexchange | CodePill | Talent Intelligence |
|---|---|---|---|
| História para recrutador | Fraca: README começa pelo Docker | Boa história técnica, mas inacessível publicamente | Melhor problema/solução e limites explícitos |
| Engenharia demonstrada | Domínio monetário, idempotência, resiliência, migrations, PIT/ArchUnit | RBAC/PKCE, cache transacional, OTel, testes e Helm/GitOps | Políticas determinísticas, isolamento por tenant, auditoria e busca vetorial |
| Produto demonstrável | API; demo offline pendente | Feed e criação; leitura de conteúdo e curadoria pela UI incompletas | Matching e workspace; experiência candidata ainda em evolução |
| Evidências de qualidade | Gates quantitativos fortes; CI de segurança em PR | Gates 85%, integração e Playwright; hardening documentado | Testes reais de segurança/dados; faltam cobertura medida e E2E de navegador no CI |
| Principal risco de apresentação | README incorreto + dívida de dependências | Promessa de produção maior que evidência pública e fluxo visual | Confundir score explicável com relevância validada |
| Uso recomendado | Case backend complementar | Destaque Java/plataforma após tornar acessível | Primeiro link hoje, especialmente para JVM + IA |

### wexchange

O código é mais forte que sua apresentação. `build.gradle` conecta `check` a integração, ArchUnit, cobertura e mutação. Os pisos são 80% linhas/70% branches globais, 90%/85% nos pacotes centrais, PIT 80% e nenhum mutante não morto nas regras financeiras selecionadas. São **gates**, não percentuais medidos nesta revisão. Testes incluem idempotência, constraints, retenção, sincronização e falhas do provedor. [Build](https://github.com/pedrovvitor/wexchange/blob/main/build.gradle).

Há dois erros concretos no quickstart: anuncia Swagger na stack com perfil `production`, que desabilita Swagger/OpenAPI; e diz ser necessário construir o JAR antes, embora o Dockerfile já seja multi-stage. O README também não explica de início qual compra é convertida, a fonte histórica e a regra temporal. [README](https://github.com/pedrovvitor/wexchange/blob/main/README.md), [perfil](https://github.com/pedrovvitor/wexchange/blob/main/src/main/resources/application-production.yml), [Dockerfile](https://github.com/pedrovvitor/wexchange/blob/main/Dockerfile).

A issue #42 registra 32 achados de scanner, sendo 4 críticos, no trabalho de CI. Confirmei as versões antigas declaradas no build, mas não reproduzi o scan nem concluí explorabilidade individual. Isso bloqueia uma alegação de segurança operacional, não o envio do código como projeto em melhoria. A postura anônima é deliberada e documentada para dados sintéticos; não recomendo adicionar login apenas para parecer mais sofisticado. [#42](https://github.com/pedrovvitor/wexchange/issues/42), [ADR de API anônima](https://github.com/pedrovvitor/wexchange/blob/main/docs/adr/0002-anonymous-api-security-baseline.md).

Os PRs [#40](https://github.com/pedrovvitor/wexchange/pull/40) (observabilidade, CI verde no head consultado) e [#41](https://github.com/pedrovvitor/wexchange/pull/41) (CI, execução consultada falhou) estão abertos. Não contei essas melhorias como entregues em main. O roadmap #13 marca #19 e #10 concluídas embora as issues permaneçam abertas; precisa reconciliação.

### CodePill

Há investimento técnico verificável: módulos de domínio, portas/adapters, matriz de autorização, Redis com invalidação após commit, tracing, containers e charts. Os testes Playwright exercitam login, criação e feed; publicação na preparação do feed acontece pela API. Isso é uma base consistente de catálogo autenticado.

O principal gap funcional é direto: `PillCard.tsx` só mostra título, resumo, tipo e duração. `router.tsx` não oferece rota de leitura de pill nem curadoria; há feed e criação. Assim, o visitante ainda não percorre sozinho “criar → publicar → ler conteúdo → concluir aprendizado” no navegador. Não é preciso implementar toda gamificação para resolver: leitura e curadoria já tornam o catálogo demonstrável.

O README ainda afirma demonstrar “production-ready” de ponta a ponta, enquanto o próprio backlog registra go-live pendente, ingress rate limiting, NetworkPolicies a verificar no k3s, dashboards/Sentry e outros controles. Tratar como referência de engenharia com validação local é mais defensável. O changelog também diverge dos standards sobre ADRs e dependências Spring na camada application. São problemas de consistência da documentação, não prova de que a arquitetura é inútil.

Evidências locais: `frontend/src/ui/components/PillCard.tsx`, `frontend/src/app/router.tsx`, `e2e/tests/`, `.github/workflows/main.yml`, `backend/codepill-catalog/application/pom.xml`, `docs/standards/STATE_OF_THE_APP.md`.

### Talent Intelligence Platform

É o melhor ponto de entrada atual: problema claro, pipeline compreensível, limites de IA explícitos e separação entre release entregue e roadmap. A implementação usa tenant no acesso vetorial e aplica elegibilidade antes da classificação final, registrando a decisão. Testes cobrem JWT real, isolamento e migrations. [README](https://github.com/pedrovvitor/talent-intelligence-platform/blob/main/README.md), [MatchingService](https://github.com/pedrovvitor/talent-intelligence-platform/blob/main/src/main/kotlin/io/github/pedrovvitor/talentintelligence/application/MatchingService.kt).

Há uma limitação concreta de recuperação: o serviço busca 100 candidatos semânticos e só depois filtra elegibilidade. Pode retornar poucos/nenhum resultado mesmo havendo vagas elegíveis além dessa janela. Também faz uma consulta `findById` por candidato, até 100 leituras adicionais. São riscos inferidos do algoritmo, ainda sem benchmark de impacto, que merecem testes direcionados. [Índice vetorial](https://github.com/pedrovvitor/talent-intelligence-platform/blob/main/src/main/kotlin/io/github/pedrovvitor/talentintelligence/adapter/persistence/PgVectorJobIndex.kt).

O score 70% semântico + 30% cobertura de skills é uma heurística implementada; sua qualidade não está demonstrada por dataset rotulado. “Explicável” não significa “validado”. O CI testa/builda backend e frontend, mas o job Compose apenas valida configuração; não sobe o produto e percorre OIDC/matching no navegador. Não há gate de cobertura nos arquivos de build examinados. [CI](https://github.com/pedrovvitor/talent-intelligence-platform/blob/main/.github/workflows/ci.yml), [build](https://github.com/pedrovvitor/talent-intelligence-platform/blob/main/build.gradle.kts).

## Backlog pronto para registro

Prioridades deste assessment: **P0** resolve impedimento imediato de compartilhamento/apresentação; **P1** prepara showcase confiável; **P2** melhora maturidade depois. P0 aqui não significa severidade de vulnerabilidade. Esforço: P = poucas horas a 1 dia; M = 2–4 dias; G = 5+ dias, estimativa orientativa dependente do ambiente. Critérios que mudam comportamento devem virar testes de regressão antes da implementação.

### wexchange — reutilizar 6 issues

| ID/prioridade | Registro | Escopo e critérios de aceite | Esforço |
|---|---|---|---|
| WX-01 · P0 | Ampliar [#12](https://github.com/pedrovvitor/wexchange/issues/12) | Corrigir quickstart/Swagger/JAR; abrir README com problema e exemplo de resultado; explicar taxas históricas e dados sintéticos; executar comandos em clone limpo; incluir diagrama, limites, licença e evidências. Separar apresentação imediata da futura release estável. | P/M |
| WX-02 · P1, antes de deploy público | Executar [#42](https://github.com/pedrovvitor/wexchange/issues/42) | Atualizar dependências e validar compatibilidade; scan do artefato final sem HIGH/CRITICAL corrigíveis não justificados; regressão de dinheiro, idempotência, HTTP e migrations verde; exceções com responsável e expiração. Não tratar baseline de ignores como correção. | M |
| WX-03 · P1 | Concluir [#10](https://github.com/pedrovvitor/wexchange/issues/10), PR #41 | Diagnosticar falha real, revisar/mesclar quando verde; CI de main executa gates, scan, smoke de container e publica relatórios; evitar sucesso que só esconde dívida por ignore. | M |
| WX-04 · P1 | Concluir [#9](https://github.com/pedrovvitor/wexchange/issues/9), PR #40 | Validar unidades de Timer e nomes, logs estruturados/correlação; teste com duração conhecida; atualizar roadmap #13 pelo estado efetivamente entregue, inclusive #19. | P/M |
| WX-05 · P1 | Executar [#11](https://github.com/pedrovvitor/wexchange/issues/11) | Um comando offline com compra → taxa → conversão real HTTP/Postgres; resultado monetário exato; cenário de falha controlado; mesmo comando no CI. | M |
| WX-06 · P2 | [#20](https://github.com/pedrovvitor/wexchange/issues/20) + [#21](https://github.com/pedrovvitor/wexchange/issues/21) | Interface focada e acessível, com taxa/data/fonte/limitações e testes de navegador. Para vaga backend, demo de API bem feita já permite compartilhar antes desta entrega. | G |

### CodePill — 6 novas issues propostas

Não havia issues abertas na consulta autenticada; os itens existentes estão no changelog.

| ID/prioridade | Título proposto | Escopo e critérios de aceite | Esforço |
|---|---|---|---|
| CP-01 · P0 | Preparar acesso verificável ao repositório para recrutadores | Escolher público ou convite direcionado; antes de público executar scan de histórico/artefatos e resolver achados; confirmar README/licença e acesso com sessão anônima após publicação. Convite deve ser validado com destinatário. Não alterar visibilidade automaticamente como parte do assessment. | P, mais eventuais correções |
| CP-02 · P1 | Completar leitura e curadoria de pills no navegador | Rota de detalhe renderiza conteúdo por tipos permitidos, estados 404/erro/loading; curator publica pelo UI; author não publica indevidamente; Playwright cria, publica e lê texto completo via browser. Tratar conteúdo não confiável sem HTML arbitrário. | M/G |
| CP-03 · P1 | Publicar showcase fiel ao estado implementado | README com screenshot real, roteiro de 3 minutos, quickstart Docker da pasta ops/local-demo, escopo atual e limites; substituir alegação ampla de produção por evidências específicas; explicar que aprendizado/progresso ainda é futuro. | P/M |
| CP-04 · P1 para demo hospedada | Validar ambiente público e controles de borda | Reutilizar charts; domínio/TLS, rate limit, isolamento de administração e métricas; provar NetworkPolicies no CNI real; smoke OIDC/401/403/2xx e jornada CP-02; reset de dados sintéticos; restaurar backup e registrar resultado. Pode ser adiado se entregar demo local + vídeo. | G |
| CP-05 · P2 | Alinhar governança, gates e auditoria com a implementação | ADR para exceção application/Spring; reconciliar decisão sobre ADRs; atualizar snapshot/testes/rotas; implementar lint backend ou formalizar desvio; documentar gates condicionais; verificar auditoria imutável de ações privilegiadas, pois log operacional sozinho não a comprova. Dividir em subtarefas. | M/G |
| CP-06 · P2 | Entregar primeira conclusão de aprendizado | Learner lê e conclui pill; progresso persistido e isolado por identidade; conclusão idempotente, métrica e testes 401/403/2xx; sem exigir streaks, gamificação ou novos serviços distribuídos. | G |

### Talent Intelligence — 8 itens, com reaproveitamento

| ID/prioridade | Registro | Escopo e critérios de aceite | Esforço |
|---|---|---|---|
| TIP-01 · P1 | Nova: Provar jornada de produto em browser no CI | Playwright sobe stack real; login recruiter/candidate, matching com evidência, isolamento, expiração e recuperação de erro; CI executa produto, não apenas compose config; artifacts em falha. | M |
| TIP-02 · P1 | Nova: Demonstrar qualidade e limites do matching | Ampliar [#20](https://github.com/pedrovvitor/talent-intelligence-platform/issues/20): dataset sintético rotulado e versionado, baseline lexical, Recall@10/nDCG e violações de elegibilidade; separar avaliação de ajuste; registrar idioma suportado, amostra, modelo e hardware; justificar pesos/limiares com dados. Não precisa esperar Redis/custos de modelo remoto. | M/G |
| TIP-03 · P1 | Nova: Evitar perda de elegíveis após janela semântica | Teste com mais de 100 vagas próximas inelegíveis e vaga elegível além da janela; implementar filtragem compatível com regras ou recuperação adaptativa limitada; medir recall/latência e preservar tenant/modelo. Dependência: baseline TIP-02. | M |
| TIP-04 · P1 | Reutilizar [#38](https://github.com/pedrovvitor/talent-intelligence-platform/issues/38), [#40](https://github.com/pedrovvitor/talent-intelligence-platform/issues/40) | Catálogo navegável, detalhe, perfil candidato coerente; timeout/network/API distintos, retry e preservação de resultado anterior; desktop/mobile e testes. Candidatura #39 pode vir depois se não for prometida na demo. | M/G |
| TIP-05 · P1 | Nova: Adicionar gates quantitativos e fitness de arquitetura | Medir cobertura backend/frontend, adotar piso justificado e publicar relatórios; impedir imports proibidos no domínio; scanner de segredos/dependências bloqueante como primeira fatia de [#24](https://github.com/pedrovvitor/talent-intelligence-platform/issues/24). Não copiar automaticamente piso do CodePill sem decisão própria. | M |
| TIP-06 · P1 | Nova: Publicar kit visual de demonstração | Screenshots reais + vídeo curto com identidade sintética, consulta, resultado e explicação; instruções reproduzíveis; separar v0.2 entregue de main em evolução; indicar ausência de billing e agentes onde pertinente. | P/M |
| TIP-07 · P2 | Nova: Medir e reduzir consultas por candidato recuperado | `findById` dentro do loop gera até 100 consultas; medir query count/p95, substituir por leitura em lote preservando escopo de tenant e consistência da auditoria; testar resultados iguais e limite de consultas. | M |
| TIP-08 · P2; antes de hospedagem, elevar borda a P1 | Reutilizar [#17](https://github.com/pedrovvitor/talent-intelligence-platform/issues/17), [#23](https://github.com/pedrovvitor/talent-intelligence-platform/issues/23) | Traces e logs sem PII; request/rate limits e TLS antes de expor matching CPU-bound; teste de sobrecarga e evidência de redaction. Kubernetes #22 não é pré-requisito para compartilhar o código. | M/G |

## Ordem recomendada

1. **Enviar agora:** GitHub e Talent Intelligence como principal; wexchange como case de evolução backend; CodePill apenas com acesso acordado. Não adiar a oportunidade até terminar o roadmap.
2. **Primeira entrega curta:** WX-01, CP-01, CP-03 e TIP-06. Uma história clara e prova visual ajudam o avaliador antes de ele ler o código.
3. **Confiabilidade demonstrável:** WX-02/03/04/05; TIP-01/02/03/04; CP-02. Corrigir fundamentos e tornar fluxos verificáveis.
4. **Depois:** deploy público validado, progresso de aprendizado, observabilidade adicional e melhoria de performance medida. Agentes/SSE, WAF avançado e expansão cloud não precisam bloquear o showcase inicial.

Para vaga Java/backend, enfatize correção monetária, idempotência e cache transacional. Para JVM/IA, lidere com Talent Intelligence e explique por que o modelo não decide elegibilidade. Para plataforma/Kubernetes, use CodePill distinguindo claramente charts e validação local de operação pública comprovada.

Mensagem possível ao recrutador: “Seguem meu GitHub e três projetos em evolução. Destaco o Talent Intelligence, com matching semântico e políticas determinísticas, e o wexchange, focado em correção e resiliência de backend. Também desenvolvo o CodePill, com Java/React, observabilidade e infraestrutura Kubernetes; o repositório está privado e posso disponibilizar acesso.”
