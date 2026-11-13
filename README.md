<div align="center">

# 🛡️ BPS — Registros de Passagem de Posto

### Gestão operacional, comunicação e monitoramento de equipamentos de portaria

Organize a troca de turnos, padronize registros e facilite a comunicação entre os profissionais de portaria.

[📲 **BAIXAR APLICATIVO ANDROID (APK)**](BPS_Facilities.apk)

</div>

---

## 📌 Sobre o projeto

O **BPS — Registros de Passagem de Posto** é um aplicativo Android desenvolvido para apoiar a rotina operacional de portarias, tornando a passagem de turno mais organizada, prática e eficiente.

A proposta é ajudar os profissionais a registrar as informações importantes do expediente e transmiti-las ao próximo funcionário, contribuindo para a continuidade das atividades e o acompanhamento das condições dos equipamentos.

## 🎯 Objetivo

Digitalizar e simplificar o processo de passagem de posto, centralizando informações relevantes e promovendo uma comunicação mais clara entre os profissionais responsáveis pelos turnos.

## ✨ Funcionalidades

- **Passagem de posto:** organização das informações transmitidas entre os turnos.
- **Identificação dos funcionários:** registro dos profissionais responsáveis pela entrega e pelo recebimento do posto.
- **Registro de ocorrências:** documentação de situações relevantes durante o expediente.
- **Monitoramento de equipamentos:** registro das condições dos equipamentos utilizados na portaria.
- **Comunicação facilitada:** preparação de mensagens para compartilhar as informações da passagem de posto.
- **Padronização dos registros:** informações estruturadas para facilitar a leitura e reduzir falhas de comunicação.

> **Observação:** as funcionalidades descritas devem ser conferidas com a versão atual do aplicativo.

## 📲 Download do aplicativo

<div align="center">

### BPS Facilities para Android

**[⬇️ Clique aqui para baixar o APK](BPS_Facilities.apk)**

</div>

O botão acima utiliza o endereço de download da versão mais recente publicada em **Releases** neste repositório.

### Como instalar

1. Abra o link de download em um dispositivo Android.
2. Baixe o arquivo `BPS_Facilities.apk`.
3. Abra o arquivo baixado.
4. Se o Android solicitar, autorize temporariamente a instalação de aplicativos dessa fonte nas configurações do dispositivo.
5. Siga as instruções exibidas na tela para concluir a instalação.

**Segurança:** instale o APK somente se confiar na origem do arquivo. O Android pode exibir um aviso para aplicativos instalados fora da Google Play Store.

### Como publicar o APK para ativar o botão de download

Para que o botão funcione para todos, publique o arquivo `BPS_Facilities.apk` em uma versão do GitHub:

1. Acesse a área **Releases** do repositório.
2. Selecione **Create a new release**.
3. Crie uma tag de versão, por exemplo, `v1.0.0`.
4. Adicione um título, como `BPS Facilities v1.0.0`.
5. Anexe o arquivo `BPS_Facilities.apk` à versão.
6. Publique a release.

Depois de publicar uma release com esse nome de arquivo, o botão de download deste README apontará para o APK da versão mais recente.

## 💼 Benefícios esperados

- Mais organização na troca de turnos.
- Melhor comunicação entre os profissionais.
- Facilidade para registrar ocorrências.
- Maior controle das informações sobre os equipamentos.
- Padronização do processo de passagem de posto.
- Apoio à continuidade das atividades operacionais.

## 🧭 Visão do projeto

O BPS busca transformar uma atividade operacional cotidiana em um processo mais organizado, padronizado e eficiente, utilizando a tecnologia para apoiar o trabalho dos profissionais de portaria.

## 👨‍💻 Informações do projeto

| Campo | Informação |
| --- | --- |
| **Nome** | BPS — Registros de Passagem de Posto |
| **Aplicativo Android** | BPS Facilities |
| **Categoria** | Gestão operacional e controle de informações |
| **Plataforma** | Android |
| **Repositório** | [GitHub — BPS Registros de Passagem de Posto](https://github.com/medeiroshigor0709-byte/BPS-Registros-de-passagem-de-posto) |

---

## 🤖 Compilação Automática e Download via GitHub Actions

O projeto está configurado com o workflow `.github/workflows/build-apk.yml` para gerar o APK em modo **Release** (`Passagem-de-Ponto.apk`).

### Como baixar o APK pela aba Actions

1. Acesse a aba **Actions** do repositório no GitHub.
2. Selecione o workflow **Build APK Android** (executado automaticamente a cada `push` na branch principal `main`/`master` ou manualmente pelo botão **Run workflow**).
3. Na execução concluída, role até a seção **Artifacts** e baixe **`APK-Passagem-de-Ponto`** (disponível por **30 dias**).
4. Extraia o arquivo compactado para obter o instalável **`Passagem-de-Ponto.apk`**.

### Como configurar os GitHub Secrets para Assinatura Release

Sem os Secrets abaixo, o workflow gera automaticamente um **APK Release de teste** assinado com uma chave temporária (sem expor credenciais no código). Para assinar com sua chave definitiva de produção:

1. Gere uma keystore local com o alias obrigatório `upload`:
   ```bash
   keytool -genkeypair -v -keystore bps-release.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Converta a keystore para Base64 (sem quebras de linha):
   ```bash
   base64 -w 0 bps-release.jks
   ```
   *(No macOS: `base64 -i bps-release.jks | tr -d '\n'`)*
3. No GitHub, acesse **Settings → Secrets and variables → Actions → New repository secret** e cadastre:
   - `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 da sua `.jks`
   - `ANDROID_KEYSTORE_PASSWORD`: senha da keystore (`storePassword`)
   - `ANDROID_KEY_PASSWORD`: senha da chave (`keyPassword`)

> **Segurança:** Nunca envie arquivos `.jks`, `.keystore` ou senhas para o repositório. O `.gitignore` já bloqueia esses arquivos.

### Como publicar uma Release automática com tag (`v1.0.0`)

Ao enviar uma tag de versão no formato `vX.Y.Z`, o workflow compila o APK, cria a Release sem duplicação e anexa automaticamente o arquivo `Passagem-de-Ponto.apk`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

<div align="center">

**BPS — Mais organização, comunicação e eficiência na passagem de posto.**

</div>
