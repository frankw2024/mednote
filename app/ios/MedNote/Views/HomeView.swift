import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var visitStore: VisitStore
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    quickActions
                    if visitStore.visits.isEmpty {
                        emptyState
                    } else {
                        recentVisits
                    }
                }
                .padding()
            }
            .navigationTitle("MedNote")
            .background(Color(.systemGroupedBackground))
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🩺 MedNote")
                .font(.title.bold())
            Text("Record doctor calls & visits, then AI transcribes and summarizes.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                statusChip(settings.hasTranscriptionKey ? "Groq ✓" : "Groq —", ok: settings.hasTranscriptionKey)
                statusChip(settings.hasSummaryKey ? "AI ✓" : "AI —", ok: settings.hasSummaryKey)
            }
        }
    }

    private func statusChip(_ text: String, ok: Bool) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(ok ? Color.green.opacity(0.15) : Color.orange.opacity(0.15))
            .foregroundStyle(ok ? .green : .orange)
            .clipShape(Capsule())
    }

    private var quickActions: some View {
        VStack(spacing: 12) {
            NavigationLink {
                CallVisitView()
            } label: {
                actionRow(icon: "phone.fill", color: .green, title: "Call & Record", subtitle: "Dial doctor, record with speakerphone")
            }

            NavigationLink {
                RecordView()
            } label: {
                actionRow(icon: "mic.fill", color: .indigo, title: "In-Person Visit", subtitle: "Record at the clinic")
            }
        }
    }

    private func actionRow(icon: String, color: Color, title: String, subtitle: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(color.gradient)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("No visits yet")
                .font(.headline)
            Text("Use Call or Record to capture your first consultation.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }

    private var recentVisits: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Recent")
                .font(.headline)
            ForEach(visitStore.visits.prefix(3)) { visit in
                NavigationLink {
                    VisitDetailView(visit: visit)
                } label: {
                    VisitRow(visit: visit)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct VisitRow: View {
    let visit: Visit

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(visit.doctor).font(.subheadline.weight(.semibold))
                Text("\(visit.date) · \(visit.duration)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if visit.isProcessing {
                ProgressView()
            } else if visit.summary != nil {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
