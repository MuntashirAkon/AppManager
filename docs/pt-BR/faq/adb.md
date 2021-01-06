---
prev: ./app-components
next: ./misc
sidebarDepth: 2
---

# ADB sobre TCP
Nesta página, **AsT** denota **ADB sobre TCP** e será usado de forma intercambiável.

::: details Tabela de Conteúdos
[[toc]]
:::

## Tenho que ativar ADB sobre TCP toda vez que reinicio?
Infelizmente, sim. Mas a partir da v2.5.13 você não precisa manter o AsT ativado o tempo todo pois agora um mecanismo de servidor-cliente é usado para interagir com o sistema, mas você tem que manter as **Opções de desenvolvedor** bem como a **Depuração USB** ativadas. Para fazer isso, ative o [ADB sobre TCP][aot] e abra o App Manager. Você deve ver a mensagem _trabalhando em modo ADB_ na parte inferior. Se você vê-la, você pode parar com segurança o servidor. Para o Lineage OS ou algum OS derivado dele, você pode alternar o AsT sem qualquer PC ou Mac, simplesmente alternando a opção **ADB sobre rede** localizada logo abaixo da **Depuração USB**.

## Não é possível ativar a depuração USB. O que fazer?
Veja [ativar depuração USB][aott].

## Posso bloquear o rastreador ou qualquer outro componente de aplicativo usando o ADB sobre TCP?
Infelizmente, não. O ADB tem [permissões][adb_perms] limitadas e controlar os componentes de aplicativos não é uma delas.

## Quais funções podem ser usadas no modo ADB?
A maioria das funções suportadas pelo modo ADB são ativadas por padrão uma vez que o suporte ao ADB é detectado pelo AM. Estas incluem desativar, forçar parada, limpar dados, conceder/revogar operações de aplicativos e permissões. Você também pode instalar aplicativos sem qualquer prompt e ver [aplicativos/processos em execução][running_apps].

[aot]: ../guide/adb-over-tcp.md
[aott]: ../guide/adb-over-tcp.md#_2-ativar-a-depuracao-usb
[adb_perms]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml
[running_apps]: ../guide/main-page.md#aplicativos-em-execucao
