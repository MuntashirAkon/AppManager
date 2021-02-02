---
sidebarDepth: 2
tags:
  - exodus
  - rastreador
  - anúncio
  - bloqueio
  - biblioteca
  - anti-função
---

# Página de Scanner
**A página de scanner** aparece depois de clicar no botão _scanner_ na [aba de Informações do Aplicativo][app_info]. Arquivos APK externos também podem ser abertos para serem scaneados a partir de gerenciadores de arquivos, navegadores, etc.

Ele verifica rastreadores e bibliotecas e exibe o número de rastreadores e bibliotecas como um resumo. Ele também exibe checksums do arquivo apk, bem como o(s) certificado(s) de assinatura(s).

::: danger Disclaimer
AM only scans an app statically. An app may provide the options for opting out, or in some cases, certain features of the tracker may not be used at all by the app (such as F-Droid), or some apps may simply use them as placeholders to prevent breaking certain features (such as Fennec F-Droid). The intention of the scanner is to give you an idea about what the APK might contain. It should be taken as an initial step for further investigations.
:::

Clicar no primeiro item (ou seja, número de classes) abre uma nova página contendo uma lista de classes rastreadoras para o aplicativo. Todas as classes também podem ser visualizadas clicando no menu _Alternar Lista de Classes_. Uma espiada de cada classe pode ser visualizada simplesmente clicando em qualquer item da classe.

::: warning Aviso
Devido a várias limitações, não é possível digitalizar todos os componentes de um arquivo apk. Isso é especialmente verdade se um apk é altamente ofuscado. O scanner também não verifica strings (ou assinaturas do site).
:::

O segundo item lista o número de rastreadores junto com seus nomes. Clicar no item exibe uma caixa de diálogo contendo o nome dos rastreadores, assinaturas combinadas e o número de classes em cada assinatura. Os nomes do rastreador podem ter alguns prefixos, tais como:
- `°` denota que o rastreador está faltando na lista do εxodus (retirado do [IzzyOnDroid repo][izzy])
- `²` denota que o rastreador está na lista de espera [ETIP][etip], ou seja, ainda não está decidido se é um rastreador real
- `µ` denota que é um micro rastreador não intrusivo, significando que esses rastreadores são inofensivos, mas ainda é um rastreador
- `?` denota que o status do rastreador é desconhecido

O terceiro item lista o número de bibliotecas junto com seus nomes. Essas informações são retiradas do [IzzyOnDroid repo][izzy].

_Veja também: [FAQ: Rastreadores vs componentes rastreadores][t_vs_tc]_

[app_info]: ./app-details-page.md#aba-de-informacoes-do-aplicativo
[etip]: https://etip.exodus-privacy.eu.org
[t_vs_tc]: ../faq/app-components.md#classes-de-rastreador-versus-componentes-rastreadores
[izzy]: https://gitlab.com/IzzyOnDroid/repo
