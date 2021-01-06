---
sidebarDepth: 2
---

# Página de Detalhes do Aplicativo
A página de **Detalhes do Aplicativo** consiste em 11 (onze) abas. Ela basicamente descreve quase todos os bits de informações que um aplicativo pode ter, incluindo todos os atributos do manifest do aplicativo, permissões, [operações de aplicativo][1], informações de assinatura, etc.

::: details Tabela de Conteúdos
[[toc]]
:::

## Códigos de Cores
Lista de cores de fundo usadas nesta página e seu significado:
- <code style="background-color: #FF0000; color: #000">Vermelho (dia)</code> ou <code style="background-color: #790D0D; color: #FFF">vermelho escuro (noite)</code> - Qualquer operação de aplicativo ou permissão que tenha a bandeira perigosa está marcada como vermelho. Os componentes que são bloqueados dentro do App Manager também são marcados como vermelho
- <code style="background-color: #FF8A80; color: #000">Vermelho claro (dia)</code> ou <code style="background-color: #4F1C14; color: #FFF">vermelho muito escuro (noite)</code> - Os componentes que são desativados fora do App Manager têm essas cores. Deve-se notar que um componente marcado como desativado nem sempre significa que ele é desativado pelo usuário: Ele pode ser desativado pelo sistema, bem como marcado como desativado no manifest do aplicativo. Além disso, todos os componentes de um aplicativo desativado são considerados desativados pelo sistema (e pelo App Manager também)
- <code style="background-color: #FF8017; color: #000">Laranja vívido (dia)</code> ou <code style="background-color: #FF8017; color: #FFF">laranja muito escuro (noite)</code> - Componentes de anúncios ou rastreadores
- <code style="background-color: #EA80FC; color: #000">Magenta suave (dia)</code> ou <code style="background-color: #431C5D; color: #FFF">violeta muito escuro (noite)</code> - Atualmente executando serviços

## Aba de Informações do Aplicativo
A aba de **Informações do Aplicativo** contém informações gerais sobre um aplicativo. Ela também lista muitas ações que podem ser realizadas dentro desta aba. Uma descrição completa é dada abaixo:

