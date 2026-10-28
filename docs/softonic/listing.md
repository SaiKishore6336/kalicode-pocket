# KaliCode Pocket — Softonic submission package

Everything needed to submit KaliCode Pocket 1.1.1 through Softonic's developer
portal (https://publishers.softonic.com). Submissions require a Softonic
publisher account and go through their review before listing.

## Files

- `KaliCodePocket-1.1.1-release.apk` — in `dist/` (not committed; see the GitHub release)
- `icon-512.png` — 512×512 store icon (matches the in-app launcher)
- `screenshot-dashboard.png`, `screenshot-editor.png`, `screenshot-python.png`

## Listing details

| Field | Value |
| --- | --- |
| App name | KaliCode Pocket |
| Version | 1.1.1 (versionCode 3) |
| Category | Developer Tools |
| License | Free, MIT (app code) |
| Language | English |
| Developer / publisher | Sai Kishore |
| Website | https://github.com/SaiKishore6336/kalicode-pocket |
| Contact email | _fill in your email before submitting_ |
| Requirements | Android 8.0+ (API 26), arm64-v8a (universal APK also includes x86_64) |
| File size | ~41 MB |
| APK SHA-256 | `4acf4112b73b6b653f7a196f95b055899d9bbbf8ce396fdf9f82302faba32fef` |
| Signing certificate SHA-256 | `CB:8B:54:2C:9A:F6:17:2F:87:0C:62:53:CB:65:C8:98:74:06:A0:76:20:E4:E1:12:F5:FA:D7:7C:7B:6C:EA:68` |

## Short description (1 line)

A fast, native Android code editor with an embedded Python 3.12 runtime.

## Full description

KaliCode Pocket is a fast, native Android code editor built for phones. The
editor is fully native — Kotlin, Android XML Views, Sora Editor, and Monarch
grammars — with no Electron, no WebView-based editing, no ads, and no telemetry.

Features:

- 19 languages with syntax highlighting: Python, JavaScript, TypeScript, HTML,
  CSS, JSON, Markdown, Java, Kotlin, XML, YAML, Shell, SQL, C/C++, Go, Rust,
  PHP, Ruby, and Lua.
- Embedded CPython 3.12 runtime — run Python files directly on the phone with
  no Termux or external app. Stdout, stderr, and tracebacks appear in a native
  output screen.
- Interactive `input()` calls open a native dialog, so beginner programs work
  on-device.
- Sibling `.py` imports, relative file access, `sys.argv`, and
  `if __name__ == "__main__":` all work from the opened project.
- Up to 8 open tabs in one editor instance.
- Find and replace with match counts, next/previous, replace, and replace-all.
- Built-in symbol keyboard for `{}`, `()`, `[]`, `<>`, quotes, `=`, and Tab.
- Open real folders through Android's Storage Access Framework, with recent
  projects and a bundled playground.
- HTML/CSS/JavaScript live preview with relative asset loading and a captured
  JavaScript console.
- Dark Kali and light Paper themes, auto-save, manual save, and adjustable
  font size, wrapping, line numbers, and tab size.
- No `INTERNET` permission. No ads, analytics, or telemetry.

Python limits: pure-Python modules placed in the project work; `pip` is not
supported and desktop/native modules (e.g. pyttsx3, tkinter, pygame) do not
run on Android.

## What's new (1.1.1)

- Fixed an issue where Enter in a Python file added automatic indentation; new
  Python lines now start clean (use Tab for blocks).
- The release APK is now signed with the developer's private release key. If
  you installed an earlier test-signed build, uninstall it before updating.

## Submission steps

1. Sign in at the Softonic publisher portal (or create a publisher account —
   requires email verification).
2. Submit a new app for Android and upload
   `dist/KaliCodePocket-1.1.1-release.apk`.
3. Fill in the listing fields above; upload `icon-512.png` and the three
   screenshots.
4. Use the short/full description text above.
5. Submit for review. Softonic reviews apps before listing; signing-certificate
   mismatch and test-signed APKs are common rejection reasons, which this
   package now avoids.

A ready-to-upload bundle including the APK is at
`dist/KaliCodePocket-1.1.1-softonic.zip` after building.
