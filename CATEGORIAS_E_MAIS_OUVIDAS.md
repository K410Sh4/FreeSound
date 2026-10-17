# Categorias e mais ouvidas — FreeSound

## Categorias (busca temática)
Presets de descoberta disponíveis: Instrumental, Eletrônica, Rock, Jazz, Clássica, Hip-hop, Lo-fi, Ambiente, Natureza, Meditação, Pop, Reggae, Folk e Trilhas.
Um toque no chip insere palavras-chave nas buscas dos provedores; o usuário ainda pode digitar artista ou título para combinar termos. A categoria Todas retorna à busca livre. Os provedores não usam uma taxonomia de gênero consistente, portanto os resultados são associados ao termo pesquisado e **não** rotulados como gênero verificado.

## Três rankings distintos
1. **Pesquisar**: resultados sem ordem global arbitrária.
2. **Mais baixadas**: contagem acumulada de downloads de itens no Internet Archive, que pode incluir coleções ou álbuns; não corresponde a escutas individuais.
3. **Mais ouvidas**: suas próprias reproduções no aparelho, ordenadas por quantas audições efetivas foram registradas para cada item.

## Contagem de reprodução
Um toque em Ouvir/Reproduzir inicia uma sessão. Apenas tempo real com player em reprodução conta: 20 segundos, ou 55% da duração para áudio com menos de 40 segundos. Seek (pular trecho) não aumenta a contagem; a sessão conta no máximo uma vez. O usuário pode reproduzir a mesma faixa novamente e gerar outra contagem. Armazenamento local via SharedPreferences, mantendo os últimos 300 itens ouvidos. Sem telemetria ou coleta remota.

## Escopo e limitações
Os dados do ranking pessoal começam a ser contabilizados após a atualização, não são retroativos. O histórico existe apenas nesta instalação; desinstalar apagará os dados. O build debug continua com assinatura temporária, diferente dos builds anteriores. Para atualizar preservando dados e histórico, configurar release com keystore persistente. APIs e categorias exigem verificação em dispositivo.
