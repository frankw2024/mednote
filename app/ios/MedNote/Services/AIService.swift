import Foundation

struct AIService {
    struct PipelineResult {
        var transcript: String
        var transcriptLines: [String]
        var summary: String
        var extractedItems: [ExtractedItem]
        var translatedTranscript: [String]?
    }

    static func runPipeline(
        audioURL: URL,
        groqKey: String,
        geminiKey: String,
        userLanguage: AppLanguage,
        recordLanguage: AppLanguage,
        durationSeconds: Int
    ) async throws -> PipelineResult {
        let gKey = groqKey.trimmingCharacters(in: .whitespaces)
        let cKey = geminiKey.trimmingCharacters(in: .whitespaces)
        let userLangName = userLanguage.label
        let origLang = recordLanguage.rawValue

        var originalText = "[No transcript — add a Groq API key in Settings]"
        if !gKey.isEmpty {
            if let whisper = try? await groqWhisper(fileURL: audioURL, apiKey: gKey, language: recordLanguage.whisperCode) {
                originalText = whisper
            }
        }

        let lines = splitTranscriptLines(originalText)
        let shouldTranslate = userLanguage != recordLanguage && !originalText.hasPrefix("[")

        async let summaryTask = callGemini(
            system: "Medical appointment summarizer. Warm, concise, plain language.",
            message: """
            Brief summary in \(userLangName). Headings: 📋 Findings 💊 Medications ⚠️ Watch for 📅 Next steps

            Transcript:
            \(originalText)

            Write entirely in \(userLangName).
            """,
            geminiKey: cKey,
            groqKey: gKey
        )

        async let extractTask = callGemini(
            system: "Medical data extractor. Return ONLY a valid JSON array, no markdown, no other text.",
            message: """
            Extract actionable items from the transcript. Respond ONLY with a valid JSON array (no markdown, no extra text), each item: {"type":"medication"|"appointment"|"action","text":"description in \(userLangName)","time":"time/date","suggestedHour":8}

            IMPORTANT: All "text" values MUST be written in \(userLangName).

            Transcript:
            \(originalText)
            """,
            geminiKey: cKey,
            groqKey: gKey
        )

        async let translateTask: [String]? = {
            guard shouldTranslate else { return nil }
            let result = await callGemini(
                system: "Medical transcript translator. Keep drug names, dosages, and numbers accurate. Return ONLY the translated transcript.",
                message: "Translate into \(userLangName):\n\n\(originalText)",
                geminiKey: cKey,
                groqKey: gKey
            )
            guard let result else { return nil }
            return splitTranscriptLines(result)
        }()

        let summary = await summaryTask ?? "AI summary could not be generated — add a Groq or Gemini key in Settings."
        let extractRaw = await extractTask
        let translated = await translateTask
        let items = parseExtractedItems(extractRaw, language: userLanguage)

        _ = durationSeconds
        return PipelineResult(
            transcript: originalText,
            transcriptLines: lines.isEmpty ? [originalText] : lines,
            summary: summary,
            extractedItems: items,
            translatedTranscript: translated
        )
    }

    // MARK: - Groq Whisper

