# 🎬 Flyer Media Player

**Flyer Media Player** é um reprodutor de mídias desenvolvido especialmente para **TV Boxes Android, TVs e dispositivos móveis**, ideal para exibição autônoma de cartazes/flyers de eventos, atrações musicais e vídeos institucionais em bares, casas noturnas e estabelecimentos comerciais.

> 💡 **Modo de Uso Simples:** Configure a palavra-chave dos vídeos do seu estabelecimento, coloque os vídeos aleatórios na pasta `VIDEOS` do Pen Drive e o aplicativo assume o controle total da reprodução em ciclo contínuo!

---

## 📁 Como Organizar o Pen Drive

1. **💾 Raiz do Pen Drive (`/`):** Coloque os vídeos dos seus **Flyers/Propagandas Principais** soltos no Pen Drive com a palavra-chave no nome (ex: `MARIA_SEXTA.mp4`, `MARIA_PROMO.mp4`).
2. **📁 Pasta `VIDEOS`:** Crie uma pasta chamada **`VIDEOS`** na raiz do Pen Drive e coloque dentro dela todos os vídeos aleatórios (shows, clipes e comerciais secundários).

---

## 🌟 Principais Recursos

- **🔍 Varredura Automática de Pen Drive (USB OTG):** Detecta e organiza automaticamente vídeos armazenados no Pen Drive USB montado no dispositivo.
- **🏷️ Suporte a Múltiplas Palavras-chave:** Suporta múltiplas palavras-chave separadas por vírgula para identificar os **Vídeos Principais (Flyers)** (ex: `MARIA, PROMO, SEXTA`).
- **🔀 Rotação Inteligente de Playlists:**
  - **Modo Bloco Completo (`INTERCALAR = OFF`):** Reproduz **todos** os vídeos principais da lista em sequência antes de seguir para os vídeos aleatórios.
  - **Modo Intercalado (`INTERCALAR = ON`):** Alterna a reprodução de 1 vídeo principal (Flyer) com os vídeos aleatórios.
- **🎤 Modo Atração (Atração Principal em Destaque):** Permite inserir vídeos de atrações específicas (shows, grupos) no fluxo da playlist com repetição dedicada.
- **⏱️ Corte e Barra de Tempo Inteligente:**
  - **Vídeos Aleatórios:** Aplica o tempo de corte configurado (ex: 1 minuto) com barra de progresso enchendo de 0% a 100% no tempo exato.
  - **Vídeos Principais / Atração:** Sempre reproduzidos até o fim em duração real completa.
- **🛡️ Watchdog Anti-Travamento:** Monitora a reprodução a cada segundo. Caso um arquivo de vídeo esteja corrompido ou congelado por mais de 4 segundos, o sistema pula o arquivo automaticamente.
- **⌨️ Teclado Virtual Amigável:** Esconde o teclado e limpa o foco dos campos ao pressionar **Enter / Concluído**.
- **🎮 Suporte Completo a Controle Remoto (DPAD) e Gestos de Tela:**
  - **Zona Esquerda (2 Toques / DPAD LEFT):** Voltar ao vídeo anterior.
  - **Zona do Meio (2 Toques / DPAD UP):** Abrir Painel de Configurações.
  - **Zona Direita (2 Toques / DPAD RIGHT):** Pular para o próximo vídeo.

---

## ⚙️ Opções do Painel de Configurações

| Configuração | Descrição |
| :--- | :--- |
| **Palavra-chave do vídeo principal** | Termos separados por vírgula para identificar os Flyers (ex: `MARIA, PROMO`). Fallback automático para `MARIA`. |
| **Pasta dos vídeos aleatórios** | Nome do diretório no Pen Drive contendo vídeos gerais (ex: `VIDEOS`). |
| **Qtd. de vídeos principais por bloco** | Quantidade de Flyers que devem tocar por ciclo. |
| **Qtd. de vídeos aleatórios por bloco** | Quantidade de vídeos aleatórios por ciclo. |
| **Tempo máx. vídeo aleatório (minutos)** | Tempo de corte dos vídeos aleatórios (em minutos). |
| **Intercalar Vídeos Principais** | Toggle para alternar entre reprodução 1 a 1 ou bloco completo. |
| **Modo Atração** | Ativa a inclusão de vídeos de atrações específicas no ciclo da playlist. |
| **Iniciar com o Sistema** | Inicia a reprodução automaticamente ao abrir o aplicativo. |

---

## 🕹️ Gestos e Atalhos de Controle

| Ação | Gesto na Tela | Controle Remoto (DPAD / Teclado) |
| :--- | :--- | :--- |
| **Voltar Vídeo** | Duplo toque no **lado esquerdo** da tela | Seta para a Esquerda (`DPAD_LEFT`) / `MEDIA_PREVIOUS` |
| **Abrir Configurações** | Duplo toque no **centro** da tela | Seta para Cima (`DPAD_UP`) |
| **Próximo Vídeo** | Duplo toque no **lado direito** da tela | Seta para a Direita (`DPAD_RIGHT`) / `MEDIA_NEXT` |

---

## 👨‍💻 Desenvolvido Por

Criado com ❤️ por **Rodrigo Vernaschi**.

- 🐙 **GitHub:** [rodrigoVernaschi](https://github.com/rodrigoVernaschi)
- 📸 **Instagram:** [@rodrigo_Vernaschi](https://instagram.com/rodrigo_Vernaschi)
