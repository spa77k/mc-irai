# mc-irai 運用メモ

## リリース

- バージョンは上げずに `v1.0.0` を打ち直す運用。タグを HEAD に付け替えて force push し、`gh release upload --clobber` で `contractboard-1.0.0.jar` を差し替える。
- リリース本文には既存の内容を残したまま `## YYYY-MM-DD 更新` の見出しで今回の変更点を追記する。見出しに「再リリース」「(同一バージョンでJARを差し替え)」などの注記は入れない。
- 本文末尾の `SHA-256:` は最新JARの値1つだけにする(古い値は消す)。
- リリース後は `../spsmc-infra` の `Dockerfile` と `compose.yml` にある `MCIRAI_SHA256` を新しいJARのSHA-256に書き換えてコミット・pushする。

## 動作確認

- `scripts/test-plugin.sh` で Docker の Paper テストサーバーを起動して確認する。起動済みコンテナは古いJARのままなので、`docker compose up -d --force-recreate` で作り直してから実行する。
