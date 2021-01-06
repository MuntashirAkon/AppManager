# Operações em 1 Clique

Esta página aparece depois de clicar na opção **Operações em 1 Clique** no [menu principal](./main-page.md#menu-de-opcoes). As operações atualmente suportadas incluem _bloquear/desbloquear rastreadores_, _bloquear componentes_ e _negar operações de aplicativo_. Mais opções serão adicionadas mais tarde.

::: details Tabela de Conteúdos
[[toc]]
:::

## Bloquear Rastreadores
Essa opção pode ser usada para bloquear ou desbloquear componentes de anúncios ou rastreadores de todos os aplicativos instalados. Ao clicar nesta opção, você terá as opções de listar todos os aplicativos ou apenas os aplicativos do usuário. Os usuários iniciantes devem selecionar apenas os aplicativos do usuário. Depois disso, uma caixa de diálogo com multiplas escolhas aparecerá, nela você pode desmarcar os aplicativos que deseja excluir desta operação. Clicar em _bloquear_ ou _desbloquear_ aplicará as alterações imediatamente.

::: warning Aviso
Certos aplicativos podem não funcionar como esperado após a aplicação do bloqueio. Se esse for o caso, remova as regras de bloqueio de uma vez ou uma por uma usando a página correspondente em [Detalhes do Aplicativo][1].
:::

_Veja também: [Como desbloquear os componentes rastreadores bloqueados usando Operações em 1 Clique ou Operações de Lote?](../faq/app-components.md#como-desbloquear-os-componentes-rastreadores-bloqueados-usando-operacoes-em-1-clique-ou-operacoes-em-lote)_

## Bloquear Componentes…
Essa opção pode ser usada para bloquear certos componentes do aplicativo denotados pelas assinaturas. A assinatura do aplicativo é o nome completo ou o nome parcial dos componentes. Por segurança, é recomendável que você adicione um `.` (ponto) no final de cada nome de assinatura parcial, pois o algoritmo usado aqui escolhe todos os componentes combinados de forma gananciosa. Você pode inserir mais de uma assinatura, caso em que todas as assinaturas têm que ser separadas por espaços. Semelhante à opção acima, há uma opção de aplicar bloqueio aos aplicativos do sistema também.

::: danger Atenção
Se você não estiver ciente das consequências de bloquear componentes de aplicativos por assinatura(s), você deve evitar usar esta configuração, pois pode resultar em boot loop ou soft brick, e você pode ter que aplicar um reset de fábrica para usar seu sistema operacional.
:::

## Negar Operações de Aplicativo…
Esta opção pode ser usada para bloquear certas [operações de aplicativo](../tech/AppOps.md) de todos ou aplicativos selecionados. Você pode inserir mais de uma constante de operação de aplicativo separadas por espaços. Não é possível conhecer com antecedência todas as constantes de operações de aplicativo, pois elas variam de dispositivo para dispositivo e de OS para OS. Para encontrar a constante de operação de aplicativo desejada, navegue pela aba _Operações de Aplicativo_ na página de [Detalhes do Aplicativo][1]. As constantes são números inteiros fechados dentro de parênteses ao lado do nome de cada aplicação.

::: danger Atenção
A menos que você esteja bem informado sobre as operações de aplicativos e as consequências de bloqueá-las, você deve evitar usar esta função, pois pode resultar em boot loop ou soft brick, e você pode ter que aplicar um reset de fábrica para usar o seu sistema operacional.
:::

[1]: ./app-details-page.md
