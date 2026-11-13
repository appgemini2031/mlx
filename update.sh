#!/data/data/com.termux/files/usr/bin/bash
# =======================================================
# 🚀 ACOPY PIPELINE MANAGER v5.4 (ZERO-APK-FOOTPRINT)
# =======================================================
set +e
export GIT_TERMINAL_PROMPT=0
export GCM_INTERACTIVE=never
export GIT_ASKPASS=/bin/echo

# =======================================================
# 0. UTILITAS CLEANLOG & SANITASI LOG OTOMATIS
# =======================================================
sanitize_log() {
  sed -E \
    -e 's|(https?://)[^/@:]+:[^/@]+@|\1[DISEMBUNYIKAN]@|g' \
    -e 's|(https?://)[^/@]+@|\1[DISEMBUNYIKAN]@|g' \
    -e 's|https?://github\.com/[^[:space:]"'\'']+|[ENDPOINT_TERLINDUNGI]|g' \
    -e 's/(Bearer|token|password)[[:space:]:=]+[^[:space:]"'\'']+/\1 [DISEMBUNYIKAN]/Ig' \
    -e 's/[A-Za-z0-9_]{20,}/[TERSEMBUNYIKAN]/g'
}

log_step() {
  echo ""
  echo "───────────────────────────────────────────────────────"
  echo " 🔹 $1"
  echo "───────────────────────────────────────────────────────"
}

log_info() {
  echo "  [ ℹ️  INFO ] $1"
}

log_ok() {
  echo "  [ ✅ OK   ] $1"
}

log_warn() {
  echo "  [ ⚠️  WARN ] $1"
}

log_err() {
  echo "  [ ❌ ERR  ] $1"
}

is_cancel_input() {
  local val="${1:-}"
  val=$(echo "$val" | tr '[:upper:]' '[:lower:]' | tr -d '\r\n ')
  case "$val" in
    0|q|quit|exit|batal|cancel|keluar)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

purge_obsolete_tools() {
  if [ -d "tools" ]; then
    find tools/ -type f ! -name "apk_downloader.py" ! -name "release_manager.py" -delete 2>/dev/null || true
    rm -rf tools/__pycache__ 2>/dev/null || true
  fi
}

# =======================================================
# 0B. PRE-EXECUTION SELF-UPDATE (AMAN & TIDAK MENJEBAK)
# =======================================================
DIRS_TO_SEARCH=(
  "/storage/emulated/0/Download"
  "/storage/emulated/0/Downloads"
  "/sdcard/Download"
  "/sdcard/Downloads"
  "$HOME/downloads"
  "$HOME/storage/downloads"
  "$HOME/storage/shared/Download"
  "."
)

PRE_DETECTED_ZIP=""
if [ "$1" = "--from-self-update" ]; then
  shift
  if [ -n "$1" ] && [ -f "$1" ]; then
    PRE_DETECTED_ZIP="$1"
    shift
  fi
