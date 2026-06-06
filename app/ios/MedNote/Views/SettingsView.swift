import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("Groq API key", text: $settings.groqKey)
                        .textContentType(.password)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    SecureField("Gemini API key (optional)", text: $settings.geminiKey)
                        .textContentType(.password)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                } header: {
                    Text("API Keys")
                } footer: {
                    Text("Groq (console.groq.com) powers transcription and summaries. Gemini (aistudio.google.com) is optional. Keys are stored in the iOS Keychain on your device only.")
                }

                Section("Language") {
                    Picker("App language", selection: $settings.language) {
                        ForEach(AppLanguage.allCases) { lang in
                            Text(lang.label).tag(lang)
                        }
                    }
                    Picker("Doctor speaks", selection: $settings.recordLanguage) {
                        ForEach(AppLanguage.allCases) { lang in
                            Text(lang.label).tag(lang)
                        }
                    }
                }

                Section("Default phone") {
                    TextField("Doctor phone number", text: $settings.doctorPhone)
                        .keyboardType(.phonePad)
                }

                Section {
                    Link("Get free Groq key", destination: URL(string: "https://console.groq.com")!)
                    Link("Get Gemini key", destination: URL(string: "https://aistudio.google.com")!)
                }

                Section {
                    HStack {
                        Text("Transcription")
                        Spacer()
                        Text(settings.hasTranscriptionKey ? "Ready" : "Needs Groq key")
                            .foregroundStyle(settings.hasTranscriptionKey ? .green : .orange)
                    }
                    HStack {
                        Text("AI summaries")
                        Spacer()
                        Text(settings.hasSummaryKey ? "Ready" : "Needs key")
                            .foregroundStyle(settings.hasSummaryKey ? .green : .orange)
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }
}
