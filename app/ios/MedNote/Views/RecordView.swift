import SwiftUI

struct RecordView: View {
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var visitStore: VisitStore
    @EnvironmentObject private var pipeline: VisitPipeline
    @EnvironmentObject private var recorder: AudioRecorderService

    @State private var isActive = false
    @State private var recordingURL: URL?
    @State private var alertMessage: String?
    @State private var finishedVisit: Visit?

    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Spacer()
                ZStack {
                    Circle()
                        .fill(isActive ? Color.red.opacity(0.15) : Color.indigo.opacity(0.12))
                        .frame(width: 180, height: 180)
                    Circle()
                        .fill(isActive ? Color.red : Color.indigo)
                        .frame(width: 120, height: 120)
                    Image(systemName: isActive ? "stop.fill" : "mic.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(.white)
                }
                .onTapGesture { Task { await toggleRecording() } }

                Text(isActive ? formatTime(recorder.elapsedSeconds) : "Tap to record")
                    .font(.title2.monospacedDigit().weight(.semibold))

                Text(isActive ? "Recording in-person visit…" : "Record at the clinic or consultation room")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                if let step = pipeline.step {
                    HStack {
                        ProgressView()
                        Text(step.label)
                    }
                    .font(.subheadline)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Record Visit")
            .alert("MedNote", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(alertMessage ?? "")
            }
            .navigationDestination(item: $finishedVisit) { visit in
                VisitDetailView(visit: visit)
            }
        }
    }

    private func toggleRecording() async {
        if isActive {
            await stopAndProcess()
        } else {
            await startRecording()
        }
    }

    private func startRecording() async {
        guard await recorder.requestPermission() else {
            alertMessage = "Microphone permission is required."
            return
        }
        do {
            let url = try recorder.startRecording(to: visitStore.audioDirectory)
            recordingURL = url
            isActive = true
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    private func stopAndProcess() async {
        isActive = false
        guard let url = recorder.stopRecording() ?? recordingURL else { return }
        recordingURL = nil
        if let visit = await pipeline.processRecording(
            audioURL: url,
            durationSeconds: recorder.elapsedSeconds,
            visitStore: visitStore,
            settings: settings,
            doctorLabel: "Clinic Visit"
        ) {
            finishedVisit = visit
        }
    }

    private func formatTime(_ seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
