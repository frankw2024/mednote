import Foundation

struct Visit: Identifiable, Codable, Equatable, Hashable {
    var id: String
    var doctor: String
    var specialty: String
    var date: String
    var time: String
    var duration: String
    var originalLang: String
    var originalTranscript: [String]
    var translatedTranscript: [String]?
    var summary: String?
    var translatedSummary: String?
    var audioFileName: String?
    var extractedItems: [ExtractedItem]
    var isProcessing: Bool

    init(
        id: String = "v\(Int(Date().timeIntervalSince1970 * 1000))",
        doctor: String = "Phone Visit",
        specialty: String = "",
        date: String = Visit.formatDate(Date()),
        time: String = Visit.formatTime(Date()),
        duration: String = "0m 0s",
        originalLang: String = "en",
        originalTranscript: [String] = [],
        translatedTranscript: [String]? = nil,
        summary: String? = nil,
        translatedSummary: String? = nil,
        audioFileName: String? = nil,
        extractedItems: [ExtractedItem] = [],
        isProcessing: Bool = false
    ) {
        self.id = id
        self.doctor = doctor
        self.specialty = specialty
        self.date = date
        self.time = time
        self.duration = duration
        self.originalLang = originalLang
        self.originalTranscript = originalTranscript
        self.translatedTranscript = translatedTranscript
        self.summary = summary
        self.translatedSummary = translatedSummary
        self.audioFileName = audioFileName
        self.extractedItems = extractedItems
        self.isProcessing = isProcessing
    }

    static func formatDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        f.locale = Locale(identifier: "en_US")
        return f.string(from: date)
    }

    static func formatTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f.string(from: date)
    }

    static func formatDuration(seconds: Int) -> String {
        guard seconds > 0 else { return "uploaded" }
        return "\(seconds / 60)m \(seconds % 60)s"
    }
}

struct ExtractedItem: Identifiable, Codable, Equatable {
    var id: String
    var type: ItemType
    var text: String
    var time: String
    var suggestedHour: Int
    var status: String

    enum ItemType: String, Codable {
        case medication
        case appointment
        case action
    }
}

enum AppLanguage: String, CaseIterable, Identifiable {
    case en, zh, fr, de, ja, ko, es, hi, vi, ar

    var id: String { rawValue }

    var label: String {
        switch self {
        case .en: return "English"
        case .zh: return "中文"
        case .fr: return "Français"
        case .de: return "Deutsch"
        case .ja: return "日本語"
        case .ko: return "한국어"
        case .es: return "Español"
        case .hi: return "हिंदी"
        case .vi: return "Tiếng Việt"
        case .ar: return "العربية"
        }
    }

    var whisperCode: String? {
        rawValue
    }
}

enum ProcessingStep: Int, CaseIterable {
    case saving = 1
    case transcribing
    case analyzing
    case done

    var label: String {
        switch self {
        case .saving: return "Saving recording…"
        case .transcribing: return "Transcribing audio…"
        case .analyzing: return "Creating summary…"
        case .done: return "Done!"
        }
    }
}

enum CallVisitPhase: Equatable {
    case idle
    case recording
    case onCall
    case processing
}
