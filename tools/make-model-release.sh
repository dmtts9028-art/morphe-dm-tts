#!/usr/bin/env bash
# Creates the GitHub Releases asset layout the in-app downloader expects:
#   SHA256SUMS.txt + the four model files, uploaded flat to a release tagged `model`.
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
