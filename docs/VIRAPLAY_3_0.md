# ViraPlay 3.0 - versão de teste consolidada

Esta versão foi preparada para uso real com os primeiros clientes, priorizando estabilidade, navegação simples e baixo custo de infraestrutura.

## Cliente Android / Android TV
- Um único APK para celular, tablet, Android TV e TV Box.
- Interface própria para TV com navegação por controle remoto.
- Interface própria para celular com barra inferior.
- Home simples e direta.
- Ao vivo com categorias, busca, favoritos e programação quando a fonte oferece EPG.
- Filmes com categorias e capas.
- Séries organizadas como série > temporada > episódios.
- Favoritos.
- Continuar assistindo.
- Progresso local de filmes e episódios.
- Pergunta para continuar de onde parou ou reiniciar.
- Player em tela cheia e rotação no celular.
- Retorno à tela anterior sem recriar o catálogo.
- Suporte ViraPlay pelo WhatsApp: +55 84 9927-6322.

## Catálogo e desempenho
- Detecta URL no padrão Xtream `get.php` e usa `player_api.php` quando disponível.
- Separa TV, filmes e séries pelo servidor, evitando classificação por nome.
- Usa capas e metadados fornecidos pelo servidor.
- Episódios de séries são carregados somente ao abrir a série, reduzindo o catálogo inicial.
- Cache local em SQLite.
- Abre usando o catálogo salvo e atualiza em segundo plano.
- Atualização automática a cada 6 horas ou manual.
- Se o backend ViraPlay ficar temporariamente fora do ar, o catálogo já salvo continua disponível.
- Fallback para M3U quando a API estruturada não estiver disponível.

## ADM
- Painel, Pendentes e Clientes.
- Novo aparelho aparece automaticamente em Pendentes.
- Para ativar: nome + URL M3U. Não precisa digitar o código novamente.
- Exibe usuário detectado na URL quando existir.
- Pesquisa por nome, código ou usuário.
- Editar lista e nome.
- Bloquear/liberar.
- Excluir aparelho.
- Atualização automática do painel enquanto aberto.

## Arquitetura
O vídeo não passa pelo Worker/Supabase da ViraPlay. O backend é usado apenas para cadastro/configuração do aparelho. O streaming vai da fonte configurada diretamente ao aparelho.

## Roku
A API e o fluxo de ativação foram mantidos independentes da interface Android para permitir um cliente Roku futuro sem refazer o painel e o cadastro.
