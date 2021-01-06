---
prev: ./
sidebarDepth: 2
---

# Operações de Aplicativo

*Vá para a [questão relacionada](https://github.com/MuntashirAkon/AppManager/issues/17) para discussão.*

::: details Tabela de Conteúdos
[[toc]]
:::

## Plano de Fundo
**App Ops** (abreviação para **Operações de Aplicativo**) são usadas pelo sistema Android (desde o Android 4.3) para controlar as permissões do aplicativo. O usuário *pode* controlar algumas permissões, mas apenas as permissões que são consideradas perigosas (e o Google acha que saber seu número de telefone não é uma coisa perigosa). Assim, as operações de aplicativo parecem ser o que precisamos se quisermos instalar aplicativos como o Facebook e o Messenger (que registra literalmente tudo) e ainda quiser ter *alguma* privacidade e/ou segurança. Embora certas funções das operações de aplicativo estivessem disponíveis nas Configurações e mais tarde nas configurações ocultas na versão mais antiga do Android, isso está completamente escondido em versões mais recentes do Android e continuará a ser mantido escondido. Agora, qualquer aplicativo com a permissão **android.Manifest.permission.GET_APP_OPS_STATS** pode obter informações sobre as operações de aplicativo para outros aplicativos, mas essa permissão está oculta dos usuários e só pode ser ativada usando ADB ou root. Ainda assim, o aplicativo com essa permissão não pode conceder ou revogar permissões (na verdade modo de operação) para aplicativos que não sejam eles mesmos (com capacidade limitada, é claro). Para modificar as operações de outro aplicativo, o aplicativo precisa das permissões **android.Manifest.permission.UPDATE_APP_OPS_STATS** que não são acessíveis via comando _pm_. Assim, você não pode concedê-lo via root ou ADB, a permissão só é concedida aos aplicativos do sistema. Há muito poucos aplicativos que suportam desativar permissões via operações de aplicativo. O melhor que conheço é o [AppOpsX][1]. A principal diferença (visível) entre meu aplicativo (AppManager) e este aplicativo é que o posterior também fornece a capacidade de revogar permissões de internet (escrevendo tabelas de ip). Outra diferença é que o autor usou a API oculta para acessar/conceder/revogar as operações, enquanto eu usei a ferramenta de linha de comando [_appops_](#interface-de-linha-de-comando-appops) para isso. Eu fiz isso por causa do limite de [Reflexão][2] que o Android recentemente impôs que tornou muitas APIs ocultas inutilizáveis (existem alguns hacks, mas eles podem não funcionar após o lançamento final do R, eu acredito). Um problema crucial que enfrentei durante o desenvolvimento de uma API para Operações de Aplicativo foi a falta de documentação em Inglês.

## Introdução às Operações de Aplicativo

<img :src="$withBase('/assets/how_app_ops_work.png')" alt="Como o AppOps Funciona" />

A figura (tirada deste [artigo][3]) acima descreve o processo de alteração e processamento de permissões. O [**Gerenciador de Operações de Aplicativo**](#gerenciador-de-operacoes-de-aplicativo) pode ser usado para gerenciar permissões nas Configurações do aplicativo. O **Gerenciador de Operações de Aplicativo** também é útil para determinar se uma determinada permissão (ou operação) foi concedida ao aplicativo. A maioria dos métodos do **Gerenciador de Operações de Aplicativo** são acessíveis ao aplicativo do usuário, mas ao contrário de um aplicativo do sistema, ele só pode ser usado para verificar permissões para qualquer aplicativo ou para o próprio aplicativo e iniciar ou encerrar certas operações. Além disso, nem todas as operações são realmente acessíveis a partir desta classe Java. O **Gerenciador de Operações de Aplicativo** detém todas as constantes necessárias, como [_OP\_*_](#constantes-op), `OPSTR_*`, [_MODE\_*_](#constantes-mode) que descreve código de operação, sequência de operação e modo de operações, respectivamente. Também mantém estuturas de dados necessárias, como [**PackageOps**](#pacote-de-operacoes) e **OpEntry**. **PackageOps** mantém **OpEntry** para um pacote, e **OpEntry**, como o nome sugere, descreve cada operação. Por baixo dos panos, o **Gerenciador de Operações de Aplicativo** chama o **Serviço de Operações de Aplicativo** para realizar qualquer trabalho real.

O [**Serviço de Operações de Aplicativo**][5] é completamente oculto de um aplicativo de usuário, mas acessível para os aplicativos do sistema. Como visto na foto, esta é a classe que realmente gerencía as coisas. Contém estruturas de dados como **Ops** para armazenar informações básicas do pacote e **Op** que é semelhante a **OpEntry** do **Gerenciador de Operações de Aplicativo**. Ele também tem **Shell** que é, na verdade, o código fonte da ferramenta de linha de comando _appops_. Ele grava configurações para ou lê configurações do [/data/system/appops.xml](#appops-xml). Os serviços do sistema chama o **Serviço de Operações de Aplicativo** para descobrir o que um aplicativo é permitido e o que não é permitido realizar, e o **Serviço de Operações de Aplicativo** determina essas permissões analisando `/data/system/appops.xml`. Se nenhum valor personalizado for definido em _appops.xml_, ele retorna ao modo padrão disponível no **Gerenciador de Operações de Aplicativo**.


## Gerenciador de Operações de Aplicativo
[AppOpsManager][4] significa gerenciador de operações de aplicativo. Consiste em várias constantes e classes para modificar as operações de aplicativo. A documentação oficial pode ser encontrada [aqui][11].

### Constantes OP_*
`OP_*` são as constantes inteiras a partir de `0`. `OP_NONE` implica que nenhuma operação é especificada, enquanto `_NUM_OP` denota o número de operações definidas no prefixo `OP_*`.  Isso denota cada operação. Mas essas operações não são necessariamente únicas. Na verdade, há muitas operações que são realmente uma única operação denotada por múltiplas constantes `OP_*` (possivelmente para uso futuro). Os fornecedores podem definir sua própria operação com base em suas necessidades. A MIUI é um dos fornecedores que são conhecidos por fazer isso.

_Uma pequena espiada sobre `OP_*`:_
``` java{1,10}
public static final int OP_NONE = -1;
public static final int OP_COARSE_LOCATION = 0;
public static final int OP_FINE_LOCATION = 1;
public static final int OP_GPS = 2;
public static final int OP_VIBRATE = 3;
...
public static final int OP_READ_DEVICE_IDENTIFIERS = 89;
public static final int OP_ACCESS_MEDIA_LOCATION = 90;
public static final int OP_ACTIVATE_PLATFORM_VPN = 91;
public static final int _NUM_OP = 92;
```

Se uma operação é única, é definida por [`sOpToSwitch`][7]. Ele mapeia cada operação para outra operação ou para si mesma (se for uma operação única). Por exemplo,, `OP_FINE_LOCATION` e `OP_GPS` são mapeadas para `OP_COARSE_LOCATION`.

Cada operação tem um nome privado que são descritos por [`sOpNames`][10]. Esses nomes são geralmente os mesmos nomes das constantes sem o prefixo `OP_`. Algumas operações têm nomes públicos, bem como que são descritos por `sOpToString`. Por exemplo, `OP_COARSE_LOCATION` tem o nome público **android:coarse_location**.

Como um processo gradual de movimentação de permissões para operações de aplicativo, já existem muitas permissões que são definidas em algumas operações. Essas permissões são mapeadas em [`sOpPerms`][8]. Por exemplo, a permissão **android.Manifest.permission.ACCESS_COARSE_LOCATION** é mapeada para `OP_COARSE_LOCATION`. Algumas operações podem não ter quaisquer permissões associadas que tenham valores `null`.

Como descrito na seção anterior, as operações configuradas para um aplicativo são armazenadas em [/data/system/appops.xml](#appops-xml). Se uma operação não estiver configurada, então o sistema permitirá que essa operação seja determinada a partir de [`sOpDefaultMode`][9]. Ele lista o _modo padrão_ para cada operação.

### Constantes MODE_*
Constantes `MODE_*` também constantes inteiras a partir de `0`. Essas constantes são atribuídas a cada operação descrevendo se um aplicativo está autorizado a realizar essa operação. Esses modos geralmente têm nomes associados, como **allow** para `MODE_ALLOWED`, **ignore** para `MODE_IGNORED`, **deny** para `MODE_ERRORED` (um termo um pouco errado), **default** para `MODE_DEFAULT` e **foreground** para `MODE_FOREGROUND`.

_Modos padrão:_
``` java
/**
 * o chamador fornecido é autorizado a realizar a operação fornecida.
 */
public static final int MODE_ALLOWED = 0;
/**
 * o chamador fornecido não está autorizado a realizar a operação fornecida, e esta tentativa deve
 * <em>falhar silenciosamente</em> (ele não deve fazer com que o aplicativo caia).
 */
public static final int MODE_IGNORED = 1;
/**
 * o chamador fornecido não está autorizado a realizar a operação fornecida, e esta tentativa deve
 * porque ele tem um erro fatal, tipicamente um {@link SecurityException}.
 */
public static final int MODE_ERRORED = 1 << 1;  // 2
/**
 * o chamador deve usar sua verificação de segurança padrão. Este modo não é normalmente usado
 */
public static final int MODE_DEFAULT = 3;
/**
 * Modo especial que significa "permitir apenas quando o aplicativo estiver em primeiro plano."
 */
public static final int MODE_FOREGROUND = 1 << 2;
```

Além desses modos padrão, os fornecedores podem definir modos personalizados, como `MODE_ASK` (com o nome **ask**) que é usado ativamente pela MIUI. A MIUI também usa alguns outros modos sem qualquer nome associado a eles.


### Pacote de Operações
**AppOpsManager.PackageOps** é uma estrutura de dados para armazenar todos as **OpEntry** para um pacote. Em termos simples, armazena todas as operações personalizadas para um pacote.

``` java
public static class PackageOps implements Parcelable {
  private final String mPackageName;
  private final int mUid;
  private final List<OpEntry> mEntries;
  ...
}
```
Como pode ser visto acima, ele armazena todas as **OpEntry** para um pacote, bem como o nome do pacote correspondente e seu ID de usuário do kernel.


### OpEntry
**AppOpsManager.OpEntry** é uma estrutura de dados que armazena uma única operação para qualquer pacote.

``` java
public static final class OpEntry implements Parcelable {
    private final int mOp;
    private final boolean mRunning;
    private final @Mode int mMode;
    private final @Nullable LongSparseLongArray mAccessTimes;
    private final @Nullable LongSparseLongArray mRejectTimes;
    private final @Nullable LongSparseLongArray mDurations;
    private final @Nullable LongSparseLongArray mProxyUids;
    private final @Nullable LongSparseArray<String> mProxyPackageNames;
    ...
}
```
Aqui:
- `mOp`: Denota uma das constantes [`OP_*`](#constantes-op).
- `mRunning`: Se as operações estão em andamento (ou seja, a operação começou, mas ainda não terminou). Nem todas as operações podem ser iniciadas ou concluídas assim.
- `mMOde`: Uma das constantes [`MODE_*`](#constantes-mode).
- `mAccessTimes`: Armazena todos os horários de acesso disponíveis
- `mRejectTimes`: Armazena todos os horários de rejeição disponíveis
- `mDurations`: Todas as durações de acesso disponíveis, verificando isso com `mRunning` lhe dirá por quanto tempo o aplicativo está realizando uma determinada operação de aplicativo.
- `mProxyUids`: Nenhuma documentação encontrada
- `mProxyPackageNames:` Nenhuma documentação encontrada

### Uso
TAREFAS

## Serviços de Operações de Aplicativo
TAREFAS

## appops.xml

As últimas `appops.xml` tem o seguinte formato: (Este DTD é feito por mim e não está perfeito, tem problemas de compatibilidade.)

```dtd
<!DOCTYPE app-ops [

<!ELEMENT app-ops (uid|pkg)*>
<!ATTLIST app-ops v CDATA #IMPLIED>

<!ELEMENT uid (op)*>
<!ATTLIST uid n CDATA #REQUIRED>

<!ELEMENT pkg (uid)*>
<!ATTLIST pkg n CDATA #REQUIRED>

<!ELEMENT uid (op)*>
<!ATTLIST uid
n CDATA #REQUIRED
p CDATA #IMPLIED>

<!ELEMENT op (st)*>
<!ATTLIST op
n CDATA #REQUIRED
m CDATA #REQUIRED>

<!ELEMENT st EMPTY>
<!ATTLIST st
n CDATA #REQUIRED
t CDATA #IMPLIED
r CDATA #IMPLIED
d CDATA #IMPLIED
pp CDATA #IMPLIED
pu CDATA #IMPLIED>

]>
```

As instruções abaixo seguem a ordem exata dada acima:
* `app-ops`: O elemento root. Ele pode conter qualquer número de `pkg` ou pacote `uid`
  - `v`: (opcional, número inteiro) O número da versão (padrão: `NO_VERSION` ou `-1`)
* `pkg`: Armazena informações do pacote. Ele pode conter qualquer número do `uid`
  - `n`: (necessário, string) Nome do pacote
* Pacote `uid`: Pacotes de lojas ou informações sobre pacotes
  - `n`: (necessário, número inteiro) O ID do usuário
* `uid`: O ID do pacote do usuário. Ele pode conter qualquer número de `op`
  - `n`: (necessário, número inteiro) O ID do usuário
  - `p`: (optional, boolean) Se o aplicativo é um aplicativo privado/do sistema
* `op`: A operação pode conter `st` ou nada
  - `n`: (necessário, número inteiro) O nome da operação em inteiro, ou seja. AppOpsManager.OP_*
  - `m`: (necessário, número inteiro) O modo da operação, ou seja. AppOpsManager.MODE_*
* `st`: Estado de funcionamento: se a operação é acessada, rejeitada ou em execução (não disponível em versões antigas)
  - `n`: (necessário, longo) Chave contendo flags e uid
  - `t`: (opcional, longo) Tempo de acesso (padrão: `0`)
  - `r`: (opcional, longo) Tempo de rejeição (padrão: `0`)
  - `d`: (opcional, longo) Duração do acesso (padrão: `0`)
  - `pp`: (opcional, string) Nome do pacote proxy
  - `pu`: (opcional, número inteiro) Pacote uid do proxy

Esta definição pode ser encontrada em [**AppOpsService**][5].

## interface de linha de comando appops
`appops` ou `cmd appops` (nas versões mais recentes) pode ser acessado via ADB ou root. Este é um método mais fácil de obter ou atualizar qualquer operação para um pacote (desde que o nome do pacote seja conhecido). A página de ajuda deste comando é auto explicativa:

```
Comandos do serviço de Operações de Aplicativo (appops):
help
  Escreva este texto para receber ajuda.
start [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Inicia uma determinada operação para um determinado aplicativo.
stop [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Interrompe uma determinada operação para um determinado aplicativo.
set [--user <USER_ID>] <[--uid] PACKAGE | UID> <OP> <MODE>
  Define o modo para um determinado aplicativo e operação.
get [--user <USER_ID>] <PACKAGE | UID> [<OP>]
  Retorne o modo para um determinado aplicativo e operação opcional.
query-op [--user <USER_ID>] <OP> [<MODE>]
  Imprime todos os pacotes que atualmente possuem a operação fornecida no modo fornecido.
reset [--user <USER_ID>] [<PACKAGE>]
  Redefine o aplicativo ou todos os aplicativos para os modos padrão.
write-settings
  Grava imediatamente as alterações pendentes no armazenamento.
read-settings
  Lê as últimas configurações escritas, substituindo o estado atual em RAM.
opções:
  <PACKAGE> um nome de pacote Android ou seu UID se prefixado por --uid
  <OP>      uma operação das Operações de aplicativo.
  <MODE>    um de permitir, ignorar, negar ou padrão
  <USER_ID> o id do usuário sob o qual o pacote está instalado. Se --user não for
            especificado, o usuário atual é assumido.
```

[1]: https://github.com/8enet/AppOpsX
[2]: https://stackoverflow.com/questions/37628
[3]: https://translate.googleusercontent.com/translate_c?depth=2&pto=aue&rurl=translate.google.com&sl=auto&sp=nmt4&tl=en&u=https://www.cnblogs.com/0616--ataozhijia/p/5009718.html&usg=ALkJrhgSo4IcKp2cXJlqttXuiRJZGa_jnw
[5]: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java
[4]: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/AppOpsManager.java
[11]: https://developer.android.com/reference/android/app/AppOpsManager
[7]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=211?q=AppOpsManager&ss=android%2Fplatform%2Fsuperproject&gsn=sOpToSwitch&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%238ffb80c9b09fce58d7fe1a0af7d50fd025765d8f41e838fa3bc2754dd99d9c48
[8]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=361?gsn=sOpPerms&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%23230bc1462b07a3c1575477761782a9d3537d75b4ea0a16748082c74f50bc2814
[9]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=410?gsn=sOpDefaultMode&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%23a8c8e4e247453a8ce329b2c1130f9c7a7f91e2b97d159c3e18c768b4d42f1b75
[10]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=311?gsn=sOpNames&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%234f77b221ad3e5d9212e217eadec0b78cd35717a3bf2d0f2bc642dea241e02d72
