---
sidebarDepth: 2
---

# App Manager: Especificação das Regras

*Vá para a [questão relacionada](https://github.com/MuntashirAkon/AppManager/issues/24) para discussão.*

**App Manager** atualmente suporta o bloqueio de atividades, receptores de transmissão, provedores de conteúdo, serviços, operações de aplicativos e permissões, e no futuro posso adicionar mais opções de bloqueio. Para adicionar mais portabilidade, é necessário importar/exportar todos esses dados.

::: details Tabela de Conteúdos
[[toc]]
:::

## Plano de Fundo
Todos os arquivos de configuração são armazenados em <tt>/data/data/io.github.muntashirakon.AppManager/Files/conf</tt>, e <tt>/sdcard/Android/data/io.github.muntashirakon.AppManager/Files/ifw</tt> é usado como um armazenamento temporário. Este último diretório é mantido para fornecer compatibilidade para o App Manager v2.5.5 ou versões anteriores. Este último diretório será removido na v2.6, pois não está protegido para armazenar dados confidenciais no armazenamento compartilhado, pois qualquer aplicativo que tenha acesso a esses diretórios pode criar ou modificar esses arquivos.

::: tip A partir da v2.5.6, este último diretório é mantido principalmente para armazenamento temporário. Se você estiver atualizando a partir da v2.5.5 ou versões mais antigas, certifique-se de aplicar o [Bloqueio global de componentes][gcb] que importará todas as regras deste diretório automaticamente (você pode desativar esta opção posteriormente). :::

Manter um banco de dados deve ser a melhor escolha quando se trata de armazenar dados. Mas por enquanto, eu vou estar usando vários arquivos `tsv` com cada arquivo tendo o nome do pacote e uma extensão `.tsv`. O arquivo/banco de dados será consultado/processado pela classe `RulesStorageManager`. Devido a essa abstração, deve ser mais fácil mudar para sistemas de banco de dados ou de banco de dados criptografados no futuro sem alterar o design de todo o projeto.

## Formato do Arquivo de Regras

### Interno
O formato abaixo é usado internamente dentro do App Manager e _não é compatível com o formato externo._
```
<name> <type> <mode>|<component_status>|<is_granted>
```
Onde:
- `<name>` - Componente/Permissão/Nome da operação de aplicativo (no caso da operação de aplicativo, pode ser uma string ou um número inteiro)
- `<type>` - Um dos `ACTIVITY`, `RECEIVER`, `PROVIDER`, `SERVICE`, `APP_OP`,  `PERMISSION`
- `<mode>` - (Para operações de aplicativo) O associado [modo constante][mode_constants]
- `<component_status>` - (Para componentes) Status do componente
    * `true` - Componente foi aplicado (`true` valor é mantido para compatibilidade)
    * `false` - Componente ainda não foi aplicado, mas será aplicado no futuro (`false` valor é mantido para compatibilidade)
    * `unblocked` - O componente está programado para ser desbloqueado
- `<is_granted>` - (Para permissões) Se a permissão é concedida ou revogada

### Externo
O formato externo é usado para importar ou exportar regras no App Manager.
```
<package_name> <component_name> <type> <mode>|<component_status>|<is_granted>
```
Este formato é essencialmente o mesmo que acima, exceto para o primeiro item que é o nome do pacote.

::: danger Atenção
As regras exportadas têm um formato diferente do interno e não devem ser copiadas diretamente para a pasta **conf**.
:::

[mode_constants]: ./AppOps.md#constantes-mode
[gcb]: ../guide/settings-page.md#bloqueio-global-de-componentes