### Informações Gerais
A lista abaixo está na mesma ordem listada na aba de Informações do Aplicativo.
- **Ícone do Aplicativo.** O ícone do aplicativo, se um aplicativo não tiver um ícone, o ícone padrão do sistema será exibido.
- **Rótulo do Aplicativo.** O rótulo do aplicativo ou o nome do aplicativo.
- **Versão.** A versão do aplicativo é dividida em duas partes. A primeira parte é chamada de _nome da versão_, o formato desta parte varia, mas muitas vezes consiste em vários números inteiros separados por pontos. A segunda parte é chamada de _código da versão_ e é fechado entre parênteses. O código da versão é um número inteiro que geralmente é usado para diferenciar entre versões de aplicativos (como o nome da versão pode muitas vezes ser ilegível por uma máquina). Em geral, a nova versão de um aplicativo tem código de versão mais alto do que os antigos. Por exemplo, se`123` e `125` são dois códigos da versão de um aplicativo, podemos dizer que este último é mais atualizado do que o primeiro porque o código de versão deste último é maior. Para aplicativos que dependem de plataformas (celulares, tablets, computadores, etc.), esses números da versão podem ser enganosos, pois usam prefixos para cada plataforma.
- **Tags.** (Também conhecidas como tag clouds) Tags incluem as informações básicas, concisas e mais úteis de um aplicativo. As tags contêm _informações de rastreadores_ (isto é, número de componentes rastreadores), _tipo de aplicativo_ (aplicativo do usuário ou aplicativo do sistema e se o aplicativo é uma versão atualizada do aplicativo do sistema ou se o aplicativo está instalado sem sistema usando Magisk), _Executando_ (isto é, um ou mais serviços do aplicativo está sendo executado em segundo plano), _informações do apk dividido_ (isto é, número de divisões), _depurável_ (o aplicativo é uma versão de depuração), _apenas teste_ (o aplicativo é um aplicativo apenas para testes), _grande heap_ (o aplicativo solicitou um grande tamanho de heap), _parado_ (o aplicativo forçou uma parada), _desativado_ (o aplicativo está desativado), _KeyStore_ (o aplicativo tem itens no Android KeyStore) e _nenhum código_ (o aplicativo não tem nenhum código associado a ele). A importância de incluir _apenas teste_ e _depurável_ é que o aplicativo com essas propriedades pode fazer tarefas adicionais ou esses aplicativos podem ser `executados como` sem root, o que pode causar problemas potenciais de segurança se esses aplicativos armazenarem qualquer informação privada. _grande heap_ denota que o aplicativo vai alocar uma maior quantidade de memória (RAM) se necessário. Embora isso possa não ser prejudicial para a maioria dos casos, quaisquer aplicativos suspeitos solicitando um grande heap devem ser levados a sério.
- **Painel de Ação Horizontal.** Este é um painel de ação contendo várias ações em relação ao aplicativo. Veja [abaixo](#painel-de-acao-horizontal) para uma lista completa das ações disponíveis lá.
- **Caminhos e Diretórios.** Contém várias informações sobre os caminhos de aplicativos, incluindo _o diretório de aplicativos_ (onde os arquivos apk são armazenados), _os diretórios de dados_ (internos, protegidos pelo dispositivo e externos), _os diretórios de apks divididos_ (juntamente com os nomes das divisões), e _a biblioteca JNI nativa_ (se presente). As bibliotecas JNI são usadas para invocar códigos nativos geralmente escritos em C/C++. O uso da biblioteca nativa pode fazer com que o aplicativo seja executado mais rápido ou ajudar um aplicativo a usar bibliotecas de terceiros escritas usando idiomas diferentes do Java, como na maioria dos jogos. Você também pode abrir esses diretórios usando seus gerenciadores de arquivos favoritos (desde que eles o apoiem e tenham permissões necessárias) clicando no ícone de iniciar no lado direito de cada item.
- **Uso de Dados Desde a Última Inicialização.** Uma opção autoexplicativa. Mas cuidado que, devido a alguns problemas, os resultados podem muitas vezes ser enganosos e simplesmente errados. Esta parte permanece escondida se a permissão de _Acesso ao Uso_ não é concedida em dispositivos mais novos.
- **Armazenamento e Cache.** Exibe informações sobre o tamanho do aplicativo (arquivos apk), dados e cache. Em dispositivos mais antigos, o tamanho das pastas de dados externos, cache, mídia e obb também são exibidos. Esta parte permanece escondida se a permissão de _Acesso ao Uso_ não é concedida em dispositivos mais novos.
- **Mais Informações.** Exibe outras informações, como
  * **SDK.** Exibe informações relacionadas ao Android SDK. Existem dois valores (um em dispositivos antigos): _Max_ denota o SDK alvo e _Min_ denota o SDK mínimo (este último não está disponível em dispositivos antigos). É melhor usar aplicativos com o SDK máximo que a plataforma suporta atualmente. O SDK também é conhecido como **Nível da API**. _Veja também: [Histórico das Versões do Android][wiki_android_versions]_
  * **Flags.** As flags do aplicativo usadas no momento da construção do aplicativo. Para uma lista completa de flags e o que elas fazem, visite a [documentação oficial][app_flags].
  * **Data de Instalação.** A data em que o aplicativo foi instalado pela primeira vez.
  * **Data de Atualização.** A data em que o aplicativo foi atualizado pela última vez. Isso é o mesmo que _Data de Instalação_ se o aplicativo não foi atualizado.
  * **Aplicativo Instalador.** O aplicativo que instalou o aplicativo. Nem todos os aplicativos fornecem as informações usadas pelo gerenciador de pacotes para registrar o aplicativo instalador. Portanto, esse valor não deve ser dado como certo.
  * **ID do Usuário.** O ID exclusivo do usuário definido pelo sistema Android para o aplicativo. Para aplicativos compartilhados, o mesmo ID do usuário é atribuído a vários aplicativos que têm o mesmo _ID de Usuário Compartilhado_.
  * **ID de Usuário Compartilhado.** Aplicável para aplicações que são compartilhadas em conjunto. Embora diga ID, este é realmente um valor de string. O aplicativo compartilhado deve ter as mesmas [assinaturas](#aba-de-assinaturas).
  * **Atividade Principal.** O principal ponto de entrada para o aplicativo. Isso só é visível se o aplicativo tiver [atividades](#atividades) e qualquer uma delas pode ser aberta a partir do Launcher. Há também o botão de iniciar no lado direito que pode ser usado para iniciar esta atividade.

### Painel de Ação Horizontal
O Painel de Ação Horizontal, conforme descrito na seção anterior, consiste em várias ações relacionadas a aplicativos, tais como —
- **Iniciar.** Um aplicativo que tem uma [atividade](#atividades) iniciável pode ser iniciado usando este botão.
- **Desativar.** Desativar um aplicativo. Este botão não é exibido para aplicativos já desativados ou para usuários que não têm root ou [ADB][2]. Se você desativar um aplicativo, o aplicativo não será exibido no seu Launcher. Atalhos para o aplicativo também serão removidos. Se você desativar um aplicativo de usuário, você só poderá ativá-lo via App Manager ou qualquer outra ferramenta que suporte isso. Não há nenhuma opção nas Configurações do Android para ativar um aplicativo de usuário desativado.
- **Desinstalar.** Desinstale um aplicativo.
- **Ativar.** Ative um aplicativo. Este botão não é exibido para aplicativos já ativados ou para usuários que não têm root ou [ADB][2].
- **Forçar Parada.** Force um aplicativo a parar. Quando você forçar um aplicativo a parar, o aplicativo não será capaz de ser executado em segundo plano a menos que você explicitamente abra primeiro. No entanto, isso nem sempre é verdade.
- **Limpar Dados.** Limpe os dados de um aplicativo. Isso inclui qualquer informação armazenada nos diretórios internos e muitas vezes externos, incluindo contas (se definidas pelo aplicativo), cache, etc. Limpar os dados do App Manager, por exemplo, remove todas as regras (o bloqueio não é removido) salvos dentro do aplicativo. É por isso que você deve sempre fazer backups de suas regras. Este botão não é exibido para usuários que não têm root ou [ADB][2].
- **Limpar Cache.** Limpe apenas o cache de aplicativos. Não há nenhuma maneira do Android limpar o cache do aplicativo. Portanto, ele precisa de permissão root para limpar o cache do armazenamento interno do aplicativo.
- **Instalar.** Instale um apk aberto usando qualquer aplicativo de terceiros. Este botão é exibido apenas para um apk externo que não foi instalado.
- **Novidades.** Este botão é exibido para um apk que tem código de versão mais alto do que o instalado. Clicar neste botão exibe uma diálogo que consiste em diferenças de uma maneira de controle de versão. As informações que ele exibe incluem _versão_, _rastreadores_, _permissões_, _componentes_, _assinaturas_ (mudanças no checksum), _funções_, _bibliotecas compartilhadas_ e _sdk_.
- **Atualizar.** Exibido para um aplicativo que tem um código de versão mais alto do que o aplicativo instalado.
- **Reinstalar.** Exibido para um aplicativo que tem o mesmo código de versão do aplicativo instalado.
- **Downgrade.** Exibido para um aplicativo que tem um código de versão mais baixo do que o aplicativo instalado.
- **Manifest.** Clicar neste botão exibe o arquivo de manifest do aplicativo em uma página separada. Você pode ativar/desativar a quebra de palavras no arquivo de manifest usando o botão de alternar correspondente (no lado superior direito) ou pode salvá-lo em seu armazenamento compartilhado usando o botão de salvar.
- **Scanner.** Clicar neste botão exibe as informações do rastreador e da biblioteca do aplicativo. No início, ele verifica o aplicativo para extrair uma lista de classes. Em seguida, a lista de classe é combinada com uma série de assinaturas. Depois disso, um resumo de varredura é exibido. _Veja também: [Página de Scanner][scanner]_
- **Preferências Compartilhadas.** Clicar neste botão exibe uma lista de preferências compartilhadas usadas pelo aplicativo. Clicar em um item de preferência na lista abre a [Página de Edição de Preferências Compartilhadas][3]. Esta opção só é visível para os usuários com root.
- **Bancos de Dados.** Clicar neste botão exibe uma lista de bancos de dados usados pelo aplicativo. Isso precisa de mais melhorias e um editor de banco de dados que pode ser adicionado no futuro. Esta opção só é visível para os usuários com root.
- **Aurora.** Abre o aplicativo no _Aurora Droid_. A opção só é visível se o _Aurora Droid_ estiver instalado.
- **F-Droid.** Abre o aplicativo no _F-Droid_. Esta opção só é visível se o _F-Droid_ estiver instalado e o _Aurora Droid_ não estiver instalado.
- **Loja.** Abre o aplicativo no _Aurora Store_. A opção só é visível se o _Aurora Store_ estiver instalado.

### Menu de Opções
O menu de opções está localizado no canto superior direito da página. Uma descrição completa das opções presentes são dadas abaixo:
- **Compartilhar.** O botão compartilhar pode ser usado para compartilhar o arquivo apk ou o arquivo _apks_ (se o aplicativo tiver várias divisões) pode ser importado para o [SAI][sai]. Você pode compartilhá-lo com seu gerenciador de arquivos favorito para salvar o arquivo em seu armazenamento compartilhado.
- **Atualizar.** Atualiza a aba de Informações do Aplicativo.
- **Ver nas Configurações.** Abre o aplicativo nas Configurações do Android.
- **Backup/Restauração.** Abre o diálogo de backup/restauração.
- **Exportar Regras de Bloqueio.** Exporta as regras configuradas para este aplicativo dentro do App Manager.
- **Abrir no Termux.** Abre o aplicativo no Termux. Na verdade, isto executa `su - user_id` onde `user_id` denota o ID de usuário do kernel do aplicativo (descrito na [Seção de Informações Gerais](#informacoes-gerais)). Esta opção só é visível para os usuários com root. Veja abaixo a seção [Termux](#termux) para aprender como configurar o Termux para executar comandos a partir de aplicativos de terceiros.
- **Executar no Termux.** Abre o aplicativo usando `run-as package_name` no Termux. Isso só é aplicável para um aplicativo depurável e funciona para usuários sem root também. Veja abaixo a seção [Termux](#termux) para aprender como configurar o Termux para executar comandos a partir de aplicativos de terceiros.
- **Extrair Ícone.** Extraia e salve o ícone do aplicativo na localização desejada.

### Termux
Por padrão, o Termux não permite executar comandos de aplicativos de terceiros. Para ativar essa opção, você tem que adicionar `allow-external-apps=true` em <tt>~/.termux/termux.properties</tt> e certifique-se de que você está executando Termux v0.96 ou posterior.

::: tip Informação
Ativar essa opção não enfraquece a segurança do seu Termux. Os aplicativos de terceiros ainda precisam solicitar ao usuário para permitir a execução de comandos arbitrários no Termux como qualquer outra permissão perigosa.
:::

## Abas de Componentes
**Atividades**, **Serviços**, **Receptores** (originalmente _receptores de transmissão_) e **Provedores** (originalmente _Provedores de Conteúdo_) são juntos chamados de componentes do aplicativo. Isso porque eles compartilham características semelhantes em muitos aspectos. Por exemplo, todos eles têm um _nome_ e um _rótulo_. Os componentes do aplicativo são os blocos de construção de qualquer aplicativo, e a maioria deles tem que ser declarado no manifest do aplicativo. O manifest do aplicativo é um arquivo onde metadados específicos do aplicativo são armazenados. O sistema operacional Android aprende o que fazer com um aplicativo lendo os metadados. As [cores](#codigos-de-cores) usadas nestas abas são explicadas acima.

::: details Tabela de Conteúdos
- [Atividades](#atividades)
- [Serviços](#servicos)
- [Receptores](#receivers)
- [Provedores](#providers)
- [Funções Adicionais para Telefones Rooteados](#additional-features-for-rooted-phones)
:::

### Atividades
As **Atividades** são janelas ou páginas que você pode navegar (por exemplo _Página principal_ e _Página de Detalhes do Aplicativo_ são duas atividades separadas). Em outras palavras, uma atividade é um componente da interface de usuário (UI). Cada atividade pode ter vários componentes de interface de usuário conhecidos como _widgets_ ou _fragmentos_, e da mesma forma, cada um desses últimos componentes pode ter vários deles aninhados ou em cima um do outro. Mas uma atividade é um componente _mestre_: Não pode haver duas atividades aninhadas. Um autor de aplicativo também pode optar por abrir arquivos externos dentro de uma atividade usando um método chamado _filtros de intenção_. Quando você tenta abrir um arquivo usando seu gerenciador de arquivos, o gerenciador de arquivos ou o sistema verifica se os filtros de intenção decidem quais atividades podem abrir esse arquivo em particular e oferece que você abra o arquivo com essas atividades (portanto, não tem nada a ver com o aplicativo em si). Há outros filtros de intenção também.

Atividades que são _exportáveis_ geralmente podem ser abertas por quaisquer aplicativos de terceiros (algumas atividades exigem permissões, se esse for o caso, apenas um aplicativo com essas permissões pode abri-las). Na aba de _Atividades_, o nome da atividade (no topo de cada item da lista) é na verdade um botão. Isso é ativado para as atividades _exportáveis_ e desativado para as outras. Você pode usá-lo para abrir a atividade diretamente usando o App Manager.

::: warning Aviso
Se você não é capaz de abrir qualquer atividade, as chances são de que ele tenha certas dependências que não se encontram, por exemplo, você não pode abrir a _Atividade de Detalhes do Aplicativo_ porque requer que você pelo menos forneça um nome de pacote. Essas dependências nem sempre podem ser inferidas programáticamente. Portanto, você não pode abri-las usando o App Manager.
:::

Você também pode criar um atalho para estas atividades _exportáveis_ (usando o botão dedicado), e se você quiser, você pode editar o atalho também usando o botão _Editar Atalho_.

::: danger Atenção
Se você desinstalar o App Manager, os atalhos criados pelo App Manager serão perdidos.
:::

### Serviços
Ao contrário das [atividades](#atividades) que os usuários podem ver, os **Serviços** lidam com tarefas em segundo plano. Se você está, por exemplo, baixando um vídeo da internet usando o navegador de Internet do seu telefone, o navegador de Internet está usando um serviço em segundo plano para baixar o conteúdo.

Quando você fecha uma atividade, ela geralmente é destruída imediatamente (dependendo de muitos fatores, como quanta memória RAM livre seu telefone tem). Mas os serviços podem ser executados por períodos indeterminados, se desejar. Se mais serviços estiverem sendo executados em segundo plano, o telefone ficará mais lento devido à falta de memória RAM e/ou poder de processamento, e a bateria do seu telefone pode ser drenada mais rapidamente. As versões mais recentes do Android têm uma função de otimização de bateria ativada por padrão para todos os aplicativos. Com esta função ativada, o sistema pode encerrar aleatoriamente qualquer serviço.

A propósito, tanto as atividades quanto os serviços são executados no mesmo looper chamado de [looper][looper] principal, o que significa que os serviços não são realmente executados em segundo plano. É o trabalho dos autores de aplicativos garantir isso. Como a aplicação se comunica com os serviços? Eles usam [receptores de transmissão](#receptores).

### Receptores
**Receptores** (também chamados de _receptores de transmissão_) podem ser usados para desencadear a execução de certas tarefas para certos eventos. Esses componentes são chamados de receptores de transmissão porque são executados assim que uma mensagem de transmissão é recebida. Essas mensagens de transmissão são enviadas usando um método chamado intenção. A intenção é uma função especial para Android que pode ser usada para abrir aplicativos, atividades, serviços e enviar mensagens de transmissão. Portanto, como as [atividades](#atividades), os receptores de transmissão usam filtros de intenção para receber apenas a(s) mensagem(ns) de transmissão desejada(s). Mensagens de transmissão podem ser enviadas pelo sistema ou pelo próprio aplicativo. Quando uma mensagem de transmissão é enviada, os receptores correspondentes são despertados pelo sistema para que eles possam executar tarefas. Por exemplo, se você tiver pouca memória RAM, seu telefone pode congelar ou experimentar travamentos por um momento depois de ativar os dados móveis ou se conectar ao Wi-Fi. Já se perguntou por quê? Isso ocorre porque os receptores de transmissão que podem receber `android.net.conn.CONNECTIVITY_CHANGE` são despertados pelo sistema assim que você ativa a conexão de dados. Como muitos aplicativos usam esse filtro de intenção, todos esses aplicativos são despertados quase imediatamente pelo sistema, o que causa o congelamento ou travamentos. Dito isto, os receptores podem ser usados para comunicação entre processos (IPC), ou seja, ajuda você a se comunicar entre diferentes aplicativos (desde que você tenha as permissões necessárias) ou mesmo componentes diferentes de um único aplicativo.

### Provedores
**Provedores** (também chamados de _provedores de conteúdo_) são usados para o gerenciamento de dados. Por exemplo, quando você salva um arquivo apk ou regras de exportação no App Manager, ele usa um provedor de conteúdo chamado `androidx.core.content.FileProvider` para salvar o apk ou exportar as regras. Existem outros provedores de conteúdo ou mesmo personalizados para gerenciar várias tarefas relacionadas ao conteúdo, como gerenciamento de banco de dados, rastreamento, busca, etc. Cada provedor de conteúdo tem um campo chamado _Autoridade_ que é exclusivo desse aplicativo em particular em todo o ecossistema Android, assim como o nome do pacote.

### Funções Adicionais para Telefones Rooteados
Ao contrário dos usuários sem root que são apenas espectadores nessas abas, os usuários com root podem executar várias operações. No lado direito de cada item componente, há um ícone de "bloqueio" (que se torna um ícone de "desbloquear/restaurar" quando o componente está sendo bloqueado). Este ícone pode ser usado para alternar o status de bloqueio desse componente em particular. Se você não tem o [Bloqueio Global de Componentes][settings_gcb] ativado ou não aplicou um bloqueio para o aplicativo antes, você tem que aplicar as alterações usando a opção **Aplicar regras** no menu superior direito. Você também pode remover regras já aplicadas usando a mesma opção (que seria lida como **Remover regras** desta vez). Você também tem a capacidade de classificar a lista de componentes para exibir componentes bloqueados ou rastreadores no topo da lista usando a opção **Ordenar** no mesmo menu. Você também pode desativar todos os componentes de anúncios e rastreadores usando a opção **Bloquear rastreadores** no menu.

_Veja também:_
- _[Página de Scanner][scanner]_
- _[FAQ: Componentes de Aplicativo][faq_ac]_

## Abas de Permissão
**Operações de Aplicativo**, **Permissões Utilizadas** e abas de **Permissões** estão relacionadas com permissões. Na comunicação do Android entre aplicativos ou processos que não têm a mesma identidade (conhecida como _id compartilhado_) muitas vezes requerem permissão(ões). Essas permissões são gerenciadas pelo controlador de permissão. Algumas permissões são consideradas permissões _normais_ que são concedidas automaticamente se elas aparecem no manifest do aplicativo, mas permissões _perigosas_ e de _desenvolvimento_ exigem confirmação do usuário. As [cores](#codigos-de-cores) usadas nestas abas são explicadas acima.

::: details Tabela de Conteúdos
- [Operações de Aplicativo](#operacoes-de-aplicativo)
- [Permissões Utilizadas](#permissoes-utilizadas)
- [Permissões](#permissoes)
:::

### Operações de Aplicativo
**App Ops** significa **Operações de Aplicativo**. Desde o Android 4.3, _Opções de Aplicativo_ são usadas pelo sistema Android para controlar a maioria das permissões do aplicativo. Cada operação de aplicativo tem um número único associado a elas que estão fechados dentro dos primeiros suportes na aba de Operações de Aplicativo. Eles também têm um nome privado e, opcionalmente um público. Algumas operações de aplicativo estão associadas a _permissões_ também. A periculosidade de uma operação de aplicativo é decidida com base na permissão associada, e outras informações, como _flags_, _nome da permissão_, _descrição da permissão_, _nome do pacote_, _grupo_ são retiradas da [permissão](#permissoes) associada. Outras informações podem incluir o seguinte:
- **Modo.** Ele descreve o status de autorização atual que pode ser _allow_ (permitir), _deny_ (negar), (um termo bastante equivocado, significa simplesmente erro), _ignore_ (ignorar), (isso realmente significa negar), _default_ (padrão), (inferido a partir de uma lista de padrões definidos internamente pelo fornecedor), _foreground_ (primeiro plano), (em Androids mais novos, isso significa que a operação de aplicativo só pode ser usada quando o aplicativo está sendo executado em primeiro plano), e alguns modos personalizados definidos pelos fornecedores (MIUI usa _ask_ (perguntar), e outros modos com apenas números sem nomes associados).
- **Duração.** A quantidade de tempo que esta operação de aplicativo foi usada (pode haver durações negativas cujos casos de uso não são conhecidos atualmente por mim).
- **Hora de Aceitação.** A última vez que a operação de aplicativo foi aceita.
- **Hora de Rejeição.** A última vez que a operação de aplicativo foi rejeitada.

::: tip Informação
O conteúdo desta aba só é visível para usuários de root e [ADB][2].
:::

Há um botão de alternar ao lado de cada item operação de aplicativo que pode ser usado para permitir ou negar (ignorar) a operação de aplicativo. Você também pode redefinir suas alterações usando a opção _Restaurar ao padrão_ ou negar todas as operações de aplicativo perigosas usando a opção correspondente no menu. Você também pode classificá-las em ordem crescente por nomes de operações de aplicativo e os números únicos (ou valores) associados. Você também pode listar as operações de aplicativo negadas primeiro usando a opção de classificação correspondente.

::: warning Negar
uma operação de aplicativo pode fazer com que o aplicativo se comporte mal. Use a opção _restaurar ao padrão_ se esse for o caso.
:::

_Veja também: [Informações Técnicas: Operações de Aplicativo][1]_

### Permissões Utilizadas
**Permissões Utilizadas** são as permissões usadas pelo aplicativo. Isso é nomeado assim porque elas são declaradas no manifest usando as tags `uses-permission`. Informações como _flags_, _nome da permissão_, _descrição da permissão_, _nome do pacote_, _grupo_ são retiradas da [permissão](#permissoes) associada.

**Usuários de Root e [ADB][2] podem conceder ou revogar as permissões _perigosas_ e de _desenvolvimento_ usando o botão alternar no lado direito de cada item de permissão. Eles também podem revogar permissões perigosas de uma só vez usando a opção correspondente no menu. Apenas esses dois tipos de permissões podem ser revogadas porque o Android não permite modificar permissões _normais_ (que são a maioria delas). A única alternativa é editar o manifest do aplicativo e remover essas permissões de lá.</p>

::: tip Informação
Uma vez que as permissões perigosas são revogadas por padrão pelo sistema, revogar todas as permissões perigosas é o mesmo que redefinir todas as permissões.
:::

::: tip Aviso
As permissões não podem ser alteradas para aplicativos voltados para a API 23 ou anterior. Portanto, os alternadores de permissão são desativados para esses aplicativos.
:::

Os usuários podem ordenar as permissões pelo nome de permissão (em ordem crescente) ou optar por exibir permissões negadas ou perigosas primeiro usando as opções correspondentes no menu.

### Permissões
**Permissões** geralmente são permissões personalizadas definidas pelo próprio aplicativo. Pode conter permissões regulares também, principalmente em aplicativos antigos. Aqui está uma descrição completa de cada item que é exibido lá:
- **Nome.** Cada permissão tem um nome único como `android.permission.INTERNET` mas vários aplicativos podem solicitar a permissão.
- **Ícone.** Cada permissão pode ter um ícone personalizado. As outras abas de permissão não possuem nenhum ícone porque não contêm nenhum ícone no manifest do aplicativo.
- **Descrição.** Este campo opcional descreve a permissão. Se não houver qualquer descrição associada à permissão, o campo não será exibido.
- **Flags.** (Usa o símbolo ⚑ ou o nome **Nível de Proteção**) Isso descreve várias flags de permissão, como _normais_, _desenvolvimento_, _perigosas_, _instantânea_, _concedida_, _revogada_, _assinatura_, _privilegiada_, etc.
- **Nome do Pacote.** Denota o nome do pacote associado à permissão, ou seja, o pacote que definiu a permissão.
- **Grupo.** O nome do grupo associado à permissão (se houver). Androids mais novos não parecem usar nomes de grupo, é por isso que você geralmente vai ver `android.permission-group.UNDEFINED` ou nenhum nome de grupo.

## Aba de Assinaturas
As **Assinaturas** são realmente chamadas de informações de assinatura. Um aplicativo é assinado por um ou mais certificados de assinatura pelos desenvolvedores do aplicativo antes de publicá-lo. A integridade de um aplicativo (se o aplicativo é do desenvolvedor real e não é modificado por outras pessoas) pode ser verificada usando as informações de assinatura; porque quando um aplicativo é modificado por uma pessoa não autorizada de terceiros, o aplicativo não pode ser assinado usando o certificado original novamente porque as informações de assinatura são mantidas privadas pelo desenvolvedor real. _Como você verifica essas assinaturas?_ Usando checksums. Checksums são gerados a partir dos próprios certificados. Se o desenvolvedor fornece os checksums, você pode igualar os checksums usando os diferentes checksums gerados na aba de **Assinaturas**. Por exemplo, se você baixou o App Manager pelo Github, Canal no Telegram ou IzzyOnDroid repo, você pode verificar se o aplicativo é realmente lançado por mim, simplesmente combinando o seguinte checksum _sha256_ com o exibido nesta aba:
```
320c0c0fe8cef873f2b554cb88c837f1512589dcced50c5b25c43c04596760ab
```

Existem três tipos de checksums exibidos lá: _md5_, _sha1_ e _sha256_.

::: danger Atenção
Recomenda-se que você verifique informações de assinatura usando apenas checksums _sha256_ ou todas as três delas. NÃO confie somente nos checksums _md5_ ou _sha1_ pois eles são conhecidos por gerar os mesmos resultados para vários certificados.
:::

## Outras Abas
Outras abas listam componentes do manifest do Android, como funções, configurações, bibliotecas compartilhadas e assinaturas. Uma descrição completa sobre essas abas estará disponível em breve.

[1]: ../tech/AppOps.md
[2]: ./adb-over-tcp.md
[3]: ./shared-pref-editor-page.md
[looper]: https://stackoverflow.com/questions/7597742
[settings_gcb]: ./settings.md#bloqueio-global-de-componentes
[faq_ac]: ../faq/app-components.md
[app_flags]: https://developer.android.com/reference/android/content/pm/ApplicationInfo#flags
[wiki_android_versions]: https://en.wikipedia.org/wiki/Android_version_history#Overview
[scanner]: ./scanner-page.md
[sai]: https://github.com/Aefyr/SAI
