---
prev: ./
next: ./adb
sidebarDepth: 2
---

# Componentes de Aplicativo

::: details Tabela de Conteúdos
[[toc]]
:::

## O que são os componentes de aplicativo?
Atividades, serviços, receptores de transmissão (também conhecidos como receptores) e provedores de conteúdo (também conhecidos como provedores) são combinadamente chamados de componentes de aplicativo. Mais tecnicamente, todos eles herdam a classe `ComponentInfo`.

## Por que os componentes bloqueados pela AM não são detectados por outros aplicativos relacionados?
É por causa do método de bloqueio que estou usando. Este método é chamado de [Intent Firewall][1] (IFW) e é compatível com [Watt][2] e [Blocker][3]. [MyAndroidTool][4] (MAT) suporta o IFW mas usa um formato diferente. Existem outros métodos para bloquear componentes de aplicativo, como _pm_ e [Shizuku][5]. Se um componente de aplicativo for bloqueado usando esses últimos métodos, o aplicativo afetado pode identificá-lo e desbloqueá-lo, pois tem acesso total aos seus próprios componentes. Muitos aplicativos enganosos realmente exploram isso para manter os componentes rastreadores desbloqueados.

## Os componentes de aplicativo são bloqueados por outras ferramentas retidas no AM?
**Não.** Mas componentes bloqueados pelo Sistema Android ou quaisquer outras ferramentas são exibidos na página [Detalhes do Aplicativo][10] (dentro das abas de componentes). A partir da v2.5.12, você pode importar essas regras nas [Configurações][9]. Mas como não há como distinguir entre componentes bloqueados por aplicativos de terceiros e componentes bloqueados pelo Sistema, você deve ter muito cuidado ao escolher o aplicativo.

## O que acontece com os componentes bloqueados pela AM que também são bloqueados por outras ferramentas?
AM bloqueia novamente os componentes usando o [Intent Firewall][1] (IFW). Eles não são desbloqueados (se bloqueados usando os métodos _pm_ ou [Shizuku][5]) e bloqueados novamente. Mas se você desbloquear um componente na página [Detalhes do Aplicativo][6], ele será revertido de volta ao estado padrão — bloqueado ou desbloqueado como descrito no manifest do aplicativo correspondente — usando ambos os métodos IFW e _pm_. No entanto, componentes bloqueados pelo [MyAndroidTools][4] (MAT) com o método IFW não serão desbloqueados pelo AM. Para resolver este problema, você pode primeiro importar a configuração correspondente para o AM nas [Configurações][9] neste caso, as configurações do MAT serão removidas. Mas esta opção só está disponível a partir da v2.5.12.

## O que é o bloqueio global de componentes?
Quando você bloqueia um componente na página [Detalhes do Aplicativo][6], o bloqueio não é aplicado por padrão. Ele só é aplicado quando você aplica o bloqueio usando a opção _Aplicar regras_ no menu superior direito. Se você ativar o _bloqueio global de componentes_, o bloqueio será aplicado assim que você bloquear um componente. Se você optar por bloquear componentes rastreadores, no entanto, o bloqueio será aplicado automaticamente, independentemente desta configuração. Você também pode remover o bloqueio de um aplicativo simplesmente clicando em _Remover regras_ no mesmo menu na página **Detalhes do Aplicativo**. Uma vez que o comportamento padrão lhe dá mais controle sobre aplicativos, é melhor manter a opção _bloqueio global de componentes_ desativada.

_Veja também: [Bloqueio Global de Componentes][7]_

## Classes de rastreador versus componentes rastreadores
Todos os componentes de aplicativo são classes, mas nem todas as classes são componentes. Na verdade, apenas algumas das classes são componentes. Dito isso, a [página de scanner][scanner] exibe uma lista de rastreadores junto com o número de classes, não apenas os componentes. Em todas as outras páginas, rastreadores e componentes rastreadores são usados sinonimamente para denotar componentes rastreadores, ou seja, bloquear o rastreador significa bloquear componentes rastreadores, não classes rastreadoras.

::: tip Informação
As classes rastreadoras não podem ser bloqueadas. Elas só podem ser removidas editando o próprio aplicativo.
:::

## Como desbloquear os componentes rastreadores bloqueados usando Operações em 1 Clique ou Operações em Lote?
Alguns aplicativos podem se comportar mal devido à sua dependência de componentes rastreadores bloqueados pelo AM. A partir da v2.5.12, há uma opção para desbloquear componentes rastreadores na página [Operações em 1 Clique][8]. No entanto, em versões anteriores, não existem tais opções. Para desbloquear esses componentes do rastreador, primeiro vá para a página [Detalhes do Aplicativo][6] do aplicativo com mau comportamento. Então, mudando para a aba _Atividades_, clique na opção _Remover regras_ no menu superior direito. Todas as regras de bloqueio relacionadas aos componentes do aplicativo serão removidas imediatamente. Alternativamente, se você encontrou o componente que está causando o problema, você pode desbloquear o componente clicando no botão _desbloquear_ ao lado do nome do componente. Se você ativou a opção _bloqueio global de componentes_ nas Configurações, desative-a primeiro pois a opção _Remover regras_ não será visível enquanto esta opção estiver ativada.

Se você tem o **Google Play Services** (`com.google.android.gms`) instalado, desbloquear os seguintes [serviços][services] pode corrigir o problema:
- **Ad Request Broker Service**<br /> `.ads.AdRequestBrokerService`
- **Cache Broker Service**<br /> `.ads.cache.CacheBrokerService`
- **Gservices Value Broker Service**<br /> `.ads.GservicesValueBrokerService`
- **Advertising Id Notification Service**<br /> `.ads.identifier.service.AdvertisingIdNotificationService`
- **Advertising Id Service**<br /> `.ads.identifier.service.AdvertisingIdService`

[1]: https://carteryagemann.com/pages/android-intent-firewall.html
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: https://www.myandroidtools.com
[5]: https://github.com/RikkaApps/Shizuku
[6]: ../guide/app-details-page.md
[7]: ../guide/settings-page.md#bloqueio-global-de-componentes
[8]: ../guide/one-click-ops-page.md
[9]: ../guide/settings-page.md#importar-regras-existentes
[10]: ../guide/app-details-page.md#codigos-de-cores
[services]: ../guide/app-details-page.md#servicos
[scanner]: ../guide/scanner-page.md
