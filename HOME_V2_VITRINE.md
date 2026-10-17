# FreeSound V2 — Vitrine automática

## Mudança de arquitetura da interface
A versão anterior apenas usava cores similares à Steam. A V2 passa a usar composição de vitrine:
- cabeçalho azul escuro, com barra de pesquisa permanente;
- hero de destaque, alimentado por uma música real retornada pelos catálogos (não existe faixa inventada);
- prateleiras temáticas com nomes definidos, capa e botões de reprodução/origem/download;
- deslize horizontal dentro das prateleiras;
- rolagem vertical da página inicial, carregando novas prateleiras automaticamente quando a pessoa se aproxima do fim;
- fontes e filtros avançados separados da descoberta inicial;
- botão de volta à vitrine e atalho de navegação inferior.

## Títulos de prateleiras
Mais baixadas; Instrumental para descobrir; Sons da natureza; Eletrônica em destaque; Para relaxar; Jazz e blues; Clássica para ouvir; Lo-fi e beats; Acústico e folk; Meditação e foco; Rock independente; Hip-hop instrumental; Trilhas cinematográficas; Piano e teclas.

Estas são curadorias de **consultas**. Os títulos e artistas das músicas não são gerados. O app consulta Internet Archive, Wikimedia Commons e Openverse. Se um provedor não retorna dados, mostra mensagem de indisponibilidade; quando adequado, tenta uma segunda fonte, mantendo a origem real em cada card.

## Rolagem e busca
O carregamento começa automaticamente na primeira abertura, com 3 prateleiras. Ao se aproximar do final da página, adiciona mais 2. Cada ciclo de 14 seções consulta páginas seguintes, sem repetir deliberadamente a mesma primeira página. A vitrine nesta versão oferece até 6 ciclos (84 seções), para evitar crescimento indefinido de consumo de memória; não deve anunciar conteúdo infinito se a API se esgotar. A navegação não tem botão 'carregar mais'.

A busca manual mantém os filtros avançados da v1.0.6. O usuário pode tocar em 'Ver tudo' na prateleira para abrir a pesquisa correspondente e na navegação 'Descobrir' para voltar à home.

## Licenciamento e limitações
A fonte pode conter gravações cuja licença exibida precisa de confirmação manual. Downloads diretos continuam condicionados à allowlist de domínios e metadados de licença. Openverse pode ser usado para descoberta, mas não concede download irrestrito. Não há extração de DRM. As APIs online são variáveis, sujeitas a indisponibilidade e não testadas em aparelho físico nesta entrega.

## Testes
Os testes unitários verificam o plano editorial das prateleiras e a mudança das páginas para evitar a repetição da primeira página. O GitHub Actions valida a compilação Android. Testes no dispositivo continuam necessários.