else
  # Hanya cek self-update jika tidak sedang memanggil sub-menu utilitas (2/3/4/5/0)
  FIRST_ARG="${1:-}"
  case "$FIRST_ARG" in
    2|3|4|5|0|--download|-dl|--release|-rel|--clean|-c|--repo|-r|q|exit)
      ;;
    *)
      LATEST_ZIP=""
      for d in "${DIRS_TO_SEARCH[@]}"; do
        if [ -d "$d" ] && [ -r "$d" ]; then
          while IFS= read -r z_cand; do
            if [ -n "$z_cand" ] && [ -f "$z_cand" ]; then
              if unzip -l "$z_cand" 2>/dev/null | grep -q "update\.sh"; then
                if [ -z "$LATEST_ZIP" ] || [ "$z_cand" -nt "$LATEST_ZIP" ]; then
                  LATEST_ZIP="$z_cand"
                fi
                break
              fi
            fi
          done < <(ls -t "$d"/*.zip 2>/dev/null || true)
        fi
      done

      # Hanya self-update jika arsip ZIP benar-benar lebih baru dari update.sh saat ini
      if [ -n "$LATEST_ZIP" ] && [ -f "$LATEST_ZIP" ] && [ "$LATEST_ZIP" -nt "$0" ]; then
        if unzip -l "$LATEST_ZIP" 2>/dev/null | grep -q "update\.sh"; then
          BASE_TMP="$HOME/.acopy_tmp"
          if [ -n "$TMPDIR" ] && [ -d "$TMPDIR" ] && [ -w "$TMPDIR" ] && [ "$TMPDIR" != "/tmp" ]; then
            BASE_TMP="$TMPDIR"
          fi
          mkdir -p "$BASE_TMP" 2>/dev/null || BASE_TMP="./.acopy_tmp"
          mkdir -p "$BASE_TMP" 2>/dev/null || true
          TMP_PRE="$BASE_TMP/pre_$$"
          mkdir -p "$TMP_PRE" 2>/dev/null || TMP_PRE="./.tmp_pre_$$"
          mkdir -p "$TMP_PRE" 2>/dev/null || true

          unzip -q "$LATEST_ZIP" "update.sh" "tools/*" -d "$TMP_PRE" 2>/dev/null || unzip -q "$LATEST_ZIP" -d "$TMP_PRE" 2>/dev/null || true
          NEW_UPDATE=$(find "$TMP_PRE" -name "update.sh" 2>/dev/null | head -n 1)

          if [ -n "$NEW_UPDATE" ] && [ -f "$NEW_UPDATE" ]; then
            if ! cmp -s "$0" "$NEW_UPDATE"; then
              log_info "Memperbarui skrip pipeline dari arsip terbaru..."
              cp -f "$NEW_UPDATE" "$0"
              chmod +x "$0"
              touch "$0" 2>/dev/null || true

              NEW_TOOLS_DIR=$(dirname "$NEW_UPDATE")/tools
              if [ -d "$NEW_TOOLS_DIR" ]; then
                mkdir -p tools 2>/dev/null || true
                cp -rf "$NEW_TOOLS_DIR"/* tools/ 2>/dev/null || true
                purge_obsolete_tools
                chmod +x tools/*.sh tools/*.py 2>/dev/null || true
              fi

              rm -rf "$TMP_PRE" 2>/dev/null || true
              exec bash "$0" --from-self-update "$LATEST_ZIP" "$@"
              exit 0
            else
              touch "$0" 2>/dev/null || true
            fi
          fi
          rm -rf "$TMP_PRE" 2>/dev/null || true
        fi
      fi
      ;;
  esac
fi

# =======================================================
# 1. UTILITAS LINGKUNGAN & KONFIGURASI REMOTE
# =======================================================
check_termux_tools() {
  MISSING=()
  for t in python3 git curl rsync unzip openssl; do
    if ! command -v "$t" &>/dev/null; then
      MISSING+=("$t")
    fi
  done
  if [ ${#MISSING[@]} -ne 0 ]; then
    log_info "Menyiapkan komponen pendukung: ${MISSING[*]}..."
    if command -v pkg &>/dev/null; then
      pkg install -y "${MISSING[@]}" >/dev/null 2>&1 || true
    fi
  fi
  PYTHON_CMD="python3"
  if ! command -v python3 &>/dev/null && command -v python &>/dev/null; then
    PYTHON_CMD="python"
  fi
}

extract_repo_slug() {
  local raw="${1:-}"
  if [ -z "$raw" ]; then
    raw=$(git remote get-url origin 2>/dev/null || echo "")
  fi
  raw=$(echo "$raw" | tr -d '\r\n ' | sed "s/['\"]//g")
  raw=$(echo "$raw" | sed -E 's|^https?://[^@]+@github\.com/||; s|^https?://github\.com/||; s|^git@github\.com:||; s|\.git/*$||; s|/*$||')
  echo "$raw" | cut -d'/' -f1,2
}

is_valid_credential() {
  local cand="${1:-}"
  if [ "${#cand}" -ge 20 ]; then
    return 0
  fi
  return 1
}

extract_auth_token() {
  local token="${1:-${GITHUB_TOKEN:-${GH_TOKEN:-}}}"
  if is_valid_credential "$token"; then
    echo "$token"
    return 0
  fi

  local remote_url
  remote_url=$(git remote get-url origin 2>/dev/null || echo "")
  if [[ "$remote_url" =~ https://([^:@]+):([^@]+)@github\.com ]]; then
    local p2="${BASH_REMATCH[2]}"
    if is_valid_credential "$p2"; then
      echo "$p2"
      return 0
    fi
  elif [[ "$remote_url" =~ https://([^:@]+)@github\.com ]]; then
    local p1="${BASH_REMATCH[1]}"
    if is_valid_credential "$p1"; then
      echo "$p1"
      return 0
    fi
  fi

  local cur_slug
  cur_slug=$(extract_repo_slug "$remote_url")

  local fallback_tok=""
  for cfg in ".git/acopy_repo.cfg" "$HOME/.acopy_repo.cfg"; do
    if [ -f "$cfg" ]; then
      local cfg_tok cfg_url cfg_slug
      cfg_tok=$(grep -E "^GITHUB_TOKEN=" "$cfg" 2>/dev/null | head -n 1 | cut -d'"' -f2 | tr -d '\r\n ')
      cfg_url=$(grep -E "^CLEAN_REPO_URL=" "$cfg" 2>/dev/null | head -n 1 | cut -d'"' -f2 | tr -d '\r\n ')
      cfg_slug=$(extract_repo_slug "$cfg_url")
      if is_valid_credential "$cfg_tok"; then
        if [ -z "$cur_slug" ] || [ -z "$cfg_slug" ] || [ "${cur_slug,,}" = "${cfg_slug,,}" ]; then
          echo "$cfg_tok"
          return 0
        elif [ -z "$fallback_tok" ]; then
          fallback_tok="$cfg_tok"
        fi
      fi
    fi
  done

  if command -v gh &>/dev/null; then
    local gh_tok
    gh_tok=$(gh auth token 2>/dev/null || true)
    if is_valid_credential "$gh_tok"; then
      echo "$gh_tok"
      return 0
    fi
  fi

  if [ -n "$fallback_tok" ]; then
    echo "$fallback_tok"
    return 0
  fi

  echo ""
}

# =======================================================
# 2. MODUL DELEGASI FITUR & HELPER SINKRONISASI
# =======================================================
sync_tools_dir() {
  local src_dir="${1:-temp_update/tools}"
  if [ -d "$src_dir" ]; then
    mkdir -p tools 2>/dev/null || true
    cp -rf "$src_dir"/* tools/ 2>/dev/null || true
    purge_obsolete_tools
    chmod +x tools/*.sh tools/*.py 2>/dev/null || true
  fi
}

ensure_git_identity() {
  if [ ! -d ".git" ]; then
    git init -b main >/dev/null 2>&1 || git init >/dev/null 2>&1 || true
  fi
  local git_name git_email
  git_name=$(git config user.name 2>/dev/null || true)
  git_email=$(git config user.email 2>/dev/null || true)
  if [ -z "$git_name" ]; then
    git config --global user.name "Developer" 2>/dev/null || git config user.name "Developer" 2>/dev/null || true
  fi
  if [ -z "$git_email" ]; then
    git config --global user.email "dev@local.build" 2>/dev/null || git config user.email "dev@local.build" 2>/dev/null || true
  fi
}

save_repo_config() {
  local authed_url="$1"
  local clean_url="$2"
  local tok="$3"
  local now_str
  now_str="$(date)"
  mkdir -p "$HOME" .git 2>/dev/null || true
  for cfg_dest in "$HOME/.acopy_repo.cfg" ".git/acopy_repo.cfg"; do
    cat <<EOF > "$cfg_dest" 2>/dev/null || true
REPO_URL="$authed_url"
CLEAN_REPO_URL="$clean_url"
GITHUB_TOKEN="$tok"
UPDATED_AT="$now_str"
EOF
    chmod 600 "$cfg_dest" 2>/dev/null || true
  done
}

read_version_prop() {
  local prop_file="$1"
  local prop_key="$2"
  local def_val="${3:-}"
  local val=""
  if [ -f "$prop_file" ]; then
    val=$(grep -E "^${prop_key}=" "$prop_file" 2>/dev/null | head -n 1 | cut -d '=' -f 2 | tr -d '\r\n ')
  fi
  echo "${val:-$def_val}"
}

ensure_tools_available() {
  local req_file="$1"
  if [ -f "$req_file" ]; then
    return 0
  fi
  for d in "${DIRS_TO_SEARCH[@]}"; do
    if [ -d "$d" ] && [ -r "$d" ]; then
      while IFS= read -r z; do
        if [ -n "$z" ] && [ -f "$z" ]; then
          if unzip -l "$z" 2>/dev/null | grep -q "update\.sh"; then
            mkdir -p tools 2>/dev/null || true
            unzip -q -o "$z" "tools/*" 2>/dev/null || true
            chmod +x tools/*.sh tools/*.py 2>/dev/null || true
            if [ -f "$req_file" ]; then
              return 0
            fi
          fi
        fi
      done < <(ls -t "$d"/*.zip 2>/dev/null || true)
    fi
  done
  return 1
}

manage_github_releases() {
  check_termux_tools
  ensure_tools_available "tools/release_manager.py" || true
  local ARG_SLUG="$1"
  local ARG_TOKEN="$2"
  local DETECTED_SLUG="$ARG_SLUG"
  if [ -z "$DETECTED_SLUG" ]; then
    DETECTED_SLUG=$(extract_repo_slug)
  fi
  local DETECTED_TOKEN
  DETECTED_TOKEN=$(extract_auth_token "$ARG_TOKEN")

  if [ -f "tools/release_manager.py" ]; then
    $PYTHON_CMD tools/release_manager.py "$DETECTED_SLUG" "$DETECTED_TOKEN"
  else
    log_err "Modul release manager belum tersedia di direktori tools/."
    return 1
  fi
}

# Keystore Permanen Tunggal (SHA-1: CB:91:04:91:62:F3:07:7C:DC:21:A6:2F:81:89:4E:FB:DF:9D:42:D5)
# Menjamin APK tidak pernah bentrok tanda tangan (INSTALL_FAILED_UPDATE_INCOMPATIBLE) saat di-update
CANONICAL_KEYSTORE_B64="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"

decode_b64_to_keystore() {
  local src_b64="$1"
  local dst_ks="$2"
  $PYTHON_CMD -c "import base64, re; raw = re.sub(r'[^A-Za-z0-9+/=]', '', open('$src_b64').read()); open('$dst_ks','wb').write(base64.b64decode(raw))" 2>/dev/null || \
    base64 -di "$src_b64" > "$dst_ks" 2>/dev/null || \
    base64 -d "$src_b64" > "$dst_ks" 2>/dev/null || true
}

ensure_canonical_keystore() {
  printf "%s" "$CANONICAL_KEYSTORE_B64" > "debug.keystore.base64"
  mkdir -p .git 2>/dev/null || true
  cp -f "debug.keystore.base64" ".git/acopy_keystore.b64" 2>/dev/null || true
  cp -f "debug.keystore.base64" "$HOME/.acopy_keystore.b64" 2>/dev/null || true
  decode_b64_to_keystore "debug.keystore.base64" "debug.keystore"
}

backup_version_and_keystore() {
  mkdir -p .git 2>/dev/null || true
  if [ -s "app/version.properties" ]; then
    cp -f "app/version.properties" ".git/acopy_version.cfg" 2>/dev/null || true
    cp -f "app/version.properties" "$HOME/.acopy_version.cfg" 2>/dev/null || true
  fi
  ensure_canonical_keystore
}

restore_version_and_keystore() {
  if [ ! -s "app/version.properties" ]; then
    if [ -s ".git/acopy_version.cfg" ]; then
      mkdir -p app 2>/dev/null || true
      cp -f ".git/acopy_version.cfg" "app/version.properties" 2>/dev/null || true
    elif [ -s "$HOME/.acopy_version.cfg" ]; then
      mkdir -p app 2>/dev/null || true
      cp -f "$HOME/.acopy_version.cfg" "app/version.properties" 2>/dev/null || true
    fi
  fi
  ensure_canonical_keystore
}

purge_local_apk_footprint() {
  backup_version_and_keystore

  log_info "Menghapus seluruh kode sumber & logika APK dari lokal Termux..."
  rm -rf \
    app \
    gradle \
    .gradle \
    build \
    temp_update \
    dist \
    .idea \
    .github \
    captures \
    .externalNativeBuild \
    .cxx \
    out 2>/dev/null || true

  rm -f \
    build.gradle.kts \
    settings.gradle.kts \
    gradle.properties \
    gradlew \
    gradlew.bat \
    metadata.json \
    local.properties \
    debug.keystore \
    debug.keystore.base64 \
    test.keystore \
    .gitignore \
    *.apk \
    *.aab \
    *.idsig \
    *.iml 2>/dev/null || true

  purge_obsolete_tools

  log_ok "Zero-APK-Footprint aktif: Kode APK lokal dihapus (Pipeline GitHub & Tools aman)."
}

run_clean_apk_workspace() {
  log_step "PEMBERSIHAN KODE & LOGIKA APK LOKAL (ZERO-FOOTPRINT)"
  purge_local_apk_footprint
  echo ""
  echo "======================================================="
  echo "  🛡️  DIREKTORI TERMUX BERSIH DARI KODE SUMBER APK"
  echo "======================================================="
  echo "  ✅ Dipertahankan : update.sh, tools/ (Downloader/Release), Kredensial & Versi"
  echo "  🗑️  Dihapus       : app/, gradle/, build.gradle.kts, *.apk, dan logika APK"
  echo "======================================================="
}

download_github_apk() {
  check_termux_tools
  ensure_tools_available "tools/apk_downloader.py" || true
  local ARG_SHA="$1"
  local ARG_VER="$2"
  local ARG_SLUG="$3"
  local ARG_TOKEN="$4"

  local DETECTED_SLUG="$ARG_SLUG"
  if [ -z "$DETECTED_SLUG" ]; then
    DETECTED_SLUG=$(extract_repo_slug)
  fi
  local DETECTED_TOKEN
  DETECTED_TOKEN=$(extract_auth_token "$ARG_TOKEN")

  if [ -f "tools/apk_downloader.py" ]; then
    $PYTHON_CMD tools/apk_downloader.py "$ARG_SHA" "$ARG_VER" "$DETECTED_SLUG" "$DETECTED_TOKEN"
  else
    log_err "Modul pengunduh APK belum tersedia di direktori tools/."
    return 1
  fi
}

switch_github_repository() {
  echo ""
  echo "======================================================="
  echo "   🔄 KONFIGURASI ENDPOINT REPOSITORI GIT"
  echo "======================================================="
  echo "  💡 Ketik '0' atau tekan Enter kosong kapan saja untuk batal."
  echo "───────────────────────────────────────────────────────"

  ensure_git_identity

  printf "👉 Masukkan URL Repositori atau username/repo (0 untuk batal): "
  read -r NEW_REPO_INPUT
  NEW_REPO_INPUT=$(echo "$NEW_REPO_INPUT" | sed "s/['\"]//g" | tr -d '\r\n ')
  if [ -z "$NEW_REPO_INPUT" ] || is_cancel_input "$NEW_REPO_INPUT"; then
    log_info "Konfigurasi repositori dibatalkan."
    return 1
  fi

  PREV_TOKEN=$(extract_auth_token)

  P_USER=""
  P_REPO=""
  P_TOKEN=""

  STRIPPED_INPUT=$(echo "$NEW_REPO_INPUT" | sed -E 's|\.git/*$||; s|/*$||')

  if [[ "$STRIPPED_INPUT" =~ ^https?://([^:@]+):([^@]+)@github\.com/([^/]+)/([^/]+)$ ]]; then
    P_USER="${BASH_REMATCH[3]}"
    P_TOKEN="${BASH_REMATCH[2]}"
    P_REPO="${BASH_REMATCH[4]}"
  elif [[ "$STRIPPED_INPUT" =~ ^https?://([^:@]+)@github\.com/([^/]+)/([^/]+)$ ]]; then
    if is_valid_credential "${BASH_REMATCH[1]}"; then
      P_TOKEN="${BASH_REMATCH[1]}"
    fi
    P_USER="${BASH_REMATCH[2]}"
    P_REPO="${BASH_REMATCH[3]}"
  elif [[ "$STRIPPED_INPUT" =~ ^https?://github\.com/([^/]+)/([^/]+)$ ]]; then
    P_USER="${BASH_REMATCH[1]}"
    P_REPO="${BASH_REMATCH[2]}"
  elif [[ "$STRIPPED_INPUT" =~ ^([a-zA-Z0-9_-]+)/([a-zA-Z0-9_.-]+)$ ]]; then
    P_USER="${BASH_REMATCH[1]}"
    P_REPO="${BASH_REMATCH[2]}"
  fi

  P_REPO="${P_REPO%.git}"

  if [ -z "$P_USER" ]; then
    printf "👉 Masukkan Username Git (0 untuk batal): "
    read -r P_USER
    P_USER=$(echo "$P_USER" | tr -d '\r\n ')
    if [ -z "$P_USER" ] || is_cancel_input "$P_USER"; then
      log_info "Konfigurasi dibatalkan."
      return 1
    fi
  fi

  if [ -z "$P_REPO" ]; then
    printf "👉 Masukkan Nama Repositori (0 untuk batal): "
    read -r P_REPO
    P_REPO=$(echo "$P_REPO" | tr -d '\r\n ')
    P_REPO="${P_REPO%.git}"
    if [ -z "$P_REPO" ] || is_cancel_input "$P_REPO"; then
      log_info "Konfigurasi dibatalkan."
      return 1
    fi
  fi

  if [ -z "$P_TOKEN" ]; then
    if [ -n "$PREV_TOKEN" ]; then
      echo "🔑 Masukkan Token Akses Git (Tekan Enter untuk memakai kredensial tersimpan, atau '0' untuk batal):"
    else
      echo "🔑 Masukkan Token Akses Git ('0' untuk batal):"
    fi
    printf "👉 Token Akses (input disembunyikan): "
    read -rs P_TOKEN
    echo ""
    P_TOKEN=$(echo "$P_TOKEN" | tr -d '\r\n ')
    if is_cancel_input "$P_TOKEN"; then
      log_info "Konfigurasi dibatalkan."
      return 1
    fi
    if [ -z "$P_TOKEN" ] && [ -n "$PREV_TOKEN" ]; then
      P_TOKEN="$PREV_TOKEN"
      log_info "Menggunakan kredensial tersimpan."
    fi
  fi

  CLEAN_URL="https://github.com/${P_USER}/${P_REPO}.git"
  if [ -n "$P_TOKEN" ]; then
    AUTHED_URL="https://${P_USER}:${P_TOKEN}@github.com/${P_USER}/${P_REPO}.git"
  else
    AUTHED_URL="$CLEAN_URL"
  fi

  if git remote get-url origin &>/dev/null; then
    git remote set-url origin "$AUTHED_URL"
  else
    git remote add origin "$AUTHED_URL"
  fi

  save_repo_config "$AUTHED_URL" "$CLEAN_URL" "$P_TOKEN"

  echo ""
  log_ok "Konfigurasi endpoint repositori berhasil disimpan."
  log_info "Status Remote : Terhubung & Siap Sinkronisasi"
  if [ -n "$P_TOKEN" ]; then
    log_info "Status Akses  : Kredensial Tersimpan Aman (Terproteksi)"
  fi
  echo "======================================================="
  if [ -f "app/build.gradle.kts" ]; then
    purge_obsolete_tools
    git rm -r --cached -q tools/ >/dev/null 2>&1 || true
    [ -f ".env.example" ] || touch .env.example
    git add -f .env.example >/dev/null 2>&1 || true
    git add -A >/dev/null 2>&1 || true
    git reset -q HEAD tools/ *.pem *.key *.cfg *.conf .*_token debug.keystore >/dev/null 2>&1 || true
    if ! git rev-parse --verify HEAD &>/dev/null; then
      git commit --allow-empty -m "chore: initialize repository" --quiet 2>/dev/null || true
    elif ! git diff --cached --quiet; then
      git commit -m "chore: sync repository files" --quiet 2>/dev/null || true
    fi
  fi

  if git rev-parse --verify HEAD &>/dev/null; then
    log_info "Menyinkronkan repositori ke remote..."
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "main")
    [ -z "$CURRENT_BRANCH" ] && CURRENT_BRANCH="main"

    SW_PUSH_OUT=$(git push -u origin "$CURRENT_BRANCH" --force 2>&1)
    SW_STATUS=$?
    if [ $SW_STATUS -ne 0 ]; then
      SW_PUSH_OUT=$(git push -u origin HEAD:"$CURRENT_BRANCH" --force 2>&1)
      SW_STATUS=$?
    fi

    if [ $SW_STATUS -eq 0 ]; then
      log_ok "Sinkronisasi ke remote berhasil diselesaikan!"
      return 0
    else
      log_warn "Gagal menyinkronkan ke remote."
      echo "$SW_PUSH_OUT" | sanitize_log
      return 1
    fi
  else
    log_ok "Endpoint siap digunakan untuk pengiriman paket pada tahap selanjutnya."
    return 0
  fi
}

# =======================================================
# 4. PIPELINE UPDATE PROYEK (MODE 1 & MODE 2)
# =======================================================
run_pipeline_update() {
  local PUSH_TO_GITHUB="${1:-true}"
  local PARAM_ZIP="${2:-}"

  log_step "TAHAP 1/5 : VALIDASI LINGKUNGAN & KONFIGURASI"
check_termux_tools

if command -v termux-setup-storage &>/dev/null; then
  if [ ! -d "/storage/emulated/0/Download" ] && [ ! -d "/sdcard/Download" ]; then
    termux-setup-storage 2>/dev/null || true
  fi
fi

ensure_git_identity
git rebase --abort 2>/dev/null || true
git merge --abort 2>/dev/null || true
rm -f .git/index.lock .git/MERGE_HEAD .git/rebase-apply .git/rebase-merge 2>/dev/null || true

if [ -f ".git/acopy_repo.cfg" ]; then
  . ".git/acopy_repo.cfg" 2>/dev/null || true
elif [ -f "$HOME/.acopy_repo.cfg" ]; then
  . "$HOME/.acopy_repo.cfg" 2>/dev/null || true
fi

if [ -n "$REPO_URL" ]; then
  if ! git remote get-url origin &>/dev/null; then
    git remote add origin "$REPO_URL" 2>/dev/null || true
  else
    CURRENT_REMOTE=$(git remote get-url origin 2>/dev/null || echo "")
    CUR_SLUG=$(extract_repo_slug "$CURRENT_REMOTE")
    CFG_SLUG=$(extract_repo_slug "$REPO_URL")
    if [ "${CUR_SLUG,,}" = "${CFG_SLUG,,}" ] && [ "$CURRENT_REMOTE" != "$REPO_URL" ] && [[ "$REPO_URL" == *"@"* ]]; then
      git remote set-url origin "$REPO_URL" 2>/dev/null || true
    fi
  fi
fi

if [ "$PUSH_TO_GITHUB" = true ]; then
  REMOTE_ORIGIN=$(git remote get-url origin 2>/dev/null || true)
  if [ -z "$REMOTE_ORIGIN" ]; then
    log_warn "Endpoint remote belum dikonfigurasi."
    if ! switch_github_repository; then
      log_err "Konfigurasi remote GitHub dibatalkan."
      return 1
    fi
  fi
fi

log_info "Memindai arsip pembaruan (.zip) terbaru..."
ALL_ZIPS=()
if [ -n "$PARAM_ZIP" ] && [ -f "$PARAM_ZIP" ]; then
  ALL_ZIPS+=("$PARAM_ZIP")
fi
if [ -n "$PRE_DETECTED_ZIP" ] && [ -f "$PRE_DETECTED_ZIP" ]; then
  ALL_ZIPS+=("$PRE_DETECTED_ZIP")
fi

for d in "${DIRS_TO_SEARCH[@]}"; do
  if [ -d "$d" ] && [ -r "$d" ]; then
    while IFS= read -r z_file; do
      if [ -n "$z_file" ] && [ -f "$z_file" ]; then
        ALL_ZIPS+=("$z_file")
      fi
    done < <(ls -t "$d"/*.zip 2>/dev/null || true)
  fi
done

ZIP=""
for cand in "${ALL_ZIPS[@]}"; do
  if [ -f "$cand" ]; then
    if unzip -l "$cand" 2>/dev/null | grep -qE "update\.sh|settings\.gradle\.kts"; then
      if [ -z "$ZIP" ] || [ "$cand" -nt "$ZIP" ]; then
        ZIP="$cand"
      fi
    fi
  fi
done

if [ -n "$ZIP" ] && [ -f "$ZIP" ]; then
  ZIP_TIME=$(date -r "$ZIP" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "Terbaru")
  ZIP_SIZE=$(du -h "$ZIP" 2>/dev/null | cut -f1 || echo "-")
  log_ok "Arsip ditemukan : $(basename "$ZIP") (${ZIP_SIZE} • ${ZIP_TIME})"
else
  log_warn "Arsip ZIP proyek belum ditemukan di folder Download atau Termux."
  if [ -t 0 ]; then
    printf "👉 Masukkan path berkas ZIP (atau ketik '0' untuk batal): "
    read -r CUSTOM_ZIP_PATH
    CUSTOM_ZIP_PATH=$(echo "$CUSTOM_ZIP_PATH" | sed "s/['\"]//g" | tr -d '\r\n')
    if [ -z "$CUSTOM_ZIP_PATH" ] || is_cancel_input "$CUSTOM_ZIP_PATH" || [ ! -f "$CUSTOM_ZIP_PATH" ]; then
      log_info "Operasi dibatalkan. Kembali ke Menu Utama."
      return 0
    fi
    ZIP="$CUSTOM_ZIP_PATH"
  else
    log_err "Arsip ZIP tidak ditemukan dalam mode non-interaktif."
    return 1
  fi
fi

log_step "TAHAP 2/5 : EKSTRAKSI & SANITASI PAKET PROYEK"
# Pulihkan versi & keystore dari penyimpanan tersembunyi sebelum ekstraksi
restore_version_and_keystore
backup_version_and_keystore

rm -rf temp_update
mkdir -p temp_update
log_info "Mengekstrak arsip $(basename "$ZIP")..."
if ! unzip -q "$ZIP" -d temp_update; then
  log_err "Gagal mengekstrak arsip ZIP! Pastikan berkas telah selesai diunduh & utuh."
  log_ok "🛡️  PROTEKSI BERKAS: Arsip '$ZIP' aman & TIDAK dihapus."
  rm -rf temp_update
  return 1
fi

NESTED_SETTINGS=$(find temp_update -maxdepth 3 -name "settings.gradle.kts" 2>/dev/null | head -n 1)
if [ -n "$NESTED_SETTINGS" ]; then
  NESTED_DIR=$(dirname "$NESTED_SETTINGS")
  if [ "$NESTED_DIR" != "temp_update" ]; then
    log_info "Menormalisasi struktur direktori proyek..."
    find "$NESTED_DIR" -mindepth 1 -maxdepth 1 | while read -r item; do
      mv "$item" temp_update/ 2>/dev/null || true
    done
    rm -rf "$NESTED_DIR" 2>/dev/null || true
  fi
fi

APP_NAME=$(grep '"name":' temp_update/metadata.json 2>/dev/null | head -n 1 | cut -d '"' -f 4 || echo "asisten copy")
log_info "Identitas Proyek : $APP_NAME"

# Jika Mode Lokal (Ekstraksi update.sh & GitHub Tools Saja Tanpa Kode APK)
if [ "$PUSH_TO_GITHUB" = false ]; then
  log_step "TAHAP 3/5 : EKSTRAKSI UPDATE.SH & GITHUB TOOLS SAJA"
  if [ -f "temp_update/update.sh" ]; then
    cp -f "temp_update/update.sh" "./update.sh" 2>/dev/null || true
    chmod +x "./update.sh" 2>/dev/null || true
  fi
  sync_tools_dir "temp_update/tools"

  if [ ! -s ".git/acopy_version.cfg" ] && [ -s "temp_update/app/version.properties" ]; then
    mkdir -p .git 2>/dev/null || true
    cp -f "temp_update/app/version.properties" ".git/acopy_version.cfg" 2>/dev/null || true
    cp -f "temp_update/app/version.properties" "$HOME/.acopy_version.cfg" 2>/dev/null || true
  fi
  if [ ! -s ".git/acopy_keystore.b64" ] && [ -s "temp_update/debug.keystore.base64" ]; then
    mkdir -p .git 2>/dev/null || true
    cp -f "temp_update/debug.keystore.base64" ".git/acopy_keystore.b64" 2>/dev/null || true
    cp -f "temp_update/debug.keystore.base64" "$HOME/.acopy_keystore.b64" 2>/dev/null || true
  fi

  if [ -n "$ZIP" ] && [ -f "$ZIP" ]; then
    ZIP_BASENAME=$(basename "$ZIP")
    rm -f "$ZIP" 2>/dev/null || true
    log_ok "Arsip sumber ($ZIP_BASENAME) selesai diproses & dihapus otomatis."
  fi

  purge_local_apk_footprint

  echo ""
  echo "======================================================="
  echo "  🎉 EKSTRAKSI UPDATE.SH & GITHUB TOOLS SELESAI!"
  echo "======================================================="
  echo "  ✅ Tersimpan : update.sh, tools/ (apk_downloader, release_manager)"
  echo "  🗑️  Dibersihkan: Seluruh kode sumber APK (app/, gradle/) & file ZIP"
  echo "======================================================="
  return 0
fi

log_info "Target Distribusi: Cloud CI/CD Remote (Zero-Local-APK-Footprint)"
log_ok "Mode Sinkronisasi: Cloud Build Otomatis + Auto-Purge Lokal"

log_info "Membersihkan metadata IDE & konfigurasi pengujian..."
find temp_update -type f \( -name "*.kt" -o -name "*.kts" \) -exec sed -i \
  -e '/com\.intellij/d' \
  -e '/ksp\.com\.intellij/d' {} \; 2>/dev/null || true

rm -rf temp_update/app/src/test 2>/dev/null || true
rm -rf temp_update/app/src/androidTest 2>/dev/null || true
sed -i '/robolectric/d' temp_update/app/build.gradle.kts 2>/dev/null || true
sed -i '/robolectric/d' temp_update/gradle/libs.versions.toml 2>/dev/null || true
rm -f temp_update/test.keystore test.keystore 2>/dev/null || true

log_step "TAHAP 3/5 : SINKRONISASI STAGING & PROTEKSI KONFIGURASI"
RSYNC_EXCLUDE=(
  "--exclude=.git"
  "--exclude=.git/"
  "--exclude=app/version.properties"
  "--exclude=local.properties"
  "--include=.env.example"
  "--exclude=.env"
  "--exclude=.env.*"
  "--exclude=temp_update"
  "--exclude=temp_update/"
  "--exclude=tools"
  "--exclude=tools/"
  "--exclude=*.pem"
  "--exclude=*.key"
  "--exclude=*.conf"
  "--exclude=*.cfg"
  "--exclude=.*_token"
  "--exclude=debug.keystore"
  "--exclude=debug.keystore.base64"
)

if command -v rsync &>/dev/null; then
  rsync -a --delete "${RSYNC_EXCLUDE[@]}" temp_update/ ./ >/dev/null 2>&1 || true
else
  for item in temp_update/* temp_update/.*; do
    [ -e "$item" ] || continue
    base_item=$(basename "$item")
    case "$base_item" in
      .env.example)
        cp -f "$item" ./ 2>/dev/null || true
        ;;
      .|..|.git|.env|.env.*|tools|*.pem|*.key|*.conf|*.cfg|.*_token|debug.keystore|debug.keystore.base64|local.properties)
        ;;
      *)
        cp -rf "$item" ./ 2>/dev/null || true
        ;;
    esac
  done
fi

if [ -f "temp_update/.env.example" ]; then
  cp -f "temp_update/.env.example" "./.env.example" 2>/dev/null || true
fi
[ -f ".env.example" ] || touch .env.example

sync_tools_dir "temp_update/tools"

# Pulihkan kembali state versi & paksa sinkronisasi keystore tunggal permanen
restore_version_and_keystore
ensure_canonical_keystore
log_ok "Sinkronisasi staging & sertifikat permanen selesai (anti-bentrok update)."

ZIP_VER_PROPS="temp_update/app/version.properties"
ZIP_MAJ=$(read_version_prop "$ZIP_VER_PROPS" "VERSION_MAJOR" "")
ZIP_MIN=$(read_version_prop "$ZIP_VER_PROPS" "VERSION_MINOR" "")
ZIP_PAT=$(read_version_prop "$ZIP_VER_PROPS" "VERSION_PATCH" "")
ZIP_COD=$(read_version_prop "$ZIP_VER_PROPS" "VERSION_CODE" "")

if [ -f "temp_update/.github/workflows/build-apk.yml" ]; then
  mkdir -p .github/workflows
  cp -f "temp_update/.github/workflows/build-apk.yml" ".github/workflows/build-apk.yml"
fi

rm -rf temp_update

log_step "TAHAP 4/5 : PEMBARUAN VERSI & PROTEKSI GITIGNORE"
mkdir -p app
PROP_FILE="app/version.properties"
C_MAJ=$(read_version_prop "$PROP_FILE" "VERSION_MAJOR" "1")
C_MIN=$(read_version_prop "$PROP_FILE" "VERSION_MINOR" "0")
C_PAT=$(read_version_prop "$PROP_FILE" "VERSION_PATCH" "50")
C_COD=$(read_version_prop "$PROP_FILE" "VERSION_CODE" "2550")

[ -z "$ZIP_MAJ" ] && ZIP_MAJ="$C_MAJ"
[ -z "$ZIP_MIN" ] && ZIP_MIN="$C_MIN"
[ -z "$ZIP_PAT" ] && ZIP_PAT="$C_PAT"
[ -z "$ZIP_COD" ] && ZIP_COD="$C_COD"

# Gunakan patokan versi tertinggi berdasarkan tuple (MAJOR, MINOR, PATCH) agar cache lama v0.0.xxx
# tidak menimpa patokan v1.0.50+
pick_higher_semver() {
  local m1="${1:-0}" n1="${2:-0}" p1="${3:-0}"
  local m2="${4:-0}" n2="${5:-0}" p2="${6:-0}"
  if [ "$m2" -gt "$m1" ] 2>/dev/null; then
    echo "$m2 $n2 $p2"
  elif [ "$m2" -eq "$m1" ] 2>/dev/null && [ "$n2" -gt "$n1" ] 2>/dev/null; then
    echo "$m2 $n2 $p2"
  elif [ "$m2" -eq "$m1" ] 2>/dev/null && [ "$n2" -eq "$n1" ] 2>/dev/null && [ "$p2" -gt "$p1" ] 2>/dev/null; then
    echo "$m2 $n2 $p2"
  else
    echo "$m1 $n1 $p1"
  fi
}

# Patokan minimum mutlak v1.0.50
MAX_MAJ=1
MAX_MIN=0
MAX_PAT=50
read -r MAX_MAJ MAX_MIN MAX_PAT <<< "$(pick_higher_semver "$MAX_MAJ" "$MAX_MIN" "$MAX_PAT" "$C_MAJ" "$C_MIN" "$C_PAT")"
read -r MAX_MAJ MAX_MIN MAX_PAT <<< "$(pick_higher_semver "$MAX_MAJ" "$MAX_MIN" "$MAX_PAT" "$ZIP_MAJ" "$ZIP_MIN" "$ZIP_PAT")"

H_MAJ=$(read_version_prop "$HOME/.acopy_version.cfg" "VERSION_MAJOR" "0")
H_MIN=$(read_version_prop "$HOME/.acopy_version.cfg" "VERSION_MINOR" "0")
H_PAT=$(read_version_prop "$HOME/.acopy_version.cfg" "VERSION_PATCH" "0")
H_COD=$(read_version_prop "$HOME/.acopy_version.cfg" "VERSION_CODE" "0")

G_MAJ=$(read_version_prop ".git/acopy_version.cfg" "VERSION_MAJOR" "0")
G_MIN=$(read_version_prop ".git/acopy_version.cfg" "VERSION_MINOR" "0")
G_PAT=$(read_version_prop ".git/acopy_version.cfg" "VERSION_PATCH" "0")
G_COD=$(read_version_prop ".git/acopy_version.cfg" "VERSION_CODE" "0")

read -r MAX_MAJ MAX_MIN MAX_PAT <<< "$(pick_higher_semver "$MAX_MAJ" "$MAX_MIN" "$MAX_PAT" "$H_MAJ" "$H_MIN" "$H_PAT")"
read -r MAX_MAJ MAX_MIN MAX_PAT <<< "$(pick_higher_semver "$MAX_MAJ" "$MAX_MIN" "$MAX_PAT" "$G_MAJ" "$G_MIN" "$G_PAT")"

# Cek otomatis versi tertinggi di GitHub Release & Remote Tags:
# Jika di Termux rendah sedangkan di Release sudah tinggi, otomatis ikuti versi Release lalu naikkan +1.
REL_MAJ=0
REL_MIN=0
REL_PAT=0
CUR_SLUG_VER=$(extract_repo_slug)
CUR_TOK_VER=$(extract_auth_token)
if [ -n "$CUR_SLUG_VER" ]; then
  REL_OUT=$("$PYTHON_CMD" -c "
import urllib.request, json, re, subprocess
slug = '$CUR_SLUG_VER'
tok = '$CUR_TOK_VER'
best = (0, 0, 0)
headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'Acopy-Updater'}
if tok:
    headers['Authorization'] = f'Bearer {tok}'
for ep in [f'https://api.github.com/repos/{slug}/releases?per_page=30', f'https://api.github.com/repos/{slug}/tags?per_page=30']:
    try:
        req = urllib.request.Request(ep, headers=headers)
        with urllib.request.urlopen(req, timeout=8) as r:
            data = json.loads(r.read().decode('utf-8', errors='ignore'))
            if isinstance(data, list):
                for item in data:
                    cand_texts = [str(item.get('tag_name', '')), str(item.get('name', ''))]
                    for a in item.get('assets', []) if isinstance(item.get('assets'), list) else []:
                        cand_texts.append(str(a.get('name', '')))
                    for t in cand_texts:
                        for m in re.finditer(r'v?(\d+)\.(\d+)\.(\d+)', t):
                            v = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
                            if v > best:
                                best = v
    except Exception:
        pass
print(f'{best[0]} {best[1]} {best[2]}')
" 2>/dev/null || echo "0 0 0")
  read -r REL_MAJ REL_MIN REL_PAT <<< "$REL_OUT"
fi

# Fallback tambahan dari git ls-remote --tags origin jika ada
while IFS= read -r tag_ver; do
  if [ -n "$tag_ver" ]; then
    t_maj=$(echo "$tag_ver" | cut -d'.' -f1)
    t_min=$(echo "$tag_ver" | cut -d'.' -f2)
    t_pat=$(echo "$tag_ver" | cut -d'.' -f3)
    read -r REL_MAJ REL_MIN REL_PAT <<< "$(pick_higher_semver "$REL_MAJ" "$REL_MIN" "$REL_PAT" "$t_maj" "$t_min" "$t_pat")"
  fi
done < <(git ls-remote --tags origin 2>/dev/null | grep -oE 'refs/tags/v?[0-9]+\.[0-9]+\.[0-9]+' | sed -E 's|refs/tags/v?||' || true)

HAS_HIGHER_REMOTE=0
if [ "${REL_MAJ:-0}" -gt 0 ] 2>/dev/null || [ "${REL_PAT:-0}" -gt 0 ] 2>/dev/null; then
  AFTER_REL=$(pick_higher_semver "$MAX_MAJ" "$MAX_MIN" "$MAX_PAT" "$REL_MAJ" "$REL_MIN" "$REL_PAT")
  read -r NEW_M NEW_N NEW_P <<< "$AFTER_REL"
  if [ "$NEW_M" != "$MAX_MAJ" ] || [ "$NEW_N" != "$MAX_MIN" ] || [ "$NEW_P" != "$MAX_PAT" ] || { [ "$REL_MAJ" -eq "$MAX_MAJ" ] && [ "$REL_MIN" -eq "$MAX_MIN" ] && [ "$REL_PAT" -ge "$MAX_PAT" ]; }; then
    HAS_HIGHER_REMOTE=1
    MAX_MAJ=$NEW_M
    MAX_MIN=$NEW_N
    MAX_PAT=$NEW_P
  fi
fi

MAX_COD=2550
[ "$C_COD" -gt "$MAX_COD" ] 2>/dev/null && MAX_COD=$C_COD
[ "$ZIP_COD" -gt "$MAX_COD" ] 2>/dev/null && MAX_COD=$ZIP_COD
[ "$H_COD" -gt "$MAX_COD" ] 2>/dev/null && MAX_COD=$H_COD
[ "$G_COD" -gt "$MAX_COD" ] 2>/dev/null && MAX_COD=$G_COD
REL_EST_COD=$((2500 + MAX_PAT))
[ "$REL_EST_COD" -gt "$MAX_COD" ] 2>/dev/null && MAX_COD=$REL_EST_COD

# Jika di GitHub Release sudah ada versi yang sama/lebih tinggi, otomatis ikuti versi Release lalu naikkan +1!
# Hanya jika belum ada Release v1.x sama sekali dan Termux baru migrasi dari 0.x, mulai tepat di v1.0.50.
if [ "$HAS_HIGHER_REMOTE" -eq 1 ]; then
  PREV_VER_STR="Release v${REL_MAJ}.${REL_MIN}.${REL_PAT}"
  V_MAJOR=$MAX_MAJ
  V_MINOR=$MAX_MIN
  V_PATCH=$((MAX_PAT + 1))
  V_CODE=$((MAX_COD + 1))
elif [ "${H_MAJ:-0}" -lt 1 ] 2>/dev/null && [ "${G_MAJ:-0}" -lt 1 ] 2>/dev/null && [ "${REL_MAJ:-0}" -lt 1 ] 2>/dev/null; then
  PREV_VER_STR="v${H_MAJ:-0}.${H_MIN:-0}.${H_PAT:-0} (${H_COD:-2500})"
  V_MAJOR=$MAX_MAJ
  V_MINOR=$MAX_MIN
  V_PATCH=$MAX_PAT
  V_CODE=$MAX_COD
else
  PREV_VER_STR="v${MAX_MAJ}.${MAX_MIN}.${MAX_PAT} (${MAX_COD})"
  V_MAJOR=$MAX_MAJ
  V_MINOR=$MAX_MIN
  V_PATCH=$((MAX_PAT + 1))
  V_CODE=$((MAX_COD + 1))
fi

cat << EOF > "$PROP_FILE"
# Auto-generated Version Properties
VERSION_MAJOR=${V_MAJOR}
VERSION_MINOR=${V_MINOR}
VERSION_PATCH=${V_PATCH}
VERSION_CODE=${V_CODE}
EOF

# Simpan versi terbaru dan keystore ke cache tersembunyi sebelum push & purge
backup_version_and_keystore
log_ok "Versi Build : ${PREV_VER_STR} ➔ v${V_MAJOR}.${V_MINOR}.${V_PATCH} (Code: ${V_CODE})"

cat << 'EOF' > .gitignore
# Android Studio & Gradle
.gradle/
build/
app/build/
captures/
.externalNativeBuild/
.cxx/
local.properties

# Local Environment & Protected Configs
.env
.env.*
!.env.example
*.keystore
!debug.keystore.base64
.acopy/
.acopy*
tools/
*.pem
*.key
*.conf
*.cfg
.*_token
temp_update/
download/
*.zip

# Kotlin & IntelliJ IDEA
.idea/
*.iml
out/

# System files
.DS_Store
Thumbs.db
EOF

log_step "TAHAP 5/5 : KOMIT, PUSH CLOUD & HAPUS KODE APK LOKAL"
purge_obsolete_tools
[ -f ".env.example" ] || touch .env.example
git rm -r --cached -q tools/ >/dev/null 2>&1 || true
git add -f .env.example >/dev/null 2>&1 || true
git add -f debug.keystore.base64 >/dev/null 2>&1 || true
git add -f app/version.properties >/dev/null 2>&1 || true
git add -f .github/workflows/build-apk.yml >/dev/null 2>&1 || true
git add -A >/dev/null 2>&1 || true
git reset -q HEAD tools/ *.pem *.key *.cfg *.conf .*_token debug.keystore >/dev/null 2>&1 || true

CHANGED_FILES_COUNT=$(git diff --cached --numstat 2>/dev/null | wc -l | tr -d ' ')
if git diff --cached --quiet; then
  git commit --allow-empty -m "chore: trigger build v${V_MAJOR}.${V_MINOR}.${V_PATCH}" --quiet
else
  git commit -m "chore: update build v${V_MAJOR}.${V_MINOR}.${V_PATCH}" --quiet
fi
TARGET_COMMIT_SHA=$(git rev-parse HEAD 2>/dev/null || echo "")
TARGET_VERSION="v${V_MAJOR}.${V_MINOR}.${V_PATCH}"
log_ok "Commit tercatat : ${TARGET_COMMIT_SHA:0:7} (${CHANGED_FILES_COUNT:-0} berkas diperbarui)"

CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "main")
log_info "Mengirim pembaruan ke remote repository (branch: $CURRENT_BRANCH)..."

set +e
PUSH_OUTPUT=$(git push -u origin "$CURRENT_BRANCH" 2>&1)
PUSH_STATUS=$?

if [ $PUSH_STATUS -ne 0 ]; then
  if echo "$PUSH_OUTPUT" | grep -qiE "fetch first|non-fast-forward|Updates were rejected"; then
    log_info "Menyelaraskan riwayat cabang remote..."
    PUSH_OUTPUT=$(git push -f -u origin "$CURRENT_BRANCH" 2>&1)
    PUSH_STATUS=$?
  fi
fi
  if [ $PUSH_STATUS -ne 0 ]; then
    echo ""
    log_warn "Gagal mengirim pembaruan ke remote:"
    echo "$PUSH_OUTPUT" | sanitize_log
    echo ""
    if [ -t 0 ]; then
      printf "👉 Konfigurasi ulang kredensial Git sekarang? [y/N, otomatis lewati 8 dtk]: "
      read -t 8 -r RETRY_REPO || RETRY_REPO="n"
      echo ""
      if [[ "$RETRY_REPO" =~ ^[Yy] ]]; then
        if switch_github_repository; then
          log_info "Mencoba push ulang setelah pembaruan kredensial..."
          PUSH_OUTPUT=$(git push -u origin "$CURRENT_BRANCH" --force 2>&1)
          PUSH_STATUS=$?
          if [ $PUSH_STATUS -eq 0 ]; then
            TARGET_COMMIT_SHA=$(git rev-parse HEAD 2>/dev/null || echo "$TARGET_COMMIT_SHA")
          fi
        fi
      fi
    fi
  fi

  # =======================================================
  # LOGIKA PEMBERSIHAN BERKAS ZIP:
  # HANYA HAPUS JIKA PUSH GIT BENAR-BENAR TER-ACTION & BERHASIL!
  # =======================================================
  if [ $PUSH_STATUS -eq 0 ]; then
    log_ok "Sinkronisasi ke cloud remote berhasil diselesaikan!"

    # Hapus arsip ZIP HANYA setelah push git benar-benar sukses ter-action
    if [ -n "$ZIP" ] && [ -f "$ZIP" ]; then
      ZIP_BASENAME=$(basename "$ZIP")
      rm -f "$ZIP" 2>/dev/null || true
      log_ok "Arsip sumber ($ZIP_BASENAME) telah ter-action ke remote & dibersihkan otomatis."
    fi

    # Hapus otomatis seluruh kode sumber & logika APK lokal pasca-push (Zero-APK-Footprint)
    purge_local_apk_footprint

    REPO_SLUG=$(extract_repo_slug)

    echo ""
    echo "======================================================="
    echo "  ☁️  RINGKASAN PIPELINE & ZERO-LOCAL-APK-FOOTPRINT"
    echo "======================================================="
    echo "  🏷️  Versi Target : v${V_MAJOR}.${V_MINOR}.${V_PATCH} (Code: ${V_CODE})"
    echo "  📌 Commit SHA   : ${TARGET_COMMIT_SHA:0:7}"
    echo "  🛡️  Status Push  : Terkirim & Sedang Dikompilasi di Cloud"
    echo "  🧹 Lokal Termux : Kode APK Dihapus (GitHub Pipeline Aman)"
    echo "======================================================="

    AUTO_DL_REPLY="n"
    if [ -t 0 ]; then
      printf "👉 Pantau kompilasi & unduh APK sekarang? [y/N, otomatis selesai dlm 8 dtk]: "
      read -t 8 -r AUTO_DL_REPLY || AUTO_DL_REPLY="n"
      echo ""
      AUTO_DL_REPLY=$(echo "$AUTO_DL_REPLY" | tr -d '\r\n ')
    fi

    if [[ "$AUTO_DL_REPLY" =~ ^[Yy] ]]; then
      MAIN_TOKEN=$(extract_auth_token)
      download_github_apk "$TARGET_COMMIT_SHA" "$TARGET_VERSION" "$REPO_SLUG" "$MAIN_TOKEN"
    else
      log_info "Unduh APK kapan saja saat kompilasi selesai dari Menu Utama (Opsi 2)."
    fi

    echo "======================================================="
    echo "  🎉 PIPELINE PEMBARUAN SELESAI (TANPA RESIDU APK LOKAL)!"
    echo "======================================================="
    return 0
  else
    echo ""
    echo "======================================================="
    log_err "❌ PUSH GIT GAGAL! KODE BELUM BERHASIL TERKIRIM KE REMOTE."
    echo "======================================================="
    if [ -n "$ZIP" ] && [ -f "$ZIP" ]; then
      ZIP_BASENAME=$(basename "$ZIP")
      log_ok "🛡️  PROTEKSI BERKAS: Arsip '$ZIP_BASENAME' AMAN & TIDAK DIHAPUS!"
      log_info "Lokasi arsip : $ZIP"
      log_info "Arsip tetap utuh sehingga Anda tidak perlu mengunduh ulang ZIP."
      log_info "Silakan periksa koneksi atau perbarui token via Opsi 5 di Menu Utama,"
      log_info "kemudian jalankan kembali Opsi 1."
    fi
    echo "======================================================="
    return 1
  fi
}

pause_and_return() {
  echo ""
  if [ -t 0 ]; then
    printf "👉 Tekan Enter untuk kembali ke Menu Utama..."
    read -r _dummy 2>/dev/null || true
  fi
}

# =======================================================
# 5. MENU UTAMA INTERAKTIF (LOOPING PENUH, PINTU KELUAR DI OPSI 0)
# =======================================================
interactive_main_menu() {
  while true; do
    ACTIVE_SLUG=$(extract_repo_slug)
    ACTIVE_TOK=$(extract_auth_token)
    if [ -n "$ACTIVE_SLUG" ]; then
      REMOTE_DISP="Terhubung (Siap Sinkronisasi)"
    else
      REMOTE_DISP="Belum Dikonfigurasi"
    fi
    if [ -n "$ACTIVE_TOK" ]; then
      AUTH_DISP="Aktif & Terproteksi"
    else
      AUTH_DISP="Belum Disetel"
    fi

    echo ""
    echo "======================================================="
    echo "   🚀 ASISTEN COPY — GITHUB PIPELINE MANAGER"
    echo "======================================================="
    echo "  ☁️  Status Remote : $REMOTE_DISP"
    echo "  🔒 Status Akses  : $AUTH_DISP"
    echo "  🛡️  Mode Lokal    : Zero-APK-Footprint (Kode APK Dihapus Pasca-Push)"
    echo "───────────────────────────────────────────────────────"
    echo "  1) ☁️  Push ke GitHub & Hapus Kode APK Lokal (CI Build)"
    echo "  2) 📥 Unduh Paket APK (/sdcard/Download)"
    echo "  3) 📦 Manajemen Rilis APK (Release Manager)"
    echo "  4) 🧹 Bersihkan Sisa File/Logika APK Lokal (Purge)"
    echo "  5) 🔄 Konfigurasi Endpoint & Kredensial Git"
    echo "  0) 🚪 Keluar (Exit)"
    echo "======================================================="
    printf "👉 Pilihan Anda [0-5, Ketik '0' untuk Keluar]: "
    read -r MODE_CHOICE 2>/dev/null || MODE_CHOICE=""
    MODE_CHOICE=$(echo "$MODE_CHOICE" | tr -d '\r\n ')

    if [ "$MODE_CHOICE" = "0" ] || [ "$MODE_CHOICE" = "q" ] || [ "$MODE_CHOICE" = "quit" ] || [ "$MODE_CHOICE" = "exit" ] || [ "$MODE_CHOICE" = "keluar" ]; then
      log_info "Selesai. Keluar dari Pipeline Manager."
      exit 0
    elif [ "$MODE_CHOICE" = "1" ]; then
      log_info "Menjalankan Mode Push Cloud & Auto-Purge Kode APK Lokal..."
      run_pipeline_update true "$PRE_DETECTED_ZIP"
      pause_and_return
    elif [ "$MODE_CHOICE" = "2" ]; then
      download_github_apk
      pause_and_return
    elif [ "$MODE_CHOICE" = "3" ]; then
      manage_github_releases
      pause_and_return
    elif [ "$MODE_CHOICE" = "4" ]; then
      run_clean_apk_workspace
      pause_and_return
    elif [ "$MODE_CHOICE" = "5" ]; then
      switch_github_repository
      pause_and_return
    elif [ -z "$MODE_CHOICE" ]; then
      log_warn "Pilihan kosong. Silakan masukkan angka [1-5] atau ketik '0' untuk keluar."
      sleep 1.2
    else
      log_warn "Pilihan '$MODE_CHOICE' tidak dikenal. Masukkan angka [1-5] atau ketik '0' untuk keluar."
      sleep 1.2
    fi
  done
}

# =======================================================
# 6. ENTRYPOINT ARGUMEN CLI ATAU MENU UTAMA INTERAKTIF
# =======================================================
CLI_RUN_MODE=""
CLI_PUSH_GITHUB=true

if [ "$#" -gt 0 ]; then
  case "$1" in
    1|--github|--push|-g)
      CLI_PUSH_GITHUB=true
      CLI_RUN_MODE="update"
      shift 2>/dev/null || true
      ;;
    --tools-only)
      CLI_PUSH_GITHUB=false
      CLI_RUN_MODE="update"
      shift 2>/dev/null || true
      ;;
    2|--download|--unduh|-dl|download)
      shift 2>/dev/null || true
      download_github_apk "$@"
      exit $?
      ;;
    3|--release|-rel)
      shift 2>/dev/null || true
      manage_github_releases "$@"
      exit $?
      ;;
    4|--clean|--purge|-c|--build|-b)
      shift 2>/dev/null || true
      run_clean_apk_workspace "$@"
      exit $?
      ;;
    5|--repo|-r)
      shift 2>/dev/null || true
      switch_github_repository "$@"
      exit $?
      ;;
    0|q|quit|exit|batal)
      log_info "Keluar dari skrip."
      exit 0
      ;;
  esac
fi

if [ -n "$1" ] && [ -f "$1" ]; then
  PRE_DETECTED_ZIP="$1"
  CLI_RUN_MODE="update"
  shift 2>/dev/null || true
fi

if [ -n "$CLI_RUN_MODE" ]; then
  run_pipeline_update "$CLI_PUSH_GITHUB" "$PRE_DETECTED_ZIP"
  exit $?
else
  interactive_main_menu
fi
