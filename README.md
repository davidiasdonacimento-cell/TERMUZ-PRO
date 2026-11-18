# TERMUZ PRO

Terminal Android independente com interface azul/ciano e ambiente Ubuntu ARM64 via PRoot (sem root e sem depender do aplicativo Termux).

**Desenvolvedor:** Davi Dias  
**Versão:** 0.2.0  
**Application ID:** `com.davidias.termuzpro`

## Recursos previstos nesta versão
- Interface Android nativa em Java.
- Shell Android de fallback.
- Instalador de Ubuntu Base 24.04 ARM64.
- PRoot ARM64 integrado ao APK pelo workflow.
- APT, Python 3, pip e Git instalados no primeiro uso.
- Validação SHA-256 do rootfs antes da extração.

## Como usar
1. Abra **Actions** e execute o workflow **TERMUZ PRO Android APK** (ou aguarde o build disparado por push).
2. Baixe o artefato `TERMUZ-PRO-0.2.0-android-arm64`.
3. Instale o APK de depuração em um Android ARM64.
4. Abra TERMUZ PRO e toque em **INSTALAR LINUX**.
5. Aguarde o Ubuntu e os pacotes serem instalados. É necessária internet e espaço livre adicional.

## Build
O workflow prepara Java 17, Android SDK 35, baixa o PRoot ARM64 com hash fixado e executa `gradle :app:assembleDebug`.

## Limitações conhecidas
- O APK é de depuração; não é uma versão assinada para publicação na Play Store.
- O ambiente PRoot não concede root ao Android nem substitui o kernel do aparelho.
- O primeiro uso depende da disponibilidade dos servidores Ubuntu e de compatibilidade do aparelho.
- O código precisa ser compilado e testado em aparelho físico antes de ser considerado validado.

Consulte [BUILD-STATUS.md](BUILD-STATUS.md) e [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).


## Estado desta publicação
O código-fonte foi publicado na branch `main`. A geração do APK depende do workflow GitHub Actions; não marque o APK como pronto até existir uma execução concluída e um artefato disponível.
