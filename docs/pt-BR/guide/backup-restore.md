---
sidebarDepth: 2
---

# Backup/Restauração
O App Manager tem um moderno, avançado e fácil de usar sistema de backup/restauração implementado do zero. Este é provavelmente o único aplicativo que tem a capacidade de restaurar não apenas o aplicativo ou seus dados, mas também permissões e regras que você configurou dentro do App Manager. Você também pode optar por fazer backup de um aplicativo várias vezes (com nomes personalizados) ou para todos os usuários.

::: tip Aviso
Backup/Restauração é completamente diferente de **Fazer Backup do APK** que também faz parte das operações em lote.
:::

::: details Tabela de Conteúdos
[[toc]]
:::

## Localização
Backup/restauração faz parte das [operações em lote][batch_ops]. Ele também está localizado dentro do [menu de opções][app_info_menu] na [Aba de Informações do Aplicativo][app_info]. Clicar em **Backup/Restauração** abre as **Opções de Backup**. Os backups estão atualmente localizados em <tt>/sdcard/AppManager</tt>.

::: tip Observação
Se um ou mais aplicativos selecionados não tiverem backup, as opções **Restaurar** e **Excluir Backup** não serão mostradas.
:::

## Opções de Backup
As opções de backup (internamente conhecidas como flags de backup) permitem personalizar seus backups em tempo real. No entanto, as personalizações não serão lembradas para backups futuros. Se você quiser personalizar este diálogo, use as [Opções de Backup][settings_bo] na [Página de Configurações][settings].

Uma descrição completa das opções de backup é fornecida abaixo:
- **Fonte.** Seja para fazer backup ou restaurar todo o diretório de origem. Quando você instala um aplicativo, os arquivos apk são salvos dentro de <tt>/data/app/</tt> juntamente com quaisquer bibliotecas nativas, bem como alguns outros arquivos, como os arquivos odex e vdex. Este diretório é chamado de **diretório fonte** ou **caminho de código**. Você pode personalizar ainda mais isso usando a opção **Apenas APK** (veja abaixo).
- **Apenas APK.** Quando você ativa a opção **Fonte**, todo o diretório de origem é salvo em backup ou restaurado. Ativando isso junto com **Fonte** apenas fará o backup ou restauração dos arquivos apk e pulará o backup de bibliotecas nativas ou arquivos ODEX e VDEX.
- **Dados.** Se deve fazer backup dos diretórios internos de dados. Esses diretórios estão localizados em <tt>/data/user/<user_id></tt> e (para Android N ou superior) <tt>/data/user_de/<user_id></tt>.
- **Dados externos.** Se deve fazer backup de diretórios de dados localizados na memória interna, bem como cartão SD (se existir). Diretórios de dados externos geralmente contêm dados de aplicativos ou arquivos de mídia não essenciais (em vez de usar a pasta de mídia dedicada) e podem aumentar o tamanho do backup. Mas pode ser essencial para alguns aplicativos. Embora não seja marcado por padrão (pois pode aumentar drasticamente o tamanho do backup), você precisará marcar isso para garantir uma restauração suave de seus backups.
- **OBB e mídia.** Seja para fazer backup ou restaurar o OBB e os diretórios de mídia localizados no armazenamento externo ou cartão SD. Isso é útil para jogos e alguns softwares gráficos que realmente usam essas pastas.
- **Excluir o cache.** Os aplicativos Android têm multiplos diretórios de cache localizados em todos os diretórios de dados (tanto internos quanto externos). Existem dois tipos de cache: **cache** e **cache de código**. A ativação dessa opção exclui ambos os diretórios de cache de todos os diretórios de dados. É geralmente aconselhável excluir diretórios de cache, uma vez que a maioria dos aplicativos não limpa o cache (por algum motivo, a única maneira de um aplicativo limpar seu cache é excluindo todo o diretório de cache) e geralmente manuseando pelo próprio OS. Aplicativos como o Telegram podem usar cache muito grande (dependendo do espaço de armazenamento) que pode aumentar drasticamente o tamanho do seu backup. When it is enabled, AM also ignores backup from the **no_backup** directories.
- **Extras.** Seja para fazer backup ou restaurar permissões de aplicativos, ativado por padrão. Observe que as regras de bloqueio são aplicadas _após_ aplicar as permissões. Assim, se uma permissão também estiver disponível nas regras de bloqueio, ela será substituída (ou seja, a das regras de bloqueio será usada).
- **Rules.** This option lets you back up blocking rules configured within App Manager. This might come in handy if you have customised permissions or block some components using App Manager as they will also be backed up or restored when you enable this option.
- **Backup múltiplo.** Se isso é um backup múltiplo. Por padrão os backups são salvos usando seu ID de usuário. Ativar essa opção permite criar backups adicionais. Esses backups usam a data atual como nome de backup padrão, mas você também pode adicionar um nome de backup personalizado usando o campo de entrada que é exibido quando você clica no botão **Backup**.
- **Todos os usuários.** Backup ou restauração para todos os usuários em vez de apenas o usuário atual. _This option is obsolete and will be replaced with a more suitable option in future._
- **Pular verificação de assinatura.** Ao fazer um backup, os checksums para cada arquivo (bem como o(s) certificado(s) de assinatura do arquivo apk base) são gerados e armazenados em `checksums.txt`. Quando você restaura o backup, os checksums são gerados novamente e são combinados com os checksums armazenados no referido arquivo. Ativar essa opção desativará as verificações de assinatura. Esta opção só é aplicada quando você restaura um backup. Durante o backup, os checksums são gerados independentemente desta opção.
  ::: warning Atenção
  Você deve sempre desativar essa opção para garantir que os backups não sejam modificados por nenhum aplicativo de terceiros. Mas isso só funciona se você ativar a criptografia.
  :::
  ::: tip Notice
  App Manager doesn't yet support restoring Storage Access Framework (SAF) rules. You have to enable them manually after restoring a backup.
  :::

