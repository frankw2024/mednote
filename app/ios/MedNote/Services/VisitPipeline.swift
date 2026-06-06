import Foundation

@MainActor
final class VisitPipeline: ObservableObject {
    @Published var step: ProcessingStep?
    @Published var errorMessage: String?

    func processRecording(
        audioURL: URL,
        durationSeconds: Int,
        visitStore: VisitStore,
        settings: SettingsStore,
        doctorLabel: String = "Phone Visit"
    ) async -> Visit? {
        errorMessage = nil
        step = .saving

        var visit = Visit(
            doctor: doctorLabel,
            duration: Visit.formatDuration(seconds: durationSeconds),
            originalLang: settings.recordLanguage.rawValue,
            isProcessing: true
        )

        do {
            let storedName = try visitStore.storeAudio(from: audioURL, visitId: visit.id)
            visit.audioFileName = storedName
        } catch {
            errorMessage = error.localizedDescription
            step = nil
            return nil
        }

        visitStore.add(visit)
        step = .transcribing

        do {
            let result = try await AIService.runPipeline(
                audioURL: visitStore.audioURL(for: visit)!,
                groqKey: settings.groqKey,
                geminiKey: settings.geminiKey,
                userLanguage: settings.language,
                recordLanguage: settings.recordLanguage,
                durationSeconds: durationSeconds
            )

            step = .analyzing
            visit.originalTranscript = result.transcriptLines
            visit.summary = result.summary
            visit.translatedTranscript = result.translatedTranscript
            visit.translatedSummary = settings.language != .en ? result.summary : nil
            visit.extractedItems = result.extractedItems
            visit.isProcessing = false
            visitStore.update(visit)
            step = .done
            try? await Task.sleep(nanoseconds: 600_000_000)
            step = nil
            return visit
        } catch {
            errorMessage = error.localizedDescription
            visit.isProcessing = false
            visit.summary = "Processing failed: \(error.localizedDescription)"
            visitStore.update(visit)
            step = nil
            return visit
        }
    }

    func processImportedFile(
        url: URL,
        visitStore: VisitStore,
        settings: SettingsStore
    ) async -> Visit? {
        await processRecording(
            audioURL: url,
            durationSeconds: 0,
            visitStore: visitStore,
            settings: settings,
            doctorLabel: "Imported Recording"
        )
    }
}
