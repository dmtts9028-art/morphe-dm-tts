# 🎧 DM TTS — Instagram message read-aloud patches

Morphe patch bundle that adds text-to-speech to written Instagram direct messages:
an **"Écouter le message"** link above every text bubble, and a voice-message-like player
(play/pause, waveform, duration) after you tap it. Compatible with Morphe.

- TTS backends: **system TextToSpeech** (default, no download) or **Phonon 40m multilingual**
  (on-device neural TTS by [Gradium](https://huggingface.co/gradium/phonon-40m-multilingual)).
- Voice selection per conversation, including cloned voices: drop a `*.safetensors` voice file
  into the model's `voices/` folder (see [Voice cloning](#-voice-cloning)) and it appears in the picker.
- French-first UI, multilingual model. Messages are never read automatically.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/dmtts9028-art/morphe-dm-tts

<a href="https://morphe.software/add-source?github=dmtts9028-art/morphe-dm-tts">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release. -->

#### A list of your patches will automatically be shown here after your first patches release is created.

&nbsp;

## 📥 Phonon model download

The Phonon engine is not shipped inside the patch file. On first use in the DM TTS settings,
after accepting the model license (**CC BY-NC 4.0, © Gradium — non-commercial use**), the app
downloads `phonon-40m-multilingual` (`config.json`, `tokenizer.json`, `model.q8.gguf`,
`default-voice.safetensors`, ≈ a few hundred MB) from this repository's GitHub Releases mirror
and verifies every file against `SHA256SUMS.txt`.

If the mirror is ever unavailable, open **Avancé** in the settings and either point
"Téléchargement" at your own host, or enter your own Hugging Face token (the model is
gated on Hugging Face: you must accept the terms there with your own account first) to
download from `huggingface.co` directly.

## 🎙️ Voice cloning

Voice identities are plain `*.safetensors` files; there is no on-device training.

1. In a conversation's long-press menu, **"Extraire les notes vocales"** exports that thread's
   voice-note audio to `Android/data/com.instagram.android/files/phonon/refs/<thread>/`.
   Only extract voices of people who consented to being cloned.
2. Turn the reference audio into a voice file on a PC with
   [phonon-encoder](https://github.com/connerohnesorge/phonon-encoder) applied to an
   [xn-ptts](https://github.com/gradium-ai/xn-ptts) checkout: `python src/make_voice.py 40m voices reference.wav`.
3. Import the resulting `voice.safetensors` from **Réglages DM TTS → Voix → Importer**
   (pick it from Downloads), or push it to the model's `voices/` folder with adb.
   It then appears in the voice picker and can be bound per conversation.

## 🧑‍💻 Dev usage

- **Make all changes to the `dev` branch.**
- Build with `./gradlew buildAndroid`; the bundle is `patches/build/libs/patches-*.mpp`.
  Patch an Instagram **452.1.0.53.64** (arm64-v8a) APKM with Morphe Desktop or Manager.
- Building requires a GitHub `read:packages` PAT in `~/.gradle/gradle.properties`
  (`gpr.user` / `gpr.key`) to resolve the Morphe Gradle plugin.
- Use semantic commits: `feat:`, `fix:`, `chore:`, and `bump:` for Instagram version updates.
- Native libraries (`libptts_ffi.so`) are prebuilt under `patches/src/main/resources/native/`;
  rebuild them with the `xn-ptts` `android/build.sh` against the NDK (arm64-v8a, ARMv8.2+).

## 📜 License

DM TTS patches are licensed under the [GNU General Public License v3.0](LICENSE).

The Phonon model weights are **not** redistributed by the patch file; every user downloads them
from the mirror after accepting their license: `phonon-40m-multilingual` © Gradium,
CC BY-NC 4.0 (https://huggingface.co/gradium/phonon-40m-multilingual).
