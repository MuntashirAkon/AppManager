---
next: false
sidebarDepth: 2
---

# Miscelânea

::: details Tabela de Conteúdos
[[toc]]
:::

## Algum plano para Shizuku?

**Atualização:** Em 12 de Setembro de 2020, Os autores de Shizuku finalmente adicionaram uma [licença][shizuku_license] (Licença Apache 2.0) com as seguintes exceções à licença:
> 1. Você está PROIBIDO de usar arquivos de imagem listados abaixo de qualquer forma. `
  manager/src/main/res/mipmap-hdpi/ic_launcher.png
  manager/src/main/res/mipmap-hdpi/ic_launcher_background.png
  manager/src/main/res/mipmap-hdpi/ic_launcher_foreground.png
  manager/src/main/res/mipmap-xhdpi/ic_launcher.png
  manager/src/main/res/mipmap-xhdpi/ic_launcher_background.png
  manager/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png
  manager/src/main/res/mipmap-xxhdpi/ic_launcher.png
  manager/src/main/res/mipmap-xxhdpi/ic_launcher_background.png
  manager/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png
  manager/src/main/res/mipmap-xxxhdpi/ic_launcher.png
  manager/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png
  manager/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`
> 2. Você está PROIBIDO de distribuir o apk compilado por você (incluindo modificado, por exemplo, renomear "Shizuku" para outra coisa) para qualquer loja (incluindo, mas não limitado à Google Play Store, etc.).

Saúdo absolutamente esta mudança (embora até onde eu sei apache 2.0 não suporta exceções), mas eu não concordo com as exceções e, portanto, nenhum suporte para Shizuku será adicionado. Mas isso me dá a capacidade de incorporar funções semelhantes ao AM, pois copiar funções do Shizuku cumpre com a licença.

**Textos Originais:**

Seria definitivamente bom se eu adicionasse suporte ao Shizuku. Mas o problema não é comigo ou com meu aplicativo, são eles.

Embora [Shizuku][shizuku] seja um aplicativo de código aberto, ele não usa nenhuma licença. Portanto, é um software não-livre (non-libre). Inicialmente pensando nele como um descuido, um número de pessoas tinham [sugerido aos autores][shizuku_56] para adicionar uma licença para seu aplicativo pois os scanners F-Droid não construiriam uma biblioteca sem uma licença gratuita. Em resposta, os autores separaram a biblioteca da API responsável por acessar Shizuku com uma licença gratuita (libre) e encerraram o problema. Isto Implica implicitamente que <mark>os autores não têm intenção de tornar o aplicativo gratuito,</mark> e, portanto, sendo um forte defensor da liberdade, não posso adicionar um suporte ao Shizuku.

Como declarado em [choosealicense.com][cal]:
> Se você encontrar um software que não tenha uma licença, isso geralmente significa que você não tem permissão dos criadores do software para usar, modificar ou compartilhar o software. Embora um host de código como o GitHub possa permitir que você visualize e bifurque (fork) o código, isso não implica que você tenha permissão para usar, modificar ou compartilhar o software para qualquer finalidade.
> 
> Suas opções:
> - **Peça gentilmente aos responsáveis para adicionar uma licença.** A menos que o software inclua fortes indicações para o contrário, a falta de licença é provavelmente um descuido. Se o software estiver hospedado em um site como o GitHub, abra um problema (issue) solicitando uma licença e inclua um link para este site. Se você é ousado e é bastante óbvio qual licença é mais apropriada, abra um pedido (pull request) para adicionar uma licença – consulte "sugerir esta licença" na barra lateral da página para cada licença neste site (por exemplo, MIT).
> - **Não use o software.** Encontre ou crie uma alternativa que esteja sob uma licença de código aberto.
> - **Negocie uma licença privada.** Traga seu advogado.

Eu tenho planos de trabalhar mais em ADB e APIs privadas para tornar as coisas um pouco mais rápidas.

