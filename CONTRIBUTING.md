# Contributing to MedNote

Thank you for your interest in contributing! 🎉

MedNote is a single-file browser app — contributions don't require any build tooling.

---

## 🐛 Reporting Bugs

1. Check [existing issues](../../issues) first to avoid duplicates
2. Open a new issue with:
   - Browser and OS version
   - Steps to reproduce
   - Expected vs actual behaviour
   - Screenshots if relevant

---

## 💡 Suggesting Features

Open an issue with the `enhancement` label. Describe:
- The problem you're trying to solve
- Your proposed solution
- Any alternatives you considered

---

## 🔧 Making Changes

### Setup

No build step required.

```bash
git clone https://github.com/fwang2024/mednote.git
cd mednote
# Open RecallMD_web.html or RecallMD_v12.html directly in your browser
```

### Guidelines

- **Keep it single-file** — both HTML files must remain self-contained (no external JS/CSS imports beyond the existing CDN links)
- **Maintain i18n** — any new user-facing string must be added to all 10 language entries in the `T` and `T_EXTRA` translation objects
- **No new API dependencies** — prefer using the existing Groq/Gemini calls rather than adding new third-party services
- **Test in both layouts** — changes should work in both `RecallMD_web.html` (desktop) and `RecallMD_v12.html` (mobile)
- **Test in both themes** — dark and light mode

### Translation Keys

When adding a new UI string, add it to every language block in both `T` and `T_EXTRA`:

```js
// In T object (short strings: labels, buttons, badges)
en: { myNewKey: "My string" },
zh: { myNewKey: "我的字符串" },
fr: { myNewKey: "Ma chaîne" },
// ... all 10 languages

// In T_EXTRA object (longer strings: descriptions, messages)
en: { myNewMessage: "My longer message" },
// ... all 10 languages
```

### Pull Requests

1. Fork the repo and create a branch: `git checkout -b feature/my-feature`
2. Make your changes
3. Test in Chrome and Safari (Web Speech API support varies)
4. Open a PR with a clear description of what changed and why

---

## 📋 Good First Issues

- Adding a missing translation string
- Fixing a UI alignment issue in elder mode
- Adding a new export format
- Improving accessibility (ARIA labels, keyboard navigation)

---

## 🙏 Code of Conduct

Be kind and respectful. This project is built for patients and caregivers — keep that mission in mind.
