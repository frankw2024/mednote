import Foundation
import Security

@MainActor
final class SettingsStore: ObservableObject {
    @Published var groqKey: String {
        didSet { KeychainHelper.save(groqKey, for: "recallmd_groq") }
    }
    @Published var geminiKey: String {
        didSet { KeychainHelper.save(geminiKey, for: "recallmd_gemini") }
    }
    @Published var doctorPhone: String {
        didSet { UserDefaults.standard.set(doctorPhone, forKey: "recallmd_call_phone") }
    }
    @Published var language: AppLanguage {
        didSet { UserDefaults.standard.set(language.rawValue, forKey: "recallmd_lang") }
    }
    @Published var recordLanguage: AppLanguage {
        didSet { UserDefaults.standard.set(recordLanguage.rawValue, forKey: "recallmd_rec_lang") }
    }

    init() {
        groqKey = KeychainHelper.load("recallmd_groq") ?? ""
        geminiKey = KeychainHelper.load("recallmd_gemini") ?? ""
        doctorPhone = UserDefaults.standard.string(forKey: "recallmd_call_phone") ?? ""
        let lang = UserDefaults.standard.string(forKey: "recallmd_lang") ?? "en"
        language = AppLanguage(rawValue: lang) ?? .en
        let recLang = UserDefaults.standard.string(forKey: "recallmd_rec_lang") ?? "en"
        recordLanguage = AppLanguage(rawValue: recLang) ?? .en
    }

    var hasTranscriptionKey: Bool { !groqKey.trimmingCharacters(in: .whitespaces).isEmpty }
    var hasSummaryKey: Bool {
        !groqKey.trimmingCharacters(in: .whitespaces).isEmpty ||
        !geminiKey.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

enum KeychainHelper {
    static func save(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]
        SecItemDelete(query as CFDictionary)
        guard !value.isEmpty else { return }
        SecItemAdd(query as CFDictionary, nil)
    }

    static func load(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }
}
