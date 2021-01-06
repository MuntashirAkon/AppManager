---
sidebarDepth: 2
---
# ADB sobre TCP

Muitas funções exclusivas para root ainda podem ser usadas ativando o ADB sobre TCP. Para isso, um PC ou Mac é necessário com ferramentas de plataforma Android instaladas, e um telefone Android com opções de desenvolvedor e Depuração USB ativadas.

::: tip Dica
Usuários de root Se a permissão de superusuário tiver sido concedida ao App Manager, ele já pode executar o código privilegiado sem qualquer problema. **Portanto, os usuários de root não precisam ativar o ADB sobre TCP.** Se você ainda quiser usar o ADB sobre TCP, você deve revogar a permissão de superusuário para o App Manager e reiniciar o seu dispositivo. Você pode ver a mensagem _trabalhando no modo ADB_ sem reiniciar, mas isso não é inteiramente verdade. O servidor (usado como interface entre o sistema e o App Manager) ainda está sendo executado no modo root. Este é um problema conhecido e será corrigido em uma versão futura do App Manager.
:::

_Veja também: [FAQ: ADB sobre TCP][faq_aot]_

::: details Tabela de Conteúdos
[[toc]]
:::

## 1. Ativar opções de desenvolvedor

### 1.1. Localização das opções de desenvolvedor
**Opções de desenvolvedor** está localizado nas **Configurações** do Android, seja diretamente perto da parte inferior da página (na maioria das ROMs) ou em algumas outras configurações, como **Sistema** (Lineage OS, Asus Zenfone 8.0+), **Sistema** > **Avançado** (Google Pixel), **Configurações Adicionais** (Xiaomi MIUI, Oppo ColorOS), **Mais Configurações** (Vivo FuntouchOS), **Mais** (ZTE Nubia). Ao contrário de outras opções, ele não é visível até ser explicitamente ativada pelo usuário. Se as opções de desenvolvedor forem ativadas, você pode usar a barra de busca nas **Configurações** do Android para localizá-la também.

### 1.2. Como ativar as opções de desenvolvedor
Esta opção também está disponível nas **Configurações** do Android, mas, assim como a localização das opções de desenvolvedor, ela também difere de dispositivo para dispositivo. Mas, em geral, você tem que encontrar o **Número de compilação** (ou **Versão do MIUI** para ROMs MIUI e **Versão do software** para Vivo FuntouchOS, **Versão** para Oppo ColorOS) e tocá-lo pelo menos 7 (sete) vezes até que você finalmente obtenha uma mensagem dizendo _Você agora é um desenvolvedor_ (você pode ser solicitado a inserir um pin/senha/padrão ou resolver captchas neste momento). Na maioria dos dispositivos, ela está localizada na parte inferior da página de configurações, dentro de **Sobre o telefone**. Mas a melhor maneira de encontrá-lo é usar a barra de busca.

