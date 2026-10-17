# FreeSound: navegação inspirada na Steam e pesquisa avançada

Versão do design: interface do catálogo Android com paleta azul-marinho, azul-claro, camadas escuras e capas em destaque. A marca e os elementos gráficos da Steam não foram copiados.

## Navegação
- Navegação inferior: Descobrir, Biblioteca e Fontes.
- Descobrir: Explorar, Mais baixadas (Internet Archive) e Suas ouvidas (histórico local).
- Carrossel de 14 estilos. Uma categoria é uma sugestão de palavras-chave, não uma prova de gênero.
- Cards de música: capa oficial quando disponível, artista, licença, disponibilidade do download, abrir origem, favoritos e reprodução.
- Painel compacto de filtros com expansão opcional.

## Como os filtros funcionam
- Texto digitado vai aos catálogos junto com a categoria selecionada.
- Busca somente nas fontes selecionadas, evitando consultas desnecessárias.
- Correspondência local em título/artista ou ambos; acentos e maiúsculas são ignorados. Com o modo estrito, **todas** as palavras digitadas precisam estar no título ou artista (não precisam estar juntas).
- Se a API retorna um resultado relacionado apenas por outras informações, é possível usar **Incluir relacionados**. A interface também oferece esse controle caso o resultado estrito seja vazio.
- **Só downloads** mostra apenas itens sinalizados como baixáveis pelo mecanismo de licença do FreeSound. Não garante autorização jurídica absoluta.
- **Com capa** seleciona músicas com imagem fornecida pela fonte ou associada ao arquivo.
- Ordenação: relevância (priorizando correspondência de título), contagem acumulada de downloads do item (dados apenas quando fornecidos), título A-Z.
- Resultados deduplicados por ID retornado do catálogo, mostrando o número real de itens exibidos.
- Painel com opção para limpar filtros.

## Limites
As APIs têm formatos distintos. Nem todo resultado tem imagem, licença ou contagem de downloads; valores não são inventados. A fonte original deve ser consultada para conferir direitos de uso. O filtro de categoria não usa classificação automática independente por áudio. Instalações debug de builds diferentes podem ter assinaturas incompatíveis; recomenda-se criar keystore persistente antes de usar como aplicativo principal.
