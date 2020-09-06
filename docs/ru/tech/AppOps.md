---
prev: ./
sidebarDepth: 2
---

# Операции приложения

*Перейдите к [похожей проблеме](https://github.com/MuntashirAkon/AppManager/issues/17) для обсуждения.*

::: details Таблица содержания
[[toc]]
:::

## Фоновый режим
**Операции приложения** используются системой Android (начиная с Android 4.3) для управления разрешениями приложений. The user *can* control some permissions, but only the permissions that are considered dangerous (and Google thinks knowing your phone number isn't a dangerous thing). So, app ops seems to be the one we need if we want to install apps like Facebook and it's Messenger (which literary records everything) and still want *some* privacy and/or security. Although certain features of app ops were available in Settings and later in hidden settings in older version of Android, it's completely hidden in newer versions of Android and is continued to be kept hidden. Теперь любое приложение с разрешением **android.Manifest.permission.GET_APP_OPS_STATS** может получать информацию об операциях приложения для других приложений, но это разрешение скрыто от пользователей и может быть включено только с помощью ADB или root. Тем не менее, приложение с этим разрешением не может предоставлять или отзывать разрешения (фактически режим операции) для приложений, отличных от себя (конечно, с ограниченными возможностями). Для изменения операции другого приложения, приложению необходимо разрешение **android.Manifest.permission.UPDATE_APP_OPS_STATS**, которое недоступно через команду _pm_. Таким образом, вы не можете предоставить его через root или ADB, разрешение предоставляется только системным приложениям. Очень мало приложений, которые поддерживают отключение разрешений через операции приложения. Насколько мне известно, лучшее среди них это [AppOpsX][1]. The main (visible) difference between my app (AppManager) and this app is that the later also provides you the ability to revoke internet permissions (by writing ip tables). Другое отличие состоит в том, что автор использовал скрытый API для доступа/предоставления/отзыва операций, тогда как я использовал инструмент командой строки [_appops_](#appops-command-line-interface) для этого. I did this because of the limit of [Reflection][2] that Android recently imposed which rendered many hidden APIs unusable (there are some hacks but they may not work after the final release of R, I believe). Одна из важнейших проблем, с которыми я столкнулся при разработке API раздела операций приложений, – это отсутствие документации на английском языке.

## Знакомство с операциями приложений

<img :src="$withBase('/assets/how_app_ops_work.png')" alt="Как работают операции приложений" />

Фигура (взята из [этой статьи][3]) выше описывает процесс изменения и обработки разрешения. [**Диспетчер операций приложений**](#appopsmanager) можно использовать для управления разрешениями в приложении "Настройки". **Диспетчер операций приложений** также полезен при определении того, предоставлено ли приложению определенное разрешение (или операция). Большинство методов **диспетчера операций приложений** доступны для пользовательских приложений, но в отличие от системных приложений, их можно использовать только для проверки разрешений для любого приложения или для самого приложения, а также для запуска или завершения определенных операций. Более того, не все операции фактически доступны из этого класса Java. **Диспетчер операций приложений** содержит все необходимые константы, такие как [_OP\_*_](#op-constants), `OPSTR_*`, [_MODE\_*_](#mode-constants), которые описывают код операции, строку операции и режим работы соответственно. Он также содержит необходимые структуры данных, такие как [**PackageOps**](#packageops) и **OpEntry**. **PackageOps** держит **OpEntry** длч пакета, и **OpEntry**, как следует из названия, описывает каждую операцию. Под влиянием, **AppOpsManager** вызывает службу **AppOpsService** выполнять любую реальную работу.

Служба [**AppOpService**][5] полностью скрыта для пользовательских приложений, но доступна для системных. Как видно на рисунке, это класс, который выполняет фактическое управление. Он содержит такие структуры данных, как **операции** для хранения базовой информации о пакете и **операцию** что похоже на **OpEntry** из **AppOpsManager**. Он также имеет **оболочку** которая на самом деле является исходным кодом инструмента командной строки_appops_. Он записывает или считывает конфигурации из [/data/system/appops.xml](#appops-xml). Системные службы вызывают **AppOpsService**, чтобы узнать, какие приложения разрешены, а какие не разрешены, а **AppOpsService** определяет эти разрешения путем анализа `/data/system/appops.xml`. Если пользовательские значения не заданы в _appops.xml_, он возвращает режим по умолчанию, доступный в **AppOpsManager**.


## Диспетчер операций приложений
[Диспетчер операций][4], он же диспетчер операций приложения. Он содержит в себе различные константы и классы для редактирования операций приложений. Официальную документацию можно найти [здесь][11].

### Константы OP_*
`OP_*` – целые константы, начинающиеся с цифры `0`. `OP_NONE` означает, что операции не определены, тогда как `_NUM_OP` обозначает количество операций, определенных в префиксе `OP_*`.  Обозначает каждую операцию. Но эти операции не обязательно должны быть уникальными. Фактически, есть много операций, которые на самом деле являются одной операцией, обозначенной несколькими константами `OP_*` (возможно для будущего использования). Надстройки могут определять свои собственные операции в зависимости от своих требований. MIUI – одна из известных надстроек, которая умеет это делать.

_Краткий обзор `OP_*`:_
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

Уникальность операции определяется значением [`sOpToSwitch`][7]. Оно соотносит каждую операцию с другой операцией или с собой (если это уникальная операция). Например, `OP_FINE_LOCATION` и `OP_GPS` соотносится с `OP_COARSE_LOCATION`.

Каждая операция имеет личное имя, которое описывается значением [`sOpNames`][10]. Эти имена обычно совпадают с именами констант без префикса `OP_`. Некоторые операции также имеют публичные имена, которые описываются `sOpToString`. Например, `OP_COARSE_LOCATION` имеет публичное имя **android:coarse_location**.

По мере постепенного процесса перемещения разрешений в операции приложений уже существует множество разрешений, определенных для некоторых операций. Эти разрешения сопоставляются с [`sOpPerms`][8]. Например, разрешение **android.Manifest.permission.ACCESS_COARSE_LOCATION** сопоставлено с `OP_COARSE_LOCATION`. Некоторые операции могут не иметь связанных разрешений, которые имеют значения `null`.

Как описано в предыдущем разделе, операции, настроенные для приложения, хранятся в [/data/system/appops.xml](#appops-xml). If an operation is not configured, then whether system will allow that operation is determined from [`sOpDefaultMode`][9]. В нем отображается _режим по умолчанию_ для каждой операции.

### Константы MODE_*
Константы `MODE_*`, а также целочисленные константы, начинаются с цифры `0`. Эти константы назначаются каждой операции, описывающей, авторизовано ли приложение для выполнения этой операции. Эти режимы обычно имеют связанные имена, такие как **allow** для `MODE_ALLOWED`, **ignore** для `MODE_IGNORED`, **deny** для `MODE_ERRORED` (довольно мизономер), **default** для `MODE_DEFAULT` и **foreground** для `MODE_FOREGROUND`.

_Режимы по умолчанию:_
``` java
/**
 * данному вызывающему абоненту разрешено выполнять данную операцию.
 */
public static final int MODE_ALLOWED = 0;
/**
 * the given caller is not allowed to perform the given operation, and this attempt should
 * <em>silently fail</em> (it should not cause the app to crash).
 */
public static final int MODE_IGNORED = 1;
/**
 * the given caller is not allowed to perform the given operation, and this attempt should
 * cause it to have a fatal error, typically a {@link SecurityException}.
 */
public static final int MODE_ERRORED = 1 << 1;  // 2
/**
 * the given caller should use its default security check. This mode is not normally used
 */
public static final int MODE_DEFAULT = 3;
/**
 * Special mode that means "allow only when app is in foreground."
 */
public static final int MODE_FOREGROUND = 1 << 2;
```

Besides these default modes, vendors can set custom modes such as `MODE_ASK` (with the name **ask**) which is actively used by MIUI. MIUI also uses some other modes without any name associated with them.


### Операции пакетов
**AppOpsManager.PackageOps** – это структура данных для хранения всех **OpEntry** пакета. Проще говоря, она хранит все настроенные операции для пакета.

``` java
public static class PackageOps implements Parcelable {
  private final String mPackageName;
  private final int mUid;
  private final List<OpEntry> mEntries;
  ...
}
```
Как видно выше, в нем хранятся все **OpEntry** пакета, а также соответствующее имя пакета и его идентификатор пользователя ядра.


### OpEntry
**AppOpsManager.OpEntry** – это структура данных, в которой хранится одна операция для любого пакета.

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
Здесь:
- `mOp`: обозначает одну из констант [`OP_*`](#op-constants).
- `mRunning`: выполняются ли операции (т. е, операция началась, но еще не завершена). Не все операции можно запустить или завершить таким образом.
- `mMOde`: одна из констант [`MODE_*`](#mode-constants).
- `mAccessTimes`: хранит все доступные времена принятия
- `mRejectTimes`: хранит все доступные времена отклонения
- `mDurations`: все доступные длительности доступа, выполняемые с помощью `mRunning` сообщат вам, как долго приложение выполняет определенную операцию.
- `mProxyUids`: документация не найдена
- `mProxyPackageNames:` документация не найдена

### Использование
Список задач

## Служба AppOpsService
Список задач

## appops.xml

Latest `appops.xml` has the following format: (This DTD is made by me and by no means perfect, has compatibility issues.)

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

Приведенные ниже инструкции следуют точному порядку, указанному выше:
* `app-ops`: корневой элемент. Оно может содержать любое количество `pkg` или пакетов `uid`
  - `v`: (опционально, целое число) номер версии (по умолчанию: `NO_VERSION` или `-1`)
* `pkg`: информация о пакете магазинов. Оно может содержать любое количество `uid`
  - `n`: (обязательно, строка) название пакета
* Пакет `uid`: хранит пакет или информацию о пакетах
  - `n`: (обязательно, целое число) идентификатор пользователя
* `uid`: идентификатор пользователя пакета Оно может содержать любое количество `операций`
  - `n`: (обязательно, целое число) идентификатор пользователя
  - `p`: (необязательно, логический) указывает, является ли приложение приватным или системным
* `op`: операция, которая может содержать `st` или вообще ничего
  - `n`: (обязательно, целое число) имя операции в целом числе AppOpsManager.OP_*
  - `m`: (обязательно, целое число) режим операции AppOpsManager.MODE_*
* `st`: State of operation: whether the operation is accessed, rejected or running (not available on old versions)
  - `n`: (обязательно, длинное число) ключ, содержащий флаги и uid
  - `t`: (опционально, длинное число) время доступа (по умолчанию: `0`)
  - `r`: (опционально, длинное число) время отклонения (по умолчанию: `0`)
  - `d`: (опционально, длинное число) длительность доступа (по умолчанию: `0`)
  - `pp`: (опционально, строка) имя пакета прокси
  - `pu`: (опционально, целое число) uid пакета прокси

Это определение можно найти на странице [**AppOpsService**][5].

## Интерфейс командной строки appops
`appops` или `cmd appops` (в последних версиях) могут быть доступны через ADB или root. Это более простой способ получить или обновить любую операцию для пакета (при условии, что имя пакета известно). Страница справки по этой команде не требует пояснений:

```
Команды службы AppOps (appops):
help
  Напишите этот текст для получения справки.
start [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Запускает заданную операцию для определенного приложения.
stop [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Останавливает заданную операцию для определенного приложения.
set [--user <USER_ID>] <[--uid] PACKAGE | UID> <OP> <MODE>
  Устанавливает режим для конкретного приложения и операции.
get [--user <USER_ID>] <PACKAGE | UID> [<OP>]
  Возвращает режим для конкретного приложения и дополнительной операции.
query-op [--user <USER_ID>] <OP> [<MODE>]
  Print all packages that currently have the given op in the given mode.
reset [--user <USER_ID>] [<PACKAGE>]
  Reset the given application or all applications to default modes.
write-settings
  Immediately write pending changes to storage.
read-settings
  Read the last written settings, replacing current state in RAM.
options:
  <PACKAGE> an Android package name or its UID if prefixed by --uid
  <OP>      an AppOps operation.
  <MODE>    one of allow, ignore, deny, or default
  <USER_ID> the user id under which the package is installed. If --user is not
            specified, the current user is assumed.
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