## 2. Ativar a depuração USB
Depois de [localizar as opções de desenvolvedor](#_1-1-localizacao-das-opcoes-de-desenvolvedor), ative a **Opção de desenvolvedor** (se já não estiver). Depois disso, role um pouco para baixo até encontrar a opção **Depuração USB**. Use o botão de alternar no lado direito para ativá-la. Neste ponto, você pode obter um alerta onde você pode ter que clicar _OK_ para realmente ativá-la. Você também pode ter que ativar algumas outras opções, dependendo do fornecedor do dispositivo e ROM. Aqui estão alguns exemplos:

### 2.1. Xiaomi (MIUI)
Ative a **Depuração USB (configurações de segurança)** também.

### 2.2. Huawei (EMUI)
Ative **Permitir a depuração do ADB apenas no modo de carga** também. Ao se conectar ao seu PC ou Mac, você pode obter um alerta dizendo **Permitir acesso aos dados do dispositivo?** nesse caso clique em **SIM, PERMITIR ACESSO**.

::: tip Aviso
Muitas vezes o modo de **Depuração USB** pode ser desativado automaticamente pelo sistema. Se for esse o caso, repita o procedimento acima.
:::

### 2.3. LG
Certifique-se de que você tem a opção **USB tethering** ativada.

### 2.4. Solucionando problemas
No caso da **Depuração USB** está acinzentada, você pode fazer o seguinte:
1. Certifique-se de ativar a depuração USB antes de conectar seu telefone ao PC ou Mac via cabo USB
2. Ative o USB tethering depois de se conectar ao PC ou Mac via cabo USB
3. (Para a Samsung) Se o dispositivo está executando KNOX, você pode ter que seguir alguns passos adicionais. Consulte as documentações oficiais ou consulte o suporte para assistência adicional


## 3. Configurar o ADB no PC ou Mac
Para ativar o ADB sobre o TCP, você tem que configurar o ADB no seu PC ou Mac. **_Os usuários do Lineage OS podem pular para a [seção 4.1](#_4-1-lineage-os)._**

### 3.1. Windows
1. Baixe a versão mais recente do [Android SDK Platform-Tools][sdk_pt_win] para Windows
2. Extrair o conteúdo do arquivo zip em qualquer diretório (como `C:\adb`) e navegue para esse diretório usando o _Explorador de arquivos_
3. Abra o **Prompt de Comando** ou o **PowerShell** deste diretório. Você pode fazê-lo manualmente a partir do menu inicial ou segurando `Shift` e clicando com o botão direito do mouse dentro do diretório no _Explorador de Arquivos_ e, em seguida, clicando em _Abrir janela de comando aqui_ ou em _Abrir janela do PowerShell aqui_ (dependendo do que você instalou). Agora você pode acessar o ADB digitando `adb` (Prompt de Comando) ou `./adb` (PowerShell). Não feche essa janela ainda

### 3.2. macOS
1. Baixe a versão mais recente do [Android SDK Platform-Tools][sdk_pt_mac] para macOS
2. Extraia o conteúdo do arquivo zip em um diretório clicando nele. Depois disso, navegue até esse diretório usando o _Finder_ e localize o `adb`
3. Abra o **Terminal** usando o _Launchpad_ ou o _Spotlight_ e arraste e solte o `adb` a partir da janela do _Finder_ para a janela do _Terminal_. Não feche a janela do _Terminal_ ainda

::: tip Dica
Se você não tem medo de usar linhas de comando, aqui está uma linha única:
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-darwin.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
Depois disso, você pode simplesmente digitar `./adb` na mesma janela do _Terminal_ para acessar o ADB.
:::

### 3.3. Linux
1. Abra seu emulador de terminal favorito. Na maioria dos GUI-distros, você pode abri-lo segurando `Control`, `Alter` e `T` ao mesmo tempo
2. Execute o seguinte comando:
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-linux.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
3. Se for bem sucedido, você pode simplesmente digitar `./adb` na _mesma_ janela do emulador de terminal ou digitar `~/Downloads/platform-tools/adb` em qualquer emulador de terminal para acessar o ADB.

## 4. Configure o ADB sobre TCP

### 4.1. Lineage OS
Os usuários do LINEAGE OS (ou seus derivados) podem ativar diretamente o ADB sobre TCP usando as opções de desenvolvedor. Para ativar isso, vá para as **Opções de desenvolvedor**, role para baixo até encontrar **ADB sobre Network**. Agora, use o botão alternar no lado direito para ativá-la e siga para a [seção 4.3](#_4-3-ative-o-modo-adb-no-app-manager).

### 4.2. Ative o ADB sobre TCP via PC ou Mac
Para outras ROMs, você pode fazer isso usando o prompt de comando/PowerShell/emulador de terminal que você abriu na etapa 3 da seção anterior. Nesta seção, eu vou usar `adb` para denotar `./adb`, `adb` ou qualquer outro comando que você precisa usar com base em sua plataforma e software na seção anterior.
1. Conecte o dispositivo ao seu PC ou Mac usando um cabo USB. Para alguns dispositivos, é necessário ligar o _Modo de transferência de arquivos (MTP)_ também
2. Para confirmar que tudo está funcionando como esperado, digite `adb devices` no seu terminal. Se o seu dispositivo estiver conectado com sucesso, você verá algo assim:
    ```
    List of devices attached
    xxxxxxxx  device
    ```
    ::: tip Aviso
    Em alguns telefones Android, um alerta será exibido com a mensagem **Permitir depuração USB** nesse caso, marque _Sempre permita a partir deste computador_ e clique em **Permitir**.
    :::
3. Finalmente, execute o seguinte comando para ativar o ADB sobre TCP:
    ``` sh
    adb tcpip 5555
    ```
    ::: danger Perigo
    Você não pode desativar as opções de desenvolvedor ou a depuração USB depois de ativar o ADB sobre TCP.
    :::

### 4.3. Ative o modo ADB no App Manager
Depois de ativar o ADB sobre TCP (nas subseções anteriores), abra o App Manager (AM). Você deve ver a mensagem **trabalhando no modo ADB** na parte inferior. Se não, remova o AM dos recentes e abra o AM novamente a partir do launcher. Se você ver a mensagem, você pode com segurança [parar o ADB sobre TCP](#_4-4-parar-o-adb-sobre-tcp).

::: tip Aviso
Em alguns telefones Android, o cabo USB pode precisar ser conectado ou desconectado do PC para que ele funcione.
:::

::: warning Aviso
O ADB sobre TCP será desativado após uma reinicialização. Nesse caso, você tem que seguir a [seção 4.2](#_4-2-ative-o-adb-sobre-tcp-via-pc-ou-mac) novamente.
:::

### 4.4. Parar o ADB sobre TCP
Para garantir a segurança do dispositivo, você deve parar o ADB sobre TCP logo após o AM detectá-lo. Para fazer isso, conecte seu dispositivo ao seu PC ou Mac e execute o seguinte comando:
```sh
adb kill-server
```
Substitua `adb` por `./adb` ou qualquer outro comando que você teve que usar em etapas anteriores.

Para o Lineage OS, você pode desligar o **ADB sobre Network** nas opções de desenvolvedor.

## 5. Referências
1. [Como instalar o ADB no Windows, macOS, e Linux](https://www.xda-developers.com/install-adb-windows-macos-linux)
2. [Android Debug Bridge (adb)](https://developer.android.com/studio/command-line/adb)
3. [Como corrigir a depuração USB acinzentada?](https://www.syncios.com/android/fix-usb-debugging-grey-out.html)

[faq_aot]: ../faq/adb.md
[sdk_pt_win]: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
[sdk_pt_mac]: https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
