import SwiftUI

struct VisitsListView: View {
    @EnvironmentObject private var visitStore: VisitStore

    var body: some View {
        NavigationStack {
            Group {
                if visitStore.visits.isEmpty {
                    ContentUnavailableView(
                        "No visits yet",
                        systemImage: "doc.text",
                        description: Text("Phone calls and recordings appear here after AI processing.")
                    )
                } else {
                    List {
                        ForEach(visitStore.visits) { visit in
                            NavigationLink {
                                VisitDetailView(visit: visit)
                            } label: {
                                VisitRow(visit: visit)
                            }
                        }
                        .onDelete(perform: delete)
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Past Visits")
        }
    }

    private func delete(at offsets: IndexSet) {
        for index in offsets {
            visitStore.delete(visitStore.visits[index])
        }
    }
}
