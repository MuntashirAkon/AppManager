---
next: ./app-details-page
prev: ./adb-over-tcp
sidebarDepth: 2
---

# Página Principal

A página principal lista todos os aplicativos instalados (ou uma lista de aplicativos fornecidos por qualquer aplicativo de terceiros) bem como aplicativos que têm backups existentes. Um único clique em qualquer item de aplicativo instalado abre a respectiva página de [Detalhes do Aplicativo][4]. Usando a opção de [ordenar](#ordenar) a partir do menu, os itens de aplicativo podem ser classificados de várias maneiras e preservados de perto. Também é possível filtrar itens com base em suas necessidades usando a opção [filtrar](#filtrar) no menu. Você pode filtrar usando mais de uma opção. Você também pode filtrar aplicativos usando rótulos de aplicativos ou o nome do pacote usando o botão de busca.

::: details Tabela de Conteúdos
[[toc]]
:::

## Operações em Lote
Operações em lote ou operação em vários aplicativos também estão disponíveis nesta página (a maioria dessas operações exigem root ou [ADB sobre TCP][1]). Para ativar o modo de seleção múltipla, clique em qualquer ícone de aplicativo ou aperte e segure em qualquer aplicativo. Depois disso, você pode usar o clique regular para selecionar aplicativos em vez de abrir a página de [Detalhes do Aplicativo][4]. Essas operações incluem:
- Backup do arquivo apk (sem root)
- Backup/restauração/exclusão de dados do aplicativo (apk, dex, dados do aplicativo, etc.)
- Limpar dados de aplicativos
- Desativar/desinstalar/encerrar aplicativos
- Desativar a execução em plano de fundo, anúncios e componentes rastreadores
- Exportar regras de bloqueio

## Códigos de Cores
Aqui está uma lista de cores usadas nesta página e seus significados:
- <code style="background-color: #FCEED1; color: #000">Laranja claro acinzentado (dia)</code> ou <code style="background-color: #091F36; color: #FFF">azul escuro (noite)</code> - Aplicativo selecionado para operação em lote
- <code style="background-color: #FF8A80; color: #000">Vermelho claro (dia)</code> ou <code style="background-color: #4F1C14; color: #FFF">vermelho muito escuro (noite)</code> - Aplicativo desativado
- <code style="background-color: yellow; color: #000">Estrela Amarela</code> - Aplicativo está no modo de depuração
- <code style="color: #E05915">_Data_ Laranja</code> - O aplicativo pode ler logs (permissão concedida)
- <code style="color: #E05915FF">_UID_ Laranja</code> - O ID do usuário está sendo compartilhado entre aplicativos
- <code style="color: #E05915FF">_SDK/Tamanho_ Laranja</code> - Usa texto claro (ou seja. HTTP) tráfego
- <code style="color: red">Vermelho</code> - Aplicativo não permite limpar dados
- <code style="color: #09868BFF">_Nome do pacote_ Ciano escuro</code> - Aplicativo parado ou parado a força
- <code style="color: #09868BFF">_Versão_ Ciano escuro</code> - Aplicativo inativo
- <code style="color: magenta">Magenta</code> - Aplicativo persistente, ou seja, permanece em execução o tempo todo

## Tipos de Aplicativos
Um aplicativo é qualquer **Aplicativo do Usuário** ou **Aplicativo do Sistema** juntamente com os seguintes códigos:
- `X` - O aplicativo suporta várias arquiteturas: 32-bit, 64-bit ou arm-v7, arm-v8, etc.
- `0` - O aplicativo não tem código com ele
- `°` - O aplicativo está em estado suspenso
- `#` - O aplicativo solicitou um grande heap
- `?` - O aplicativo solicitou VM no modo de segurança

## Informações da Versão
O nome da versão é seguido pelos seguintes prefixos:
- `_` - Sem aceleração de hardware
- `~` - Modo apenas teste
- `debug` - Aplicativo depurável

## Menu de Opções
O menu de opções tem várias opções que podem ser usadas para ordenar, filtrar os aplicativos listados, bem como navegar para diferentes páginas.

### Ordenar
Os aplicativos listados na página principal podem ser classificados de diferentes maneiras. A preferência de classificação é preservada, o que significa que os aplicativos serão classificados da mesma forma que foi classificado na inicialização anterior. Independentemente da sua preferência de classificação, no entanto, os aplicativos são primeiro classificados alfabeticamente para evitar resultados aleatórios.
- _Aplicativos do usuário primeiro_ - Os aplicativos do usuário aparecerão primeiro
- _Rótulo de aplicativo_ - Ordenar em ordem crescente com base nos rótulos do aplicativo (também conhecido como nome do aplicativo). Esta é a preferência de ordenação padrão
- _Nome do pacote_ - Ordenar em ordem crescente com base nos nomes de pacotes
- _Última atualização_ - Ordenar em ordem decrescente com base na data de atualização do pacote (ou data de instalação se for um pacote recém-instalado)
- _ID de Usuário compartilhado_ - Ordenar em ordem decrescente com base no id do usuário do kernel
- _Tamanho/sdk do aplicativo_ - Ordenar em ordem crescente com base no sdk do aplicativo (ordenar pelo tamanho do aplicativo não está disponível no momento)
- _Assinatura_ - Ordenar em ordem crescente com base nas informações de assinatura do aplicativo
- _Desativado primeiro_ - Liste primeiro aplicativos desativados
- _Bloqueado primeiro_ - Ordenar em ordem decrescente com base no número de componentes bloqueados

### Filtrar
Os aplicativos listados na página principal podem ser filtrados de várias maneiras. Como na ordenação, as preferências de filtragem também são armazenadas e retidas após um reinício.
- _Aplicativos do usuário_ - Lista apenas aplicativos do usuário
- _Aplicativos do sistema_ - Lista apenas aplicativos do sistema
- _Aplicativos desativados_ - Lista apenas aplicativos desativados
- _Aplicativos com regras_ - Lista apenas aplicativos com regras de bloqueio

Ao contrário da ordenação, você pode filtrar usando mais de uma opção. Por exemplo, você pode listar todos os aplicativos de usuário desativados filtrando a lista de aplicativos usando _Aplicativos do usuário_ e _Aplicativos desativados_. Isso também é útil para operações em lote onde você pode filtrar todos os aplicativos de usuários para realizar determinada operação.

### Operações em 1 Clique
**1-Click Ops** significa **Operações em 1 (um) clique**. Você pode abrir diretamente a página correspondente clicando nesta opção.

_Veja também: [Página de Operações em 1 Clique][5]_

### Uso de Aplicativos
Estatísticas de uso de aplicativos, como _tempo de tela_, _uso de dados_ (tanto móvel quanto Wi-Fi), _número de vezes que um aplicativo foi aberto_ podem ser acessadas clicando na opção **Uso de Aplicativos** no menu (requer a permissão de _Acesso ao Uso_).

### Configuração do Sistema
Exibe várias configurações do sistema e listas brancas/listas negras incluídas no Android por OEM/fornecedor, AOSP ou até mesmo alguns módulos do Magisk.

### Aplicativos em Execução
Uma lista de aplicativos ou processos em execução pode ser visualizada clicando na opção **Aplicativos em Execução** no menu (requer root ou [ADB][1]). Aplicativos ou processos em execução também podem ser fechados à força ou encerrados dentro da página resultante.

### APK Updater
Se você tem o aplicativo [APK Updater][3] instalado, você pode usar a opção correspondente no menu para abrir o aplicativo diretamente. A opção é ocultada se você não tiver ele instalado.

### Termux
Se você tem o [Termux][2] instalado, você pode ir diretamente para a sessão de execução ou abrir uma nova sessão usando a opção **Termux** no menu.

### Configurações
Você pode ir para as [Configurações][settings] do aplicativo clicando na opção correspondente.

[1]: ./adb-over-tcp.md
[2]: https://github.com/termux/termux-app
[3]: https://github.com/rumboalla/apkupdater
[4]: ./app-details-page.md
[5]: ./one-click-ops-page.md
[settings]: ./settings-page.md
