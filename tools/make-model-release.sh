#!/usr/bin/env bash
# License-conformant model distribution for the DM TTS patch (phonon-40m-multilingual,
# CC BY-NC 4.0 © Gradium — redistribution of unmodified copies for non-commercial use).
#
# Preferred path: the in-app downloader with the user's own Hugging Face token (Réglages DM TTS
# → Avancé), pointing modelUrl at any HF repo, e.g. https://huggingface.co/dmtts/<repo>/resolve/main/
# — each user accepts the gated terms individually. No mirror needed.
#
# Fallback path (this script): a SHA256SUMS-pinned GitHub Releases mirror. Requires the gated
# terms accepted once with an approved account. Upload layout expected by the app:
#   SHA256SUMS.txt + the four model files, flat, on the release tagged `model-phonon-v1`.
#
# Usage: tools/make-model-release.sh /path/to/phonon-40m-multilingual
set -euo pipefail

DIR="${1:?usage: $0 /path/to/phonon-40m-multilingual}"
cd "$DIR"

for f in config.json tokenizer.json model.q8.gguf default-voice.safetensors; do
    [[ -f "$f" ]] || { echo "missing $f (download it from Hugging Face after accepting the terms)" >&2; exit 1; }
done

sha256sum config.json tokenizer.json model.q8.gguf default-voice.safetensors > SHA256SUMS.txt
cat SHA256SUMS.txt

# The in-app downloader fetches files from the release download base, so the
# mirror URL must point at a release whose tag is `model`:
#   https://github.com/<owner>/<repo>/releases/download/model-phonon-v1/
gh release create model-phonon-v1 --repo "${MIRROR_REPO:?set MIRROR_REPO=owner/repo}" --title "Phonon 40m multilingual mirror" \
    --notes "SHA256-verified mirror of gradium/phonon-40m-multilingual (CC BY-NC 4.0). Files verified against SHA256SUMS.txt by the DM TTS patch." \
    SHA256SUMS.txt config.json tokenizer.json model.q8.gguf default-voice.safetensors
