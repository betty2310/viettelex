#!/bin/zsh
# verify-launchable.sh <App.app> — chặn bản ký mà macOS sẽ KHÔNG cho chạy.
#
# Bài học 1.8.7 (02/10/2026, issue #110): app ký bằng "Developer ID Application:
# SENPRINTS LLC" trong khi embedded.provisionprofile (iCloud) chỉ chứa chứng chỉ
# "Phil Trinh". codesign/notarize/spctl đều PASS, nhưng lúc chạy taskgated báo
# "Unsatisfied entitlements … Disallowing" ⇒ bộ gõ không khởi động, người dùng không
# gõ được gì và nút cập nhật trong app cũng chết theo.
#
# Kiểm tra (không cần keychain, không chạy app):
#   1. Có embedded.provisionprofile ⇒ chứng chỉ ký app PHẢI nằm trong
#      DeveloperCertificates của profile, profile chưa hết hạn, App ID khớp.
#   2. Mọi entitlement hạn chế (com.apple.developer.*, application-identifier) app
#      mang theo PHẢI được profile cho phép (giá trị khớp, hoặc profile dùng "TEAM.*").
#   3. Không có profile ⇒ app KHÔNG được mang entitlement hạn chế nào.
# Thoát 1 + thông báo rõ nếu sai. Gọi từ dev-install.sh, notarize-install.sh, make-release.sh.
set -e
APP="${1:?usage: verify-launchable.sh <App.app>}"
fail() { echo "✗ verify-launchable: $*" >&2; exit 1; }
[ -d "$APP" ] || fail "không thấy $APP"

TMP="$(mktemp -d -t vtverify)"
trap 'rm -rf "$TMP"' EXIT

codesign -d --entitlements :- "$APP" > "$TMP/ent.plist" 2>/dev/null || fail "không đọc được entitlements của $APP"
pe() { /usr/libexec/PlistBuddy -c "Print :$1" "$TMP/ent.plist" 2>/dev/null || true; }
RESTRICTED=()
for k in com.apple.application-identifier com.apple.developer.team-identifier \
         com.apple.developer.ubiquity-kvstore-identifier com.apple.developer.icloud-services \
         com.apple.developer.icloud-container-identifiers com.apple.developer.ubiquity-container-identifiers; do
  [ -n "$(pe "$k")" ] && RESTRICTED+=("$k")
done

PROF="$APP/Contents/embedded.provisionprofile"
if [ ! -f "$PROF" ]; then
  [ ${#RESTRICTED[@]} -eq 0 ] || fail "app mang entitlement hạn chế (${RESTRICTED[*]}) nhưng không có embedded.provisionprofile ⇒ macOS sẽ chặn chạy"
  echo "✓ verify-launchable: không profile, không entitlement hạn chế"
  exit 0
fi

security cms -D -i "$PROF" > "$TMP/prof.plist" 2>/dev/null || fail "không giải mã được embedded.provisionprofile"
pp() { /usr/libexec/PlistBuddy -c "Print :$1" "$TMP/prof.plist" 2>/dev/null || true; }

# 1. Chứng chỉ ký ∈ DeveloperCertificates.
codesign -d --extract-certificates="$TMP/sig" "$APP" >/dev/null 2>&1 || fail "không trích được chứng chỉ ký"
SIG_SHA=$(openssl x509 -inform der -in "$TMP/sig0" -noout -fingerprint -sha1 | sed 's/.*=//; s/://g')
SIG_CN=$(openssl x509 -inform der -in "$TMP/sig0" -noout -subject | sed -n 's/.*CN=\([^,]*\).*/\1/p')
N=$(plutil -extract DeveloperCertificates raw "$TMP/prof.plist" 2>/dev/null || echo 0)
FOUND=0; ALLOWED=()
for i in $(seq 0 $((N - 1))); do
  plutil -extract "DeveloperCertificates.$i" raw "$TMP/prof.plist" | base64 -d > "$TMP/pc$i.der"
  S=$(openssl x509 -inform der -in "$TMP/pc$i.der" -noout -fingerprint -sha1 | sed 's/.*=//; s/://g')
  ALLOWED+=("$(openssl x509 -inform der -in "$TMP/pc$i.der" -noout -subject | sed -n 's/.*CN=\([^,]*\).*/\1/p')")
  [ "$S" = "$SIG_SHA" ] && FOUND=1
done
[ "$FOUND" = 1 ] || fail "app ký bằng '$SIG_CN' nhưng profile chỉ cho phép: ${(j:, :)ALLOWED} ⇒ macOS sẽ chặn chạy (lỗi 1.8.7). Đổi SIGN_ID về chứng chỉ trong profile, hoặc tạo profile mới cho chứng chỉ này."

# Hạn dùng.
EXP=$(plutil -extract ExpirationDate raw "$TMP/prof.plist" 2>/dev/null || true)
if [ -n "$EXP" ]; then
  EXP_S=$(LC_ALL=C date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$EXP" +%s 2>/dev/null || true)
  [ -z "$EXP_S" ] || [ "$EXP_S" -gt "$(date +%s)" ] || fail "profile đã hết hạn ($EXP)"
fi

# 2. Entitlement hạn chế ⊆ profile.
for k in "${RESTRICTED[@]}"; do
  have="$(pe "$k")"; allow="$(pp "Entitlements:$k")"
  [ -n "$allow" ] || fail "profile không cho phép entitlement $k"
  [ "$have" = "$allow" ] && continue
  # Wildcard "TEAM.*" (kvstore / application-identifier).
  case "$allow" in *'.*') [[ "$have" == "${allow%\*}"* ]] && continue ;; esac
  # Mảng (container ids, services): so từng dòng giá trị của app có trong profile.
  if [[ "$have" == Array* ]]; then
    ok=1
    for v in ${(f)"$(print -r -- "$have" | sed -n 's/^ *\([^{}]*[^ ]\) *$/\1/p' | grep -v '^Array')"}; do
      print -r -- "$allow" | grep -qF -- "$v" || print -r -- "$allow" | grep -qF -- '*' || ok=0
    done
    [ "$ok" = 1 ] && continue
  fi
  fail "entitlement $k='$have' không khớp profile ('$allow')"
done

echo "✓ verify-launchable: ký '$SIG_CN' khớp profile, ${#RESTRICTED[@]} entitlement hạn chế đều được phép"