## Backup
O backup respeita todas as opções de backup, exceto **Pular verificação de assinatura**. Se os backups base (ou seja, backups que não têm a opção **Backup múltiplo**) já existem, você receberá um aviso, pois os backups serão substituídos. Se **Backup múltiplo** está selecionado, você tem uma opção para inserir o nome de backup ou você pode deixá-lo em branco para usar a data e hora atual.

## Restauração
Restaurar respeita todas as opções de backup e falhará se a opção **Fonte** está selecionada mas o backup não contém backups de origem ou em outros casos, se o aplicativo não estiver instalado. Ao restaurar backups de vários pacotes, você só pode restaurar os backups base (veja a seção [backup](#backup) para uma explicação). No entanto, ao restaurar backups de um único pacote, você tem a opção de selecionar qual backup restaurar. Se a opção **Todos os usuários** está selecionada, o AM restaurará o backup selecionado para todos os usuários neste último caso, mas no primeiro caso, ele restaurará backups básicos para os respectivos usuários.

## Excluir Backup
Excluir backup apenas respeita a opção **Todos os usuários** e quando ele for selecionado, apenas os backups base para todos os usuários serão excluídos com um prompt. Ao excluir backups para um único pacote, outra caixa de diálogo será exibida onde você pode selecionar os backups para excluir.

## Criptografia
O App Manager atualmente suporta criptografia OpenPGP. Para ativá-la, você precisa instalar um provedor OpenPGP, como o [OpenKeychain][open_keychain]. Para configurar o provedor OpenPGP, vá para a [Página de configurações][settings].

[batch_ops]: ./main-page.md#operacoes-em-lote
[app_info]: ./app-details-page.md#aba-de-informacoes-do-aplicativo
[app_info_menu]: ./app-details-page.md#menu-de-opcoes
[settings]: ./settings-page.md
[settings_bo]: ./settings-page.md#opcoes-de-backup
[open_keychain]: https://openkeychain.org
