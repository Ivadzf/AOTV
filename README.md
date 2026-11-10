<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/readme/logo-escuro.png">
  <img src="docs/readme/logo-claro.png" alt="Logo do AOTV" width="112">
</picture>

# AOTV

**Filmes, séries e TV ao vivo no celular e na televisão.**

Baseado no JOTV (Apache 2.0), de LucasR0cha.

Um app só para celular, tablet, Android TV, LG webOS, Samsung Tizen e Windows.<br>
O catálogo vem do TMDB, em português. Os vídeos vêm dos addons que você escolhe.

[![Baixar para Android](https://img.shields.io/badge/Android-baixar%20o%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../releases/latest)
[![Baixar para LG webOS](https://img.shields.io/badge/LG%20webOS-baixar%20o%20IPK-A50034?style=for-the-badge&logo=lg&logoColor=white)](../../releases/latest)
[![Baixar para Samsung Tizen](https://img.shields.io/badge/Samsung%20Tizen-baixar%20o%20WGT-1428A0?style=for-the-badge&logo=samsung&logoColor=white)](../../releases/latest)
[![Baixar para Windows](https://img.shields.io/badge/Windows-baixar%20o%20instalador-0078D4?style=for-the-badge&logo=windows&logoColor=white)](../../releases/latest)

[Novidades](#novidades) · [Na TV](#na-tv) · [No celular](#no-celular) · [No Windows](#no-windows) · [Tudo o que o app faz](#tudo-o-que-o-app-faz) · [Instalação](#instalação) · [Para desenvolvedores](#para-desenvolvedores)

</div>

<p align="center">
  <img src="docs/readme/vitrine.jpg" alt="AOTV na TV e no celular, com a Início em destaque" width="100%">
</p>

## O que é o AOTV

O AOTV junta num lugar só tudo o que você assiste. Capas, sinopses, elenco, trailers e notas vêm do
**TMDB**, sempre em português. Os vídeos vêm dos **addons no padrão Stremio** que você instala, e o
app escolhe sozinho a melhor fonte para o seu gosto (dublado ou legendado, até a qualidade que você
definir). Na TV tudo funciona pelo controle remoto, no celular pelo toque, e cada pessoa da casa tem o
próprio perfil com lista, histórico e progresso.

O AOTV não hospeda nem distribui vídeo nenhum. Leia o [aviso legal](#aviso-legal).

## Novidades

### Coleções no formato do Nuvio

Pastas temáticas na Início, como Marvel, Pixar, uma franquia, um canal de streaming ou a obra de um
diretor. Cada pasta junta catálogos de addon e listas do TMDB e abre com uma aba por fonte e a aba
**Tudo**, que intercala as fontes sem repetir título. No celular dá para criar e editar tudo. Na TV as
coleções chegam pela **conta Nuvio**, por **link** ou **colando o JSON**, que é o mesmo arquivo do
Nuvio, então entra e sai igual.

<p align="center">
  <img src="docs/readme/tv-colecao-inicio.jpg" alt="Fileira de coleção na Início da TV com as pastas Marvel, Pixar, Star Wars e Netflix" width="100%">
</p>

<p align="center">
  <img src="docs/readme/tv-colecao-pasta.jpg" alt="Pasta Marvel aberta na TV com as abas Tudo, Marvel Studios e Vingadores" width="100%">
</p>

### Fontes que se explicam

Cada versão do vídeo vira um cartão com resolução, HDR, Dolby Vision, Atmos, canais de áudio e
idioma (Dublado, Legendado, Dual áudio). O app testa as fontes antes de tocar, marca a **melhor
opção**, junta o mesmo arquivo achado em addons diferentes e passa para a próxima quando uma falha.

<p align="center">
  <img src="docs/readme/tv-fontes.jpg" alt="Lista de fontes na TV com selos 4K, HDR10, Dolby Atmos e Dual áudio" width="100%">
</p>

### TV ao vivo

Listas **M3U**, contas **Xtream** e addons com canais, com guia de programação **XMLTV**,
categorias, favoritos, recentes e busca de canal. No player, as setas (ou CH+ e CH-) trocam de canal
e o OK mostra o que está passando.

<p align="center">
  <img src="docs/readme/tv-ao-vivo.jpg" alt="Grade de canais ao vivo na TV com categorias" width="100%">
</p>

### E mais

| | |
|---|---|
| **Início do seu jeito** | Ligue, desligue, reordene e renomeie cada fileira, escolha de onde vem o destaque e transforme catálogos de addon em fileiras. Dá até para desligar o catálogo AOTV e deixar só os seus addons. |
| **Contas Stremio e Nuvio** | Entre por e-mail ou pelo QR code e traga addons, Minha Lista, o que você estava assistindo e as chaves de debrid. O QR é gerado no próprio aparelho. |
| **Debrid** | Real-Debrid, TorBox e Premiumize. Fonte de torrent que já está pronta no debrid vira link direto e toca na hora, com um raio na lista indicando o que está pronto. |
| **Legenda no seu idioma** | 53 idiomas, começando no português do Brasil, e a opção de a legenda já entrar ligada. O app prefere a legenda do próprio vídeo e completa com o OpenSubtitles e com as legendas dos addons. |
| **Torrent direto no celular** | O Android tem motor P2P próprio e o vídeo começa enquanto baixa. Na TV o torrent toca pelo debrid. |
| **Login com QR na TV** | O login por código do Stremio, do Nuvio, do Real-Debrid e do TorBox mostra o QR ao lado do código. É só apontar a câmera do celular. |
| **Player novo no celular** | Controles na base da tela, como na TV, com botões maiores para o toque e espaço certo para a fonte ampliada do sistema. |

## Na TV

Versões próprias para **LG webOS** e **Samsung Tizen**, leves e feitas para o controle remoto, e o
mesmo APK do celular na **Android TV**.

<table>
  <tr>
    <td width="50%"><img src="docs/readme/tv-inicio.jpg" alt="Início da TV com destaque em tela cheia"><br><b>Início</b><br><sub>Destaque em tela cheia que acompanha o título em foco, com trailer opcional.</sub></td>
    <td width="50%"><img src="docs/readme/tv-detalhe.jpg" alt="Detalhe de um filme na TV"><br><b>Detalhe</b><br><sub>Logo do título, nota, classificação, sinopse e o Assistir que já sabe a melhor fonte.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/readme/tv-descobrir.jpg" alt="Tela Descobrir com filtros"><br><b>Descobrir</b><br><sub>Filtros de conteúdo, ordem, gênero, ano, classificação e nota mínima.</sub></td>
    <td width="50%"><img src="docs/readme/tv-busca.jpg" alt="Busca com teclado na tela"><br><b>Busca</b><br><sub>Teclado na tela pensado para o controle e buscas recentes.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/readme/tv-perfis.jpg" alt="Seletor de perfis"><br><b>Perfis</b><br><sub>Cada perfil com a própria lista e o próprio histórico, e o perfil infantil.</sub></td>
    <td width="50%"><img src="docs/readme/tv-configuracoes.jpg" alt="Configurações da TV"><br><b>Configurações</b><br><sub>Início, Coleções, contas, TV ao vivo, addons e debrid, com o resumo ao lado.</sub></td>
  </tr>
</table>

## No celular

O mesmo visual da TV, feito para o toque, com a barra em cápsula embaixo. Tem tudo o que a TV tem e
mais o editor de coleções, downloads, Chromecast e Picture in Picture.

<p align="center">
  <img src="docs/readme/celular.jpg" alt="Cinco telas do AOTV no celular" width="100%"><br>
  <sub>Início com a coleção fixada, pasta Marvel com a aba Tudo, detalhe, lista de fontes e TV ao vivo</sub>
</p>

## No Windows

O mesmo app da TV no computador, leve e em tela cheia, com o vídeo tocando no mpv e os controles do
player iguais aos da TV. Funciona com teclado (setas, Enter e Esc) e com mouse. Para sair da tela
cheia, aperte F11 ou leve o mouse ao topo da tela, que aparecem os botões de sair da tela cheia,
minimizar e fechar.

## Tudo o que o app faz

<details open>
<summary><b>Catálogo e descoberta</b></summary>

- Início com destaque em tela cheia, trailer opcional que toca sem som depois de alguns segundos, Continuar assistindo e Novos episódios
- Fileiras de tendências, populares, nos cinemas, lançamentos do ano, trailers e gêneros, todas do TMDB
- Descobrir com filtros de conteúdo, ordem, gênero, ano, classificação e nota mínima
- Busca de filmes e séries com buscas recentes
- Detalhe com elenco, temporadas, episódios, recomendações e a franquia do filme
- Logo do título em português quando existe, notas com vírgula e classificação indicativa do Brasil

</details>

<details>
<summary><b>Fontes e player</b></summary>

- Escolha automática da melhor fonte pelo áudio preferido (dublado, legendado ou tanto faz) e pela qualidade máxima
- Teste rápido das fontes, com troca automática quando uma não responde
- Lista de fontes com selos de resolução, HDR, Dolby Vision, Atmos, DTS e idioma
- Som de outra fonte sincronizado, para assistir uma versão ouvindo o áudio dublado de outra
- Pula a abertura que você já pulou uma vez e passa para o próximo episódio sozinho
- Legenda com tamanho, fundo e atraso ajustáveis, em 53 idiomas
- Player do celular com os controles na base, como na TV, e botões grandes para o toque
- Prévia em miniatura ao arrastar a barra (celular)
- Chromecast, Picture in Picture e controle pela tela bloqueada ou pelo fone (celular)
- Download para assistir sem internet (celular)
- DNS seguro da Cloudflare nos links de vídeo, para fugir de bloqueio de operadora (celular)

</details>

<details>
<summary><b>Addons</b></summary>

- Gerenciador com ordem de prioridade, liga e desliga
- Loja com o catálogo da comunidade
- Configuração no site do addon, com QR code para configurar pelo celular e levar o link pronto para a TV
- Catálogos dos addons viram fileiras da Início
- Você decide quais addons usar. O app começa sem nenhum, no celular e na TV, e você instala pelo link do manifest, pela Loja ou trazendo da sua conta Stremio ou Nuvio

</details>

<details>
<summary><b>Coleções</b></summary>

- Fontes de addon e do TMDB (lista, franquia, estúdio, canal, descoberta com filtros, ator e direção)
- Pasta com abas e aba Tudo, ou uma fileira por fonte
- Capa, emoji, GIF animado e formato pôster, paisagem ou quadrado
- Coleção fixada no topo da Início
- Editor completo no celular, com atalhos para estúdios e canais conhecidos e busca no TMDB
- Importa o mesmo JSON do Nuvio pela conta Nuvio, por link ou colando o texto, e exporta pelo celular

</details>

<details>
<summary><b>Contas, debrid e torrent</b></summary>

- Stremio e Nuvio por e-mail ou QR, importando addons, Minha Lista, progresso e as chaves de debrid salvas na conta
- Real-Debrid (por código ou chave), TorBox e Premiumize
- Motor P2P no Android, com o vídeo tocando enquanto baixa
- Na TV o torrent toca pelo debrid

</details>

<details>
<summary><b>TV ao vivo</b></summary>

- Listas M3U, contas Xtream e addons com canais
- Guia XMLTV (inclusive compactado em .gz no celular e na LG)
- Categorias, favoritos, recentes e busca de canal
- Troca de canal pelas setas ou pelo CH+ e CH-, com o programa atual na tela
- A aba Ao vivo só aparece depois que você cadastra uma fonte

</details>

<details>
<summary><b>Perfis e família</b></summary>

- Vários perfis, cada um com Minha Lista, histórico, progresso e preferências
- Perfil infantil que só mostra animação e família e bloqueia título acima de 12 anos
- PIN dos pais para sair do infantil e mexer em perfis e ajustes
- TV ao vivo, catálogos de addon e coleções ficam fora do perfil infantil

</details>

<details>
<summary><b>Feito para cada tela</b></summary>

- Celular, tablet e Android TV no mesmo APK, com layout próprio para o controle remoto na TV
- LG webOS e Samsung Tizen em app próprio, leve e sem framework
- Windows 10 e 11 em app próprio, com a interface da TV e o player mpv
- Navegação inteira pelo controle, com foco sempre visível e retorno ao card de onde você saiu
- Temas Grafite, Azul e Verde e opção de movimento reduzido (TV)

</details>

## Instalação

Todos os arquivos ficam na página de **[Releases](../../releases/latest)**. Não precisa compilar nada.

| Aparelho | Arquivo | Precisa de |
|---|---|---|
| Celular ou tablet Android | `AOTV-v*.apk` | Android 6 ou mais novo |
| Android TV e Google TV | `AOTV-v*.apk` | Android TV 6 ou mais novo |
| TV LG | `AOTV-webOS-v*.ipk` | webOS 4 ou mais novo, em modo desenvolvedor |
| TV Samsung | `AOTV-Tizen-v*.wgt` | Tizen 5.5 ou mais novo (TVs de 2020 em diante), em modo desenvolvedor |
| Computador | `AOTV-Windows-v*-setup.exe` | Windows 10 ou 11 de 64 bits |

<details>
<summary><b>Celular ou tablet Android</b></summary>

1. Baixe o `.apk` da Release direto no aparelho.
2. Abra o arquivo. Na primeira vez o Android pede para liberar a instalação de fonte desconhecida no app que você usou para baixar (navegador ou Arquivos). Libere e confirme.

</details>

<details>
<summary><b>Android TV e Google TV (qualquer marca)</b></summary>

Vale para Sony, TCL, Philips, Hisense, Xiaomi, NVIDIA Shield, Chromecast com Google TV, Fire TV e
qualquer aparelho com Android TV. A instalação é por sideload, de um destes jeitos.

**Pelo computador, com ADB.** Na TV, libere as opções do desenvolvedor (em geral tocando várias vezes
na versão do sistema em Configurações, Sobre) e ligue a depuração de rede. No PC, com o
[Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) instalado,

```sh
adb connect <ip-da-tv>:5555
adb install AOTV-v<versão>.apk
```

**Direto na TV.** Instale um app como o Downloader ou o Send Files to TV pela loja da própria TV,
baixe o `.apk` por ele e instale.

</details>

<details>
<summary><b>TV LG (webOS)</b></summary>

1. Na TV, instale o app **Developer Mode** pela LG Content Store, entre com uma conta grátis de [developer.lge.com](https://developer.lge.com) e ligue o modo desenvolvedor e o Key Server. Anote o IP e a frase (passphrase) que aparecem na tela.
2. No computador, instale a [CLI do webOS TV](https://webostv.developer.lge.com/develop/tools/cli-installation) (precisa do Node.js) e cadastre a TV uma vez.
   ```sh
   npm install -g @webos-tools/cli
   ares-setup-device
   ```
3. Instale e abra o app.
   ```sh
   ares-install --device <nome-da-tv> AOTV-webOS-v<versão>.ipk
   ares-launch --device <nome-da-tv> com.aotv.app
   ```

O modo desenvolvedor desliga sozinho depois de alguns dias. Se o app sumir, abra o Developer Mode e
renove a sessão (o IP e a frase podem mudar).

</details>

<details>
<summary><b>TV Samsung (Tizen)</b></summary>

1. Na TV, abra a área de Apps, digite **1 2 3 4 5** no controle e ligue o modo desenvolvedor com o IP do seu computador. Reinicie a TV.
2. No computador, instale o [Tizen Studio](https://samsungtizenos.com/tools-download/) (o instalador de linha de comando basta). Ele traz o `sdb`.
3. Conecte e instale.
   ```sh
   sdb connect <ip-da-tv>:26101
   sdb install AOTV-Tizen-v<versão>.wgt
   ```

O `.wgt` sai assinado com certificado de desenvolvedor, o que basta para uso pessoal em modo
desenvolvedor. Ele não serve para a loja da Samsung.

</details>

<details>
<summary><b>Windows</b></summary>

1. Baixe o `AOTV-Windows-v<versão>-setup.exe` da Release e abra.
2. O instalador não tem assinatura digital paga, então o Windows pode avisar que protegeu o computador. Clique em **Mais informações** e depois em **Executar assim mesmo**.
3. O AOTV abre em tela cheia. F11 ou Alt+Enter alternam a tela cheia, e o mouse no topo da tela mostra os botões de sair da tela cheia, minimizar e fechar.

</details>

<details>
<summary><b>Roku, Apple TV ou outra TV sem Android, webOS ou Tizen</b></summary>

Essas não têm versão própria. Ligue um aparelho com Android TV na entrada HDMI (um Chromecast com
Google TV, um TV Box Android ou um Fire TV Stick) e instale o APK nele. A TV vira só a tela.

</details>

## Para desenvolvedores

Este repositório traz o código do app Android (celular, tablet e Android TV). As versões de LG,
Samsung e Windows saem como pacote pronto nas Releases.

<details>
<summary><b>Como o app é feito</b></summary>

```
UI (Jetpack Compose)  →  ViewModel (Hilt)  →  Repository  →  Room / Retrofit (TMDB) / addons Stremio (OkHttp)
```

- Jetpack Compose com Material 3 e `androidx.tv` para a TV. Uma árvore só de telas se adapta ao celular, ao tablet e à TV.
- Hilt para injeção de dependência e Room para perfis, Minha Lista, histórico, episódios vistos e downloads.
- Preferências por perfil em `PlatformPrefs`, inclusive a Início personalizada e as coleções.
- Media3 (ExoPlayer) com `MediaSession` e Chromecast, e libtorrent4j no motor P2P.
- Retrofit e OkHttp para o TMDB, com DNS seguro para os links de vídeo dos addons.

</details>

<details>
<summary><b>Como compilar</b></summary>

Precisa do **JDK 17** (ou do JBR do Android Studio) e do Android SDK. Crie um `local.properties` na
raiz, que não entra no Git,

```properties
sdk.dir=/caminho/para/o/Android/Sdk
```

A chave do TMDB pode ir no `~/.gradle/gradle.properties` como `TMDB_API_KEY`. Depois,

```sh
./gradlew installDebug          # instala num aparelho conectado
./gradlew assembleRelease       # APK de release em app/build/outputs/apk/release/
./gradlew testDebugUnitTest     # testes
```

Sem a `TMDB_API_KEY` o app abre pedindo a sua chave do TMDB em Configurações. O AOTV nunca vem
com chave embutida: cada pessoa usa a própria chave. A sincronia com o
Trakt liga quando `TRAKT_CLIENT_ID` e `TRAKT_CLIENT_SECRET` estão no `local.properties`. Chave do
TMDB em [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) e app do Trakt em
[trakt.tv/oauth/applications](https://trakt.tv/oauth/applications).

</details>

<details>
<summary><b>Estrutura do projeto</b></summary>

```
app/src/main/java/com/aotv/app/
├── data/
│   ├── accounts/      contas Stremio e Nuvio
│   ├── collections/   coleções no formato do Nuvio
│   ├── debrid/        Real-Debrid, TorBox e Premiumize
│   ├── live/          M3U, Xtream e guia XMLTV
│   ├── local/         Room e preferências por perfil
│   ├── remote/        TMDB, addons Stremio, legendas e DNS seguro
│   ├── repository/    biblioteca e downloads
│   └── torrent/       motor P2P
├── di/                módulos Hilt
├── domain/model/      filme, série, episódio e elenco
└── ui/
    ├── collections/   coleções, pasta e editor
    ├── common/        componentes e layout adaptável
    ├── live/          TV ao vivo
    ├── movies/        Início, detalhe, busca e player
    ├── navigation/    navegação e menu lateral da TV
    ├── settings/      configurações, addons, contas, debrid e perfis
    ├── theme/         cores e tipografia
    └── tv/            telas exclusivas da TV
```

</details>

## Aviso legal

O AOTV não hospeda, não distribui e não guarda vídeo nenhum. Ele é um player e uma interface para
addons de terceiros no protocolo Stremio, que a própria pessoa escolhe instalar. O uso e a legalidade
das fontes acessadas por esses addons são responsabilidade de quem os instala.

Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB. As capturas usam
dados públicos do TMDB, canais abertos e o filme aberto Sintel, da Blender Foundation (CC BY 3.0),
numa fonte de demonstração.

## Licença

[Apache License 2.0](LICENSE)

O instalador de Windows leva o [mpv](https://mpv.io) como programa separado, que é software livre
sob a GPL. O código do mpv fica em [mpv-player/mpv](https://github.com/mpv-player/mpv) e a build
usada vem de [zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-10-26-baa3e11d42).
