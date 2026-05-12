# FlyerMediaPlayer - Digital Signage para Android TV 🇧🇷

O FlyerMediaPlayer é uma solução robusta de sinalização digital (Digital Signage) projetada para Android TV (TV Boxes). Ele automatiza a reprodução de flyers promocionais e conteúdo de entretenimento (como vídeos musicais ou partidas de futebol) diretamente de um pendrive.

## Funcionalidades

- **Ciclo de Reprodução Inteligente**: Alterna automaticamente entre 1 flyer da casa e 2 vídeos aleatórios de entretenimento.
- **Limite de 8 Minutos**: Vídeos de entretenimento são limitados automaticamente a 8 minutos cada para garantir que os flyers promocionais apareçam com frequência, enquanto os flyers sempre tocam até o fim.
- **Shuffle Inteligente**: Implementa uma fila de reprodução aleatória sem repetição para flyers e vídeos. Nenhum conteúdo é repetido até que toda a lista de reprodução tenha sido exibida.
- **Inicialização Automática (Boot)**: O aplicativo inicia automaticamente quando o dispositivo Android TV é ligado.
- **Plug-and-Play USB**:
  - Coloque seus flyers contendo "MARIA" no nome na **raiz** do pendrive.
  - Coloque todos os outros vídeos de entretenimento dentro de uma pasta chamada `VIDEOS` (ou `videos`).
- **Tela Sempre Ligada**: Impede que a tela escureça ou entre em modo de suspensão durante a reprodução.

## Como Funciona

1. **Escaneamento**: Ao iniciar, o app escaneia o pendrive conectado.
2. **Flyers**: Identifica arquivos com "MARIA" no nome na raiz do USB.
3. **Vídeos**: Identifica todos os arquivos de vídeo dentro da pasta `/VIDEOS`.
4. **Lógica**: 
   - Toca 1 Flyer (Duração total).
   - Toca 2 Vídeos Aleatórios (Máximo de 8 minutos cada).
   - Repete o ciclo.

## Detalhes Técnicos

- Desenvolvido com **Kotlin** e **Android Media3 (ExoPlayer)**.
- Target SDK: **35**.
- Requer permissões `READ_EXTERNAL_STORAGE` ou `READ_MEDIA_VIDEO`.

---

# FlyerMediaPlayer - Digital Signage for Android TV EN

FlyerMediaPlayer is a robust digital signage solution designed for Android TV (TV Boxes). It automates the playback of promotional flyers and entertainment content (like music videos or football matches) directly from a USB drive.

## Features

- **Intelligent Playback Cycle**: Automatically alternates between 1 house flyer and 2 random entertainment videos.
- **8-Minute Limit**: Entertainment videos are automatically limited to 8 minutes each to ensure promotional flyers appear frequently, while flyers always play to completion.
- **Smart Shuffling**: Implements a non-repeating shuffle queue for both flyers and videos. No content is repeated until the entire playlist has been seen.
- **Automatic Boot Start**: The app automatically launches when the Android TV device is powered on.
- **USB Plug-and-Play**:
  - Place your flyers containing "MARIA" in the name at the **root** of the USB drive.
  - Place all other entertainment videos inside a folder named `VIDEOS` (or `videos`).
- **Always-On Display**: Prevents the screen from dimming or sleeping during playback.

## How it Works

1. **Scan**: On startup, the app scans the connected USB drive.
2. **Flyers**: It identifies files with "MARIA" in their name on the USB root.
3. **Videos**: It identifies all video files inside the `/VIDEOS` folder.
4. **Logic**: 
   - Play 1 Flyer (Full length).
   - Play 2 Random Videos (Max 8 minutes each).
   - Repeat.

## Technical Details

- Built with **Kotlin** and **Android Media3 (ExoPlayer)**.
- Target SDK: **35**.
- Requires `READ_EXTERNAL_STORAGE` or `READ_MEDIA_VIDEO` permissions.

---
