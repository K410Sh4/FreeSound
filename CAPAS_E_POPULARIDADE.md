# Capas e classificação por popularidade

## Capas
- Internet Archive: miniatura oficial do item (archive.org/services/img/IDENTIFIER).
- Openverse: miniatura própria da API de áudio quando disponível; sem imagem, um ícone.
- Wikimedia Commons: miniatura de imageinfo quando disponibilizada para o áudio.
- Música importada e músicas baixadas: tentativa de ler imagem incorporada ao arquivo com MediaMetadataRetriever.
- Ausência ou erro: ícone padrão do FreeSound; nunca usa uma capa inventada ou imagem de outra música.
- Cache em memória e no armazenamento de cache do Android. Limite de leitura de imagem 3 MB, redirecionamentos externos bloqueados.

## Mais populares
- O filtro Mais populares consulta o Internet Archive ordenando pelo campo downloads, do maior ao menor.
- O número exibido é a contagem de downloads do item do Archive, que pode ser uma coleção e não necessariamente da faixa individual.
- Wikimedia Commons e Openverse não oferecem a mesma métrica neste projeto, por isso são excluídos desse filtro, sem números fictícios.
- A busca normal preserva os resultados de todas as fontes.
- Nenhuma mudança nas regras de direitos autorais: cada música continua dependendo de licença permissiva verificável.

## Limites
A contagem do Archive não equivale ao ranking global de músicas (Spotify, Billboard etc.). A exibição das capas depende do catálogo oferecer imagem. Dados e conectividade das APIs precisam ser validados em aparelho físico.
