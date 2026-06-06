import AVFoundation
import UIKit

@MainActor
final class AudioRecorderService: NSObject, ObservableObject {
    @Published private(set) var isRecording = false
    @Published private(set) var elapsedSeconds = 0

    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var outputURL: URL?

    func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                cont.resume(returning: granted)
            }
        }
    }

    func startRecording(to directory: URL) throws -> URL {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth, .mixWithOthers])
        try session.setActive(true)

        let name = "rec_\(Int(Date().timeIntervalSince1970)).m4a"
        let url = directory.appendingPathComponent(name)
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder?.delegate = self
        recorder?.isMeteringEnabled = true
        guard recorder?.record() == true else {
            throw RecorderError.failedToStart
        }
        outputURL = url
        isRecording = true
        elapsedSeconds = 0
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.elapsedSeconds += 1
            }
        }
        return url
    }

    func stopRecording() -> URL? {
        timer?.invalidate()
        timer = nil
        recorder?.stop()
        isRecording = false
        let url = outputURL
        recorder = nil
        outputURL = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return url
    }

    enum RecorderError: LocalizedError {
        case failedToStart
        case permissionDenied

        var errorDescription: String? {
            switch self {
            case .failedToStart: return "Could not start recording."
            case .permissionDenied: return "Microphone permission is required."
            }
        }
    }
}

extension AudioRecorderService: AVAudioRecorderDelegate {}

enum CallService {
    static func normalizedPhone(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.hasPrefix("+") {
            let digits = trimmed.dropFirst().filter(\.isNumber)
            return digits.isEmpty ? nil : "+\(digits)"
        }
        let digits = trimmed.filter(\.isNumber)
        return digits.isEmpty ? nil : digits
    }

    static func dial(_ phone: String) {
        guard let normalized = normalizedPhone(phone),
              let url = URL(string: "tel://\(normalized)") else { return }
        UIApplication.shared.open(url)
    }
}