::: tip Em Suma
Devido à natureza não-livre do Shizuku, não posso adicionar um suporte ao Shizuku.
:::

_Veja também:_
- _[Discussão relacionada][shizuku_discussion]_
- _[O que é um software livre?][free_sw]_

## O que são bloatwares e como removê-los?
Bloatwares são os aplicativos desnecessários fornecidos pelo fornecedor ou OEM e geralmente são aplicativos do sistema. Esses aplicativos são frequentemente usados para rastrear usuários e coletar dados de usuários que eles podem vender para obter lucros. Os aplicativos do sistema não precisam solicitar qualquer permissão para acessar informações do dispositivo, contatos e dados de mensagens, e outras informações de uso, como o hábito de uso do telefone e tudo o que você armazena em seu(s) armazenamento(s) compartilhado(s). Esses bloatwares também podem incluir aplicativos do Google (como Google Play Services, Google Play Store, Gmail, Google, Mensagens, Discador, Contatos), aplicativos do Facebook (o aplicativo do Facebook consiste em quatro ou cinco aplicativos), Facebook Messenger, Instagram, Twitter e muitos outros aplicativos que também podem rastrear usuários e/ou coletar dados do usuário sem consentimento, dado que todos eles são aplicativos do sistema. Você pode desativar apenas algumas permissões usando as configurações do Android, mas esteja ciente de que as Configurações do Android esconde quase todas as permissões que qualquer especialista em segurança chamaria de potencialmente _perigosa_. Se os bloatwares fossem aplicativos do usuário, você poderia facilmente desinstalá-los a partir das Configurações do Android ou AM. Mas desinstalar aplicativos do sistema não é possível sem a permissão de superusuário. Você também pode desinstalar aplicativos usando ADB, mas pode não funcionar para todos os aplicativos. Am pode desinstalar aplicativos do sistema com root ou ADB (este último com certas limitações, é claro). Mas esses métodos não podem _remover_ os aplicativos do sistema completamente pois eles estão localizados na partição do _sistema_ que é uma partição de somente leitura. Se você tem root, você pode remontar esta partição para manualmente _purgar_ esses aplicativos, mas isso vai quebrar as atualizações do Ar (OTA) desde que os dados na partição do sistema foram modificados. Existem dois tipos de atualizações, delta (de tamanho curto, que consiste apenas em mudanças) e atualizações completas. Você ainda pode aplicar atualizações completas, mas os bloatwarez serão instalados novamente e, consequentemente, você terá que excluí-los. Além disso, nem todos os fornecedores fornecem atualizações completas. Outra solução é desativar esses aplicativos a partir das Configurações do Android (sem root) ou AM. Mas alguns serviços ainda podem ser executados em segundo plano, pois podem ser iniciados por outros aplicativos do sistema usando a comunicação interprocesso (IPC). Uma solução possível é desativar todos os bloatwares até que o serviço finalmente pare (após uma reinicialização). Mas devido a pesadas modificações das estruturas do Android pelos fornecedores, remover ou desativar certos bloatwares pode fazer com que a UI do Sistema trave ou até mesmo causar o bootloop, assim, (levemente) brickando seu dispositivo. Você pode buscar na internet ou consultar outros usuários para saber mais sobre como remover bloatwares do seu dispositivo.

::: tip Observação
Na maioria dos casos, você não pode remover todos os bloatwares do seu dispositivo. Portanto, recomenda-se que você use uma ROM personalizada livre de bloatwares, como a Graphene OS ou a Lineage OS.
:::

[shizuku]: https://shizuku.rikka.app
[shizuku_56]: https://github.com/RikkaApps/Shizuku/issues/56
[shizuku_license]: https://github.com/RikkaApps/Shizuku/commit/c079e47637b9becd57bfb6e225c91168cbe228ff
[cal]: https://choosealicense.com/no-permission/
[shizuku_discussion]: https://github.com/MuntashirAkon/AppManager/issues/55
[free_sw]: https://www.gnu.org/philosophy/free-sw.html