    private static func groqWhisper(fileURL: URL, apiKey: String, language: String?) async throws -> String? {
        let data = try Data(contentsOf: fileURL)
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        let ext = fileURL.pathExtension.isEmpty ? "m4a" : fileURL.pathExtension
        let mime = ext == "wav" ? "audio/wav" : "audio/mp4"

        func append(_ string: String) {
            body.append(Data(string.utf8))
        }

        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.\(ext)\"\r\n")
        append("Content-Type: \(mime)\r\n\r\n")
        body.append(data)
        append("\r\n")
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
        append("whisper-large-v3-turbo\r\n")
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
        append("verbose_json\r\n")
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"temperature\"\r\n\r\n")
        append("0\r\n")
        if let language {
            append("--\(boundary)\r\n")
            append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
            append("\(language)\r\n")
        }
        append("--\(boundary)--\r\n")

        var request = URLRequest(url: URL(string: "https://api.groq.com/openai/v1/audio/transcriptions")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body

        let (responseData, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
        struct WhisperResponse: Decodable { let text: String? }
        return try JSONDecoder().decode(WhisperResponse.self, from: responseData).text
    }

    // MARK: - Gemini / Groq chat

    private static func callGemini(system: String, message: String, geminiKey: String, groqKey: String) async -> String? {
        let prompt = "\(system)\n\n\(message)"
        let gKey = geminiKey.trimmingCharacters(in: .whitespaces)
        let qKey = groqKey.trimmingCharacters(in: .whitespaces)

        if !gKey.isEmpty {
            for model in ["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest"] {
                if let text = await geminiGenerate(prompt: prompt, apiKey: gKey, model: model) {
                    return text
                }
            }
        }

        if !qKey.isEmpty {
            return await groqChat(prompt: prompt, apiKey: qKey)
        }
        return nil
    }

    private static func geminiGenerate(prompt: String, apiKey: String, model: String) async -> String? {
        guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(apiKey)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "contents": [["role": "user", "parts": [["text": prompt]]]],
            "generationConfig": ["maxOutputTokens": 1500, "temperature": 0.3]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
        struct GeminiResponse: Decodable {
            struct Candidate: Decodable {
                struct Content: Decodable {
                    struct Part: Decodable { let text: String? }
                    let parts: [Part]?
                }
                let content: Content?
            }
            struct ErrorBody: Decodable { struct Err: Decodable { let message: String? }; let error: Err? }
            let candidates: [Candidate]?
            let error: ErrorBody.Err?
        }
        guard let decoded = try? JSONDecoder().decode(GeminiResponse.self, from: data),
              decoded.error == nil else { return nil }
        return decoded.candidates?.first?.content?.parts?.first?.text
    }

    private static func groqChat(prompt: String, apiKey: String) async -> String? {
        var request = URLRequest(url: URL(string: "https://api.groq.com/openai/v1/chat/completions")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        let body: [String: Any] = [
            "model": "llama-3.3-70b-versatile",
            "messages": [["role": "user", "content": prompt]],
            "max_tokens": 1500,
            "temperature": 0.3
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
        struct GroqResponse: Decodable {
            struct Choice: Decodable {
                struct Message: Decodable { let content: String? }
                let message: Message?
            }
            let choices: [Choice]?
        }
        return (try? JSONDecoder().decode(GroqResponse.self, from: data))?.choices?.first?.message?.content
    }

    // MARK: - Helpers

    private static func splitTranscriptLines(_ text: String) -> [String] {
        guard !text.isEmpty else { return [] }
        var lines: [String] = []
        text.enumerateSubstrings(in: text.startIndex..., options: [.bySentences, .substringNotRequired]) { substring, _, _, _ in
            if let s = substring?.trimmingCharacters(in: .whitespacesAndNewlines), s.count > 1 {
                lines.append(s)
            }
        }
        if lines.isEmpty {
            return text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        }
        return lines
    }

    private static func parseExtractedItems(_ raw: String?, language: AppLanguage) -> [ExtractedItem] {
        guard var raw else { return [] }
        raw = raw.replacingOccurrences(of: "```json", with: "").replacingOccurrences(of: "```", with: "")
        if let start = raw.firstIndex(of: "["), let end = raw.lastIndex(of: "]") {
            raw = String(raw[start...end])
        }
        struct RawItem: Decodable {
            let type: String?
            let text: String?
            let time: String?
            let suggestedHour: Int?
        }
        guard let data = raw.data(using: .utf8),
              let parsed = try? JSONDecoder().decode([RawItem].self, from: data) else { return [] }
        return parsed.enumerated().compactMap { idx, item in
            guard let text = item.text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
            let typeRaw = item.type ?? "action"
            let type = ExtractedItem.ItemType(rawValue: typeRaw) ?? .action
            return ExtractedItem(
                id: "ei\(Int(Date().timeIntervalSince1970 * 1000) + idx)",
                type: type,
                text: text,
                time: item.time ?? "",
                suggestedHour: item.suggestedHour ?? 8,
                status: "provisional"
            )
        }
    }
}
