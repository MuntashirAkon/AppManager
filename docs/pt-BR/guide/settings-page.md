---
next: false
sidebarDepth: 2
---

# Página de Configurações
As configurações podem ser usadas para personalizar o comportamento do aplicativo.

::: details Tabela de Conteúdos
[[toc]]
:::

## Idioma
Configure o idioma no aplicativo. O App Manager atualmente suporta 12 (doze) idiomas.

## Tema do Aplicativo
Configure o tema no aplicativo.

## Mode of Operation
You can select one of the four options:
- **Auto.** Let AM select the suitable option for you. Although this is the default option, it may cause problems in some devices.
- **Root.** Select root mode. AM will request for root permission if not already granted. But when selected, AM will run in root mode even if you don't allow root. This may cause crashes, therefore, you shouldn't enable it if you haven't granted root.
- **ADB over TCP.** Enable [ADB over TCP][1] mode. AM may hang indefinitely if you haven't enabled ADB over TCP.
- **No-root.** AM runs in no-root mode. AM will perform better if this is enabled but all the root/ADB features will be disabled.

## Acesso ao Uso
Desativar esta opção desativa a página de **Uso de Aplicativos**, bem como _uso de dados_ e _informações de armazenamento de aplicativos_ na [Aba de Informações do Aplicativo][3]. Com essa opção desligada, o App Manager nunca pedirá pela permissão de _Acesso ao Uso_

## APK Signing
### Signature schemes
Select the [signature schemes](https://source.android.com/security/apksigning) to use. It is recommended that you use at least v1 and v2 signature schemes. Use the _Reset to Default_ button in case you're confused.

## Regras

### Bloqueio Global de Componentes
Ative o bloqueio de componentes globalmente. Por padrão, as regras de bloqueio não são aplicadas a menos que sejam aplicadas na página de [Detalhes do Aplicativo][2] para qualquer pacote. Ao ativar essa opção, todas as regras (antigas e novas) são aplicadas imediatamente para todos os aplicativos sem permitir explicitamente o bloqueio para qualquer aplicativo.

::: warning Aviso
A ativação dessa configuração pode ter alguns efeitos colaterais não intencionais, como regras que não estão completamente removidas sendo aplicadas. Então, proceda com cautela. Esta opção deve ser mantida desativada se não for necessária por algumas razões.
:::

_Veja também: [O que é o bloqueio global de componentes?][7]_

### Importar/Exportar Regras de Bloqueio
É possível importar ou exportar regras de bloqueio dentro do App Manager para todos os aplicativos. Há uma opção de exportar ou importar apenas certas regras (componentes, operações de aplicativo ou permissões) em vez de todas elas. Também é possível importar regras de bloqueio do [Blocker][4] e [Watt][5]. Se for necessário exportar regras de bloqueio para um único aplicativo, use a página correspondente em [Detalhes do Aplicativo][2] para exportar regras, ou para vários aplicativos, use as [operações em lote][6].

_Veja também: [App Manager: Especificação das Regras][rules_spec]_

#### Exportar
Exportar regras de bloqueio para todos os aplicativos configurados no App Manager. Isso pode incluir [componentes do aplicativo][what_are_components], operações de aplicativo e permissões com base em quais opções são/estão selecionadas nas opções de múltipla escolha.

#### Importar
Importar regras de bloqueio exportadas anteriormente do App Manager. Semelhante à exportação, isso pode incluir [componentes do aplicativo][what_are_components], operações de aplicativo e permissões com base em quais opções são/estão selecionadas nas opções de múltipla escolha.

#### Importar Regras Existentes
Adicione componentes desativados por outros aplicativos ao App Manager. O App Manager só mantém o controle dos componentes desativados dentro do App Manager. Se você usar outras ferramentas para bloquear componentes do aplicativo, você pode usar essas ferramentas para importar esses componentes desativados. Clicar nesta opção desencadeia uma busca por componentes desativados e listará aplicativos com componentes desativados pelo usuário. Por segurança, todos os aplicativos não são selecionados por padrão. Você pode selecionar manualmente os aplicativos da lista e reaplicar o bloqueio através do App Manager.

::: danger Atenção
Tenha cuidado ao usar esta ferramenta, pois pode haver muitos falsos positivos. Escolha apenas os aplicativos que você está certo.
:::

#### Importar do Watt
Importar arquivos de configuração do [Watt][5], cada arquivo contendo regras para um único pacote e nome do arquivo sendo o nome do pacote com a extensão `.xml`.

::: tip
Localização dos arquivos de configuração no Watt: <tt>/sdcard/Android/data/com.tuyafeng.watt/files/ifw</tt>
:::

#### Importar do Blocker
Importar regras de bloqueio do [Blocker][4], cada arquivo contendo regras para um único pacote. Esses arquivos têm a extensão `.json`.

### Remover todas as regras
Opção de um clique para remover todas as regras configuradas no App Manager. Isso ativará todos os componentes bloqueados, as opções de aplicativos serão definidas para seus valores padrão e as permissões serão concedidas.

## Installer
Installer specific options

### Show users in installer
For root/ADB users, a list of users will be displayed before installing the app. The app will be installed only for the specified user (or all users if selected).

### Sign APK
Whether to sign the APK files before installing the app. See [APK signing](#apk-signing) section above to configure signing.

### Install location
Choose APK install location. This can be one of _auto_, _internal only_ and _prefer external_. Depending on Android version, the last option may not always install the app in the external storage.

### Installer App
Select the installer app, useful for some apps which explicitly checks for installer. This only works for root/ADB users.

## Backup/Restauração
Configurações relacionadas a [backup/restauração][backup_restore].

### Método de compressão
Defina qual método de compressão deve ser usado durante os backups. O App Manager suporta métodos de compactação GZip e BZip2, sendo o GZip o método padrão de compressão. Isso não afeta a restauração de um backup existente.

### Opções de backup
Personalize o diálogo de backup/restauração.

### Backup apps with Android KeyStore
Allow backup of apps that has entries in the Android KeyStore (disabled by default). Some apps (such as Signal) may crash if restored. KeyStore backup also doesn't work from Android 9 but still kept as many apps having KeyStore can be restored without problem.

### Encryption
Set an encryption method for the backups. AM currently supports OpenPGP only.

::: warning Atenção
A partir da v2.5.16, o App Manager não se lembra do ID das chaves para um backup específico. Você tem que se lembrar deles você mesmo.
:::

## Device Info
Display Android version, security, CPU, GPU, battery, memory, screen, languages, user info, etc.

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#aba-de-informacoes-do-aplicativo
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#operacoes-em-lote
[7]: ../faq/app-components.md#o-que-e-o-bloqueio-global-de-componentes
[what_are_components]: ../faq/app-components.md#o-que-sao-os-componentes-de-aplicativo
[rules_spec]: ../tech/rules-specification.md
[backup_restore]: ./backup-restore.md
