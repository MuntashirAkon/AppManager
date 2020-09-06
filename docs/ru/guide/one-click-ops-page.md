# Страница «Операции в один клик»

Эта страница появляется после нажатия на опцию **Операции в один клик** на [главной странице](./main-page.md#меню-опций). В настоящее время поддерживаемые операции включают в себя: _блокировку/разблокировку трекеров_, _блокировку компонентов_ и _отклонение операций приложений_. Больше опций будет добавлено позже.

::: details Таблица содержания
[[toc]]
:::

## Блокировка трекеров
Этот параметр можно использовать для блокировки или разблокировки рекламы или компонентов трекера для всех установленных приложений. При нажатии на эту опцию вам будет предложено выбрать, отображать ли в списке все приложения или только пользовательские приложения. Начинающим пользователям следует выбрать отображение только пользовательских приложений. После этого появится диалоговое окно с множественным выбором, в котором вы можете отменить выбор приложений, которые хотите исключить из этой операции. Нажатие на _заблокировать_ или _разблокировать_ приведет к немедленному изменению настройки.

::: warning Notice
Certain apps may not function as expected after applying the blocking. If that is the case, remove blocking rules all at once or one by one using the corresponding [App Details][1] page.
:::

_Смотрите также: [Как разблокировать компоненты трекера, заблокированные с помощью операций в один клик или пакетных операций?](../faq/app-components.md#как-разбnокировать-компоненты-трекера-забnокированные-с-помощью-операций-в-один-кnик-иnи-пакетных-операций)_

## Блокировка компонентов…
This option can be used to block certain app components denoted by the signatures. App signature is the full name or partial name of the components. For safety, it is recommended that you should add a `.` (dot) at the end of each partial signature name as the algorithm used here chooses all matched components in a greedy manner. You can insert more than one signature in which case all signatures have to be separated by spaces. Similar to the option above, there is an option to apply blocking to system apps as well.

::: danger Caution
If you are not aware of the consequences of blocking app components by signature(s), you should avoid usinig this setting as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

## Отклонение операций приложения…
Эта опция может использоваться для блокировки определенных [операций](../tech/AppOps.md) всех или выбранных приложений. Вы можете вставить несколько констант операций приложения, разделенных пробелами. Невозможно заранее узнать все константы операций приложения, поскольку они различаются от устройства к устройству и от ОС к ОС. Чтобы найти нужную константу операции приложения, откройте вкладку _Операции приложения_ на странице [О приложении][1]. Константы представляют собой целые числа, заключенные в квадратные скобки рядом с каждым именем операции приложения.

::: danger Caution
Unless you are well informed about app ops and the consequences of blocking them, you should avoid using this feature as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

[1]: ./app-details-page.md
