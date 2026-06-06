import SwiftUI
import AVFoundation

struct VisitDetailView: View {
    @EnvironmentObject private var visitStore: VisitStore
    let visit: Visit

    @State private var player: AVAudioPlayer?
    @State private var isPlaying = false
    @State private var showTranscript = true

    private var current: Visit {
        visitStore.visits.first(where: { $0.id == visit.id }) ?? visit
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                if current.isProcessing {
                    HStack {
                        ProgressView()
                        Text("AI is analyzing this visit…")
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                if visitStore.audioURL(for: current) != nil {
                    playButton
                }
                if let summary = current.summary {
                    section(title: "AI Summary", icon: "text.alignleft") {
                        Text(summary)
                            .font(.body)
                            .textSelection(.enabled)
                    }
                }
                transcriptSection
                if !current.extractedItems.isEmpty {
                    section(title: "Extracted Items", icon: "checklist") {
                        ForEach(current.extractedItems) { item in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: icon(for: item.type))
                                    .foregroundStyle(.indigo)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.text).font(.subheadline)
                                    if !item.time.isEmpty {
                                        Text(item.time).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle(current.doctor)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(current.date) · \(current.time)")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Duration: \(current.duration)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var playButton: some View {
        Button {
            togglePlayback()
        } label: {
            Label(isPlaying ? "Pause audio" : "Play audio", systemImage: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
    }

    private var transcriptSection: some View {
        let lines = current.translatedTranscript ?? current.originalTranscript
        return Group {
            if !lines.isEmpty {
                section(title: current.translatedTranscript != nil ? "Translated Transcript" : "Transcript", icon: "waveform") {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.subheadline)
                            .padding(.vertical, 2)
                    }
                }
            }
        }
    }

    private func section<Content: View>(title: String, icon: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title, systemImage: icon)
                .font(.headline)
            content()
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func icon(for type: ExtractedItem.ItemType) -> String {
        switch type {
        case .medication: return "pills.fill"
        case .appointment: return "calendar"
        case .action: return "exclamationmark.circle"
        }
    }

    private func togglePlayback() {
        guard let url = visitStore.audioURL(for: current) else { return }
        if isPlaying {
            player?.stop()
            isPlaying = false
            return
        }
        do {
            player = try AVAudioPlayer(contentsOf: url)
            player?.play()
            isPlaying = true
        } catch {}
    }
}
