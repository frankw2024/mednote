import SwiftUI
import UniformTypeIdentifiers

struct CallVisitView: View {
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var visitStore: VisitStore
    @EnvironmentObject private var pipeline: VisitPipeline
    @EnvironmentObject private var recorder: AudioRecorderService
    @Environment(\.scenePhase) private var scenePhase

    @State private var phase: CallVisitPhase = .idle
    @State private var recordingURL: URL?
    @State private var alertMessage: String?
    @State private var showImport = false
    @State private var finishedVisit: Visit?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    infoCard
                    phoneField
                    primaryButton
                    if phase == .onCall || phase == .recording {
                        onCallPanel
                    }
                    importSection
                    if let step = pipeline.step {
                        processingBanner(step)
                    }
                }
                .padding()
            }
            .navigationTitle("Phone Visit")
            .background(Color(.systemGroupedBackground))
            .alert("MedNote", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(alertMessage ?? "")
            }
            .fileImporter(
                isPresented: $showImport,
                allowedContentTypes: [.audio, .mpeg4Audio, .mp3, .wav, .aiff],
                allowsMultipleSelection: false
            ) { result in
                Task { await handleImport(result) }
            }
            .navigationDestination(item: $finishedVisit) { visit in
                VisitDetailView(visit: visit)
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active, phase == .onCall, recorder.isRecording {
                    // User returned from Phone app — ready to finish
                }
            }
        }
    }

    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("One-tap call workflow", systemImage: "bolt.fill")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.orange)
            Text("1. MedNote starts recording\n2. Phone app opens to dial your doctor\n3. Use speakerphone during the call\n4. Return here and tap End & Transcribe")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("If audio is quiet during the call, import your Voice Memo below.")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var phoneField: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Doctor's phone number")
                .font(.subheadline.weight(.semibold))
            TextField("+1 555 123 4567", text: $settings.doctorPhone)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .padding()
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var primaryButton: some View {
        Group {
            switch phase {
            case .idle:
                Button(action: { Task { await startCallVisit() } }) {
                    Label("Call & Record", systemImage: "phone.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
            case .recording, .onCall:
                Button(action: { Task { await finishCallVisit() } }) {
                    Label("End Call & Transcribe", systemImage: "stop.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
            case .processing:
                ProgressView("Processing…")
                    .frame(maxWidth: .infinity)
                    .padding()
            }
        }
    }

    private var onCallPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Circle()
                    .fill(.red)
                    .frame(width: 10, height: 10)
                Text(phase == .onCall ? "Call in progress" : "Recording…")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)
                Spacer()
                Text(formatTime(recorder.elapsedSeconds))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Text("Use speakerphone. When the call ends, return to MedNote and tap End Call & Transcribe.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Button("Redial") {
                CallService.dial(settings.doctorPhone)
            }
            .font(.caption.weight(.semibold))
        }
        .padding()
        .background(Color.green.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var importSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("After the call")
                .font(.subheadline.weight(.semibold))
            Button {
                showImport = true
            } label: {
                Label("Import Voice Memo / audio file", systemImage: "square.and.arrow.down")
                    .frame(maxWidth: .infinity)
                    .padding()
            }
            .buttonStyle(.bordered)
        }
    }

    private func processingBanner(_ step: ProcessingStep) -> some View {
        HStack {
            ProgressView()
            Text(step.label).font(.subheadline)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.indigo.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func startCallVisit() async {
        guard CallService.normalizedPhone(settings.doctorPhone) != nil else {
            alertMessage = "Enter a valid phone number."
            return
        }
        guard await recorder.requestPermission() else {
            alertMessage = "Microphone permission is required."
            return
        }
        do {
            let url = try recorder.startRecording(to: visitStore.audioDirectory)
            recordingURL = url
            phase = .recording
            try await Task.sleep(nanoseconds: 800_000_000)
            CallService.dial(settings.doctorPhone)
            phase = .onCall
        } catch {
            alertMessage = error.localizedDescription
            phase = .idle
        }
    }

    private func finishCallVisit() async {
        phase = .processing
        guard let url = recorder.stopRecording() ?? recordingURL else {
            alertMessage = "No recording found."
            phase = .idle
            return
        }
        recordingURL = nil
        if let visit = await pipeline.processRecording(
            audioURL: url,
            durationSeconds: recorder.elapsedSeconds,
            visitStore: visitStore,
            settings: settings,
            doctorLabel: "Phone Visit"
        ) {
            finishedVisit = visit
        }
        phase = .idle
    }

    private func handleImport(_ result: Result<[URL], Error>) async {
        guard case .success(let urls) = result, let url = urls.first else { return }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        phase = .processing
        if let visit = await pipeline.processImportedFile(url: url, visitStore: visitStore, settings: settings) {
            finishedVisit = visit
        }
        phase = .idle
    }

    private func formatTime(_ seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}